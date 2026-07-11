package me.rerere.rikkahub.ui.components.ai.completion

import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Pin

class DefaultModelCompletionProvider(
    private val model: Model?,
    private val detail: String,
    private val unknownModelName: String,
) : ChatCompletionProvider {
    override val id: String = "assistant_default_model"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val command = findTildeCompletionCommand(context.text, context.cursor) ?: return null
        if (command.query != DEFAULT_MODEL_COMMAND_QUERY) return null

        val modelName = model?.displayName
            ?.ifBlank { model.modelId }
            ?.ifBlank { unknownModelName }
        return ChatCompletionList(
            providerId = id,
            replacementRange = command.range,
            items = listOf(
                ChatCompletionItem(
                    label = "~$DEFAULT_MODEL_COMMAND_QUERY",
                    detail = detail,
                    insertText = "",
                    icon = HugeIcons.Pin,
                    sortScore = COMMAND_SORT_SCORE,
                    action = ChatCompletionAction.SetAssistantDefaultModel(
                        modelId = model?.id,
                        modelName = modelName,
                    ),
                )
            ),
        )
    }

    private companion object {
        const val COMMAND_SORT_SCORE = 2_000
    }
}
