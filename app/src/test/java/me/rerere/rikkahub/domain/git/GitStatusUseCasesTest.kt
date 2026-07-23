package me.rerere.rikkahub.domain.git

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.GitDiffUiState
import me.rerere.rikkahub.data.model.GitStatusUiState
import me.rerere.rikkahub.data.repository.GitReadException
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitStatusUseCasesTest {
    @Test
    fun nullBindingNeverFallsBackToAnotherWorkspace() {
        assertEquals(GitStatusUiState.Unbound, classifyGitWorkspaceBinding(null, readyWorkspace()))
    }

    @Test
    fun missingAndNotReadyBindingsFailClosed() {
        assertEquals(GitStatusUiState.WorkspaceMissing, classifyGitWorkspaceBinding("missing", null))
        assertEquals(
            GitStatusUiState.WorkspaceNotReady(WorkspaceShellStatus.INSTALLING.name),
            classifyGitWorkspaceBinding(
                "workspace",
                readyWorkspace().copy(shellStatus = WorkspaceShellStatus.INSTALLING.name),
            ),
        )
    }

    @Test
    fun readyBindingContinuesToRepositoryRead() {
        assertNull(classifyGitWorkspaceBinding("workspace", readyWorkspace()))
    }

    @Test
    fun diffErrorsKeepDirectoryAndPathFailuresDistinct() {
        assertEquals(
            GitDiffUiState.DirectoryUnavailable,
            GitReadException.DirectoryUnavailable.toDiffUiState(),
        )
        assertEquals(GitDiffUiState.InvalidPath, GitReadException.InvalidPath.toDiffUiState())
    }

    private fun readyWorkspace() = WorkspaceEntity(
        id = "workspace",
        name = "Project",
        root = "workspace",
        shellStatus = WorkspaceShellStatus.READY.name,
        createdAt = 0,
        updatedAt = 0,
    )
}
