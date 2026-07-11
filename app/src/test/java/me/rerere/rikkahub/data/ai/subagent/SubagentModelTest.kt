package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.Json
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.migrateSubagentBuiltinsIfNeeded
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentModelTest {
    private val json: Json = JsonInstant

    @Test
    fun subagentProfile_roundTrip() {
        val profile = SubagentProfile(
            name = "custom_agent",
            description = "desc",
            systemPrompt = "sys",
            reasoningLevel = ReasoningLevel.LOW,
            workspaceAccess = WorkspaceAccess.FULL,
            workspaceApproval = WorkspaceApproval.OVERRIDE,
            canSpawn = true,
            toolApprovalOverrides = mapOf("workspace_shell" to false),
        )
        val decoded = json.decodeFromString(SubagentProfile.serializer(), json.encodeToString(SubagentProfile.serializer(), profile))
        assertEquals(profile, decoded)
    }

    @Test
    fun subagentProfile_nameAllowsUnicodeLetters() {
        val profile = SubagentProfile(name = "研究员_1")
        assertEquals("研究员_1", profile.name)
        assertTrue("研究员_1".matches(SubagentProfile.IdentifierRegex))
        assertTrue("agent_1".matches(SubagentProfile.IdentifierRegex))
        assertFalse("1_agent".matches(SubagentProfile.IdentifierRegex))
        assertFalse("研究 员".matches(SubagentProfile.IdentifierRegex))
        assertFalse("研究员/tool".matches(SubagentProfile.IdentifierRegex))
    }

    @Test
    fun subagentProfile_legacyDisplayNameAndMaxSteps_areIgnored() {
        val legacy = """
            {
              "name": "legacy_agent",
              "displayName": "Legacy",
              "description": "desc",
              "maxSteps": 99,
              "maxToolCalls": 12
            }
        """.trimIndent()
        val decoded = json.decodeFromString(SubagentProfile.serializer(), legacy)
        assertEquals("legacy_agent", decoded.name)
        assertEquals("desc", decoded.description)
        assertEquals(12, decoded.maxToolCalls)
    }

    @Test
    fun subagentResult_roundTrip() {
        val result = SubagentResult(
            profileName = "explore",
            summary = "done",
            succeeded = true,
            depth = 1,
            usage = TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15),
            steps = 3,
            toolLoopSteps = 2,
            transcript = listOf(
                SubagentTranscriptStep.Reasoning("think"),
                SubagentTranscriptStep.ToolCall("workspace_read_file", "{}", "ok"),
                SubagentTranscriptStep.Text("summary"),
            ),
        )
        val decoded = json.decodeFromString(SubagentResult.serializer(), json.encodeToString(SubagentResult.serializer(), result))
        assertEquals(result, decoded)
    }

    @Test
    fun subagentTranscriptStep_polymorphicRoundTrip() {
        val steps: List<SubagentTranscriptStep> = listOf(
            SubagentTranscriptStep.Reasoning("r"),
            SubagentTranscriptStep.ToolCall("t", "in", "out"),
            SubagentTranscriptStep.Text("c"),
        )
        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SubagentTranscriptStep.serializer()), steps)
        val decoded = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SubagentTranscriptStep.serializer()), encoded)
        assertEquals(steps, decoded)
    }

    @Test
    fun subagentTranscriptStep_roundTrip_preservesCreatedAtAndExecuted() {
        val steps: List<SubagentTranscriptStep> = listOf(
            SubagentTranscriptStep.Reasoning(text = "r", createdAt = 1_700_000_000_123L),
            SubagentTranscriptStep.ToolCall(
                toolName = "workspace_shell",
                input = "{}",
                output = "",
                executed = false,
                changedFiles = listOf("/workspace/generated.txt"),
            ),
        )
        val listSerializer = kotlinx.serialization.builtins.ListSerializer(SubagentTranscriptStep.serializer())
        val decoded = json.decodeFromString(listSerializer, json.encodeToString(listSerializer, steps))
        assertEquals(steps, decoded)
        val reasoning = decoded[0] as SubagentTranscriptStep.Reasoning
        val tool = decoded[1] as SubagentTranscriptStep.ToolCall
        assertEquals(1_700_000_000_123L, reasoning.createdAt)
        assertFalse(tool.executed)
        assertEquals(listOf("/workspace/generated.txt"), tool.changedFiles)
    }

    @Test
    fun subagentTranscriptStep_oldToolCallDefaultsChangedFilesToEmpty() {
        val encoded = """[{"type":"tool_call","toolName":"workspace_shell","input":"{}","output":"ok"}]"""
        val serializer = kotlinx.serialization.builtins.ListSerializer(SubagentTranscriptStep.serializer())

        val tool = json.decodeFromString(serializer, encoded).single() as SubagentTranscriptStep.ToolCall

        assertEquals(emptyList<String>(), tool.changedFiles)
    }

    @Test
    fun subagentResult_roundTrip_preservesTranscriptMetadataFields() {
        val result = SubagentResult(
            profileName = "explore",
            summary = "ok",
            succeeded = true,
            transcript = listOf(
                SubagentTranscriptStep.Reasoning("think", createdAt = 42L),
                SubagentTranscriptStep.ToolCall("t", "in", "out", executed = false),
            ),
        )
        val decoded = json.decodeFromString(SubagentResult.serializer(), json.encodeToString(SubagentResult.serializer(), result))
        assertEquals(result, decoded)
    }

    /**
     * Mirrors [SubagentHost.runToCompletion] progress gate: emit when assistant part-count
     * signature changes or [minIntervalMs] elapsed (default 120ms).
     */
    private fun shouldEmitSubagentProgress(
        messages: List<UIMessage>,
        lastSignature: Int,
        lastEmitTime: Long,
        now: Long,
        minIntervalMs: Long = 120L,
    ): Boolean {
        val signature = messages.sumOf { msg ->
            if (msg.role == MessageRole.ASSISTANT) msg.parts.size else 0
        }
        return signature != lastSignature || now - lastEmitTime >= minIntervalMs
    }

    @Test
    fun subagentProgressThrottle_sameSignatureWithin120ms_doesNotEmit() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("a"), UIMessagePart.Text("b")),
            ),
        )
        assertFalse(shouldEmitSubagentProgress(messages, lastSignature = 2, lastEmitTime = 1_000L, now = 1_050L))
    }

    @Test
    fun subagentProgressThrottle_sameSignatureAfter120ms_emits() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("a"), UIMessagePart.Text("b")),
            ),
        )
        assertTrue(shouldEmitSubagentProgress(messages, lastSignature = 2, lastEmitTime = 1_000L, now = 1_121L))
    }

    @Test
    fun subagentProgressThrottle_signatureChange_emitsEvenWithin120ms() {
        val twoParts = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("a"), UIMessagePart.Text("b")),
            ),
        )
        assertTrue(shouldEmitSubagentProgress(twoParts, lastSignature = 1, lastEmitTime = 1_000L, now = 1_010L))
        val threeParts = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Text("a"),
                    UIMessagePart.Text("b"),
                    UIMessagePart.Reasoning("r"),
                ),
            ),
        )
        assertTrue(shouldEmitSubagentProgress(threeParts, lastSignature = 2, lastEmitTime = 1_000L, now = 1_010L))
    }

    @Test
    fun assistant_withSubagentFields_roundTrip() {
        val assistant = Assistant(
            name = "a1",
            enableSubagents = true,
            subagentMaxDepth = 3,
            subagentProfiles = listOf(
                SubagentProfile(name = "my_bot"),
            ),
            disabledBuiltinSubagents = setOf("reviewer"),
        )
        val decoded = json.decodeFromString(Assistant.serializer(), json.encodeToString(Assistant.serializer(), assistant))
        assertEquals(assistant.enableSubagents, decoded.enableSubagents)
        assertEquals(assistant.subagentMaxDepth, decoded.subagentMaxDepth)
        assertEquals(assistant.subagentProfiles, decoded.subagentProfiles)
        assertEquals(assistant.disabledBuiltinSubagents, decoded.disabledBuiltinSubagents)
        assertEquals(assistant.name, decoded.name)
    }

    @Test
    fun assistant_legacyJson_usesDefaults() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","name":"legacy"}"""
        val decoded = json.decodeFromString(Assistant.serializer(), legacy)
        assertFalse(decoded.enableSubagents)
        assertEquals(2, decoded.subagentMaxDepth)
        assertTrue(decoded.subagentProfiles.isEmpty())
        assertTrue(decoded.disabledBuiltinSubagents.isEmpty())
    }

    @Test
    fun workspaceAccess_matrix() {
        assertFalse(workspaceToolAllowed(WorkspaceAccess.NONE, "workspace_read_file"))
        assertTrue(workspaceToolAllowed(WorkspaceAccess.READ_ONLY, "workspace_read_file"))
        assertTrue(workspaceToolAllowed(WorkspaceAccess.READ_ONLY, "workspace_shell"))
        assertFalse(workspaceToolAllowed(WorkspaceAccess.READ_ONLY, "workspace_write_file"))
        assertTrue(workspaceToolAllowed(WorkspaceAccess.FULL, "workspace_write_file"))
        assertTrue(workspaceToolAllowed(WorkspaceAccess.FULL, "workspace_edit_file"))
    }

    @Test
    fun workspaceApproval_resolution() {
        val overrides = mapOf("workspace_shell" to true)
        assertFalse(
            resolveSubagentWorkspaceApproval(
                WorkspaceApproval.AUTO,
                overrides,
                "workspace_shell",
            ),
        )
        assertTrue(
            resolveSubagentWorkspaceApproval(
                WorkspaceApproval.INHERIT,
                overrides,
                "workspace_shell",
            ),
        )
        assertFalse(
            resolveSubagentWorkspaceApproval(
                WorkspaceApproval.OVERRIDE,
                overrides,
                "workspace_shell",
                profileOverrides = mapOf("workspace_shell" to false),
            ),
        )
    }

    @Test
    fun registry_resolveBuiltinWhenGlobalEmpty() {
        val assistant = Assistant()
        val explore = SubagentRegistry.resolveProfile("explore", assistant, globalProfiles = emptyList())
        assertNotNull(explore)
        assertEquals("explore", explore!!.name)
        val merged = mergeSubagentProfiles(assistant.subagentProfiles, global = emptyList())
        assertTrue(merged.any { it.name == "explore" })
    }

    @Test
    fun registry_resolveGlobalProfile() {
        val exploreBuiltin = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "explore" }
        val assistant = Assistant()
        val explore = SubagentRegistry.resolveProfile("explore", assistant, listOf(exploreBuiltin))
        assertNotNull(explore)
        assertEquals("explore", explore!!.name)
        assertEquals(WorkspaceAccess.READ_ONLY, explore.workspaceAccess)
    }

    @Test
    fun registry_respectsDisabledGlobal() {
        val coderBuiltin = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "coder" }
        val global = listOf(coderBuiltin)
        val assistant = Assistant(disabledGlobalSubagents = setOf("coder"))
        assertNull(SubagentRegistry.resolveProfile("coder", assistant, global))
        val all = SubagentRegistry.allProfiles(assistant, global)
        assertFalse(all.any { it.name == "coder" })
    }

    @Test
    fun registry_mergesCustomOverride() {
        val exploreBuiltin = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "explore" }
        val custom = SubagentProfile(
            name = "explore",
            maxToolCalls = 99,
        )
        val assistant = Assistant(subagentProfiles = listOf(custom))
        val resolved = SubagentRegistry.resolveProfile("explore", assistant, listOf(exploreBuiltin))
        assertNotNull(resolved)
        assertEquals("explore", resolved!!.name)
        assertEquals(exploreBuiltin.description, resolved.description)
        assertEquals(99, resolved.maxToolCalls)
    }

    @Test
    fun effectiveGlobalProfiles_emptyGlobal_returnsAllBuiltins() {
        val effective = SubagentRegistry.effectiveGlobalProfiles(emptyList())
        assertEquals(
            SubagentRegistry.BUILTIN_PROFILES.map { it.name }.toSet(),
            effective.map { it.name }.toSet(),
        )
    }

    @Test
    fun effectiveGlobalProfiles_partialGlobal_unionsMissingBuiltins() {
        val customExplore = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "explore" }.copy(
            description = "My Explore",
        )
        val effective = SubagentRegistry.effectiveGlobalProfiles(listOf(customExplore))
        val names = effective.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("explore", "coder", "reviewer")))
        assertEquals("My Explore", effective.first { it.name == "explore" }.description)
    }

    @Test
    fun effectiveGlobalProfiles_customOverridesBuiltinOnSameName() {
        val customCoder = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "coder" }.copy(maxToolCalls = 7)
        val effective = SubagentRegistry.effectiveGlobalProfiles(listOf(customCoder))
        assertEquals(7, effective.first { it.name == "coder" }.maxToolCalls)
        assertTrue(effective.any { it.name == "reviewer" })
    }

    @Test
    fun registry_allProfiles_includesGlobalAndCustom() {
        val global = SubagentRegistry.BUILTIN_PROFILES
        val assistant = Assistant(
            subagentProfiles = listOf(SubagentProfile(name = "extra")),
        )
        val names = SubagentRegistry.allProfiles(assistant, global).map { it.name }.toSet()
        assertTrue("explore" in names)
        assertTrue("coder" in names)
        assertTrue("reviewer" in names)
        assertTrue("extra" in names)
    }

    @Test
    fun builtinDefaults_matchSpec() {
        val explore = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "explore" }
        val coder = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "coder" }
        val reviewer = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "reviewer" }
        assertEquals(48, explore.maxToolCalls)
        assertFalse(explore.canSpawn)
        assertEquals(WorkspaceApproval.INHERIT, explore.workspaceApproval)
        assertEquals(64, coder.maxToolCalls)
        assertTrue(coder.canSpawn)
        assertEquals(WorkspaceApproval.AUTO, coder.workspaceApproval)
        assertEquals(WorkspaceAccess.FULL, coder.workspaceAccess)
        assertEquals(24, reviewer.maxToolCalls)
        assertFalse(reviewer.canSpawn)
        assertTrue(reviewer.excludedTools.contains("workspace_write_file"))
    }

    @Test
    fun migration_copiesBuiltinToGlobal() {
        val settings = Settings(init = false, subagentBuiltinMigrated = false)
        val result = migrateSubagentBuiltinsIfNeeded(settings)
        assertTrue(result.subagentBuiltinMigrated)
        val globalNames = result.globalSubagentProfiles.map { it.name }.toSet()
        val builtinNames = SubagentRegistry.BUILTIN_PROFILES.map { it.name }.toSet()
        assertTrue(globalNames.containsAll(builtinNames))
    }

    @Test
    fun migration_migratesDisabledBuiltinToGlobal() {
        val assistant = Assistant(
            id = Uuid.random(),
            name = "test",
            disabledBuiltinSubagents = setOf("coder"),
        )
        val settings = Settings(
            init = false,
            subagentBuiltinMigrated = false,
            assistants = listOf(assistant),
        )
        val result = migrateSubagentBuiltinsIfNeeded(settings)
        val migrated = result.assistants.first()
        assertTrue("coder" in migrated.disabledGlobalSubagents)
    }

    @Test
    fun migration_isIdempotent() {
        val assistant = Assistant(
            id = Uuid.random(),
            name = "test",
            disabledBuiltinSubagents = setOf("coder"),
        )
        val settings = Settings(
            init = false,
            subagentBuiltinMigrated = false,
            assistants = listOf(assistant),
        )
        val first = migrateSubagentBuiltinsIfNeeded(settings)
        val second = migrateSubagentBuiltinsIfNeeded(first)
        assertEquals(first.globalSubagentProfiles.size, second.globalSubagentProfiles.size)
        assertEquals(first.assistants.first().disabledGlobalSubagents, second.assistants.first().disabledGlobalSubagents)
    }

    @Test
    fun migration_preservesExistingGlobalDisabled() {
        val assistant = Assistant(
            id = Uuid.random(),
            name = "test",
            disabledBuiltinSubagents = setOf("coder"),
            disabledGlobalSubagents = setOf("reviewer"),
        )
        val settings = Settings(
            init = false,
            subagentBuiltinMigrated = false,
            assistants = listOf(assistant),
        )
        val result = migrateSubagentBuiltinsIfNeeded(settings)
        val migrated = result.assistants.first()
        assertTrue("reviewer" in migrated.disabledGlobalSubagents)
        assertTrue("coder" in migrated.disabledGlobalSubagents)
    }

    @Test
    fun migration_skipsWhenAlreadyMigrated() {
        val settings = Settings(init = false, subagentBuiltinMigrated = true)
        val result = migrateSubagentBuiltinsIfNeeded(settings)
        assertTrue(result.globalSubagentProfiles.isEmpty())
    }
}
