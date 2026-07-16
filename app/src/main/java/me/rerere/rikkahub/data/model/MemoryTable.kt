package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.uuid.Uuid

@Serializable
data class MemoryTableTemplate(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val description: String = DEFAULT_MEMORY_TABLE_DESCRIPTION,
    val schemaJson: String = DEFAULT_MEMORY_TABLE_SCHEMA_JSON,
    val scopeType: MemoryTableScopeType = MemoryTableScopeType.GLOBAL,
    val scopeId: String = MEMORY_TABLE_GLOBAL_SCOPE_ID,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class MemoryTableDocument(
    val id: String = Uuid.random().toString(),
    val templateId: String,
    val scopeType: MemoryTableScopeType = MemoryTableScopeType.ASSISTANT,
    val scopeId: String,
    val payloadJson: String = "{}",
    val revision: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    // #89: id of the assistant-scoped document this document mirrors when following its source.
    val sourceDocumentId: String? = null,
    // #89: true = payload mirrors the source document; false = detached for independent edits.
    val followSource: Boolean = false,
)

@Serializable
enum class MemoryTableScopeType {
    CONVERSATION,
    ASSISTANT,
    GLOBAL;

    companion object {
        fun fromStorage(value: String?): MemoryTableScopeType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ASSISTANT
        }
    }
}

fun isMemoryTableScopeEffective(
    scopeType: String,
    scopeId: String,
    assistantId: String,
    conversationId: String? = null,
): Boolean = when (scopeType) {
    MemoryTableScopeType.GLOBAL.name -> true
    MemoryTableScopeType.ASSISTANT.name -> scopeId == assistantId
    MemoryTableScopeType.CONVERSATION.name -> conversationId != null && scopeId == conversationId
    else -> false
}

fun MemoryTableDocument.isEffectiveFor(
    assistantId: String,
    conversationId: String? = null,
): Boolean = isMemoryTableScopeEffective(
    scopeType = scopeType.name,
    scopeId = scopeId,
    assistantId = assistantId,
    conversationId = conversationId,
)

fun isMemoryTableTemplateScopeEffective(
    scopeType: String,
    scopeId: String,
    assistantId: String,
): Boolean = when (scopeType) {
    MemoryTableScopeType.GLOBAL.name -> scopeId == MEMORY_TABLE_GLOBAL_SCOPE_ID
    MemoryTableScopeType.ASSISTANT.name -> scopeId == assistantId
    else -> false
}

fun MemoryTableTemplate.isEffectiveFor(assistantId: String): Boolean =
    isMemoryTableTemplateScopeEffective(
        scopeType = scopeType.name,
        scopeId = scopeId,
        assistantId = assistantId,
    )

data class MemoryCapabilities(
    val normalMemoryEnabled: Boolean,
    val memoryTableEnabled: Boolean,
)

fun resolveMemoryCapabilities(
    normalMemoryEnabled: Boolean,
    settingsMemoryTableEnabled: Boolean,
    assistantMemoryTableEnabled: Boolean,
): MemoryCapabilities = MemoryCapabilities(
    normalMemoryEnabled = normalMemoryEnabled,
    memoryTableEnabled = settingsMemoryTableEnabled && assistantMemoryTableEnabled,
)

fun shouldEnableMemoryTable(settingsEnabled: Boolean, assistantEnabled: Boolean): Boolean =
    resolveMemoryCapabilities(
        normalMemoryEnabled = false,
        settingsMemoryTableEnabled = settingsEnabled,
        assistantMemoryTableEnabled = assistantEnabled,
    ).memoryTableEnabled

fun normalizeMemoryTableSchemaJson(schemaJson: String): String =
    schemaJson.trim().ifBlank { DEFAULT_MEMORY_TABLE_SCHEMA_JSON.trimIndent() }

fun normalizeMemoryTablePayloadJson(payloadJson: String): String =
    payloadJson.trim().ifBlank { "{}" }

fun validateMemoryTableSchemaJson(schemaJson: String) {
    val root = parseMemoryTableJsonObject(schemaJson, "schemaJson")
    val tables = root["tables"] as? JsonArray
        ?: invalidMemoryTableJson("schemaJson.tables must be a non-empty array")
    if (tables.isEmpty()) {
        invalidMemoryTableJson("schemaJson.tables must be a non-empty array")
    }
    tables.forEachIndexed { tableIndex, tableElement ->
        val table = tableElement as? JsonObject
            ?: invalidMemoryTableJson("schemaJson.tables[$tableIndex] must be an object")
        val tableName = table.stringValue("name")
        if (tableName.isNullOrBlank()) {
            invalidMemoryTableJson("schemaJson.tables[$tableIndex].name must not be blank")
        }
        val columns = table["columns"] as? JsonArray
            ?: invalidMemoryTableJson("schemaJson.tables[$tableIndex].columns must be a non-empty array")
        if (columns.isEmpty()) {
            invalidMemoryTableJson("schemaJson.tables[$tableIndex].columns must be a non-empty array")
        }
        columns.forEachIndexed { columnIndex, columnElement ->
            val column = columnElement as? JsonObject
                ?: invalidMemoryTableJson(
                    "schemaJson.tables[$tableIndex].columns[$columnIndex] must be an object"
                )
            if (column.stringValue("name").isNullOrBlank()) {
                invalidMemoryTableJson(
                    "schemaJson.tables[$tableIndex].columns[$columnIndex].name must not be blank"
                )
            }
        }
        // #93: injectPolicy is an optional per-table injection gate. When present it
        // must be an object whose known boolean flags (enabled/triggerSend) are actual
        // booleans, so a malformed policy is rejected instead of silently ignored.
        validateMemoryTablePolicy(
            table["injectPolicy"],
            "schemaJson.tables[$tableIndex].injectPolicy",
        )
        // updatePolicy shares the same shape and is consumed by MemoryTableTools
        // to gate AI row writes for tables whose enabled flag is false.
        validateMemoryTablePolicy(
            table["updatePolicy"],
            "schemaJson.tables[$tableIndex].updatePolicy",
        )
    }
}

