// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.sqrt

private const val TAG = "RecallService"

data class RecallResult(
    val memories: List<RecalledMemory>,
    val usedFallback: Boolean,
    val queryEmbeddingDim: Int?,
) {
    val isEmpty get() = memories.isEmpty()
}

data class RecalledMemory(
    val id: Int,
    val content: String,
    val summary: String,
    val importance: Int,
    val isCore: Boolean,
    val score: Float,
)

/** Hybrid semantic recall: cosine + time decay + importance, with keyword fallback. */
class RecallService(
    private val repository: SemanticMemoryRepository,
    private val embeddingService: EmbeddingService,
) {
    private data class CachedEntry(
        val id: Int,
        val content: String,
        val summary: String,
        val importance: Int,
        val isCore: Boolean,
        val embedding: FloatArray?,
        val createdAt: Long,
        val updatedAt: Long,
        val recallCount: Int,
    )

    private data class Cache(val entries: List<CachedEntry>, val fingerprint: Long)

    private val cache = ConcurrentHashMap<String, Cache>()

    fun invalidateCache(assistantId: String) {
        cache.remove(assistantId)
        Log.d(TAG, "invalidateCache: $assistantId")
    }

    fun invalidateAll() {
        cache.clear()
        Log.d(TAG, "invalidateAll")
    }

    /**
     * @param sideEffects when false (preview / read-only), skip durable [updateRecallStats] bumps.
     */
    suspend fun recall(
        query: String,
        assistantId: String,
        settings: Settings,
        config: SemanticMemoryConfig,
        sideEffects: Boolean = true,
    ): RecallResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext RecallResult(emptyList(), false, null)

        val cached = getOrLoadMemories(assistantId)
        if (cached.isEmpty()) {
            Log.i(TAG, "recall: no memories for $assistantId")
            return@withContext RecallResult(emptyList(), false, null)
        }

        SemanticMemoryStats.update { it.copy(totalMemories = cached.size) }

        val coreMemories = cached.filter { it.isCore }
        val embedResult = embeddingService.embed(query, settings, config)

        if (embedResult.isSuccess) {
            val queryEmbedding = embedResult.getOrThrow()
            val dim = queryEmbedding.size
            val queryArr = queryEmbedding.toFloatArray()

            val scored = cached.mapNotNull { entry ->
                val emb = entry.embedding ?: return@mapNotNull null
                if (emb.size != dim) return@mapNotNull null
                val sim = cosineSimilarity(queryArr, emb)
                if (sim < config.similarityThreshold) return@mapNotNull null
                val score = hybridScore(sim, entry.createdAt, entry.importance, entry.isCore)
                ScoredMemory(entry, score)
            }.sortedByDescending { it.score }

            val recalled = scored.take(config.topK)
            val recalledIds = recalled.map { it.entry.id }
            if (sideEffects && recalledIds.isNotEmpty()) {
                repository.updateRecallStats(recalledIds)
            }

            val result = (recalled.map { it.toRecalled() } + coreMemories.map { it.toRecalled() })
                .distinctBy { it.id }

            if (sideEffects) {
                SemanticMemoryStats.update {
                    it.copy(
                        lastRecallCount = result.size,
                        lastRecallQuery = query.take(100),
                        lastRecallAt = System.currentTimeMillis(),
                        lastRecallFallback = false,
                        recallCount = it.recallCount + 1,
                    )
                }
            }

            Log.i(TAG, "recall: semantic success, ${result.size} memories (core=${coreMemories.size})")
            return@withContext RecallResult(result, false, dim)
        } else {
            Log.w(TAG, "recall: embedding failed, falling back to keyword")
            if (!config.enableFallbackKeyword) {
                val result = coreMemories.map { it.toRecalled() }
                return@withContext RecallResult(result, true, null)
            }

            val keywords = extractKeywords(query)
            val scored = cached.map { entry ->
                val matchCount = keywords.count { kw ->
                    entry.content.contains(kw, ignoreCase = true) ||
                        entry.summary.contains(kw, ignoreCase = true)
                }
                val density = if (keywords.isNotEmpty()) matchCount.toFloat() / keywords.size else 0f
                val score = 0.50f * density +
                    0.10f * timeDecay(entry.createdAt) +
                    0.15f * importanceWeight(entry.importance, entry.isCore) +
                    (if (entry.isCore) 0.10f else 0f)
                ScoredMemory(entry, score)
            }.filter { it.score > 0f || it.entry.isCore }
                .sortedByDescending { it.score }

            val recalled = scored.take(config.topK)
            val recalledIds = recalled.map { it.entry.id }
            if (sideEffects && recalledIds.isNotEmpty()) {
                repository.updateRecallStats(recalledIds)
            }

            val result = (recalled.map { it.toRecalled() } + coreMemories.map { it.toRecalled() })
                .distinctBy { it.id }

            if (sideEffects) {
                SemanticMemoryStats.update {
                    it.copy(
                        lastRecallCount = result.size,
                        lastRecallQuery = query.take(100),
                        lastRecallAt = System.currentTimeMillis(),
                        lastRecallFallback = true,
                        recallCount = it.recallCount + 1,
                    )
                }
            }
            Log.i(TAG, "recall: keyword fallback, ${result.size} memories")
            return@withContext RecallResult(result, true, null)
        }
    }

    private suspend fun getOrLoadMemories(assistantId: String): List<CachedEntry> {
        val allMemories = repository.getMemories(assistantId)
        val fingerprint = cacheFingerprint(allMemories)
        val cached = cache[assistantId]
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached.entries
        }
        val entries = allMemories.map { it.toCachedEntry() }
        cache[assistantId] = Cache(entries, fingerprint)
        Log.d(TAG, "getOrLoadMemories: loaded ${entries.size} memories for $assistantId")
        return entries
    }

    private fun EpisodicMemoryEntity.toCachedEntry(): CachedEntry {
        val emb = embedding?.let { str ->
            runCatching { EmbeddingService.decodeEmbedding(str) }.getOrNull()
        }
        return CachedEntry(
            id = id,
            content = content,
            summary = summary,
            importance = importance,
            isCore = isCore,
            embedding = emb,
            createdAt = createdAt,
            updatedAt = updatedAt,
            recallCount = recallCount,
        )
    }

    private data class ScoredMemory(val entry: CachedEntry, val score: Float) {
        fun toRecalled() = RecalledMemory(
            id = entry.id,
            content = entry.content,
            summary = entry.summary,
            importance = entry.importance,
            isCore = entry.isCore,
            score = score,
        )
    }

    private fun CachedEntry.toRecalled() = RecalledMemory(
        id = id,
        content = content,
        summary = summary,
        importance = importance,
        isCore = isCore,
        score = if (isCore) 1.0f else 0f,
    )

    companion object {
        fun cacheFingerprint(memories: List<EpisodicMemoryEntity>): Long {
            var h = memories.size.toLong()
            for (m in memories) {
                h = h * 31 + m.id
                h = h * 31 + m.updatedAt
                h = h * 31 + m.recallCount
                h = h * 31 + (m.embedding?.hashCode()?.toLong() ?: 0L)
            }
            return h
        }

        fun hybridScore(
            cosine: Float,
            createdAt: Long,
            importance: Int,
            isCore: Boolean,
            nowMs: Long = System.currentTimeMillis(),
        ): Float {
            return 0.60f * cosine +
                0.25f * timeDecay(createdAt, nowMs) +
                0.15f * importanceWeight(importance, isCore)
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom > 0f) dot / denom else 0f
        }

        fun timeDecay(createdAt: Long, nowMs: Long = System.currentTimeMillis()): Float {
            val daysSince = (nowMs - createdAt) / (1000f * 60 * 60 * 24)
            return exp(-daysSince / 30.0).toFloat()
        }

        fun importanceWeight(importance: Int, isCore: Boolean): Float {
            if (isCore) return 1.0f
            return when (importance) {
                5 -> 0.8f
                4 -> 0.6f
                3 -> 0.4f
                2 -> 0.2f
                else -> 0.4f
            }
        }

        fun extractKeywords(text: String): List<String> {
            return text.split(Regex("[\\s,。.!?,！？;；、（）()\"'\\[\\]{}]+"))
                .filter { it.length >= 2 }
                .map { it.lowercase() }
                .distinct()
        }
    }
}
