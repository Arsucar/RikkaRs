package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.hasInputDraftReplyTarget
import me.rerere.rikkahub.ui.components.ai.requireInputDraftText
import me.rerere.rikkahub.ui.hooks.ChatInputState
import java.util.Locale
import kotlin.uuid.Uuid

/**
 * Composer draft streaming, translation, title/suggestion helpers for chat.
 * Owns [inputState] so draft streaming stays co-located with the composer buffer
 * (avoids TransactionTooLargeException and draft/input race).
 * Shares conversation id with [ChatVM] via the same NavBackStackEntry + parametersOf(id).
 */
class ChatDraftVM(
    id: String,
    private val context: Application,
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
) : ViewModel() {
    private val conversationId: Uuid = Uuid.parse(id)
    private val conversation: StateFlow<Conversation> = chatService.getConversationFlow(conversationId)

    private val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    private var inputDraftJob: Job? = null
    private var inputDraftGeneration = 0L
    private var originalInputDraftText: String? = null
    private var lastInputDraftText: String? = null
    private val _inputDraftLoading = MutableStateFlow(false)
    val inputDraftLoading = _inputDraftLoading.asStateFlow()

    // #181: 草稿生成成功完成时发出事件，由 UI 复用现有 Toaster 提示。
    // 带 1 格缓冲 + tryEmit，避免 emit 时无活跃订阅者（页面切换/销毁）导致挂起。
    private val _inputDraftSuccessFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val inputDraftSuccessFlow: SharedFlow<Unit> = _inputDraftSuccessFlow

    override fun onCleared() {
        inputDraftJob?.cancel()
        super.onCleared()
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        chatService.translateMessage(conversationId, message, targetLanguage)
    }

    fun clearTranslationField(messageId: Uuid) {
        chatService.clearTranslationField(conversationId, messageId)
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch
            chatService.generateTitle(conversationId, conversationFull, force)
        }
    }

    fun generateSuggestion(conversation: Conversation) {
        viewModelScope.launch {
            chatService.generateSuggestion(conversationId, conversation)
        }
    }

    fun generateInputDraft(
        conversation: Conversation,
        userInstruction: String = inputState.textContent.text.toString().trim(),
    ) {
        // #181: 编辑自己的消息时也允许「写回复草稿」，不再用 isEditing() 硬禁用。
        if (inputDraftJob?.isActive == true ||
            !hasInputDraftReplyTarget(
                latestMessageRole = conversation.currentMessages.lastOrNull()?.role,
                mainGenerationActive = conversationJob.value != null,
            )
        ) {
            return
        }
        val isEditingDraft = inputState.isEditing()
        val generation = ++inputDraftGeneration
        val originalText = inputState.textContent.text.toString()
        originalInputDraftText = originalText
        lastInputDraftText = ""
        inputState.setMessageText("")
        inputDraftJob = viewModelScope.launch {
            _inputDraftLoading.value = true
            try {
                val generatedDraft = chatService.generateInputDraft(
                    conversationId = conversationId,
                    conversation = conversation,
                    onStreamUpdate = streamUpdate@{ partial ->
                        if (generation != inputDraftGeneration) return@streamUpdate
                        val currentText = inputState.textContent.text.toString()
                        if (currentText != lastInputDraftText) {
                            // A user/ASR edit wins. Invalidate before cancelling so late chunks are ignored.
                            inputDraftGeneration++
                            inputDraftJob?.cancel()
                            inputDraftJob = null
                            _inputDraftLoading.value = false
                            originalInputDraftText = null
                            lastInputDraftText = null
                            return@streamUpdate
                        }
                        lastInputDraftText = partial
                        inputState.setMessageText(partial)
                    },
                    userInstruction = userInstruction,
                )
                val completedDraft = requireInputDraftText(
                    draft = generatedDraft,
                    emptyMessage = context.getString(R.string.input_draft_empty_response),
                )
                if (generation == inputDraftGeneration &&
                    inputState.textContent.text.toString() == lastInputDraftText
                ) {
                    lastInputDraftText = completedDraft
                    inputState.setMessageText(completedDraft)
                    // #181: 编辑态草稿生成成功后，通知 UI 用现有 Toaster 提示可继续修改。
                    // 用 tryEmit 配合带缓冲的 SharedFlow，避免无订阅者时 emit 挂起。
                    if (isEditingDraft) {
                        _inputDraftSuccessFlow.tryEmit(Unit)
                    }
                }
            } catch (error: CancellationException) {
                restoreInputDraftIfSafe(generation)
                throw error
            } catch (error: Throwable) {
                restoreInputDraftIfSafe(generation)
                chatService.addError(
                    error = error,
                    conversationId = conversationId,
                    title = context.getString(R.string.error_title_generate_input_draft),
                )
            } finally {
                if (generation == inputDraftGeneration) {
                    _inputDraftLoading.value = false
                    inputDraftJob = null
                    originalInputDraftText = null
                    lastInputDraftText = null
                }
            }
        }
    }

    fun cancelInputDraft() {
        val generation = inputDraftGeneration
        restoreInputDraftIfSafe(generation)
        inputDraftGeneration++
        inputDraftJob?.cancel()
        inputDraftJob = null
        _inputDraftLoading.value = false
        originalInputDraftText = null
        lastInputDraftText = null
    }

    /** Stops draft streaming while preserving the current text for send/edit actions. */
    fun finishInputDraft() {
        inputDraftGeneration++
        inputDraftJob?.cancel()
        inputDraftJob = null
        _inputDraftLoading.value = false
        originalInputDraftText = null
        lastInputDraftText = null
    }

    private fun restoreInputDraftIfSafe(generation: Long) {
        if (generation != inputDraftGeneration) return
        val streamedText = lastInputDraftText ?: return
        if (inputState.textContent.text.toString() == streamedText) {
            inputState.setMessageText(originalInputDraftText.orEmpty())
        }
    }
}
