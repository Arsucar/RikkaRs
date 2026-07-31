package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class DraftContextConfigTest {

    private fun replyDraftEntry(
        override: String? = "custom draft template",
        enabled: Boolean = true,
        order: Int = 0,
    ) = PresetEntry.Builtin(
        id = Uuid.random(),
        enabled = enabled,
        order = order,
        builtinKey = BuiltinPromptRegistry.KEY_REPLY_DRAFT,
        overrideContent = override,
    )

    @Test
    fun `toDraftContextText keeps tail when truncating`() {
        val longBody = "HEAD" + "x".repeat(40) + "TAIL"
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(longBody)),
        )
        val config = DraftContextConfig(maxCharsPerMessage = 20, keepLatestMessageIntact = false)
        val result = message.toDraftContextText(config, isLatest = false)
        assertTrue(result.endsWith("TAIL"))
        assertEquals(20, result.length)
        assertFalse(result.contains("HEAD"))
    }

    @Test
    fun `toDraftContextText skips truncate for latest when keepLatestMessageIntact`() {
        val body = "a".repeat(200)
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(body)),
        )
        val config = DraftContextConfig(maxCharsPerMessage = 30, keepLatestMessageIntact = true)
        val intact = message.toDraftContextText(config, isLatest = true)
        val truncated = message.toDraftContextText(config, isLatest = false)
        assertTrue(intact.length > 30)
        assertTrue(intact.contains(body))
        assertEquals(30, truncated.length)
    }

    @Test
    fun `toDraftContextText media tools reasoning placeholders follow flags`() {
        val message = UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Text("hi"),
                UIMessagePart.Image("https://example.com/a.png"),
                UIMessagePart.Document(url = "file://r.pdf", fileName = "report.pdf"),
                UIMessagePart.Tool(
                    toolCallId = "c1",
                    toolName = "web_search",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("found results")),
                ),
                UIMessagePart.Reasoning(reasoning = "think carefully about this"),
            ),
        )
        val off = DraftContextConfig(
            includeMedia = false,
            includeTools = false,
            includeReasoning = false,
        )
        val offText = message.toDraftContextText(off, isLatest = true)
        assertEquals("[USER]: hi", offText)
        assertFalse(offText.contains("[图片]"))
        assertFalse(offText.contains("[文件:"))
        assertFalse(offText.contains("[工具:"))
        assertFalse(offText.contains("[推理:"))

        val on = DraftContextConfig(
            includeMedia = true,
            includeTools = true,
            includeReasoning = true,
        )
        val onText = message.toDraftContextText(on, isLatest = true)
        assertTrue(onText.contains("[图片]"))
        assertTrue(onText.contains("[文件: report.pdf]"))
        assertTrue(onText.contains("[工具: web_search → found results]"))
        assertTrue(onText.contains("[推理: think carefully about this]"))
    }

    @Test
    fun `toDraftContextText empty parts yields role prefix only`() {
        val message = UIMessage(role = MessageRole.USER, parts = emptyList())
        val text = message.toDraftContextText(DEFAULT_DRAFT_CONTEXT, isLatest = true)
        assertEquals("[USER]: ", text)
    }

    @Test
    fun `toDraftContextContent respects messageCount and marks only last as latest`() {
        val messages = (1..5).map { i ->
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("m$i-" + "x".repeat(50))),
            )
        }
        val config = DraftContextConfig(
            messageCount = 2,
            maxCharsPerMessage = 25,
            keepLatestMessageIntact = true,
        )
        val content = messages.toDraftContextContent(config)
        // Only last two messages (m1..m3 out of window)
        assertFalse(content.contains("m1-"))
        assertFalse(content.contains("m2-"))
        assertFalse(content.contains("m3-"))
        val lines = content.split("\n\n")
        assertEquals(2, lines.size)
        // Older (m4) truncated to maxChars; latest (m5) kept intact
        assertEquals(25, lines[0].length)
        assertTrue(lines[0].endsWith("x".repeat(10)) || lines[0].length == 25)
        assertTrue(lines[1].contains("m5-"))
        assertTrue(lines[1].length > 25)
        assertTrue(lines[1].contains("x".repeat(50)))
    }

    @Test
    fun `toDraftContextContent messageCount zero yields empty`() {
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("a"))),
        )
        assertEquals("", messages.toDraftContextContent(DraftContextConfig(messageCount = 0)))
    }

    @Test
    fun `resolveDraftContextConfig null assistant or empty presets uses defaults`() {
        assertEquals(DEFAULT_DRAFT_CONTEXT, resolveDraftContextConfig(null, emptyList()))
        val assistant = Assistant(presetIds = emptySet())
        assertEquals(DEFAULT_DRAFT_CONTEXT, resolveDraftContextConfig(assistant, emptyList()))
    }

    @Test
    fun `resolveDraftContextConfig uses draftContext from preset that supplies reply_draft override`() {
        // Earlier preset has draftContext but no reply_draft — must not win (#196 multi-preset).
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            draftContext = DraftContextConfig(messageCount = 99),
        )
        val presetB = Preset(
            id = Uuid.random(),
            name = "b",
            entries = listOf(replyDraftEntry(override = "draft from B")),
            draftContext = DraftContextConfig(messageCount = 3),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id, presetB.id))
        val resolved = resolveDraftContextConfig(assistant, listOf(presetA, presetB))
        assertEquals(3, resolved.messageCount)
        assertEquals(presetB.id, resolveReplyDraftSourcePreset(assistant, listOf(presetA, presetB))?.id)
    }

    @Test
    fun `resolveDraftContextConfig prefers first reply_draft override over later draftContext-only preset`() {
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            entries = listOf(replyDraftEntry(override = "template A")),
            draftContext = DraftContextConfig(messageCount = 2, includeMedia = true),
        )
        val presetB = Preset(
            id = Uuid.random(),
            name = "b",
            draftContext = DraftContextConfig(messageCount = 12),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id, presetB.id))
        val resolved = resolveDraftContextConfig(assistant, listOf(presetA, presetB))
        assertEquals(2, resolved.messageCount)
        assertTrue(resolved.includeMedia)
    }

    @Test
    fun `resolveDraftContextConfig null draftContext on reply_draft preset uses defaults not other presets`() {
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            entries = listOf(replyDraftEntry(override = "template A")),
            draftContext = null,
        )
        val presetB = Preset(
            id = Uuid.random(),
            name = "b",
            draftContext = DraftContextConfig(messageCount = 7),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id, presetB.id))
        assertEquals(
            DEFAULT_DRAFT_CONTEXT,
            resolveDraftContextConfig(assistant, listOf(presetA, presetB)),
        )
    }

    @Test
    fun `resolveDraftContextConfig skips disabled reply_draft and uses next enabled`() {
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            entries = listOf(replyDraftEntry(override = "disabled", enabled = false)),
            draftContext = DraftContextConfig(messageCount = 1),
        )
        val presetB = Preset(
            id = Uuid.random(),
            name = "b",
            entries = listOf(replyDraftEntry(override = "enabled B")),
            draftContext = DraftContextConfig(messageCount = 5),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id, presetB.id))
        assertEquals(5, resolveDraftContextConfig(assistant, listOf(presetA, presetB)).messageCount)
    }

    @Test
    fun `resolveDraftContextConfig uses first enabled reply_draft without override when no override exists`() {
        // Default template path: still bind draftContext to the reply_draft-owning preset.
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            entries = listOf(replyDraftEntry(override = null)),
            draftContext = DraftContextConfig(messageCount = 4),
        )
        val presetB = Preset(
            id = Uuid.random(),
            name = "b",
            draftContext = DraftContextConfig(messageCount = 20),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id, presetB.id))
        assertEquals(4, resolveDraftContextConfig(assistant, listOf(presetA, presetB)).messageCount)
    }

    @Test
    fun `resolveDraftContextConfig ignores blank override for template source but still needs enabled entry`() {
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            entries = listOf(replyDraftEntry(override = "   ")),
            draftContext = DraftContextConfig(messageCount = 6),
        )
        val presetB = Preset(
            id = Uuid.random(),
            name = "b",
            entries = listOf(replyDraftEntry(override = "real override")),
            draftContext = DraftContextConfig(messageCount = 9),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id, presetB.id))
        // Blank override does not count as template source; non-blank on B wins.
        assertEquals(9, resolveDraftContextConfig(assistant, listOf(presetA, presetB)).messageCount)
    }

    @Test
    fun `resolveDraftContextConfig without any reply_draft uses defaults`() {
        val presetA = Preset(
            id = Uuid.random(),
            name = "a",
            draftContext = DraftContextConfig(messageCount = 3),
        )
        val assistant = Assistant(presetIds = setOf(presetA.id))
        assertEquals(DEFAULT_DRAFT_CONTEXT, resolveDraftContextConfig(assistant, listOf(presetA)))
        assertNull(resolveReplyDraftSourcePreset(assistant, listOf(presetA)))
    }

    @Test
    fun `preset without draftContext field deserializes as null`() {
        val json = """{"id":"${Uuid.random()}","name":"legacy","description":"","modeInjectionIds":[],"disabledEntryIds":[],"entries":[],"entriesVersion":0}"""
        val decoded = JsonInstant.decodeFromString<Preset>(json)
        assertNull(decoded.draftContext)
    }

    @Test
    fun `preset draftContext round trips`() {
        val preset = Preset(
            name = "with-draft",
            draftContext = DraftContextConfig(
                messageCount = 12,
                maxCharsPerMessage = 200,
                includeMedia = true,
                includeTools = false,
                includeReasoning = true,
                keepLatestMessageIntact = false,
            ),
        )
        val decoded = JsonInstant.decodeFromString<Preset>(JsonInstant.encodeToString(preset))
        assertEquals(preset.draftContext, decoded.draftContext)
    }

    @Test
    fun `tool output summary is capped at 300 chars inside placeholder`() {
        val longOut = "z".repeat(500)
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = "t",
                    toolName = "run",
                    input = "{}",
                    output = listOf(UIMessagePart.Text(longOut)),
                ),
            ),
        )
        val text = message.toDraftContextText(
            DraftContextConfig(includeTools = true),
            isLatest = true,
        )
        // Placeholder summary itself ≤ 300; full line is longer due to prefix.
        val arrow = text.substringAfter("→ ").removeSuffix("]")
        assertEquals(300, arrow.length)
    }
}
