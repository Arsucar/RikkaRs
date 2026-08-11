package me.rerere.rikkahub.data.ai.transformers

import android.util.Log

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.ai.prompts.resolveBuiltinOverride
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableTemplate

private const val MEMORY_TABLE_CHARS_PER_TOKEN = 4
private const val TAG = "MemoryTableInjectionTransformer"

// Manual injection placeholder (#99). When present in any message text, the
// rendered memory-table block replaces the placeholder instead of being appended
// to the system prompt, letting users control the injection position.
internal const val MEMORY_TABLE_MACRO = "{{memory_tables}}"

private val memoryTableSchemaJson = Json { ignoreUnknownKeys = true }

class MemoryTableInjectionTransformer(
    private val templates: List<MemoryTableTemplate>,
    private val documents: List<MemoryTableDocument>,
    private val maxDocuments: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
    private val maxTokens: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
    private val maxChars: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
) : InputMessageTransformer {
    override val previewPolicy: PreviewTransformPolicy = PreviewTransformPolicy.SideEffectFree
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (documents.isEmpty()) return messages
        val dataBlock = buildMemoryTablePrompt(
            templates = templates,
            documents = documents,
            maxDocuments = maxDocuments,
            maxTokens = maxTokens,
            maxChars = maxChars,
            recentConversationText = messages.recentConversationText(),
        )
        if (dataBlock.isBlank()) return messages

        // #182: 预设内启用的 memory_table_guide 覆盖只影响「引导文案」；实时数据块（dataBlock）
        // 仍由运行时文档渲染。覆盖文案里的 {{memory_tables}} 宏在此处替换为数据块，
        // 无宏时把数据块追加到文案之后（避免数据丢失）。无覆盖则维持原行为（仅注入数据块）。
        val guideOverride = resolveBuiltinOverride(
            ctx.assistant,
            ctx.settings.presets,
            BuiltinPromptRegistry.KEY_MEMORY_TABLE_GUIDE,
        )
        val content = if (guideOverride != null) {
            applyMemoryTableGuideOverride(guideOverride, dataBlock)
        } else {
            dataBlock
        }

        val result = messages.toMutableList()
        val systemIndex = result.indexOfFirst { it.role == me.rerere.ai.core.MessageRole.SYSTEM }
        if (systemIndex >= 0) {
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }
            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(mergeMemoryTableIntoSystemText(originalText, content)))
            )
        } else {
            result.add(0, UIMessage.system(content))
        }
        return result
    }
}

// #182: 把预设覆盖的引导文案与运行时数据块合并。覆盖文案含 {{memory_tables}} 宏时，
// 数据块替换到该位置（用户控制摆放）；不含宏时把数据块追加到文案之后，保证实时数据不丢失。
// 既替换 `{{memory_tables}}` 也替换 `{{ memory_tables }}`，与 BuiltinPromptRegistry 宏名一致。
internal fun applyMemoryTableGuideOverride(guideOverride: String, dataBlock: String): String {
    val hasMacro = guideOverride.contains(MEMORY_TABLE_MACRO) ||
        guideOverride.contains("{{ memory_tables }}")
    return if (hasMacro) {
        guideOverride
            .replace(MEMORY_TABLE_MACRO, dataBlock)
            .replace("{{ memory_tables }}", dataBlock)
    } else {
        "$guideOverride\n\n$dataBlock"
    }
}

// #99: if the user placed a manual macro in the system prompt, substitute the
// memory table content at that position instead of appending it, so the user
// controls placement and the content is not injected twice.
internal fun mergeMemoryTableIntoSystemText(originalText: String, content: String): String =
    if (originalText.contains(MEMORY_TABLE_MACRO)) {
        originalText.replace(MEMORY_TABLE_MACRO, content)
    } else {
        "$originalText\n\n$content"
    }

