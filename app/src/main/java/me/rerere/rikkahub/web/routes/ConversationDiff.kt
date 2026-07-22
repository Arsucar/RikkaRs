package me.rerere.rikkahub.web.routes

import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode

internal data class NodeDiff(
    val nodeIndex: Int,
    val node: MessageNode
)

internal fun Conversation.singleNodeDiffOrNull(current: Conversation): NodeDiff? {
    if (id != current.id || assistantId != current.assistantId || createAt != current.createAt) {
        return null
    }

    if (
        title != current.title ||
        chatSuggestions != current.chatSuggestions ||
        isPinned != current.isPinned ||
        customSystemPrompt != current.customSystemPrompt ||
        modeInjectionIds != current.modeInjectionIds ||
        lorebookIds != current.lorebookIds ||
        workspaceCwd != current.workspaceCwd ||
        folderId != current.folderId
    ) {
        return null
    }

    if (messageNodes.size > current.messageNodes.size) {
        return null
    }

    var changedIndex = -1
    val maxSize = maxOf(messageNodes.size, current.messageNodes.size)
    for (index in 0 until maxSize) {
        val previousNode = messageNodes.getOrNull(index)
        val currentNode = current.messageNodes.getOrNull(index)
        if (previousNode == currentNode) continue

        if (changedIndex != -1) {
            return null
        }
        changedIndex = index
    }

    if (changedIndex == -1) {
        return null
    }

    val changedNode = current.messageNodes.getOrNull(changedIndex) ?: return null
    return NodeDiff(nodeIndex = changedIndex, node = changedNode)
}
