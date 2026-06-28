package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock

private val THINKING_REGEX = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)
private val CLOSING_TAG_REGEX = Regex("</think>")

internal fun splitThinkTaggedText(text: String, createdAt: kotlinx.datetime.Instant, finishedAtOnClose: kotlinx.datetime.Instant?): List<UIMessagePart> {
    if (!THINKING_REGEX.containsMatchIn(text)) {
        return listOf(UIMessagePart.Text(text))
    }
    val parts = mutableListOf<UIMessagePart>()
    var lastEnd = 0
    for (match in THINKING_REGEX.findAll(text)) {
        val range = match.range
        if (range.first > lastEnd) {
            val segment = text.substring(lastEnd, range.first)
            if (segment.isNotEmpty()) {
                parts.add(UIMessagePart.Text(segment))
            }
        }
        val reasoning = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (reasoning.isNotEmpty()) {
            val hasClosing = CLOSING_TAG_REGEX.containsMatchIn(text.substring(range))
            parts.add(
                UIMessagePart.Reasoning(
                    reasoning = reasoning,
                    createdAt = createdAt,
                    finishedAt = if (hasClosing) finishedAtOnClose else null,
                ),
            )
        }
        lastEnd = range.last + 1
    }
    if (lastEnd < text.length) {
        val tail = text.substring(lastEnd)
        if (tail.isNotEmpty()) {
            parts.add(UIMessagePart.Text(tail))
        }
    }
    return parts.ifEmpty { listOf(UIMessagePart.Text(text)) }
}

object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text && THINKING_REGEX.containsMatchIn(part.text)) {
                            splitThinkTaggedText(
                                text = part.text,
                                createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                                finishedAtOnClose = Clock.System.now(),
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val now = Clock.System.now()
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text && THINKING_REGEX.containsMatchIn(part.text)) {
                            splitThinkTaggedText(
                                text = part.text,
                                createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                                finishedAtOnClose = now,
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }
}
