package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class MemoryTableOperationPolicy(
    val strictSchema: Boolean = true,
    val explicitRowKey: String? = null,
    val allowUnknownTables: Boolean = false,
    val externallyReadOnlyTables: Set<String> = emptySet(),
)

data class MemoryTableOperationApplication(
    val payloadJson: String,
    val operationCount: Int,
    val operationSummaryJson: String,
    val diffSummaryJson: String,
)

class MemoryTableOperationException(message: String) : IllegalArgumentException(message)

fun applyValidatedMemoryTableOperations(
    json: Json,
    schemaJson: String,
    payloadJson: String,
    operations: JsonArray,
    maxOperations: Int,
    policy: MemoryTableOperationPolicy = MemoryTableOperationPolicy(),
): MemoryTableOperationApplication {
    if (operations.isEmpty()) operationError("operations must not be empty")
    if (operations.size > maxOperations) {
        operationError("operation count ${operations.size} exceeds limit $maxOperations")
    }
    val schema = parseOperationSchema(json, schemaJson, policy)
    val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull()
        ?.toMutableMap()
        ?: operationError("memory table payload must be a JSON object")
    val counts = linkedMapOf("insert" to 0, "update" to 0, "delete" to 0)
    val diffs = mutableListOf<JsonObject>()

    operations.forEachIndexed { index, element ->
        val operation = element as? JsonObject ?: operationError("operations[$index] must be an object")
        val type = operation.requiredString("type", "operations[$index]").lowercase()
        val tableName = operation.requiredString("table", "operations[$index]")
        val table = schema[tableName]
        if (table == null && !policy.allowUnknownTables) {
            operationError("operations[$index] references unknown table '$tableName'")
        }
        if (table?.readOnly == true || tableName in policy.externallyReadOnlyTables) {
            operationError("operations[$index] cannot modify read-only table '$tableName'")
        }
        val rowKey = policy.explicitRowKey?.takeIf { it.isNotBlank() }
            ?: table?.rowKey
            ?: DEFAULT_MEMORY_TABLE_ROW_KEY
        val rows = when (val value = payload[tableName]) {
            null -> JsonArray(emptyList())
            is JsonArray -> value
            else -> operationError("payload table '$tableName' must be an array")
        }
        if (policy.strictSchema && table != null) {
            rows.forEachIndexed { rowIndex, row ->
                validateRow(row, table, "payload.$tableName[$rowIndex]", requireKey = true)
            }
        }

        val updatedRows = when (type) {
            "insert", "update" -> {
                if (operation.keys != setOf("type", "table", "row")) {
                    operationError("operations[$index] has invalid keys for $type")
                }
                val row = operation["row"] as? JsonObject
                    ?: operationError("operations[$index].row must be an object")
                if (table != null) validateRow(row, table, "operations[$index].row", requireKey = true)
                val keyValue = row.keyValue(rowKey)
                    ?: operationError("operations[$index].row is missing row key '$rowKey'")
                val existingIndex = rows.indexOfFirst { it.keyValue(rowKey) == keyValue }
                when {
                    type == "insert" && existingIndex >= 0 ->
                        operationError("insert conflicts with existing '$rowKey=$keyValue' in '$tableName'")

                    type == "update" && existingIndex < 0 ->
                        operationError("update target '$rowKey=$keyValue' not found in '$tableName'")

                    existingIndex >= 0 -> JsonArray(rows.toMutableList().apply {
                        val existing = this[existingIndex] as? JsonObject
                            ?: operationError("existing row '$rowKey=$keyValue' must be an object")
                        set(existingIndex, JsonObject(existing.toMutableMap().apply { putAll(row) }))
                    })

                    else -> JsonArray(rows + row)
                }.also {
                    diffs += operationDiff(type, tableName, rowKey, keyValue)
                }
            }

            "delete" -> {
                if (operation.keys != setOf("type", "table", "row_key_value")) {
                    operationError("operations[$index] has invalid keys for delete")
                }
                val keyValue = operation.requiredString("row_key_value", "operations[$index]")
                val existingIndex = rows.indexOfFirst { it.keyValue(rowKey) == keyValue }
                if (existingIndex < 0) {
                    operationError("delete target '$rowKey=$keyValue' not found in '$tableName'")
                }
                diffs += operationDiff(type, tableName, rowKey, keyValue)
                JsonArray(rows.filterIndexed { rowIndex, _ -> rowIndex != existingIndex })
            }

            else -> operationError("operations[$index].type must be one of [insert, update, delete]")
        }
        counts[type] = counts.getValue(type) + 1
        payload[tableName] = updatedRows
    }

    val operationSummary = buildJsonObject {
        counts.forEach { (type, count) -> put(type, count) }
    }
    val diffSummary = buildJsonArray { diffs.forEach(::add) }
    return MemoryTableOperationApplication(
        payloadJson = json.encodeToString(JsonObject.serializer(), JsonObject(payload)),
        operationCount = operations.size,
        operationSummaryJson = json.encodeToString(JsonObject.serializer(), operationSummary),
        diffSummaryJson = json.encodeToString(JsonArray.serializer(), diffSummary),
    )
}

