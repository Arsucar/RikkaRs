package me.rerere.rikkahub.data.repository

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationRepositorySyncOpsTest {

    private fun node(id: Uuid, selectIndex: Int = 0) = MessageNode(
        id = id,
        messages = listOf(UIMessage(role = MessageRole.USER, parts = emptyList())),
        selectIndex = selectIndex,
    )

    @Test
    fun `orphan existing ids are marked for delete`() {
        val keep = Uuid.random()
        val orphan = Uuid.random()
        val (deleteIds, upsert) = computeNodeSyncOps(
            existingIds = listOf(keep.toString(), orphan.toString()),
            newNodes = listOf(node(keep)),
        )
        assertEquals(listOf(orphan.toString()), deleteIds)
        assertEquals(1, upsert.size)
        assertEquals(keep, upsert[0].id)
    }

    @Test
    fun `new node id appears in upsert list`() {
        val existing = Uuid.random()
        val added = Uuid.random()
        val (_, upsert) = computeNodeSyncOps(
            existingIds = listOf(existing.toString()),
            newNodes = listOf(node(existing), node(added)),
        )
        assertEquals(2, upsert.size)
        assertEquals(listOf(existing, added), upsert.map { it.id })
    }

    @Test
    fun `same id with updated selectIndex is fully represented in upsert`() {
        val id = Uuid.random()
        val updated = node(id, selectIndex = 2)
        val (deleteIds, upsert) = computeNodeSyncOps(
            existingIds = listOf(id.toString()),
            newNodes = listOf(updated),
        )
        assertTrue(deleteIds.isEmpty())
        assertEquals(1, upsert.size)
        assertEquals(2, upsert[0].selectIndex)
    }
}