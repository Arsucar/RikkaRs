package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.GitChangeKind
import me.rerere.rikkahub.data.model.GitChangeSection
import me.rerere.rikkahub.data.model.GitDiffUiState
import me.rerere.rikkahub.data.model.GitFileChange
import me.rerere.rikkahub.data.model.GitRepositoryStatus
import me.rerere.rikkahub.data.model.GitStatusUiState

@Composable
internal fun gitStatusMenuSubtitle(state: GitStatusUiState): String = when (state) {
    GitStatusUiState.Idle,
    GitStatusUiState.Loading,
    -> stringResource(R.string.git_status_loading)

    GitStatusUiState.Unbound -> stringResource(R.string.git_status_unbound_short)
    GitStatusUiState.WorkspaceMissing -> stringResource(R.string.git_status_workspace_missing_short)
    is GitStatusUiState.WorkspaceNotReady -> stringResource(R.string.git_status_workspace_not_ready_short)
    is GitStatusUiState.Success -> {
        val ref = state.status.branch
            ?: stringResource(R.string.git_status_detached_short, state.status.detachedHead ?: "HEAD")
        val count = if (state.status.truncated) {
            stringResource(R.string.git_status_change_count_truncated, state.status.changeCount)
        } else {
            stringResource(R.string.git_status_change_count, state.status.changeCount)
        }
        stringResource(R.string.git_status_menu_summary, state.status.projectName, ref, count)
    }

    GitStatusUiState.DirectoryUnavailable,
    GitStatusUiState.PermissionDenied,
    GitStatusUiState.NotRepository,
    GitStatusUiState.TimedOut,
    GitStatusUiState.GitUnavailable,
    GitStatusUiState.Failed,
    -> stringResource(R.string.git_status_unavailable_short)
}

