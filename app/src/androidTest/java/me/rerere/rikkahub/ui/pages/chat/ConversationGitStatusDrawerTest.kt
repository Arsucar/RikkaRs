package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.test.ComposeTestActivity
import me.rerere.rikkahub.data.model.GitChangeSection
import me.rerere.rikkahub.data.model.GitDiffUiState
import me.rerere.rikkahub.data.model.GitFileChange
import me.rerere.rikkahub.data.model.GitRepositoryStatus
import me.rerere.rikkahub.data.model.GitStatusUiState
import org.junit.Rule
import org.junit.Test

class ConversationGitStatusDrawerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun unboundStateShowsBindingAction() {
        setDrawer(status = GitStatusUiState.Unbound)
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        composeRule.onNodeWithText(resources.getString(R.string.git_status_unbound_title)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.git_status_open_binding)).assertIsDisplayed()
    }

    @Test
    fun unboundActionRemainsReachableInShortViewport() {
        setDrawer(status = GitStatusUiState.Unbound, height = 180.dp)
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        composeRule.onNodeWithText(resources.getString(R.string.git_status_open_binding))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun cleanRepositoryShowsCleanState() {
        setDrawer(status = GitStatusUiState.Success(status(changes = emptyList())))
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        composeRule.onNodeWithText(resources.getString(R.string.git_status_clean)).assertIsDisplayed()
    }

    @Test
    fun groupedChangesOpenBinaryDiffWithoutBodyText() {
        val changes = listOf(
            GitFileChange("staged.bin", indexStatus = 'A'),
            GitFileChange("unstaged.txt", workTreeStatus = 'M'),
            GitFileChange("untracked.txt", indexStatus = '?', workTreeStatus = '?'),
        )
        setDrawer(
            status = GitStatusUiState.Success(status(changes)),
            diff = GitDiffUiState.Binary,
        )
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        composeRule.onNodeWithText(resources.getString(R.string.git_status_staged_changes)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.git_status_unstaged_changes)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.git_status_untracked_files)).assertIsDisplayed()
        composeRule.onNodeWithText("staged.bin").performClick()
        composeRule.onNodeWithText(resources.getString(R.string.git_diff_binary_title)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.git_diff_binary_message)).assertIsDisplayed()
    }

    private fun setDrawer(
        status: GitStatusUiState,
        diff: GitDiffUiState = GitDiffUiState.Idle,
        height: Dp? = null,
    ) {
        composeRule.setContent {
            MaterialTheme {
                val sizeModifier = if (height == null) {
                    Modifier.width(300.dp).fillMaxHeight()
                } else {
                    Modifier.width(300.dp).height(height)
                }
                Box(modifier = sizeModifier) {
                    ConversationGitStatusDrawer(
                        statusState = status,
                        workspaceId = "workspace",
                        diffState = diff,
                        onRefresh = {},
                        onOpenDiff = { _: String, _: GitChangeSection -> },
                        onClearDiff = {},
                        onNavigateWorkspaceBinding = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    private fun status(changes: List<GitFileChange>) = GitRepositoryStatus(
        workspaceId = "workspace",
        projectName = "Project",
        branch = "main",
        detachedHead = null,
        changes = changes,
        truncated = false,
    )
}
