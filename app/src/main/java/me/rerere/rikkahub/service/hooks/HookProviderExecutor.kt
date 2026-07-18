package me.rerere.rikkahub.service.hooks

import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ProviderRateLimiter
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import me.rerere.rikkahub.utils.applyPlaceholders
import kotlin.uuid.Uuid

sealed interface FrozenHookModelRequest {
    val modelId: Uuid
    val prompt: String

    data class AddConversationTag(
        override val modelId: Uuid,
        override val prompt: String,
        val messageTextSnapshot: String,
        val allowedTags: Map<Uuid, String>,
    ) : FrozenHookModelRequest

    data class SyncMemoryTable(
        override val modelId: Uuid,
        override val prompt: String,
        val messages: List<FrozenHookMessage>,
        val targetDocumentId: String,
        val baseRevision: Int,
        val schemaJson: String,
        val payloadJson: String,
        val maxOperations: Int,
    ) : FrozenHookModelRequest
}

data class FrozenHookMessage(
    val messageId: Uuid,
    val role: MessageRole,
    val text: String,
)

fun interface HookModelExecutor {
    suspend fun execute(request: FrozenHookModelRequest): String
}

class ProviderHookModelExecutor(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) : HookModelExecutor {
    override suspend fun execute(request: FrozenHookModelRequest): String {
        val settings = settingsStore.settingsFlow.value
        val model = settings.findModelById(request.modelId)
            ?: throw HookOutputException(HookErrorCode.MODEL_NOT_FOUND)
        val provider = model.findProvider(settings.providers)
            ?: throw HookOutputException(HookErrorCode.PROVIDER_NOT_FOUND)
        val providerHandler = providerManager.getProviderByType(provider)
        val messages = listOf(
            UIMessage.user(buildHookEvaluationPrompt(request))
        )
        val params = backgroundTextGenerationParams(model)
        ProviderRateLimiter.await(provider = provider, messages = messages, params = params)
        return providerHandler.generateText(
            providerSetting = provider,
            messages = messages,
            params = params,
        ).choices.firstOrNull()?.message?.toText()?.trim()
            ?: throw HookOutputException(HookErrorCode.MODEL_REQUEST_FAILED)
    }
}

internal fun buildHookEvaluationPrompt(request: FrozenHookModelRequest): String = when (request) {
    is FrozenHookModelRequest.AddConversationTag -> buildAddTagEvaluationPrompt(request)
    is FrozenHookModelRequest.SyncMemoryTable -> buildMemoryTableSyncEvaluationPrompt(request)
}

private fun buildAddTagEvaluationPrompt(request: FrozenHookModelRequest.AddConversationTag): String {
    val allowedTags = request.allowedTags.entries
        .sortedBy { it.key.toString() }
        .joinToString("\n") { (id, name) -> "$id\t$name" }
    val configuredPrompt = request.prompt.applyPlaceholders(
        "content" to request.messageTextSnapshot,
        "allowed_tags" to allowedTags,
    )
    return buildString {
        appendLine(configuredPrompt.trim())
        appendLine()
        appendLine("Frozen final assistant response:")
        appendLine("<assistant_response>")
        appendLine(request.messageTextSnapshot)
        appendLine("</assistant_response>")
        appendLine()
        appendLine("Allowed existing tags (tagId, then display name):")
        appendLine("<allowed_tags>")
        appendLine(allowedTags)
        appendLine("</allowed_tags>")
        appendLine()
        append(
            "Return exactly one JSON object with exactly the keys decision, tagId, and reason. " +
                "Use {\"decision\":\"apply\",\"tagId\":\"<uuid>\",\"reason\":\"...\"} to apply, " +
                "or {\"decision\":\"skip\",\"tagId\":null,\"reason\":\"...\"} to skip. " +
                "Do not include Markdown fences or any other text."
        )
    }
}

private fun buildMemoryTableSyncEvaluationPrompt(request: FrozenHookModelRequest.SyncMemoryTable): String {
    val boundedMessages = request.messages.joinToString("\n") { message ->
        "<message id=\"${message.messageId}\" role=\"${message.role.name.lowercase()}\">" +
            "${message.text}</message>"
    }
    val configuredPrompt = request.prompt.applyPlaceholders(
        "content" to boundedMessages,
        "memory_table_schema" to request.schemaJson,
        "memory_table_payload" to request.payloadJson,
    )
    return buildString {
        appendLine(configuredPrompt.trim())
        appendLine()
        appendLine("Frozen bounded conversation messages:")
        appendLine("<messages>")
        appendLine(boundedMessages)
        appendLine("</messages>")
        appendLine()
        appendLine("Target document id: ${request.targetDocumentId}")
        appendLine("Frozen base revision: ${request.baseRevision}")
        appendLine("Target schema:")
        appendLine("<schema>")
        appendLine(request.schemaJson)
        appendLine("</schema>")
        appendLine("Current target payload:")
        appendLine("<payload>")
        appendLine(request.payloadJson)
        appendLine("</payload>")
        appendLine()
        appendLine(
            "Return exactly one JSON object with exactly the keys decision, baseRevision, operations, and reason."
        )
        appendLine(
            "For apply, decision must be \"apply\", baseRevision must equal ${request.baseRevision}, and operations " +
                "must be an array of at most ${request.maxOperations} insert/update/delete operations."
        )
        appendLine(
            "Operation shapes are exactly: " +
                "{\"type\":\"insert\",\"table\":\"...\",\"row\":{...}}, " +
                "{\"type\":\"update\",\"table\":\"...\",\"row\":{...}}, or " +
                "{\"type\":\"delete\",\"table\":\"...\",\"row_key_value\":\"...\"}."
        )
        append(
            "For skip, use {\"decision\":\"skip\",\"baseRevision\":${request.baseRevision}," +
                "\"operations\":[],\"reason\":\"...\"}. Do not output target ids, scopes, tools, Markdown, or other text."
        )
    }
}
