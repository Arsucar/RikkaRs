package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * The blocking retry/replay loop (which needs the replay's response code) runs on
 * [Dispatchers.IO] via [runBlocking] so the OkHttp dispatcher thread is released while
 * the coroutine is suspended on [delay] and the Clash network calls. Trace writes are
 * pure in-memory side effects ([ClashRetryTracer.record] only mutates an ArrayDeque
 * guarded by a Mutex and pushes a StateFlow snapshot), so they are dispatched as
 * fire-and-forget on [interceptorScope] instead of blocking the calling thread.
 *
 * Tracing note: every 429 decision (pre-check skip, exhausted, success, pass-through)
 * is recorded to [ClashRetryTracer] for the debug panel. Trace writes are dispatched
 * as fire-and-forget on [interceptorScope] (no [runBlocking]); they are guarded
 * internally by a Mutex and expose a consistent snapshot via StateFlow so the UI
 * thread can read them safely.
 */
class AIRequestInterceptor(
    private val settingsStore: SettingsStore,
    private val clashApiClient: ClashApiClient,
    private val clashRetryTracer: ClashRetryTracer,
) : Interceptor {

    // 并发 429 互斥（AC6）：只保护"选节点 + 切换"这个快动作
    private val switchMutex = Mutex()

    /**
     * 专用协程作用域，用于 fire-and-forget 的 trace 记录写入，避免在 OkHttp 网络线程上
     * 阻塞等待 [ClashRetryTracer] 的 Mutex。trace 写入仅为内存副作用，丢弃返回值无影响。
     */
    private val interceptorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            // trace 写入仅为内存副作用，fire-and-forget 避免阻塞 OkHttp 线程
            recordTrace {
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
            }
            return response
        }
        if (provider == null) {
            recordTrace {
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
            }
            return response
        }
        if (!provider.enable429IpRotation) {
            recordTrace {
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
            }
            return response
        }

        // Only the response returned to OkHttp may remain open. Every response that is
        // replaced by a replay is closed before the next chain.proceed call. This is
        // important for OkHttp: proceeding while the previous response body is still
        // open can fail with "previous response is still open".
        val responseLifecycle = ReplayResponseLifecycle(response)
        val switches = mutableListOf<SwitchAttempt>()
        var finalCode: Int? = response.code
        var exhausted = false

        return try {
            // OkHttp's Interceptor.intercept() is synchronous by API contract.
            // runBlocking is unavoidable here; the calling OkHttp thread is already
            // dedicated to this request. Dispatchers.IO lets the thread be released
            // while the coroutine is suspended on [delay] and Clash network calls.
            // This is acceptable per #271 scope.
            runBlocking(Dispatchers.IO) {
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
                        return@runBlocking responseLifecycle.handOff()
                    }
                    delay(clashConfig.switchDelayMs) // 挂起式等待，不占 dispatcher 线程

                    // Release the previous response before opening the replay response.
                    // If proceed throws, the old response is already closed and must not be
                    // returned to the caller; the exception is allowed to propagate.
                    responseLifecycle.closeBeforeReplay()
                    val replayed = try {
                        chain.proceed(request) // 重放原请求（不持锁）
                    } catch (io: IOException) {
                        Log.i("ClashRetry", "attempt $attempt proceed IOException: ${io.message}")
                        switches += SwitchAttempt(nodeName = switchedTo, success = true, error = "proceed IOException: ${io.message}", replayedCode = null)
                        throw io
                    }
                    responseLifecycle.accept(replayed)
                    switches += SwitchAttempt(nodeName = switchedTo, success = true, error = null, replayedCode = replayed.code)
                    finalCode = replayed.code
                    if (replayed.code != 429) {
                        return@runBlocking responseLifecycle.handOff() // 重放成功（AC2）
                    }
                    Log.i("ClashRetry", "attempt $attempt still 429, switched to $switchedTo")
                }
                exhausted = true // 重试耗尽 -> 最后一次 429 原样返回（AC4）
                responseLifecycle.handOff()
            }
        } finally {
            // Covers cancellation during the delay/replay and any unexpected exception.
            // A response handed to OkHttp must remain open for its caller to consume.
            responseLifecycle.closeIfNotHandedOff()
            // trace 写入仅为内存副作用，fire-and-forget 避免阻塞 OkHttp 线程
            recordTrace {
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
            }
        }
    }

    /**
     * 以 fire-and-forget 方式记录一条 trace。trace 写入仅修改内存中的 ArrayDeque（受
     * [ClashRetryTracer] 内部 Mutex 保护）并推送 StateFlow 快照，无返回值需求，因此不阻塞
     * 调用线程，直接派发到 [interceptorScope]。trace 对象在派发前构建，避免协程内捕获
     * 调用栈的可变状态（如 response.code）。
     */
    private fun recordTrace(build: () -> ClashRetryTrace) {
        val trace = build()
        interceptorScope.launch { clashRetryTracer.record(trace) }
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

    companion object {
        // 前置检查失败分类的稳定代码，供调试面板分类展示（AC1/AC7 透传路径）
        private const val SKIP_MAX_RETRIES = "SKIP_MAX_RETRIES"
        private const val SKIP_NO_PROVIDER = "SKIP_NO_PROVIDER"
        private const val SKIP_ROTATION_DISABLED = "SKIP_ROTATION_DISABLED"
    }
}

/** Owns the response currently held by the interceptor during replay. */
internal class ReplayResponseLifecycle(initial: Response) {
    private var current: Response? = initial
    private var handedOff = false

    /** Closes the response before [Interceptor.Chain.proceed] opens its replay response. */
    fun closeBeforeReplay() {
        current?.close()
        current = null
    }

    fun accept(response: Response) {
        check(current == null) { "previous response must be closed before accepting replay" }
        current = response
    }

    fun handOff(): Response {
        handedOff = true
        return checkNotNull(current)
    }

    /** Closes an owned response on cancellation or an exception before hand-off. */
    fun closeIfNotHandedOff() {
        if (!handedOff) current?.close()
    }
}
