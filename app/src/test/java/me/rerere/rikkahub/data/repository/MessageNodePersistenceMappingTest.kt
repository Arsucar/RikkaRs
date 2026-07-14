package me.rerere.rikkahub.data.repository

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class MessageNodePersistenceMappingTest {
    @Test
    fun `compress hidden count round trips through normalized entity mapping`() {
        val node = MessageNode(
            id = Uuid.random(),
            messages = listOf(UIMessage.user("summary")),
            selectIndex = 0,
            hidden = false,
            compressHiddenCount = 17,
            isFavorite = true,
        )

        val entity = messageNodeToEntity(
            node = node,
            conversationId = Uuid.random().toString(),
            nodeIndex = 3,
        )
        val restored = messageNodeEntityToMessageNode(entity, isFavorite = true)

        assertEquals(17, entity.compressHiddenCount)
        assertEquals(node.id, restored.id)
        assertEquals(node.messages, restored.messages)
        assertEquals(node.compressHiddenCount, restored.compressHiddenCount)
        assertTrue(restored.isFavorite)
    }
}
