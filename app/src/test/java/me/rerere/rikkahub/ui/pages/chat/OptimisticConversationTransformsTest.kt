package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Pure unit tests for #295 conversation overlay helpers used by optimistic writes.
 */
class OptimisticConversationTransformsTest {
    private val conversationId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val assistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val nodeId = Uuid.parse("33333333-3333-3333-3333-333333333333")
    private val otherNodeId = Uuid.parse("44444444-4444-4444-4444-444444444444")

    private fun baseConversation(
        title: String = "Hello",
        isPinned: Boolean = false,
        memoryTableIsolation: Boolean = false,
        nodes: List<MessageNode> = emptyList(),
    ) = Conversation(
        id = conversationId,
        assistantId = assistantId,
        title = title,
        messageNodes = nodes,
        isPinned = isPinned,
        memoryTableIsolation = memoryTableIsolation,
    )

    private fun node(id: Uuid, isFavorite: Boolean = false) = MessageNode(
        id = id,
        messages = listOf(UIMessage.user("hi")),
        isFavorite = isFavorite,
    )

    @Test
    fun titleOverlayReplacesTitleOnly() {
        val base = baseConversation(title = "old", isPinned = true)
        val updated = conversationWithTitle(base, "new title")
        assertEquals("new title", updated.title)
        assertTrue(updated.isPinned)
        assertEquals(base.id, updated.id)
    }

    @Test
    fun pinOverlayFlipsPinnedFlag() {
        val base = baseConversation(isPinned = false)
        assertTrue(conversationWithPinned(base, true).isPinned)
        assertFalse(conversationWithPinned(base, false).isPinned)
    }

    @Test
    fun favoriteOverlayOnlyTouchesTargetNode() {
        val base = baseConversation(
            nodes = listOf(
                node(nodeId, isFavorite = false),
                node(otherNodeId, isFavorite = true),
            ),
        )
        val updated = conversationWithNodeFavorite(base, nodeId, isFavorite = true)
        assertTrue(updated.messageNodes.first { it.id == nodeId }.isFavorite)
        assertTrue(updated.messageNodes.first { it.id == otherNodeId }.isFavorite)
        assertEquals(2, updated.messageNodes.size)
    }

    @Test
    fun favoriteOverlayCanClearFavorite() {
        val base = baseConversation(nodes = listOf(node(nodeId, isFavorite = true)))
        val updated = conversationWithNodeFavorite(base, nodeId, isFavorite = false)
        assertFalse(updated.messageNodes.single().isFavorite)
    }

    @Test
    fun memoryTableIsolationOverlay() {
        val base = baseConversation(memoryTableIsolation = false)
        assertTrue(conversationWithMemoryTableIsolation(base, true).memoryTableIsolation)
        assertFalse(conversationWithMemoryTableIsolation(base, false).memoryTableIsolation)
    }
}
