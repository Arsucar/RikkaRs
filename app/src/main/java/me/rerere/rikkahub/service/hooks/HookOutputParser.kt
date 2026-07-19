package me.rerere.rikkahub.service.hooks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookRuntimeRules
import me.rerere.rikkahub.data.model.truncateHookReason
import kotlin.uuid.Uuid

sealed interface ParsedHookOutput {
    val decision: HookDecision
    val reason: String
    val reasonTruncated: Boolean
}

data class ParsedAddTagHookOutput(
    override val decision: HookDecision,
    val tagId: Uuid?,
    override val reason: String,
    override val reasonTruncated: Boolean,
) : ParsedHookOutput

data class ParsedTransitionConversationTagsHookOutput(
    override val decision: HookDecision,
    override val reason: String,
    override val reasonTruncated: Boolean,
) : ParsedHookOutput

enum class TagManageOpKind {
    ADD,
    REMOVE,
}

data class TagManageOperation(
    val kind: TagManageOpKind,
    val tagId: Uuid,
)

data class ParsedManageConversationTagsHookOutput(
    override val decision: HookDecision,
    val operations: List<TagManageOperation>,
    override val reason: String,
    override val reasonTruncated: Boolean,
) : ParsedHookOutput

data class ParsedMemoryTableSyncHookOutput(
    override val decision: HookDecision,
    val baseRevision: Int,
    val operations: JsonArray,
    override val reason: String,
    override val reasonTruncated: Boolean,
) : ParsedHookOutput

class HookOutputException(val code: HookErrorCode) : IllegalArgumentException(code.name)

