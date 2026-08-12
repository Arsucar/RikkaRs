package me.rerere.rikkahub.ui.pages.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.GitChangeSection
import me.rerere.rikkahub.data.model.GitDiffUiState
import me.rerere.rikkahub.data.model.GitStatusUiState
import me.rerere.rikkahub.domain.git.GetAssistantGitStatusUseCase
import me.rerere.rikkahub.domain.git.GetGitFileDiffUseCase
import kotlin.uuid.Uuid

/**
 * Git status / diff for the chat right drawer.
 * Shares conversation id with [ChatVM] via the same NavBackStackEntry + parametersOf(id).
 */
class ChatGitVM(
    id: String,
    private val getAssistantGitStatus: GetAssistantGitStatusUseCase,
    private val getGitFileDiff: GetGitFileDiffUseCase,
) : ViewModel() {
    init {
        // Parse id early so invalid conversation keys fail at construction (same as sibling chat VMs).
        Uuid.parse(id)
    }

    private var gitStatusJob: Job? = null
    private var gitDiffJob: Job? = null
    private var gitStatusGeneration = 0L
    private var gitDiffGeneration = 0L
    private var loadingGitWorkspaceId: String? = null
    private var loadingGitWorkspaceCwd: String? = null
    // #180: 记录上次已完成加载的 workspace key，避免抽屉重开时对同一目标强制全量重载
    private var loadedGitWorkspaceId: String? = null
    private var loadedGitWorkspaceCwd: String? = null
    private var hasLoadedGitStatus = false

    val gitStatusState = MutableStateFlow<GitStatusUiState>(GitStatusUiState.Idle)
    val gitDiffState = MutableStateFlow<GitDiffUiState>(GitDiffUiState.Idle)
    val gitStatusWorkspaceId = MutableStateFlow<String?>(null)

    override fun onCleared() {
        gitStatusJob?.cancel()
        gitDiffJob?.cancel()
        super.onCleared()
    }

    /**
     * 加载助手绑定 workspace 的 Git 状态。
     *
     * #180: 抽屉每次打开都会调用本方法。为避免对同一 workspaceId + cwd 反复置 Loading
     * 并走 proot 双次 git 造成卡顿，非强制刷新时若已对同一目标加载过（无论成功与否）
     * 就复用现有 [gitStatusState]，不再重置。仅在 workspace/cwd 变化或
     * [forceRefresh]（手动刷新）时才重新全量拉取。
     */
    fun loadGitStatus(
        workspaceId: String?,
        workspaceCwd: String? = null,
        forceRefresh: Boolean = false,
    ) {
        // 已有同一目标的在途请求，直接复用，避免重复触发。
        if (gitStatusJob?.isActive == true &&
            loadingGitWorkspaceId == workspaceId &&
            loadingGitWorkspaceCwd == workspaceCwd
        ) {
            return
        }
        // 非强制刷新且已对同一目标加载过：复用缓存结果，不再置 Loading 重载。
        if (!forceRefresh &&
            hasLoadedGitStatus &&
            loadedGitWorkspaceId == workspaceId &&
            loadedGitWorkspaceCwd == workspaceCwd
        ) {
            // 仍需同步当前展示归属，保证 UI 过滤逻辑指向正确 workspace。
            gitStatusWorkspaceId.value = workspaceId
            return
        }
        gitStatusJob?.cancel()
        gitDiffJob?.cancel()
        loadingGitWorkspaceId = workspaceId
        loadingGitWorkspaceCwd = workspaceCwd
        gitStatusWorkspaceId.value = workspaceId
        val generation = ++gitStatusGeneration
        ++gitDiffGeneration
        gitStatusState.value = GitStatusUiState.Loading
        gitDiffState.value = GitDiffUiState.Idle
        gitStatusJob = viewModelScope.launch {
            val result = getAssistantGitStatus(workspaceId, workspaceCwd)
            if (generation == gitStatusGeneration) {
                gitStatusState.value = result
                loadingGitWorkspaceId = null
                loadingGitWorkspaceCwd = null
                // #180: 仅缓存稳定结果；瞬态错误（proot 未就绪/超时/git 不可用/执行失败）
                // 不缓存，使重开抽屉能自动重试，无需用户手动刷新。
                val isTransientError = result is GitStatusUiState.WorkspaceNotReady ||
                    result is GitStatusUiState.TimedOut ||
                    result is GitStatusUiState.GitUnavailable ||
                    result is GitStatusUiState.DirectoryUnavailable ||
                    result is GitStatusUiState.Failed
                if (isTransientError) {
                    hasLoadedGitStatus = false
                } else {
                    loadedGitWorkspaceId = workspaceId
                    loadedGitWorkspaceCwd = workspaceCwd
                    hasLoadedGitStatus = true
                }
            }
        }
    }

    fun loadGitDiff(
        workspaceId: String,
        path: String,
        section: GitChangeSection,
        workspaceCwd: String? = null,
    ) {
        gitDiffJob?.cancel()
        val generation = ++gitDiffGeneration
        gitDiffState.value = GitDiffUiState.Loading
        gitDiffJob = viewModelScope.launch {
            val result = getGitFileDiff(workspaceId, path, section, workspaceCwd)
            if (generation == gitDiffGeneration) {
                gitDiffState.value = result
            }
        }
    }

    fun clearGitDiff() {
        gitDiffJob?.cancel()
        gitDiffJob = null
        ++gitDiffGeneration
        gitDiffState.value = GitDiffUiState.Idle
    }
}
