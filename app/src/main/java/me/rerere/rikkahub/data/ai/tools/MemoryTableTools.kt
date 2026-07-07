package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.repository.MemoryRepository

private const val DEFAULT_ROW_BUSINESS_KEY = "key"

fun buildMemoryTableToolsIfEnabled(
    enabled: Boolean,
    json: Json,
    assistantId: String,
    conversationId: String? = null,
    readDocuments: suspend () -> List<MemoryTableDocument>,
    getDocument: suspend (String) -> MemoryTableDocument?,
    upsertDocument: suspend (MemoryTableDocument) -> MemoryTableDocument,
    deleteDocument: suspend (String) -> Unit,
    readTemplates: suspend () -> List<MemoryTableTemplate> = { emptyList() },
    upsertTemplate: suspend (MemoryTableTemplate) -> MemoryTableTemplate = { it },
): List<Tool> {
    if (!enabled) return emptyList()
    return buildMemoryTableTools(
        json = json,
        assistantId = assistantId,
        conversationId = conversationId,
        readDocuments = readDocuments,
        getDocument = getDocument,
        upsertDocument = upsertDocument,
        deleteDocument = deleteDocument,
        readTemplates = readTemplates,
        upsertTemplate = upsertTemplate,
    )
}

fun buildMemoryTableTools(
    json: Json,
    assistantId: String,
    conversationId: String? = null,
    readDocuments: suspend () -> List<MemoryTableDocument>,
    getDocument: suspend (String) -> MemoryTableDocument?,
    upsertDocument: suspend (MemoryTableDocument) -> MemoryTableDocument,
    deleteDocument: suspend (String) -> Unit,
    readTemplates: suspend () -> List<MemoryTableTemplate> = { emptyList() },
    upsertTemplate: suspend (MemoryTableTemplate) -> MemoryTableTemplate = { it },
): List<Tool> = listOf(
    Tool(
        name = "memory_table_tool",
        description = """
            Read or update structured memory table documents and their templates.
            Use `list_templates` to inspect available templates (id/name/description/schema) before writing,
            `create_template` to create a new template when no suitable one exists,
            `read` to inspect active documents, `upsert_rows` to create or replace payload JSON,
            `patch_rows` to merge a JSON object into an existing document payload,
            and `delete_rows` to delete a document.
            Recommended flow: `list_templates` → if none fits `create_template` → then `upsert_rows` with the returned template id.
            The schema is template-defined; do not invent table names outside the template.
            `scope` is optional for upsert: `conversation` stores only for this conversation,
            `assistant` stores for this assistant, and `global` shares across assistants.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("list_templates")
                            add("create_template")
                            add("read")
                            add("patch_rows")
                            add("upsert_rows")
                            add("delete_rows")
                        })
                    })
                    put("document_id", buildJsonObject {
                        put("type", "string")
                    })
                    put("template_id", buildJsonObject {
                        put("type", "string")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Template name, required for create_template.")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional template description for create_template.")
                    })
                    put("schema_json", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional template schema JSON for create_template; defaults to a facts(key,value) table.")
                    })
                    put("scope", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("conversation")
                            add("assistant")
                            add("global")
                        })
                    })
                    put("payload_json", buildJsonObject {
                        put("type", "string")
                    })
                },
                required = listOf("action"),
            )
        },
        execute = {
            val params = it.jsonObject
            val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val payload = when (action) {
                "list_templates" -> json.encodeToJsonElement(
                    ListSerializer(MemoryTableTemplate.serializer()),
                    readTemplates(),
                )

                "create_template" -> {
                    val name = params["name"]?.jsonPrimitive?.contentOrNull?.takeIf { n -> n.isNotBlank() }
                        ?: error("name is required for create_template")
                    val description = params["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val schemaJson = params["schema_json"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    json.encodeToJsonElement(
                        MemoryTableTemplate.serializer(),
                        upsertTemplate(
                            MemoryTableTemplate(
                                name = name,
                                description = description,
                                schemaJson = schemaJson,
                            )
                        ),
                    )
                }

                "read" -> json.encodeToJsonElement(
                    ListSerializer(MemoryTableDocument.serializer()),
                    readDocuments(),
                )
                "upsert_rows" -> {
                    val existingId = params["document_id"]?.jsonPrimitive?.contentOrNull
                    val old = existingId?.takeIf { id -> id.isNotBlank() }?.let { id -> getDocument(id) }
                    val templateId = params["template_id"]?.jsonPrimitive?.contentOrNull
                        ?: old?.templateId
                        ?: error("template_id is required")
                    val scopeType = params["scope"]
                        ?.toMemoryTableScopeType()
                        ?: old?.scopeType
                        ?: MemoryTableScopeType.ASSISTANT
                    val scopeId = scopeType.toScopeId(
                        assistantId = assistantId,
                        conversationId = conversationId,
                    )
                    val payloadJson = params["payload_json"]?.jsonPrimitive?.contentOrNull
                        ?: old?.payloadJson
                        ?: error("payload_json is required")
                    json.encodeToJsonElement(
                        MemoryTableDocument.serializer(),
                        upsertDocument(
                            (old ?: MemoryTableDocument(
                                templateId = templateId,
                                scopeType = scopeType,
                                scopeId = scopeId,
                            )).copy(
                                templateId = templateId,
                                scopeType = scopeType,
                                scopeId = scopeId,
                                payloadJson = payloadJson,
                            )
                        )
                    )
                }

                "patch_rows" -> {
                    val id = params["document_id"]?.jsonPrimitive?.contentOrNull ?: error("document_id is required")
                    val old = getDocument(id) ?: error("memory table document not found: $id")
                    val patchJson = params["payload_json"]?.jsonPrimitive?.contentOrNull
                        ?: error("payload_json is required")
                    val merged = mergeTopLevelJsonObject(json, old.payloadJson, patchJson)
                    json.encodeToJsonElement(
                        MemoryTableDocument.serializer(),
                        upsertDocument(old.copy(payloadJson = merged)),
                    )
                }

                "delete_rows" -> {
                    val id = params["document_id"]?.jsonPrimitive?.contentOrNull ?: error("document_id is required")
                    deleteDocument(id)
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("document_id", id)
                    }
                }

                else -> error("unknown action: $action")
            }
            listOf(UIMessagePart.Text(payload.toString()))
        },
    )
)

private fun kotlinx.serialization.json.JsonElement.toMemoryTableScopeType(): MemoryTableScopeType {
    return when (jsonPrimitive.contentOrNull?.lowercase()) {
        "conversation" -> MemoryTableScopeType.CONVERSATION
        "global" -> MemoryTableScopeType.GLOBAL
        "assistant" -> MemoryTableScopeType.ASSISTANT
        else -> error("scope must be one of [conversation, assistant, global]")
    }
}

private fun MemoryTableScopeType.toScopeId(assistantId: String, conversationId: String?): String =
    when (this) {
        MemoryTableScopeType.CONVERSATION ->
            conversationId ?: error("conversation scope is unavailable outside a conversation")

        MemoryTableScopeType.ASSISTANT -> assistantId
        MemoryTableScopeType.GLOBAL -> MemoryRepository.GLOBAL_MEMORY_ID
    }

private fun mergeTopLevelJsonObject(json: Json, original: String, patch: String): String {
    val originalObject = runCatching { json.parseToJsonElement(original).jsonObject }.getOrNull()
    val patchObject = runCatching { json.parseToJsonElement(patch).jsonObject }.getOrNull()
    if (originalObject == null || patchObject == null) {
        return patch
    }
    return json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            originalObject.toMutableMap().apply {
                patchObject.forEach { (key, patchValue) ->
                    val originalValue = originalObject[key]
                    put(key, mergeTopLevelJsonValue(originalValue, patchValue))
                }
            },
        ),
    )
}

private fun mergeTopLevelJsonValue(originalValue: JsonElement?, patchValue: JsonElement): JsonElement {
    return if (originalValue is JsonArray && patchValue is JsonArray) {
        mergeKeyedRows(originalValue, patchValue)
    } else {
        patchValue
    }
}

private fun mergeKeyedRows(originalRows: JsonArray, patchRows: JsonArray): JsonElement {
    if (patchRows.isEmpty() || patchRows.any { it.rowKey() == null }) {
        return patchRows
    }
    val mergedRows = originalRows.toMutableList()
    patchRows.forEach { patchRow ->
        val patchKey = patchRow.rowKey()
        val originalIndex = patchKey.let { key ->
            mergedRows.indexOfFirst { row -> row.rowKey() == key }
        }
        if (originalIndex >= 0) {
            mergedRows[originalIndex] = mergeJsonObjectsOrReplace(mergedRows[originalIndex], patchRow)
        } else {
            mergedRows += patchRow
        }
    }
    return JsonArray(mergedRows)
}

private fun mergeJsonObjectsOrReplace(original: JsonElement, patch: JsonElement): JsonElement {
    if (original !is JsonObject || patch !is JsonObject) return patch
    return JsonObject(original.toMutableMap().apply { putAll(patch) })
}

private fun JsonElement.rowKey(): String? {
    val row = this as? JsonObject ?: return null
    return (row[DEFAULT_ROW_BUSINESS_KEY] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}
