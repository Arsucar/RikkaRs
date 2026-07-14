package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ManualCompressionPreferencesTest {
    @Test
    fun successfulCompressionPersistsThePairOnce() = runBlocking {
        val writes = mutableListOf<Pair<Int, Int>>()
        var compressionFailureHandled = false
        var persistenceFailureHandled = false

        handleManualCompressionResult(
            result = Result.success(Unit),
            targetTokens = 3_333,
            keepRecentMessages = 21,
            persistPreferences = { targetTokens, keepRecentMessages ->
                writes += targetTokens to keepRecentMessages
            },
            onCompressionFailure = { compressionFailureHandled = true },
            onPreferencePersistenceFailure = { persistenceFailureHandled = true },
        )

        assertEquals(listOf(3_333 to 21), writes)
        assertFalse(compressionFailureHandled)
        assertFalse(persistenceFailureHandled)
    }

    @Test
    fun failedCompressionDoesNotPersist() = runBlocking {
        val failure = IllegalStateException("compression failed")
        var persisted = false
        var handledFailure: Throwable? = null

        handleManualCompressionResult(
            result = Result.failure(failure),
            targetTokens = 3_333,
            keepRecentMessages = 21,
            persistPreferences = { _, _ -> persisted = true },
            onCompressionFailure = { handledFailure = it },
            onPreferencePersistenceFailure = {},
        )

        assertFalse(persisted)
        assertSame(failure, handledFailure)
    }

    @Test
    fun compressionCancellationDoesNotPersistAndPropagates() {
        val cancellation = CancellationException("cancelled")
        var persisted = false

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                handleManualCompressionResult(
                    result = Result.failure(cancellation),
                    targetTokens = 3_333,
                    keepRecentMessages = 21,
                    persistPreferences = { _, _ -> persisted = true },
                    onCompressionFailure = {},
                    onPreferencePersistenceFailure = {},
                )
            }
        }

        assertFalse(persisted)
        assertSame(cancellation, thrown)
    }

    @Test
    fun ordinaryPersistenceFailureIsHandledWithoutChangingCompressionSuccess() = runBlocking {
        val persistenceFailure = IllegalStateException("write failed")
        var handledFailure: Throwable? = null

        handleManualCompressionResult(
            result = Result.success(Unit),
            targetTokens = 3_333,
            keepRecentMessages = 21,
            persistPreferences = { _, _ -> throw persistenceFailure },
            onCompressionFailure = {},
            onPreferencePersistenceFailure = { handledFailure = it },
        )

        assertSame(persistenceFailure, handledFailure)
    }

    @Test
    fun persistenceCancellationPropagates() {
        val cancellation = CancellationException("cancelled")
        var persistenceFailureHandled = false

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                handleManualCompressionResult(
                    result = Result.success(Unit),
                    targetTokens = 3_333,
                    keepRecentMessages = 21,
                    persistPreferences = { _, _ -> throw cancellation },
                    onCompressionFailure = {},
                    onPreferencePersistenceFailure = { persistenceFailureHandled = true },
                )
            }
        }

        assertSame(cancellation, thrown)
        assertFalse(persistenceFailureHandled)
    }
}
