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

    data class ManageConversationTags(
        override val modelId: Uuid,
        override val prompt: String,
        val messageTextSnapshot: String,
        val allowedTags: Map<Uuid, String>,
    ) : FrozenHookModelRequest

    data class AddConversationTag(
        override val modelId: Uuid,
        override val prompt: String,
        val messageTextSnapshot: String,
        val allowedTags: Map<Uuid, String>,
    ) : FrozenHookModelRequest

    data class TransitionConversationTags(
        override val modelId: Uuid,
        override val prompt: String,
        val messageTextSnapshot: String,
        val evidence: GitHubIssueEvidence,
        val addTagId: Uuid,
        val addTagName: String,
        val removeTagId: Uuid,
        val removeTagName: String,
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
        val errorExperience: Boolean = false,
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
        val raw = providerHandler.generateText(
            providerSetting = provider,
            messages = messages,
            params = params,
        ).choices.firstOrNull()?.message?.toText()
            ?: throw HookOutputException(HookErrorCode.MODEL_REQUEST_FAILED)
        return if (request is FrozenHookModelRequest.TransitionConversationTags) raw else raw.trim()
    }
}

internal fun buildHookEvaluationPrompt(request: FrozenHookModelRequest): String = when (request) {
    is FrozenHookModelRequest.ManageConversationTags -> buildManageTagsEvaluationPrompt(request)
    is FrozenHookModelRequest.AddConversationTag -> buildAddTagEvaluationPrompt(request)
    is FrozenHookModelRequest.TransitionConversationTags -> buildTagTransitionEvaluationPrompt(request)
    is FrozenHookModelRequest.SyncMemoryTable -> buildMemoryTableSyncEvaluationPrompt(request)
}

private fun buildManageTagsEvaluationPrompt(request: FrozenHookModelRequest.ManageConversationTags): String {
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
            "Return exactly one JSON object with exactly the keys decision, operations, and reason. " +
                "Use {\"decision\":\"apply\",\"operations\":[{\"op\":\"add|remove\",\"tagId\":\"<uuid>\"}]," +
                "\"reason\":\"...\"} to apply up to ${me.rerere.rikkahub.data.model.HookRuntimeRules.MAX_TAG_MANAGE_OPS} " +
                "operations, or {\"decision\":\"skip\",\"operations\":[],\"reason\":\"...\"} to skip. " +
                "Only use tagIds from the allowlist. Prefer remove before add when swapping tags near the limit. " +
                "Do not include Markdown fences or any other text."
        )
    }
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

private fun buildTagTransitionEvaluationPrompt(
    request: FrozenHookModelRequest.TransitionConversationTags,
): String {
    val evidenceValue = when (request.evidence.type) {
        GitHubIssueEvidenceType.ISSUE_URL -> request.evidence.normalizedUrl.orEmpty()
        GitHubIssueEvidenceType.ISSUE_NUMBER -> "#${request.evidence.issueNumber}"
    }
    val configuredPrompt = request.prompt.applyPlaceholders(
        "content" to request.messageTextSnapshot,
        "github_issue_evidence" to evidenceValue,
        "add_tag" to request.addTagName,
        "remove_tag" to request.removeTagName,
    )
    return buildString {
        appendLine(configuredPrompt.trim())
        appendLine()
        appendLine("Frozen final assistant response:")
        appendLine("<assistant_response>")
        appendLine(request.messageTextSnapshot)
        appendLine("</assistant_response>")
        appendLine()
        appendLine("Locally verified GitHub Issue creation evidence: $evidenceValue")
        appendLine("Configured transition: remove '${request.removeTagName}', then add '${request.addTagName}'.")
        appendLine("The tag IDs and transition scope are fixed locally and cannot be changed by your response.")
        append(
            "Return exactly one JSON object with exactly the keys decision and reason. " +
                "Use {\"decision\":\"apply\",\"reason\":\"...\"} to apply the configured transition, " +
                "or {\"decision\":\"skip\",\"reason\":\"...\"} to skip. " +
                "Do not include tag IDs, Markdown fences, whitespace outside the JSON object, or any other text."
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
        if (request.errorExperience) {
            appendLine(
                "Return exactly one JSON object with exactly the keys should_remember, deduplication_key, " +
                    "symptom, root_cause, correction, scope, tools, commands, and reason."
            )
            appendLine(
                "should_remember must be a boolean. The five text fields must be bounded plain strings; tools and " +
                    "commands must be arrays of bounded plain strings. Use empty strings/arrays when unavailable."
            )
            append(
                "Set should_remember=false for transient network failures, cancellation, recovered intermediate " +
                    "failures, insufficient evidence, or content that cannot be safely generalized. " +
                    "Do not include secrets, credentials, URLs, file-system paths, raw tool output, Markdown, or other text."
            )
            return@buildString
        }
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
