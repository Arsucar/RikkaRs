package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.Json
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRuntimeTest {
    private val json: Json = JsonInstant

    @Test
    fun subagentResult_serializationRoundTrip() {
        val result = SubagentResult(
            profileName = "explore",
            summary = "found files",
            succeeded = true,
            depth = 1,
            usage = TokenUsage(promptTokens = 1, completionTokens = 2, totalTokens = 3),
            steps = 2,
            transcript = listOf(SubagentTranscriptStep.Text("ok")),
        )
        val encoded = json.encodeToString(SubagentResult.serializer(), result)
        val decoded = json.decodeFromString(SubagentResult.serializer(), encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun spawnSubagentTool_hasExpectedNameAndParameters() {
        val tools = createSubagentTools(
            profiles = listOf(SubagentProfile(name = "explore", description = "d")),
            json = json,
            spawn = { _, _, _ ->
                SubagentResult("explore", "s", true)
            },
            askBtw = { "a" },
        )
        val spawn = tools.first { it.name == "spawn_subagent" }
        assertEquals("spawn_subagent", spawn.name)
        val schema = spawn.parameters() as InputSchema.Obj
        assertTrue(schema.required?.contains("profile_name") == true)
        assertTrue(schema.required?.contains("task") == true)
    }

    @Test
    fun askBtwTool_hasExpectedName() {
        val tools = createSubagentTools(
            profiles = listOf(SubagentProfile(name = "explore")),
            json = json,
            spawn = { _, _, _ -> SubagentResult("explore", "s", true) },
            askBtw = { "answer" },
        )
        val btw = tools.first { it.name == "ask_btw" }
        assertEquals("ask_btw", btw.name)
        val schema = btw.parameters() as InputSchema.Obj
        assertTrue(schema.required?.contains("question") == true)
    }

    @Test
    fun manageSubagentProfileTool_hasExpectedName() {
        val tool = createManageSubagentTool(
            profiles = emptyList(),
            json = json,
            depth = 0,
            manage = { _, _, _ -> "ok" },
        )
        assertEquals("manage_subagent_profile", tool!!.name)
        val schema = tool.parameters() as InputSchema.Obj
        assertTrue(schema.required?.contains("action") == true)
    }

    @Test
    fun manageSubagentProfileTool_absentWhenDepthNotZero() {
        assertEquals(null, createManageSubagentTool(emptyList(), json, depth = 1) { _, _, _ -> "" })
    }

    @Test
    fun summaryContinuationPrompt_isReasonable() {
        assertTrue(SUMMARY_CONTINUATION_PROMPT.length >= 50)
        assertFalse(SUMMARY_CONTINUATION_PROMPT.isBlank())
    }

    @Test
    fun buildTranscript_extractsReasoningToolAndText() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning(reasoning = "think"),
                    UIMessagePart.Tool(
                        toolCallId = "1",
                        toolName = "workspace_read_file",
                        input = "{\"path\":\"/a\"}",
                        output = listOf(UIMessagePart.Text("body")),
                    ),
                    UIMessagePart.Text("done"),
                ),
            ),
        )
        val steps = SubagentHost.buildTranscript(messages)
        assertEquals(3, steps.size)
        assertTrue(steps[0] is SubagentTranscriptStep.Reasoning)
        assertTrue(steps[1] is SubagentTranscriptStep.ToolCall)
        assertTrue(steps[2] is SubagentTranscriptStep.Text)
    }

    @Test
    fun buildTranscript_mapsReasoningCreatedAtEpochMillis() {
        val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_456L)
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Reasoning(reasoning = "chain", createdAt = createdAt)),
            ),
        )
        val steps = SubagentHost.buildTranscript(messages)
        assertEquals(1, steps.size)
        val reasoning = steps.single() as SubagentTranscriptStep.Reasoning
        assertEquals(1_700_000_000_456L, reasoning.createdAt)
    }

    @Test
    fun buildTranscript_mapsToolExecutedFromOutputPresence() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "1",
                        toolName = "workspace_read_file",
                        input = "{}",
                        output = emptyList(),
                    ),
                    UIMessagePart.Tool(
                        toolCallId = "2",
                        toolName = "workspace_read_file",
                        input = "{}",
                        output = listOf(UIMessagePart.Text("done")),
                    ),
                ),
            ),
        )
        val steps = SubagentHost.buildTranscript(messages)
        assertEquals(2, steps.size)
        val pending = steps[0] as SubagentTranscriptStep.ToolCall
        val done = steps[1] as SubagentTranscriptStep.ToolCall
        assertFalse(pending.executed)
        assertTrue(done.executed)
    }

    @Test
    fun buildTranscript_honorsTruncateToolOutputLimit() {
        val longOutput = "x".repeat(50)
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "1",
                        toolName = "t",
                        input = "{}",
                        output = listOf(UIMessagePart.Text(longOutput)),
                    ),
                ),
            ),
        )
        val steps = SubagentHost.buildTranscript(messages, truncateChars = 200, truncateToolOutput = 10)
        val tool = steps.single() as SubagentTranscriptStep.ToolCall
        assertEquals(11, tool.output.length)
        assertEquals("x".repeat(10) + "\u2026", tool.output)
    }
}