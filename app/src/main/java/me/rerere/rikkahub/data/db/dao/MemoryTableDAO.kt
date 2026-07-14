package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query(
        """
        SELECT * FROM memory_table_templates
        WHERE (scope_type = 'GLOBAL' AND scope_id = '__global__')
           OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
        ORDER BY CASE scope_type
            WHEN 'GLOBAL' THEN 0
            WHEN 'ASSISTANT' THEN 1
            ELSE 2
        END, updated_at DESC
        """
    )
    fun getEffectiveTemplatesFlow(assistantId: String): Flow<List<MemoryTableTemplateEntity>>

    @Query(
        """
        SELECT * FROM memory_table_templates
        WHERE (scope_type = 'GLOBAL' AND scope_id = '__global__')
           OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
        ORDER BY CASE scope_type
            WHEN 'GLOBAL' THEN 0
            WHEN 'ASSISTANT' THEN 1
            ELSE 2
        END, updated_at DESC
        """
    )
    suspend fun getEffectiveTemplates(assistantId: String): List<MemoryTableTemplateEntity>

    @Query(
        """
        SELECT * FROM memory_table_templates
        WHERE id = :id
          AND (
              (scope_type = 'GLOBAL' AND scope_id = '__global__')
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
          )
        """
    )
    suspend fun getEffectiveTemplate(id: String, assistantId: String): MemoryTableTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: MemoryTableTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplateIgnore(template: MemoryTableTemplateEntity): Long

    @Query(
        """
        UPDATE memory_table_templates
        SET name = :name,
            description = :description,
            schema_json = :schemaJson,
            updated_at = :updatedAt
        WHERE id = :id
          AND (
              (scope_type = 'GLOBAL' AND scope_id = '__global__')
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
          )
        """
    )
    suspend fun updateEffectiveTemplateFields(
        id: String,
        assistantId: String,
        name: String,
        description: String,
        schemaJson: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM memory_table_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String): Int

    @Transaction
    suspend fun deleteEffectiveTemplateAndDocuments(id: String, assistantId: String): Int {
        getEffectiveTemplate(id, assistantId) ?: return 0
        deleteDocumentsByTemplate(id)
        return deleteTemplate(id)
    }

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

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE id = :id
          AND (
              scope_type = 'GLOBAL'
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
              OR (:conversationId IS NOT NULL AND scope_type = 'CONVERSATION' AND scope_id = :conversationId)
          )
        """
    )
    suspend fun getEffectiveDocument(
        id: String,
        assistantId: String,
        conversationId: String?,
    ): MemoryTableDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: MemoryTableDocumentEntity)

    @Query("DELETE FROM memory_table_documents WHERE id = :id")
    suspend fun deleteDocument(id: String): Int

    @Query("DELETE FROM memory_table_documents WHERE template_id = :templateId")
    suspend fun deleteDocumentsByTemplate(templateId: String): Int

    @Query(
        """
        SELECT id FROM memory_table_documents
        WHERE (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
           OR template_id IN (
               SELECT id FROM memory_table_templates
               WHERE scope_type = 'ASSISTANT' AND scope_id = :assistantId
           )
        """
    )
    suspend fun getDocumentIdsOwnedByAssistant(assistantId: String): List<String>

    @Query(
        """
        DELETE FROM memory_table_documents
        WHERE (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
           OR template_id IN (
               SELECT id FROM memory_table_templates
               WHERE scope_type = 'ASSISTANT' AND scope_id = :assistantId
           )
        """
    )
    suspend fun deleteDocumentsOwnedByAssistant(assistantId: String): Int

    @Query(
        """
        DELETE FROM memory_table_templates
        WHERE scope_type = 'ASSISTANT' AND scope_id = :assistantId
        """
    )
    suspend fun deleteTemplatesOwnedByAssistant(assistantId: String): Int

    @Transaction
    suspend fun deleteMemoryTableDataOwnedByAssistant(assistantId: String) {
        deleteDocumentsOwnedByAssistant(assistantId)
        deleteTemplatesOwnedByAssistant(assistantId)
    }
}