object HookOutputParser {
    private val exactKeys = setOf("decision", "tagId", "reason")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String): ParsedAddTagHookOutput {
        if (raw != raw.trim()) throw HookOutputException(HookErrorCode.INVALID_JSON)
        val objectValue = runCatching { strictJson.parseToJsonElement(raw) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)

        val decisionValue = objectValue["decision"].stringValue()
        val reasonValue = objectValue["reason"].stringValue().trim()
        val truncatedReason = truncateHookReason(reasonValue)
        return when (decisionValue) {
            "apply" -> {
                val tagValue = objectValue["tagId"].stringValue()
                val tagId = runCatching { Uuid.parse(tagValue) }.getOrElse {
                    throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
                }
                ParsedAddTagHookOutput(
                    HookDecision.APPLY,
                    tagId,
                    truncatedReason.value,
                    truncatedReason.truncated,
                )
            }

            "skip" -> {
                if (objectValue["tagId"] !is JsonNull) {
                    throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
                }
                ParsedAddTagHookOutput(
                    HookDecision.SKIP,
                    null,
                    truncatedReason.value,
                    truncatedReason.truncated,
                )
            }

            else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String {
        val primitive = this as? JsonPrimitive
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (primitive.isString.not()) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        return primitive.contentOrNull ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
    }
}

object TransitionConversationTagsHookOutputParser {
    private val exactKeys = setOf("decision", "reason")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String): ParsedTransitionConversationTagsHookOutput {
        if (raw.length > HookRuntimeRules.MAX_TAG_TRANSITION_RESPONSE_CHARS) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        if (raw != raw.trim()) throw HookOutputException(HookErrorCode.INVALID_JSON)
        val objectValue = runCatching { strictJson.parseToJsonElement(raw) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val decision = when (objectValue["decision"].stringValue()) {
            "apply" -> HookDecision.APPLY
            "skip" -> HookDecision.SKIP
            else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val truncatedReason = truncateHookReason(objectValue["reason"].stringValue())
        return ParsedTransitionConversationTagsHookOutput(
            decision = decision,
            reason = truncatedReason.value,
            reasonTruncated = truncatedReason.truncated,
        )
    }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String {
        val primitive = this as? JsonPrimitive
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (!primitive.isString) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        return primitive.contentOrNull ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
    }
}

object ManageConversationTagsHookOutputParser {
    private val exactKeys = setOf("decision", "operations", "reason")
    private val operationKeys = setOf("op", "tagId")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String): ParsedManageConversationTagsHookOutput {
        val trimmed = raw.trim()
        if (trimmed.length > HookRuntimeRules.MAX_TAG_MANAGE_RESPONSE_CHARS) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val objectValue = runCatching { strictJson.parseToJsonElement(trimmed) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val decision = when (objectValue["decision"].stringValue()) {
            "apply" -> HookDecision.APPLY
            "skip" -> HookDecision.SKIP
            else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val operationsArray = objectValue["operations"] as? JsonArray
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (operationsArray.size > HookRuntimeRules.MAX_TAG_MANAGE_OPS) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        if (decision == HookDecision.SKIP && operationsArray.isNotEmpty()) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        if (decision == HookDecision.APPLY && operationsArray.isEmpty()) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val operations = operationsArray.map { element ->
            val opObject = element as? JsonObject
                ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
            if (opObject.keys != operationKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
            val kind = when (opObject["op"].stringValue()) {
                "add" -> TagManageOpKind.ADD
                "remove" -> TagManageOpKind.REMOVE
                else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
            }
            val tagId = runCatching { Uuid.parse(opObject["tagId"].stringValue()) }.getOrElse {
                throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
            }
            TagManageOperation(kind = kind, tagId = tagId)
        }
        val truncatedReason = truncateHookReason(objectValue["reason"].stringValue().trim())
        return ParsedManageConversationTagsHookOutput(
            decision = decision,
            operations = operations,
            reason = truncatedReason.value,
            reasonTruncated = truncatedReason.truncated,
        )
    }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String {
        val primitive = this as? JsonPrimitive
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (!primitive.isString) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        return primitive.contentOrNull ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
    }
}

object MemoryTableSyncHookOutputParser {
    private val exactKeys = setOf("decision", "baseRevision", "operations", "reason")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String, maxOperations: Int): ParsedMemoryTableSyncHookOutput {
        if (raw.length > HookRuntimeRules.MAX_SYNC_RESPONSE_CHARS) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        if (raw != raw.trim()) throw HookOutputException(HookErrorCode.INVALID_JSON)
        val objectValue = runCatching { strictJson.parseToJsonElement(raw) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val decision = when (objectValue["decision"].stringValue()) {
            "apply" -> HookDecision.APPLY
            "skip" -> HookDecision.SKIP
            else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val baseRevision = (objectValue["baseRevision"] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.intOrNull
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val operations = objectValue["operations"] as? JsonArray
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (operations.size > maxOperations || (decision == HookDecision.SKIP && operations.isNotEmpty())) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val truncatedReason = truncateHookReason(objectValue["reason"].stringValue().trim())
        return ParsedMemoryTableSyncHookOutput(
            decision = decision,
            baseRevision = baseRevision,
            operations = operations,
            reason = truncatedReason.value,
            reasonTruncated = truncatedReason.truncated,
        )
    }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String {
        val primitive = this as? JsonPrimitive
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (!primitive.isString) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        return primitive.contentOrNull ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
    }
}

object MemoryExperienceHookOutputParser {
    private val exactKeys = setOf(
        "should_remember",
        "deduplication_key",
        "symptom",
        "root_cause",
        "correction",
        "scope",
        "tools",
        "commands",
        "reason",
    )
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(
        raw: String,
        prepared: PreparedHookAction.SyncMemoryTable,
        nowEpochMillis: Long,
    ): ParsedMemoryTableSyncHookOutput {
        if (raw.length > HookRuntimeRules.MAX_SYNC_RESPONSE_CHARS || raw != raw.trim()) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val objectValue = runCatching { strictJson.parseToJsonElement(raw) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val shouldRemember = (objectValue["should_remember"] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.booleanOrNull
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val reason = objectValue.requiredSafeExperienceString("reason", allowBlank = false)
        val deduplicationKeyValue = objectValue.requiredSafeExperienceString("deduplication_key", allowBlank = !shouldRemember)
        val symptomValue = objectValue.requiredSafeExperienceString("symptom", allowBlank = !shouldRemember)
        val rootCauseValue = objectValue.requiredSafeExperienceString("root_cause", allowBlank = !shouldRemember)
        val correctionValue = objectValue.requiredSafeExperienceString("correction", allowBlank = !shouldRemember)
        val scopeValue = objectValue.requiredSafeExperienceString("scope", allowBlank = !shouldRemember)
        val truncatedReason = truncateHookReason(reason)
        val tools = objectValue.requiredSafeExperienceStrings("tools")
        val commands = objectValue.requiredSafeExperienceStrings("commands")
        if (!shouldRemember) {
            return ParsedMemoryTableSyncHookOutput(
                decision = HookDecision.SKIP,
                baseRevision = prepared.target.revision,
                operations = JsonArray(emptyList()),
                reason = truncatedReason.value,
                reasonTruncated = truncatedReason.truncated,
            )
        }

        val deduplicationKey = deduplicationKeyValue
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .take(160)
        val symptom = symptomValue
        val rootCause = rootCauseValue
        val correction = correctionValue
        val scope = scopeValue
        val table = parseExperienceTable(prepared.schemaJson, prepared.target.payloadJson)
        val existing = table.rows.firstOrNull { row ->
            row.stringCell(table.deduplicationColumn) == deduplicationKey
        }
        val rowKeyValue = existing?.stringCell(table.rowKey) ?: deduplicationKey
        val row = buildJsonObject {
            put(table.rowKey, rowKeyValue)
            table.putString(this, EXPERIENCE_DEDUP_COLUMNS, deduplicationKey)
            table.putExperienceSummary(this, symptom, rootCause, correction)
            table.putString(this, EXPERIENCE_ROOT_CAUSE_COLUMNS, rootCause)
            table.putString(this, EXPERIENCE_CORRECTION_COLUMNS, correction)
            table.putString(this, EXPERIENCE_SCOPE_COLUMNS, scope)
            table.putCollection(this, EXPERIENCE_TOOLS_COLUMNS, tools)
            table.putCollection(this, EXPERIENCE_COMMANDS_COLUMNS, commands)
            table.putString(
                this,
                EXPERIENCE_EVIDENCE_COLUMNS,
                "scope=$scope | symptom=$symptom | root_cause=$rootCause | correction=$correction".take(500),
            )
            table.putString(this, EXPERIENCE_TIME_COLUMNS, nowEpochMillis.toString())
            prepared.eventId?.let { table.putString(this, EXPERIENCE_SOURCE_EVENT_COLUMNS, it) }
            table.findColumn(EXPERIENCE_COUNT_COLUMNS)?.let { column ->
                val previous = existing?.get(column.name)?.jsonLongOrNull() ?: 0L
                when (column.type) {
                    "integer", "number" -> put(column.name, previous + 1L)
                    else -> Unit
                }
            }
        }
        if (row.keys.none { key -> key != table.rowKey && key != table.deduplicationColumn }) {
            throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_SCHEMA_UNSUPPORTED)
        }
        val operation = buildJsonObject {
            put("type", if (existing == null) "insert" else "update")
            put("table", table.name)
            put("row", row)
        }
        return ParsedMemoryTableSyncHookOutput(
            decision = HookDecision.APPLY,
            baseRevision = prepared.target.revision,
            operations = buildJsonArray { add(operation) },
            reason = truncatedReason.value,
            reasonTruncated = truncatedReason.truncated,
        )
    }

    private fun JsonObject.requiredSafeExperienceString(name: String, allowBlank: Boolean): String {
        val value = (get(name) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val safe = sanitizeMemoryExperienceText(value)
        if (safe == null) {
            if (allowBlank && value.isBlank()) return ""
            throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_REDACTION_FAILED)
        }
        return safe.take(500)
    }

    private fun JsonObject.requiredSafeExperienceStrings(name: String): List<String> {
        val values = get(name) as? JsonArray ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (values.size > 16) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        return values.map { element ->
            val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
            sanitizeMemoryExperienceText(value)?.take(200)
                ?: throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_REDACTION_FAILED)
        }
    }

    private fun parseExperienceTable(schemaJson: String, payloadJson: String): ExperienceTable {
        val schema = runCatching { strictJson.parseToJsonElement(schemaJson).jsonObject }.getOrNull()
            ?: throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_SCHEMA_UNSUPPORTED)
        val payload = if (payloadJson.trim() == "{}") {
            JsonObject(emptyMap())
        } else {
            runCatching { strictJson.parseToJsonElement(payloadJson).jsonObject }.getOrNull()
                ?: throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_DEDUP_FAILED)
        }
        val tables = schema["tables"] as? JsonArray
            ?: throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_SCHEMA_UNSUPPORTED)
        for (tableElement in tables) {
            val table = tableElement as? JsonObject ?: continue
            val writable = ((table["updatePolicy"] as? JsonObject)?.get("enabled") as? JsonPrimitive)
                ?.booleanOrNull != false
            if (!writable) continue
            val name = table.stringField("name") ?: continue
            val columnElements = table["columns"] as? JsonArray ?: continue
            val columns = columnElements.mapNotNull { element ->
                val column = element as? JsonObject ?: return@mapNotNull null
                val columnName = column.stringField("name") ?: return@mapNotNull null
                ExperienceColumn(
                    name = columnName,
                    normalizedName = normalizeExperienceColumn(columnName),
                    type = column.stringField("type")?.lowercase(),
                    primaryKey = (column["primaryKey"] as? JsonPrimitive)?.booleanOrNull == true,
                )
            }
            val rowKey = columns.singleOrNull { it.primaryKey }?.name
                ?: columns.firstOrNull { it.normalizedName == "key" }?.name
                ?: continue
            val dedup = columns.firstOrNull { it.normalizedName in EXPERIENCE_DEDUP_COLUMNS }
                ?: columns.firstOrNull { it.name == rowKey }
                ?: continue
            val hasDurableContent = columns.any { column ->
                column.type == "string" && column.normalizedName in EXPERIENCE_CONTENT_COLUMNS
            }
            if (!hasDurableContent) continue
            val rowsElement = payload[name] ?: JsonArray(emptyList())
            val rows = (rowsElement as? JsonArray)?.map { row ->
                row as? JsonObject ?: throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_DEDUP_FAILED)
            } ?: throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_DEDUP_FAILED)
            return ExperienceTable(name, rowKey, dedup.name, columns, rows)
        }
        throw HookOutputException(HookErrorCode.MEMORY_EXPERIENCE_SCHEMA_UNSUPPORTED)
    }

    private fun JsonObject.stringField(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

private data class ExperienceColumn(
    val name: String,
    val normalizedName: String,
    val type: String?,
    val primaryKey: Boolean,
)

private data class ExperienceTable(
    val name: String,
    val rowKey: String,
    val deduplicationColumn: String,
    val columns: List<ExperienceColumn>,
    val rows: List<JsonObject>,
) {
    fun findColumn(aliases: Set<String>): ExperienceColumn? = columns.firstOrNull { it.normalizedName in aliases }

    fun putString(target: kotlinx.serialization.json.JsonObjectBuilder, aliases: Set<String>, value: String) {
        findColumn(aliases)?.takeIf { it.type == null || it.type == "string" }?.let { target.put(it.name, value) }
    }

    fun putExperienceSummary(
        target: kotlinx.serialization.json.JsonObjectBuilder,
        symptom: String,
        rootCause: String,
        correction: String,
    ) {
        findColumn(EXPERIENCE_SYMPTOM_COLUMNS)?.takeIf { it.type == null || it.type == "string" }?.let { column ->
            val value = if (column.normalizedName == "summary") {
                "Symptom: $symptom; Root cause: $rootCause; Correction: $correction".take(500)
            } else {
                symptom
            }
            target.put(column.name, value)
        }
    }

    fun putCollection(
        target: kotlinx.serialization.json.JsonObjectBuilder,
        aliases: Set<String>,
        values: List<String>,
    ) {
        findColumn(aliases)?.let { column ->
            when (column.type) {
                "array" -> target.put(column.name, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                null, "string" -> target.put(column.name, values.joinToString(", "))
            }
        }
    }
}

private fun normalizeExperienceColumn(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
private fun JsonObject.stringCell(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun kotlinx.serialization.json.JsonElement.jsonLongOrNull(): Long? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

private val EXPERIENCE_DEDUP_COLUMNS = setOf("deduplication_key", "dedup_key", "key")
private val EXPERIENCE_SYMPTOM_COLUMNS = setOf("symptom", "problem", "issue", "error", "summary")
private val EXPERIENCE_ROOT_CAUSE_COLUMNS = setOf("root_cause", "rootcause", "cause")
private val EXPERIENCE_CORRECTION_COLUMNS = setOf("correction", "fix", "solution")
private val EXPERIENCE_SCOPE_COLUMNS = setOf("scope", "category")
private val EXPERIENCE_TOOLS_COLUMNS = setOf("tools", "related_tools", "tool")
private val EXPERIENCE_COMMANDS_COLUMNS = setOf("commands", "related_commands", "command")
private val EXPERIENCE_EVIDENCE_COLUMNS = setOf("latest_evidence", "evidence")
private val EXPERIENCE_TIME_COLUMNS = setOf("last_occurred_at", "last_seen_at", "updated_at", "updatedat")
private val EXPERIENCE_COUNT_COLUMNS = setOf("occurrence_count", "count", "times")
private val EXPERIENCE_SOURCE_EVENT_COLUMNS = setOf("source_event_id", "sourceeventid", "latest_source_event_id")
private val EXPERIENCE_CONTENT_COLUMNS = EXPERIENCE_SYMPTOM_COLUMNS + EXPERIENCE_ROOT_CAUSE_COLUMNS +
    EXPERIENCE_CORRECTION_COLUMNS + EXPERIENCE_EVIDENCE_COLUMNS