// Validates the optional per-table policy object. A missing policy is fine (defaults
// apply). When present it must be a JSON object, and any recognized boolean flag that
// is supplied must be a JSON boolean.
private fun validateMemoryTablePolicy(policyElement: JsonElement?, fieldName: String) {
    if (policyElement == null) return
    val policy = policyElement as? JsonObject
        ?: invalidMemoryTableJson("$fieldName must be an object")
    listOf("enabled", "triggerSend").forEach { flag ->
        val value = policy[flag] as? JsonPrimitive ?: return@forEach
        if (value.booleanOrNull == null) {
            invalidMemoryTableJson("$fieldName.$flag must be a boolean")
        }
    }
}

fun validateMemoryTablePayloadJson(payloadJson: String) {
    parseMemoryTableJsonObject(payloadJson, "payloadJson")
}

private val memoryTableJson = Json {
    ignoreUnknownKeys = true
}

private fun parseMemoryTableJsonObject(json: String, fieldName: String): JsonObject {
    val element = try {
        memoryTableJson.parseToJsonElement(json)
    } catch (error: SerializationException) {
        invalidMemoryTableJson("$fieldName must be valid JSON", error)
    } catch (error: IllegalArgumentException) {
        invalidMemoryTableJson("$fieldName must be valid JSON", error)
    }
    return element as? JsonObject
        ?: invalidMemoryTableJson("$fieldName must be a JSON object")
}

