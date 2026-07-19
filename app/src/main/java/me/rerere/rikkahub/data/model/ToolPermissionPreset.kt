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

enum class ToolPresetApplyStatus { APPLIED, SKIPPED_UNKNOWN, REJECTED_RELAXATION, TARGET_NOT_FOUND, INVALID }

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

fun ToolPermissionPreset.isValidForPersistence(): Boolean =
    version == 1 &&
        name.isNotBlank() && name.length <= 128 &&
        description.length <= 512 &&
        permissions.size <= 256 &&
        permissions.keys.all { key ->
            key.isNotBlank() && key.length <= 256 &&
                !key.startsWith("mcp:") &&
                (!key.startsWith("skill:") || key == "skill:management")
        }

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
        (existing == ToolPermission.DENY && value != ToolPermission.DENY) ||
            (existing == ToolPermission.ASK && value == ToolPermission.ALLOW) ||
            (existing == ToolPermission.INHERIT && value == ToolPermission.ALLOW)
    }
    return ToolPresetDiff(changed, unknown, widens)
}

fun applyToolPermissionPreset(
    preset: ToolPermissionPreset,
    assistant: Assistant,
    knownCapabilityIds: Set<String>,
    confirmRelaxation: Boolean = false,
): ToolPresetTargetResult {
    if (!preset.isValidForPersistence()) {
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
        if (diff.unknownKeys.isEmpty()) ToolPresetApplyStatus.APPLIED else ToolPresetApplyStatus.SKIPPED_UNKNOWN,
        changed = diff.changed,
        skippedKeys = diff.unknownKeys,
    )
}

/** Copies only stable permission keys; resource bindings and secrets are never part of a preset. */
fun permissionPresetFromAssistant(
    name: String,
    source: Assistant,
    knownCapabilityIds: Set<String>,
): ToolPermissionPreset = ToolPermissionPreset(
    name = name,
    permissions = source.toolPermissions
        .filterKeys {
            it in knownCapabilityIds && !it.startsWith("mcp:") &&
                (!it.startsWith("skill:") || it == "skill:management")
        },
)

fun batchToolPermission(
    assistant: Assistant,
    capabilityIds: Set<String>,
    permission: ToolPermission,
    knownCapabilityIds: Set<String>,
): ToolPresetTargetResult {
    val changed = capabilityIds
        .filter { it in knownCapabilityIds }
        .associateWith { permission }
    return ToolPresetTargetResult(
        assistant.id,
        if (capabilityIds.all { it in knownCapabilityIds }) {
            ToolPresetApplyStatus.APPLIED
        } else {
            ToolPresetApplyStatus.SKIPPED_UNKNOWN
        },
        changed = changed,
        skippedKeys = capabilityIds - knownCapabilityIds,
    )
}
