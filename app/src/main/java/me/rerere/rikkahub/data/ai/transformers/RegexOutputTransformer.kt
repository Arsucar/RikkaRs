package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import org.koin.core.component.KoinComponent

object RegexOutputTransformer : OutputMessageTransformer, KoinComponent {
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val assistant = ctx.assistant
        if (assistant.regexes.isEmpty()) return messages
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            part.copy(
                                text = part.text.replaceRegexes(
                                    assistant,
                                    AssistantAffectScope.ASSISTANT,
                                    visual = false,
                                )
                            )
                        }

                        is UIMessagePart.Reasoning -> {
                            part.copy(
                                reasoning = part.reasoning.replaceRegexes(
                                    assistant,
                                    AssistantAffectScope.ASSISTANT,
                                    visual = false,
                                )
                            )
                        }

                        else -> part
                    }
                }
            )
        }
    }
}
