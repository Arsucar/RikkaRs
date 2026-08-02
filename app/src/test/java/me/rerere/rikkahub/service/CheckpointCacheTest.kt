package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Conversation
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
