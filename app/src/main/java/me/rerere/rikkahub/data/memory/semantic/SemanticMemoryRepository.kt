// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import android.util.Log
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.EpisodicMemoryDAO
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import me.rerere.rikkahub.data.db.entity.SemanticMemoryStateEntity

private const val TAG = "SemanticMemoryRepo"

class SemanticMemoryRepository(
    private val dao: EpisodicMemoryDAO,
) {
    fun getMemoriesFlow(assistantId: String): Flow<List<EpisodicMemoryEntity>> =
        dao.getMemoriesFlow(assistantId)

    suspend fun getMemories(assistantId: String): List<EpisodicMemoryEntity> =
        dao.getMemories(assistantId)

    suspend fun getCoreMemories(assistantId: String): List<EpisodicMemoryEntity> =
        dao.getCoreMemories(assistantId)

    suspend fun getMemoriesWithEmbedding(assistantId: String): List<EpisodicMemoryEntity> =
        dao.getMemoriesWithEmbedding(assistantId)

    suspend fun getMemoryById(id: Int): EpisodicMemoryEntity? = dao.getMemoryById(id)

    suspend fun getMemoryCount(assistantId: String): Int = dao.getMemoryCount(assistantId)

    suspend fun getCoreMemoryCount(assistantId: String): Int = dao.getCoreMemoryCount(assistantId)

    suspend fun addMemory(memory: EpisodicMemoryEntity): Long {
        // Do not log memory content (may contain chat-derived private text).
        Log.i(TAG, "addMemory: assistantId=${memory.assistantId}, len=${memory.content.length}")
        return dao.insertMemory(memory)
    }

    suspend fun addMemories(memories: List<EpisodicMemoryEntity>): List<Long> {
        Log.i(TAG, "addMemories: ${memories.size} memories")
        return dao.insertMemories(memories)
    }

    suspend fun updateMemory(memory: EpisodicMemoryEntity) = dao.updateMemory(memory)

    suspend fun updateEmbedding(id: Int, embedding: String, model: String) =
        dao.updateEmbedding(id, embedding, model)

    suspend fun updateRecallStats(ids: List<Int>, timestamp: Long = System.currentTimeMillis()) =
        dao.updateRecallStats(ids, timestamp)

    suspend fun updateImportance(id: Int, importance: Int, isCore: Boolean) =
        dao.updateImportance(id, importance, isCore)

    suspend fun deleteMemory(id: Int) = dao.deleteMemory(id)

    suspend fun deleteMemoriesOfAssistant(assistantId: String) = dao.deleteMemoriesOfAssistant(assistantId)

    suspend fun getState(assistantId: String, conversationId: String): SemanticMemoryStateEntity? =
        dao.getState(assistantId, conversationId)

    suspend fun updateState(assistantId: String, conversationId: String, messageCount: Int) {
        dao.upsertState(
            SemanticMemoryStateEntity(
                assistantId = assistantId,
                conversationId = conversationId,
                lastSummarizedMessageCount = messageCount,
                lastSummarizedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getAllMemories(): List<EpisodicMemoryEntity> = dao.getAllMemories()

    /** Atomically wipe + insert so a crash mid-import cannot leave an empty table. */
    suspend fun replaceAllMemories(memories: List<EpisodicMemoryEntity>) {
        dao.replaceAllMemories(memories)
    }
}
