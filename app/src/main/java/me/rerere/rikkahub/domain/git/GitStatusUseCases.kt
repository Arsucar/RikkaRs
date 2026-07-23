package me.rerere.rikkahub.domain.git

import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.GitChangeSection
import me.rerere.rikkahub.data.model.GitDiffUiState
import me.rerere.rikkahub.data.model.GitStatusUiState
import me.rerere.rikkahub.data.repository.GitDiffContent
import me.rerere.rikkahub.data.repository.GitReadException
import me.rerere.rikkahub.data.repository.WorkspaceGitRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus

class GetAssistantGitStatusUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val gitRepository: WorkspaceGitRepository,
) {
    suspend operator fun invoke(
        workspaceId: String?,
        workspaceCwd: String? = null,
    ): GitStatusUiState {
        return try {
            if (workspaceId == null) return GitStatusUiState.Unbound
            val workspace = workspaceRepository.getById(workspaceId)
            classifyGitWorkspaceBinding(workspaceId, workspace)?.let { return it }
            val resolvedWorkspace = checkNotNull(workspace)
            if (!workspaceRepository.workspaceFilesExist(resolvedWorkspace.id)) {
                return GitStatusUiState.DirectoryUnavailable
            }
            GitStatusUiState.Success(gitRepository.readStatus(resolvedWorkspace, workspaceCwd))
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            GitStatusUiState.PermissionDenied
        } catch (error: GitReadException) {
            error.toStatusUiState()
        } catch (_: IllegalArgumentException) {
            GitStatusUiState.DirectoryUnavailable
        } catch (_: Throwable) {
            GitStatusUiState.Failed
        }
    }
}

class GetGitFileDiffUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val gitRepository: WorkspaceGitRepository,
) {
    suspend operator fun invoke(
        workspaceId: String,
        path: String,
        section: GitChangeSection,
        workspaceCwd: String? = null,
    ): GitDiffUiState {
        return try {
            val workspace = workspaceRepository.getById(workspaceId) ?: return GitDiffUiState.Failed
            if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
                return GitDiffUiState.Failed
            }
            if (!workspaceRepository.workspaceFilesExist(workspace.id)) {
                return GitDiffUiState.DirectoryUnavailable
            }
            when (val result = gitRepository.readDiff(workspace, path, section, workspaceCwd)) {
                GitDiffContent.Binary -> GitDiffUiState.Binary
                is GitDiffContent.Text -> GitDiffUiState.Text(result.content, result.truncated)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            GitDiffUiState.PermissionDenied
        } catch (error: GitReadException) {
            error.toDiffUiState()
        } catch (_: Throwable) {
            GitDiffUiState.Failed
        }
    }
}

internal fun classifyGitWorkspaceBinding(
    workspaceId: String?,
    workspace: WorkspaceEntity?,
): GitStatusUiState? = when {
    workspaceId == null -> GitStatusUiState.Unbound
    workspace == null -> GitStatusUiState.WorkspaceMissing
    workspace.shellStatus != WorkspaceShellStatus.READY.name -> {
        GitStatusUiState.WorkspaceNotReady(workspace.shellStatus)
    }
    else -> null
}

private fun GitReadException.toStatusUiState(): GitStatusUiState = when (this) {
    GitReadException.DirectoryUnavailable -> GitStatusUiState.DirectoryUnavailable
    GitReadException.PermissionDenied -> GitStatusUiState.PermissionDenied
    GitReadException.NotRepository -> GitStatusUiState.NotRepository
    GitReadException.TimedOut -> GitStatusUiState.TimedOut
    GitReadException.GitUnavailable -> GitStatusUiState.GitUnavailable
    GitReadException.InvalidPath,
    GitReadException.Failed,
    -> GitStatusUiState.Failed
}

internal fun GitReadException.toDiffUiState(): GitDiffUiState = when (this) {
    GitReadException.InvalidPath -> GitDiffUiState.InvalidPath
    GitReadException.DirectoryUnavailable -> GitDiffUiState.DirectoryUnavailable
    GitReadException.PermissionDenied -> GitDiffUiState.PermissionDenied
    GitReadException.TimedOut -> GitDiffUiState.TimedOut
    GitReadException.NotRepository,
    GitReadException.GitUnavailable,
    GitReadException.Failed,
    -> GitDiffUiState.Failed
}
