package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

/**
 * Pure conversation overlays for optimistic writes (#295).
 * Kept in a dedicated file so concurrent ChatVM splits (#296) do not drop them.
 */

/** Pure title overlay for optimistic title edits. */
internal fun conversationWithTitle(conversation: Conversation, title: String): Conversation =
    conversation.copy(title = title)

/** Pure pin overlay for optimistic pin toggles. */
internal fun conversationWithPinned(conversation: Conversation, isPinned: Boolean): Conversation =
    conversation.copy(isPinned = isPinned)

/** Pure favorite overlay for a single message node. */
internal fun conversationWithNodeFavorite(
    conversation: Conversation,
    nodeId: Uuid,
    isFavorite: Boolean,
): Conversation = conversation.copy(
    messageNodes = conversation.messageNodes.map { node ->
        if (node.id == nodeId) node.copy(isFavorite = isFavorite) else node
    },
)

/** Pure memory-table isolation overlay. */
internal fun conversationWithMemoryTableIsolation(
    conversation: Conversation,
    enabled: Boolean,
): Conversation = conversation.copy(memoryTableIsolation = enabled)
