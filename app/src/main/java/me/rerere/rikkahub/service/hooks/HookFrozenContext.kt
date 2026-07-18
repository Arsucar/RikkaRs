package me.rerere.rikkahub.service.hooks

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookErrorCode
import kotlin.uuid.Uuid

internal fun freezeMemoryTableHookMessages(
    conversation: Conversation,
    cutoffMessageId: Uuid,
    config: HookActionConfig.SyncMemoryTable,
): List<FrozenHookMessage> {
    val current = conversation.currentMessages
    val cutoffIndex = current.indexOfFirst { it.id == cutoffMessageId }
    if (cutoffIndex < 0) throw HookOutputException(HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE)
    val eligible = current
        .take(cutoffIndex + 1)
        .asSequence()
        .filter { message ->
            when (message.role) {
                MessageRole.USER -> config.includeUserMessages
                MessageRole.ASSISTANT -> config.includeAssistantMessages
                MessageRole.SYSTEM, MessageRole.TOOL -> false
            }
        }
        .mapNotNull { message ->
            message.toText().trim().takeIf { it.isNotEmpty() }?.let { text ->
                FrozenHookMessage(message.id, message.role, text)
            }
        }
        .toList()
        .takeLast(config.recentMessageCount)

    var remaining = config.maxContextChars
    val reversed = mutableListOf<FrozenHookMessage>()
    for (message in eligible.asReversed()) {
        if (remaining <= 0) break
        val codePoints = Character.codePointCount(message.text, 0, message.text.length)
        val boundedText = if (codePoints <= remaining) {
            message.text
        } else {
            val start = Character.offsetByCodePoints(message.text, message.text.length, -remaining)
            message.text.substring(start)
        }
        if (boundedText.isNotEmpty()) {
            reversed += message.copy(text = boundedText)
            remaining -= Character.codePointCount(boundedText, 0, boundedText.length)
        }
    }
    return reversed.asReversed()
}
