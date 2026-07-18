package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.workspace.WorkspaceShellStatus
import java.util.Locale
import kotlin.uuid.Uuid

const val WORKSPACE_READ_FILE_TOOL = "workspace_read_file"
const val WORKSPACE_WRITE_FILE_TOOL = "workspace_write_file"
const val WORKSPACE_EDIT_FILE_TOOL = "workspace_edit_file"
const val WORKSPACE_SHELL_TOOL = "workspace_shell"

val WORKSPACE_TOOL_NAMES = listOf(
    WORKSPACE_READ_FILE_TOOL,
    WORKSPACE_WRITE_FILE_TOOL,
    WORKSPACE_EDIT_FILE_TOOL,
    WORKSPACE_SHELL_TOOL,
)

enum class WorkspaceUnavailableReason {
    UNCONFIGURED,
    MISSING,
    DISABLED,
    INSTALLING,
    BROKEN,
    UNKNOWN,
}

enum class NormalizedWorkspaceShellStatus {
    DISABLED,
    INSTALLING,
    READY,
    BROKEN,
    UNKNOWN,
}

fun normalizeWorkspaceShellStatus(status: String): NormalizedWorkspaceShellStatus =
    when (status.trim().uppercase(Locale.ROOT)) {
        WorkspaceShellStatus.DISABLED.name -> NormalizedWorkspaceShellStatus.DISABLED
        WorkspaceShellStatus.INSTALLING.name -> NormalizedWorkspaceShellStatus.INSTALLING
        WorkspaceShellStatus.READY.name -> NormalizedWorkspaceShellStatus.READY
        WorkspaceShellStatus.BROKEN.name -> NormalizedWorkspaceShellStatus.BROKEN
        else -> NormalizedWorkspaceShellStatus.UNKNOWN
    }

data class WorkspaceToolCapability(
    val configured: Boolean,
    val workspace: WorkspaceEntity?,
    val available: Boolean,
    val availableToolNames: List<String>,
    val unavailableReason: WorkspaceUnavailableReason?,
)

fun resolveWorkspaceToolCapability(
    workspaceId: Uuid?,
    workspaces: List<WorkspaceEntity>,
    readOnly: Boolean = false,
): WorkspaceToolCapability = resolveWorkspaceToolCapability(
    workspaceId = workspaceId?.toString(),
    workspaces = workspaces,
    readOnly = readOnly,
)

fun resolveWorkspaceToolCapability(
    workspaceId: String?,
    workspaces: List<WorkspaceEntity>,
    readOnly: Boolean = false,
): WorkspaceToolCapability {
    if (workspaceId.isNullOrBlank()) {
        return WorkspaceToolCapability(false, null, false, emptyList(), WorkspaceUnavailableReason.UNCONFIGURED)
    }
    val workspace = workspaces.firstOrNull { it.id == workspaceId }
        ?: return WorkspaceToolCapability(true, null, false, emptyList(), WorkspaceUnavailableReason.MISSING)
    val reason = when (normalizeWorkspaceShellStatus(workspace.shellStatus)) {
        NormalizedWorkspaceShellStatus.READY -> null
        NormalizedWorkspaceShellStatus.DISABLED -> WorkspaceUnavailableReason.DISABLED
        NormalizedWorkspaceShellStatus.INSTALLING -> WorkspaceUnavailableReason.INSTALLING
        NormalizedWorkspaceShellStatus.BROKEN -> WorkspaceUnavailableReason.BROKEN
        NormalizedWorkspaceShellStatus.UNKNOWN -> WorkspaceUnavailableReason.UNKNOWN
    }
    return WorkspaceToolCapability(
        configured = true,
        workspace = workspace,
        available = reason == null,
        availableToolNames = when {
            reason != null -> emptyList()
            readOnly -> listOf(WORKSPACE_READ_FILE_TOOL)
            else -> WORKSPACE_TOOL_NAMES
        },
        unavailableReason = reason,
    )
}

sealed interface WorkspaceEnableDecision {
    data object CreateOrManage : WorkspaceEnableDecision
    data object Invalid : WorkspaceEnableDecision
    data class Bind(val workspaceId: Uuid) : WorkspaceEnableDecision
    data class Select(val workspaces: List<WorkspaceEntity>) : WorkspaceEnableDecision
}

fun decideWorkspaceEnable(workspaces: List<WorkspaceEntity>): WorkspaceEnableDecision {
    val valid = workspaces.filter { runCatching { Uuid.parse(it.id) }.isSuccess }
    return when (valid.size) {
        0 -> if (workspaces.isEmpty()) WorkspaceEnableDecision.CreateOrManage else WorkspaceEnableDecision.Invalid
        1 -> WorkspaceEnableDecision.Bind(Uuid.parse(valid.single().id))
        else -> WorkspaceEnableDecision.Select(valid)
    }
}

sealed interface WorkspaceSelectionDecision {
    data object Cancel : WorkspaceSelectionDecision
    data object Invalid : WorkspaceSelectionDecision
    data class Bind(val workspaceId: Uuid) : WorkspaceSelectionDecision
}

fun decideWorkspaceSelection(id: String?): WorkspaceSelectionDecision {
    if (id == null) return WorkspaceSelectionDecision.Cancel
    val workspaceId = runCatching { Uuid.parse(id) }.getOrNull()
        ?: return WorkspaceSelectionDecision.Invalid
    return WorkspaceSelectionDecision.Bind(workspaceId)
}
