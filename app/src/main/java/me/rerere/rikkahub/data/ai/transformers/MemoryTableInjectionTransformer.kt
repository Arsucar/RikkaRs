package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableTemplate

internal const val DEFAULT_MEMORY_TABLE_MAX_INJECT_ROWS = 20
internal const val DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS = 800
internal const val DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS = 12_000

private const val MEMORY_TABLE_CHARS_PER_TOKEN = 4
private val memoryTableSchemaJson = Json { ignoreUnknownKeys = true }

class MemoryTableInjectionTransformer(
    private val templates: List<MemoryTableTemplate>,
    private val documents: List<MemoryTableDocument>,
    private val maxRows: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_ROWS,
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
            maxRows = maxRows,
            maxTokens = maxTokens,
            maxChars = maxChars,
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
                parts = listOf(UIMessagePart.Text("$originalText\n\n$content"))
            )
        } else {
            result.add(0, UIMessage.system(content))
        }
        return result
    }
}

internal fun buildMemoryTablePrompt(
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    maxRows: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_ROWS,
    maxTokens: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
    maxChars: Int = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
): String {
    if (documents.isEmpty() || maxRows <= 0 || maxTokens <= 0 || maxChars <= 0) return ""
    val templatesById = templates.associateBy { it.id }
    val schemaTokenLimit = templates
        .mapNotNull { extractMaxInjectTokens(it.schemaJson) }
        .minOrNull()
    val effectiveTokens = minOf(maxTokens, schemaTokenLimit ?: maxTokens).coerceAtLeast(1)
    val charBudget = minOf(maxChars, effectiveTokens * MEMORY_TABLE_CHARS_PER_TOKEN)
    val limitedDocuments = documents.take(maxRows)
    val omittedDocuments = documents.size - limitedDocuments.size
    val rendered = buildString {
        appendLine("<memory_tables>")
        appendLine("Structured memory table documents are active for this conversation.")
        appendLine(
            "Limits: rows=${limitedDocuments.size}/${documents.size}, " +
                "maxTokens=$effectiveTokens, maxChars=$charBudget."
        )
        limitedDocuments.forEach { document ->
            val template = templatesById[document.templateId]
            appendLine()
            appendLine("## ${template?.name ?: document.templateId}")
            appendLine("scope=${document.scopeType.name.lowercase()} revision=${document.revision}")
            appendLine("schema:")
            appendLine((template?.schemaJson ?: "{}").trim())
            appendLine("payload:")
            appendLine(document.payloadJson.trim())
        }
        if (omittedDocuments > 0) {
            appendLine()
            appendLine("... $omittedDocuments memory table document(s) omitted by row limit.")
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

private fun String.limitMemoryTableChars(maxChars: Int): String {
    if (length <= maxChars) return this
    val marker = "\n... truncated by memory table limit\n</memory_tables>"
    if (maxChars <= marker.length) return take(maxChars)
    return take(maxChars - marker.length) + marker
}
