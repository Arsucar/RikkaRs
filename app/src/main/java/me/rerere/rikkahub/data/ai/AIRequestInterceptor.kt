package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.clash.ClashApiClient
import me.rerere.rikkahub.data.ai.clash.ClashRetryTrace
import me.rerere.rikkahub.data.ai.clash.ClashRetryTracer
import me.rerere.rikkahub.data.ai.clash.SwitchAttempt
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries a 429 response by switching the Clash proxy node and replaying the request.
 * Experimental, off by default: only applies when the provider has enabled it and a
 * Clash external controller is reachable. Any failure -> original 429 passes through.
 *
 * Threading note: OkHttp application interceptors run synchronously on the OkHttp
 * dispatcher thread pool, so suspend calls are bridged with [runBlocking] (the app
 * already bridges OkHttp-suspend calls this way). The switch mutation (query + select
 * + PUT) is guarded by a coroutine [Mutex] so concurrent 429s never switch at the same
 * time (AC6). The wait ([delay]) and the request replay run OUTSIDE the lock so a slow
 * retry of one request does not stall unrelated AI traffic.
 *
 * Tracing note: every 429 decision (pre-check skip, exhausted, success, pass-through)
 * is recorded to [ClashRetryTracer] for the debug panel. Trace writes are also bridged
 * with [runBlocking]; they are guarded internally by a Mutex and expose a consistent
 * snapshot via StateFlow so the UI thread can read them safely.
 */
