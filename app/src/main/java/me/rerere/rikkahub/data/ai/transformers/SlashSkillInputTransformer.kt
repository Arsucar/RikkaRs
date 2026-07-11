package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.SlashSkillTags

object SlashSkillInputTransformer : InputMessageTransformer {
    override val previewPolicy: PreviewTransformPolicy = PreviewTransformPolicy.SideEffectFree
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.USER) return@map message
            val slashSkills = message.parts.filterIsInstance<UIMessagePart.SlashSkill>()
            val filtered = message.parts.filterNot { it is UIMessagePart.SlashSkill }
            if (slashSkills.isEmpty()) return@map message

            val skillNames = slashSkills.map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
            val tag = buildString {
                append(SlashSkillTags.OPEN)
                append(skillNames.joinToString(", "))
                append(SlashSkillTags.CLOSE)
                append("\n\n")
            }

            val firstTextIndex = filtered.indexOfFirst { it is UIMessagePart.Text }
            val newParts = if (firstTextIndex >= 0) {
                val textPart = filtered[firstTextIndex] as UIMessagePart.Text
                filtered.toMutableList().apply {
                    set(firstTextIndex, textPart.copy(text = tag + textPart.text))
                }
            } else {
                listOf(UIMessagePart.Text(tag)) + filtered
            }

            message.copy(parts = newParts)
        }
    }
}
