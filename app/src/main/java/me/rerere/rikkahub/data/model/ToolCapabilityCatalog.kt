package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.SkillMetadata
import kotlin.uuid.Uuid

enum class ToolCapabilitySource { BUILTIN, LOCAL, CALENDAR, MEMORY, MEMORY_TABLE, WORKSPACE, SKILL, MCP, SUBAGENT }
enum class ToolApproval { NONE, USER, DELEGATE_ONLY }
enum class ToolCapabilityReason {
    AVAILABLE, DISABLED, GLOBAL_DISABLED, NOT_SELECTED, MISSING, UNAVAILABLE,
    CONNECTING, CONNECTION_ERROR, NEEDS_AUTHORIZATION, DELEGATE_ONLY, INVALID_CONFIGURATION,
    POLICY_DENIED,
}

data class ToolCapability(
    val id: String,
    val source: ToolCapabilitySource,
    val runtimeName: String,
    val displayName: String = runtimeName,
    val configured: Boolean,
    val available: Boolean,
    val effective: Boolean,
    val reasonCode: ToolCapabilityReason,
    val approval: ToolApproval = ToolApproval.NONE,
)

data class ToolCapabilitySnapshot(val capabilities: List<ToolCapability>) {
    val effectiveRuntimeNames: List<String>
        get() = capabilities.asSequence().filter { it.effective }.map { it.runtimeName }.distinct().toList()
    val effectiveCount: Int get() = effectiveRuntimeNames.size

    fun matchesRuntimeNames(runtimeNames: Collection<String>): Boolean =
        effectiveRuntimeNames.toSet() == runtimeNames.toSet()
}

private val DELEGATE_ALLOWED_LOCAL_TOOLS = setOf(
    LocalToolOption.TimeInfo,
    LocalToolOption.Clipboard,
    LocalToolOption.Logs,
    LocalToolOption.AskUser,
)

private fun LocalToolOption.runtimeNames(): List<String> = when (this) {
    LocalToolOption.JavascriptEngine -> listOf("eval_javascript")
    LocalToolOption.TimeInfo -> listOf("get_time_info")
    LocalToolOption.Clipboard -> listOf("clipboard_tool")
    LocalToolOption.Tts -> listOf("text_to_speech")
    LocalToolOption.AskUser -> listOf("ask_user")
    LocalToolOption.ScreenTime -> listOf("get_screen_time")
    LocalToolOption.Logs -> listOf("get_logs")
    LocalToolOption.Calendar -> listOf("calendar_query", "calendar_create")
}

fun isValidMcpServerRuntimeName(name: String): Boolean = name.isNotEmpty() && name.all {
    it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9'
}

