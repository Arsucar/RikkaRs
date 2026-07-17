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
            scope_type = :scopeType,
            scope_id = :scopeId,
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
        scopeType: String,
        scopeId: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM memory_table_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String): Int

    @Transaction
    suspend fun deleteEffectiveTemplateAndDocuments(id: String, assistantId: String): Int {
        getEffectiveTemplate(id, assistantId) ?: return 0
        deleteSnapshotsByTemplate(id)
        deleteDocumentsByTemplate(id)
        return deleteTemplate(id)
    }

    @Transaction
    suspend fun deleteTemplateAndDocuments(id: String): Int {
        getTemplate(id) ?: return 0
        deleteSnapshotsByTemplate(id)
        deleteDocumentsByTemplate(id)
        return deleteTemplate(id)
    }

    @Query(
        """
        DELETE FROM memory_table_snapshots
        WHERE document_id IN (
            SELECT id FROM memory_table_documents
            WHERE template_id = :templateId
        )
        """
    )
    suspend fun deleteSnapshotsByTemplate(templateId: String): Int

    @Query("SELECT * FROM memory_table_documents WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun getDocumentsFlow(): Flow<List<MemoryTableDocumentEntity>>

    @Query("SELECT * FROM memory_table_documents WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    suspend fun getDocuments(): List<MemoryTableDocumentEntity>

    @Query("SELECT * FROM memory_table_documents ORDER BY updated_at DESC")
    suspend fun getDocumentsIncludingDeleted(): List<MemoryTableDocumentEntity>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE deleted_at IS NOT NULL
          AND (
              scope_type = 'GLOBAL'
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
              OR (
                  scope_type = 'CONVERSATION'
                  AND scope_id IN (
                      SELECT id FROM conversationentity WHERE assistant_id = :assistantId
                  )
              )
          )
        ORDER BY deleted_at DESC, updated_at DESC, id ASC
        """
    )
    fun getDeletedDocumentsForAssistantFlow(assistantId: String): Flow<List<MemoryTableDocumentEntity>>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE deleted_at IS NULL
          AND (
              scope_type = 'GLOBAL'
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
              OR (:conversationId IS NOT NULL AND scope_type = 'CONVERSATION' AND scope_id = :conversationId)
          )
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
        WHERE deleted_at IS NULL
          AND (
              scope_type = 'GLOBAL'
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
              OR (:conversationId IS NOT NULL AND scope_type = 'CONVERSATION' AND scope_id = :conversationId)
          )
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
        WHERE deleted_at IS NULL AND scope_type = :scopeType AND scope_id = :scopeId
        ORDER BY updated_at DESC
        """
    )
    fun getDocumentsForScopeFlow(scopeType: String, scopeId: String): Flow<List<MemoryTableDocumentEntity>>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE deleted_at IS NULL AND scope_type = :scopeType AND scope_id = :scopeId
        ORDER BY updated_at DESC
        """
    )
    suspend fun getDocumentsForScope(scopeType: String, scopeId: String): List<MemoryTableDocumentEntity>

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE scope_type = :scopeType AND scope_id = :scopeId
        ORDER BY updated_at DESC
        """
    )
    suspend fun getDocumentsForScopeIncludingDeleted(
        scopeType: String,
        scopeId: String,
    ): List<MemoryTableDocumentEntity>

    @Query("SELECT * FROM memory_table_documents WHERE id = :id AND deleted_at IS NULL")
    suspend fun getDocument(id: String): MemoryTableDocumentEntity?

    @Query("SELECT * FROM memory_table_documents WHERE id = :id")
    suspend fun getDocumentIncludingDeleted(id: String): MemoryTableDocumentEntity?

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE id = :id AND deleted_at IS NULL
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
    suspend fun getEffectiveDocumentIncludingDeleted(
        id: String,
        assistantId: String,
        conversationId: String?,
    ): MemoryTableDocumentEntity?

    @Query(
        """
        SELECT * FROM memory_table_documents
        WHERE id = :id
          AND (
              scope_type = 'GLOBAL'
              OR (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
              OR (
                  scope_type = 'CONVERSATION'
                  AND scope_id IN (
                      SELECT id FROM conversationentity WHERE assistant_id = :assistantId
                  )
              )
          )
        """
    )
    suspend fun getDocumentForAssistantIncludingDeleted(
        id: String,
        assistantId: String,
    ): MemoryTableDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: MemoryTableDocumentEntity)

    @Query(
        """
        UPDATE memory_table_documents
        SET deleted_at = :deletedAt, deleted_by = :deletedBy
        WHERE id = :id AND deleted_at IS NULL
        """
    )
    suspend fun softDeleteDocument(id: String, deletedAt: Long, deletedBy: String): Int

    @Query(
        """
        UPDATE memory_table_documents
        SET deleted_at = NULL, deleted_by = NULL
        WHERE id = :id AND deleted_at IS NOT NULL
        """
    )
    suspend fun restoreDocument(id: String): Int

    @Query(
        """
        UPDATE memory_table_documents
        SET source_document_id = :sourceDocumentId, follow_source = :followSource
        WHERE id = :id AND deleted_at IS NOT NULL
        """
    )
    suspend fun updateDeletedDocumentFollowReference(
        id: String,
        sourceDocumentId: String?,
        followSource: Boolean,
    ): Int

    @Query("DELETE FROM memory_table_documents WHERE id = :id")
    suspend fun deleteDocument(id: String): Int

    @Query("DELETE FROM memory_table_snapshots WHERE document_id = :documentId")
    suspend fun deleteSnapshotsForDocument(documentId: String): Int

    @Query(
        """
        DELETE FROM memory_table_snapshots
        WHERE document_id IN (
            SELECT id FROM memory_table_documents
            WHERE scope_type = 'CONVERSATION' AND scope_id = :conversationId
        )
        """
    )
    suspend fun deleteSnapshotsForConversation(conversationId: String): Int

    @Query(
        """
        DELETE FROM memory_table_documents
        WHERE scope_type = 'CONVERSATION' AND scope_id = :conversationId
        """
    )
    suspend fun deleteDocumentsForConversation(conversationId: String): Int

    @Transaction
    suspend fun deleteDocumentAndSnapshots(id: String): Int {
        deleteSnapshotsForDocument(id)
        return deleteDocument(id)
    }

    @Transaction
    suspend fun deleteMemoryTableDataForConversation(conversationId: String) {
        deleteSnapshotsForConversation(conversationId)
        deleteDocumentsForConversation(conversationId)
    }

    @Query("DELETE FROM memory_table_documents WHERE template_id = :templateId")
    suspend fun deleteDocumentsByTemplate(templateId: String): Int

    @Query(
        """
        SELECT id FROM memory_table_documents
        WHERE (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
           OR (
               scope_type = 'CONVERSATION'
               AND scope_id IN (
                   SELECT id FROM conversationentity WHERE assistant_id = :assistantId
               )
           )
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
           OR (
               scope_type = 'CONVERSATION'
               AND scope_id IN (
                   SELECT id FROM conversationentity WHERE assistant_id = :assistantId
               )
           )
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

    @Query(
        """
        DELETE FROM memory_table_snapshots
        WHERE document_id IN (
            SELECT id FROM memory_table_documents
            WHERE (scope_type = 'ASSISTANT' AND scope_id = :assistantId)
               OR (
                   scope_type = 'CONVERSATION'
                   AND scope_id IN (
                       SELECT id FROM conversationentity WHERE assistant_id = :assistantId
                   )
               )
               OR template_id IN (
                   SELECT id FROM memory_table_templates
                   WHERE scope_type = 'ASSISTANT' AND scope_id = :assistantId
               )
        )
        """
    )
    suspend fun deleteSnapshotsOwnedByAssistant(assistantId: String): Int

    @Transaction
    suspend fun deleteMemoryTableDataOwnedByAssistant(assistantId: String) {
        deleteSnapshotsOwnedByAssistant(assistantId)
        deleteDocumentsOwnedByAssistant(assistantId)
        deleteTemplatesOwnedByAssistant(assistantId)
    }
}
