package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.MemoryTableDAO
import me.rerere.rikkahub.data.db.dao.MemoryTableSnapshotDAO
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableSnapshotEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import me.rerere.rikkahub.data.model.MemoryTableBundle
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableImportConflictPolicy
import me.rerere.rikkahub.data.model.MemoryTableImportPlan
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.decodeMemoryTableBundle
import me.rerere.rikkahub.data.model.encodeMemoryTableBundle
import me.rerere.rikkahub.data.model.resolveMemoryTableBundleImport
import me.rerere.rikkahub.data.model.normalizeMemoryTablePayloadJson
import me.rerere.rikkahub.data.model.normalizeMemoryTableSchemaJson
import me.rerere.rikkahub.data.model.shouldEnableMemoryTable
import me.rerere.rikkahub.data.model.validateMemoryTablePayloadJson
import me.rerere.rikkahub.data.model.validateMemoryTableSchemaJson
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MemoryTableRepository(
    private val dao: MemoryTableDAO,
    // #96: optional snapshot DAO. Null keeps the repository fully functional
    // without revision history (used by existing unit tests that don't wire it).
    private val snapshotDao: MemoryTableSnapshotDAO? = null,
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
            description = template.description.trim(),
            schemaJson = normalizeMemoryTableSchemaJson(template.schemaJson),
            createdAt = template.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
        validateMemoryTableSchemaJson(normalized.schemaJson)
        dao.upsertTemplate(normalized.toEntity())
        return normalized
    }

    suspend fun deleteTemplate(id: String): Boolean {
        if (dao.getTemplate(id) == null) return false
        dao.deleteDocumentsByTemplate(id)
        return dao.deleteTemplate(id) > 0
    }

    suspend fun upsertDocument(document: MemoryTableDocument): MemoryTableDocument {
        val now = Clock.System.now().toEpochMilliseconds()
        val payloadJson = normalizeMemoryTablePayloadJson(document.payloadJson)
        validateMemoryTablePayloadJson(payloadJson)
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
            payloadJson = payloadJson,
            revision = (old?.revision ?: document.revision).coerceAtLeast(0) + if (old == null) 0 else 1,
            createdAt = old?.createdAt ?: document.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
        // #96: before overwriting an existing document, snapshot its prior payload so
        // a previous revision can be restored later. New documents have no prior state.
        if (old != null) {
            snapshotDao?.let { snapshots ->
                snapshots.upsertSnapshot(
                    MemoryTableSnapshotEntity(
                        id = Uuid.random().toString(),
                        documentId = old.id,
                        revision = old.revision,
                        payloadJson = old.payloadJson,
                        createdAt = now,
                    )
                )
                snapshots.pruneSnapshots(old.id, MEMORY_TABLE_SNAPSHOT_RETENTION)
            }
        }
        dao.upsertDocument(normalized.toEntity())
        return normalized
    }

    // #96: list stored revision snapshots for a document, newest revision first.
    suspend fun getDocumentSnapshots(documentId: String): List<MemoryTableDocumentSnapshot> =
        snapshotDao?.getSnapshotsForDocument(documentId)
            ?.map { MemoryTableDocumentSnapshot(it.documentId, it.revision, it.payloadJson, it.createdAt) }
            .orEmpty()

    // #96: restore a document payload from a stored snapshot revision. Restoring writes
    // a new revision (and snapshots the current payload first) so history stays linear.
    suspend fun rollbackDocument(documentId: String, revision: Int): MemoryTableDocument {
        val snapshots = snapshotDao
            ?: error("memory table snapshots are unavailable")
        val snapshot = snapshots.getSnapshot(documentId, revision)
            ?: error("memory table snapshot not found for $documentId@$revision")
        val current = dao.getDocument(documentId)?.toModel()
            ?: error("memory table document not found: $documentId")
        return upsertDocument(current.copy(payloadJson = snapshot.payloadJson))
    }

    suspend fun getDocument(id: String): MemoryTableDocument? =
        dao.getDocument(id)?.toModel()

    suspend fun deleteDocument(id: String) {
        dao.deleteDocument(id)
    }

    // #100: export all templates + documents as a versioned JSON bundle.
    suspend fun exportBundle(): String =
        encodeMemoryTableBundle(
            templates = getTemplates(),
            documents = getDocuments(),
        )

    // #100: import a versioned JSON bundle, resolving id conflicts by policy and
    // persisting the resolved templates/documents through the validating upserts.
    suspend fun importBundle(
        bundleJson: String,
        policy: MemoryTableImportConflictPolicy,
    ): MemoryTableImportPlan {
        val bundle = decodeMemoryTableBundle(bundleJson)
        val existingTemplateIds = getTemplates().map { it.id }.toSet()
        val existingDocumentIds = getDocuments().map { it.id }.toSet()
        val plan = resolveMemoryTableBundleImport(
            bundle = bundle,
            existingTemplateIds = existingTemplateIds,
            existingDocumentIds = existingDocumentIds,
            policy = policy,
        )
        plan.templates.forEach { upsertTemplate(it) }
        plan.documents.forEach { upsertDocument(it) }
        return plan
    }

    // #89: duplicate every document from one scope into another (used when forking a
    // conversation/assistant). Each copy gets a fresh id and reset revision so it never
    // collides with or bumps the revision of an existing document in the target scope.
    suspend fun copyDocumentsToScope(
        fromScopeType: MemoryTableScopeType,
        fromScopeId: String,
        toScopeType: MemoryTableScopeType,
        toScopeId: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        getDocumentsForScope(fromScopeType, fromScopeId).forEach { document ->
            upsertDocument(
                document.copy(
                    id = Uuid.random().toString(),
                    scopeType = toScopeType,
                    scopeId = toScopeId,
                    revision = 0,
                    createdAt = 0,
                    updatedAt = now,
                )
            )
        }
    }

    // #89: after a conversation is moved to a new assistant, rebind its following
    // conversation-scoped documents to the matching assistant-scoped document (matched by
    // templateId) under the new assistant. When no match exists the document is detached
    // (followSource = false) so it keeps its current payload instead of dangling.
    suspend fun relinkFollowReferences(
        conversationId: String,
        oldAssistantId: String,
        newAssistantId: String,
    ) {
        val newAssistantDocs = getDocumentsForScope(MemoryTableScopeType.ASSISTANT, newAssistantId)
        val newSourceByTemplate = newAssistantDocs.associateBy { it.templateId }
        getDocumentsForScope(MemoryTableScopeType.CONVERSATION, conversationId)
            .filter { it.followSource }
            .forEach { document ->
                val match = newSourceByTemplate[document.templateId]
                val relinked = if (match != null) {
                    document.copy(sourceDocumentId = match.id, followSource = true)
                } else {
                    document.copy(followSource = false)
                }
                upsertDocument(relinked)
            }
    }

    private fun MemoryTableTemplateEntity.toModel(): MemoryTableTemplate =
        MemoryTableTemplate(
            id = id,
            name = name,
            description = description,
            schemaJson = schemaJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun MemoryTableTemplate.toEntity(): MemoryTableTemplateEntity =
        MemoryTableTemplateEntity(
            id = id,
            name = name,
            description = description,
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
            sourceDocumentId = sourceDocumentId,
            followSource = followSource,
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
            sourceDocumentId = sourceDocumentId,
            followSource = followSource,
        )
}

// #96: max revision snapshots kept per document; older snapshots are pruned.
const val MEMORY_TABLE_SNAPSHOT_RETENTION = 20

// #96: a restorable prior revision of a memory table document payload.
data class MemoryTableDocumentSnapshot(
    val documentId: String,
    val revision: Int,
    val payloadJson: String,
    val createdAt: Long,
)
