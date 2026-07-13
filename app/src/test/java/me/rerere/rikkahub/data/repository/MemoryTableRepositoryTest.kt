package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryTableDAO
import me.rerere.rikkahub.data.db.dao.MemoryTableSnapshotDAO
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableSnapshotEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableImportConflictPolicy
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.encodeMemoryTableBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableRepositoryTest {
    @Test
    fun upsertTemplateNormalizesBlankNameAndSchema() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao)

        val template = repository.upsertTemplate(MemoryTableTemplate(name = "", schemaJson = ""))

        assertEquals("Default memory table", template.name)
        assertTrue(template.schemaJson.contains(""""name": "key""""))
        assertTrue(template.schemaJson.contains(""""name": "category""""))
        assertTrue(template.schemaJson.contains(""""name": "summary""""))
        assertEquals(1, dao.templateUpserts)
        assertEquals(template.id, dao.templates.single().id)
        assertEquals(template.schemaJson, dao.templates.single().schemaJson)
    }

    @Test
    fun upsertTemplateRejectsInvalidSchemaBeforeDaoWrite() {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao)

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertTemplate(
                    MemoryTableTemplate(
                        name = "bad",
                        schemaJson = """{"tables":[{"name":"facts","columns":[]}]}""",
                    )
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("schemaJson.tables[0].columns"))
        assertEquals(0, dao.templateUpserts)
        assertTrue(dao.templates.isEmpty())
    }

    @Test
    fun deleteTemplateReturnsFalseWhenTemplateIsMissing() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao)

        val deleted = repository.deleteTemplate("missing")

        assertFalse(deleted)
    }

    @Test
    fun deleteTemplateDeletesDocumentsOnlyWhenTemplateExists() = runBlocking {
        val template = MemoryTableTemplateEntity(
            id = "template",
            name = "Template",
            description = "",
            schemaJson = """{"tables":[{"name":"facts","columns":[{"name":"key"}]}]}""",
            createdAt = 1,
            updatedAt = 1,
        )
        val dao = FakeMemoryTableDAO(
            templates = listOf(template),
            documents = listOf(
                doc("doc", "ASSISTANT", "assistant-a"),
                doc("other", "ASSISTANT", "assistant-a").copy(templateId = "other-template"),
            )
        )
        val repository = MemoryTableRepository(dao)

        val deleted = repository.deleteTemplate("template")

        assertTrue(deleted)
        assertTrue(dao.templates.isEmpty())
        assertEquals(listOf("other"), dao.documents.map { it.id })
    }

    @Test
    fun effectiveDocumentsReturnsGlobalAssistantAndConversationScopes() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
                doc("assistant-a", "ASSISTANT", "assistant-a"),
                doc("assistant-b", "ASSISTANT", "assistant-b"),
                doc("conversation-a", "CONVERSATION", "conversation-a"),
            )
        )
        val repository = MemoryTableRepository(dao)

        val documents = repository.getEffectiveDocuments(
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        )

        assertEquals(listOf("global", "assistant-a", "conversation-a"), documents.map { it.id })
    }

    @Test
    fun effectiveDocumentsDefensivelyFilterPollutedSuspendAndFlowResults() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
                doc("assistant-a", "ASSISTANT", "assistant-a"),
                doc("assistant-b", "ASSISTANT", "assistant-b"),
                doc("conversation-a", "CONVERSATION", "conversation-a"),
                doc("conversation-b", "CONVERSATION", "conversation-b"),
                doc("unknown", "UNKNOWN", "assistant-a"),
            ),
        )
        val repository = MemoryTableRepository(dao)

        val conversationSuspend = repository.getEffectiveDocuments(
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        )
        val conversationFlow = repository.getEffectiveDocumentsFlow(
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        ).first()
        val assistantSuspend = repository.getEffectiveDocuments(assistantId = "assistant-a")
        val assistantFlow = repository.getAssistantMemoryDocumentsFlow(assistantId = "assistant-a").first()

        val expectedConversationIds = listOf("global", "assistant-a", "conversation-a")
        val expectedAssistantIds = listOf("global", "assistant-a")
        assertEquals(expectedConversationIds, conversationSuspend.map { it.id })
        assertEquals(expectedConversationIds, conversationFlow.map { it.id })
        assertEquals(expectedAssistantIds, assistantSuspend.map { it.id })
        assertEquals(expectedAssistantIds, assistantFlow.map { it.id })
    }

    @Test
    fun getEffectiveDocumentsIfEnabledDoesNotReadDaoWhenDisabled() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
            )
        )
        val repository = MemoryTableRepository(dao)

        val documents = repository.getEffectiveDocumentsIfEnabled(
            settingsEnabled = false,
            assistantEnabled = true,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        )

        assertTrue(documents.isEmpty())
        assertEquals(0, dao.effectiveDocumentReads)
    }

    @Test
    fun upsertDocumentBumpsRevisionWhenUpdating() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao)
        val created = repository.upsertDocument(
            MemoryTableDocument(
                templateId = "template",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
                payloadJson = """{"a":1}""",
            )
        )

        val updated = repository.upsertDocument(created.copy(payloadJson = """{"a":2}"""))

        assertEquals(1, updated.revision)
        assertEquals("""{"a":2}""", dao.documents.single().payloadJson)
    }

    @Test
    fun upsertDocumentRejectsInvalidPayloadBeforeDaoWrite() {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao)

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertDocument(
                    MemoryTableDocument(
                        templateId = "template",
                        scopeType = MemoryTableScopeType.ASSISTANT,
                        scopeId = "assistant-a",
                        payloadJson = """["not-an-object"]""",
                    )
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("payloadJson must be a JSON object"))
        assertEquals(0, dao.documentUpserts)
        assertTrue(dao.documents.isEmpty())
    }

    @Test
    fun upsertDocumentRejectsInvalidPayloadWithoutReplacingExistingDocument() {
        val existing = doc(
            id = "doc",
            scopeType = "ASSISTANT",
            scopeId = "assistant-a",
            payloadJson = """{"valid":true}""",
            revision = 3,
        )
        val dao = FakeMemoryTableDAO(documents = listOf(existing))
        val repository = MemoryTableRepository(dao)

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertDocument(
                    MemoryTableDocument(
                        id = "doc",
                        templateId = "template",
                        scopeType = MemoryTableScopeType.ASSISTANT,
                        scopeId = "assistant-a",
                        payloadJson = """{"truncated":""",
                    )
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("payloadJson must be valid JSON"))
        assertEquals(0, dao.documentUpserts)
        assertEquals("""{"valid":true}""", dao.documents.single().payloadJson)
        assertEquals(3, dao.documents.single().revision)
    }

    @Test
    fun rollbackDocumentRestoresPriorRevision() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc(
                    id = "doc",
                    scopeType = "ASSISTANT",
                    scopeId = "assistant-a",
                    payloadJson = """{"facts":[{"key":"name","value":"Alice"}]}""",
                    revision = 0,
                )
            ),
        )
        val snapshotDao = FakeMemoryTableSnapshotDAO()
        val repository = MemoryTableRepository(dao, snapshotDao)

        // First overwrite: revision 0's payload is snapshotted, document becomes revision 1.
        repository.upsertDocument(
            dao.documents.single().let {
                MemoryTableDocument(
                    id = it.id,
                    templateId = it.templateId,
                    scopeType = MemoryTableScopeType.ASSISTANT,
                    scopeId = it.scopeId,
                    payloadJson = """{"facts":[{"key":"name","value":"Bob"}]}""",
                )
            }
        )
        assertEquals("""{"facts":[{"key":"name","value":"Bob"}]}""", dao.documents.single().payloadJson)

        val snapshots = repository.getDocumentSnapshots("doc")
        assertEquals(1, snapshots.size)
        assertEquals(0, snapshots.single().revision)

        // Rollback to revision 0's payload.
        val restored = repository.rollbackDocument("doc", revision = 0)
        assertTrue(restored.payloadJson.contains("Alice"))
        assertTrue(dao.documents.single().payloadJson.contains("Alice"))
    }

    private fun doc(
        id: String,
        scopeType: String,
        scopeId: String,
        payloadJson: String = "{}",
        revision: Int = 0,
    ) = MemoryTableDocumentEntity(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = payloadJson,
        revision = revision,
        createdAt = 1,
        updatedAt = 1,
    )

    private class FakeMemoryTableDAO(
        templates: List<MemoryTableTemplateEntity> = emptyList(),
        documents: List<MemoryTableDocumentEntity> = emptyList(),
    ) : MemoryTableDAO {
        val templates = templates.toMutableList()
        val documents = documents.toMutableList()
        var effectiveDocumentReads = 0
        var templateUpserts = 0
        var documentUpserts = 0

        override fun getTemplatesFlow(): Flow<List<MemoryTableTemplateEntity>> = flowOf(templates)

        override suspend fun getTemplates(): List<MemoryTableTemplateEntity> = templates

        override suspend fun getTemplate(id: String): MemoryTableTemplateEntity? =
            templates.firstOrNull { it.id == id }

        override suspend fun upsertTemplate(template: MemoryTableTemplateEntity) {
            templateUpserts++
            templates.removeAll { it.id == template.id }
            templates += template
        }

        override suspend fun deleteTemplate(id: String): Int {
            val before = templates.size
            templates.removeAll { it.id == id }
            return before - templates.size
        }

        override fun getDocumentsFlow(): Flow<List<MemoryTableDocumentEntity>> = flowOf(documents)

        override suspend fun getDocuments(): List<MemoryTableDocumentEntity> = documents

        override suspend fun getEffectiveDocuments(
            assistantId: String,
            conversationId: String?,
        ): List<MemoryTableDocumentEntity> {
            effectiveDocumentReads++
            return documents.toList()
        }

        override fun getEffectiveDocumentsFlow(
            assistantId: String,
            conversationId: String?,
        ): Flow<List<MemoryTableDocumentEntity>> = flowOf(documents.toList())

        override fun getDocumentsForScopeFlow(
            scopeType: String,
            scopeId: String,
        ): Flow<List<MemoryTableDocumentEntity>> =
            flowOf(documents.filter { it.scopeType == scopeType && it.scopeId == scopeId })

        override suspend fun getDocumentsForScope(
            scopeType: String,
            scopeId: String,
        ): List<MemoryTableDocumentEntity> =
            documents.filter { it.scopeType == scopeType && it.scopeId == scopeId }

        override suspend fun getDocument(id: String): MemoryTableDocumentEntity? =
            documents.firstOrNull { it.id == id }

        override suspend fun upsertDocument(document: MemoryTableDocumentEntity) {
            documentUpserts++
            documents.removeAll { it.id == document.id }
            documents += document
        }

        override suspend fun deleteDocument(id: String): Int {
            val before = documents.size
            documents.removeAll { it.id == id }
            return before - documents.size
        }

        override suspend fun deleteDocumentsByTemplate(templateId: String): Int {
            val before = documents.size
            documents.removeAll { it.templateId == templateId }
            return before - documents.size
        }
    }

    private class FakeMemoryTableSnapshotDAO : MemoryTableSnapshotDAO {
        val snapshots = mutableListOf<MemoryTableSnapshotEntity>()

        override fun getSnapshotsForDocumentFlow(documentId: String) =
            flowOf(snapshots.filter { it.documentId == documentId }.sortedByDescending { it.revision })

        override suspend fun getSnapshotsForDocument(documentId: String): List<MemoryTableSnapshotEntity> =
            snapshots.filter { it.documentId == documentId }.sortedByDescending { it.revision }

        override suspend fun getSnapshot(documentId: String, revision: Int): MemoryTableSnapshotEntity? =
            snapshots.firstOrNull { it.documentId == documentId && it.revision == revision }

        override suspend fun upsertSnapshot(snapshot: MemoryTableSnapshotEntity) {
            snapshots.removeAll { it.documentId == snapshot.documentId && it.revision == snapshot.revision }
            snapshots += snapshot
        }

        override suspend fun countSnapshots(documentId: String): Int =
            snapshots.count { it.documentId == documentId }

        override suspend fun pruneSnapshots(documentId: String, keep: Int): Int {
            val forDoc = snapshots.filter { it.documentId == documentId }.sortedByDescending { it.revision }
            val toRemove = forDoc.drop(keep)
            snapshots.removeAll(toRemove)
            return toRemove.size
        }

        override suspend fun deleteSnapshotsForDocument(documentId: String): Int {
            val before = snapshots.size
            snapshots.removeAll { it.documentId == documentId }
            return before - snapshots.size
        }
    }
}
