package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
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
    fun patchRowsAddsNewFactRowWithoutDroppingExistingRows() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """
                {"facts":[{"key":"name","value":"Ada"},{"key":"role","value":"engineer"}],"topic":"old"}
            """.trimIndent(),
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
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
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"facts":[{"key":"acceptance","value":"testing merge"}],"topic":"new"}""")
            }
        )

        val payload = json.parseToJsonElement(captured?.payloadJson.orEmpty()).jsonObject
        val facts = payload.getValue("facts").jsonArray
        assertEquals("new", payload.getValue("topic").jsonPrimitive.content)
        assertEquals(3, facts.size)
        assertEquals("name", facts[0].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals("role", facts[1].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals("acceptance", facts[2].jsonObject.getValue("key").jsonPrimitive.content)
    }

    @Test
    fun patchRowsUpdatesExistingFactKeyWithoutDuplicating() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """
                {"facts":[{"key":"name","value":"Ada"},{"key":"role","value":"engineer","source":"profile"}]}
            """.trimIndent(),
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
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
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"facts":[{"key":"role","value":"reviewer"}]}""")
            }
        )

        val facts = json.parseToJsonElement(captured?.payloadJson.orEmpty())
            .jsonObject
            .getValue("facts")
            .jsonArray
        assertEquals(2, facts.size)
        assertEquals("name", facts[0].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals("role", facts[1].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals("reviewer", facts[1].jsonObject.getValue("value").jsonPrimitive.content)
        assertEquals("profile", facts[1].jsonObject.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun patchRowsReplacesArrayWhenPatchRowsHaveNoKey() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"items":[{"value":"old"}]}""",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
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
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"items":[{"value":"new"}]}""")
            }
        )

        val items = json.parseToJsonElement(captured?.payloadJson.orEmpty())
            .jsonObject
            .getValue("items")
            .jsonArray
        assertEquals(1, items.size)
        assertEquals("new", items[0].jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun patchRowsCanClearArrayWithEmptyPatchArray() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"facts":[{"key":"name","value":"Ada"}]}""",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
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
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"facts":[]}""")
            }
        )

        val facts = json.parseToJsonElement(captured?.payloadJson.orEmpty())
            .jsonObject
            .getValue("facts")
            .jsonArray
        assertEquals(0, facts.size)
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

    @Test
    fun listTemplatesReturnsTemplates() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = {
                listOf(
                    MemoryTableTemplate(id = "tpl-1", name = "user_memories", description = "prefs"),
                )
            },
            upsertTemplate = { error("unexpected upsert template") },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "list_templates")
            }
        ).single() as UIMessagePart.Text

        assertTrue(result.text.contains("tpl-1"))
        assertTrue(result.text.contains("user_memories"))
    }

    @Test
    fun createTemplatePersistsTemplateAndReturnsIt() = runBlocking {
        var captured: MemoryTableTemplate? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { error("unexpected read templates") },
            upsertTemplate = {
                val created = it.copy(id = "generated-id")
                captured = created
                created
            },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "create_template")
                put("name", "user_memories")
                put("description", "prefs")
            }
        ).single() as UIMessagePart.Text

        assertEquals("user_memories", captured?.name)
        assertTrue(result.text.contains("generated-id"))
    }

    @Test
    fun createTemplateRequiresName() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { error("unexpected read templates") },
            upsertTemplate = { error("unexpected upsert template") },
        ).single()

        val failure = runCatching {
            tool.execute(
                buildJsonObject {
                    put("action", "create_template")
                }
            )
        }.exceptionOrNull()

        assertTrue(failure?.message?.contains("name is required") == true)
    }

    private fun document(
        id: String,
        scopeType: MemoryTableScopeType,
        scopeId: String,
        payloadJson: String = """{"id":"$id"}""",
    ) = MemoryTableDocument(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = payloadJson,
    )
}
