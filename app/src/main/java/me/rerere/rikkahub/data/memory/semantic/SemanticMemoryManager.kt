// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import me.rerere.rikkahub.data.repository.MemoryRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val TAG = "SemanticMemoryManager"

@Serializable
private data class ExportEntry(
    val id: Int = 0,
    val assistantId: String,
    val content: String,
    val summary: String = "",
    val importance: Int = 3,
    val isCore: Boolean = false,
    val embedding: String? = null,
    val embeddingModel: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRecalledAt: Long? = null,
    val recallCount: Int = 0,
    val sourceConversationId: String? = null,
)

@Serializable
private data class ExportData(
    val version: Int = 1,
    val exportedAt: Long,
    val memories: List<ExportEntry>,
)

/** Orchestrates auto/manual summarize, import/export, migration, eviction. */
class SemanticMemoryManager(
    private val summarizer: MemorySummarizer,
    private val repository: SemanticMemoryRepository,
    private val embeddingService: EmbeddingService,
    private val recallService: RecallService,
    private val oldMemoryRepository: MemoryRepository,
    private val settingsStore: SettingsStore,
    private val json: Json,
) {
    private val summarizeLocks = ConcurrentHashMap<String, Mutex>()
    private val migrateMutex = Mutex()

    private fun summarizeMutex(assistantId: String): Mutex =
        summarizeLocks.getOrPut(assistantId) { Mutex() }

    suspend fun checkAndSummarize(
        messages: List<UIMessage>,
        assistantId: String,
        conversationId: String,
        assistantSemanticEnabled: Boolean,
    ): SummarizeResult? = withContext(Dispatchers.IO) {
        summarizeMutex(assistantId).withLock {
            val settings = settingsStore.settingsFlow.value
            val config = settings.semanticMemoryConfig
            if (!config.enabled || !assistantSemanticEnabled) {
                Log.d(TAG, "checkAndSummarize: disabled, skipping")
                return@withLock null
            }
            if (!config.autoSummarizeEnabled) {
                Log.d(TAG, "checkAndSummarize: auto-summarize disabled")
                return@withLock null
            }

            val state = repository.getState(assistantId, conversationId)
            val lastCount = state?.lastSummarizedMessageCount ?: 0
            val currentCount = messages.count { it.role == MessageRole.USER }
            // Cursor is per-conversation; still guard against conversation rewrites / forks.
            val effectiveLastCount = if (currentCount < lastCount) 0 else lastCount
            val turnsSinceLast = currentCount - effectiveLastCount
            Log.i(
                TAG,
                "checkAndSummarize: conv=$conversationId userMsgCount=$currentCount last=$lastCount " +
                    "effective=$effectiveLastCount turns=$turnsSinceLast interval=${config.summarizeInterval}",
            )

            if (turnsSinceLast < config.summarizeInterval) {
                return@withLock null
            }

            val newMsgCount = (turnsSinceLast * 2 + 4).coerceAtMost(config.autoSummarizeMessageCount)
            val messagesToSummarize = messages.takeLast(newMsgCount)
            Log.i(TAG, "checkAndSummarize: triggering with ${messagesToSummarize.size} messages")

            SemanticMemoryStats.update { it.copy(summarizeStatus = SummarizeStatus.RUNNING) }
            val result = summarizer.summarize(
                messagesToSummarize,
                assistantId,
                conversationId,
                settings,
                config,
            )

            if (result.errors.isEmpty()) {
                repository.updateState(assistantId, conversationId, currentCount)
                SemanticMemoryStats.update {
                    it.copy(
                        summarizeStatus = SummarizeStatus.SUCCESS,
                        lastAutoSummarizeAt = System.currentTimeMillis(),
                        lastAutoSummarizeNew = result.newMemories,
                        lastAutoSummarizeUpdated = result.updatedMemories,
                        lastAutoSummarizeError = null,
                        autoSummarizeCount = it.autoSummarizeCount + 1,
                    )
                }
            } else {
                Log.w(TAG, "checkAndSummarize: errors ${result.errors}")
                SemanticMemoryStats.update {
                    it.copy(
                        summarizeStatus = SummarizeStatus.FAILED,
                        lastAutoSummarizeError = result.errors.firstOrNull(),
                    )
                }
            }
            result
        }
    }

    suspend fun summarizeNow(
        messages: List<UIMessage>,
        assistantId: String,
        conversationId: String,
    ): SummarizeResult = withContext(Dispatchers.IO) {
        summarizeMutex(assistantId).withLock {
            Log.i(TAG, "summarizeNow: manual for $assistantId, ${messages.size} messages")
            val settings = settingsStore.settingsFlow.value
            val config = settings.semanticMemoryConfig

            SemanticMemoryStats.update { it.copy(summarizeStatus = SummarizeStatus.RUNNING) }
            val result = summarizer.summarize(messages, assistantId, conversationId, settings, config)

            if (result.errors.isEmpty()) {
                val currentCount = messages.count { it.role == MessageRole.USER }
                repository.updateState(assistantId, conversationId, currentCount)
                SemanticMemoryStats.update { it.copy(summarizeStatus = SummarizeStatus.SUCCESS) }
            } else {
                SemanticMemoryStats.update { it.copy(summarizeStatus = SummarizeStatus.FAILED) }
            }
            result
        }
    }

    suspend fun exportToFile(filePath: String) = withContext(Dispatchers.IO) {
        val memories = repository.getAllMemories()
        val export = ExportData(
            exportedAt = System.currentTimeMillis(),
            memories = memories.map { it.toExportEntry() },
        )
        val jsonStr = json.encodeToString(ExportData.serializer(), export)
        File(filePath).outputStream().use { fos ->
            GZIPOutputStream(fos).use { it.write(jsonStr.toByteArray()) }
        }
        Log.i(TAG, "exportToFile: ${memories.size} memories to $filePath")
    }

    suspend fun importFromFile(filePath: String): Int = withContext(Dispatchers.IO) {
        val jsonStr = File(filePath).inputStream().use { fis ->
            GZIPInputStream(fis).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val export = json.decodeFromString(ExportData.serializer(), jsonStr)
        val entities = export.memories.map { it.toEntity() }
        repository.replaceAllMemories(entities)
        recallService.invalidateAll()
        Log.i(TAG, "importFromFile: imported ${entities.size} memories")
        entities.size
    }

    /**
     * Idempotent migration from classic memory rows.
     * Skips content already present for the assistant; skips `__global__` / GLOBAL-owned rows
     * (semantic recall is per-assistant and would never match real assistant UUIDs).
     */
    suspend fun migrateAllOldMemories(): Int = withContext(Dispatchers.IO) {
        migrateMutex.withLock {
            val settings = settingsStore.settingsFlow.value
            val config = settings.semanticMemoryConfig
            val allOldMemories = oldMemoryRepository.getAllMemoriesRaw()
            if (allOldMemories.isEmpty()) {
                Log.i(TAG, "migrateAllOldMemories: nothing to migrate")
                return@withLock 0
            }

            val grouped = allOldMemories.groupBy { it.assistantId }
            var totalMigrated = 0
            var skippedGlobal = 0
            for ((aid, memories) in grouped) {
                if (isGlobalMemoryOwner(aid)) {
                    skippedGlobal += memories.size
                    Log.i(
                        TAG,
                        "migrateAllOldMemories: skipping global/unscoped rows for owner=$aid " +
                            "count=${memories.size}",
                    )
                    continue
                }

                val existingContents = repository.getMemories(aid)
                    .map { it.content.trim() }
                    .toHashSet()
                val toMigrate = memories.filter { it.content.trim() !in existingContents }
                if (toMigrate.isEmpty()) {
                    Log.i(TAG, "migrateAllOldMemories: assistant=$aid already up to date")
                    continue
                }

                val contents = toMigrate.map { it.content }
                val embeddingResult = embeddingService.embed(contents, settings, config)
                val embeddings: List<List<Float>?> = if (embeddingResult.isSuccess) {
                    embeddingResult.getOrThrow()
                } else {
                    contents.map { null }
                }
                val embeddingModelLabel = EmbeddingService.resolveEmbeddingModelLabel(settings, config)
                val entities = toMigrate.mapIndexed { index, mem ->
                    EpisodicMemoryEntity(
                        assistantId = aid,
                        content = mem.content,
                        summary = "",
                        importance = 3,
                        isCore = false,
                        embedding = embeddings[index]?.let {
                            EmbeddingService.encodeEmbedding(it.toFloatArray())
                        },
                        embeddingModel = embeddings[index]?.let { embeddingModelLabel },
                    )
                }
                repository.addMemories(entities)
                recallService.invalidateCache(aid)
                totalMigrated += entities.size
            }
            Log.i(
                TAG,
                "migrateAllOldMemories: total $totalMigrated (skippedGlobal=$skippedGlobal)",
            )
            totalMigrated
        }
    }

    suspend fun getEvictionCandidates(
        assistantId: String,
        limit: Int = 50,
    ): List<EpisodicMemoryEntity> = withContext(Dispatchers.IO) {
        val config = settingsStore.settingsFlow.value.semanticMemoryConfig
        val count = repository.getMemoryCount(assistantId)
        if (count <= config.maxMemoriesPerAssistant) return@withContext emptyList()
        val toEvict = count - config.maxMemoriesPerAssistant
        repository.getMemories(assistantId)
            .filter { !it.isCore && it.importance <= 3 }
            .sortedWith(compareBy(nullsLast()) { it.lastRecalledAt ?: it.createdAt })
            .take(minOf(toEvict, limit))
    }

    private fun EpisodicMemoryEntity.toExportEntry() = ExportEntry(
        id = id,
        assistantId = assistantId,
        content = content,
        summary = summary,
        importance = importance,
        isCore = isCore,
        embedding = embedding,
        embeddingModel = embeddingModel,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastRecalledAt = lastRecalledAt,
        recallCount = recallCount,
        sourceConversationId = sourceConversationId,
    )

    private fun ExportEntry.toEntity() = EpisodicMemoryEntity(
        id = 0,
        assistantId = assistantId,
        content = content,
        summary = summary,
        importance = importance,
        isCore = isCore,
        embedding = embedding,
        embeddingModel = embeddingModel,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastRecalledAt = lastRecalledAt,
        recallCount = recallCount,
        sourceConversationId = sourceConversationId,
    )

    companion object {
        fun isGlobalMemoryOwner(assistantId: String): Boolean {
            val id = assistantId.trim()
            return id.isEmpty() ||
                id.equals(MemoryRepository.GLOBAL_MEMORY_ID, ignoreCase = true) ||
                id.equals("GLOBAL", ignoreCase = true)
        }

        /** Contents already present for an assistant (trim-normalized). */
        fun filterNewMigrationContents(
            existingContents: Set<String>,
            candidateContents: List<String>,
        ): List<String> {
            val existing = existingContents.map { it.trim() }.toHashSet()
            return candidateContents.filter { it.trim() !in existing }
        }
    }
}