private fun JsonObject.stringValue(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun invalidMemoryTableJson(message: String, cause: Throwable? = null): Nothing {
    throw IllegalArgumentException(message, cause)
}

// #93: a single table's per-table injection gate, surfaced to the editor UI so users
// can toggle "inject into prompt" per table without hand-editing schema JSON. Missing
// injectPolicy.enabled defaults to true (tables inject unless explicitly disabled).
data class MemoryTableInjectionToggle(
    val name: String,
    val injectEnabled: Boolean,
)

// Reads each table's name + injectPolicy.enabled from a schema JSON string. Returns an
// empty list when the schema cannot be parsed, so the UI degrades gracefully instead of
// crashing on malformed input.
fun readMemoryTableInjectionToggles(schemaJson: String): List<MemoryTableInjectionToggle> {
    return runCatching {
        val tables = memoryTableJson.parseToJsonElement(schemaJson)
            .let { it as? JsonObject }
            ?.get("tables") as? JsonArray
            ?: return@runCatching emptyList()
        tables.mapNotNull { element ->
            val table = element as? JsonObject ?: return@mapNotNull null
            val name = table.stringValue("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val enabled = (table["injectPolicy"] as? JsonObject)
                ?.get("enabled")
                ?.let { it as? JsonPrimitive }
                ?.booleanOrNull
                ?: true
            MemoryTableInjectionToggle(name = name, injectEnabled = enabled)
        }
    }.getOrDefault(emptyList())
}

// Returns a copy of the schema JSON with the named table's injectPolicy.enabled set to
// [enabled], preserving all other schema fields. Falls back to the original string when
// the schema cannot be parsed or the table is not found, so a bad edit never destroys
// the user's schema.
fun setMemoryTableInjectionEnabled(
    schemaJson: String,
    tableName: String,
    enabled: Boolean,
): String {
    return runCatching {
        val root = memoryTableJson.parseToJsonElement(schemaJson) as? JsonObject
            ?: return@runCatching schemaJson
        val tables = root["tables"] as? JsonArray ?: return@runCatching schemaJson
        var matched = false
        val updatedTables = JsonArray(
            tables.map { element ->
                val table = element as? JsonObject ?: return@map element
                if (table.stringValue("name") != tableName) return@map element
                matched = true
                val existingPolicy = (table["injectPolicy"] as? JsonObject)?.toMutableMap()
                    ?: mutableMapOf()
                existingPolicy["enabled"] = JsonPrimitive(enabled)
                JsonObject(
                    table.toMutableMap().apply {
                        put("injectPolicy", JsonObject(existingPolicy))
                    }
                )
            }
        )
        if (!matched) return@runCatching schemaJson
        JsonObject(root.toMutableMap().apply { put("tables", updatedTables) }).toString()
    }.getOrDefault(schemaJson)
}

// #100: versioned import/export bundle for memory table templates + documents.
const val MEMORY_TABLE_BUNDLE_VERSION = 2

@Serializable
data class MemoryTableBundle(
    val version: Int = MEMORY_TABLE_BUNDLE_VERSION,
    val templates: List<MemoryTableTemplate> = emptyList(),
    val documents: List<MemoryTableDocument> = emptyList(),
)

enum class MemoryTableImportConflictPolicy {
    // Keep existing rows, skip any incoming item whose id already exists.
    SKIP,

    // Overwrite existing items that share an id with an incoming item.
    OVERWRITE,

    // Assign fresh ids to every incoming item so nothing is overwritten.
    DUPLICATE,
}

data class MemoryTableImportPlan(
    val templates: List<MemoryTableTemplate>,
    val documents: List<MemoryTableDocument>,
    val skippedTemplateIds: List<String>,
    val skippedDocumentIds: List<String>,
)

private val memoryTableBundleJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun encodeMemoryTableBundle(
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
): String =
    memoryTableBundleJson.encodeToString(
        MemoryTableBundle.serializer(),
        MemoryTableBundle(templates = templates, documents = documents),
    )

fun decodeMemoryTableBundle(bundleJson: String): MemoryTableBundle {
    val bundle = try {
        memoryTableBundleJson.decodeFromString(MemoryTableBundle.serializer(), bundleJson)
    } catch (error: SerializationException) {
        throw IllegalArgumentException("memory table bundle must be valid JSON", error)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("memory table bundle must be valid JSON", error)
    }
    if (bundle.version > MEMORY_TABLE_BUNDLE_VERSION) {
        throw IllegalArgumentException(
            "unsupported memory table bundle version ${bundle.version}; " +
                "this build supports up to $MEMORY_TABLE_BUNDLE_VERSION"
        )
    }
    bundle.templates.forEach { validateMemoryTableSchemaJson(normalizeMemoryTableSchemaJson(it.schemaJson)) }
    bundle.documents.forEach { validateMemoryTablePayloadJson(normalizeMemoryTablePayloadJson(it.payloadJson)) }
    return bundle
}

// Resolves an import against the current store contents according to the conflict
// policy, remapping template ids for DUPLICATE so documents keep pointing at the
// freshly created templates.
fun resolveMemoryTableBundleImport(
    bundle: MemoryTableBundle,
    existingTemplateIds: Set<String>,
    existingDocumentIds: Set<String>,
    policy: MemoryTableImportConflictPolicy,
    newId: () -> String = { Uuid.random().toString() },
): MemoryTableImportPlan {
    val templates = mutableListOf<MemoryTableTemplate>()
    val documents = mutableListOf<MemoryTableDocument>()
    val skippedTemplateIds = mutableListOf<String>()
    val skippedDocumentIds = mutableListOf<String>()
    val templateIdRemap = mutableMapOf<String, String>()

    bundle.templates.forEach { template ->
        when {
            template.id !in existingTemplateIds -> templates += template
            policy == MemoryTableImportConflictPolicy.OVERWRITE -> templates += template
            policy == MemoryTableImportConflictPolicy.DUPLICATE -> {
                val fresh = newId()
                templateIdRemap[template.id] = fresh
                templates += template.copy(id = fresh)
            }

            else -> skippedTemplateIds += template.id
        }
    }

    bundle.documents.forEach { document ->
        val remappedTemplateId = templateIdRemap[document.templateId] ?: document.templateId
        when {
            document.id !in existingDocumentIds ->
                documents += document.copy(templateId = remappedTemplateId)

            policy == MemoryTableImportConflictPolicy.OVERWRITE ->
                documents += document.copy(templateId = remappedTemplateId)

            policy == MemoryTableImportConflictPolicy.DUPLICATE ->
                documents += document.copy(id = newId(), templateId = remappedTemplateId)

            else -> skippedDocumentIds += document.id
        }
    }

    return MemoryTableImportPlan(
        templates = templates,
        documents = documents,
        skippedTemplateIds = skippedTemplateIds,
        skippedDocumentIds = skippedDocumentIds,
    )
}

const val DEFAULT_MEMORY_TABLE_DESCRIPTION =
    "Structured memory rows for durable user preferences, profile details, and facts. Keep key stable for merges."

const val MEMORY_TABLE_GLOBAL_SCOPE_ID = "__global__"

const val DEFAULT_MEMORY_TABLE_SCHEMA_JSON = """
{
  "tables": [
    {
      "name": "memories",
      "columns": [
        { "name": "key", "type": "string", "description": "Stable row key for merge updates" },
        { "name": "category", "type": "string", "description": "Preference, profile, task, or other group" },
        { "name": "summary", "type": "string", "description": "Concise durable memory content" },
        { "name": "evidence", "type": "string", "description": "Short source note or reason to keep it" },
        { "name": "updated_at", "type": "string", "description": "Date or timestamp for the latest update" }
      ],
      "injectPolicy": { "enabled": true },
      "updatePolicy": { "enabled": true }
    }
  ],
  "maxInjectTokens": 800
}
"""
