package me.rerere.rikkahub.data.ai.transformers

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
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableTemplate

internal const val DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS = 20
internal const val DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS = 800
internal const val DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS = 12_000

private const val MEMORY_TABLE_CHARS_PER_TOKEN = 4

// Manual injection placeholder (#99). When present in any message text, the
// rendered memory-table block replaces the placeholder instead of being appended
// to the system prompt, letting users control the injection position.
internal const val MEMORY_TABLE_MACRO = "{{memory_tables}}"

private val memoryTableSchemaJson = Json { ignoreUnknownKeys = true }

class MemoryTableInjectionTransformer(
    private val templates: List<MemoryTableTemplate>,
    private val documents: List<MemoryTableDocument>,
    private val maxDocuments: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
    private val maxTokens: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
    private val maxChars: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (documents.isEmpty()) return messages
        val content = buildMemoryTablePrompt(
            templates = templates,
            documents = documents,
            maxDocuments = maxDocuments,
            maxTokens = maxTokens,
            maxChars = maxChars,
            recentConversationText = messages.recentConversationText(),
        )
        if (content.isBlank()) return messages

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
    maxDocuments: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
    maxTokens: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
    maxChars: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
    recentConversationText: String = "",
): String {
    if (documents.isEmpty() || maxDocuments <= 0 || maxTokens <= 0 || maxChars <= 0) return ""
    val templatesById = templates.associateBy { it.id }
    val schemaTokenLimit = templates
        .mapNotNull { extractMaxInjectTokens(it.schemaJson) }
        .minOrNull()
    val effectiveTokens = minOf(maxTokens, schemaTokenLimit ?: maxTokens).coerceAtLeast(1)
    val charBudget = minOf(maxChars, effectiveTokens * MEMORY_TABLE_CHARS_PER_TOKEN)
    val limitedDocuments = documents.take(maxDocuments)
    val omittedDocuments = documents.size - limitedDocuments.size
    val rendered = buildString {
        appendLine("<memory_tables>")
        appendLine("Structured memory table documents are active for this conversation.")
        appendLine(
            "Limits: documents=${limitedDocuments.size}/${documents.size}, " +
                "maxTokens=$effectiveTokens, maxChars=$charBudget."
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
            appendLine((template?.schemaJson ?: "{}").trim())
            appendLine("payload:")
            appendLine(payload.trim())
        }
        if (omittedDocuments > 0) {
            appendLine()
            appendLine("... $omittedDocuments memory table document(s) omitted by document limit.")
        }
        appendLine("</memory_tables>")
    }
    return rendered.limitMemoryTableChars(charBudget)
}

private fun extractMaxInjectTokens(schemaJson: String): Int? {
    return runCatching {
        memoryTableSchemaJson
            .parseToJsonElement(schemaJson)
            .jsonObject["maxInjectTokens"]
            ?.jsonPrimitive
            ?.intOrNull
            ?.takeIf { it > 0 }
    }.getOrNull()
}

// Names of tables whose schema sets injectPolicy.enabled == false; their payload
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
    }.getOrNull().orEmpty()
}

// Drops the top-level payload entries for disabled tables. Returns the payload
// unchanged when nothing is disabled or the payload cannot be parsed as an object.
private fun filterInjectablePayload(payloadJson: String, disabledTables: Set<String>): String {
    if (disabledTables.isEmpty()) return payloadJson
    val payload = runCatching {
        memoryTableSchemaJson.parseToJsonElement(payloadJson).jsonObject
    }.getOrNull() ?: return payloadJson
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
    }.getOrNull().orEmpty()
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
    }.getOrNull() ?: return payloadJson
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
