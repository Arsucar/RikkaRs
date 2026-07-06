package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity

@Dao
interface MemoryTableDAO {
    @Query("SELECT * FROM memory_table_templates ORDER BY updated_at DESC")
    fun getTemplatesFlow(): Flow<List<MemoryTableTemplateEntity>>

    @Query("SELECT * FROM memory_table_templates ORDER BY updated_at DESC")
    suspend fun getTemplates(): List<MemoryTableTemplateEntity>

    @Query("SELECT * FROM memory_table_templates WHERE id = :id")
    suspend fun getTemplate(id: String): MemoryTableTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: MemoryTableTemplateEntity)

    @Query("DELETE FROM memory_table_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String): Int

    @Query("SELECT * FROM memory_table_documents ORDER BY updated_at DESC")
    fun getDocumentsFlow(): Flow<List<MemoryTableDocumentEntity>>

    @Query("SELECT * FROM memory_table_documents ORDER BY updated_at DESC")
    suspend fun getDocuments(): List<MemoryTableDocumentEntity>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE scope_type = 'GLOBAL'
           OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
           OR (:conversationId IS NOT NULL AND scope_type = 'CONVERSATION' AND scope_id = :conversationId)
        ORDER BY CASE scope_type
            WHEN 'GLOBAL' THEN 0
            WHEN 'ASSISTANT' THEN 1
            ELSE 2
        END, updated_at DESC
        """
    )
    suspend fun getEffectiveDocuments(
        assistantId: String,
        conversationId: String?,
    ): List<MemoryTableDocumentEntity>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE scope_type = 'GLOBAL'
           OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
           OR (:conversationId IS NOT NULL AND scope_type = 'CONVERSATION' AND scope_id = :conversationId)
        ORDER BY CASE scope_type
            WHEN 'GLOBAL' THEN 0
            WHEN 'ASSISTANT' THEN 1
            ELSE 2
        END, updated_at DESC
        """
    )
    fun getEffectiveDocumentsFlow(
        assistantId: String,
        conversationId: String?,
    ): Flow<List<MemoryTableDocumentEntity>>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE scope_type = :scopeType AND scope_id = :scopeId
        ORDER BY updated_at DESC
        """
    )
    fun getDocumentsForScopeFlow(scopeType: String, scopeId: String): Flow<List<MemoryTableDocumentEntity>>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE scope_type = :scopeType AND scope_id = :scopeId
        ORDER BY updated_at DESC
        """
    )
    suspend fun getDocumentsForScope(scopeType: String, scopeId: String): List<MemoryTableDocumentEntity>

    @Query("SELECT * FROM memory_table_documents WHERE id = :id")
    suspend fun getDocument(id: String): MemoryTableDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: MemoryTableDocumentEntity)

    @Query("DELETE FROM memory_table_documents WHERE id = :id")
    suspend fun deleteDocument(id: String): Int

    @Query("DELETE FROM memory_table_documents WHERE template_id = :templateId")
    suspend fun deleteDocumentsByTemplate(templateId: String): Int
}
