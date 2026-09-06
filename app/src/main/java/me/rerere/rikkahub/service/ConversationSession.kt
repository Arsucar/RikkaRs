package me.rerere.rikkahub.service

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

private const val TAG = "ConversationSession"
private const val IDLE_TIMEOUT_MS = 5_000L

data class ConversationStateSnapshot(
    val conversation: Conversation,
    val revision: Long,
)

class ConversationSession(
    val id: Uuid,
    initial: Conversation,
    private val scope: CoroutineScope,
    private val onIdle: (Uuid) -> Unit,
    private val onGenerationFinished: (Uuid, Throwable?) -> Unit = { _, _ -> },
) {
    val state = MutableStateFlow(initial)
    val messageQueue = MessageQueue()

    // 从队列取出到写入会话历史之间，附件仍需作为有效引用保留。
    @Volatile
    var submittingMessage: QueuedMessage? = null
        internal set

    private val stateLock = Any()
    private val stateRevision = AtomicLong(0L)

    /** Serializes durable conversation writes without blocking in-memory streaming updates. */
    val persistenceMutex = Mutex()

    /**
     * #220: last tool-step index successfully written as a mid-generation checkpoint for this
     * in-memory generation. Reset to -1 when a generation starts or a Final snapshot is saved.
     */
    @Volatile
    var lastCheckpointStep: Int = -1

    /** Shared by manual and automatic compression for this conversation only. */
    internal val compressionCoordinator = ConversationCompressionCoordinator()

    private val autoCompressionLock = Any()
    private var autoCompressionState = AutoCompressionRuntimeState()

    private val refCount = AtomicInteger(0)

    val processingStatus = MutableStateFlow<String?>(null)

    private val _generationJob = MutableStateFlow<Job?>(null)
    private val activeJobs = mutableSetOf<Job>()
    val generationJob: StateFlow<Job?> = _generationJob.asStateFlow()
    val isGenerating: Boolean get() = _generationJob.value?.isActive == true
    val isInUse: Boolean
        get() = refCount.get() > 0 || _generationJob.value != null ||
                messageQueue.state.value.messages.isNotEmpty()

    private var idleCheckJob: Job? = null

    fun acquire(): Int = refCount.incrementAndGet().also {
        cancelIdleCheck()
        Log.d(TAG, "acquire $id (refs=$it)")
    }

    fun release(): Int = refCount.decrementAndGet().also {
        Log.d(TAG, "release $id (refs=$it)")
        if (it <= 0) scheduleIdleCheck()
    }

    inline fun <T> withRef(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    suspend inline fun <T> withRefSuspend(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    @Synchronized
    fun setJob(job: Job?, cancelPrevious: Boolean = true) {
        val previous = _generationJob.value
        _generationJob.value = job
        if (cancelPrevious) previous?.cancel()
        if (job != null) activeJobs.add(job)
        job?.invokeOnCompletion { cause ->
            synchronized(this) {
                activeJobs.remove(job)
                // Also propagate cancellation when a queued coroutine never entered its body.
                if (!cancelPrevious && cause is CancellationException) previous?.cancel()
                // A replaced job must not clear or advance its successor.
                if (_generationJob.compareAndSet(job, null)) {
                    onGenerationFinished(id, cause)
                    if (refCount.get() <= 0) scheduleIdleCheck()
                }
            }
        }
        job?.start()
    }

    fun getJob(): Job? = _generationJob.value

    @Synchronized
    fun cancelJobs(): List<Job> = activeJobs.toList().also { jobs ->
        // Cancel waiters first so a predecessor finishing cannot start the next approval.
        jobs.asReversed().forEach { it.cancel() }
    }

    fun snapshotState(): ConversationStateSnapshot = synchronized(stateLock) {
        ConversationStateSnapshot(
            conversation = state.value,
            revision = stateRevision.get(),
        )
    }

    fun replaceState(newState: Conversation): Conversation? = synchronized(stateLock) {
        if (newState.id != id) return@synchronized null
        val previous = state.value
        state.value = newState
        stateRevision.incrementAndGet()
        previous
    }

    fun updateState(transform: (Conversation) -> Conversation): Pair<Conversation, Conversation>? =
        synchronized(stateLock) {
            val previous = state.value
            val updated = transform(previous)
            if (updated.id != id) return@synchronized null
            state.value = updated
            stateRevision.incrementAndGet()
            previous to updated
        }

    fun matchesSnapshot(snapshot: ConversationStateSnapshot): Boolean = synchronized(stateLock) {
        stateRevision.get() == snapshot.revision && state.value == snapshot.conversation
    }

    fun compareAndSetState(
        snapshot: ConversationStateSnapshot,
        newState: Conversation,
    ): Conversation? = synchronized(stateLock) {
        if (
            newState.id != id ||
            stateRevision.get() != snapshot.revision ||
            state.value != snapshot.conversation
        ) {
            return@synchronized null
        }
        val previous = state.value
        state.value = newState
        stateRevision.incrementAndGet()
        previous
    }

    fun evaluateAutoCompression(input: AutoCompressionPolicyInput): AutoCompressionEvaluation =
        synchronized(autoCompressionLock) {
            evaluateAutoCompression(input, autoCompressionState).also { evaluation ->
                if (evaluation.decision != AutoCompressionDecision.Trigger) {
                    autoCompressionState = evaluation.nextState
                }
            }
        }

    fun commitAutoCompressionTrigger(evaluation: AutoCompressionEvaluation): Boolean =
        synchronized(autoCompressionLock) {
            if (
                evaluation.decision != AutoCompressionDecision.Trigger ||
                autoCompressionState != evaluation.previousState
            ) {
                return@synchronized false
            }
            autoCompressionState = evaluation.nextState
            true
        }

    fun recordAutoCompressionFailure(visibleMessageCount: Int) {
        synchronized(autoCompressionLock) {
            autoCompressionState = autoCompressionState.withFailureAt(visibleMessageCount)
        }
    }

    fun observeAutoCompressionPreparedInput(
        config: AutoCompressionConfig,
        promptTokens: Int,
    ) {
        synchronized(autoCompressionLock) {
            if (!config.enabled) {
                autoCompressionState = AutoCompressionRuntimeState(configSignature = config.signature)
                return@synchronized
            }
            val lowWatermark = calculateAutoCompressionLowWatermark(
                thresholdTokens = config.thresholdTokens,
                targetTokens = config.targetTokens,
            ) ?: return@synchronized
            if (autoCompressionState.configSignature != config.signature) {
                autoCompressionState = AutoCompressionRuntimeState(configSignature = config.signature)
            }
            if (promptTokens <= lowWatermark) {
                autoCompressionState = autoCompressionState.copy(
                    armed = true,
                    lastAttemptFingerprint = null,
                    failedVisibleMessageCount = null,
                )
            }
        }
    }

    internal fun autoCompressionStateForTest(): AutoCompressionRuntimeState =
        synchronized(autoCompressionLock) { autoCompressionState }

    private fun scheduleIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (refCount.get() <= 0 && !isGenerating) {
                onIdle(id)
            }
        }
    }

    private fun cancelIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    @Synchronized
    fun cleanup() {
        _generationJob.value = null
        cancelJobs()
        idleCheckJob?.cancel()
        idleCheckJob = null
        synchronized(autoCompressionLock) {
            autoCompressionState = AutoCompressionRuntimeState()
        }
    }
}

/** Serialize approval saves without cancelling earlier decisions; stopping cancels the whole chain. */
internal suspend fun afterPreviousGeneration(previous: Job?, block: suspend () -> Unit) {
    try {
        previous?.join()
        block()
    } catch (e: CancellationException) {
        previous?.cancel()
        withContext(NonCancellable) { previous?.join() }
        throw e
    }
}