internal fun buildMemoryTablePrompt(
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    maxDocuments: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
    maxTokens: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
    maxChars: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
    recentConversationText: String = "",
): String {
    if (
        documents.isEmpty() ||
        maxDocuments?.let { it <= 0 } == true ||
        maxTokens?.let { it <= 0 } == true ||
        maxChars?.let { it <= 0 } == true
    ) {
        return ""
    }
    val templatesById = templates.associateBy { it.id }
    val schemaTokenLimit = templates
        .mapNotNull { extractMaxInjectTokens(it.schemaJson) }
        .minOrNull()
    val effectiveTokenLimit = minNullableLimit(maxTokens, schemaTokenLimit)
    val tokenDerivedCharLimit = effectiveTokenLimit?.let(::tokensToCharLimit)
    val effectiveCharLimit = minNullableLimit(maxChars, tokenDerivedCharLimit)
    val limitedDocuments = maxDocuments?.let { limit -> documents.take(limit) } ?: documents
    val omittedDocuments = documents.size - limitedDocuments.size
    val rendered = buildString {
        appendLine("<memory_tables>")
        appendLine("Structured memory table documents are active for this conversation.")
        appendLine(
            "Limits: documents=${limitedDocuments.size}/${documents.size}, " +
                "maxTokens=${effectiveTokenLimit.formatMemoryTableLimit()}, " +
                "maxChars=${effectiveCharLimit.formatMemoryTableLimit()}."
        )
        limitedDocuments.forEach { document ->
            val template = templatesById[document.templateId]
            val disabledTables = template?.schemaJson?.let { disabledInjectionTables(it) }.orEmpty()
            val triggerSendTables = template?.schemaJson?.let { triggerSendTables(it) }.orEmpty()
            val payload = filterInjectablePayload(document.payloadJson, disabledTables)
                .let { filtered ->
                    filterRowsByRecentText(
                        payloadJson = filtered,
                        triggerSendTables = triggerSendTables,
                        recentConversationText = recentConversationText,
                    )
                }
            appendLine()
            appendLine("## ${template?.name ?: document.templateId}")
            template?.description?.takeIf { it.isNotBlank() }?.let { description ->
                appendLine("description=${description.trim()}")
            }
            appendLine("scope=${document.scopeType.name.lowercase()} revision=${document.revision}")
            appendLine("schema:")
            appendLine(slimSchemaForInjection(template?.schemaJson))
            appendLine("payload:")
            appendLine(payload.trim())
        }
        if (omittedDocuments > 0) {
            appendLine()
            appendLine("... $omittedDocuments memory table document(s) omitted by document limit.")
        }
        appendLine("</memory_tables>")
    }
    return effectiveCharLimit?.let { limit -> rendered.limitMemoryTableChars(limit) } ?: rendered
}

/**
 * #194: Strip engine/policy fields from schema JSON before system-prompt injection.
 * Keeps only tables[].name and columns[].{name, type, primaryKey} so the model can
 * address rows without carrying injectPolicy/updatePolicy/maxInjectTokens/descriptions.
 * Storage and list_templates still use the full schema; this is injection-only.
 *
 * - null/blank → `"{}"`
 * - invalid JSON → original string (no crash)
 */
