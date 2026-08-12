package me.rerere.rikkahub.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimisticWriteTest {
    @Test
    fun successKeepsOptimisticValueWithoutRollback() = runBlocking {
        var ui = "base"
        var rolledBack = false
        var errorHandled = false

        val result = runOptimisticWrite(
            applyOptimistic = {
                ui = "optimistic"
                "optimistic"
            },
            persist = { /* ok */ },
            rollback = {
                rolledBack = true
                ui = "base"
            },
            onError = { errorHandled = true },
        )

        assertTrue(result.success)
        assertEquals("optimistic", result.value)
        assertNull(result.error)
        assertEquals("optimistic", ui)
        assertFalse(rolledBack)
        assertFalse(errorHandled)
    }

    @Test
    fun failureRollsBackAndReportsError() = runBlocking {
        var ui = "base"
        val failure = IllegalStateException("disk full")
        var handled: Throwable? = null

        val result = runOptimisticWrite(
            applyOptimistic = {
                ui = "optimistic"
                "optimistic"
            },
            persist = { throw failure },
            rollback = { ui = "base" },
            onError = { handled = it },
        )

        assertFalse(result.success)
        assertSame(failure, result.error)
        assertEquals("base", ui)
        assertSame(failure, handled)
    }

    @Test
    fun cancellationPropagatesWithoutRollback() {
        var ui = "base"
        var rolledBack = false
        val cancellation = CancellationException("scope cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                runOptimisticWrite(
                    applyOptimistic = {
                        ui = "optimistic"
                        "optimistic"
                    },
                    persist = { throw cancellation },
                    rollback = {
                        rolledBack = true
                        ui = "base"
                    },
                )
            }
        }

        assertSame(cancellation, thrown)
        assertFalse(rolledBack)
        // Cancel mid-persist leaves optimistic state; caller owns structured cleanup.
        assertEquals("optimistic", ui)
    }

    @Test
    fun applyRunsBeforePersist() = runBlocking {
        val order = mutableListOf<String>()

        runOptimisticWrite(
            applyOptimistic = {
                order += "apply"
                1
            },
            persist = { order += "persist" },
            rollback = { order += "rollback" },
        )

        assertEquals(listOf("apply", "persist"), order)
    }

    @Test
    fun coordinatorStaleFailureDoesNotRollbackNewerState() = runBlocking {
        val coordinator = OptimisticWriteCoordinator()
        var ui = 0
        var rollbackCount = 0
        var errorCount = 0

        // First write applies 1, then we simulate a newer write before first persist finishes.
        val first = coordinator.run(
            applyOptimistic = {
                ui = 1
                1
            },
            persist = {
                // Newer generation owns UI now.
                coordinator.bumpGeneration()
                ui = 2
                throw IllegalStateException("first write failed late")
            },
            rollback = {
                rollbackCount++
                ui = 0
            },
            onError = { errorCount++ },
        )

        assertFalse(first.success)
        assertEquals(0, rollbackCount)
        assertEquals(0, errorCount)
        assertEquals(2, ui)
    }

    @Test
    fun coordinatorCurrentFailureStillRollsBack() = runBlocking {
        val coordinator = OptimisticWriteCoordinator()
        var ui = 0
        val failure = IllegalStateException("persist failed")

        val result = coordinator.run(
            applyOptimistic = {
                ui = 1
                1
            },
            persist = { throw failure },
            rollback = { ui = 0 },
        )

        assertFalse(result.success)
        assertSame(failure, result.error)
        assertEquals(0, ui)
    }

    @Test
    fun foldOverlaysAppliesLeftToRight() {
        val result = foldOverlays(0, listOf({ it + 1 }, { it * 10 }, { it + 2 }))
        assertEquals(12, result)
    }

    @Test
    fun foldOverlaysEmptyReturnsBase() {
        assertEquals("base", foldOverlays("base", emptyList()))
    }
}
