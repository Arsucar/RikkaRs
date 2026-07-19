package me.rerere.rikkahub.ui.pages.assistant.detail

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.AssistantWorkspaceBindingUpdateResult
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import java.io.File
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.mcp.McpTool
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.assistantToolCapabilitySnapshot
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantToolsPageTest {
    @Test
    fun titleStatsIncludeDynamicLocalSkillAndMcpFromTheSharedSnapshot() {
        val server = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(name = "demo", tools = listOf(McpTool(name = "lookup"))),
        )
        val assistant = Assistant(
            localTools = listOf(LocalToolOption.Calendar),
            enabledSkills = setOf("alpha"),
            mcpServers = setOf(server.id),
        )
        val skills = listOf(SkillMetadata("alpha", "test", skillDir = File("alpha")))
        val statuses = mapOf(server.id to McpStatus.Connected)
        val snapshot = assistantToolCapabilitySnapshot(
            assistant, true, visibleSkills = skills, mcpServerConfigs = listOf(server), mcpStatuses = statuses,
        )

        assertEquals(
            snapshot.effectiveCount to snapshot.capabilities.size,
            empowermentToolStats(
                assistant, visibleSkills = skills, mcpServerConfigs = listOf(server), mcpStatuses = statuses,
            ),
        )
        assertTrue(snapshot.effectiveRuntimeNames.containsAll(listOf("calendar_query", "calendar_create", "use_skill", "mcp__demo__lookup")))
    }

    @Test
    fun memoryTableUiPreservesPreferenceWhenGlobalGateIsOff() {
        val state = memoryTableToolUiState(
            memoryTableGloballyEnabled = false,
            assistantMemoryTableEnabled = true,
        )

        assertTrue(state.checked)
        assertFalse(state.active)
        assertFalse(state.controlEnabled)
    }

    @Test
    fun memoryTableUiActivatesStoredPreferenceWhenGlobalGateReturns() {
        val state = memoryTableToolUiState(
            memoryTableGloballyEnabled = true,
            assistantMemoryTableEnabled = true,
        )

        assertTrue(state.checked)
        assertTrue(state.active)
        assertTrue(state.controlEnabled)
    }

    @Test
    fun empowermentStatsCountOnlyEffectiveMemoryTableCapability() {
        val assistant = Assistant(enableMemory = false, enableMemoryTable = true)
        val enabledWithGlobalGate = empowermentToolStats(
            assistant = assistant,
            memoryTableGloballyEnabled = true,
        ).first
        val enabledWithoutGlobalGate = empowermentToolStats(
            assistant = assistant,
            memoryTableGloballyEnabled = false,
        ).first

        assertEquals(1, enabledWithGlobalGate - enabledWithoutGlobalGate)
    }

    @Test
    fun workspaceSaveReportsMissingAssistantAsFailure() = runBlocking {
        val assistantId = Uuid.random()
        val attemptedWorkspaceId = Uuid.random()
        var persistCalls = 0

        val event = persistWorkspaceBinding(assistantId, attemptedWorkspaceId) { _, _ ->
            persistCalls++
            AssistantWorkspaceBindingUpdateResult.NOT_FOUND
        }

        assertEquals(WorkspaceBindingSaveEvent.Failure, event)
        assertEquals(1, persistCalls)
    }

    @Test
    fun workspaceSaveSuccessCommitsRequestedBinding() = runBlocking {
        val assistantId = Uuid.random()
        val attemptedWorkspaceId = Uuid.random()
        var persistedAssistantId: Uuid? = null
        var persistedWorkspaceId: Uuid? = null

        val event = persistWorkspaceBinding(assistantId, attemptedWorkspaceId) { targetId, workspaceId ->
            persistedAssistantId = targetId
            persistedWorkspaceId = workspaceId
            AssistantWorkspaceBindingUpdateResult.UPDATED
        }

        assertEquals(WorkspaceBindingSaveEvent.Success(attemptedWorkspaceId), event)
        assertEquals(assistantId, persistedAssistantId)
        assertEquals(attemptedWorkspaceId, persistedWorkspaceId)
    }

    @Test
    fun empowermentStatsUseTheSameReadyGateAsWorkspaceCapability() {
        val workspaceId = Uuid.random()
        val assistant = Assistant(workspaceId = workspaceId)
        fun workspace(status: String) = WorkspaceEntity(
            id = workspaceId.toString(),
            name = "Test",
            root = "/test",
            shellStatus = status,
            createdAt = 1,
            updatedAt = 1,
        )

        val ready = empowermentToolStats(assistant, listOf(workspace(WorkspaceShellStatus.READY.name))).first
        val disabled = empowermentToolStats(assistant, listOf(workspace(WorkspaceShellStatus.DISABLED.name))).first
        val missing = empowermentToolStats(assistant, emptyList()).first

        assertEquals(4, ready - disabled)
        assertEquals(disabled, missing)
    }
}