@Composable
internal fun ConversationGitStatusDrawer(
    statusState: GitStatusUiState,
    workspaceId: String?,
    diffState: GitDiffUiState,
    onRefresh: () -> Unit,
    onOpenDiff: (String, GitChangeSection) -> Unit,
    onClearDiff: () -> Unit,
    onNavigateWorkspaceBinding: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedDiff by remember { mutableStateOf<SelectedGitDiff?>(null) }

    LaunchedEffect(statusState) {
        if (statusState !is GitStatusUiState.Success) {
            selectedDiff = null
            onClearDiff()
        }
    }

    fun closeDiff() {
        selectedDiff = null
        onClearDiff()
    }

    BackHandler(enabled = selectedDiff != null) { closeDiff() }

    val selected = selectedDiff?.takeIf {
        workspaceId != null && it.workspaceId == workspaceId && statusState is GitStatusUiState.Success
    }
    if (selected != null) {
        GitDiffContent(
            selected = selected,
            state = diffState,
            onBack = ::closeDiff,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GitDrawerHeader(
            title = stringResource(R.string.git_status_title),
            loading = statusState is GitStatusUiState.Loading,
            onBack = onBack,
            onRefresh = onRefresh,
        )
        when (statusState) {
            GitStatusUiState.Idle,
            GitStatusUiState.Loading,
            -> GitLoadingState()

            GitStatusUiState.Unbound -> GitMessageState(
                title = stringResource(R.string.git_status_unbound_title),
                message = stringResource(R.string.git_status_unbound_message),
                action = stringResource(R.string.git_status_open_binding),
                onAction = onNavigateWorkspaceBinding,
            )

            GitStatusUiState.WorkspaceMissing -> GitMessageState(
                title = stringResource(R.string.git_status_workspace_missing_title),
                message = stringResource(R.string.git_status_workspace_missing_message),
                action = stringResource(R.string.git_status_open_binding),
                onAction = onNavigateWorkspaceBinding,
            )

            is GitStatusUiState.WorkspaceNotReady -> GitMessageState(
                title = stringResource(R.string.git_status_workspace_not_ready_title),
                message = stringResource(R.string.git_status_workspace_not_ready_message),
                action = stringResource(R.string.git_status_open_binding),
                onAction = onNavigateWorkspaceBinding,
            )

            GitStatusUiState.DirectoryUnavailable -> GitMessageState(
                title = stringResource(R.string.git_status_directory_unavailable_title),
                message = stringResource(R.string.git_status_directory_unavailable_message),
                action = stringResource(R.string.git_status_open_binding),
                onAction = onNavigateWorkspaceBinding,
            )

            GitStatusUiState.PermissionDenied -> GitRetryState(
                title = stringResource(R.string.git_status_permission_denied_title),
                message = stringResource(R.string.git_status_permission_denied_message),
                onRetry = onRefresh,
            )

            GitStatusUiState.NotRepository -> GitRetryState(
                title = stringResource(R.string.git_status_not_repository_title),
                message = stringResource(R.string.git_status_not_repository_message),
                onRetry = onRefresh,
            )

            GitStatusUiState.TimedOut -> GitRetryState(
                title = stringResource(R.string.git_status_timeout_title),
                message = stringResource(R.string.git_status_timeout_message),
                onRetry = onRefresh,
            )

            GitStatusUiState.GitUnavailable -> GitRetryState(
                title = stringResource(R.string.git_status_git_unavailable_title),
                message = stringResource(R.string.git_status_git_unavailable_message),
                onRetry = onRefresh,
            )

            GitStatusUiState.Failed -> GitRetryState(
                title = stringResource(R.string.git_status_failed_title),
                message = stringResource(R.string.git_status_failed_message),
                onRetry = onRefresh,
            )

            is GitStatusUiState.Success -> GitStatusContent(
                status = statusState.status,
                onOpenDiff = { change, section ->
                    selectedDiff = SelectedGitDiff(statusState.status.workspaceId, change, section)
                    onOpenDiff(change.path, section)
                },
            )
        }
    }
}

@Composable
private fun GitDrawerHeader(
    title: String,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.git_status_back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Lucide.RefreshCw, contentDescription = stringResource(R.string.git_status_refresh))
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun GitLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GitRetryState(title: String, message: String, onRetry: () -> Unit) {
    GitMessageState(
        title = title,
        message = message,
        action = stringResource(R.string.git_status_retry),
        onAction = onRetry,
    )
}

@Composable
private fun GitMessageState(
    title: String,
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(action)
            }
        }
    }
}