class AIRequestInterceptor(
    private val settingsStore: SettingsStore,
    private val clashApiClient: ClashApiClient,
    private val clashRetryTracer: ClashRetryTracer,
) : Interceptor {

    // 并发 429 互斥（AC6）：只保护"选节点 + 切换"这个快动作
    private val switchMutex = Mutex()

    // 前置检查失败分类的稳定代码，供调试面板分类展示（AC1/AC7 透传路径）
    private const val SKIP_MAX_RETRIES = "SKIP_MAX_RETRIES"
    private const val SKIP_NO_PROVIDER = "SKIP_NO_PROVIDER"
    private const val SKIP_ROTATION_DISABLED = "SKIP_ROTATION_DISABLED"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 429) return response

        // ① 功能默认关闭（AC1）；任何异常/未启用走原样透传
        val settings = settingsStore.settingsFlow.value
        val clashConfig = settings.clashConfig
        val requestHost = request.url.host
        val provider = settingsStore.findProviderByBaseUrl(requestHost)

        // 前置检查失败路径：记录 trace 后静默透传（这是调试面板的核心价值，原来完全静默）
        if (clashConfig.maxRetries <= 0) {
            runBlocking {
                clashRetryTracer.record(
                    ClashRetryTrace(
                        timestamp = System.currentTimeMillis(),
                        requestHost = requestHost,
                        responseCode = response.code,
                        matchedProvider = null,
                        rotationEnabled = false,
                        maxRetries = clashConfig.maxRetries,
                        finalCode = response.code,
                        skippedReason = SKIP_MAX_RETRIES,
                    )
                )
            }
            return response
        }
        if (provider == null) {
            runBlocking {
                clashRetryTracer.record(
                    ClashRetryTrace(
                        timestamp = System.currentTimeMillis(),
                        requestHost = requestHost,
                        responseCode = response.code,
                        matchedProvider = null,
                        rotationEnabled = false,
                        maxRetries = clashConfig.maxRetries,
                        finalCode = response.code,
                        skippedReason = SKIP_NO_PROVIDER,
                    )
                )
            }
            return response
        }
        if (!provider.enable429IpRotation) {
            runBlocking {
                clashRetryTracer.record(
                    ClashRetryTrace(
                        timestamp = System.currentTimeMillis(),
                        requestHost = requestHost,
                        responseCode = response.code,
                        matchedProvider = provider.name,
                        rotationEnabled = false,
                        maxRetries = clashConfig.maxRetries,
                        finalCode = response.code,
                        skippedReason = SKIP_ROTATION_DISABLED,
                    )
                )
            }
            return response
        }

        // 当前仍存活的 429 响应（未被 close），供上层读取/分类
        var lastResponse: Response = response
        val switches = mutableListOf<SwitchAttempt>()
        var finalCode: Int? = response.code
        var exhausted = false
        return try {
            runBlocking {
                for (attempt in 1..clashConfig.maxRetries) {
                    // 互斥：查询 + 选不同节点 + 切换（快动作）；失败在循环内捕获并记录，不抛到外层
                    val switchedTo = try {
                        switchMutex.withLock {
                            val nodes = clashApiClient.getSelectableNodes(clashConfig.apiBaseUrl, clashConfig.groupName)
                            val currentName = clashApiClient.getCurrentNode(clashConfig.apiBaseUrl, clashConfig.groupName)
                            val alternates = nodes.filter { it != currentName }
                            if (alternates.isEmpty()) {
                                throw IllegalStateException("no alternate node")
                            }
                            // Round-robin across alternates so multi-retry can leave the first two nodes
                            val next = alternates[(attempt - 1) % alternates.size]
                            clashApiClient.switchNode(clashConfig.apiBaseUrl, clashConfig.groupName, next)
                            next
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        switches += SwitchAttempt(nodeName = null, success = false, error = e.message, replayedCode = null)
                        return@runBlocking lastResponse
                    }
                    delay(clashConfig.switchDelayMs) // 挂起式等待，不占 dispatcher 线程

                    val replayed = try {
                        chain.proceed(request) // 重放原请求（不持锁）
                    } catch (io: IOException) {
                        Log.i("ClashRetry", "attempt $attempt proceed IOException: ${io.message}")
                        switches += SwitchAttempt(nodeName = switchedTo, success = true, error = "proceed IOException: ${io.message}", replayedCode = null)
                        return@runBlocking lastResponse
                    }
                    switches += SwitchAttempt(nodeName = switchedTo, success = true, error = null, replayedCode = replayed.code)
                    if (replayed.code != 429) {
                        finalCode = replayed.code
                        lastResponse.close() // 丢弃旧 429 body，避免连接泄漏
                        return@runBlocking replayed // 重放成功（AC2）
                    }
                    lastResponse.close()
                    lastResponse = replayed
                    Log.i("ClashRetry", "attempt $attempt still 429, switched to $switchedTo")
                }
                exhausted = true // 重试耗尽 -> 最后一次 429 原样返回（AC4）
                lastResponse
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.i("ClashRetry", "429 retry skipped: ${e.message}")
            switches += SwitchAttempt(nodeName = null, success = false, error = e.message, replayedCode = null)
            lastResponse // 任一步失败 -> 原 429（AC3），保持未 close 可读
        }.also {
            runBlocking {
                clashRetryTracer.record(
                    ClashRetryTrace(
                        timestamp = System.currentTimeMillis(),
                        requestHost = requestHost,
                        responseCode = response.code,
                        matchedProvider = provider.name,
                        rotationEnabled = provider.enable429IpRotation,
                        maxRetries = clashConfig.maxRetries,
                        switches = switches.toList(),
                        finalCode = finalCode,
                        exhausted = exhausted,
                    )
                )
            }
        }
    }

    /**
     * Locates the provider whose base URL host equals the request host.
     * All concrete [ProviderSetting] subtypes carry a `baseUrl`.
     */
    private fun SettingsStore.findProviderByBaseUrl(host: String): ProviderSetting? =
        settingsFlow.value.providers.firstOrNull { provider ->
            providerBaseUrl(provider)?.let { it.toHttpUrlOrNull()?.host } == host
        }

    private fun providerBaseUrl(provider: ProviderSetting): String? = when (provider) {
        is ProviderSetting.OpenAI -> provider.baseUrl
        is ProviderSetting.Google -> provider.baseUrl
        is ProviderSetting.Claude -> provider.baseUrl
    }
}