private data class OperationColumn(
    val name: String,
    val type: String?,
)

private data class OperationTable(
    val name: String,
    val columns: Map<String, OperationColumn>,
    val rowKey: String,
    val readOnly: Boolean,
)

private fun parseOperationSchema(
    json: Json,
    schemaJson: String,
    policy: MemoryTableOperationPolicy,
): Map<String, OperationTable> {
    val root = runCatching { json.parseToJsonElement(schemaJson).jsonObject }.getOrNull()
        ?: operationError("memory table schema must be a JSON object")
    val tables = root["tables"] as? JsonArray ?: operationError("memory table schema tables must be an array")
    val result = linkedMapOf<String, OperationTable>()
    tables.forEachIndexed { tableIndex, tableElement ->
        val table = tableElement as? JsonObject ?: operationError("schema table[$tableIndex] must be an object")
        val name = table.requiredString("name", "schema table[$tableIndex]")
        if (result.containsKey(name)) operationError("duplicate schema table '$name'")
        val columnsArray = table["columns"] as? JsonArray
            ?: operationError("schema table '$name' columns must be an array")
        val columns = linkedMapOf<String, OperationColumn>()
        val primaryKeys = mutableListOf<String>()
        columnsArray.forEachIndexed { columnIndex, columnElement ->
            val column = columnElement as? JsonObject
                ?: operationError("schema table '$name' column[$columnIndex] must be an object")
            val columnName = column.requiredString("name", "schema table '$name' column[$columnIndex]")
            if (columns.containsKey(columnName)) operationError("duplicate column '$name.$columnName'")
            val type = (column["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()
            if (policy.strictSchema && type !in SUPPORTED_MEMORY_TABLE_COLUMN_TYPES) {
                operationError("unsupported column type for '$name.$columnName': ${type ?: "missing"}")
            }
            if ((column["primaryKey"] as? JsonPrimitive)?.booleanOrNull == true) primaryKeys += columnName
            columns[columnName] = OperationColumn(columnName, type)
        }
        if (primaryKeys.size > 1) operationError("schema table '$name' has multiple primary keys")
        val rowKey = policy.explicitRowKey?.takeIf { it.isNotBlank() }
            ?: primaryKeys.singleOrNull()
            ?: DEFAULT_MEMORY_TABLE_ROW_KEY.takeIf(columns::containsKey)
            ?: operationError("schema table '$name' requires one primaryKey column or a 'key' column")
        val readOnly = ((table["updatePolicy"] as? JsonObject)?.get("enabled") as? JsonPrimitive)
            ?.booleanOrNull == false
        result[name] = OperationTable(name, columns, rowKey, readOnly)
    }
    return result
}

private fun validateRow(value: JsonElement, table: OperationTable, path: String, requireKey: Boolean) {
    val row = value as? JsonObject ?: operationError("$path must be an object")
    val unknown = row.keys - table.columns.keys
    if (unknown.isNotEmpty()) operationError("$path contains unknown columns: ${unknown.sorted().joinToString()}")
    if (requireKey && row.keyValue(table.rowKey) == null) operationError("$path is missing row key '${table.rowKey}'")
    row.forEach { (name, cell) ->
        val column = table.columns.getValue(name)
        if (!cell.matchesColumnType(column.type)) {
            operationError("$path.$name does not match type ${column.type}")
        }
    }
}

private fun JsonElement.matchesColumnType(type: String?): Boolean = when (type) {
    null -> true
    "string" -> this is JsonPrimitive && isString
    "number" -> this is JsonPrimitive && !isString && doubleOrNull != null
    "integer" -> this is JsonPrimitive && !isString && longOrNull != null
    "boolean" -> this is JsonPrimitive && !isString && booleanOrNull != null
    "object" -> this is JsonObject
    "array" -> this is JsonArray
    else -> false
} && this !is JsonNull

private fun JsonObject.requiredString(name: String, path: String): String =
    (this[name] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: operationError("$path.$name must be a non-blank string")

private fun JsonElement.keyValue(rowKey: String): String? =
    (this as? JsonObject)
        ?.get(rowKey)
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

private fun operationDiff(type: String, table: String, rowKey: String, keyValue: String): JsonObject =
    buildJsonObject {
        put("type", type)
        put("table", table)
        put("rowKey", rowKey)
        put("key", keyValue)
    }

private fun operationError(message: String): Nothing = throw MemoryTableOperationException(message)

private const val DEFAULT_MEMORY_TABLE_ROW_KEY = "key"
private val SUPPORTED_MEMORY_TABLE_COLUMN_TYPES = setOf("string", "number", "integer", "boolean", "object", "array")
