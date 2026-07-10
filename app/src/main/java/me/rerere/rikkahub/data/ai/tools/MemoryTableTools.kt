package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
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
    deleteTemplate: suspend (String) -> Boolean = { false },
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
        deleteTemplate = deleteTemplate,
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
    deleteTemplate: suspend (String) -> Boolean = { false },
): List<Tool> = listOf(
    Tool(
        name = "memory_table_tool",
        description = """
            Read or update structured memory table documents and their templates.
            Use `list_templates` to inspect available templates (id/name/description/schema) before writing,
            `create_template` to create a new template when no suitable one exists,
            `update_template` to revise a template, `delete_template` to delete a confirmed template and its documents,
            `read` to inspect active documents, `query` to find rows in one table by column/value without loading the whole payload,
            `upsert_rows` to create or replace payload JSON,
            `patch_rows` to merge a JSON object into an existing document payload,
            `delete_row` to delete one row by table/key, and `delete_document` to delete a whole document.
            `apply_ops` applies an ordered `ops` array (insert/update/delete) atomically to one document; if any op fails nothing is written.
            Recommended flow: `list_templates` → if none fits `create_template` → then `upsert_rows` with the returned template id.
            The schema is template-defined; do not invent table names outside the template.
            Row identity uses explicit `row_key`, then the template column with primaryKey=true, then a template column named `key`.
            `scope` is optional for upsert: `conversation` is currently read-only until the conversation memory-table UI exists,
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
                            add("update_template")
                            add("delete_template")
                            add("read")
                            add("query")
                            add("apply_ops")
                            add("patch_rows")
                            add("upsert_rows")
                            add("delete_rows")
                            add("delete_document")
                            add("delete_row")
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
                        put("description", "Optional template schema JSON for create_template; defaults to a structured memories table.")
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
                    put("row_key", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional row identity column for patch_rows/delete_row.")
                    })
                    put("row_key_value", buildJsonObject {
                        put("type", "string")
                        put("description", "Required for delete_row; the row key value to delete.")
                    })
                    put("table", buildJsonObject {
                        put("type", "string")
                        put("description", "Required for delete_row and query; the top-level payload table name.")
                    })
                    put("column", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional for query; restrict matching to this column instead of any column.")
                    })
                    put("value", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional for query; match rows whose column value equals or contains this string.")
                    })
                    put("contains", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Optional for query; when true, match by substring instead of exact equality (default false).")
                    })
                    put("ops", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "Required for apply_ops; JSON array of ops applied atomically in order. " +
                                "Each op is {\"type\":\"insert|update|delete\",\"table\":\"...\"," +
                                "\"row\":{...} for insert/update, \"row_key_value\":\"...\" for update/delete}. " +
                                "All ops succeed or none are persisted."
                        )
                    })
                    put("confirm_document_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Required for delete_document and must exactly match document_id.")
                    })
                    put("confirm_template_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Required for delete_template and must exactly match template_id.")
                    })
                },
                required = listOf("action"),
            )
        },
        execute = {
            val payload = try {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
                when (action) {
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

                    "update_template" -> {
                        val templateId = params["template_id"]?.jsonPrimitive?.contentOrNull
                            ?: error("template_id is required for update_template")
                        val old = readTemplates().firstOrNull { template -> template.id == templateId }
                            ?: error("memory table template not found: $templateId")
                        json.encodeToJsonElement(
                            MemoryTableTemplate.serializer(),
                            upsertTemplate(
                                old.copy(
                                    name = params.stringParameter("name") ?: old.name,
                                    description = params.stringParameter("description")
                                        ?: old.description,
                                    schemaJson = params.stringParameter("schema_json")
                                        ?: old.schemaJson,
                                )
                            ),
                        )
                    }

                    "delete_template" -> {
                        val templateId = params["template_id"]?.jsonPrimitive?.contentOrNull
                            ?: error("template_id is required for delete_template")
                        val confirmation = params["confirm_template_id"]?.jsonPrimitive?.contentOrNull
                            ?: error("confirm_template_id is required for delete_template")
                        if (confirmation != templateId) {
                            error("confirm_template_id must match template_id for delete_template")
                        }
                        if (readTemplates().none { template -> template.id == templateId }) {
                            error("memory table template not found: $templateId")
                        }
                        if (!deleteTemplate(templateId)) {
                            error("memory table template not found: $templateId")
                        }
                        buildJsonObject {
                            put("success", JsonPrimitive(true))
                            put("template_id", templateId)
                            put("cascade", JsonPrimitive("documents_for_template_deleted_by_repository"))
                        }
                    }

                    "read" -> json.encodeToJsonElement(
                        ListSerializer(MemoryTableDocument.serializer()),
                        readDocuments().filterByScope(params["scope"]?.toMemoryTableScopeTypeOrNull()),
                    )

                    "query" -> {
                        val id = params["document_id"]?.jsonPrimitive?.contentOrNull
                            ?: error("document_id is required for query")
                        val table = params.stringParameter("table") ?: error("table is required for query")
                        val old = getDocument(id) ?: error("memory table document not found: $id")
                        val column = params.stringParameter("column")
                        val value = params.stringParameter("value")
                        val contains = params["contains"]?.jsonPrimitive?.booleanOrNull == true
                        queryMemoryTableRows(
                            json = json,
                            documentId = id,
                            payloadJson = old.payloadJson,
                            table = table,
                            column = column,
                            value = value,
                            contains = contains,
                        )
                    }

                    "apply_ops" -> {
                        val id = params["document_id"]?.jsonPrimitive?.contentOrNull
                            ?: error("document_id is required for apply_ops")
                        val old = getDocument(id) ?: error("memory table document not found: $id")
                        ensureWritableMemoryTableScope(old.scopeType)
                        val opsJson = params["ops"]?.jsonPrimitive?.contentOrNull
                            ?: error("ops is required for apply_ops")
                        val ops = runCatching { json.parseToJsonElement(opsJson) as? JsonArray }.getOrNull()
                            ?: error("ops must be a JSON array")
                        val explicitRowKey = params.stringParameter("row_key")
                        val templates = if (explicitRowKey == null) readTemplates() else emptyList()
                        val updatedPayload = applyMemoryTableOps(
                            json = json,
                            documentId = id,
                            templateId = old.templateId,
                            templates = templates,
                            explicitRowKey = explicitRowKey,
                            payloadJson = old.payloadJson,
                            ops = ops,
                        )
                        json.encodeToJsonElement(
                            MemoryTableDocument.serializer(),
                            upsertDocument(old.copy(payloadJson = updatedPayload)),
                        )
                    }

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
                        ensureWritableMemoryTableScope(scopeType)
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
                        ensureWritableMemoryTableScope(old.scopeType)
                        val patchJson = params["payload_json"]?.jsonPrimitive?.contentOrNull
                            ?: error("payload_json is required")
                        val explicitRowKey = params.stringParameter("row_key")
                        val templates = if (explicitRowKey == null) readTemplates() else emptyList()
                        val merged = mergeTopLevelJsonObject(
                            json = json,
                            templateId = old.templateId,
                            templates = templates,
                            explicitRowKey = explicitRowKey,
                            original = old.payloadJson,
                            patch = patchJson,
                        )
                        json.encodeToJsonElement(
                            MemoryTableDocument.serializer(),
                            upsertDocument(old.copy(payloadJson = merged)),
                        )
                    }

                    "delete_rows" -> {
                        error(
                            "delete_rows is guarded and does not delete documents. " +
                                "Use delete_row with document_id, table, and row_key_value, " +
                                "or delete_document with confirm_document_id."
                        )
                    }

                    "delete_document" -> {
                        val id = params["document_id"]?.jsonPrimitive?.contentOrNull ?: error("document_id is required")
                        val confirmation = params["confirm_document_id"]?.jsonPrimitive?.contentOrNull
                            ?: error("confirm_document_id is required for delete_document")
                        if (confirmation != id) {
                            error("confirm_document_id must match document_id for delete_document")
                        }
                        val old = getDocument(id) ?: error("memory table document not found: $id")
                        ensureWritableMemoryTableScope(old.scopeType)
                        deleteDocument(id)
                        buildJsonObject {
                            put("success", JsonPrimitive(true))
                            put("document_id", id)
                        }
                    }

                    "delete_row" -> {
                        val id = params["document_id"]?.jsonPrimitive?.contentOrNull ?: error("document_id is required")
                        val table = params.stringParameter("table") ?: error("table is required for delete_row")
                        val rowKeyValue = params.stringParameter("row_key_value")
                            ?: error("row_key_value is required for delete_row")
                        val old = getDocument(id) ?: error("memory table document not found: $id")
                        ensureWritableMemoryTableScope(old.scopeType)
                        val explicitRowKey = params.stringParameter("row_key")
                        val rowKey = resolveMemoryTableRowKey(
                            json = json,
                            templateId = old.templateId,
                            templates = if (explicitRowKey == null) readTemplates() else emptyList(),
                            table = table,
                            explicitRowKey = explicitRowKey,
                        ) ?: error(
                            "row key for table '$table' could not be resolved; " +
                                "provide row_key or define primaryKey/key in the template schema"
                        )
                        val updatedPayload = deleteMemoryTableRow(
                            json = json,
                            documentId = id,
                            payloadJson = old.payloadJson,
                            table = table,
                            rowKey = rowKey,
                            rowKeyValue = rowKeyValue,
                        )
                        json.encodeToJsonElement(
                            MemoryTableDocument.serializer(),
                            upsertDocument(old.copy(payloadJson = updatedPayload)),
                        )
                    }

                    else -> error("unknown action: $action")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                memoryTableToolError(error)
            }
            listOf(UIMessagePart.Text(payload.toString()))
        },
    )
)

private fun memoryTableToolError(error: Throwable): JsonObject =
    buildJsonObject {
        put("success", JsonPrimitive(false))
        put("error", JsonPrimitive(error.message ?: error::class.simpleName.orEmpty()))
    }

private fun kotlinx.serialization.json.JsonElement.toMemoryTableScopeType(): MemoryTableScopeType {
    return toMemoryTableScopeTypeOrNull()
        ?: error("scope must be one of [conversation, assistant, global]")
}

private fun kotlinx.serialization.json.JsonElement.toMemoryTableScopeTypeOrNull(): MemoryTableScopeType? {
    return when (jsonPrimitive.contentOrNull?.lowercase()) {
        "conversation" -> MemoryTableScopeType.CONVERSATION
        "global" -> MemoryTableScopeType.GLOBAL
        "assistant" -> MemoryTableScopeType.ASSISTANT
        null -> null
        else -> error("scope must be one of [conversation, assistant, global]")
    }
}

private fun List<MemoryTableDocument>.filterByScope(scopeType: MemoryTableScopeType?): List<MemoryTableDocument> =
    scopeType?.let { scope -> filter { document -> document.scopeType == scope } } ?: this

private fun ensureWritableMemoryTableScope(scopeType: MemoryTableScopeType) {
    if (scopeType == MemoryTableScopeType.CONVERSATION) {
        error(
            "conversation scope writes are disabled until conversation memory-table UI is available; " +
                "use assistant or global scope"
        )
    }
}

private fun MemoryTableScopeType.toScopeId(assistantId: String, conversationId: String?): String =
    when (this) {
        MemoryTableScopeType.CONVERSATION ->
            conversationId ?: error("conversation scope is unavailable outside a conversation")

        MemoryTableScopeType.ASSISTANT -> assistantId
        MemoryTableScopeType.GLOBAL -> MemoryRepository.GLOBAL_MEMORY_ID
    }

private fun JsonObject.stringParameter(name: String): String? =
    this[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

private fun mergeTopLevelJsonObject(
    json: Json,
    templateId: String,
    templates: List<MemoryTableTemplate>,
    explicitRowKey: String?,
    original: String,
    patch: String,
): String {
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
                    put(
                        key,
                        mergeTopLevelJsonValue(
                            table = key,
                            originalValue = originalValue,
                            patchValue = patchValue,
                            rowKey = resolveMemoryTableRowKey(
                                json = json,
                                templateId = templateId,
                                templates = templates,
                                table = key,
                                explicitRowKey = explicitRowKey,
                            ),
                        ),
                    )
                }
            },
        ),
    )
}

private fun mergeTopLevelJsonValue(
    table: String,
    originalValue: JsonElement?,
    patchValue: JsonElement,
    rowKey: String?,
): JsonElement {
    return if (patchValue is JsonArray) {
        val resolvedRowKey = rowKey ?: error(
            "row key for table '$table' could not be resolved; " +
                "provide row_key or define primaryKey/key in the template schema"
        )
        mergeKeyedRows(
            table = table,
            rowKey = resolvedRowKey,
            originalRows = originalValue as? JsonArray ?: JsonArray(emptyList()),
            patchRows = patchValue,
        )
    } else {
        patchValue
    }
}

private fun mergeKeyedRows(table: String, rowKey: String, originalRows: JsonArray, patchRows: JsonArray): JsonElement {
    if (patchRows.isEmpty()) {
        return originalRows
    }
    val mergedRows = originalRows.toMutableList()
    patchRows.forEach { patchRow ->
        val patchKey = patchRow.rowValue(rowKey)
            ?: error("patch row for table '$table' is missing row key '$rowKey'")
        val originalIndex = patchKey.let { key ->
            mergedRows.indexOfFirst { row -> row.rowValue(rowKey) == key }
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

// Returns rows of a single table matching optional column/value filters, without
// loading or echoing the full document payload (#97 query action). When no filter
// is given, returns all rows of the table.
private fun queryMemoryTableRows(
    json: Json,
    documentId: String,
    payloadJson: String,
    table: String,
    column: String?,
    value: String?,
    contains: Boolean,
): JsonObject {
    val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull()
        ?: error("payload_json for memory table document $documentId must be a JSON object")
    val rows = payload[table] as? JsonArray
        ?: error("table '$table' not found in memory table document: $documentId")
    val matches = rows.filter { row ->
        if (value == null) return@filter true
        val obj = row as? JsonObject ?: return@filter false
        val cells = if (column != null) {
            listOfNotNull((obj[column] as? JsonPrimitive)?.contentOrNull)
        } else {
            obj.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }
        cells.any { cell -> if (contains) cell.contains(value) else cell == value }
    }
    return buildJsonObject {
        put("document_id", JsonPrimitive(documentId))
        put("table", JsonPrimitive(table))
        put("total", JsonPrimitive(rows.size))
        put("matched", JsonPrimitive(matches.size))
        put("rows", JsonArray(matches))
    }
}

private fun deleteMemoryTableRow(
    json: Json,
    documentId: String,
    payloadJson: String,
    table: String,
    rowKey: String,
    rowKeyValue: String,
): String {
    val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull()
        ?: error("payload_json for memory table document $documentId must be a JSON object")
    val rows = payload[table] as? JsonArray
        ?: error("table '$table' not found in memory table document: $documentId")
    val rowIndex = rows.indexOfFirst { row -> row.rowValue(rowKey) == rowKeyValue }
    if (rowIndex < 0) {
        error("row not found in table '$table' for $rowKey=$rowKeyValue")
    }
    val updatedRows = JsonArray(rows.filterIndexed { index, _ -> index != rowIndex })
    return json.encodeToString(
        JsonObject.serializer(),
        JsonObject(payload.toMutableMap().apply { put(table, updatedRows) }),
    )
}

private fun resolveMemoryTableRowKey(
    json: Json,
    templateId: String,
    templates: List<MemoryTableTemplate>,
    table: String,
    explicitRowKey: String?,
): String? {
    explicitRowKey?.takeIf { it.isNotBlank() }?.let { return it }
    val template = templates.firstOrNull { it.id == templateId } ?: return null
    val schema = runCatching { json.parseToJsonElement(template.schemaJson).jsonObject }.getOrNull() ?: return null
    val schemaTables = schema["tables"] as? JsonArray ?: return null
    val schemaTable = schemaTables
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.stringValue("name") == table }
        ?: return null
    val columns = (schemaTable["columns"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()
    return columns.firstOrNull { it.booleanValue("primaryKey") }?.stringValue("name")
        ?: columns.firstOrNull { it.stringValue("name") == DEFAULT_ROW_BUSINESS_KEY }?.stringValue("name")
}

// Applies a batch of ops (insert/update/delete) against a single document payload
// in memory and returns the resulting JSON. All ops are applied in order; if any
// op fails the whole batch throws before returning, so the caller never persists a
// partially mutated payload (#98 atomic batch writes).
private fun applyMemoryTableOps(
    json: Json,
    documentId: String,
    templateId: String,
    templates: List<MemoryTableTemplate>,
    explicitRowKey: String?,
    payloadJson: String,
    ops: JsonArray,
): String {
    if (ops.isEmpty()) error("ops must not be empty for apply_ops")
    val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull()
        ?.toMutableMap()
        ?: error("payload_json for memory table document $documentId must be a JSON object")
    val rowKeyByTable = mutableMapOf<String, String>()

    ops.forEachIndexed { index, opElement ->
        val op = opElement as? JsonObject ?: error("ops[$index] must be an object")
        val type = op.stringValue("type")?.lowercase()
            ?: error("ops[$index].type is required (insert, update, or delete)")
        val table = op.stringValue("table") ?: error("ops[$index].table is required")
        val rows = payload[table] as? JsonArray ?: JsonArray(emptyList())

        val updatedRows = when (type) {
            "insert", "update" -> {
                val row = op["row"] as? JsonObject
                    ?: error("ops[$index].row must be an object for $type")
                val rowKey = rowKeyByTable.getOrPut(table) {
                    resolveMemoryTableRowKey(
                        json = json,
                        templateId = templateId,
                        templates = templates,
                        table = table,
                        explicitRowKey = explicitRowKey,
                    ) ?: error(
                        "row key for table '$table' could not be resolved; " +
                            "provide row_key or define primaryKey/key in the template schema"
                    )
                }
                val keyValue = row.rowValue(rowKey)
                    ?: error("ops[$index].row is missing row key '$rowKey'")
                val existingIndex = rows.indexOfFirst { it.rowValue(rowKey) == keyValue }
                when {
                    type == "insert" && existingIndex >= 0 ->
                        error("ops[$index] insert conflicts with existing row '$rowKey=$keyValue' in '$table'")

                    type == "update" && existingIndex < 0 ->
                        error("ops[$index] update target '$rowKey=$keyValue' not found in '$table'")

                    existingIndex >= 0 -> JsonArray(
                        rows.toMutableList().apply {
                            set(existingIndex, mergeJsonObjectsOrReplace(this[existingIndex], row))
                        }
                    )

                    else -> JsonArray(rows + row)
                }
            }

            "delete" -> {
                val rowKey = rowKeyByTable.getOrPut(table) {
                    resolveMemoryTableRowKey(
                        json = json,
                        templateId = templateId,
                        templates = templates,
                        table = table,
                        explicitRowKey = explicitRowKey,
                    ) ?: error(
                        "row key for table '$table' could not be resolved; " +
                            "provide row_key or define primaryKey/key in the template schema"
                    )
                }
                val keyValue = op.stringValue("row_key_value")
                    ?: error("ops[$index].row_key_value is required for delete")
                val existingIndex = rows.indexOfFirst { it.rowValue(rowKey) == keyValue }
                if (existingIndex < 0) {
                    error("ops[$index] delete target '$rowKey=$keyValue' not found in '$table'")
                }
                JsonArray(rows.filterIndexed { i, _ -> i != existingIndex })
            }

            else -> error("ops[$index].type must be one of [insert, update, delete]")
        }
        payload[table] = updatedRows
    }

    return json.encodeToString(JsonObject.serializer(), JsonObject(payload))
}

private fun JsonObject.stringValue(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

private fun JsonObject.booleanValue(name: String): Boolean =
    (this[name] as? JsonPrimitive)
        ?.booleanOrNull == true

private fun JsonElement.rowValue(rowKey: String): String? {
    val row = this as? JsonObject ?: return null
    return (row[rowKey] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}
