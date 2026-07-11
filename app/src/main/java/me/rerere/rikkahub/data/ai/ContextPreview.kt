package me.rerere.rikkahub.data.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

enum class GenerationPreparationMode {
    Send,
    Preview,
}

sealed class GenerationPreparationException(message: String) : IllegalStateException(message) {
    class PendingToolExecution(message: String) : GenerationPreparationException(message)

    class InvalidMcpServerName(
        val invalidNames: List<String>,
        message: String,
    ) : GenerationPreparationException(message)
}

data class PreparedProviderInput(
    val messages: List<UIMessage>,
    val tools: List<Tool>,
    val sourceMessageCount: Int,
    val retainedSourceMessageCount: Int,
    val usedConversationSystemPrompt: Boolean,
)

internal fun List<Tool>.snapshotToolDefinitions(): List<Tool> = map { tool ->
    val parameters = tool.parameters()
    tool.copy(parameters = { parameters })
}

@Serializable
data class ContextPreviewTool(
    val name: String,
    val description: String,
    val parameters: JsonElement? = null,
)

@Serializable
data class ContextPreview(
    val messages: List<UIMessage>,
    val tools: List<ContextPreviewTool>,
    val sourceMessageCount: Int,
    val retainedSourceMessageCount: Int,
    val truncated: Boolean,
    val usedConversationSystemPrompt: Boolean,
    val characterCount: Int,
)

fun PreparedProviderInput.toContextPreview(json: Json): ContextPreview {
    val toolSnapshots = tools.map { tool ->
        ContextPreviewTool(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters()?.let {
                json.encodeToJsonElement(InputSchema.serializer(), it)
            },
        )
    }
    return ContextPreview(
        messages = messages,
        tools = toolSnapshots,
        sourceMessageCount = sourceMessageCount,
        retainedSourceMessageCount = retainedSourceMessageCount,
        truncated = retainedSourceMessageCount < sourceMessageCount,
        usedConversationSystemPrompt = usedConversationSystemPrompt,
        characterCount = messages.sumOf { message ->
            message.parts.sumOf(::visibleCharacterCount)
        } + toolSnapshots.sumOf { tool ->
            tool.name.length + tool.description.length +
                (tool.parameters?.toString()?.length ?: 0)
        },
    )
}

fun ContextPreview.toCopyJson(json: Json): String = json.encodeToString(
    JsonElement.serializer(),
    buildJsonObject {
        put("formatVersion", 1)
        putJsonObject("overview") {
            put("messageCount", messages.size)
            put("sourceMessageCount", sourceMessageCount)
            put("retainedSourceMessageCount", retainedSourceMessageCount)
            put("truncated", truncated)
            put("usedConversationSystemPrompt", usedConversationSystemPrompt)
            put("characterCount", characterCount)
            put("toolCount", tools.size)
        }
        put("messages", buildJsonArray {
            messages.forEach { message ->
                add(
                    buildJsonObject {
                        put("role", message.role.name.lowercase())
                        put("parts", buildJsonArray {
                            message.parts.forEach { add(it.toPreviewJson()) }
                        })
                    },
                )
            }
        })
        put("tools", json.encodeToJsonElement(ListSerializer(ContextPreviewTool.serializer()), tools))
    },
)

private fun ToolApprovalState.toPreviewJson(): JsonElement = buildJsonObject {
    when (this@toPreviewJson) {
        ToolApprovalState.Auto -> put("state", "auto")
        ToolApprovalState.Pending -> put("state", "pending")
        ToolApprovalState.Approved -> put("state", "approved")
        is ToolApprovalState.Denied -> {
            put("state", "denied")
            put("reason", reason)
        }
        is ToolApprovalState.Answered -> {
            put("state", "answered")
            put("answer", answer)
        }
    }
}

@Suppress("DEPRECATION")
private fun UIMessagePart.toPreviewJson(): JsonElement = buildJsonObject {
    when (this@toPreviewJson) {
        is UIMessagePart.Text -> {
            put("type", "text")
            put("text", text)
        }
        is UIMessagePart.Image -> {
            put("type", "image")
            put("url", url)
        }
        is UIMessagePart.Video -> {
            put("type", "video")
            put("url", url)
        }
        is UIMessagePart.Audio -> {
            put("type", "audio")
            put("url", url)
        }
        is UIMessagePart.Document -> {
            put("type", "document")
            put("url", url)
            put("fileName", fileName)
            put("mime", mime)
        }
        is UIMessagePart.SlashSkill -> {
            put("type", "slash_skill")
            put("name", name)
        }
        is UIMessagePart.Reasoning -> {
            put("type", "reasoning")
            put("reasoning", reasoning)
        }
        UIMessagePart.Search -> put("type", "search")
        is UIMessagePart.ToolCall -> {
            put("type", "tool_call")
            put("toolCallId", toolCallId)
            put("toolName", toolName)
            put("arguments", arguments)
            put("approval", approvalState.toPreviewJson())
        }
        is UIMessagePart.ToolResult -> {
            put("type", "tool_result")
            put("toolCallId", toolCallId)
            put("toolName", toolName)
            put("arguments", arguments)
            put("content", content)
        }
        is UIMessagePart.Tool -> {
            put("type", "tool")
            put("toolCallId", toolCallId)
            put("toolName", toolName)
            put("input", input)
            put("output", buildJsonArray { output.forEach { add(it.toPreviewJson()) } })
            put("approval", approvalState.toPreviewJson())
        }
    }
}

@Suppress("DEPRECATION")
private fun visibleCharacterCount(part: UIMessagePart): Int = when (part) {
    is UIMessagePart.Text -> part.text.length
    is UIMessagePart.Image -> part.url.length
    is UIMessagePart.Video -> part.url.length
    is UIMessagePart.Audio -> part.url.length
    is UIMessagePart.Document -> part.url.length + part.fileName.length + part.mime.length
    is UIMessagePart.SlashSkill -> part.name.length
    is UIMessagePart.Reasoning -> part.reasoning.length
    UIMessagePart.Search -> 0
    is UIMessagePart.ToolCall ->
        part.toolCallId.length + part.toolName.length + part.arguments.length + part.approvalState.visibleCharacterCount()
    is UIMessagePart.ToolResult ->
        part.toolCallId.length + part.toolName.length + part.arguments.toString().length + part.content.toString().length
    is UIMessagePart.Tool ->
        part.toolCallId.length + part.toolName.length + part.input.length +
            part.output.sumOf(::visibleCharacterCount) + part.approvalState.visibleCharacterCount()
}

private fun ToolApprovalState.visibleCharacterCount(): Int = when (this) {
    ToolApprovalState.Auto -> 4
    ToolApprovalState.Pending -> 7
    ToolApprovalState.Approved -> 8
    is ToolApprovalState.Denied -> 6 + reason.length
    is ToolApprovalState.Answered -> 8 + answer.length
}
