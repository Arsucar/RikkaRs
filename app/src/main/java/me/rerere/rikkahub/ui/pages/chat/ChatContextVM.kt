package me.rerere.rikkahub.ui.pages.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.ContextPreview
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

/**
 * Context-window preview for the chat right drawer.
 * Shares conversation id with [ChatVM] via the same NavBackStackEntry + parametersOf(id).
 */
class ChatContextVM(
    id: String,
    private val chatService: ChatService,
) : ViewModel() {
    private val conversationId: Uuid = Uuid.parse(id)
    private var contextPreviewJob: Job? = null

    val contextPreviewState = MutableStateFlow<UiState<ContextPreview>>(UiState.Idle)

    override fun onCleared() {
        contextPreviewJob?.cancel()
        super.onCleared()
    }

    fun loadContextPreview() {
        contextPreviewJob?.cancel()
        contextPreviewJob = viewModelScope.launch {
            contextPreviewState.value = UiState.Loading
            try {
                contextPreviewState.value = UiState.Success(chatService.buildContextPreview(conversationId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                contextPreviewState.value = UiState.Error(error)
            }
        }
    }

    fun clearContextPreview() {
        contextPreviewJob?.cancel()
        contextPreviewJob = null
        contextPreviewState.value = UiState.Idle
    }
}
