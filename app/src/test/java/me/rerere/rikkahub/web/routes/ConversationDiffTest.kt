package me.rerere.rikkahub.web.routes

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationDiffTest {
    @Test
    fun `single changed node returns only that node`() {
        val previous = conversation("first", "second")
        val changedNode = previous.messageNodes[1].copy(messages = listOf(UIMessage.user("updated")))
        val current = previous.copy(messageNodes = previous.messageNodes.toMutableList().apply { set(1, changedNode) })

        val diff = previous.singleNodeDiffOrNull(current)

        assertEquals(1, diff?.nodeIndex)
        assertEquals(changedNode, diff?.node)
    }

    @Test
    fun `single appended node returns append update`() {
        val previous = conversation("first")
        val appendedNode = MessageNode.of(UIMessage.user("second"))
        val current = previous.copy(messageNodes = previous.messageNodes + appendedNode)

        val diff = previous.singleNodeDiffOrNull(current)

        assertEquals(1, diff?.nodeIndex)
        assertEquals(appendedNode, diff?.node)
    }

    @Test
    fun `multiple changed nodes require a snapshot`() {
        val previous = conversation("first", "second")
        val current = previous.copy(
            messageNodes = previous.messageNodes.map { node ->
                node.copy(messages = listOf(UIMessage.user("changed")))
            },
        )

        assertNull(previous.singleNodeDiffOrNull(current))
    }

    @Test
    fun `conversation metadata change requires a snapshot`() {
        val previous = conversation("first")

        assertNull(previous.singleNodeDiffOrNull(previous.copy(title = "renamed")))
    }

    private fun conversation(vararg messages: String) = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        title = "title",
        messageNodes = messages.map { MessageNode.of(UIMessage.user(it)) },
    )
}
