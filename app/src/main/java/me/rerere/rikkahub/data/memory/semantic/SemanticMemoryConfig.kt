// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Semantic memory plugin config.
 *
 * Embedding uses a provider [ModelType.EMBEDDING] selected via [embeddingModelId]
 * (same ModelSelector pattern as [summarizeModelId]), not independent API credentials.
 */
/** Default max core memories injected into the system prompt. */
const val DEFAULT_SEMANTIC_MAX_CORE_INJECT = 20

/** Default max characters for the injected memory body (excluding wrapper tags). */
const val DEFAULT_SEMANTIC_MAX_INJECT_CHARS = 6_000

/** Default max characters per single memory line after sanitize. */
const val DEFAULT_SEMANTIC_MAX_MEMORY_CONTENT_LEN = 500

@Serializable
data class SemanticMemoryConfig(
    val enabled: Boolean = false,
    /** Provider model UUID with [me.rerere.ai.provider.ModelType.EMBEDDING]; null = not configured. */
    val embeddingModelId: Uuid? = null,
    val topK: Int = 10,
    val similarityThreshold: Float = 0.3f,
    val enableFallbackKeyword: Boolean = true,
    val summarizeInterval: Int = 50,
    val autoSummarizeEnabled: Boolean = true,
    val autoSummarizeMessageCount: Int = 20,
    val maxMemoriesPerSummary: Int = 5,
    val summarizeModelId: Uuid? = null,
    val summarizePrompt: String? = null,
    val maxMemoriesPerAssistant: Int = 2000,
    val autoEvictionEnabled: Boolean = true,
    /**
     * Max core memories injected into system prompt.
     * `null` = no cap (all cores from recall).
     */
    val maxCoreInject: Int? = DEFAULT_SEMANTIC_MAX_CORE_INJECT,
    /**
     * Max characters for injected memory lines (body budget).
     * `null` = no char budget.
     */
    val maxInjectChars: Int? = DEFAULT_SEMANTIC_MAX_INJECT_CHARS,
    /**
     * Max characters per memory content line after sanitization.
     * `null` = no per-item truncate.
     */
    val maxMemoryContentLen: Int? = DEFAULT_SEMANTIC_MAX_MEMORY_CONTENT_LEN,
)

enum class SummarizeStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
}

/**
 * Runtime debug counters. StateFlow-backed so Settings UI recomposes (AC14).
 */
data class SemanticMemoryStatsSnapshot(
    val embeddingCallCount: Int = 0,
    val embeddingSuccessCount: Int = 0,
    val embeddingFailCount: Int = 0,
    val lastEmbeddingError: String? = null,
    val lastEmbeddingDim: Int? = null,
    val lastRecallCount: Int = 0,
    val lastRecallQuery: String? = null,
    val totalMemories: Int = 0,
    val lastAutoSummarizeAt: Long = 0,
    val lastAutoSummarizeNew: Int = 0,
    val lastAutoSummarizeUpdated: Int = 0,
    val lastAutoSummarizeError: String? = null,
    val autoSummarizeCount: Int = 0,
    val summarizeStatus: SummarizeStatus = SummarizeStatus.IDLE,
    val lastRecallAt: Long = 0,
    val lastRecallFallback: Boolean = false,
    val recallCount: Int = 0,
)

object SemanticMemoryStats {
    private val _state = MutableStateFlow(SemanticMemoryStatsSnapshot())
    val state: StateFlow<SemanticMemoryStatsSnapshot> = _state.asStateFlow()

    val snapshot: SemanticMemoryStatsSnapshot get() = _state.value

    fun update(block: (SemanticMemoryStatsSnapshot) -> SemanticMemoryStatsSnapshot) {
        _state.update(block)
    }

    fun reset() {
        _state.value = SemanticMemoryStatsSnapshot()
    }
}
