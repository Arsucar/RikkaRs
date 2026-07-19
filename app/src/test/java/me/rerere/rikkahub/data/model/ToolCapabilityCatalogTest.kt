package me.rerere.rikkahub.data.model

import java.io.File
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.mcp.McpTool
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ToolCapabilityCatalogTest {
    @Test
    fun localOptionsMatchRuntimeNamesAndCalendarExpands() {
        val snapshot = snapshot(Assistant(localTools = listOf(LocalToolOption.Calendar, LocalToolOption.Clipboard)))
        val localRuntimeNames = snapshot.capabilities
            .filter { it.source == ToolCapabilitySource.LOCAL || it.source == ToolCapabilitySource.CALENDAR }
            .filter { it.effective }
            .map { it.runtimeName }
            .toSet()
        assertEquals(setOf("calendar_query", "calendar_create", "clipboard_tool"), localRuntimeNames)
    }

    @Test
    fun multipleVisibleSkillsShareOneRuntimeNameButKeepSourceRows() {
        val skills = listOf(skill("alpha"), skill("beta"))
        val snapshot = snapshot(
            Assistant(enabledSkills = setOf("alpha", "beta"), localTools = emptyList()),
            skills = skills,
        )
        assertEquals(
            2,
            snapshot.capabilities.count {
                it.source == ToolCapabilitySource.SKILL && it.id != "skill:management"
            },
        )
        assertEquals(setOf("use_skill", "skill_tool"), snapshot.effectiveRuntimeNames.toSet())
    }

    @Test
    fun sameNamedMcpToolsAreIsolatedByServerId() {
        val first = server("first", "lookup")
        val second = server("second", "lookup")
        val assistant = Assistant(mcpServers = setOf(first.id, second.id))
        val snapshot = snapshot(
            assistant, servers = listOf(first, second),
            statuses = mapOf(first.id to McpStatus.Connected, second.id to McpStatus.Connected),
        )
        val mcp = snapshot.capabilities.filter { it.source == ToolCapabilitySource.MCP }
        assertEquals(2, mcp.map { it.id }.distinct().size)
        assertEquals(setOf("mcp__first__lookup", "mcp__second__lookup"), mcp.map { it.runtimeName }.toSet())
    }

    @Test
    fun mcpAvailabilityRequiresConnectedAndReportsIdleAndAuth() {
        val connected = server("connected", "tool")
        val idle = server("idle", "tool")
        val auth = server("auth", "tool")
        val assistant = Assistant(mcpServers = setOf(connected.id, idle.id, auth.id))
        val result = snapshot(
            assistant, servers = listOf(connected, idle, auth),
            statuses = mapOf(connected.id to McpStatus.Connected, auth.id to McpStatus.NeedsAuthorization),
        ).capabilities.associateBy { it.id }
        assertTrue(result.getValue("mcp:${connected.id}:tool").effective)
        val idleCapability = result.getValue("mcp:${idle.id}:tool")
        val authCapability = result.getValue("mcp:${auth.id}:tool")
        assertFalse(idleCapability.effective)
        assertFalse(idleCapability.available)
        assertEquals(ToolCapabilityReason.UNAVAILABLE, idleCapability.reasonCode)
        assertFalse(authCapability.effective)
        assertFalse(authCapability.available)
        assertEquals(ToolCapabilityReason.NEEDS_AUTHORIZATION, authCapability.reasonCode)
    }

    @Test
    fun mcpStatusReasonsCoverTransientAuthorizationAndFailureStates() {
        val statuses = listOf(
            McpStatus.Connecting to ToolCapabilityReason.CONNECTING,
            McpStatus.Reconnecting(1, 3) to ToolCapabilityReason.CONNECTING,
            McpStatus.Authorizing to ToolCapabilityReason.NEEDS_AUTHORIZATION,
            McpStatus.Error("boom") to ToolCapabilityReason.CONNECTION_ERROR,
        )

        statuses.forEachIndexed { index, (status, expectedReason) ->
            val server = server("server$index", "tool")
            val capability = snapshot(
                Assistant(mcpServers = setOf(server.id)),
                servers = listOf(server),
                statuses = mapOf(server.id to status),
            ).capabilities.single { it.source == ToolCapabilitySource.MCP }

            assertTrue(capability.configured)
            assertFalse(capability.available)
            assertFalse(capability.effective)
            assertEquals(expectedReason, capability.reasonCode)
        }
    }

    @Test
    fun mcpDisabledInvalidAndApprovalMetadataRemainVisible() {
        val disabledServer = server("disabled", "tool", serverEnabled = false)
        val disabledTool = server("toolDisabled", "tool", toolEnabled = false)
        val invalidServer = server("invalid-name", "tool")
        val invalidTool = server("valid", "", needsApproval = true)
        val assistant = Assistant(
            mcpServers = setOf(disabledServer.id, disabledTool.id, invalidServer.id, invalidTool.id),
        )
        val result = snapshot(
            assistant,
            servers = listOf(disabledServer, disabledTool, invalidServer, invalidTool),
            statuses = listOf(disabledServer, disabledTool, invalidServer, invalidTool)
                .associate { it.id to McpStatus.Connected },
        ).capabilities.filter { it.source == ToolCapabilitySource.MCP }

        assertEquals(
            ToolCapabilityReason.DISABLED,
            result.single { it.id == "mcp:${disabledServer.id}:tool" }.reasonCode,
        )
        assertEquals(
            ToolCapabilityReason.DISABLED,
            result.single { it.id == "mcp:${disabledTool.id}:tool" }.reasonCode,
        )
        assertEquals(
            ToolCapabilityReason.INVALID_CONFIGURATION,
            result.single { it.id == "mcp:${invalidServer.id}:tool" }.reasonCode,
        )
        val invalidToolCapability = result.single { it.id == "mcp:${invalidTool.id}:" }
        assertEquals(ToolCapabilityReason.INVALID_CONFIGURATION, invalidToolCapability.reasonCode)
        assertEquals(ToolApproval.USER, invalidToolCapability.approval)
        assertTrue(result.none { it.available || it.effective })
    }

    @Test
    fun unavailableAndUnselectedReasonsAreNotReplacedByAvailable() {
        val selected = server("selected", "tool")
        val unselected = server("unselected", "tool")
        val snapshot = snapshot(
            Assistant(mcpServers = setOf(selected.id)),
            servers = listOf(selected, unselected),
            statuses = mapOf(selected.id to McpStatus.Connected, unselected.id to McpStatus.Connected),
        )

        assertEquals(
            ToolCapabilityReason.AVAILABLE,
            snapshot.capabilities.first { it.id == "mcp:${selected.id}:tool" }.reasonCode,
        )
        assertEquals(
            ToolCapabilityReason.NOT_SELECTED,
            snapshot.capabilities.first { it.id == "mcp:${unselected.id}:tool" }.reasonCode,
        )
    }

    @Test
    fun missingSkillsDoNotInjectUseSkillButManagementRemainsAvailable() {
        val snapshot = snapshot(
            Assistant(enabledSkills = setOf("missing"), localTools = emptyList()),
            skills = emptyList(),
        )

        assertFalse("use_skill" in snapshot.effectiveRuntimeNames)
        assertTrue("skill_tool" in snapshot.effectiveRuntimeNames)
        assertEquals(
            ToolCapabilityReason.MISSING,
            snapshot.capabilities.first { it.id == "skill:missing" }.reasonCode,
        )
        assertEquals(
            ToolApproval.USER,
            snapshot.capabilities.first { it.id == "skill:management" }.approval,
        )
    }

    @Test
    fun mcpServerRuntimeNameValidationMatchesGenerationNamespaceRules() {
        assertTrue(isValidMcpServerRuntimeName("Server01"))
        assertFalse(isValidMcpServerRuntimeName(""))
        assertFalse(isValidMcpServerRuntimeName("server-name"))
        assertFalse(isValidMcpServerRuntimeName("服务器"))
    }

    @Test
    fun delegateOnlyUsesRuntimeAllowlistAndRootHasNoFinishWork() {
        val assistant = Assistant(
            enableSubagents = true,
            subagentDelegateOnly = true,
            localTools = listOf(LocalToolOption.TimeInfo, LocalToolOption.JavascriptEngine),
        )
        val snapshot = snapshot(assistant)
        assertTrue("get_time_info" in snapshot.effectiveRuntimeNames)
        assertFalse("eval_javascript" in snapshot.effectiveRuntimeNames)
        assertFalse("finish_work" in snapshot.capabilities.map { it.runtimeName })
        assertEquals(
            ToolApproval.DELEGATE_ONLY,
            snapshot.capabilities.first { it.runtimeName == "eval_javascript" }.approval,
        )
        assertEquals(
            ToolCapabilityReason.DELEGATE_ONLY,
            snapshot.capabilities.first { it.id == "skill:management" }.reasonCode,
        )
    }

    @Test
    fun builtinAndSubagentRuntimeNamesMatchGenerationContract() {
        val snapshot = snapshot(
            Assistant(
                enableWebSearch = true,
                enableRecentChatsReference = true,
                enableSubagents = true,
                localTools = emptyList(),
            ),
        )

        assertEquals(
            setOf("search_web", "scrape_web", "recent_chats", "conversation_search"),
            snapshot.capabilities.filter { it.source == ToolCapabilitySource.BUILTIN }
                .map { it.runtimeName }
                .toSet(),
        )
        assertEquals(
            setOf("spawn_subagent", "ask_btw", "manage_subagent_profile"),
            snapshot.capabilities.filter { it.source == ToolCapabilitySource.SUBAGENT }
                .map { it.runtimeName }
                .toSet(),
        )
    }

    @Test
    fun snapshotDoesNotCreateSkillDirectories() {
        val root = File(System.getProperty("java.io.tmpdir"), "rikka-capability-${Uuid.random()}")
        val skillDir = File(root, "alpha")
        assertFalse(root.exists())

        snapshot(
            Assistant(enabledSkills = setOf("alpha"), localTools = emptyList()),
            skills = listOf(SkillMetadata("alpha", "description", skillDir = skillDir)),
        )

        assertFalse(root.exists())
    }

    @Test
    fun workspaceAndMemoryGatesRemainIndependent() {
        val workspaceId = Uuid.random()
        val assistant = Assistant(workspaceId = workspaceId, enableMemory = true, enableMemoryTable = true)
        val ready = WorkspaceEntity(
            id = workspaceId.toString(), name = "ready", root = "/ready",
            shellStatus = WorkspaceShellStatus.READY.name, createdAt = 1, updatedAt = 1,
        )
        val open = snapshot(assistant, memoryTableGate = true, workspaces = listOf(ready))
        val gated = snapshot(assistant, memoryTableGate = false, workspaces = emptyList())
        assertTrue("memory_tool" in gated.effectiveRuntimeNames)
        assertFalse("memory_table_tool" in gated.effectiveRuntimeNames)
        assertEquals(4, open.effectiveRuntimeNames.count { it.startsWith("workspace_") })
        assertEquals(0, gated.effectiveRuntimeNames.count { it.startsWith("workspace_") })
    }

    @Test
    fun delegateOnlyWorkspaceMatchesReadOnlyRuntimeTools() {
        val workspaceId = Uuid.random()
        val assistant = Assistant(
            workspaceId = workspaceId,
            enableSubagents = true,
            subagentDelegateOnly = true,
        )
        val ready = WorkspaceEntity(
            id = workspaceId.toString(), name = "ready", root = "/ready",
            shellStatus = WorkspaceShellStatus.READY.name, createdAt = 1, updatedAt = 1,
        )

        val result = snapshot(assistant, workspaces = listOf(ready))

        assertTrue(WORKSPACE_READ_FILE_TOOL in result.effectiveRuntimeNames)
        assertFalse(WORKSPACE_SHELL_TOOL in result.effectiveRuntimeNames)
        assertFalse(WORKSPACE_WRITE_FILE_TOOL in result.effectiveRuntimeNames)
        assertFalse(WORKSPACE_EDIT_FILE_TOOL in result.effectiveRuntimeNames)
    }

    @Test
    fun effectiveRuntimeNamesAreDistinct() {
        val snapshot = snapshot(
            Assistant(enabledSkills = setOf("alpha", "beta"), localTools = emptyList()),
            skills = listOf(skill("alpha"), skill("beta")),
        )
        assertEquals(snapshot.effectiveRuntimeNames.distinct(), snapshot.effectiveRuntimeNames)
        assertEquals(snapshot.effectiveRuntimeNames.size, snapshot.effectiveCount)
        assertTrue(snapshot.matchesRuntimeNames(listOf("use_skill", "skill_tool")))
    }

    private fun snapshot(
        assistant: Assistant,
        memoryTableGate: Boolean = true,
        workspaces: List<WorkspaceEntity> = emptyList(),
        skills: List<SkillMetadata> = emptyList(),
        servers: List<McpServerConfig> = emptyList(),
        statuses: Map<Uuid, McpStatus> = emptyMap(),
    ) = assistantToolCapabilitySnapshot(assistant, memoryTableGate, workspaces, skills, servers, statuses)

    private fun skill(name: String) = SkillMetadata(name, "$name description", skillDir = File(name))

    private fun server(
        name: String,
        tool: String,
        serverEnabled: Boolean = true,
        toolEnabled: Boolean = true,
        needsApproval: Boolean = false,
    ) = McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(
            name = name,
            enable = serverEnabled,
            tools = listOf(McpTool(name = tool, enable = toolEnabled, needsApproval = needsApproval)),
        ),
    )
}
