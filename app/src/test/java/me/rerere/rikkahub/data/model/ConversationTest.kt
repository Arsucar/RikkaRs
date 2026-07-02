package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationTest {

    private fun message(id: Uuid, text: String, role: MessageRole = MessageRole.USER) = UIMessage(
        id = id,
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private fun conversation(nodes: List<MessageNode>) = Conversation(
        assistantId = DEFAULT_ASSISTANT_ID,
        messageNodes = nodes,
    )

    @Test
    fun testNormalUpdate() {
        val userId = Uuid.random()
        val assistantId = Uuid.random()
        val user = message(userId, "hello")
        val assistant = message(assistantId, "hi", MessageRole.ASSISTANT)
        val conv = conversation(
            listOf(
                MessageNode(messages = listOf(user), selectIndex = 0),
                MessageNode(messages = listOf(assistant), selectIndex = 0),
            ),
        )

        val updatedUser = message(userId, "hello updated")
        val updatedAssistant = message(assistantId, "hi updated", MessageRole.ASSISTANT)
        val result = conv.updateCurrentMessages(listOf(updatedUser, updatedAssistant))

        assertEquals(2, result.messageNodes.size)
        assertEquals("hello updated", textOf(result.messageNodes[0]))
        assertEquals("hi updated", textOf(result.messageNodes[1]))
    }

    @Test
    fun testUpdateWithLeadingHiddenNodes() {
        val hidden1Id = Uuid.random()
        val hidden2Id = Uuid.random()
        val summaryId = Uuid.random()
        val visibleId = Uuid.random()
        val hidden1 = message(hidden1Id, "old1")
        val hidden2 = message(hidden2Id, "old2")
        val summary = message(summaryId, "summary", MessageRole.ASSISTANT)
        val visible = message(visibleId, "visible")
        val conv = conversation(
            listOf(
                MessageNode(messages = listOf(hidden1), selectIndex = 0, hidden = true),
                MessageNode(messages = listOf(hidden2), selectIndex = 0, hidden = true),
                MessageNode(messages = listOf(summary), selectIndex = 0),
                MessageNode(messages = listOf(visible), selectIndex = 0),
            ),
        )

        val updatedVisible = message(visibleId, "visible-streaming")
        val result = conv.updateCurrentMessages(listOf(summary, updatedVisible))

        assertEquals(4, result.messageNodes.size)
        assertTrue(result.messageNodes[0].hidden)
        assertTrue(result.messageNodes[1].hidden)
        assertEquals(hidden1Id, result.messageNodes[0].messages.single().id)
        assertEquals(hidden2Id, result.messageNodes[1].messages.single().id)
        assertEquals("summary", textOf(result.messageNodes[2]))
        assertEquals("visible-streaming", textOf(result.messageNodes[3]))
        assertEquals(visibleId, result.messageNodes[3].messages.single().id)
    }

    @Test
    fun testNewMessageAppendedToEnd() {
        val userId = Uuid.random()
        val newAssistantId = Uuid.random()
        val user = message(userId, "q")
        val conv = conversation(
            listOf(MessageNode(messages = listOf(user), selectIndex = 0)),
        )
        val newAssistant = message(newAssistantId, "answer", MessageRole.ASSISTANT)
        val result = conv.updateCurrentMessages(listOf(user, newAssistant))

        assertEquals(2, result.messageNodes.size)
        assertEquals(userId, result.messageNodes[0].messages.single().id)
        assertEquals(newAssistantId, result.messageNodes[1].messages.single().id)
        assertEquals("answer", textOf(result.messageNodes[1]))
    }

    @Test
    fun testRegenerateBranchesAtExistingNode() {
        val userId = Uuid.random()
        val oldAssistantId = Uuid.random()
        val newAssistantId = Uuid.random()
        val user = message(userId, "q")
        val oldAssistant = message(oldAssistantId, "old answer", MessageRole.ASSISTANT)
        val conv = conversation(
            listOf(
                MessageNode(messages = listOf(user), selectIndex = 0),
                MessageNode(messages = listOf(oldAssistant), selectIndex = 0),
            ),
        )
        // regenerate: LLM receives [user], generates new assistant with fresh id
        val newAssistant = message(newAssistantId, "new answer", MessageRole.ASSISTANT)
        val result = conv.updateCurrentMessages(listOf(user, newAssistant))

        // newAssistant should branch at node index 1, not append a new node
        assertEquals(2, result.messageNodes.size)
        // node 1 now has 2 messages: old + new branch
        assertEquals(2, result.messageNodes[1].messages.size)
        // selectIndex points to the new message
        assertEquals(1, result.messageNodes[1].selectIndex)
        assertEquals("new answer", textOf(result.messageNodes[1]))
    }

    private fun textOf(node: MessageNode): String {
        val msg = node.messages[node.selectIndex]
        return msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
    }
}