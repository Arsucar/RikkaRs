// [SemanticMemory Plugin]
package me.rerere.rikkahub.ui.pages.setting

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import me.rerere.rikkahub.data.memory.semantic.EmbeddingService
import me.rerere.rikkahub.data.memory.semantic.RecallService
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryManager
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryRepository
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryStats
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryStatsSnapshot
import me.rerere.rikkahub.data.memory.semantic.SummarizeResult
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.uuid.Uuid

private const val TAG = "SemanticMemoryVM"

class SemanticMemoryVM(
    private val settingsStore: SettingsStore,
    private val embeddingService: EmbeddingService,
    private val semanticMemoryManager: SemanticMemoryManager,
    private val repository: SemanticMemoryRepository,
    private val recallService: RecallService,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true))

    val stats: StateFlow<SemanticMemoryStatsSnapshot> = SemanticMemoryStats.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SemanticMemoryStats.snapshot)

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _lastSummarizeDetail = MutableStateFlow<SummarizeResult?>(null)
    val lastSummarizeDetail: StateFlow<SummarizeResult?> = _lastSummarizeDetail

    private val _selectedAssistantId = MutableStateFlow<String?>(null)
    val selectedAssistantId: StateFlow<String?> = _selectedAssistantId

    val memoriesFlow: Flow<List<EpisodicMemoryEntity>> = _selectedAssistantId
        .flatMapLatest { assistantId ->
            if (assistantId != null) {
                repository.getMemoriesFlow(assistantId)
            } else {
                flowOf(emptyList())
            }
        }

    private val _evictionCandidates = MutableStateFlow<List<EpisodicMemoryEntity>>(emptyList())
    val evictionCandidates: StateFlow<List<EpisodicMemoryEntity>> = _evictionCandidates

    fun selectAssistant(assistantId: String?) {
        _selectedAssistantId.value = assistantId
    }

    fun updateConfig(config: SemanticMemoryConfig) {
        viewModelScope.launch {
            val current = settings.value
            if (current.init) return@launch
            settingsStore.update(current.copy(semanticMemoryConfig = config))
        }
    }

    fun testConnection(config: SemanticMemoryConfig) {
        viewModelScope.launch {
            _isProcessing.value = true
            _testResult.value = null
            val currentSettings = settings.value
            val result = embeddingService.testConnection(currentSettings, config)
            _testResult.value = result.fold(
                onSuccess = { "OK: dim=${it.dimensions}, model=${it.model}" },
                onFailure = { "Failed: ${it.message ?: "unknown"}" },
            )
            _isProcessing.value = false
        }
    }

    fun exportData(filePath: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _message.value = null
            runCatching {
                semanticMemoryManager.exportToFile(filePath)
                _message.value = "Export OK"
            }.onFailure {
                _message.value = "Export failed: ${it.message}"
            }
            _isProcessing.value = false
        }
    }

    fun importData(filePath: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _message.value = null
            runCatching {
                val count = semanticMemoryManager.importFromFile(filePath)
                _message.value = "Imported $count memories"
            }.onFailure {
                _message.value = "Import failed: ${it.message}"
            }
            _isProcessing.value = false
        }
    }

    fun migrateAllOldMemories() {
        viewModelScope.launch {
            _isProcessing.value = true
            _message.value = null
            runCatching {
                val count = semanticMemoryManager.migrateAllOldMemories()
                _message.value = if (count > 0) "Migrated $count memories" else "No old memories"
            }.onFailure {
                _message.value = "Migrate failed: ${it.message}"
            }
            _isProcessing.value = false
        }
    }

    fun triggerSummarize(assistantId: String, messageCount: Int) {
        viewModelScope.launch {
            _isProcessing.value = true
            _message.value = null
            _lastSummarizeDetail.value = null
            runCatching {
                val aid = Uuid.parse(assistantId)
                // getRecentConversations returns light rows (empty nodes); load full conversation.
                val recent = conversationRepository.getRecentConversations(aid, limit = 1)
                if (recent.isEmpty()) {
                    _message.value = "No conversations for this assistant"
                    return@runCatching
                }
                val conversation = conversationRepository.getConversationById(recent.first().id)
                    ?: run {
                        _message.value = "No conversations for this assistant"
                        return@runCatching
                    }
                val allMessages = conversation.currentMessages
                if (allMessages.isEmpty()) {
                    _message.value = "No messages to summarize"
                    return@runCatching
                }
                val toSummarize = allMessages.takeLast(messageCount.coerceIn(1, allMessages.size))
                _message.value = "Summarizing last ${toSummarize.size} messages..."
                val result = semanticMemoryManager.summarizeNow(
                    messages = toSummarize,
                    assistantId = assistantId,
                    conversationId = conversation.id.toString(),
                )
                _lastSummarizeDetail.value = result
                _message.value = buildString {
                    append("Done: +${result.newMemories} new, ~${result.updatedMemories} merged")
                    if (result.errors.isNotEmpty()) {
                        append(" (error: ${result.errors.first()})")
                    }
                }
            }.onFailure {
                _message.value = "Summarize failed: ${it.message}"
                Log.e(TAG, "triggerSummarize failed", it)
            }
            _isProcessing.value = false
        }
    }

    fun testRecall(assistantId: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _message.value = null
            runCatching {
                val currentSettings = settings.value
                val config = currentSettings.semanticMemoryConfig
                val testQuery = "test query: hello, how are you recently?"
                val result = recallService.recall(
                    testQuery,
                    assistantId,
                    currentSettings,
                    config,
                    sideEffects = false,
                )
                val snap = SemanticMemoryStats.snapshot
                _message.value = buildString {
                    appendLine("Recall test:")
                    appendLine("- total memories: ${snap.totalMemories}")
                    appendLine("- recalled: ${result.memories.size}")
                    appendLine("- fallback: ${result.usedFallback}")
                    result.queryEmbeddingDim?.let { appendLine("- embedding dim: $it") }
                    if (result.memories.isNotEmpty()) {
                        appendLine("- samples:")
                        result.memories.take(5).forEach { mem ->
                            appendLine("  ★${mem.importance} ${mem.content.take(50)}")
                        }
                    }
                }
            }.onFailure {
                _message.value = "Recall test failed: ${it.message}"
            }
            _isProcessing.value = false
        }
    }

    fun addMemory(
        assistantId: String,
        content: String,
        summary: String,
        importance: Int,
        isCore: Boolean,
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            runCatching {
                val currentSettings = settings.value
                val config = currentSettings.semanticMemoryConfig
                val embeddingResult = embeddingService.embed(content, currentSettings, config)
                val embedding = embeddingResult.getOrNull()
                val embeddingModelLabel = EmbeddingService.resolveEmbeddingModelLabel(currentSettings, config)
                repository.addMemory(
                    EpisodicMemoryEntity(
                        assistantId = assistantId,
                        content = content,
                        summary = summary,
                        importance = importance.coerceIn(2, 5),
                        isCore = isCore,
                        embedding = embedding?.let { EmbeddingService.encodeEmbedding(it.toFloatArray()) },
                        embeddingModel = embedding?.let { embeddingModelLabel },
                    ),
                )
                recallService.invalidateCache(assistantId)
                _message.value = if (embedding == null) {
                    "Saved without embedding (API failed)"
                } else {
                    "Memory added"
                }
            }.onFailure {
                _message.value = "Add failed: ${it.message}"
            }
            _isProcessing.value = false
        }
    }

    fun updateMemory(memory: EpisodicMemoryEntity, contentChanged: Boolean) {
        viewModelScope.launch {
            _isProcessing.value = true
            runCatching {
                var updated = memory.copy(updatedAt = System.currentTimeMillis())
                if (contentChanged) {
                    val currentSettings = settings.value
                    val config = currentSettings.semanticMemoryConfig
                    val embeddingResult = embeddingService.embed(memory.content, currentSettings, config)
                    val embedding = embeddingResult.getOrNull()
                    val embeddingModelLabel = EmbeddingService.resolveEmbeddingModelLabel(currentSettings, config)
                    updated = if (embedding != null) {
                        updated.copy(
                            embedding = EmbeddingService.encodeEmbedding(embedding.toFloatArray()),
                            embeddingModel = embeddingModelLabel,
                        )
                    } else {
                        updated.copy(embedding = null, embeddingModel = null)
                    }
                    if (embedding == null) {
                        _message.value = "Saved; embedding regenerate failed (keyword fallback)"
                    } else {
                        _message.value = "Memory updated + re-embedded"
                    }
                } else {
                    _message.value = "Memory updated"
                }
                repository.updateMemory(updated)
                recallService.invalidateCache(memory.assistantId)
            }.onFailure {
                _message.value = "Update failed: ${it.message}"
            }
            _isProcessing.value = false
        }
    }

    fun deleteMemory(id: Int, assistantId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteMemory(id)
                recallService.invalidateCache(assistantId)
                _message.value = "Memory deleted"
            }.onFailure {
                _message.value = "Delete failed: ${it.message}"
            }
        }
    }

    fun checkEvictionCandidates(assistantId: String) {
        viewModelScope.launch {
            _evictionCandidates.value = semanticMemoryManager.getEvictionCandidates(assistantId)
        }
    }

    fun confirmEviction(ids: List<Int>, assistantId: String) {
        viewModelScope.launch {
            runCatching {
                ids.forEach { repository.deleteMemory(it) }
                recallService.invalidateCache(assistantId)
                _evictionCandidates.value = emptyList()
                _message.value = "Evicted ${ids.size} memories"
            }.onFailure {
                _message.value = "Eviction failed: ${it.message}"
            }
        }
    }

    fun dismissEviction() {
        _evictionCandidates.value = emptyList()
    }

    fun clearMessage() {
        _message.value = null
        _lastSummarizeDetail.value = null
    }

    fun resetStats() {
        SemanticMemoryStats.reset()
    }

    suspend fun memoryCounts(assistantId: String): Pair<Int, Int> {
        val total = repository.getMemoryCount(assistantId)
        val core = repository.getCoreMemoryCount(assistantId)
        return total to core
    }
}
