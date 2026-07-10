package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.delay
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.uuid.Uuid

private const val TAG = "ProviderRateLimiter"
private const val WINDOW_MS = 60_000L

object ProviderRateLimiter {
    private val buckets = ConcurrentHashMap<Uuid, Bucket>()

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
        val rpm = provider.rateLimit.rpm?.takeIf { it > 0 }
        val tpm = provider.rateLimit.tpm?.takeIf { it > 0 }
        if (rpm == null && tpm == null) return

        val bucket = buckets.computeIfAbsent(provider.id) { Bucket() }
        while (true) {
            val waitMs = synchronized(bucket) {
                val now = System.currentTimeMillis()
                bucket.prune(now)

                val requestWait = rpm?.let { limit ->
                    val oldestRequest = bucket.requests.peekFirst()
                    if (bucket.requests.size >= limit && oldestRequest != null) {
                        WINDOW_MS - (now - oldestRequest)
                    } else {
                        0L
                    }
                } ?: 0L

                val tokenWait = tpm?.let { limit ->
                    val tokenCost = estimatedTokens.coerceAtLeast(1)
                    val oldestToken = bucket.tokens.peekFirst()
                    if (bucket.tokens.sumOf { it.tokens } + tokenCost > limit && oldestToken != null) {
                        WINDOW_MS - (now - oldestToken.timeMs)
                    } else {
                        0L
                    }
                } ?: 0L

                max(requestWait, tokenWait).coerceAtLeast(0L).also { wait ->
                    if (wait == 0L) {
                        bucket.requests.addLast(now)
                        bucket.tokens.addLast(TokenEvent(now, estimatedTokens.coerceAtLeast(1)))
                    }
                }
            }

            if (waitMs <= 0L) return
            Log.i(TAG, "Waiting ${waitMs}ms for provider '${provider.name}' rate limit")
            delay(waitMs)
        }
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

private class Bucket {
    val requests: ArrayDeque<Long> = ArrayDeque()
    val tokens: ArrayDeque<TokenEvent> = ArrayDeque()

    fun prune(now: Long) {
        while (requests.peekFirst()?.let { now - it >= WINDOW_MS } == true) {
            requests.removeFirst()
        }
        while (tokens.peekFirst()?.let { now - it.timeMs >= WINDOW_MS } == true) {
            tokens.removeFirst()
        }
    }
}

private data class TokenEvent(
    val timeMs: Long,
    val tokens: Int,
)
