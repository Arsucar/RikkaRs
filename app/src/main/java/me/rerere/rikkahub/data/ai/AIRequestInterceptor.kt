package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.clash.ClashApiClient
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.HttpUrl
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

        // ① 功能默认关闭；任何异常/未启用走原样透传（AC1）
        val clashConfig = runBlocking { settingsStore.settingsFlow.first().clashConfig }
        if (clashConfig.maxRetries <= 0) return response
        val provider = runBlocking { settingsStore.findProviderByBaseUrl(request.url.host) }
        if (provider == null || !provider.enable429IpRotation) return response

        return try {
            val result = switchLock.withLock { // AC6 互斥
                var lastResponse: Response = response
                for (attempt in 1..clashConfig.maxRetries) {
                    // 选与当前不同节点；失败直接抛 -> 走 catch 放行原 429
                    val nodes = clashApiClient.getSelectableNodes(clashConfig.apiBaseUrl, clashConfig.groupName)
                    val currentName = clashApiClient.getCurrentNode(clashConfig.apiBaseUrl, clashConfig.groupName)
                    val next = nodes.firstOrNull { it != currentName }
                        ?: throw IllegalStateException("no alternate node")
                    clashApiClient.switchNode(clashConfig.apiBaseUrl, clashConfig.groupName, next)
                    Thread.sleep(clashConfig.switchDelayMs)
                    response.close() // 丢弃上一次 429 body，避免连接泄漏
                    lastResponse = chain.proceed(request) // 重放原请求
                    if (lastResponse.code != 429) return lastResponse
                    Log.i("ClashRetry", "attempt $attempt still 429, switched to $next")
                }
                lastResponse // 重试耗尽 -> 最后一次 429 原样返回（AC4）
            }
            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.i("ClashRetry", "429 retry skipped: ${e.message}")
            response // 任一步失败 -> 原 429（AC3）
        }
    }

    /**
     * Locates the provider whose base URL host equals the request host.
     * All concrete [ProviderSetting] subtypes carry a `baseUrl`; EXTENSION providers
     * fall back to the request host itself.
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