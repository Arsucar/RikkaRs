package me.rerere.rikkahub.data.db

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class MessageStatsComputeTest {
    @Test
    fun `empty nodes produce zero stats and no daily rows`() {
        val result = computeMessageStats(
            conversationId = "conv-1",
            nodes = emptyList(),
            updatedAt = 1000L,
        )

        assertEquals("conv-1", result.stats.conversationId)
        assertEquals(0, result.stats.messageCount)
        assertEquals(0, result.stats.userMessageCount)
        assertEquals(0L, result.stats.tokenInput)
        assertEquals(0L, result.stats.tokenOutput)
        assertEquals(0L, result.stats.tokenCached)
        assertEquals(1000L, result.stats.updatedAt)
        assertTrue(result.daily.isEmpty())
    }

    @Test
    fun `counts all messages in every node not only selectIndex`() {
        val conversationId = "conv-all"
        val nodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage.user("u1").copy(
                        usage = TokenUsage(promptTokens = 1, completionTokens = 0, cachedTokens = 0),
                    ),
                    UIMessage.assistant("a1").copy(
                        usage = TokenUsage(promptTokens = 10, completionTokens = 20, cachedTokens = 3),
                    ),
                    UIMessage.user("u2").copy(
                        usage = TokenUsage(promptTokens = 2, completionTokens = 0, cachedTokens = 0),
                    ),
                ),
                selectIndex = 1,
            ),
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage.assistant("a2").copy(
                        usage = TokenUsage(promptTokens = 5, completionTokens = 7, cachedTokens = 1),
                    ),
                ),
                selectIndex = 0,
            ),
        )

        val result = computeMessageStats(conversationId, nodes, updatedAt = 42L)

        assertEquals(4, result.stats.messageCount)
        assertEquals(2, result.stats.userMessageCount)
        assertEquals(18L, result.stats.tokenInput)
        assertEquals(27L, result.stats.tokenOutput)
        assertEquals(4L, result.stats.tokenCached)
        assertEquals(42L, result.stats.updatedAt)
        assertEquals(1, result.daily.size)
        assertEquals(conversationId, result.daily.single().conversationId)
        assertEquals(2, result.daily.single().userMessageCount)
        assertEquals(10, result.daily.single().day.length)
    }

    @Test
    fun `daily rows group user messages by createdAt date`() {
        val dayA = "2026-01-10"
        val dayB = "2026-01-11"
        val nodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = emptyList(),
                        createdAt = kotlinx.datetime.LocalDateTime.parse("${dayA}T08:00:00"),
                    ),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = emptyList(),
                        createdAt = kotlinx.datetime.LocalDateTime.parse("${dayA}T18:00:00"),
                    ),
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = emptyList(),
                        createdAt = kotlinx.datetime.LocalDateTime.parse("${dayA}T19:00:00"),
                    ),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = emptyList(),
                        createdAt = kotlinx.datetime.LocalDateTime.parse("${dayB}T09:00:00"),
                    ),
                ),
            ),
        )

        val result = computeMessageStats("conv-day", nodes, updatedAt = 1L)

        assertEquals(4, result.stats.messageCount)
        assertEquals(3, result.stats.userMessageCount)
        assertEquals(
            mapOf(dayA to 2, dayB to 1),
            result.daily.associate { it.day to it.userMessageCount },
        )
    }

    @Test
    fun `null usage does not contribute tokens`() {
        val nodes = listOf(
            MessageNode.of(UIMessage.user("plain")),
            MessageNode.of(
                UIMessage.assistant("with-usage").copy(
                    usage = TokenUsage(promptTokens = 4, completionTokens = 6, cachedTokens = 2),
                ),
            ),
        )

        val result = computeMessageStats("conv-usage", nodes, updatedAt = 9L)

        assertEquals(2, result.stats.messageCount)
        assertEquals(1, result.stats.userMessageCount)
        assertEquals(4L, result.stats.tokenInput)
        assertEquals(6L, result.stats.tokenOutput)
        assertEquals(2L, result.stats.tokenCached)
    }
}
