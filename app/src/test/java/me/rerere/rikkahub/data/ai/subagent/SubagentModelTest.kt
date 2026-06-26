package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.Json
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
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
            displayName = "Custom",
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
    fun subagentResult_roundTrip() {
        val result = SubagentResult(
            profileName = "explore",
            summary = "done",
            succeeded = true,
            depth = 1,
            usage = TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15),
            steps = 3,
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
    fun assistant_withSubagentFields_roundTrip() {
        val assistant = Assistant(
            name = "a1",
            enableSubagents = true,
            subagentMaxDepth = 3,
            subagentProfiles = listOf(
                SubagentProfile(name = "my_bot", displayName = "My Bot"),
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
    fun registry_resolveBuiltin() {
        val assistant = Assistant()
        val explore = SubagentRegistry.resolveProfile("explore", assistant)
        assertNotNull(explore)
        assertEquals("explore", explore!!.name)
        assertEquals(WorkspaceAccess.READ_ONLY, explore.workspaceAccess)
    }

    @Test
    fun registry_respectsDisabledBuiltin() {
        val assistant = Assistant(disabledBuiltinSubagents = setOf("coder"))
        assertNull(SubagentRegistry.resolveProfile("coder", assistant))
        val all = SubagentRegistry.allProfiles(assistant)
        assertFalse(all.any { it.name == "coder" })
    }

    @Test
    fun registry_mergesCustomOverride() {
        val custom = SubagentProfile(
            name = "explore",
            displayName = "Custom Explorer",
            maxSteps = 99,
        )
        val assistant = Assistant(subagentProfiles = listOf(custom))
        val resolved = SubagentRegistry.resolveProfile("explore", assistant)
        assertNotNull(resolved)
        assertEquals("Custom Explorer", resolved!!.displayName)
        assertEquals(99, resolved.maxSteps)
    }

    @Test
    fun registry_allProfiles_includesBuiltinsAndCustom() {
        val assistant = Assistant(
            subagentProfiles = listOf(SubagentProfile(name = "extra", displayName = "Extra")),
        )
        val names = SubagentRegistry.allProfiles(assistant).map { it.name }.toSet()
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
        assertEquals(48, explore.maxSteps)
        assertFalse(explore.canSpawn)
        assertEquals(WorkspaceApproval.INHERIT, explore.workspaceApproval)
        assertEquals(64, coder.maxSteps)
        assertTrue(coder.canSpawn)
        assertEquals(WorkspaceApproval.AUTO, coder.workspaceApproval)
        assertEquals(WorkspaceAccess.FULL, coder.workspaceAccess)
        assertEquals(24, reviewer.maxSteps)
        assertFalse(reviewer.canSpawn)
        assertTrue(reviewer.excludedTools.contains("workspace_write_file"))
    }
}