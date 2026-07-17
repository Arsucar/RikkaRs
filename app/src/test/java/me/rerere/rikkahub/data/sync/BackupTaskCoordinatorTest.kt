package me.rerere.rikkahub.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTaskCoordinatorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val coordinator = BackupTaskCoordinator(scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun stateRemainsObservableAfterCallerScopeWouldLeave() = runBlocking {
        val release = CompletableDeferred<Unit>()
        coordinator.start(BackupOperation.LOCAL_EXPORT) { release.await() }

        assertEquals(BackupTaskState.Running, coordinator.states.value[BackupOperation.LOCAL_EXPORT])
        release.complete(Unit)
        yield()

        assertEquals(BackupTaskState.Success, coordinator.states.value[BackupOperation.LOCAL_EXPORT])
    }

    @Test
    fun duplicateOperationIsRejectedWhileRunning() {
        val release = CompletableDeferred<Unit>()
        assertTrue(coordinator.start(BackupOperation.S3_BACKUP) { release.await() })
        assertFalse(coordinator.start(BackupOperation.S3_BACKUP) { })
        release.complete(Unit)
    }

    @Test
    fun cancellationHasDedicatedTerminalState() = runBlocking {
        coordinator.start(BackupOperation.WEB_DAV_BACKUP) { CompletableDeferred<Unit>().await() }
        assertTrue(coordinator.cancel(BackupOperation.WEB_DAV_BACKUP))
        yield()

        assertEquals(BackupTaskState.Cancelled, coordinator.states.value[BackupOperation.WEB_DAV_BACKUP])
    }

    @Test
    fun failureIsRetained() = runBlocking {
        val failure = IllegalStateException("broken")
        coordinator.start(BackupOperation.LOCAL_IMPORT) { throw failure }
        yield()

        val state = coordinator.states.value[BackupOperation.LOCAL_IMPORT]
        assertTrue(state is BackupTaskState.Failed)
        assertSame(failure, (state as BackupTaskState.Failed).error)
    }

    @Test
    fun successCanBeConsumedOnce() = runBlocking {
        coordinator.start(BackupOperation.LOCAL_EXPORT) { }
        yield()

        assertTrue(coordinator.consumeSuccess(BackupOperation.LOCAL_EXPORT))
        assertFalse(coordinator.consumeSuccess(BackupOperation.LOCAL_EXPORT))
        assertEquals(BackupTaskState.Idle, coordinator.states.value[BackupOperation.LOCAL_EXPORT])
    }
}
