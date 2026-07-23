package me.rerere.rikkahub.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class BackupOperation {
    WEB_DAV_BACKUP, WEB_DAV_RESTORE, S3_BACKUP, S3_RESTORE, LOCAL_EXPORT, LOCAL_IMPORT,
}

enum class BackupTaskStage {
    PREPARING, TRANSFERRING, WRITING, RESTORING,
}

sealed interface BackupTaskState {
    data object Idle : BackupTaskState
    data class Running(val stage: BackupTaskStage) : BackupTaskState
    data object Success : BackupTaskState
    data class Failed(val error: Throwable) : BackupTaskState
    data object Cancelled : BackupTaskState
}

class BackupTaskTimeoutException(operation: BackupOperation) : IllegalStateException(
    "Backup operation $operation timed out"
)

class BackupTaskContext internal constructor(
    private val updateStage: (BackupTaskStage) -> Unit,
) {
    fun updateStage(stage: BackupTaskStage) = updateStage.invoke(stage)
}

class BackupTaskCoordinator(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val jobs = mutableMapOf<BackupOperation, Job>()
    private val _states = MutableStateFlow(
        BackupOperation.entries.associateWith<BackupOperation, BackupTaskState> { BackupTaskState.Idle }
    )
    val states: StateFlow<Map<BackupOperation, BackupTaskState>> = _states.asStateFlow()

    @Synchronized
    fun start(
        operation: BackupOperation,
        initialStage: BackupTaskStage = BackupTaskStage.PREPARING,
        block: suspend BackupTaskContext.() -> Unit,
    ): Boolean {
        if (jobs[operation]?.isActive == true) return false

        if (!scope.isActive) {
            updateState(operation, BackupTaskState.Cancelled)
            return false
        }

        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext[Job]
            try {
                withTimeout(timeoutMillis) {
                    BackupTaskContext { stage ->
                        val state = states.value[operation]
                        if (state is BackupTaskState.Running) {
                            updateState(operation, BackupTaskState.Running(stage))
                        }
                    }.block()
                }
                updateState(operation, BackupTaskState.Success)
            } catch (_: TimeoutCancellationException) {
                updateState(operation, BackupTaskState.Failed(BackupTaskTimeoutException(operation)))
            } catch (error: CancellationException) {
                updateState(operation, BackupTaskState.Cancelled)
                throw error
            } catch (error: Throwable) {
                updateState(operation, BackupTaskState.Failed(error))
                if (error is Error) throw error
            } finally {
                synchronized(this@BackupTaskCoordinator) {
                    if (jobs[operation] === currentJob) jobs.remove(operation)
                }
            }
        }

        jobs[operation] = job
        updateState(operation, BackupTaskState.Running(initialStage))
        if (!job.start()) {
            jobs.remove(operation)
            updateState(operation, BackupTaskState.Cancelled)
            return false
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

    private fun updateState(operation: BackupOperation, state: BackupTaskState) {
        _states.update { it + (operation to state) }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15 * 60 * 1000L
    }
}
