package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryTableDAO
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableRepositoryTest {
    @Test
    fun upsertTemplateNormalizesBlankNameAndSchema() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao)

        val template = repository.upsertTemplate(MemoryTableTemplate(name = "", schemaJson = ""))

        assertEquals("Default memory table", template.name)
        assertEquals(template.id, dao.templates.single().id)
        assertEquals(template.schemaJson, dao.templates.single().schemaJson)
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

    private fun doc(id: String, scopeType: String, scopeId: String) = MemoryTableDocumentEntity(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = "{}",
        revision = 0,
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

        override fun getTemplatesFlow(): Flow<List<MemoryTableTemplateEntity>> = flowOf(templates)

        override suspend fun getTemplates(): List<MemoryTableTemplateEntity> = templates

        override suspend fun getTemplate(id: String): MemoryTableTemplateEntity? =
            templates.firstOrNull { it.id == id }

        override suspend fun upsertTemplate(template: MemoryTableTemplateEntity) {
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
            return documents.filter {
                it.scopeType == "GLOBAL" ||
                    (it.scopeType == "ASSISTANT" && it.scopeId == assistantId) ||
                    (conversationId != null && it.scopeType == "CONVERSATION" && it.scopeId == conversationId)
            }
        }

        override fun getEffectiveDocumentsFlow(
            assistantId: String,
            conversationId: String?,
        ): Flow<List<MemoryTableDocumentEntity>> =
            flowOf(documents.filter {
                it.scopeType == "GLOBAL" ||
                    (it.scopeType == "ASSISTANT" && it.scopeId == assistantId) ||
                    (conversationId != null && it.scopeType == "CONVERSATION" && it.scopeId == conversationId)
            })

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
}
