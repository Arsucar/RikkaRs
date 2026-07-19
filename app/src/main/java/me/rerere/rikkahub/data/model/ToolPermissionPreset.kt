package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ToolPermissionPreset(
    val id: Uuid = Uuid.random(),
    val name: String,
    val description: String = "",
    val permissions: Map<String, ToolPermission> = emptyMap(),
    val version: Int = 1,
)

enum class ToolPresetApplyStatus { APPLIED, SKIPPED_UNKNOWN, REJECTED_RELAXATION, INVALID }

data class ToolPresetTargetResult(
    val assistantId: Uuid,
    val status: ToolPresetApplyStatus,
    val changed: Map<String, ToolPermission> = emptyMap(),
    val skippedKeys: Set<String> = emptySet(),
)

data class ToolPresetDiff(
    val changed: Map<String, ToolPermission>,
    val unknownKeys: Set<String>,
    val widensAccess: Boolean,
)

fun builtInToolPermissionPresets(): List<ToolPermissionPreset> = listOf(
    ToolPermissionPreset(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        name = "readonly-research",
        description = "Read-only research tools",
        permissions = mapOf(
            "workspace:workspace_read_file" to ToolPermission.ALLOW,
            "workspace:workspace_shell" to ToolPermission.DENY,
            "workspace:workspace_write_file" to ToolPermission.DENY,
            "workspace:workspace_edit_file" to ToolPermission.DENY,
        ),
    ),
    ToolPermissionPreset(
        id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        name = "approval-workspace",
        description = "Workspace tools require approval",
        permissions = mapOf(
            "workspace:workspace_read_file" to ToolPermission.ALLOW,
            "workspace:workspace_write_file" to ToolPermission.ASK,
            "workspace:workspace_edit_file" to ToolPermission.ASK,
            "workspace:workspace_shell" to ToolPermission.ASK,
        ),
    ),
    ToolPermissionPreset(
        id = Uuid.parse("00000000-0000-0000-0000-000000000003"),
        name = "least-privilege",
        description = "Deny every known capability unless explicitly allowed",
        permissions = mapOf(
            "builtin:web_search" to ToolPermission.ALLOW,
            "builtin:scrape_web" to ToolPermission.ALLOW,
            "memory:normal" to ToolPermission.DENY,
            "memory:table" to ToolPermission.DENY,
            "skill:management" to ToolPermission.DENY,
        ),
    ),
)

fun diffToolPermissionPreset(
    preset: ToolPermissionPreset,
    current: Map<String, ToolPermission>,
    knownCapabilityIds: Set<String>,
): ToolPresetDiff {
    val known = preset.permissions.filterKeys { it in knownCapabilityIds }
    val unknown = preset.permissions.keys - knownCapabilityIds
    val changed = known.filter { (key, value) -> current[key] != value }
    val widens = changed.any { (key, value) ->
        val existing = current[key] ?: ToolPermission.INHERIT
        existing == ToolPermission.DENY && value != ToolPermission.DENY
    }
    return ToolPresetDiff(changed, unknown, widens)
}

fun applyToolPermissionPreset(
    preset: ToolPermissionPreset,
    assistant: Assistant,
    knownCapabilityIds: Set<String>,
    confirmRelaxation: Boolean = false,
): ToolPresetTargetResult {
    if (preset.version != 1 || preset.name.isBlank() || preset.permissions.size > 256 ||
        preset.permissions.keys.any { it.startsWith("mcp:") || it.startsWith("skill:") && it != "skill:management" }
    ) {
        return ToolPresetTargetResult(assistant.id, ToolPresetApplyStatus.INVALID)
    }
    val diff = diffToolPermissionPreset(preset, assistant.toolPermissions, knownCapabilityIds)
    if (diff.widensAccess && !confirmRelaxation) {
        return ToolPresetTargetResult(
            assistant.id,
            ToolPresetApplyStatus.REJECTED_RELAXATION,
            skippedKeys = diff.unknownKeys,
        )
    }
    return ToolPresetTargetResult(
        assistant.id,
        ToolPresetApplyStatus.APPLIED,
        changed = diff.changed,
        skippedKeys = diff.unknownKeys,
    )
}
