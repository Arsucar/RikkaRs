package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableToolsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun disabledModeRegistersNoTools() {
        val tools = buildMemoryTableToolsIfEnabled(
            enabled = false,
            json = json,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun readReturnsActiveDocuments() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            readDocuments = {
                listOf(document("doc", MemoryTableScopeType.CONVERSATION, "conversation-a"))
            },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "read")
            }
        ).single() as UIMessagePart.Text

        assertTrue(result.text.contains("doc"))
        assertTrue(result.text.contains("CONVERSATION"))
    }

    @Test
    fun upsertRowsCanCreateConversationScopedDocument() = runBlocking {
        var captured: MemoryTableDocument? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = {
                captured = it
                it
            },
            deleteDocument = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "template")
                put("scope", "conversation")
                put("payload_json", """{"topic":"local"}""")
            }
        )

        assertEquals(MemoryTableScopeType.CONVERSATION, captured?.scopeType)
        assertEquals("conversation-a", captured?.scopeId)
    }

    @Test
    fun upsertRowsDefaultsToAssistantScope() = runBlocking {
        var captured: MemoryTableDocument? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = {
                captured = it
                it
            },
            deleteDocument = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "template")
                put("payload_json", "{}")
            }
        )

        assertEquals(MemoryTableScopeType.ASSISTANT, captured?.scopeType)
        assertEquals("assistant-a", captured?.scopeId)
    }

    @Test
    fun upsertRowsCanCreateGlobalScopedDocument() = runBlocking {
        var captured: MemoryTableDocument? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = {
                captured = it
                it
            },
            deleteDocument = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "template")
                put("scope", "global")
                put("payload_json", "{}")
            }
        )

        assertEquals(MemoryTableScopeType.GLOBAL, captured?.scopeType)
        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, captured?.scopeId)
    }

    @Test
    fun upsertRowsPreservesExistingScopeWhenScopeOmitted() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document("doc", MemoryTableScopeType.CONVERSATION, "conversation-a")
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            readDocuments = { error("unexpected read") },
            getDocument = { existing },
            upsertDocument = {
                captured = it
                it
            },
            deleteDocument = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put("payload_json", """{"updated":true}""")
            }
        )

        assertEquals(MemoryTableScopeType.CONVERSATION, captured?.scopeType)
        assertEquals("conversation-a", captured?.scopeId)
    }

    @Test
    fun conversationScopeRequiresConversationId() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
        ).single()

        val failure = runCatching {
            tool.execute(
                buildJsonObject {
                    put("action", "upsert_rows")
                    put("template_id", "template")
                    put("scope", "conversation")
                    put("payload_json", "{}")
                }
            )
        }.exceptionOrNull()

        assertTrue(failure?.message?.contains("conversation scope is unavailable") == true)
    }

    private fun document(
        id: String,
        scopeType: MemoryTableScopeType,
        scopeId: String,
    ) = MemoryTableDocument(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = """{"id":"$id"}""",
    )
}