@Composable
private fun GitStatusContent(
    status: GitRepositoryStatus,
    onOpenDiff: (GitFileChange, GitChangeSection) -> Unit,
) {
    val staged = remember(status.changes) { status.changesIn(GitChangeSection.STAGED) }
    val unstaged = remember(status.changes) { status.changesIn(GitChangeSection.UNSTAGED) }
    val untracked = remember(status.changes) { status.changesIn(GitChangeSection.UNTRACKED) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "repository-header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = status.projectName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = status.branch ?: stringResource(
                        R.string.git_status_detached,
                        status.detachedHead ?: "HEAD",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (status.truncated) {
            item(key = "truncated-warning") {
                Text(
                    text = stringResource(R.string.git_status_results_truncated, status.changeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (status.changeCount == 0) {
            item(key = "clean") {
                Text(
                    text = stringResource(R.string.git_status_clean),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            gitChangeSection(
                titleRes = R.string.git_status_staged_changes,
                section = GitChangeSection.STAGED,
                changes = staged,
                onOpenDiff = onOpenDiff,
            )
            gitChangeSection(
                titleRes = R.string.git_status_unstaged_changes,
                section = GitChangeSection.UNSTAGED,
                changes = unstaged,
                onOpenDiff = onOpenDiff,
            )
            gitChangeSection(
                titleRes = R.string.git_status_untracked_files,
                section = GitChangeSection.UNTRACKED,
                changes = untracked,
                onOpenDiff = onOpenDiff,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.gitChangeSection(
    titleRes: Int,
    section: GitChangeSection,
    changes: List<GitFileChange>,
    onOpenDiff: (GitFileChange, GitChangeSection) -> Unit,
) {
    if (changes.isEmpty()) return
    item(key = "header:${section.name}") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
    items(
        items = changes,
        key = { change -> "${section.name}:${change.path}" },
    ) { change ->
        GitFileRow(change, section) { onOpenDiff(change, section) }
    }
}

@Composable
private fun GitFileRow(
    change: GitFileChange,
    section: GitChangeSection,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = change.kindFor(section).shortLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = change.path,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                change.originalPath?.let { original ->
                    Text(
                        text = stringResource(R.string.git_status_renamed_from, original),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Lucide.ChevronRight, contentDescription = stringResource(R.string.git_status_open_diff))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
}

@Composable
private fun GitDiffContent(
    selected: SelectedGitDiff,
    state: GitDiffUiState,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.git_status_back_to_changes))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selected.change.path,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = selected.change.kindFor(selected.section).displayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        when (state) {
            GitDiffUiState.Idle,
            GitDiffUiState.Loading,
            -> GitLoadingState()

            GitDiffUiState.Binary -> GitMessageState(
                title = stringResource(R.string.git_diff_binary_title),
                message = stringResource(R.string.git_diff_binary_message),
            )

            GitDiffUiState.DirectoryUnavailable -> GitMessageState(
                title = stringResource(R.string.git_status_directory_unavailable_title),
                message = stringResource(R.string.git_status_directory_unavailable_message),
            )

            GitDiffUiState.InvalidPath -> GitMessageState(
                title = stringResource(R.string.git_diff_invalid_path_title),
                message = stringResource(R.string.git_diff_invalid_path_message),
            )

            GitDiffUiState.PermissionDenied -> GitMessageState(
                title = stringResource(R.string.git_status_permission_denied_title),
                message = stringResource(R.string.git_status_permission_denied_message),
            )

            GitDiffUiState.TimedOut -> GitMessageState(
                title = stringResource(R.string.git_status_timeout_title),
                message = stringResource(R.string.git_diff_timeout_message),
            )

            GitDiffUiState.Failed -> GitMessageState(
                title = stringResource(R.string.git_diff_failed_title),
                message = stringResource(R.string.git_diff_failed_message),
            )

            is GitDiffUiState.Text -> GitTextDiff(state)
        }
    }
}

@Composable
private fun GitTextDiff(state: GitDiffUiState.Text) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.truncated) {
            Text(
                text = stringResource(R.string.git_diff_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (state.content.isEmpty()) {
            Text(
                text = stringResource(R.string.git_diff_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                        .padding(12.dp),
                ) {
                    Text(
                        text = state.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun GitChangeKind.displayLabel(): String = stringResource(
    when (this) {
        GitChangeKind.ADDED -> R.string.git_change_added
        GitChangeKind.MODIFIED -> R.string.git_change_modified
        GitChangeKind.DELETED -> R.string.git_change_deleted
        GitChangeKind.RENAMED -> R.string.git_change_renamed
        GitChangeKind.COPIED -> R.string.git_change_copied
        GitChangeKind.TYPE_CHANGED -> R.string.git_change_type_changed
        GitChangeKind.CONFLICTED -> R.string.git_change_conflicted
        GitChangeKind.UNTRACKED -> R.string.git_change_untracked
        GitChangeKind.UNKNOWN -> R.string.git_change_unknown
    }
)

private fun GitChangeKind.shortLabel(): String = when (this) {
    GitChangeKind.ADDED -> "A"
    GitChangeKind.MODIFIED -> "M"
    GitChangeKind.DELETED -> "D"
    GitChangeKind.RENAMED -> "R"
    GitChangeKind.COPIED -> "C"
    GitChangeKind.TYPE_CHANGED -> "T"
    GitChangeKind.CONFLICTED -> "U"
    GitChangeKind.UNTRACKED -> "?"
    GitChangeKind.UNKNOWN -> "-"
}

private data class SelectedGitDiff(
    val workspaceId: String,
    val change: GitFileChange,
    val section: GitChangeSection,
)
