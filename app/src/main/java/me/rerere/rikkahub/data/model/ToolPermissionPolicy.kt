package me.rerere.rikkahub.data.model

import me.rerere.ai.core.Tool

/** Stable-key policy resolution. ALLOW never overrides a tool's hard approval requirement. */
fun resolveToolPermission(
    capabilityId: String,
    configured: ToolPermission,
    inherited: ToolPermission = ToolPermission.INHERIT,
): ToolPermission = when (configured) {
    ToolPermission.INHERIT -> inherited
    else -> configured
}

fun Tool.applyToolPermission(permission: ToolPermission): Tool? = when (permission) {
    ToolPermission.DENY -> null
    ToolPermission.ASK -> copy(needsApproval = { true })
    ToolPermission.ALLOW, ToolPermission.INHERIT -> this
}

fun applyAssistantToolPermissions(
    tools: List<Tool>,
    permissions: Map<String, ToolPermission>,
    capabilityIdFor: (Tool) -> String = { stableCapabilityIdForRuntimeName(it.name) },
): List<Tool> = tools.mapNotNull { tool ->
    val policy = permissions[capabilityIdFor(tool)] ?: ToolPermission.INHERIT
    tool.applyToolPermission(policy)
}

fun stableCapabilityIdForRuntimeName(name: String): String = when (name) {
    "search_web" -> "builtin:web_search"
    "scrape_web" -> "builtin:scrape_web"
    "recent_chats" -> "builtin:recent_chats"
    "conversation_search" -> "builtin:conversation_search"
    "memory_tool" -> "memory:normal"
    "memory_table_tool" -> "memory:table"
    "skill_tool" -> "skill:management"
    "spawn_subagent", "ask_btw", "manage_subagent_profile" -> "subagent:$name"
    "calendar_query", "calendar_create" -> "calendar:$name"
    else -> when {
        name.startsWith("workspace_") -> "workspace:$name"
        else -> "local:$name"
    }
}

fun ToolCapabilitySnapshot.applyPermissions(
    permissions: Map<String, ToolPermission>,
): ToolCapabilitySnapshot = ToolCapabilitySnapshot(
    capabilities.map { capability ->
        when (permissions[capability.id] ?: ToolPermission.INHERIT) {
            ToolPermission.DENY -> capability.copy(
                effective = false,
                reasonCode = ToolCapabilityReason.DISABLED,
            )
            ToolPermission.ASK -> capability.copy(approval = ToolApproval.USER)
            ToolPermission.ALLOW, ToolPermission.INHERIT -> capability
        }
    },
)

fun orphanToolPermissionIds(
    permissions: Map<String, ToolPermission>,
    knownCapabilityIds: Set<String>,
): Set<String> = permissions.keys - knownCapabilityIds
