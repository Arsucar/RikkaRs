package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentPermissionTest {
    private fun mockTool(
        name: String,
        needsApproval: (kotlinx.serialization.json.JsonElement) -> Boolean = { false },
        execute: suspend (kotlinx.serialization.json.JsonElement) -> List<UIMessagePart> = {
            listOf(UIMessagePart.Text("ok"))
        },
    ) = Tool(
        name = name,
        description = name,
        needsApproval = needsApproval,
        execute = execute,
    )

    private fun workspaceMocks(): List<Tool> = listOf(
        mockTool("workspace_read_file"),
        mockTool("workspace_write_file"),
        mockTool("workspace_edit_file"),
        mockTool("workspace_shell", needsApproval = { true }),
    )

    private fun profile(
        access: WorkspaceAccess = WorkspaceAccess.FULL,
        approval: WorkspaceApproval = WorkspaceApproval.INHERIT,
        excluded: Set<String> = emptySet(),
        inherit: Boolean = true,
        canSpawn: Boolean = false,
        overrides: Map<String, Boolean> = emptyMap(),
        prefixes: List<String> = listOf("/workspace"),
    ) = SubagentProfile(
        name = "test",
        workspaceAccess = access,
        workspaceApproval = approval,
        excludedTools = excluded,
        inheritTools = inherit,
        canSpawn = canSpawn,
        toolApprovalOverrides = overrides,
        allowedPathPrefixes = prefixes,
    )

    @Test
    fun noneAccess_noWorkspaceTools() {
        val result = buildSubagentTools(
            profile = profile(access = WorkspaceAccess.NONE),
            depth = 0,
            maxDepth = 2,
            parentTools = listOf(mockTool("search")),
            workspaceToolsFactory = { access ->
                if (access == WorkspaceAccess.NONE) emptyList() else workspaceMocks()
            },
        )
        assertTrue(result.none { it.name.startsWith("workspace_") })
        assertEquals(1, result.count { it.name == "search" })
    }

    @Test
    fun readOnlyAccess_onlyReadAndShell() {
        val result = buildSubagentTools(
            profile = profile(access = WorkspaceAccess.READ_ONLY),
            depth = 0,
            maxDepth = 2,
            parentTools = emptyList(),
            workspaceToolsFactory = { access ->
                filterWorkspaceToolsByAccess(workspaceMocks(), access)
            },
        )
        val names = result.map { it.name }.toSet()
        assertEquals(setOf("workspace_read_file", "workspace_shell"), names)
    }

    @Test
    fun fullAccess_allWorkspaceTools() {
        val result = buildSubagentTools(
            profile = profile(access = WorkspaceAccess.FULL),
            depth = 0,
            maxDepth = 2,
            parentTools = emptyList(),
            workspaceToolsFactory = { access ->
                filterWorkspaceToolsByAccess(workspaceMocks(), access)
            },
        )
        assertEquals(4, result.size)
        assertTrue(result.map { it.name }.containsAll(WorkspaceToolNames))
    }

    @Test
    fun autoApproval_needsApprovalAlwaysFalse() {
        val shell = mockTool("workspace_shell", needsApproval = { true })
        val out = applySubagentWorkspaceApproval(
            shell,
            profile(approval = WorkspaceApproval.AUTO),
            workspaceOverrides = mapOf("workspace_shell" to true),
        )
        assertFalse(out.needsApproval(buildJsonObject {}))
    }

    @Test
    fun inheritApproval_matchesWorkspaceOverride() {
        val shell = mockTool("workspace_shell", needsApproval = { false })
        val out = applySubagentWorkspaceApproval(
            shell,
            profile(approval = WorkspaceApproval.INHERIT),
            workspaceOverrides = mapOf("workspace_shell" to true),
        )
        assertEquals(shell.needsApproval, out.needsApproval)
    }

    @Test
    fun overrideApproval_usesProfileMap() {
        val shell = mockTool("workspace_shell", needsApproval = { true })
        val out = applySubagentWorkspaceApproval(
            shell,
            profile(
                approval = WorkspaceApproval.OVERRIDE,
                overrides = mapOf("workspace_shell" to false),
            ),
            workspaceOverrides = mapOf("workspace_shell" to true),
        )
        assertFalse(out.needsApproval(buildJsonObject {}))
    }

    @Test
    fun pathOutsidePrefixes_returnsErrorWithoutExecuting() = runBlocking {
        var executed = false
        val tool = mockTool("workspace_write_file") {
            executed = true
            listOf(UIMessagePart.Text("done"))
        }.withPathValidation(listOf("/workspace"))
        val input = buildJsonObject { put("path", "/etc/passwd") }
        val parts = tool.execute(input)
        assertFalse(executed)
        val textPart = parts.single() as UIMessagePart.Text
        assertTrue(textPart.text.contains("outside allowed prefixes"))
    }

    @Test
    fun pathInsidePrefixes_executes() = runBlocking {
        var executed = false
        val tool = mockTool("workspace_read_file") {
            executed = true
            listOf(UIMessagePart.Text("done"))
        }.withPathValidation(listOf("/workspace"))
        val input = buildJsonObject { put("path", "/workspace/foo.txt") }
        tool.execute(input)
        assertTrue(executed)
    }

    @Test
    fun excludedTools_removesFromInheritedSet() {
        val result = buildSubagentTools(
            profile = profile(excluded = setOf("search", "workspace_shell")),
            depth = 0,
            maxDepth = 2,
            parentTools = listOf(mockTool("search"), mockTool("mcp_x")),
            workspaceToolsFactory = { access ->
                filterWorkspaceToolsByAccess(workspaceMocks(), access)
            },
        )
        assertFalse(result.any { it.name == "search" })
        assertFalse(result.any { it.name == "workspace_shell" })
        assertTrue(result.any { it.name == "mcp_x" })
    }

    @Test
    fun spawnInjected_whenCanSpawnAndDepthAllows() {
        val spawn = mockTool("spawn_subagent")
        val result = buildSubagentTools(
            profile = profile(canSpawn = true),
            depth = 0,
            maxDepth = 2,
            parentTools = emptyList(),
            workspaceToolsFactory = { emptyList() },
            spawnToolBuilder = { spawn },
        )
        assertTrue(result.any { it.name == "spawn_subagent" })
    }

    @Test
    fun spawnNotInjected_whenDepthAtMax() {
        val result = buildSubagentTools(
            profile = profile(canSpawn = true),
            depth = 1,
            maxDepth = 2,
            parentTools = emptyList(),
            workspaceToolsFactory = { emptyList() },
            spawnToolBuilder = { mockTool("spawn_subagent") },
        )
        assertFalse(result.any { it.name == "spawn_subagent" })
    }

    @Test
    fun spawnNotInjected_whenCanSpawnFalse() {
        val result = buildSubagentTools(
            profile = profile(canSpawn = false),
            depth = 0,
            maxDepth = 2,
            parentTools = emptyList(),
            workspaceToolsFactory = { emptyList() },
            spawnToolBuilder = { mockTool("spawn_subagent") },
        )
        assertFalse(result.any { it.name == "spawn_subagent" })
    }

    @Test
    fun pathMatchesAllowedPrefixes_defaultWorkspace() {
        assertTrue(pathMatchesAllowedPrefixes("/workspace/a", listOf("/workspace")))
        assertFalse(pathMatchesAllowedPrefixes("/etc/passwd", listOf("/workspace")))
    }

    @Test
    fun extractPathCandidate_resolvesRelativeCwd() {
        val input = buildJsonObject { put("cwd", "proj") }
        assertEquals("/workspace/proj", extractPathCandidateFromInput(input))
    }

    @Test
    fun sandboxToolsForSubagent_preservesNeedsApproval() {
        val shell = mockTool("workspace_shell", needsApproval = { true })
        val approved = applySubagentWorkspaceApproval(
            shell,
            profile(approval = WorkspaceApproval.OVERRIDE),
            workspaceOverrides = mapOf("workspace_shell" to true),
        )
        val out = SubagentHost.sandboxToolsForSubagent(listOf(approved)).single()
        assertTrue(out.needsApproval(buildJsonObject {}))
    }

    @Test
    fun sandboxToolsForSubagent_autoApprovalStillFalse() {
        val shell = mockTool("workspace_shell", needsApproval = { true })
        val out = applySubagentWorkspaceApproval(
            shell,
            profile(approval = WorkspaceApproval.AUTO),
            workspaceOverrides = mapOf("workspace_shell" to true),
        )
        val sandboxed = SubagentHost.sandboxToolsForSubagent(listOf(out)).single()
        assertFalse(sandboxed.needsApproval(buildJsonObject {}))
    }
}
