package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.Instant
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks ASSISTANT think-tag parsing for main chat. Subagent transcript paths may not use
 * [ThinkTagTransformer].
 */
class ThinkTagTransformerTest {

    private val createdAt = Instant.parse("2026-06-01T12:00:00Z")
    private val finishedAt = Instant.parse("2026-06-01T12:01:00Z")

    @Test
    fun `plain text without think tags becomes single Text part`() {
        val parts = splitThinkTaggedText("Hello world", createdAt, finishedAt)
        assertEquals(1, parts.size)
        assertTrue(parts[0] is UIMessagePart.Text)
        assertEquals("Hello world", (parts[0] as UIMessagePart.Text).text)
    }

    @Test
    fun `single closed think block splits to Reasoning with finishedAt`() {
        val parts = splitThinkTaggedText("<think>inner</think>", createdAt, finishedAt)
        assertEquals(1, parts.size)
        val reasoning = parts[0] as UIMessagePart.Reasoning
        assertEquals("inner", reasoning.reasoning)
        assertEquals(createdAt, reasoning.createdAt)
        assertEquals(finishedAt, reasoning.finishedAt)
    }

    @Test
    fun `text before and after closed think block`() {
        val parts = splitThinkTaggedText("before<think>mid</think>after", createdAt, finishedAt)
        assertEquals(3, parts.size)
        assertEquals("before", (parts[0] as UIMessagePart.Text).text)
        assertEquals("mid", (parts[1] as UIMessagePart.Reasoning).reasoning)
        assertEquals("after", (parts[2] as UIMessagePart.Text).text)
    }

    @Test
    fun `multiple closed think blocks alternate Text and Reasoning`() {
        val input = "a<think>one</think>bthinktwoc"
        val parts = splitThinkTaggedText(input, createdAt, finishedAt)
        assertTrue(parts.size >= 3)
        assertEquals("a", (parts[0] as UIMessagePart.Text).text)
        assertEquals("one", (parts[1] as UIMessagePart.Reasoning).reasoning)
        assertEquals(finishedAt, (parts[1] as UIMessagePart.Reasoning).finishedAt)
    }

    @Test
    fun `unclosed think block has null finishedAt`() {
        val parts = splitThinkTaggedText("prefix<think>streaming", createdAt, finishedAt)
        val reasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().single()
        assertEquals("streaming", reasoning.reasoning)
        assertNull(reasoning.finishedAt)
    }

    @Test
    fun `onGenerationFinish path uses finishedAtOnClose for closed blocks`() {
        val parts = splitThinkTaggedText(
            "<think>done</think>",
            createdAt,
            finishedAtOnClose = finishedAt,
        )
        assertEquals(finishedAt, (parts.single() as UIMessagePart.Reasoning).finishedAt)
    }
}