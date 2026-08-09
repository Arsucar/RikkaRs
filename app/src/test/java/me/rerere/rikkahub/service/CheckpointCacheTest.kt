package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointCacheTest {
    @Test
    fun `asFinalSnapshot clears checkpoint metadata`() {
        val conversation = Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = emptyList(),
            checkpointStep = 16,
            isCheckpointSnapshot = true,
        )
        val final = conversation.asFinalSnapshot()
        assertFalse(final.isCheckpointSnapshot)
        assertNull(final.checkpointStep)
        assertEquals(conversation.id, final.id)
    }

    @Test
    fun `checkpoint restore closes historical reasoning without changing its start`() {
        val createdAt = Instant.parse("2024-01-01T00:00:00Z")
        val conversation = Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage(
                            role = MessageRole.ASSISTANT,
                            parts = listOf(
                                UIMessagePart.Reasoning(
                                    reasoning = "restored",
                                    createdAt = createdAt,
                                    finishedAt = null,
                                )
                            ),
                        )
                    )
                )
            ),
            isCheckpointSnapshot = true,
            updateAt = java.time.Instant.parse("2024-01-01T00:05:00Z"),
        )

        val restored = conversation.closeCheckpointReasoning()
        val reasoning = restored.messageNodes.single().messages.single().parts.single()
            as UIMessagePart.Reasoning
        assertEquals(createdAt, reasoning.createdAt)
        assertEquals(Instant.parse("2024-01-01T00:05:00Z"), reasoning.finishedAt)
    }

    @Test
    fun `asFinalSnapshot is no-op for already final conversations`() {
        val conversation = Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = emptyList(),
        )
        val final = conversation.asFinalSnapshot()
        assertEquals(conversation, final)
    }

    @Test
    fun `checkpoint recovery is identified by isCheckpointSnapshot`() {
        val checkpoint = Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = emptyList(),
            checkpointStep = 8,
            isCheckpointSnapshot = true,
        )
        val final = checkpoint.asFinalSnapshot()
        assertTrue(checkpoint.isCheckpointSnapshot)
        assertFalse(final.isCheckpointSnapshot)
    }
}
