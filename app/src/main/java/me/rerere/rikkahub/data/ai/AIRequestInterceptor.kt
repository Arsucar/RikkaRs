package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.clash.ClashApiClient
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Retries a 429 response by switching the Clash proxy node and replaying the request.
 * Experimental, off by default: only applies when the provider has enabled it and a
 * Clash external controller is reachable. Any failure -> original 429 passes through.
 */
class AIRequestInterceptor(
    private val settingsStore: SettingsStore,
    private val clashApiClient: ClashApiClient,
) : Interceptor {

    private val switchLock = ReentrantLock() // 并发 429 互斥（AC6）

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 429) return response

        // ① 功能默认关闭（AC1）；任何异常/未启用走原样透传
        val clashConfig = runBlocking { settingsStore.settingsFlow.first().clashConfig }
        if (clashConfig.maxRetries <= 0) return response
        val provider = runBlocking { settingsStore.findProviderByBaseUrl(request.url.host) }
        if (provider == null || !provider.enable429IpRotation) return response

        // 当前仍存活的 429 响应（未被 close），供上层读取/分类
        var lastResponse: Response = response
        return try {
            switchLock.withLock { // AC6 互斥：并发 429 串行切换
                for (attempt in 1..clashConfig.maxRetries) {
                    // 选与当前不同节点；失败抛异常 -> 走 catch 放行原 429（AC3）
                    val nodes = clashApiClient.getSelectableNodes(clashConfig.apiBaseUrl, clashConfig.groupName)
                    val currentName = clashApiClient.getCurrentNode(clashConfig.apiBaseUrl, clashConfig.groupName)
                    val next = nodes.firstOrNull { it != currentName }
                        ?: throw IllegalStateException("no alternate node")
                    clashApiClient.switchNode(clashConfig.apiBaseUrl, clashConfig.groupName, next)
                    Thread.sleep(clashConfig.switchDelayMs)

                    val replayed = chain.proceed(request) // 重放原请求
                    if (replayed.code != 429) {
                        lastResponse.close() // 丢弃旧 429 body，避免连接泄漏
                        return replayed // 重放成功（AC2）
                    }
                    lastResponse.close()
                    lastResponse = replayed
                    Log.i("ClashRetry", "attempt $attempt still 429, switched to $next")
                }
            }
            lastResponse // 重试耗尽 -> 最后一次 429 原样返回（AC4）
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.i("ClashRetry", "429 retry skipped: ${e.message}")
            lastResponse // 任一步失败 -> 原 429（AC3），保持未 close 可读
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