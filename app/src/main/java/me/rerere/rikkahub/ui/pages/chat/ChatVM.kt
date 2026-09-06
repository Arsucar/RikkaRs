package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.withRecentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.CheckpointRecoveryHint
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.utils.OptimisticWriteCoordinator
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.runOptimisticWrite
import kotlin.uuid.Uuid

internal suspend fun handleManualCompressionResult(
    result: Result<Unit>,
    targetTokens: Int,
    keepRecentMessages: Int,
    persistPreferences: suspend (targetTokens: Int, keepRecentMessages: Int) -> Unit,
    onCompressionFailure: (Throwable) -> Unit,
    onPreferencePersistenceFailure: (Throwable) -> Unit,
) {
    val compressionFailure = result.exceptionOrNull()
    if (compressionFailure != null) {
        if (compressionFailure is CancellationException) {
            throw compressionFailure
        }
        onCompressionFailure(compressionFailure)
        return
    }

    try {
        persistPreferences(targetTokens, keepRecentMessages)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onPreferencePersistenceFailure(error)
    }
}

/**
 * Core chat ViewModel: conversation lifecycle, settings/model, list ops.
 * Satellite VMs share NavBackStackEntry parametersOf(conversationId) and ChatService flows:
 * [ChatMessageVM], [ChatGitVM], [ChatHookVM], [ChatMemoryTableVM], [ChatDraftVM], [ChatContextVM].
 */
