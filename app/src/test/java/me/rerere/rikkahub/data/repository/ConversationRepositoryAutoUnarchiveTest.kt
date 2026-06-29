package me.rerere.rikkahub.data.repository

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Documents the auto-unarchive contract implemented in [ConversationRepository.updateConversation]
 * (see repository source around `existing.isArchived && existing.messageNodes != conversation.messageNodes`).
 *
 * Full [ConversationRepository.updateConversation] integration is not covered here because the repository
 * depends on Android Room, FTS, and [FilesManager] without a JVM mock stack in this project.
 */
class ConversationRepositoryAutoUnarchiveTest {

    private val assistantId = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")

    @Test
    fun archivedConversation_messageNodesChanged_autoUnarchives() {
        val id = Uuid.random()
        val nodeA = messageNode("Hello")
        val nodeB = messageNode("Follow-up")
        val existing = archivedConversation(id, listOf(nodeA))
        val incoming = existing.copy(messageNodes = listOf(nodeA, nodeB))

        val persisted = applyAutoUnarchiveIfNeeded(existing, incoming)

        assertFalse(persisted.isArchived)
        assertNull(persisted.archivedAt)
    }

    @Test
    fun archivedConversation_messageNodesUnchanged_staysArchived() {
        val id = Uuid.random()
        val nodes = listOf(messageNode("Same"))
        val existing = archivedConversation(id, nodes)
        val incoming = existing.copy(title = "Renamed title only")

        val persisted = applyAutoUnarchiveIfNeeded(existing, incoming)

        assertTrue(persisted.isArchived)
        assertEquals(existing.archivedAt, persisted.archivedAt)
    }

    @Test
    fun notArchivedConversation_noOp() {
        val id = Uuid.random()
        val node = messageNode("Hi")
        val existing = conversation(id, listOf(node), isArchived = false)
        val incoming = existing.copy(messageNodes = listOf(node, messageNode("More")))

        val persisted = applyAutoUnarchiveIfNeeded(existing, incoming)

        assertFalse(persisted.isArchived)
        assertNull(persisted.archivedAt)
        assertEquals(incoming.messageNodes, persisted.messageNodes)
    }

    @Test
    fun archiveStatusFlip_doesNotChangeUpdateAt() {
        val updateAt = Instant.parse("2024-06-01T12:00:00Z")
        val before = conversation(
            id = Uuid.random(),
            nodes = listOf(messageNode("x")),
            isArchived = false,
            updateAt = updateAt,
        )

        val archived = before.copy(isArchived = true, archivedAt = Instant.parse("2024-06-02T00:00:00Z"))
        val unarchived = archived.copy(isArchived = false, archivedAt = null)

        assertEquals(updateAt, unarchived.updateAt)
    }

    @Test
    fun archivedAtMapper_entityZeroMeansNullInDomain() {
        val epoch = 1_700_000_000_000L
        val domain = mapArchivedAtFromEntity(epoch)
        val back = mapArchivedAtToEntity(domain)

        assertNotNull(domain)
        assertEquals(epoch, domain!!.toEpochMilli())
        assertEquals(epoch, back)
        assertEquals(0L, mapArchivedAtToEntity(null))
    }

    private fun archivedConversation(id: Uuid, nodes: List<MessageNode>): Conversation {
        return conversation(
            id = id,
            nodes = nodes,
            isArchived = true,
            archivedAt = Instant.parse("2024-01-15T08:00:00Z"),
        )
    }

    private fun conversation(
        id: Uuid,
        nodes: List<MessageNode>,
        isArchived: Boolean,
        archivedAt: Instant? = null,
        updateAt: Instant = Instant.parse("2024-06-01T12:00:00Z"),
    ): Conversation {
        return Conversation(
            id = id,
            assistantId = assistantId,
            title = "Test",
            messageNodes = nodes,
            isArchived = isArchived,
            archivedAt = archivedAt,
            createAt = Instant.parse("2024-06-01T10:00:00Z"),
            updateAt = updateAt,
        )
    }

    private fun messageNode(text: String): MessageNode {
        return MessageNode(
            id = Uuid.random(),
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(text)),
                ),
            ),
            selectIndex = 0,
        )
    }

    private fun applyAutoUnarchiveIfNeeded(
        existing: Conversation?,
        conversation: Conversation,
    ): Conversation {
        return if (
            existing != null &&
            existing.isArchived &&
            existing.messageNodes != conversation.messageNodes
        ) {
            conversation.copy(isArchived = false, archivedAt = null)
        } else {
            conversation
        }
    }

    private fun mapArchivedAtFromEntity(archivedAt: Long): Instant? {
        return archivedAt.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
    }

    private fun mapArchivedAtToEntity(archivedAt: Instant?): Long {
        return archivedAt?.toEpochMilli() ?: 0L
    }
}