fun assistantToolCapabilitySnapshot(
    assistant: Assistant,
    memoryTableGloballyEnabled: Boolean,
    workspaces: List<WorkspaceEntity> = emptyList(),
    visibleSkills: List<SkillMetadata> = emptyList(),
    mcpServerConfigs: List<McpServerConfig> = emptyList(),
    mcpStatuses: Map<Uuid, McpStatus> = emptyMap(),
): ToolCapabilitySnapshot {
    val capabilities = mutableListOf<ToolCapability>()
    val delegateOnly = assistant.enableSubagents && assistant.subagentDelegateOnly

    fun add(
        id: String,
        source: ToolCapabilitySource,
        runtimeName: String,
        configured: Boolean,
        available: Boolean = configured,
        effective: Boolean = configured && available,
        reason: ToolCapabilityReason = if (effective) ToolCapabilityReason.AVAILABLE else ToolCapabilityReason.DISABLED,
        approval: ToolApproval = ToolApproval.NONE,
        displayName: String = runtimeName,
    ) {
        capabilities += ToolCapability(
            id, source, runtimeName, displayName, configured, available, effective,
            if (available && effective) ToolCapabilityReason.AVAILABLE else reason, approval,
        )
    }

    add("builtin:web_search", ToolCapabilitySource.BUILTIN, "search_web", assistant.enableWebSearch)
    add("builtin:scrape_web", ToolCapabilitySource.BUILTIN, "scrape_web", assistant.enableWebSearch)
    add("builtin:recent_chats", ToolCapabilitySource.BUILTIN, "recent_chats", assistant.enableRecentChatsReference)
    add("builtin:conversation_search", ToolCapabilitySource.BUILTIN, "conversation_search", assistant.enableRecentChatsReference)

    add("memory:normal", ToolCapabilitySource.MEMORY, "memory_tool", assistant.enableMemory)
    add(
        "memory:table", ToolCapabilitySource.MEMORY_TABLE, "memory_table_tool",
        configured = assistant.enableMemoryTable,
        available = memoryTableGloballyEnabled,
        reason = if (memoryTableGloballyEnabled) ToolCapabilityReason.DISABLED else ToolCapabilityReason.GLOBAL_DISABLED,
    )

    val workspace = resolveWorkspaceToolCapability(
        workspaceId = assistant.workspaceId,
        workspaces = workspaces,
        readOnly = delegateOnly,
    )
    WORKSPACE_TOOL_NAMES.forEach { runtimeName ->
        val available = runtimeName in workspace.availableToolNames
        add(
            "workspace:$runtimeName", ToolCapabilitySource.WORKSPACE, runtimeName,
            configured = workspace.configured,
            available = available,
            effective = workspace.configured && available,
            reason = if (delegateOnly && workspace.available && !available) {
                ToolCapabilityReason.DELEGATE_ONLY
            } else when (workspace.unavailableReason) {
                WorkspaceUnavailableReason.MISSING -> ToolCapabilityReason.MISSING
                WorkspaceUnavailableReason.UNCONFIGURED -> ToolCapabilityReason.NOT_SELECTED
                else -> ToolCapabilityReason.UNAVAILABLE
            },
        )
    }

    assistant.localTools.distinct().forEach { option ->
        val allowed = !delegateOnly || option in DELEGATE_ALLOWED_LOCAL_TOOLS
        option.runtimeNames().forEach { runtimeName ->
            add(
                id = if (option == LocalToolOption.Calendar) "calendar:$runtimeName" else "local:$runtimeName",
                source = if (option == LocalToolOption.Calendar) ToolCapabilitySource.CALENDAR else ToolCapabilitySource.LOCAL,
                runtimeName = runtimeName,
                configured = true,
                available = true,
                effective = allowed,
                reason = ToolCapabilityReason.DELEGATE_ONLY,
                approval = if (delegateOnly) ToolApproval.DELEGATE_ONLY else ToolApproval.NONE,
            )
        }
    }

    val visibleByName = visibleSkills.associateBy { it.name }
    assistant.enabledSkills.distinct().forEach { skillName ->
        val visible = visibleByName[skillName]
        add(
            id = "skill:$skillName",
            source = ToolCapabilitySource.SKILL,
            runtimeName = "use_skill",
            displayName = skillName,
            configured = true,
            available = visible != null,
            effective = visible != null && !delegateOnly,
            reason = if (visible == null) ToolCapabilityReason.MISSING else ToolCapabilityReason.DELEGATE_ONLY,
        )
    }

    add(
        id = "skill:management",
        source = ToolCapabilitySource.SKILL,
        runtimeName = "skill_tool",
        configured = true,
        available = true,
        effective = !delegateOnly,
        reason = ToolCapabilityReason.DELEGATE_ONLY,
        approval = ToolApproval.USER,
    )

    mcpServerConfigs.forEach { server ->
        val selected = server.id in assistant.mcpServers
        val status = mcpStatuses[server.id] ?: McpStatus.Idle
        val validServerName = isValidMcpServerRuntimeName(server.commonOptions.name)
        server.commonOptions.tools.forEach { tool ->
            val serverEnabled = server.commonOptions.enable
            val connected = status == McpStatus.Connected
            val validToolName = tool.name.isNotBlank()
            val available = serverEnabled && tool.enable && connected && validServerName && validToolName
            val effective = selected && available && !delegateOnly
            val reason = when {
                delegateOnly -> ToolCapabilityReason.DELEGATE_ONLY
                !selected -> ToolCapabilityReason.NOT_SELECTED
                !validServerName || !validToolName -> ToolCapabilityReason.INVALID_CONFIGURATION
                !serverEnabled || !tool.enable -> ToolCapabilityReason.DISABLED
                status == McpStatus.NeedsAuthorization || status == McpStatus.Authorizing -> ToolCapabilityReason.NEEDS_AUTHORIZATION
                status is McpStatus.Error -> ToolCapabilityReason.CONNECTION_ERROR
                status == McpStatus.Connecting || status is McpStatus.Reconnecting -> ToolCapabilityReason.CONNECTING
                !connected -> ToolCapabilityReason.UNAVAILABLE
                else -> ToolCapabilityReason.AVAILABLE
            }
            add(
                id = "mcp:${server.id}:${tool.name}",
                source = ToolCapabilitySource.MCP,
                runtimeName = "mcp__${server.commonOptions.name}__${tool.name}",
                displayName = "${server.commonOptions.name} / ${tool.name}",
                configured = selected,
                available = available,
                effective = effective,
                reason = reason,
                approval = if (tool.needsApproval) ToolApproval.USER else ToolApproval.NONE,
            )
        }
    }

    if (assistant.enableSubagents) {
        listOf("spawn_subagent", "ask_btw", "manage_subagent_profile").forEach { runtimeName ->
            add("subagent:$runtimeName", ToolCapabilitySource.SUBAGENT, runtimeName, configured = true)
        }
    }
    return ToolCapabilitySnapshot(capabilities).applyPermissions(assistant.toolPermissions)
}
