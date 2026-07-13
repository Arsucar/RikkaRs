package me.rerere.rikkahub.data.ai

import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.math.max
import kotlin.uuid.Uuid

private const val TAG = "ProviderRateLimiter"
internal const val PROVIDER_RATE_LIMIT_WINDOW_MILLIS = 60_000L

object ProviderRateLimiter {
    private val limiter = ProviderRateLimiterStore()

    suspend fun await(
        provider: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ) {
        await(
            provider = provider,
            estimatedTokens = estimateTokens(messages, params),
        )
    }

    suspend fun await(provider: ProviderSetting, estimatedTokens: Int) {
        limiter.await(provider, estimatedTokens)
    }

    fun estimateTokens(
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Int {
        val promptChars = messages.sumOf { message ->
            message.parts.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length }
        }
        return (promptChars / 4) + (params.maxTokens ?: 0)
    }
}

internal class ProviderRateLimiterStore(
    private val windowMillis: Long = PROVIDER_RATE_LIMIT_WINDOW_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val logWait: (String) -> Unit = { message -> runCatching { Log.i(TAG, message) } },
) {
    private val buckets = ConcurrentHashMap<Uuid, Bucket>()

    init {
        require(windowMillis > 0) { "windowMillis must be positive" }
    }

    suspend fun await(provider: ProviderSetting, estimatedTokens: Int) {
        val rpm = provider.rateLimit.rpm?.takeIf { it > 0 }
        val tpm = provider.rateLimit.tpm?.takeIf { it > 0 }
        if (rpm == null && tpm == null) return

        val tokenCost = estimatedTokens.coerceAtLeast(1)
        val bucket = buckets.computeIfAbsent(provider.id) { Bucket() }
        while (true) {
            val waitMillis = bucket.mutex.withLock {
                val now = nowMillis()
                bucket.prune(now, windowMillis)

                val requestWait = rpm?.let { limit ->
                    val oldestRequest = bucket.requests.peekFirst()
                    if (bucket.requests.size >= limit && oldestRequest != null) {
                        windowMillis - (now - oldestRequest)
                    } else {
                        0L
                    }
                } ?: 0L

                val tokenWait = tpm?.let { limit ->
                    val oldestToken = bucket.tokens.peekFirst()
                    if (bucket.tokens.sumOf { it.tokens } + tokenCost > limit && oldestToken != null) {
                        windowMillis - (now - oldestToken.timeMillis)
                    } else {
                        0L
                    }
                } ?: 0L

                max(requestWait, tokenWait).coerceAtLeast(0L).also { wait ->
                    if (wait == 0L) {
                        bucket.requests.addLast(now)
                        bucket.tokens.addLast(TokenEvent(now, tokenCost))
                    }
                }
            }

            if (waitMillis == 0L) return
            logWait("Waiting ${waitMillis}ms for provider '${provider.name}' rate limit")
            delayMillis(waitMillis)
        }
    }
}

private class Bucket {
    val mutex = Mutex()
    val requests: ArrayDeque<Long> = ArrayDeque()
    val tokens: ArrayDeque<TokenEvent> = ArrayDeque()

    fun prune(now: Long, windowMillis: Long) {
        while (requests.peekFirst()?.let { now - it >= windowMillis } == true) {
            requests.removeFirst()
        }
        while (tokens.peekFirst()?.let { now - it.timeMillis >= windowMillis } == true) {
            tokens.removeFirst()
        }
    }
}

private data class TokenEvent(
    val timeMillis: Long,
    val tokens: Int,
)
