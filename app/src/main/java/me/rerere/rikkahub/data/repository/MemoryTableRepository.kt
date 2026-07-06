package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.MemoryTableDAO
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_SCHEMA_JSON
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.shouldEnableMemoryTable
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MemoryTableRepository(
    private val dao: MemoryTableDAO,
) {
    fun getTemplatesFlow(): Flow<List<MemoryTableTemplate>> =
        dao.getTemplatesFlow().map { templates -> templates.map { it.toModel() } }

    suspend fun getTemplates(): List<MemoryTableTemplate> =
        dao.getTemplates().map { it.toModel() }

    suspend fun getTemplate(id: String): MemoryTableTemplate? =
        dao.getTemplate(id)?.toModel()

    fun getDocumentsFlow(): Flow<List<MemoryTableDocument>> =
        dao.getDocumentsFlow().map { documents -> documents.map { it.toModel() } }

    suspend fun getDocuments(): List<MemoryTableDocument> =
        dao.getDocuments().map { it.toModel() }

    fun getDocumentsForScopeFlow(
        scopeType: MemoryTableScopeType,
        scopeId: String,
    ): Flow<List<MemoryTableDocument>> =
        dao.getDocumentsForScopeFlow(scopeType.name, scopeId).map { documents -> documents.map { it.toModel() } }

    suspend fun getDocumentsForScope(
        scopeType: MemoryTableScopeType,
        scopeId: String,
    ): List<MemoryTableDocument> =
        dao.getDocumentsForScope(scopeType.name, scopeId).map { it.toModel() }

    suspend fun getEffectiveDocuments(
        assistantId: String,
        conversationId: String? = null,
    ): List<MemoryTableDocument> =
        dao.getEffectiveDocuments(assistantId, conversationId).map { it.toModel() }

    fun getEffectiveDocumentsFlow(
        assistantId: String,
        conversationId: String? = null,
    ): Flow<List<MemoryTableDocument>> =
        dao.getEffectiveDocumentsFlow(assistantId, conversationId)
            .map { documents -> documents.map { it.toModel() } }

    suspend fun getEffectiveDocumentsIfEnabled(
        settingsEnabled: Boolean,
        assistantEnabled: Boolean,
        assistantId: String,
        conversationId: String? = null,
    ): List<MemoryTableDocument> {
        if (!shouldEnableMemoryTable(settingsEnabled, assistantEnabled)) return emptyList()
        return getEffectiveDocuments(assistantId, conversationId)
    }

    suspend fun upsertTemplate(template: MemoryTableTemplate): MemoryTableTemplate {
        val now = Clock.System.now().toEpochMilliseconds()
        val normalized = template.copy(
            id = template.id.ifBlank { Uuid.random().toString() },
            name = template.name.ifBlank { "Default memory table" },
            schemaJson = template.schemaJson.ifBlank { DEFAULT_MEMORY_TABLE_SCHEMA_JSON.trimIndent() },
            createdAt = template.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
        dao.upsertTemplate(normalized.toEntity())
        return normalized
    }

    suspend fun deleteTemplate(id: String) {
        dao.deleteDocumentsByTemplate(id)
        dao.deleteTemplate(id)
    }

    suspend fun upsertDocument(document: MemoryTableDocument): MemoryTableDocument {
        val now = Clock.System.now().toEpochMilliseconds()
        val old = document.id.takeIf { it.isNotBlank() }?.let { dao.getDocument(it) }
        val normalized = document.copy(
            id = document.id.ifBlank { Uuid.random().toString() },
            scopeId = document.scopeId.ifBlank {
                if (document.scopeType == MemoryTableScopeType.GLOBAL) {
                    MemoryRepository.GLOBAL_MEMORY_ID
                } else {
                    document.scopeId
                }
            },
            payloadJson = document.payloadJson.ifBlank { "{}" },
            revision = (old?.revision ?: document.revision).coerceAtLeast(0) + if (old == null) 0 else 1,
            createdAt = old?.createdAt ?: document.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
        dao.upsertDocument(normalized.toEntity())
        return normalized
    }

    suspend fun getDocument(id: String): MemoryTableDocument? =
        dao.getDocument(id)?.toModel()

    suspend fun deleteDocument(id: String) {
        dao.deleteDocument(id)
    }

    private fun MemoryTableTemplateEntity.toModel(): MemoryTableTemplate =
        MemoryTableTemplate(
            id = id,
            name = name,
            schemaJson = schemaJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun MemoryTableTemplate.toEntity(): MemoryTableTemplateEntity =
        MemoryTableTemplateEntity(
            id = id,
            name = name,
            schemaJson = schemaJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun MemoryTableDocumentEntity.toModel(): MemoryTableDocument =
        MemoryTableDocument(
            id = id,
            templateId = templateId,
            scopeType = MemoryTableScopeType.fromStorage(scopeType),
            scopeId = scopeId,
            payloadJson = payloadJson,
            revision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun MemoryTableDocument.toEntity(): MemoryTableDocumentEntity =
        MemoryTableDocumentEntity(
            id = id,
            templateId = templateId,
            scopeType = scopeType.name,
            scopeId = scopeId,
            payloadJson = payloadJson,
            revision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
