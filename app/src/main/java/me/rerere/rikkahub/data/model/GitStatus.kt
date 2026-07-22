package me.rerere.rikkahub.data.model

enum class GitChangeSection {
    STAGED,
    UNSTAGED,
    UNTRACKED,
}

enum class GitChangeKind {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    TYPE_CHANGED,
    CONFLICTED,
    UNTRACKED,
    UNKNOWN,
}

data class GitFileChange(
    val path: String,
    val originalPath: String? = null,
    val indexStatus: Char = '.',
    val workTreeStatus: Char = '.',
    val isUnmerged: Boolean = false,
) {
    fun belongsTo(section: GitChangeSection): Boolean = when (section) {
        GitChangeSection.STAGED -> indexStatus != '.' && indexStatus != '?'
        GitChangeSection.UNSTAGED -> workTreeStatus != '.' && workTreeStatus != '?'
        GitChangeSection.UNTRACKED -> indexStatus == '?' && workTreeStatus == '?'
    }

    fun kindFor(section: GitChangeSection): GitChangeKind {
        if (isUnmerged) return GitChangeKind.CONFLICTED
        val status = when (section) {
            GitChangeSection.STAGED -> indexStatus
            GitChangeSection.UNSTAGED -> workTreeStatus
            GitChangeSection.UNTRACKED -> '?'
        }
        return when (status) {
            'A' -> GitChangeKind.ADDED
            'M' -> GitChangeKind.MODIFIED
            'D' -> GitChangeKind.DELETED
            'R' -> GitChangeKind.RENAMED
            'C' -> GitChangeKind.COPIED
            'T' -> GitChangeKind.TYPE_CHANGED
            'U' -> GitChangeKind.CONFLICTED
            '?' -> GitChangeKind.UNTRACKED
            else -> GitChangeKind.UNKNOWN
        }
    }
}

data class GitRepositoryStatus(
    val workspaceId: String,
    val projectName: String,
    val branch: String?,
    val detachedHead: String?,
    val changes: List<GitFileChange>,
    val truncated: Boolean,
) {
    val changeCount: Int
        get() = changes.distinctBy { it.path }.size

    fun changesIn(section: GitChangeSection): List<GitFileChange> =
        changes.filter { it.belongsTo(section) }
}

sealed interface GitStatusUiState {
    data object Idle : GitStatusUiState
    data object Loading : GitStatusUiState
    data object Unbound : GitStatusUiState
    data object WorkspaceMissing : GitStatusUiState
    data class WorkspaceNotReady(val status: String) : GitStatusUiState
    data object DirectoryUnavailable : GitStatusUiState
    data object PermissionDenied : GitStatusUiState
    data object NotRepository : GitStatusUiState
    data object TimedOut : GitStatusUiState
    data object GitUnavailable : GitStatusUiState
    data object Failed : GitStatusUiState
    data class Success(val status: GitRepositoryStatus) : GitStatusUiState
}

sealed interface GitDiffUiState {
    data object Idle : GitDiffUiState
    data object Loading : GitDiffUiState
    data object Binary : GitDiffUiState
    data object DirectoryUnavailable : GitDiffUiState
    data object InvalidPath : GitDiffUiState
    data object PermissionDenied : GitDiffUiState
    data object TimedOut : GitDiffUiState
    data object Failed : GitDiffUiState
    data class Text(
        val content: String,
        val truncated: Boolean,
    ) : GitDiffUiState
}
