package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.WorkspaceAccess
import me.rerere.rikkahub.data.ai.subagent.WorkspaceApproval
import me.rerere.rikkahub.data.model.Assistant

internal enum class SubagentProfileSource {
    Global,
    Local,
}

internal data class SubagentListEntry(
    val profile: SubagentProfile,
    val source: SubagentProfileSource,
    val isDisabledGlobal: Boolean,
) {
    val isGlobal: Boolean get() = source == SubagentProfileSource.Global
    val isLocal: Boolean get() = source == SubagentProfileSource.Local
}

internal fun subagentListEntries(
    assistant: Assistant,
    globalProfiles: List<SubagentProfile> = emptyList(),
): List<SubagentListEntry> {
    val customByName = assistant.subagentProfiles.associateBy { it.name }

    val globals = SubagentRegistry.effectiveGlobalProfiles(globalProfiles)
        .filter { it.name !in customByName }
        .map { profile ->
            val disabled = profile.name in assistant.disabledGlobalSubagents
            SubagentListEntry(
                profile = profile,
                source = SubagentProfileSource.Global,
                isDisabledGlobal = disabled,
            )
        }

    val customs = assistant.subagentProfiles.map { profile ->
        SubagentListEntry(
            profile = profile,
            source = SubagentProfileSource.Local,
            isDisabledGlobal = false,
        )
    }

    return globals + customs
}

internal fun assistantHasSpawnableProfile(
    assistant: Assistant,
    globalProfiles: List<SubagentProfile> = emptyList(),
): Boolean =
    subagentListEntries(assistant, globalProfiles).any { entry ->
        !entry.isDisabledGlobal
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

@Composable
internal fun workspaceAccessDescription(access: WorkspaceAccess): String = when (access) {
    WorkspaceAccess.NONE -> stringResource(R.string.subagent_workspace_access_none_desc)
    WorkspaceAccess.READ_ONLY -> stringResource(R.string.subagent_workspace_access_read_only_desc)
    WorkspaceAccess.FULL -> stringResource(R.string.subagent_workspace_access_full_desc)
}

@Composable
internal fun workspaceApprovalDescription(approval: WorkspaceApproval): String = when (approval) {
    WorkspaceApproval.INHERIT -> stringResource(R.string.subagent_workspace_approval_inherit_desc)
    WorkspaceApproval.AUTO -> stringResource(R.string.subagent_workspace_approval_auto_desc)
    WorkspaceApproval.OVERRIDE -> stringResource(R.string.subagent_workspace_approval_override_desc)
}

internal fun isGlobalSubagentName(name: String, globalProfiles: List<SubagentProfile>): Boolean =
    name in SubagentRegistry.effectiveGlobalProfiles(globalProfiles).map { it.name }