package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ContextPreparationTest {
    @Test
    fun sanitizeInvalidMessagesIsPureAndDropsOnlyUnresolvedSelectedToolMessage() {
        val previous = UIMessage.assistant("previous")
        val unresolved = toolMessage(ToolApprovalState.Auto)
        val node = MessageNode(messages = listOf(previous, unresolved), selectIndex = 1)
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(node),
        )

        val sanitized = sanitizeInvalidMessages(conversation)

        assertEquals(listOf(previous, unresolved), conversation.messageNodes.single().messages)
        assertEquals(1, conversation.messageNodes.single().selectIndex)
        assertEquals(listOf(previous), sanitized.messageNodes.single().messages)
        assertEquals(0, sanitized.messageNodes.single().selectIndex)
    }

    @Test
    fun sanitizePreservesResumableToolAndPendingDetectionFailsClosed() {
        val resumable = toolMessage(ToolApprovalState.Approved)
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode.of(resumable)),
        )

        val sanitized = sanitizeInvalidMessages(conversation)

        assertSame(conversation, sanitized)
        assertTrue(sanitized.currentMessages.hasResumablePendingTool())
        assertFalse(listOf(UIMessage.user("next")).hasResumablePendingTool())
    }

    private fun toolMessage(approvalState: ToolApprovalState): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Tool(
                toolCallId = "call-1",
                toolName = "lookup",
                input = "{}",
                approvalState = approvalState,
            ),
        ),
    )
}