class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val filesManager: FilesManager,
    conversationTagRepository: ConversationTagRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    /** #220: one-shot recovery toast after process death mid-generation (this conversation only). */
    private val _checkpointRecoveryHint = MutableStateFlow<CheckpointRecoveryHint?>(null)
    val checkpointRecoveryHint: StateFlow<CheckpointRecoveryHint?> = _checkpointRecoveryHint.asStateFlow()

    fun consumeCheckpointRecoveryHintUi() {
        _checkpointRecoveryHint.value = null
    }

    val conversationTags: StateFlow<List<ConversationTag>> = conversationTagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceSession = VoiceSessionController(viewModelScope, context::getString) {
        chatService.enqueueVoiceMessage(_conversationId, it)
    }

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val processingStatus: StateFlow<String?> =
        chatService
            .getProcessingStatusFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)

        // 初始化对话
        viewModelScope.launch {
            chatService.initializeConversation(_conversationId)
            // #220: surface recovery hint after hydrate (StateFlow so UI cannot miss it)
            chatService.consumeCheckpointRecoveryHint(_conversationId)?.let { hint ->
                _checkpointRecoveryHint.value = hint
            }
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    override fun onCleared() {
        voiceSession.stop()
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings.dummy())

    // VM-scoped business object (DI Rule 5, see .trellis/spec/app/dependency-injection.md):
    // holds per-conversation switch generation + mutex + settled assistant id, so two ChatVMs
    // must never share an instance. Not registered in Koin on purpose.
    private val assistantSwitchCoordinator = AssistantSwitchCoordinator(
        currentAssistantId = { settingsStore.settingsFlow.value.assistantId },
        persistAssistant = ::persistSelectedAssistant,
        getLatestActiveConversationId = conversationRepo::getLatestActiveConversationIdOfAssistant,
        onError = { chatService.addError(it, _conversationId) },
    )

    // 网络搜索：绑定当前会话助手，而非全局 selected assistant（fork + assistant-web-search 契约）
    val enableWebSearch = combine(settings, conversation) { settings, conversation ->
        settings.assistants.firstOrNull { it.id == conversation.assistantId }?.enableWebSearch ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleWebSearch() {
        val assistantId = conversation.value.assistantId
        viewModelScope.launch {
            settingsStore.updateAssistantWebSearch(assistantId, !enableWebSearch.value)
        }
    }

    // 当前模型
    val currentChatModel = combine(settings, conversation) { settings, conversation ->
        settings.getCurrentChatModel(conversation)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    val messageQueue = chatService.getMessageQueueFlow(_conversationId)

    fun removeQueuedMessage(id: Uuid) = chatService.removeQueuedMessage(_conversationId, id)

    fun beginEditQueuedMessage(id: Uuid) = chatService.beginEditQueuedMessage(_conversationId, id)

    fun finishEditQueuedMessage(id: Uuid, parts: List<UIMessagePart>?) =
        chatService.finishEditQueuedMessage(_conversationId, id, parts)

    fun resumeMessageQueue() = chatService.resumeMessageQueue(_conversationId)

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置 (transform 原子写, #267)
    fun updateSettings(transform: (Settings) -> Settings): Job {
        return viewModelScope.launch {
            // Capture old/new under the store's atomic transform to avoid
            // snapshot-read-then-full-write races; file cleanup runs after the
            // write commits (outside the store mutex) to avoid holding the lock
            // during IO.
            var oldSettings: Settings? = null
            var newSettings: Settings? = null
            settingsStore.update {
                oldSettings = it
                transform(it).also { newSettings = it }
            }
            val old = oldSettings
            val new = newSettings
            if (old != null && new != null) {
                checkUserAvatarDelete(old, new)
            }
        }
    }

    fun switchAssistant(
        targetAssistantId: Uuid,
        navigate: (Uuid) -> Unit,
    ) {
        val request = assistantSwitchCoordinator.requestSwitch(targetAssistantId)
        viewModelScope.launch {
            assistantSwitchCoordinator.executeSwitch(request, navigate)
        }
    }

    private suspend fun persistSelectedAssistant(assistantId: Uuid) {
        settingsStore.updateAssistant(assistantId)
        settingsStore.settingsFlow.first { it.assistantId == assistantId }
    }

    // 检查用户头像删除
    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    // 设置聊天模型
    fun setChatModel(model: Model) {
        viewModelScope.launch {
            val modelId = model.id.takeIf { settings.value.findModelById(it) != null }
            chatService.saveConversation(_conversationId, conversation.value.copy(chatModelId = modelId))
            if (modelId != null) {
                settingsStore.update { it.withRecentChatModel(modelId) }
            }
        }
    }

    // Update checker
    val updateState = settingsStore.settingsFlow
        .map { settings ->
            !settings.init &&
                settings.displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()
        }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) updateChecker.updateState else flowOf(UiState.Loading)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            UiState.Loading,
        )

    // #295: per-key coordinators so rapid pin/title clicks do not lose updates.
    private val titleWriteCoordinator = OptimisticWriteCoordinator()
    private val pinWriteCoordinators = mutableMapOf<Uuid, OptimisticWriteCoordinator>()

    private fun pinCoordinatorFor(conversationId: Uuid): OptimisticWriteCoordinator =
        pinWriteCoordinators.getOrPut(conversationId) { OptimisticWriteCoordinator() }

    private fun reportWriteFailure(error: Throwable, conversationId: Uuid = _conversationId) {
        chatService.addError(
            error = error,
            conversationId = conversationId,
            title = context.getString(R.string.error_title_operation),
        )
    }

    fun saveConversationAsync() {
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, conversation.value)
        }
    }

    /**
     * #295: optimistic title update — UI flips via [ChatService.updateConversationState] before
     * [ChatService.saveConversation]; failure restores prior title and surfaces [errors].
     */
    fun updateTitle(title: String) {
        viewModelScope.launch {
            val previousTitle = conversation.value.title
            titleWriteCoordinator.run(
                applyOptimistic = {
                    chatService.updateConversationState(_conversationId) { current ->
                        conversationWithTitle(current, title)
                    }
                    conversationWithTitle(conversation.value, title)
                },
                persist = { snapshot ->
                    chatService.saveConversation(_conversationId, snapshot)
                },
                rollback = {
                    chatService.updateConversationState(_conversationId) { current ->
                        conversationWithTitle(current, previousTitle)
                    }
                },
                onError = { reportWriteFailure(it) },
            )
        }
    }

    fun deleteConversation(conversation: Conversation): Job =
        viewModelScope.launch {
            // Deletion removes the row from the drawer paging source; no in-session conversation
            // overlay is needed. Keep stop-then-delete ordering so generation cannot resurrect it.
            runOptimisticWrite(
                applyOptimistic = { conversation },
                persist = { target ->
                    chatService.stopGeneration(target.id)
                    conversationRepo.deleteConversation(target)
                },
                rollback = {
                    // Room paging will not re-insert without a full reload; surface error only.
                    // Re-inserting a deleted tree is intentionally out of scope for #295 MVP.
                },
                onError = { reportWriteFailure(it, conversation.id) },
            )
        }

    /**
     * #295: optimistic pin toggle. Updates session state immediately, then writes the pin column.
     * Rapid toggles are generation-gated per conversation id via [pinCoordinatorFor].
     */
    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            val targetId = conversation.id
            val previousPinned = if (targetId == _conversationId) {
                this@ChatVM.conversation.value.isPinned
            } else {
                conversation.isPinned
            }
            val nextPinned = !previousPinned
            pinCoordinatorFor(targetId).run(
                applyOptimistic = {
                    chatService.updateConversationState(targetId) { current ->
                        conversationWithPinned(current, nextPinned)
                    }
                    nextPinned
                },
                persist = { pinned ->
                    conversationRepo.setPinStatus(targetId, pinned)
                },
                rollback = {
                    chatService.updateConversationState(targetId) { current ->
                        conversationWithPinned(current, previousPinned)
                    }
                },
                onError = { reportWriteFailure(it, targetId) },
            )
        }
    }

    /**
     * #295: optimistic move — session state flips assistantId/folderId before durable write.
     * Settings assistant switch still runs only after a successful move of the open conversation.
     */
    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            val targetId = conversation.id
            val previousAssistantId = if (targetId == _conversationId) {
                this@ChatVM.conversation.value.assistantId
            } else {
                conversation.assistantId
            }
            val previousFolderId = if (targetId == _conversationId) {
                this@ChatVM.conversation.value.folderId
            } else {
                conversation.folderId
            }
            runOptimisticWrite(
                applyOptimistic = {
                    chatService.updateConversationState(targetId) { current ->
                        current.copy(assistantId = targetAssistantId, folderId = null)
                    }
                    targetAssistantId
                },
                persist = {
                    // #89: ChatService also relinks followSource memory docs after durable move.
                    chatService.moveConversationToAssistant(targetId, targetAssistantId)
                    if (targetId == _conversationId) {
                        // Settings update is a secondary effect; if it fails after a successful
                        // move, the conversation is already durably moved. Rolling back session
                        // state would create a session/DB mismatch, so surface the error without
                        // triggering rollback.
                        try {
                            settingsStore.updateAssistant(targetAssistantId)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            reportWriteFailure(error, targetId)
                        }
                    }
                },
                rollback = {
                    chatService.updateConversationState(targetId) { current ->
                        current.copy(assistantId = previousAssistantId, folderId = previousFolderId)
                    }
                },
                onError = { reportWriteFailure(it, targetId) },
            )
        }
    }

    fun updateConversation(newConversation: Conversation) {
        chatService.updateConversationState(_conversationId) {
            newConversation
        }
    }
}
