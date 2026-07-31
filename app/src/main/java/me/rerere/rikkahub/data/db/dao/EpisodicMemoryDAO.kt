// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import me.rerere.rikkahub.data.db.entity.SemanticMemoryStateEntity

@Dao
interface EpisodicMemoryDAO {
    @Query(
        "SELECT * FROM episodic_memory WHERE assistant_id = :assistantId " +
            "ORDER BY importance DESC, created_at DESC",
    )
    fun getMemoriesFlow(assistantId: String): Flow<List<EpisodicMemoryEntity>>

    @Query(
        "SELECT * FROM episodic_memory WHERE assistant_id = :assistantId " +
            "ORDER BY importance DESC, created_at DESC",
    )
    suspend fun getMemories(assistantId: String): List<EpisodicMemoryEntity>

    @Query("SELECT * FROM episodic_memory WHERE assistant_id = :assistantId AND is_core = 1")
    suspend fun getCoreMemories(assistantId: String): List<EpisodicMemoryEntity>

    @Query("SELECT * FROM episodic_memory WHERE assistant_id = :assistantId AND embedding IS NOT NULL")
    suspend fun getMemoriesWithEmbedding(assistantId: String): List<EpisodicMemoryEntity>

    @Query("SELECT * FROM episodic_memory WHERE id = :id")
    suspend fun getMemoryById(id: Int): EpisodicMemoryEntity?

    @Query("SELECT COUNT(*) FROM episodic_memory WHERE assistant_id = :assistantId")
    suspend fun getMemoryCount(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM episodic_memory WHERE assistant_id = :assistantId AND is_core = 1")
    suspend fun getCoreMemoryCount(assistantId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: EpisodicMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<EpisodicMemoryEntity>): List<Long>

    @Update
    suspend fun updateMemory(memory: EpisodicMemoryEntity)

    @Query("UPDATE episodic_memory SET embedding = :embedding, embedding_model = :model WHERE id = :id")
    suspend fun updateEmbedding(id: Int, embedding: String, model: String)

    @Query(
        "UPDATE episodic_memory SET last_recalled_at = :timestamp, recall_count = recall_count + 1 " +
            "WHERE id IN (:ids)",
    )
    suspend fun updateRecallStats(ids: List<Int>, timestamp: Long)

    @Query("UPDATE episodic_memory SET importance = :importance, is_core = :isCore WHERE id = :id")
    suspend fun updateImportance(id: Int, importance: Int, isCore: Boolean)

    @Query("DELETE FROM episodic_memory WHERE id = :id")
    suspend fun deleteMemory(id: Int)

    @Query("DELETE FROM episodic_memory WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesOfAssistant(assistantId: String)

    @Query("SELECT * FROM episodic_memory")
    suspend fun getAllMemories(): List<EpisodicMemoryEntity>

    @Query("DELETE FROM episodic_memory")
    suspend fun deleteAllMemories()

    @Transaction
    suspend fun replaceAllMemories(memories: List<EpisodicMemoryEntity>) {
        deleteAllMemories()
        if (memories.isNotEmpty()) {
            insertMemories(memories)
        }
    }

    @Query(
        "SELECT * FROM semantic_memory_state " +
            "WHERE assistant_id = :assistantId AND conversation_id = :conversationId",
    )
    suspend fun getState(assistantId: String, conversationId: String): SemanticMemoryStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: SemanticMemoryStateEntity)

    @Query("DELETE FROM semantic_memory_state WHERE assistant_id = :assistantId")
    suspend fun deleteState(assistantId: String)

    @Query(
        "DELETE FROM semantic_memory_state " +
            "WHERE assistant_id = :assistantId AND conversation_id = :conversationId",
    )
    suspend fun deleteStateForConversation(assistantId: String, conversationId: String)
}
