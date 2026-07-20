package me.rerere.rikkahub.data.ai.subagent

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ShellChangedFilesMetadata
import me.rerere.ai.ui.toMetadata
import me.rerere.ai.util.HttpException
import me.rerere.rikkahub.data.ai.resolveGenerationCountdownRemaining
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRuntimeTest {
    private class TimeoutFailure : Exception("deadline exceeded")
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
            contextId = "context-1",
            contextStatus = SubagentStatus.COMPLETED,
            contextCompleteness = SubagentContextCompleteness.FULL,
            startedAtEpochMillis = 100L,
            endedAtEpochMillis = 200L,
        )
        val encoded = json.encodeToString(SubagentResult.serializer(), result)
        val decoded = json.decodeFromString(SubagentResult.serializer(), encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun timeoutFailureIsDistinctFromUserCancellation() {
        assertEquals(
            SubagentFailureDisposition.TIMED_OUT,
            classifySubagentFailure(TimeoutFailure(), reusedContext = false),
        )
        assertEquals(
            SubagentFailureDisposition.INTERRUPTED,
            classifySubagentFailure(CancellationException("user stop"), reusedContext = false),
        )
    }

    @Test
    fun subagentResult_legacyPayloadWithoutContextFieldsStillDecodes() {
        val decoded = json.decodeFromString(
            SubagentResult.serializer(),
            """{"profile_name":"explore","summary":"done","succeeded":true}""",
        )

        assertNull(decoded.contextId)
        assertNull(decoded.contextStatus)
    }

    @Test
    fun subagentFailureClassificationSeparatesCancellationProviderAndLocalFailures() {
        assertEquals(
            SubagentFailureDisposition.INTERRUPTED,
            classifySubagentFailure(CancellationException("stop"), reusedContext = false),
        )
        assertEquals(
            SubagentFailureDisposition.INTERRUPTED,
            classifySubagentFailure(IOException("stream ended"), reusedContext = false),
        )
        assertEquals(
            SubagentFailureDisposition.INTERRUPTED,
            classifySubagentFailure(HttpException("provider unavailable"), reusedContext = false),
        )
        assertEquals(
            SubagentFailureDisposition.FAILED,
            classifySubagentFailure(IllegalArgumentException("invalid local profile"), reusedContext = false),
        )
    }

    @Test
    fun contextTooLongNormalizationOnlyAppliesToReuse() {
        val failure = HttpException("maximum context length exceeded")

        assertEquals(
            SubagentFailureDisposition.CONTEXT_TOO_LONG,
            classifySubagentFailure(failure, reusedContext = true),
        )
        assertEquals(
            SubagentFailureDisposition.INTERRUPTED,
            classifySubagentFailure(failure, reusedContext = false),
        )
    }

    @Test
    fun permissionFingerprintIsOrderStableAndChangesWithPermissionScope() {
        val firstServer = Uuid.random()
        val secondServer = Uuid.random()
        val tools = listOf(LocalToolOption.TimeInfo, LocalToolOption.JavascriptEngine)
        val parent = Assistant(
            localTools = tools,
            enabledSkills = linkedSetOf("alpha", "beta"),
            mcpServers = linkedSetOf(firstServer, secondServer),
        )
        val profile = SubagentProfile(
            name = "explore",
            allowedPathPrefixes = listOf("/workspace/b", "/workspace/a"),
            excludedTools = linkedSetOf("z", "a"),
            extraLocalTools = tools,
            toolApprovalOverrides = linkedMapOf("write" to true, "shell" to false),
        )
        val reorderedParent = parent.copy(
            localTools = tools.reversed(),
            enabledSkills = linkedSetOf("beta", "alpha"),
            mcpServers = linkedSetOf(secondServer, firstServer),
        )
        val reorderedProfile = profile.copy(
            allowedPathPrefixes = profile.allowedPathPrefixes.reversed(),
            excludedTools = linkedSetOf("a", "z"),
            extraLocalTools = tools.reversed(),
            toolApprovalOverrides = linkedMapOf("shell" to false, "write" to true),
        )

        val fingerprint = subagentPermissionFingerprint(profile, parent)
        assertEquals(fingerprint, subagentPermissionFingerprint(reorderedProfile, reorderedParent))
        assertNotEquals(
            fingerprint,
            subagentPermissionFingerprint(profile.copy(workspaceAccess = WorkspaceAccess.FULL), parent),
        )
    }

    @Test
    fun spawnSubagentTool_hasExpectedNameAndParameters() {
        val tools = createSubagentTools(
            json = json,
            spawn = { _, _, _, _ ->
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
        assertTrue("reuse_context_id" in schema.properties)
        assertFalse(schema.required?.contains("reuse_context_id") == true)
        val reuseDescription = schema.properties.getValue("reuse_context_id")
            .jsonObject
            .getValue("description")
            .jsonPrimitive
            .content
        assertTrue(reuseDescription.contains("most recent completed context with the same full scope"))
        assertTrue(spawn.description.contains("may automatically reuse the most recent completed context"))
    }

    @Test
    fun spawnSubagentTool_passesReuseContextAndReturnsContextMetadata() = runBlocking {
        var receivedContextId: String? = null
        val tool = createSubagentTools(
            json = json,
            spawn = { _, _, _, reuseContextId ->
                receivedContextId = reuseContextId
                SubagentResult(
                    profileName = "explore",
                    summary = "continued",
                    succeeded = true,
                    contextId = reuseContextId,
                    contextStatus = SubagentStatus.COMPLETED,
                )
            },
            askBtw = { "answer" },
            getProfiles = { listOf(SubagentProfile(name = "explore")) },
        ).first { it.name == "spawn_subagent" }

        val output = tool.execute(
            buildJsonObject {
                put("profile_name", "explore")
                put("task", "continue")
                put("reuse_context_id", "context-1")
            },
        ).single() as UIMessagePart.Text

        assertEquals("context-1", receivedContextId)
        assertEquals("context-1", output.metadata?.get("subagent_context_id")?.jsonPrimitive?.contentOrNull)
        assertTrue(output.text.contains("\"context_id\":\"context-1\""))
        assertTrue(output.text.contains("\"context_status\":\"COMPLETED\""))
    }

    @Test
    fun spawnSubagentTool_writesFullTransferredContextMetadata() = runBlocking {
        val mcpId = Uuid.random()
        val tool = createSubagentTools(
            json = json,
            spawn = { _, _, _, _ ->
                SubagentResult(
                    profileName = "explore",
                    summary = "done",
                    succeeded = true,
                    depth = 1,
                    maxToolCalls = 16,
                    contextId = "ctx-full",
                    transferredContext = SubagentTransferredContext(
                        systemPrompt = "child system",
                        workspaceAccess = WorkspaceAccess.READ_ONLY.name,
                        workspaceApproval = WorkspaceApproval.INHERIT.name,
                        canSpawn = false,
                        inheritTools = true,
                        excludedTools = listOf("spawn_subagent"),
                        allowedPathPrefixes = listOf("/workspace"),
                        childToolNames = listOf("workspace_read_file", "workspace_shell"),
                        skills = listOf("skill-a"),
                        mcpServerIds = listOf(mcpId.toString()),
                        modelId = "gpt-test",
                        cwd = "/workspace",
                        enableMemory = false,
                        injectedMemoryTableDocumentIds = listOf("doc-a", "doc-b"),
                        memoryTableLabels = listOf("Default Table", "Notes"),
                        memoryTableInjected = true,
                        memoryTableSkipReason = null,
                        includesParentHistory = false,
                        reusedContext = false,
                    ),
                )
            },
            askBtw = { "answer" },
            getProfiles = {
                listOf(
                    SubagentProfile(
                        name = "explore",
                        systemPrompt = "profile system",
                        workspaceAccess = WorkspaceAccess.READ_ONLY,
                    ),
                )
            },
        ).first { it.name == "spawn_subagent" }

        val output = tool.execute(
            buildJsonObject {
                put("profile_name", "explore")
                put("task", "inspect")
                put("description", "label")
            },
        ).single() as UIMessagePart.Text
        val meta = output.metadata!!

        assertEquals("inspect", meta["subagent_task"]?.jsonPrimitive?.contentOrNull)
        assertEquals("label", meta["subagent_description"]?.jsonPrimitive?.contentOrNull)
        assertEquals("child system", meta["subagent_system_prompt"]?.jsonPrimitive?.contentOrNull)
        assertTrue(meta["subagent_system_prompt_note"]?.jsonPrimitive?.contentOrNull?.contains("profile.systemPrompt") == true)
        assertEquals(WorkspaceAccess.READ_ONLY.name, meta["subagent_workspace_access"]?.jsonPrimitive?.contentOrNull)
        assertEquals(WorkspaceApproval.INHERIT.name, meta["subagent_workspace_approval"]?.jsonPrimitive?.contentOrNull)
        assertEquals(false, meta["subagent_can_spawn"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, meta["subagent_inherit_tools"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("1", meta["subagent_depth"]?.jsonPrimitive?.contentOrNull)
        assertEquals("gpt-test", meta["subagent_model_id"]?.jsonPrimitive?.contentOrNull)
        assertEquals("/workspace", meta["subagent_cwd"]?.jsonPrimitive?.contentOrNull)
        assertEquals(false, meta["subagent_includes_parent_history"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(false, meta["subagent_enable_memory"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, meta["subagent_memory_table_injected"]?.jsonPrimitive?.booleanOrNull)
        assertNull(meta["subagent_memory_table_skip_reason"])
        assertTrue(meta["subagent_memory_table_ids"].toString().contains("doc-a"))
        assertTrue(meta["subagent_memory_table_ids"].toString().contains("doc-b"))
        assertTrue(meta["subagent_memory_table_labels"].toString().contains("Default Table"))
        assertTrue(meta["subagent_memory_table_labels"].toString().contains("Notes"))
        assertTrue(meta["subagent_child_tools"].toString().contains("workspace_read_file"))
        assertTrue(meta["subagent_skills"].toString().contains("skill-a"))
        assertTrue(meta["subagent_mcp_servers"].toString().contains(mcpId.toString()))
        assertTrue(meta["subagent_path_prefixes"].toString().contains("/workspace"))
        assertTrue(meta["subagent_excluded_tools"].toString().contains("spawn_subagent"))
    }

    @Test
    fun spawnSubagentTool_writesMemoryTableSkipReasonMetadata() = runBlocking {
        val tool = createSubagentTools(
            json = json,
            spawn = { _, _, _, _ ->
                SubagentResult(
                    profileName = "explore",
                    summary = "done",
                    succeeded = true,
                    transferredContext = SubagentTransferredContext(
                        injectedMemoryTableDocumentIds = listOf("doc-x"),
                        memoryTableInjected = false,
                        memoryTableSkipReason = "reused_context",
                        reusedContext = true,
                    ),
                )
            },
            askBtw = { "answer" },
            getProfiles = { listOf(SubagentProfile(name = "explore")) },
        ).first { it.name == "spawn_subagent" }

        val output = tool.execute(
            buildJsonObject {
                put("profile_name", "explore")
                put("task", "continue")
            },
        ).single() as UIMessagePart.Text
        val meta = output.metadata!!

        assertEquals(false, meta["subagent_memory_table_injected"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("reused_context", meta["subagent_memory_table_skip_reason"]?.jsonPrimitive?.contentOrNull)
        assertTrue(meta["subagent_memory_table_ids"].toString().contains("doc-x"))
        assertEquals(true, meta["subagent_reused_context"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun subagentTransferredContext_serializationRoundTrip() {
        val ctx = SubagentTransferredContext(
            systemPrompt = "sys",
            childToolNames = listOf("a", "b"),
            skills = listOf("s"),
            injectedMemoryTableDocumentIds = listOf("doc-1"),
            memoryTableLabels = listOf("Default Table"),
            memoryTableInjected = true,
            memoryTableSkipReason = null,
            includesParentHistory = false,
        )
        val result = SubagentResult(
            profileName = "explore",
            summary = "ok",
            succeeded = true,
            transferredContext = ctx,
        )
        val decoded = json.decodeFromString(
            SubagentResult.serializer(),
            json.encodeToString(SubagentResult.serializer(), result),
        )
        assertEquals(ctx, decoded.transferredContext)
        assertEquals(true, decoded.transferredContext?.memoryTableInjected)
        assertNull(decoded.transferredContext?.memoryTableSkipReason)
        assertEquals(listOf("doc-1"), decoded.transferredContext?.injectedMemoryTableDocumentIds)
        assertEquals(listOf("Default Table"), decoded.transferredContext?.memoryTableLabels)

        val skipCtx = SubagentTransferredContext(
            memoryTableInjected = false,
            memoryTableSkipReason = "not_resolved",
        )
        val skipDecoded = json.decodeFromString(
            SubagentTransferredContext.serializer(),
            json.encodeToString(SubagentTransferredContext.serializer(), skipCtx),
        )
        assertEquals(false, skipDecoded.memoryTableInjected)
        assertEquals("not_resolved", skipDecoded.memoryTableSkipReason)
        assertTrue(skipDecoded.memoryTableLabels.isEmpty())

        // Backward compatible: old payloads without new fields deserialize to defaults.
        val legacy = json.decodeFromString(
            SubagentTransferredContext.serializer(),
            """{"system_prompt":"old"}""",
        )
        assertEquals(false, legacy.memoryTableInjected)
        assertNull(legacy.memoryTableSkipReason)
        assertTrue(legacy.memoryTableLabels.isEmpty())
    }

    @Test
    fun spawnSubagentTool_omittingReuseContextPassesNullForAutomaticSelection() = runBlocking {
        var receivedContextId: String? = "not-called"
        val tool = createSubagentTools(
            json = json,
            spawn = { _, _, _, reuseContextId ->
                receivedContextId = reuseContextId
                SubagentResult(profileName = "explore", summary = "done", succeeded = true)
            },
            askBtw = { "answer" },
            getProfiles = { listOf(SubagentProfile(name = "explore")) },
        ).first { it.name == "spawn_subagent" }

        tool.execute(
            buildJsonObject {
                put("profile_name", "explore")
                put("task", "continue safely")
            },
        )

        assertNull(receivedContextId)
    }

    @Test
    fun spawnSubagentTool_omitsParallelGuidanceWhenDisabled() {
        val tools = createSubagentTools(
            json = json,
            spawn = { _, _, _, _ -> SubagentResult("explore", "s", true) },
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
            spawn = { _, _, _, _ -> SubagentResult("explore", "s", true) },
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
            spawn = { _, _, _, _ -> SubagentResult("explore", "s", true) },
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

    @Test
    fun buildTranscript_truncatesWorkspaceShellOutputAsValidJson() {
        val shellOutput = buildJsonObject {
            put("exitCode", 0)
            put("stdout", "o".repeat(50))
            put("stderr", "e".repeat(50))
            put("timedOut", false)
        }.toString()
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "1",
                        toolName = "workspace_shell",
                        input = """{"command":"test"}""",
                        output = listOf(UIMessagePart.Text(shellOutput)),
                    ),
                ),
            ),
        )

        val steps = SubagentHost.buildTranscript(messages, truncateChars = 200, truncateToolOutput = 10)
        val tool = steps.single() as SubagentTranscriptStep.ToolCall
        val output = Json.parseToJsonElement(tool.output).jsonObject

        assertEquals("o".repeat(10) + "\u2026", output.getValue("stdout").jsonPrimitive.contentOrNull)
        assertEquals("e".repeat(10) + "\u2026", output.getValue("stderr").jsonPrimitive.contentOrNull)
        assertEquals("0", output.getValue("exitCode").jsonPrimitive.contentOrNull)
        assertEquals("false", output.getValue("timedOut").jsonPrimitive.contentOrNull)
    }

    @Test
    fun buildTranscript_preservesShellChangedFilesOutsideTruncatedOutput() {
        val shellOutput = buildJsonObject {
            put("exitCode", 0)
            put("stdout", "o".repeat(50))
            put("stderr", "")
            put("timedOut", false)
        }.toString()
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "1",
                        toolName = "workspace_shell",
                        input = """{"command":"generate"}""",
                        output = listOf(
                            UIMessagePart.Text(
                                shellOutput,
                                metadata = ShellChangedFilesMetadata(
                                    listOf("/workspace/a.txt", "/workspace/b.txt"),
                                ).toMetadata(),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val tool = SubagentHost.buildTranscript(messages, truncateToolOutput = 5)
            .single() as SubagentTranscriptStep.ToolCall

        assertEquals(listOf("/workspace/a.txt", "/workspace/b.txt"), tool.changedFiles)
        assertEquals("o".repeat(5) + "\u2026", Json.parseToJsonElement(tool.output).jsonObject
            .getValue("stdout").jsonPrimitive.contentOrNull)
    }
}
