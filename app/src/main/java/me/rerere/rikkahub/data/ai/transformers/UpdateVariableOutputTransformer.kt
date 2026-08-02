package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.variables.UpdateVariableParser
import me.rerere.rikkahub.data.model.isVariableSystemEnabled

/**
 * Output transformer: on generation finish, parse MVU `<UpdateVariable>` blocks,
 * apply JSON Patch to conversation variables, and strip successful blocks from text.
 * Incomplete/failed blocks are left intact. Disabled assistants: no-op.
 */
object UpdateVariableOutputTransformer : OutputMessageTransformer {
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.isVariableSystemEnabled()) return messages
        val variables = ctx.conversationVariables ?: return messages

        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            var changed = false
            val newParts = message.parts.map { part ->
                if (part !is UIMessagePart.Text) return@map part
                val result = UpdateVariableParser.apply(part.text, variables.toMap())
                if (!result.applied) return@map part
                variables.clear()
                variables.putAll(result.variables)
                changed = true
                part.copy(text = result.text)
            }
            if (changed) message.copy(parts = newParts) else message
        }
    }

    /**
     * During streaming, hide complete UpdateVariable blocks from UI while leaving
     * incomplete open tags visible so the user does not see half-parsed JSON permanently.
     * Does not mutate variables (only onGenerationFinish applies patches).
     */
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.isVariableSystemEnabled()) return messages
        val closedBlock = Regex(
            """<UpdateVariable>[\s\S]*?</UpdateVariable>""",
            RegexOption.IGNORE_CASE,
        )
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Text && part.text.contains("UpdateVariable", ignoreCase = true)) {
                        val stripped = closedBlock.replace(part.text, "").trim()
                        if (stripped != part.text) part.copy(text = stripped) else part
                    } else {
                        part
                    }
                }
            )
        }
    }
}
