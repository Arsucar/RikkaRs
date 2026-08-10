package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.ToolPermission
import me.rerere.rikkahub.data.model.applyAssistantToolPermissions
import me.rerere.rikkahub.data.ai.tools.WorkspaceKnownMount
import me.rerere.rikkahub.data.ai.tools.createFinishWorkTool
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceBindMount

val WorkspaceToolNames: Set<String> = setOf(
    "workspace_read_file",
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_shell",
)

private val PathValidationToolNames: Set<String> = setOf(
    "workspace_read_file",
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_shell",
)

fun pathMatchesAllowedPrefixes(path: String, allowedPrefixes: List<String>): Boolean {
    val normalized = path.trim().replace('\\', '/')
    if (normalized.isBlank()) return false
    val absolute = if (normalized.startsWith("/")) {
        normalized
    } else {
        "/workspace/$normalized".replace("//", "/")
    }
    return allowedPrefixes.any { prefix ->
        val p = prefix.trimEnd('/').ifBlank { "/" }
        absolute == p || absolute.startsWith("$p/")
    }
}

internal fun extractPathCandidateFromInput(input: JsonElement): String? {
    val obj = input as? JsonObject ?: return null
    val pathKey = listOf("path", "directory").firstOrNull { obj[it] != null } ?: "cwd"
    val raw = obj[pathKey]?.jsonPrimitive?.contentOrNull ?: return null
    val trimmed = raw.trim().replace('\\', '/')
    if (trimmed.isBlank()) return null
    return if (trimmed.startsWith("/")) trimmed else "/workspace/$trimmed".replace("//", "/")
}

fun pathPrefixViolationMessage(path: String, allowedPrefixes: List<String>): String =
    "Path $path is outside allowed prefixes: $allowedPrefixes"

fun Tool.withPathValidation(allowedPrefixes: List<String>): Tool {
    if (name !in PathValidationToolNames) return this
    val inner = execute
    return copy(
        execute = { input ->
            val candidate = extractPathCandidateFromInput(input)
            if (candidate != null && !pathMatchesAllowedPrefixes(candidate, allowedPrefixes)) {
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("error", pathPrefixViolationMessage(candidate, allowedPrefixes))
                        }.toString(),
                    ),
                )
            } else {
                inner(input)
            }
        },
    )
}

fun applySubagentWorkspaceApproval(
    tool: Tool,
    profile: SubagentProfile,
    workspaceOverrides: Map<String, Boolean>,
): Tool = when (profile.workspaceApproval) {
    WorkspaceApproval.AUTO -> tool.copy(needsApproval = { false })
    WorkspaceApproval.INHERIT -> tool
    WorkspaceApproval.OVERRIDE -> {
        val requires = resolveSubagentWorkspaceApproval(
            profile.workspaceApproval,
            workspaceOverrides,
            tool.name,
            profile.toolApprovalOverrides,
        )
        tool.copy(needsApproval = { requires })
    }
}

fun filterWorkspaceToolsByAccess(tools: List<Tool>, access: WorkspaceAccess): List<Tool> =
    tools.filter { workspaceToolAllowed(access, it.name) }

suspend fun createSubagentWorkspaceTools(
    access: WorkspaceAccess,
    profile: SubagentProfile,
    workspaceRepository: WorkspaceRepository,
    workspaceId: String,
    workspaceCwd: String? = null,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
): List<Tool> {
    if (access == WorkspaceAccess.NONE || workspaceId.isBlank()) return emptyList()
    val workspaceEntity = workspaceRepository.getById(workspaceId)
    val workspaceOverrides = workspaceEntity?.toolApprovalOverrides().orEmpty()
    val trustedRoots = workspaceEntity?.trustedWriteRootList().orEmpty()
    return filterWorkspaceToolsByAccess(
        createWorkspaceTools(
            workspaceId = workspaceId,
            workspaceRepository = workspaceRepository,
            cwd = workspaceCwd,
            knownMounts = knownMounts,
            extraBindMounts = extraBindMounts,
            approvalOverrides = workspaceOverrides,
            trustedWriteRoots = trustedRoots,
        ),
        access,
    )
        .map { applySubagentWorkspaceApproval(it, profile, workspaceOverrides) }
        .map { it.withPathValidation(profile.allowedPathPrefixes) }
}

fun buildSubagentTools(
    profile: SubagentProfile,
    depth: Int,
    maxDepth: Int,
    parentTools: List<Tool>,
    workspaceToolsFactory: (WorkspaceAccess) -> List<Tool>,
    spawnToolBuilder: (() -> Tool)? = null,
    extraLocalToolsProvider: () -> List<Tool> = { emptyList() },
    parentToolPermissions: Map<String, ToolPermission> = emptyMap(),
): List<Tool> {
    val workspaceTools = workspaceToolsFactory(profile.workspaceAccess)
    val base = if (profile.inheritTools) {
        val nonWorkspaceParent = parentTools
            .filter { it.name !in WorkspaceToolNames }
            .filter { it.name !in SUBAGENT_TOOL_NAMES }
            .filter { it.name !in profile.excludedTools }
        val extras = extraLocalToolsProvider()
            .filter { it.name !in WorkspaceToolNames }
            .filter { it.name !in SUBAGENT_TOOL_NAMES }
            .filter { it.name !in profile.excludedTools }
        nonWorkspaceParent + extras + workspaceTools
    } else {
        // TODO: expand with profile.localTools, enabledSkills, mcpServerIds when inheritTools is false
        workspaceTools
    }
    val withoutExcludedWorkspace = base.filter { it.name !in profile.excludedTools }
    val withSpawn = if (
        profile.canSpawn &&
        (depth + 1) <= maxDepth &&
        spawnToolBuilder != null
    ) {
        withoutExcludedWorkspace + spawnToolBuilder()
    } else {
        withoutExcludedWorkspace
    }
    // Profile/workspace settings may narrow the parent, never restore a parent DENY.
    val parentFiltered = applyAssistantToolPermissions(withSpawn, parentToolPermissions)
    return (parentFiltered + createFinishWorkTool()).distinctBy { it.name }
}
