package me.rerere.rikkahub.service.hooks

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

data class HookFinalMessageSnapshot(
    val nodeId: Uuid,
    val messageId: Uuid,
    val messageModelId: Uuid?,
    val text: String,
)

internal fun evaluateHookFinalSuccess(conversation: Conversation): HookFinalMessageSnapshot? {
    val node = conversation.messageNodes.lastOrNull { !it.hidden } ?: return null
    val message = node.messages.getOrNull(node.selectIndex) ?: return null
    return evaluateHookSourceMessage(node.id, message)
}

internal fun evaluateHookSourceMessage(
    conversation: Conversation,
    messageId: Uuid,
): HookFinalMessageSnapshot? {
    val node = conversation.messageNodes.firstOrNull { node ->
        !node.hidden && node.messages.getOrNull(node.selectIndex)?.id == messageId
    } ?: return null
    val message = node.messages.getOrNull(node.selectIndex) ?: return null
    return evaluateHookSourceMessage(node.id, message)
}

private fun evaluateHookSourceMessage(
    nodeId: Uuid,
    message: me.rerere.ai.ui.UIMessage,
): HookFinalMessageSnapshot? {
    if (message.role != MessageRole.ASSISTANT || message.finishedAt == null) return null
    val tools = message.parts.filterIsInstance<UIMessagePart.Tool>()
    if (tools.any { it.isPending || !it.isExecuted }) return null
    val text = message.toText().trim()
    if (text.isEmpty()) return null
    return HookFinalMessageSnapshot(
        nodeId = nodeId,
        messageId = message.id,
        messageModelId = message.modelId,
        text = text,
    )
}
