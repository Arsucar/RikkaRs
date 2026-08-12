package me.rerere.rikkahub.ui.pages.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.HookExecutionRecord
import me.rerere.rikkahub.data.model.HookRunHistory
import me.rerere.rikkahub.data.repository.HookRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.hooks.MemoryTableHookPreview
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

/**
 * Memory-table hook preview / run / history for the chat right drawer.
 * Shares conversation id with [ChatVM] via the same NavBackStackEntry + parametersOf(id).
 */
class ChatHookVM(
    id: String,
    private val chatService: ChatService,
    hookRepository: HookRepository,
) : ViewModel() {
    private val conversationId: Uuid = Uuid.parse(id)

    val hookHistoryState: StateFlow<UiState<List<HookRunHistory>>> = hookRepository
        .observeHistory(conversationId)
        .map<List<HookRunHistory>, UiState<List<HookRunHistory>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val hookPreviewState = MutableStateFlow<UiState<MemoryTableHookPreview>>(UiState.Idle)
    val hookManualRunState = MutableStateFlow<UiState<HookExecutionRecord>>(UiState.Idle)

    fun previewMemoryTableHook(hookId: Uuid) {
        viewModelScope.launch {
            hookPreviewState.value = UiState.Loading
            hookPreviewState.value = runCatching {
                chatService.previewMemoryTableHook(conversationId, hookId)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }

    fun applyMemoryTableHookPreview(preview: MemoryTableHookPreview) {
        viewModelScope.launch {
            hookManualRunState.value = UiState.Loading
            hookManualRunState.value = runCatching {
                chatService.applyMemoryTableHookPreview(preview)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }

    fun runMemoryTableHookNow(hookId: Uuid) {
        viewModelScope.launch {
            hookManualRunState.value = UiState.Loading
            hookManualRunState.value = runCatching {
                chatService.runMemoryTableHookNow(conversationId, hookId)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }

    fun retryMemoryTableHookExecution(executionId: Uuid) {
        viewModelScope.launch {
            hookManualRunState.value = UiState.Loading
            hookManualRunState.value = runCatching {
                chatService.retryMemoryTableHookExecution(executionId)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }

    fun clearMemoryTableHookActionState() {
        hookPreviewState.value = UiState.Idle
        hookManualRunState.value = UiState.Idle
    }
}
