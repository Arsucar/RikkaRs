package me.rerere.rikkahub.service.hooks

import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ProviderRateLimiter
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import me.rerere.rikkahub.utils.applyPlaceholders
import kotlin.uuid.Uuid

data class FrozenHookModelRequest(
    val modelId: Uuid,
    val prompt: String,
    val messageTextSnapshot: String,
    val allowedTags: Map<Uuid, String>,
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

internal fun buildHookEvaluationPrompt(request: FrozenHookModelRequest): String {
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