internal fun slimSchemaForInjection(schemaJson: String?): String {
    val raw = schemaJson?.trim().orEmpty()
    if (raw.isBlank()) return "{}"
    return runCatching {
        val root = memoryTableSchemaJson.parseToJsonElement(raw) as? JsonObject
            ?: return@runCatching raw
        val tables = (root["tables"] as? JsonArray).orEmpty()
        val slimTables = tables.mapNotNull { element ->
            val table = element as? JsonObject ?: return@mapNotNull null
            val name = (table["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val columns = (table["columns"] as? JsonArray).orEmpty().mapNotNull { columnElement ->
                val column = columnElement as? JsonObject ?: return@mapNotNull null
                val columnName = (column["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                buildMap {
                    put("name", JsonPrimitive(columnName))
                    (column["type"] as? JsonPrimitive)?.let { put("type", it) }
                    (column["primaryKey"] as? JsonPrimitive)?.let { put("primaryKey", it) }
                }.let(::JsonObject)
            }
            JsonObject(
                mapOf(
                    "name" to JsonPrimitive(name),
                    "columns" to JsonArray(columns),
                ),
            )
        }
        JsonObject(mapOf("tables" to JsonArray(slimTables))).toString()
    }.onFailure { Log.w(TAG, "Failed to slim schema for injection", it) }
        .getOrDefault(raw)
}

internal fun minNullableLimit(first: Int?, second: Int?): Int? = when {
    first == null -> second
    second == null -> first
    else -> minOf(first, second)
}

internal fun tokensToCharLimit(tokens: Int): Int =
    (tokens.toLong() * MEMORY_TABLE_CHARS_PER_TOKEN.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

private fun Int?.formatMemoryTableLimit(): String = this?.toString() ?: "unlimited"

private fun extractMaxInjectTokens(schemaJson: String): Int? {
    return runCatching {
        memoryTableSchemaJson
            .parseToJsonElement(schemaJson)
            .jsonObject["maxInjectTokens"]
            ?.jsonPrimitive
            ?.intOrNull
            ?.takeIf { it > 0 }
    }.onFailure { Log.w(TAG, "Failed to extract maxInjectTokens from schema", it) }
        .getOrNull(); their payload
// data is excluded from the injected prompt (#93 per-table injection gate).
private fun disabledInjectionTables(schemaJson: String): Set<String> {
    return runCatching {
        val tables = memoryTableSchemaJson
            .parseToJsonElement(schemaJson)
            .jsonObject["tables"] as? JsonArray
            ?: return@runCatching emptySet()
        tables
            .mapNotNull { it as? JsonObject }
            .filter { table ->
                val enabled = (table["injectPolicy"] as? JsonObject)
                    ?.get("enabled")
                    ?.jsonPrimitive
                    ?.booleanOrNull
                enabled == false
            }
            .mapNotNull { (it["name"] as? JsonPrimitive)?.contentOrNull }
            .toSet()
    }.onFailure { Log.w(TAG, "Failed to extract disabled injection tables from schema", it) }
        .getOrNull().orEmpty() Returns the payload
// unchanged when nothing is disabled or the payload cannot be parsed as an object.
private fun filterInjectablePayload(payloadJson: String, disabledTables: Set<String>): String {
    if (disabledTables.isEmpty()) return payloadJson
    val payload = runCatching {
        memoryTableSchemaJson.parseToJsonElement(payloadJson).jsonObject
    }.onFailure { Log.w(TAG, "Failed to parse payload for injectable filter", it) }
        .getOrNull() ?: return payloadJson
    if (disabledTables.none { it in payload }) return payloadJson
    val filtered = JsonObject(payload.filterKeys { it !in disabledTables })
    return filtered.toString()
}

private fun String.limitMemoryTableChars(maxChars: Int): String {
    if (length <= maxChars) return this
    val marker = "\n... truncated by memory table limit\n</memory_tables>"
    if (maxChars <= marker.length) return take(maxChars)
    return take(maxChars - marker.length) + marker
}

// Names of tables whose schema sets injectPolicy.triggerSend == true; for these
// tables only rows relevant to the recent conversation are injected (#94).
private fun triggerSendTables(schemaJson: String): Set<String> {
    return runCatching {
        val tables = memoryTableSchemaJson
            .parseToJsonElement(schemaJson)
            .jsonObject["tables"] as? JsonArray
            ?: return@runCatching emptySet()
        tables
            .mapNotNull { it as? JsonObject }
            .filter { table ->
                (table["injectPolicy"] as? JsonObject)
                    ?.get("triggerSend")
                    ?.jsonPrimitive
                    ?.booleanOrNull == true
            }
            .mapNotNull { (it["name"] as? JsonPrimitive)?.contentOrNull }
            .toSet()
    }.onFailure { Log.w(TAG, "Failed to extract trigger-send tables from schema", it) }
        .getOrNull().orEmpty()
}

// For each trigger-send table, keeps only rows that have at least one cell value
// appearing in the recent conversation text. Non-trigger-send tables are left
// untouched. Returns the payload unchanged when there is nothing to filter or the
// payload/recent text is unusable, so injection degrades gracefully.
private fun filterRowsByRecentText(
    payloadJson: String,
    triggerSendTables: Set<String>,
    recentConversationText: String,
): String {
    if (triggerSendTables.isEmpty()) return payloadJson
    val corpus = recentConversationText.lowercase().takeIf { it.isNotBlank() } ?: return payloadJson
    val payload = runCatching {
        memoryTableSchemaJson.parseToJsonElement(payloadJson).jsonObject
    }.onFailure { Log.w(TAG, "Failed to parse payload for trigger-send filter", it) }
        .getOrNull() ?: return payloadJson
    if (triggerSendTables.none { it in payload }) return payloadJson

    val updated = payload.toMutableMap()
    triggerSendTables.forEach { table ->
        val rows = payload[table] as? JsonArray ?: return@forEach
        val filtered = rows.filter { row ->
            val obj = row as? JsonObject ?: return@filter true
            obj.values
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .any { cell -> cell.isNotBlank() && corpus.contains(cell.lowercase()) }
        }
        updated[table] = JsonArray(filtered)
    }
    return JsonObject(updated).toString()
}

// Concatenated text of the most recent user/assistant turns, used as the match
// corpus for trigger-send row filtering (#94).
private fun List<UIMessage>.recentConversationText(maxMessages: Int = 6): String =
    takeLast(maxMessages)
        .flatMap { message ->
            message.parts.filterIsInstance<UIMessagePart.Text>().map { it.text }
        }
        .joinToString("\n")
