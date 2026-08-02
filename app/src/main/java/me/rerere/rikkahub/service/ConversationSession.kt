package me.rerere.rikkahub.service

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
) {
    val state = MutableStateFlow(initial)

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
    val generationJob: StateFlow<Job?> = _generationJob.asStateFlow()
    val isGenerating: Boolean get() = _generationJob.value?.isActive == true
    val isInUse: Boolean get() = refCount.get() > 0 || isGenerating

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

    fun setJob(job: Job?) {
        val previous = replaceGenerationJob(job)
        job?.invokeOnCompletion {
            if (_generationJob.compareAndSet(job, null) && refCount.get() <= 0) {
                scheduleIdleCheck()
            }
        }
        previous?.cancel()
        if (job == null && refCount.get() <= 0) {
            scheduleIdleCheck()
        }
    }

    private fun replaceGenerationJob(job: Job?): Job? {
        while (true) {
            val previous = _generationJob.value
            if (_generationJob.compareAndSet(previous, job)) return previous
        }
    }

    fun getJob(): Job? = _generationJob.value

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

    fun cleanup() {
        replaceGenerationJob(null)?.cancel()
        idleCheckJob?.cancel()
        idleCheckJob = null
        synchronized(autoCompressionLock) {
            autoCompressionState = AutoCompressionRuntimeState()
        }
    }
}
