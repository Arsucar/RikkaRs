package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.resolveGenerationCountdownRemaining
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            toolLoopSteps = 2,
            transcript = listOf(SubagentTranscriptStep.Text("ok")),
        )
        val encoded = json.encodeToString(SubagentResult.serializer(), result)
        val decoded = json.decodeFromString(SubagentResult.serializer(), encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun spawnSubagentTool_hasExpectedNameAndParameters() {
        val tools = createSubagentTools(
            json = json,
            spawn = { _, _, _ ->
                SubagentResult("explore", "s", true)
            },
            askBtw = { "a" },
            getProfiles = { listOf(SubagentProfile(name = "explore", description = "d")) },
        )
        val spawn = tools.first { it.name == "spawn_subagent" }
        assertEquals("spawn_subagent", spawn.name)
        val schema = spawn.parameters() as InputSchema.Obj
        assertTrue(schema.required?.contains("profile_name") == true)
        assertTrue(schema.required?.contains("task") == true)
    }

    @Test
    fun spawnSubagentTool_omitsParallelGuidanceWhenDisabled() {
        val tools = createSubagentTools(
            json = json,
            spawn = { _, _, _ -> SubagentResult("explore", "s", true) },
            askBtw = { "a" },
            getProfiles = { listOf(SubagentProfile(name = "explore")) },
            parallelExecutionEnabled = false,
        )
        val spawn = tools.first { it.name == "spawn_subagent" }
        assertFalse(spawn.description.contains("Multiple `spawn_subagent` calls in the SAME response"))
    }

    @Test
    fun spawnSubagentTool_includesParallelGuidanceWhenEnabled() {
        val tools = createSubagentTools(
            json = json,
            spawn = { _, _, _ -> SubagentResult("explore", "s", true) },
            askBtw = { "a" },
            getProfiles = { listOf(SubagentProfile(name = "explore")) },
            parallelExecutionEnabled = true,
        )
        val spawn = tools.first { it.name == "spawn_subagent" }
        assertTrue(spawn.description.contains("Multiple `spawn_subagent` calls in the SAME response"))
    }

    @Test
    fun askBtwTool_hasExpectedName() {
        val tools = createSubagentTools(
            json = json,
            spawn = { _, _, _ -> SubagentResult("explore", "s", true) },
            askBtw = { "answer" },
            getProfiles = { listOf(SubagentProfile(name = "explore")) },
        )
        val btw = tools.first { it.name == "ask_btw" }
        assertEquals("ask_btw", btw.name)
        val schema = btw.parameters() as InputSchema.Obj
        assertTrue(schema.required?.contains("question") == true)
    }

    @Test
    fun manageSubagentProfileTool_hasExpectedName() {
        val tool = createManageSubagentTool(
            json = json,
            depth = 0,
            resolveProfile = { null },
            manage = { _, _, _ -> "ok" },
        )
        assertEquals("manage_subagent_profile", tool!!.name)
        val schema = tool.parameters() as InputSchema.Obj
        assertTrue(schema.required?.contains("action") == true)
    }

    @Test
    fun manageSubagentProfileTool_absentWhenDepthNotZero() {
        assertEquals(
            null,
            createManageSubagentTool(json, depth = 1, resolveProfile = { null }) { _, _, _ -> "" },
        )
    }

    @Test
    fun manageSubagentProfileTool_ignoresFieldsOutsideAllowedPatch() = runBlocking {
        var saved: SubagentProfile? = null
        val tool = createManageSubagentTool(
            json = json,
            depth = 0,
            resolveProfile = {
                SubagentProfile(
                    name = "agent",
                    temperature = 0.2f,
                    inheritTools = false,
                    enableMemory = false,
                )
            },
            manage = { _, _, profile ->
                saved = profile
                "ok"
            },
        )!!

        tool.execute(
            buildJsonObject {
                put("action", "update")
                put("name", "agent")
                put("description", "visible")
                put("system_prompt", "prompt")
                put("max_tool_calls", 12)
                put("disable_tool_budget_stop", true)
                put("temperature", 1.7)
                put("max_tokens", 4096)
                put("inherit_tools", true)
                put("enable_memory", true)
            }
        )

        val updated = saved!!
        assertEquals("visible", updated.description)
        assertEquals("prompt", updated.systemPrompt)
        assertEquals(12, updated.maxToolCalls)
        assertTrue(updated.disableToolBudgetStop)
        assertEquals(0.2f, updated.temperature!!, 0.0001f)
        assertNull(updated.maxTokens)
        assertFalse(updated.inheritTools)
        assertFalse(updated.enableMemory)
    }

    @Test
    fun summaryContinuationPrompt_isReasonable() {
        assertTrue(SUMMARY_CONTINUATION_PROMPT.length >= 50)
        assertFalse(SUMMARY_CONTINUATION_PROMPT.isBlank())
    }

    @Test
    fun subagentCountdownThreshold_nullMeansAutoAndZeroMeansOff() {
        assertEquals(4, resolveSubagentCountdownThreshold(maxToolCalls = 48, configuredThreshold = null))
        assertEquals(null, resolveSubagentCountdownThreshold(maxToolCalls = 48, configuredThreshold = 0))
        assertEquals(2, resolveSubagentCountdownThreshold(maxToolCalls = 2, configuredThreshold = null))
        assertEquals(8, resolveSubagentCountdownThreshold(maxToolCalls = 48, configuredThreshold = 8))
    }

    @Test
    fun countdownRemaining_usesExecutedToolCallsWhenTotalProvided() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "done",
                        toolName = "workspace_read_file",
                        input = "{}",
                        output = listOf(UIMessagePart.Text("ok")),
                    ),
                    UIMessagePart.Tool(
                        toolCallId = "pending",
                        toolName = "workspace_read_file",
                        input = "{}",
                        output = emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(
            3,
            resolveGenerationCountdownRemaining(
                maxSteps = 10,
                stepIndex = 8,
                messages = messages,
                stepsCountdownTotal = 4,
            ),
        )
        assertEquals(
            2,
            resolveGenerationCountdownRemaining(
                maxSteps = 10,
                stepIndex = 8,
                messages = messages,
                stepsCountdownTotal = null,
            ),
        )
    }

    @Test
    fun subagentSessionRegistry_requestCancelWithoutActiveSession_doesNotPoisonNextRun() {
        val conversationId = Uuid.random()

        SubagentSessionRegistry.requestCancel(conversationId)
        assertFalse(SubagentSessionRegistry.isCancelRequested(conversationId))

        SubagentSessionRegistry.register(conversationId)
        try {
            assertFalse(SubagentSessionRegistry.isCancelRequested(conversationId))
            SubagentSessionRegistry.requestCancel(conversationId, "stop")
            assertTrue(SubagentSessionRegistry.isCancelRequested(conversationId))
            assertEquals("stop", SubagentSessionRegistry.cancelReason(conversationId))
        } finally {
            SubagentSessionRegistry.unregister(conversationId)
        }

        assertFalse(SubagentSessionRegistry.isCancelRequested(conversationId))
    }

    @Test
    fun subagentSessionRegistry_defaultCancelReasonIsNotUserCancel() {
        val conversationId = Uuid.random()

        SubagentSessionRegistry.register(conversationId)
        try {
            SubagentSessionRegistry.requestCancel(conversationId)
            assertEquals(SUBAGENT_STOPPED_REASON, SubagentSessionRegistry.cancelReason(conversationId))
        } finally {
            SubagentSessionRegistry.unregister(conversationId)
        }
    }

    @Test
    fun subagentSessionRegistry_userCancelReasonRequiresExplicitReason() {
        val conversationId = Uuid.random()

        SubagentSessionRegistry.register(conversationId)
        try {
            SubagentSessionRegistry.requestCancel(conversationId, SUBAGENT_USER_CANCEL_REASON)
            assertEquals(SUBAGENT_USER_CANCEL_REASON, SubagentSessionRegistry.cancelReason(conversationId))
        } finally {
            SubagentSessionRegistry.unregister(conversationId)
        }
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
