package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEntity

@Dao
interface MemoryDAO {
    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId AND scope = 'ASSISTANT' ORDER BY id ASC")
    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId AND scope = 'ASSISTANT' ORDER BY id ASC")
    suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE scope = 'GLOBAL' ORDER BY id ASC")
    fun getGlobalMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity WHERE scope = 'GLOBAL' ORDER BY id ASC")
    suspend fun getGlobalMemories(): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memoryentity
        WHERE scope = 'GLOBAL'
           OR (assistant_id = :assistantId AND scope = 'ASSISTANT')
        ORDER BY CASE scope WHEN 'GLOBAL' THEN 0 ELSE 1 END, id ASC
        """
    )
    fun getEffectiveMemoriesFlow(assistantId: String): Flow<List<MemoryEntity>>

    @Query(
        """
        SELECT * FROM memoryentity
        WHERE scope = 'GLOBAL'
           OR (assistant_id = :assistantId AND scope = 'ASSISTANT')
        ORDER BY CASE scope WHEN 'GLOBAL' THEN 0 ELSE 1 END, id ASC
        """
    )
    suspend fun getEffectiveMemories(assistantId: String): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memoryentity WHERE id = :id")
    suspend fun deleteMemory(id: Int)

    @Query("DELETE FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesOfAssistant(assistantId: String)
}
