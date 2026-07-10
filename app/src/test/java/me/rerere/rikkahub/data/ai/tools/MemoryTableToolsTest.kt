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
    fun queryFiltersRowsByColumnValue() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = {
                document(
                    "doc",
                    MemoryTableScopeType.ASSISTANT,
                    "assistant-a",
                    payloadJson = """
                        {"facts":[
                          {"key":"name","value":"Alice"},
                          {"key":"city","value":"Berlin"},
                          {"key":"pet","value":"cat named Alice"}
                        ]}
                    """.trimIndent(),
                )
            },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
        ).single()

        val exact = tool.execute(
            buildJsonObject {
                put("action", "query")
                put("document_id", "doc")
                put("table", "facts")
                put("column", "value")
                put("value", "Alice")
            }
        ).single() as UIMessagePart.Text

        val exactPayload = json.parseToJsonElement(exact.text).jsonObject
        assertEquals("3", exactPayload.getValue("total").jsonPrimitive.content)
        assertEquals("1", exactPayload.getValue("matched").jsonPrimitive.content)
        assertEquals(1, exactPayload.getValue("rows").jsonArray.size)

        val substring = tool.execute(
            buildJsonObject {
                put("action", "query")
                put("document_id", "doc")
                put("table", "facts")
                put("value", "Alice")
                put("contains", true)
            }
        ).single() as UIMessagePart.Text

        val substringPayload = json.parseToJsonElement(substring.text).jsonObject
        assertEquals("2", substringPayload.getValue("matched").jsonPrimitive.content)
    }

    @Test
    fun applyOpsAppliesBatchAtomically() = runBlocking {
        var captured: MemoryTableDocument? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = {
                document(
                    "doc",
                    MemoryTableScopeType.ASSISTANT,
                    "assistant-a",
                    payloadJson = """{"facts":[{"key":"name","value":"Alice"},{"key":"city","value":"Berlin"}]}""",
                )
            },
            upsertDocument = {
                captured = it
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "apply_ops")
                put("document_id", "doc")
                put(
                    "ops",
                    """
                        [
                          {"type":"insert","table":"facts","row":{"key":"pet","value":"cat"}},
                          {"type":"update","table":"facts","row":{"key":"city","value":"Munich"}},
                          {"type":"delete","table":"facts","row_key_value":"name"}
                        ]
                    """.trimIndent(),
                )
            }
        ).single() as UIMessagePart.Text

        val facts = json.parseToJsonElement(captured!!.payloadJson).jsonObject
            .getValue("facts").jsonArray
        val byKey = facts.associate {
            it.jsonObject.getValue("key").jsonPrimitive.content to
                it.jsonObject.getValue("value").jsonPrimitive.content
        }
        assertEquals(mapOf("city" to "Munich", "pet" to "cat"), byKey)
        assertTrue(result.text.contains("Munich"))
    }

    @Test
    fun applyOpsFailsWithoutMutatingOnInvalidOp() = runBlocking {
        var upserted = false
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = {
                document(
                    "doc",
                    MemoryTableScopeType.ASSISTANT,
                    "assistant-a",
                    payloadJson = """{"facts":[{"key":"name","value":"Alice"}]}""",
                )
            },
            upsertDocument = {
                upserted = true
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
        ).single()

        // Second op targets a non-existent row; the whole batch must fail and not persist.
        val result = tool.execute(
            buildJsonObject {
                put("action", "apply_ops")
                put("document_id", "doc")
                put(
                    "ops",
                    """
                        [
                          {"type":"insert","table":"facts","row":{"key":"pet","value":"cat"}},
                          {"type":"update","table":"facts","row":{"key":"missing","value":"x"}}
                        ]
                    """.trimIndent(),
                )
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertEquals(false, upserted)
    }

    @Test
    fun readCanFilterDocumentsByScope() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            readDocuments = {
                listOf(
                    document("global", MemoryTableScopeType.GLOBAL, MemoryRepository.GLOBAL_MEMORY_ID),
                    document("assistant", MemoryTableScopeType.ASSISTANT, "assistant-a"),
                    document("conversation", MemoryTableScopeType.CONVERSATION, "conversation-a"),
                )
            },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "read")
                put("scope", "conversation")
            }
        ).single() as UIMessagePart.Text

        assertTrue(result.text.contains("conversation"))
        assertTrue(result.text.contains("CONVERSATION"))
        assertTrue(!result.text.contains("assistant"))
        assertTrue(!result.text.contains("global"))
    }

    @Test
    fun upsertRowsRejectsConversationScopedDocumentUntilUiExists() = runBlocking {
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

        val payload = json.parseToJsonElement(
            tool.execute(
                buildJsonObject {
                    put("action", "upsert_rows")
                    put("template_id", "template")
                    put("scope", "conversation")
                    put("payload_json", """{"topic":"local"}""")
                }
            ).single().let { (it as UIMessagePart.Text).text }
        ).jsonObject

        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("conversation scope writes are disabled"))
        assertEquals(null, captured)
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
    fun upsertRowsRejectsExistingConversationScopedDocument() = runBlocking {
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

        val result = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put("payload_json", """{"updated":true}""")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("conversation scope writes are disabled"))
        assertEquals(null, captured)
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
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
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
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
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
    fun patchRowsReturnsReadableErrorWhenPatchArrayHasNoRowKey() = runBlocking {
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

        val result = tool.execute(
            buildJsonObject {
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"items":[{"value":"new"}]}""")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("row key for table 'items'"))
        assertEquals(null, captured)
    }

    @Test
    fun patchRowsKeepsArrayRowsWhenPatchArrayIsEmpty() = runBlocking {
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
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
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
        assertEquals(1, facts.size)
        assertEquals("name", facts[0].jsonObject.getValue("key").jsonPrimitive.content)
    }

    @Test
    fun patchRowsMergesByTemplatePrimaryKeyWhenColumnIsNotKey() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """
                {"people":[{"person_id":"ada","name":"Ada","role":"engineer"},{"person_id":"linus","name":"Linus"}]}
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
            readTemplates = { listOf(template(schemaJson = peoplePrimaryKeySchemaJson())) },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "patch_rows")
                put("document_id", "doc")
                put(
                    "payload_json",
                    """{"people":[{"person_id":"ada","role":"reviewer"},{"person_id":"grace","name":"Grace"}]}""",
                )
            }
        )

        val people = json.parseToJsonElement(captured?.payloadJson.orEmpty())
            .jsonObject
            .getValue("people")
            .jsonArray
        assertEquals(3, people.size)
        assertEquals("ada", people[0].jsonObject.getValue("person_id").jsonPrimitive.content)
        assertEquals("Ada", people[0].jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals("reviewer", people[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("linus", people[1].jsonObject.getValue("person_id").jsonPrimitive.content)
        assertEquals("grace", people[2].jsonObject.getValue("person_id").jsonPrimitive.content)
    }

    @Test
    fun deleteRowsIsGuardedAndDoesNotDeleteDocument() = runBlocking {
        var deletedId: String? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { document("doc", MemoryTableScopeType.ASSISTANT, "assistant-a") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { deletedId = it },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_rows")
                put("document_id", "doc")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("delete_row"))
        assertEquals(null, deletedId)
    }

    @Test
    fun deleteDocumentRequiresMatchingConfirmation() = runBlocking {
        var deletedId: String? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { document("doc", MemoryTableScopeType.ASSISTANT, "assistant-a") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { deletedId = it },
        ).single()

        val rejected = tool.execute(
            buildJsonObject {
                put("action", "delete_document")
                put("document_id", "doc")
                put("confirm_document_id", "other-doc")
            }
        ).single() as UIMessagePart.Text

        val rejectedPayload = json.parseToJsonElement(rejected.text).jsonObject
        assertEquals("false", rejectedPayload.getValue("success").jsonPrimitive.content)
        assertTrue(rejectedPayload.getValue("error").jsonPrimitive.content.contains("confirm_document_id"))
        assertEquals(null, deletedId)

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_document")
                put("document_id", "doc")
                put("confirm_document_id", "doc")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("true", payload.getValue("success").jsonPrimitive.content)
        assertEquals("doc", payload.getValue("document_id").jsonPrimitive.content)
        assertEquals("doc", deletedId)
    }

    @Test
    fun deleteRowRemovesOneMatchingRowAndPreservesOtherTables() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """
                {"facts":[{"key":"name","value":"Ada"},{"key":"role","value":"engineer"}],"notes":[{"key":"role","value":"keep"}]}
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
            readTemplates = { listOf(template(schemaJson = factsAndNotesKeySchemaJson())) },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "delete_row")
                put("document_id", "doc")
                put("table", "facts")
                put("row_key_value", "role")
            }
        )

        val payload = json.parseToJsonElement(captured?.payloadJson.orEmpty()).jsonObject
        val facts = payload.getValue("facts").jsonArray
        val notes = payload.getValue("notes").jsonArray
        assertEquals(1, facts.size)
        assertEquals("name", facts[0].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals(1, notes.size)
        assertEquals("role", notes[0].jsonObject.getValue("key").jsonPrimitive.content)
    }

    @Test
    fun deleteRowReturnsReadableErrorWhenRowNotFound() = runBlocking {
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
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_row")
                put("document_id", "doc")
                put("table", "facts")
                put("row_key_value", "missing")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("row not found"))
        assertEquals(null, captured)
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

        val result = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "template")
                put("scope", "conversation")
                put("payload_json", "{}")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("conversation scope writes are disabled"))
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
    fun updateTemplatePersistsChangedFields() = runBlocking {
        var captured: MemoryTableTemplate? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = {
                listOf(
                    MemoryTableTemplate(
                        id = "template",
                        name = "old",
                        description = "old desc",
                        schemaJson = factsKeySchemaJson(),
                    )
                )
            },
            upsertTemplate = {
                captured = it
                it
            },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "update_template")
                put("template_id", "template")
                put("name", "new")
                put("description", "new desc")
            }
        ).single() as UIMessagePart.Text

        assertEquals("new", captured?.name)
        assertEquals("new desc", captured?.description)
        assertTrue(result.text.contains("new"))
    }

    @Test
    fun deleteTemplateRequiresConfirmation() = runBlocking {
        var deletedId: String? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = factsKeySchemaJson())) },
            deleteTemplate = {
                deletedId = it
                true
            },
        ).single()

        val rejected = tool.execute(
            buildJsonObject {
                put("action", "delete_template")
                put("template_id", "template")
                put("confirm_template_id", "other")
            }
        ).single() as UIMessagePart.Text

        val rejectedPayload = json.parseToJsonElement(rejected.text).jsonObject
        assertEquals("false", rejectedPayload.getValue("success").jsonPrimitive.content)
        assertTrue(rejectedPayload.getValue("error").jsonPrimitive.content.contains("confirm_template_id"))
        assertEquals(null, deletedId)

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_template")
                put("template_id", "template")
                put("confirm_template_id", "template")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("true", payload.getValue("success").jsonPrimitive.content)
        assertEquals("template", deletedId)
    }

    @Test
    fun deleteTemplateReturnsReadableErrorWhenTemplateIsMissing() = runBlocking {
        var deletedId: String? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { emptyList() },
            deleteTemplate = {
                deletedId = it
                true
            },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_template")
                put("template_id", "missing")
                put("confirm_template_id", "missing")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("memory table template not found"))
        assertEquals(null, deletedId)
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

        val result = tool.execute(
            buildJsonObject {
                put("action", "create_template")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("name is required"))
    }

    @Test
    fun upsertRowsReturnsReadableErrorWhenWriteFails() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = { error("payload_json must be a JSON object") },
            deleteDocument = { error("unexpected delete") },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "template")
                put("payload_json", "[]")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("payload_json"))
    }

    private fun template(
        id: String = "template",
        schemaJson: String,
    ) = MemoryTableTemplate(
        id = id,
        schemaJson = schemaJson,
    )

    private fun factsKeySchemaJson() = """
        {
          "tables": [
            {
              "name": "facts",
              "columns": [
                { "name": "key", "type": "string" },
                { "name": "value", "type": "string" }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun factsAndNotesKeySchemaJson() = """
        {
          "tables": [
            {
              "name": "facts",
              "columns": [
                { "name": "key", "type": "string" },
                { "name": "value", "type": "string" }
              ]
            },
            {
              "name": "notes",
              "columns": [
                { "name": "key", "type": "string" },
                { "name": "value", "type": "string" }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun peoplePrimaryKeySchemaJson() = """
        {
          "tables": [
            {
              "name": "people",
              "columns": [
                { "name": "person_id", "type": "string", "primaryKey": true },
                { "name": "name", "type": "string" },
                { "name": "role", "type": "string" }
              ]
            }
          ]
        }
    """.trimIndent()

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
