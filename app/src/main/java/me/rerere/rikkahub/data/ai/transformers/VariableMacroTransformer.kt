package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.variables.ConversationVariables
import me.rerere.rikkahub.data.model.isVariableSystemEnabled

/**
 * Input transformer: expands ST variable macros after PromptInjection and before Placeholder.
 * Mutates [TransformerContext.conversationVariables] when present and feature is enabled.
 * Disabled assistants: pass-through with zero side effects.
 */
object VariableMacroTransformer : InputMessageTransformer {
    override val previewPolicy: PreviewTransformPolicy = PreviewTransformPolicy.SideEffectFree

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.isVariableSystemEnabled()) return messages
        val variables = ctx.conversationVariables ?: return messages
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(text = ConversationVariables.expandMacros(part.text, variables))
                    } else {
                        part
                    }
                }
            )
        }
    }
}
