package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.rerere.rikkahub.data.db.entity.SubagentContextEntity

@Dao
interface SubagentContextDAO {
    @Query("SELECT * FROM subagent_contexts WHERE expires_at > :nowMillis ORDER BY updated_at DESC")
    suspend fun getRestorable(nowMillis: Long): List<SubagentContextEntity>

    @Query("SELECT * FROM subagent_contexts WHERE context_id = :contextId")
    suspend fun getById(contextId: String): SubagentContextEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SubagentContextEntity): Long

    @Query(
        """
        UPDATE subagent_contexts SET
            conversation_id = :conversationId,
            parent_assistant_id = :parentAssistantId,
            scope_json = :scopeJson,
            status = :status,
            messages_json = :messagesJson,
            usage_json = :usageJson,
            last_error = :lastError,
            created_at = :createdAtMillis,
            updated_at = :updatedAtMillis,
            expires_at = :expiresAtMillis,
            revision = :revision
        WHERE context_id = :contextId AND revision < :revision
        """
    )
    suspend fun updateIfNewer(
        contextId: String,
        conversationId: String?,
        parentAssistantId: String,
        scopeJson: String,
        status: String,
        messagesJson: String,
        usageJson: String?,
        lastError: String?,
        createdAtMillis: Long,
        updatedAtMillis: Long,
        expiresAtMillis: Long,
        revision: Long,
    ): Int

    @Transaction
    suspend fun upsertIfNewer(entity: SubagentContextEntity) {
        val updated = updateIfNewer(
            contextId = entity.contextId,
            conversationId = entity.conversationId,
            parentAssistantId = entity.parentAssistantId,
            scopeJson = entity.scopeJson,
            status = entity.status,
            messagesJson = entity.messagesJson,
            usageJson = entity.usageJson,
            lastError = entity.lastError,
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            expiresAtMillis = entity.expiresAtMillis,
            revision = entity.revision,
        )
        if (updated == 0) insertIgnore(entity)
    }

    @Query("DELETE FROM subagent_contexts WHERE context_id = :contextId")
    suspend fun deleteById(contextId: String)

    @Query("DELETE FROM subagent_contexts WHERE expires_at <= :nowMillis AND status != 'RUNNING'")
    suspend fun deleteExpired(nowMillis: Long): Int
}
