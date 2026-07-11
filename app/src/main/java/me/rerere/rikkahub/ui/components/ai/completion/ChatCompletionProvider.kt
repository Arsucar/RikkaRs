package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

data class ChatCompletionContext(
    val text: String,
    val selection: TextRange,
) {
    val cursor: Int = selection.max
    val hasSelection: Boolean = selection.min != selection.max
}

data class ChatCompletionList(
    val providerId: String,
    val replacementRange: TextRange,
    val items: List<ChatCompletionItem>,
)

data class ChatCompletionItem(
    val label: String,
    val insertText: String,
    val detail: String? = null,
    val icon: ImageVector? = null,
    val sortScore: Int = 0,
    val skillName: String? = null,
    val presetId: Uuid? = null,
    val action: ChatCompletionAction? = null,
)

sealed interface ChatCompletionAction {
    data class SetAssistantDefaultModel(
        val modelId: Uuid?,
        val modelName: String?,
    ) : ChatCompletionAction
}

internal sealed interface ChatCompletionApplyResult {
    data object None : ChatCompletionApplyResult

    data class DefaultModelSaved(val modelName: String) : ChatCompletionApplyResult

    data object DefaultModelUnavailable : ChatCompletionApplyResult
}

internal data class ChatCompletionApplication(
    val replacementRange: TextRange,
    val insertText: String,
    val assistantUpdate: Assistant?,
    val result: ChatCompletionApplyResult,
) {
    val cursor: Int = replacementRange.min + insertText.length

    fun applyTo(text: String): String = text.replaceRange(
        replacementRange.min,
        replacementRange.max,
        insertText,
    )
}

internal fun prepareChatCompletionApplication(
    textLength: Int,
    replacementRange: TextRange,
    item: ChatCompletionItem,
    assistant: Assistant,
): ChatCompletionApplication {
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    var assistantUpdate = item.presetId?.let { presetId ->
        assistant.copy(presetIds = setOf(presetId))
    }
    val result = when (val action = item.action) {
        is ChatCompletionAction.SetAssistantDefaultModel -> {
            val modelId = action.modelId
            if (modelId == null) {
                ChatCompletionApplyResult.DefaultModelUnavailable
            } else {
                assistantUpdate = (assistantUpdate ?: assistant).copy(chatModelId = modelId)
                ChatCompletionApplyResult.DefaultModelSaved(action.modelName.orEmpty())
            }
        }

        null -> ChatCompletionApplyResult.None
    }
    return ChatCompletionApplication(
        replacementRange = TextRange(start, end),
        insertText = item.insertText,
        assistantUpdate = assistantUpdate,
        result = result,
    )
}

interface ChatCompletionProvider {
    val id: String

    suspend fun complete(context: ChatCompletionContext): ChatCompletionList?
}
