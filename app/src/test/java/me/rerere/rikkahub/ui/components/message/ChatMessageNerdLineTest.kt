package me.rerere.rikkahub.ui.components.message

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessageNerdLineTest {

    @Test
    fun `buildTokenStatsDisplay formats completed message`() {
        val stats = buildTokenStatsDisplay(
            message(
                finishedAt = LocalDateTime(2026, 1, 1, 12, 0, 2),
                usage = TokenUsage(
                    promptTokens = 1500,
                    completionTokens = 4,
                    cachedTokens = 100,
                ),
            )
        )

        requireNotNull(stats)
        assertEquals("1.5K tokens", stats.promptLabel)
        assertEquals("(100 cached)", stats.cachedLabel)
        assertEquals("4 tokens", stats.completionLabel)
        assertEquals("2.0 tok/s", stats.tpsLabel)
        assertEquals("2.0s", stats.durationLabel)
    }

    @Test
    fun `buildTokenStatsDisplay returns null without usage`() {
        assertNull(buildTokenStatsDisplay(message(usage = null)))
    }

    @Test
    fun `buildTokenStatsDisplay omits timing for zero duration`() {
        val createdAt = LocalDateTime(2026, 1, 1, 12, 0)
        val stats = buildTokenStatsDisplay(
            message(
                createdAt = createdAt,
                finishedAt = createdAt,
                usage = TokenUsage(completionTokens = 10),
            )
        )

        requireNotNull(stats)
        assertNull(stats.tpsLabel)
        assertNull(stats.durationLabel)
    }

    private fun message(
        createdAt: LocalDateTime = LocalDateTime(2026, 1, 1, 12, 0),
        finishedAt: LocalDateTime? = null,
        usage: TokenUsage?,
    ) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = emptyList(),
        createdAt = createdAt,
        finishedAt = finishedAt,
        usage = usage,
    )
}
