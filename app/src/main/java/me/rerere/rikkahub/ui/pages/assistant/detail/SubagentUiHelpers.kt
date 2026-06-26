package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.WorkspaceAccess
import me.rerere.rikkahub.data.ai.subagent.WorkspaceApproval
import me.rerere.rikkahub.data.model.Assistant

internal data class SubagentListEntry(
    val profile: SubagentProfile,
    val isBuiltin: Boolean,
    val isDisabledBuiltin: Boolean,
)

internal fun subagentListEntries(assistant: Assistant): List<SubagentListEntry> {
    val builtinNames = SubagentRegistry.BUILTIN_PROFILES.map { it.name }.toSet()
    val builtinByName = SubagentRegistry.BUILTIN_PROFILES.associateBy { it.name }
    val customByName = assistant.subagentProfiles.associateBy { it.name }

    val builtins = SubagentRegistry.BUILTIN_PROFILES.map { builtin ->
        val profile = customByName[builtin.name] ?: builtin
        val disabled = builtin.name in assistant.disabledBuiltinSubagents &&
            builtin.name !in customByName
        SubagentListEntry(profile = profile, isBuiltin = true, isDisabledBuiltin = disabled)
    }

    val customs = assistant.subagentProfiles
        .filter { it.name !in builtinNames }
        .map { profile ->
            SubagentListEntry(profile = profile, isBuiltin = false, isDisabledBuiltin = false)
        }

    return builtins + customs
}

internal fun assistantHasSpawnableProfile(assistant: Assistant): Boolean =
    subagentListEntries(assistant).any { entry ->
        !entry.isDisabledBuiltin && entry.profile.canSpawn
    }

@Composable
internal fun workspaceAccessLabel(access: WorkspaceAccess): String = when (access) {
    WorkspaceAccess.NONE -> stringResource(R.string.subagent_workspace_access_none)
    WorkspaceAccess.READ_ONLY -> stringResource(R.string.subagent_workspace_access_read_only)
    WorkspaceAccess.FULL -> stringResource(R.string.subagent_workspace_access_full)
}

@Composable
internal fun workspaceApprovalLabel(approval: WorkspaceApproval): String = when (approval) {
    WorkspaceApproval.INHERIT -> stringResource(R.string.subagent_workspace_approval_inherit)
    WorkspaceApproval.AUTO -> stringResource(R.string.subagent_workspace_approval_auto)
    WorkspaceApproval.OVERRIDE -> stringResource(R.string.subagent_workspace_approval_override)
}

internal fun isBuiltinSubagentName(name: String): Boolean =
    name in SubagentRegistry.BUILTIN_PROFILES.map { it.name }