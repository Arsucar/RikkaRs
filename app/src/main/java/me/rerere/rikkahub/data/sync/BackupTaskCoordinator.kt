package me.rerere.rikkahub.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupOperation {
    WEB_DAV_BACKUP, WEB_DAV_RESTORE, S3_BACKUP, S3_RESTORE, LOCAL_EXPORT, LOCAL_IMPORT,
}

sealed interface BackupTaskState {
    data object Idle : BackupTaskState
    data object Running : BackupTaskState
    data object Success : BackupTaskState
    data class Failed(val error: Throwable) : BackupTaskState
    data object Cancelled : BackupTaskState
}

class BackupTaskCoordinator(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<BackupOperation, Job>()
    private val _states = MutableStateFlow(
        BackupOperation.entries.associateWith<BackupOperation, BackupTaskState> { BackupTaskState.Idle }
    )
    val states: StateFlow<Map<BackupOperation, BackupTaskState>> = _states.asStateFlow()

    @Synchronized
    fun start(operation: BackupOperation, block: suspend () -> Unit): Boolean {
        if (jobs[operation]?.isActive == true) return false
        _states.update { it + (operation to BackupTaskState.Running) }
        jobs[operation] = scope.launch {
            try {
                block()
                _states.update { it + (operation to BackupTaskState.Success) }
            } catch (error: CancellationException) {
                _states.update { it + (operation to BackupTaskState.Cancelled) }
                throw error
            } catch (error: Exception) {
                _states.update { it + (operation to BackupTaskState.Failed(error)) }
            } finally {
                synchronized(this@BackupTaskCoordinator) { jobs.remove(operation) }
            }
        }
        return true
    }

    @Synchronized
    fun cancel(operation: BackupOperation): Boolean {
        val job = jobs[operation]?.takeIf { it.isActive } ?: return false
        job.cancel()
        return true
    }

    @Synchronized
    fun consumeSuccess(operation: BackupOperation): Boolean {
        if (_states.value[operation] !is BackupTaskState.Success) return false
        _states.update { it + (operation to BackupTaskState.Idle) }
        return true
    }
}
