package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCompressionCoordinatorTest {
    @Test
    fun `manual and auto attempts are mutually exclusive`() = runBlocking {
        val coordinator = ConversationCompressionCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            coordinator.tryRun {
                entered.complete(Unit)
                release.await()
                "done"
            }
        }
        entered.await()

        assertSame(CompressionLockResult.Busy, coordinator.tryRun { "unexpected" })
        release.complete(Unit)
        assertEquals(CompressionLockResult.Acquired("done"), first.await())
    }

    @Test
    fun `preview unsupported flows and null provider input have zero auto compression side effects`() = runBlocking {
        val original = Any()
        var policyMutations = 0
        var lockAttempts = 0
        var persistenceWrites = 0
        var eventEmissions = 0
        var limiterCalls = 0
        var providerCalls = 0
        val unsupportedCases = listOf(
            GenerationInvocationKind.Preview to true,
            GenerationInvocationKind.Regenerate to true,
            GenerationInvocationKind.ToolContinuation to true,
            GenerationInvocationKind.NormalSend to false,
        )

        unsupportedCases.forEach { (invocationKind, providerInputAvailable) ->
            val resolved = runAutoCompressionIfEligible(
                invocationKind = invocationKind,
                providerInputAvailable = providerInputAvailable,
                preparedOriginal = original,
            ) {
                policyMutations++
                lockAttempts++
                persistenceWrites++
                eventEmissions++
                limiterCalls++
                providerCalls++
                Any()
            }
            assertSame(original, resolved)
        }

        assertEquals(0, policyMutations)
        assertEquals(0, lockAttempts)
        assertEquals(0, persistenceWrites)
        assertEquals(0, eventEmissions)
        assertEquals(0, limiterCalls)
        assertEquals(0, providerCalls)
    }

    @Test
    fun `manual busy lock result becomes visible failure`() {
        val busy: CompressionLockResult<Unit> = CompressionLockResult.Busy
        val acquired: CompressionLockResult<Unit> = CompressionLockResult.Acquired(Unit)

        assertTrue(busy.toManualCompressionResult().exceptionOrNull() is CompressionBusyException)
        assertTrue(acquired.toManualCompressionResult().isSuccess)
    }

    @Test
    fun `failed attempt returns exact prepared original without reprepare`() = runBlocking {
        val original = Any()
        var reloads = 0
        var prepares = 0
        var failures = 0

        val resolved = resolveAfterAutoCompressionAttempt(
            preparedOriginal = original,
            compress = { error("compression failed") },
            reload = { reloads++; Any() },
            prepareReloaded = { prepares++; Any() },
            onSuccess = { error("must not succeed") },
            onFailure = { failures++ },
        )

        assertSame(original, resolved)
        assertEquals(0, reloads)
        assertEquals(0, prepares)
        assertEquals(1, failures)
    }

    @Test
    fun `successful attempt reloads and reprepares exactly once`() = runBlocking {
        val original = Any()
        val rebuilt = Any()
        var compressions = 0
        var reloads = 0
        var prepares = 0
        var successes = 0
        var failures = 0

        val resolved = resolveAfterAutoCompressionAttempt(
            preparedOriginal = original,
            compress = { compressions++ },
            reload = { reloads++; "snapshot" },
            prepareReloaded = {
                assertEquals("snapshot", it)
                prepares++
                rebuilt
            },
            onSuccess = { successes++ },
            onFailure = { failures++ },
        )

        assertSame(rebuilt, resolved)
        assertEquals(1, compressions)
        assertEquals(1, reloads)
        assertEquals(1, prepares)
        assertEquals(1, successes)
        assertEquals(0, failures)
    }

    @Test
    fun `parent cancellation propagates and is not converted to fail open`() = runBlocking {
        var failures = 0
        val result = runCatching {
            resolveAfterAutoCompressionAttempt(
                preparedOriginal = Any(),
                compress = { throw CancellationException("cancelled") },
                reload = { Any() },
                prepareReloaded = { Any() },
                onSuccess = {},
                onFailure = { failures++ },
            )
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(0, failures)
    }
}
