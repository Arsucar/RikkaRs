package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.model.GitRepositoryStatus
import me.rerere.rikkahub.data.model.GitStatusUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GitStatusVisibilityTest {
    @Test
    fun oldWorkspaceSuccessIsHiddenDuringAssistantSwitch() {
        val oldState = GitStatusUiState.Success(
            GitRepositoryStatus(
                workspaceId = "workspace-a",
                projectName = "Private project A",
                branch = "main",
                detachedHead = null,
                changes = emptyList(),
                truncated = false,
            )
        )

        assertEquals(
            GitStatusUiState.Loading,
            gitStatusStateForWorkspace(
                state = oldState,
                stateWorkspaceId = "workspace-a",
                assistantWorkspaceId = "workspace-b",
            ),
        )
        assertEquals(
            GitStatusUiState.Unbound,
            gitStatusStateForWorkspace(
                state = oldState,
                stateWorkspaceId = "workspace-a",
                assistantWorkspaceId = null,
            ),
        )
    }

    @Test
    fun matchingWorkspaceKeepsCurrentState() {
        val state = GitStatusUiState.NotRepository

        assertSame(
            state,
            gitStatusStateForWorkspace(
                state = state,
                stateWorkspaceId = "workspace",
                assistantWorkspaceId = "workspace",
            ),
        )
    }
}
