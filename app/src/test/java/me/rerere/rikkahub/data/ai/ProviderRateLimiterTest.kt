package me.rerere.rikkahub.data.ai

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.ProviderRateLimit
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ProviderRateLimiterTest {
    @Test
    fun concurrentRpmWaitersRecheckAtomicallyAfterWakeup() = runBlocking {
        val now = AtomicLong(0L)
        val waits = ControlledWaits()
        val limiter = ProviderRateLimiterStore(
            windowMillis = 100L,
            nowMillis = now::get,
            delayMillis = waits::delay,
            logWait = {},
        )
        val provider = provider(rpm = 1)
        limiter.await(provider, estimatedTokens = 1)

        val completions = Channel<Unit>(Channel.UNLIMITED)
        val queued = List(2) {
            async {
                limiter.await(provider, estimatedTokens = 1)
                completions.send(Unit)
            }
        }

        assertEquals(listOf(100L, 100L), listOf(waits.next(), waits.next()).sorted())
        assertTrue(completions.tryReceive().isFailure)

        now.set(100L)
        waits.release(2)
        assertEquals(100L, waits.next())
        withTimeout(5_000L) { completions.receive() }
        assertTrue(completions.tryReceive().isFailure)

        now.set(200L)
        waits.release()
        withTimeout(5_000L) { completions.receive() }
        queued.awaitAll()
        Unit
    }

    @Test
    fun sameProviderIdSharesBucketWhileDifferentProvidersStayIndependent() = runBlocking {
        val now = AtomicLong(0L)
        val waits = ControlledWaits()
        val limiter = ProviderRateLimiterStore(
            windowMillis = 100L,
            nowMillis = now::get,
            delayMillis = waits::delay,
            logWait = {},
        )
        val sharedId = Uuid.random()
        val mainProvider = provider(id = sharedId, name = "main", rpm = 1)
        val subagentProvider = provider(id = sharedId, name = "subagent", rpm = 1)
        val otherProvider = provider(name = "other", rpm = 1)

        limiter.await(mainProvider, estimatedTokens = 1)
        val sharedWaiter = async { limiter.await(subagentProvider, estimatedTokens = 1) }
        assertEquals(100L, waits.next())

        limiter.await(otherProvider, estimatedTokens = 1)
        assertTrue(sharedWaiter.isActive)

        sharedWaiter.cancelAndJoin()
        assertTrue(sharedWaiter.isCancelled)
    }

    @Test
    fun tpmCapacityWaitsUntilTokensExpire() = runBlocking {
        val now = AtomicLong(0L)
        val waits = ControlledWaits()
        val limiter = ProviderRateLimiterStore(
            windowMillis = 100L,
            nowMillis = now::get,
            delayMillis = waits::delay,
            logWait = {},
        )
        val provider = provider(tpm = 10)

        limiter.await(provider, estimatedTokens = 6)
        limiter.await(provider, estimatedTokens = 4)
        val queued = async { limiter.await(provider, estimatedTokens = 1) }

        assertEquals(100L, waits.next())
        assertTrue(queued.isActive)

        now.set(100L)
        waits.release()
        queued.await()
    }

    @Test
    fun cancellingQueuedRequestDoesNotConsumePermit() = runBlocking {
        val now = AtomicLong(0L)
        val waits = ControlledWaits()
        val limiter = ProviderRateLimiterStore(
            windowMillis = 100L,
            nowMillis = now::get,
            delayMillis = waits::delay,
            logWait = {},
        )
        val provider = provider(rpm = 1)
        limiter.await(provider, estimatedTokens = 1)

        val cancelled = async { limiter.await(provider, estimatedTokens = 1) }
        assertEquals(100L, waits.next())
        cancelled.cancelAndJoin()

        now.set(100L)
        limiter.await(provider, estimatedTokens = 1)
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun promptEstimatorHandlesEmptyTextBoundariesAndMultipleMessages() {
        assertEquals(0, estimatePromptTokens(emptyList()))
        assertEquals(0, estimatePromptTokens(listOf(UIMessage.user(""))))
        assertEquals(0, estimatePromptTokens(listOf(UIMessage.user("abc"))))
        assertEquals(1, estimatePromptTokens(listOf(UIMessage.user("abcd"))))
        assertEquals(1, estimatePromptTokens(listOf(UIMessage.user("abcdefg"))))
        assertEquals(2, estimatePromptTokens(listOf(UIMessage.user("abcdefgh"))))
        assertEquals(3, estimatePromptTokens(listOf(UIMessage.user("abcdefgh"), UIMessage.user("abcd"))))
    }

    @Test
    fun promptEstimatorIgnoresNonTextPartsAndCountsAllTextParts() {
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.USER,
            parts = listOf(
                UIMessagePart.Image("data:image/png;base64," + "a".repeat(10_000)),
                UIMessagePart.Text("1234"),
                UIMessagePart.Document("file:///tmp/doc", "doc.txt"),
                UIMessagePart.Text("5678"),
            ),
        )

        assertEquals(2, estimatePromptTokens(listOf(message)))
    }

    @Test
    fun limiterEstimateRetainsOutputReserveSemantics() {
        val messages = listOf(UIMessage.user("12345678"))
        assertEquals(7, ProviderRateLimiter.estimateTokens(
            messages,
            TextGenerationParams(model = Model("model"), maxTokens = 5),
        ))
        assertEquals(-3, ProviderRateLimiter.estimateTokens(
            messages,
            TextGenerationParams(model = Model("model"), maxTokens = -5),
        ))
        assertEquals(2, ProviderRateLimiter.estimateTokens(
            messages,
            TextGenerationParams(model = Model("model"), maxTokens = null),
        ))
    }

    private fun provider(
        id: Uuid = Uuid.random(),
        name: String = "provider",
        rpm: Int? = null,
        tpm: Int? = null,
    ): ProviderSetting = ProviderSetting.OpenAI(
        id = id,
        name = name,
        rateLimit = ProviderRateLimit(rpm = rpm, tpm = tpm),
    )

    private class ControlledWaits {
        private val waits = Channel<Long>(Channel.UNLIMITED)
        private val releases = Channel<Unit>(Channel.UNLIMITED)

        suspend fun delay(waitMillis: Long) {
            waits.send(waitMillis)
            releases.receive()
        }

        suspend fun next(): Long = withTimeout(5_000L) { waits.receive() }

        fun release(count: Int = 1) {
            repeat(count) {
                check(releases.trySend(Unit).isSuccess)
            }
        }
    }
}
