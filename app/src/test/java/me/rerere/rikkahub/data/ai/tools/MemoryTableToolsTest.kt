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
import me.rerere.rikkahub.data.repository.MemoryTableSoftDeleteResult
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
            deleteDocument = {
                deletedId = it
                MemoryTableSoftDeleteResult.DELETED
            },
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
        var includingDeletedReads = 0
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { document("doc", MemoryTableScopeType.ASSISTANT, "assistant-a") },
            getDocumentIncludingDeleted = {
                includingDeletedReads++
                document("doc", MemoryTableScopeType.ASSISTANT, "assistant-a")
            },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = {
                deletedId = it
                MemoryTableSoftDeleteResult.DELETED
            },
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
        assertEquals(0, includingDeletedReads)

        val missing = tool.execute(
            buildJsonObject {
                put("action", "delete_document")
                put("document_id", "doc")
            }
        ).single() as UIMessagePart.Text
        val missingPayload = json.parseToJsonElement(missing.text).jsonObject
        assertEquals("false", missingPayload.getValue("success").jsonPrimitive.content)
        assertEquals(0, includingDeletedReads)
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
        assertEquals("moved_to_trash", payload.getValue("status").jsonPrimitive.content)
        assertEquals("doc", deletedId)
        assertEquals(1, includingDeletedReads)
    }

    @Test
    fun repeatedDeleteDocumentReportsAlreadyInTrash() = runBlocking {
        var deleteCalls = 0
        val deleted = document(
            "doc",
            MemoryTableScopeType.ASSISTANT,
            "assistant-a",
            deletedAt = 100,
            deletedBy = "user_ui",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            getDocumentIncludingDeleted = { deleted },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = {
                deleteCalls++
                MemoryTableSoftDeleteResult.ALREADY_DELETED
            },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_document")
                put("document_id", "doc")
                put("confirm_document_id", "doc")
            }
        ).single() as UIMessagePart.Text

        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("true", payload.getValue("success").jsonPrimitive.content)
        assertEquals("already_in_trash", payload.getValue("status").jsonPrimitive.content)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun deletedDocumentIsHiddenFromQueryAndRejectedByWrites() = runBlocking {
        var upsertCalls = 0
        var includingDeletedReads = 0
        val deleted = document(
            "doc",
            MemoryTableScopeType.ASSISTANT,
            "assistant-a",
            payloadJson = """{"facts":[]}""",
            deletedAt = 100,
            deletedBy = "user_ui",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { emptyList() },
            getDocument = { null },
            getDocumentIncludingDeleted = {
                includingDeletedReads++
                deleted
            },
            upsertDocument = {
                upsertCalls++
                it
            },
            deleteDocument = { error("unexpected delete") },
        ).single()

        val query = tool.execute(
            buildJsonObject {
                put("action", "query")
                put("document_id", "doc")
                put("table", "facts")
            }
        ).single() as UIMessagePart.Text
        val queryPayload = json.parseToJsonElement(query.text).jsonObject
        assertEquals("false", queryPayload.getValue("success").jsonPrimitive.content)
        assertTrue(queryPayload.getValue("error").jsonPrimitive.content.contains("not found"))
        assertEquals(0, includingDeletedReads)

        val apply = tool.execute(
            buildJsonObject {
                put("action", "apply_ops")
                put("document_id", "doc")
                put("ops", "[]")
            }
        ).single() as UIMessagePart.Text
        val applyPayload = json.parseToJsonElement(apply.text).jsonObject
        assertEquals("false", applyPayload.getValue("success").jsonPrimitive.content)
        assertTrue(applyPayload.getValue("error").jsonPrimitive.content.contains("in trash"))

        val upsert = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put("payload_json", "{}")
            }
        ).single() as UIMessagePart.Text
        val upsertPayload = json.parseToJsonElement(upsert.text).jsonObject
        assertEquals("false", upsertPayload.getValue("success").jsonPrimitive.content)
        assertTrue(upsertPayload.getValue("error").jsonPrimitive.content.contains("in trash"))
        assertEquals(2, includingDeletedReads)
        assertEquals(0, upsertCalls)
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
            upsertTemplate = { _, _ -> error("unexpected upsert template") },
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
        var capturedScope: MemoryTableScopeType? = MemoryTableScopeType.GLOBAL
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { error("unexpected read templates") },
            upsertTemplate = { template, requestedScopeType ->
                capturedScope = requestedScopeType
                val targetScope = requestedScopeType ?: MemoryTableScopeType.ASSISTANT
                val created = template.copy(
                    id = "generated-id",
                    scopeType = targetScope,
                    scopeId = when (targetScope) {
                        MemoryTableScopeType.GLOBAL -> MemoryRepository.GLOBAL_MEMORY_ID
                        MemoryTableScopeType.ASSISTANT -> "assistant-a"
                        MemoryTableScopeType.CONVERSATION -> error("unexpected conversation scope")
                    },
                )
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
        assertEquals(MemoryTableScopeType.ASSISTANT, captured?.scopeType)
        assertEquals(null, capturedScope)
        assertTrue(result.text.contains("generated-id"))
    }

    @Test
    fun createTemplatePassesExplicitGlobalScope() = runBlocking {
        var capturedTemplate: MemoryTableTemplate? = null
        var capturedScope: MemoryTableScopeType? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            upsertTemplate = { template, requestedScopeType ->
                capturedScope = requestedScopeType
                template.copy(
                    scopeType = requestedScopeType ?: MemoryTableScopeType.ASSISTANT,
                    scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
                ).also { capturedTemplate = it }
            },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "create_template")
                put("name", "shared")
                put("scope", "global")
            }
        )

        assertEquals(MemoryTableScopeType.GLOBAL, capturedTemplate?.scopeType)
        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, capturedTemplate?.scopeId)
        assertEquals(MemoryTableScopeType.GLOBAL, capturedScope)
    }

    @Test
    fun updateTemplatePersistsChangedFields() = runBlocking {
        var captured: MemoryTableTemplate? = null
        var capturedScope: MemoryTableScopeType? = MemoryTableScopeType.GLOBAL
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
            upsertTemplate = { template, requestedScopeType ->
                captured = template
                capturedScope = requestedScopeType
                template
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
        assertEquals(null, capturedScope)
        assertTrue(result.text.contains("new"))
    }

    @Test
    fun updateTemplatePassesExplicitAssistantScope() = runBlocking {
        var capturedScope: MemoryTableScopeType? = null
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(MemoryTableTemplate(id = "template", name = "shared")) },
            upsertTemplate = { template, requestedScopeType ->
                capturedScope = requestedScopeType
                template
            },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "update_template")
                put("template_id", "template")
                put("scope", "assistant")
            }
        )

        assertEquals(MemoryTableScopeType.ASSISTANT, capturedScope)
    }

    @Test
    fun templateActionsRejectConversationScope() = runBlocking {
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            upsertTemplate = { _, _ -> error("unexpected upsert template") },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "create_template")
                put("name", "invalid")
                put("scope", "conversation")
            }
        ).single() as UIMessagePart.Text

        assertTrue(result.text.contains("templates cannot use conversation scope"))

        val updateTool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { error("unexpected get") },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(MemoryTableTemplate(id = "template", name = "existing")) },
            upsertTemplate = { _, _ -> error("unexpected upsert template") },
        ).single()
        val updateResult = updateTool.execute(
            buildJsonObject {
                put("action", "update_template")
                put("template_id", "template")
                put("scope", "conversation")
            }
        ).single() as UIMessagePart.Text

        assertTrue(updateResult.text.contains("templates cannot use conversation scope"))
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
            upsertTemplate = { _, _ -> error("unexpected upsert template") },
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
    fun upsertRowsRejectsCreatingReadOnlyTable() = runBlocking {
        var upserted = false
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = {
                upserted = true
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "template")
                put("payload_json", """{"notes":[{"key":"private","value":"keep"}]}""")
            }
        ).single() as UIMessagePart.Text

        assertUpdatePolicyError(result, "notes")
        assertEquals(false, upserted)
    }

    @Test
    fun upsertRowsRejectsChangingOrRemovingReadOnlyTable() = runBlocking {
        var upsertCount = 0
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"facts":[{"key":"name","value":"Ada"}],"notes":[{"key":"private","value":"keep"}]}""",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { existing },
            upsertDocument = {
                upsertCount += 1
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
        ).single()

        val changed = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put("payload_json", """{"facts":[],"notes":[{"key":"private","value":"changed"}]}""")
            }
        ).single() as UIMessagePart.Text
        val removed = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put("payload_json", """{"facts":[]}""")
            }
        ).single() as UIMessagePart.Text

        assertUpdatePolicyError(changed, "notes")
        assertUpdatePolicyError(removed, "notes")
        assertEquals(0, upsertCount)
    }

    @Test
    fun upsertRowsDistinguishesMissingAndNullReadOnlyTableValues() = runBlocking {
        val existingPayloads = listOf(
            "{}" to """{"notes":null}""",
            """{"notes":null}""" to "{}",
        )

        existingPayloads.forEach { (oldPayload, newPayload) ->
            var upserted = false
            val existing = document(
                id = "doc",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
                payloadJson = oldPayload,
            )
            val tool = buildMemoryTableTools(
                json = json,
                assistantId = "assistant-a",
                readDocuments = { error("unexpected read") },
                getDocument = { existing },
                upsertDocument = {
                    upserted = true
                    it
                },
                deleteDocument = { error("unexpected delete") },
                readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
            ).single()

            val result = tool.execute(
                buildJsonObject {
                    put("action", "upsert_rows")
                    put("document_id", "doc")
                    put("payload_json", newPayload)
                }
            ).single() as UIMessagePart.Text

            assertUpdatePolicyError(result, "notes")
            assertEquals(false, upserted)
        }
    }

    @Test
    fun upsertRowsAllowsWritableChangesWhenReadOnlyTableIsUnchanged() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"facts":[{"key":"name","value":"Ada"}],"notes":[{"key":"private","value":"keep"}]}""",
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
            readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put(
                    "payload_json",
                    """{"facts":[{"key":"name","value":"Grace"}],"notes":[{"key":"private","value":"keep"}]}""",
                )
            }
        )

        val payload = json.parseToJsonElement(captured?.payloadJson.orEmpty()).jsonObject
        assertEquals("Grace", payload.getValue("facts").jsonArray[0].jsonObject.getValue("value").jsonPrimitive.content)
        assertEquals("keep", payload.getValue("notes").jsonArray[0].jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun upsertRowsAppliesOldTemplatePolicyWhenSwitchingTemplates() = runBlocking {
        var upserted = false
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"notes":[{"key":"private","value":"keep"}]}""",
        ).copy(templateId = "old-template")
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { existing },
            upsertDocument = {
                upserted = true
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = {
                listOf(
                    template(id = "old-template", schemaJson = mixedUpdatePolicySchemaJson()),
                    template(id = "new-template", schemaJson = factsAndNotesKeySchemaJson()),
                )
            },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("document_id", "doc")
                put("template_id", "new-template")
                put("payload_json", """{"notes":[{"key":"private","value":"changed"}]}""")
            }
        ).single() as UIMessagePart.Text

        assertUpdatePolicyError(result, "notes")
        assertEquals(false, upserted)
    }

    @Test
    fun updatePolicyDefaultsRemainWritable() = runBlocking {
        val schemas = listOf(
            factsKeySchemaJson(),
            factsUpdatePolicyWithoutEnabledSchemaJson(),
            factsMalformedUpdatePolicySchemaJson(),
        )
        schemas.forEachIndexed { index, schema ->
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
                readTemplates = { listOf(template(id = "template-$index", schemaJson = schema)) },
            ).single()

            tool.execute(
                buildJsonObject {
                    put("action", "upsert_rows")
                    put("template_id", "template-$index")
                    put("payload_json", """{"facts":[{"key":"name","value":"Ada"}]}""")
                }
            )

            assertTrue("schema $index should remain writable", captured != null)
        }

        var noTemplateCaptured: MemoryTableDocument? = null
        val noTemplateTool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { null },
            upsertDocument = {
                noTemplateCaptured = it
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { emptyList() },
        ).single()
        noTemplateTool.execute(
            buildJsonObject {
                put("action", "upsert_rows")
                put("template_id", "missing")
                put("payload_json", """{"facts":[]}""")
            }
        )
        assertTrue(noTemplateCaptured != null)
    }

    @Test
    fun patchRowsRejectsReadOnlyChangesAndAllowsWritableChanges() = runBlocking {
        var captured: MemoryTableDocument? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"facts":[{"key":"name","value":"Ada"}],"notes":[{"key":"private","value":"keep"}]}""",
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
            readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
        ).single()

        val rejected = tool.execute(
            buildJsonObject {
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"notes":[{"key":"private","value":"changed"}]}""")
            }
        ).single() as UIMessagePart.Text
        assertUpdatePolicyError(rejected, "notes")
        assertEquals(null, captured)

        tool.execute(
            buildJsonObject {
                put("action", "patch_rows")
                put("document_id", "doc")
                put("payload_json", """{"facts":[{"key":"name","value":"Grace"}]}""")
            }
        )

        val payload = json.parseToJsonElement(captured?.payloadJson.orEmpty()).jsonObject
        assertEquals("Grace", payload.getValue("facts").jsonArray[0].jsonObject.getValue("value").jsonPrimitive.content)
        assertEquals("keep", payload.getValue("notes").jsonArray[0].jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun applyOpsRejectsAllReadOnlyMutationTypesAtomically() = runBlocking {
        var upsertCount = 0
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"facts":[],"notes":[{"key":"private","value":"keep"}]}""",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { existing },
            upsertDocument = {
                upsertCount += 1
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
        ).single()
        val opsCases = listOf(
            """[{"type":"insert","table":"notes","row":{"key":"new","value":"x"}}]""",
            """
                [
                  {"type":"insert","table":"facts","row":{"key":"ok","value":"x"}},
                  {"type":"update","table":"notes","row":{"key":"private","value":"changed"}}
                ]
            """.trimIndent(),
            """[{"type":"delete","table":"notes","row_key_value":"private"}]""",
        )

        opsCases.forEach { ops ->
            val result = tool.execute(
                buildJsonObject {
                    put("action", "apply_ops")
                    put("document_id", "doc")
                    put("row_key", "key")
                    put("ops", ops)
                }
            ).single() as UIMessagePart.Text
            assertUpdatePolicyError(result, "notes")
        }

        assertEquals(0, upsertCount)
    }

    @Test
    fun deleteRowRejectsReadOnlyTableEvenWithExplicitRowKey() = runBlocking {
        var upserted = false
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"notes":[{"key":"private","value":"keep"}]}""",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { existing },
            upsertDocument = {
                upserted = true
                it
            },
            deleteDocument = { error("unexpected delete") },
            readTemplates = { listOf(template(schemaJson = mixedUpdatePolicySchemaJson())) },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_row")
                put("document_id", "doc")
                put("table", "notes")
                put("row_key", "key")
                put("row_key_value", "private")
            }
        ).single() as UIMessagePart.Text

        assertUpdatePolicyError(result, "notes")
        assertEquals(false, upserted)
    }

    @Test
    fun deleteDocumentIgnoresReadOnlyTablePolicy() = runBlocking {
        var deletedId: String? = null
        val existing = document(
            id = "doc",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
            payloadJson = """{"notes":[{"key":"private","value":"keep"}]}""",
        )
        val tool = buildMemoryTableTools(
            json = json,
            assistantId = "assistant-a",
            readDocuments = { error("unexpected read") },
            getDocument = { existing },
            upsertDocument = { error("unexpected upsert") },
            deleteDocument = {
                deletedId = it
                MemoryTableSoftDeleteResult.DELETED
            },
            readTemplates = { error("delete_document must not read table policy") },
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("action", "delete_document")
                put("document_id", "doc")
                put("confirm_document_id", "doc")
            }
        ).single() as UIMessagePart.Text

        assertEquals("true", json.parseToJsonElement(result.text).jsonObject.getValue("success").jsonPrimitive.content)
        assertEquals("doc", deletedId)
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

    private fun mixedUpdatePolicySchemaJson() = """
        {
          "tables": [
            {
              "name": "facts",
              "columns": [
                { "name": "key", "type": "string" },
                { "name": "value", "type": "string" }
              ],
              "updatePolicy": { "enabled": true }
            },
            {
              "name": "notes",
              "columns": [
                { "name": "key", "type": "string" },
                { "name": "value", "type": "string" }
              ],
              "updatePolicy": { "enabled": false }
            }
          ]
        }
    """.trimIndent()

    private fun factsUpdatePolicyWithoutEnabledSchemaJson() = """
        {
          "tables": [
            {
              "name": "facts",
              "columns": [{ "name": "key", "type": "string" }],
              "updatePolicy": { "triggerSend": false }
            }
          ]
        }
    """.trimIndent()

    private fun factsMalformedUpdatePolicySchemaJson() = """
        {
          "tables": [
            {
              "name": "facts",
              "columns": [{ "name": "key", "type": "string" }],
              "updatePolicy": { "enabled": "false" }
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
        deletedAt: Long? = null,
        deletedBy: String? = null,
    ) = MemoryTableDocument(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = payloadJson,
        deletedAt = deletedAt,
        deletedBy = deletedBy,
    )

    private fun assertUpdatePolicyError(result: UIMessagePart.Text, table: String) {
        val payload = json.parseToJsonElement(result.text).jsonObject
        assertEquals("false", payload.getValue("success").jsonPrimitive.content)
        val error = payload.getValue("error").jsonPrimitive.content
        assertTrue(error.contains("updatePolicy.enabled=false"))
        assertTrue(error.contains(table))
    }
}
