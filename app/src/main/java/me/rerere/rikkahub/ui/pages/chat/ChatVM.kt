package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.withRecentChatModel
import me.rerere.rikkahub.data.ai.ContextPreview
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.HookRunHistory
import me.rerere.rikkahub.data.model.HookExecutionRecord
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import me.rerere.rikkahub.data.repository.MEMORY_TABLE_DELETED_BY_USER_UI
import me.rerere.rikkahub.data.repository.MemoryTableSoftDeleteResult
import me.rerere.rikkahub.data.repository.HookRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.hooks.MemoryTableHookPreview
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.components.ai.hasInputDraftReplyTarget
import me.rerere.rikkahub.ui.components.ai.requireInputDraftText
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

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

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val filesManager: FilesManager,
    private val favoriteRepository: FavoriteRepository,
    private val memoryTableRepository: MemoryTableRepository,
    hookRepository: HookRepository,
    conversationTagRepository: ConversationTagRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部
    private var contextPreviewJob: Job? = null
    private var inputDraftJob: Job? = null
    private var inputDraftGeneration = 0L
    private var originalInputDraftText: String? = null
    private var lastInputDraftText: String? = null
    private val _inputDraftLoading = MutableStateFlow(false)
    val inputDraftLoading = _inputDraftLoading.asStateFlow()
    val contextPreviewState = MutableStateFlow<UiState<ContextPreview>>(UiState.Idle)

    val hookHistoryState: StateFlow<UiState<List<HookRunHistory>>> = hookRepository
        .observeHistory(_conversationId)
        .map<List<HookRunHistory>, UiState<List<HookRunHistory>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val hookPreviewState = MutableStateFlow<UiState<MemoryTableHookPreview>>(UiState.Idle)
    val hookManualRunState = MutableStateFlow<UiState<HookExecutionRecord>>(UiState.Idle)

    val conversationTags: StateFlow<List<ConversationTag>> = conversationTagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        chatService
            .getProcessingStatusFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)

        // 初始化对话
        viewModelScope.launch {
            chatService.initializeConversation(_conversationId)
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    override fun onCleared() {
        contextPreviewJob?.cancel()
        inputDraftJob?.cancel()
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    fun loadContextPreview() {
        contextPreviewJob?.cancel()
        contextPreviewJob = viewModelScope.launch {
            contextPreviewState.value = UiState.Loading
            try {
                contextPreviewState.value = UiState.Success(chatService.buildContextPreview(_conversationId))
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

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    private val assistantSwitchCoordinator = AssistantSwitchCoordinator(
        currentAssistantId = { settingsStore.settingsFlow.value.assistantId },
        persistAssistant = ::persistSelectedAssistant,
        getLatestActiveConversationId = conversationRepo::getLatestActiveConversationIdOfAssistant,
        onError = { chatService.addError(it, _conversationId) },
    )

    // 网络搜索
    val enableWebSearch = combine(settings, conversation) { settings, conversation ->
        settings.assistants.firstOrNull { it.id == conversation.assistantId }?.enableWebSearch ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    // #89: 对话可见的记忆表模板（用于新建对话级文档时选择模板）
    @OptIn(ExperimentalCoroutinesApi::class)
    val memoryTableTemplates: StateFlow<List<MemoryTableTemplate>> = conversation
        .flatMapLatest { conv ->
            memoryTableRepository.getEffectiveTemplatesFlow(conv.assistantId.toString())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // #89: 当前对话生效的记忆表文档（CONVERSATION + 继承的 ASSISTANT/GLOBAL），
    // 随会话切换助手时自动跟随，供右侧抽屉查看与管理。
    @OptIn(ExperimentalCoroutinesApi::class)
    val memoryTableDocuments: StateFlow<List<MemoryTableDocument>> = conversation
        .flatMapLatest { conv ->
            memoryTableRepository.getEffectiveDocumentsFlow(
                assistantId = conv.assistantId.toString(),
                conversationId = conv.id.toString(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            val oldSettings = settings.value
            // 检查用户头像是否有变化，如果有则删除旧头像
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
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
    val updateState =
        updateChecker.checkUpdate().stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     */
    fun handleMessageSend(
        content: List<UIMessagePart>,
        answer: Boolean = true,
    ) {
        if (content.isEmptyInputMessage()) return

        chatService.sendMessage(_conversationId, content, answer)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        viewModelScope.launch {
            chatService.editMessage(_conversationId, messageId, parts)
        }
    }

    fun handleCompressContext(additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int): Job {
        return viewModelScope.launch {
            val result = chatService.compressConversation(
                conversationId = _conversationId,
                additionalPrompt = additionalPrompt,
                targetTokens = targetTokens,
                keepRecentMessages = keepRecentMessages,
            )
            handleManualCompressionResult(
                result = result,
                targetTokens = targetTokens,
                keepRecentMessages = keepRecentMessages,
                persistPreferences = settingsStore::updateCompressionPreferences,
                onCompressionFailure = {
                    chatService.addError(
                        it,
                        title = context.getString(R.string.error_title_compress_conversation),
                    )
                },
                onPreferencePersistenceFailure = {
                    Log.e(TAG, "Failed to persist compression preferences", it)
                },
            )
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        return chatService.forkConversationAtMessage(_conversationId, message.id)
    }

    fun deleteMessage(message: UIMessage) {
        viewModelScope.launch {
            chatService.deleteMessage(_conversationId, message)
        }
    }

    fun toggleMessageHidden(messageId: Uuid) = viewModelScope.launch {
        chatService.toggleMessageHidden(_conversationId, messageId)
    }

    fun showDeleteBlockedWhileGeneratingError() {
        chatService.addError(
            error = IllegalStateException("请先停止生成再删除消息"),
            conversationId = _conversationId,
            title = context.getString(R.string.error_title_operation)
        )
    }

    fun regenerateAtMessage(
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        chatService.regenerateAtMessage(_conversationId, message, regenerateAssistantMsg)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = ""
    ) {
        chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
    }

    fun stopGeneration() {
        viewModelScope.launch {
            chatService.stopGeneration(_conversationId)
        }
    }

    fun previewMemoryTableHook(hookId: Uuid) {
        viewModelScope.launch {
            hookPreviewState.value = UiState.Loading
            hookPreviewState.value = runCatching {
                chatService.previewMemoryTableHook(_conversationId, hookId)
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
                chatService.runMemoryTableHookNow(_conversationId, hookId)
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

    fun saveConversationAsync() {
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, conversation.value)
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            val updatedConversation = conversation.value.copy(title = title)
            chatService.saveConversation(_conversationId, updatedConversation)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(conversation)
        }
    }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.togglePinStatus(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            // #89: 下沉到 ChatService，内部改 assistantId+folderId 并重绑 followSource 的对话级记忆文档。
            chatService.moveConversationToAssistant(conversation.id, targetAssistantId)
            if (conversation.id == _conversationId) {
                settingsStore.updateAssistant(targetAssistantId)
            }
        }
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        chatService.translateMessage(_conversationId, message, targetLanguage)
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch
            chatService.generateTitle(_conversationId, conversationFull, force)
        }
    }

    fun generateSuggestion(conversation: Conversation) {
        viewModelScope.launch {
            chatService.generateSuggestion(_conversationId, conversation)
        }
    }

    fun generateInputDraft(conversation: Conversation) {
        if (inputDraftJob?.isActive == true ||
            inputState.isEditing() ||
            !hasInputDraftReplyTarget(
                latestMessageRole = conversation.currentMessages.lastOrNull()?.role,
                mainGenerationActive = conversationJob.value != null,
            )
        ) {
            return
        }
        val generation = ++inputDraftGeneration
        val originalText = inputState.textContent.text.toString()
        originalInputDraftText = originalText
        lastInputDraftText = ""
        inputState.setMessageText("")
        inputDraftJob = viewModelScope.launch {
            _inputDraftLoading.value = true
            try {
                val generatedDraft = chatService.generateInputDraft(
                    conversationId = _conversationId,
                    conversation = conversation,
                ) streamUpdate@{ partial ->
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
                }
                val completedDraft = requireInputDraftText(
                    draft = generatedDraft,
                    emptyMessage = context.getString(R.string.input_draft_empty_response),
                )
                if (generation == inputDraftGeneration &&
                    inputState.textContent.text.toString() == lastInputDraftText
                ) {
                    lastInputDraftText = completedDraft
                    inputState.setMessageText(completedDraft)
                }
            } catch (error: CancellationException) {
                restoreInputDraftIfSafe(generation)
                throw error
            } catch (error: Throwable) {
                restoreInputDraftIfSafe(generation)
                chatService.addError(
                    error = error,
                    conversationId = _conversationId,
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

    fun clearTranslationField(messageId: Uuid) {
        chatService.clearTranslationField(_conversationId, messageId)
    }

    fun updateConversation(newConversation: Conversation) {
        chatService.updateConversationState(_conversationId) {
            newConversation
        }
    }

    // #89: 切换对话级记忆表隔离开关。先无条件更新内存状态，保证开关立即响应
    // （空的新对话拨动也生效）；再尝试落库——非空对话直接持久化，空的新对话
    // 由首条消息发送时的 saveConversation 一并写入，避免重启后丢失。
    fun setMemoryTableIsolation(enabled: Boolean) {
        val updated = conversation.value.copy(memoryTableIsolation = enabled)
        chatService.updateConversationState(_conversationId) { updated }
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, updated)
        }
    }

    fun toggleMessageFavorite(node: MessageNode) {
        viewModelScope.launch {
            val currentlyFavorited = favoriteRepository.isNodeFavorited(_conversationId, node.id)
            if (currentlyFavorited) {
                favoriteRepository.removeNodeFavorite(_conversationId, node.id)
            } else {
                favoriteRepository.addNodeFavorite(
                    NodeFavoriteTarget(
                        conversationId = _conversationId,
                        conversationTitle = conversation.value.title,
                        nodeId = node.id,
                        node = node
                    )
                )
            }

            chatService.updateConversationState(_conversationId) { currentConversation ->
                currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.map { existingNode ->
                        if (existingNode.id == node.id) {
                            existingNode.copy(isFavorite = !currentlyFavorited)
                        } else {
                            existingNode
                        }
                    }
                )
            }
        }
    }

    // #89: 保存（新建/更新）一个对话级记忆表文档。scopeType 强制为 CONVERSATION，
    // scopeId 绑定到当前对话，从而让 conversation scope 真正可写、可查看。
    fun upsertConversationMemoryTableDocument(
        document: MemoryTableDocument,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                memoryTableRepository.upsertDocument(
                    document.copy(
                        scopeType = MemoryTableScopeType.CONVERSATION,
                        scopeId = _conversationId.toString(),
                    ),
                    actorAssistantId = conversation.value.assistantId.toString(),
                    actorConversationId = _conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

    // #89: 将助手级/全局的记忆表文档同步（复制）到当前对话级。
    // 若当前对话已存在同模板的对话级文档则覆盖其内容，否则新建，避免重复。
    fun syncMemoryTableDocumentToConversation(
        source: MemoryTableDocument,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val conversationScopeId = _conversationId.toString()
                val existing = memoryTableRepository
                    .getDocumentsForScope(MemoryTableScopeType.CONVERSATION, conversationScopeId)
                    .firstOrNull { it.templateId == source.templateId }
                val target = (existing ?: MemoryTableDocument(
                    templateId = source.templateId,
                    scopeType = MemoryTableScopeType.CONVERSATION,
                    scopeId = conversationScopeId,
                )).copy(
                    templateId = source.templateId,
                    scopeType = MemoryTableScopeType.CONVERSATION,
                    scopeId = conversationScopeId,
                    payloadJson = source.payloadJson,
                    // #89: 记录来源助手级文档，默认跟随其更新；用户可在抽屉里断开独立编辑。
                    sourceDocumentId = source.id,
                    followSource = true,
                )
                memoryTableRepository.upsertDocument(
                    target,
                    actorAssistantId = conversation.value.assistantId.toString(),
                    actorConversationId = _conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

    // #89: 设置对话级记忆文档是否跟随来源助手级文档。follow=false 即"断开独立编辑"，
    // 后续源文档更新不再覆盖该对话级文档。
    fun setMemoryTableDocumentFollow(
        documentId: String,
        follow: Boolean,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val doc = memoryTableRepository.getEffectiveDocument(
                    id = documentId,
                    assistantId = conversation.value.assistantId.toString(),
                    conversationId = _conversationId.toString(),
                )
                    ?: error("Memory table document not found: $documentId")
                memoryTableRepository.upsertDocument(
                    doc.copy(followSource = follow),
                    actorAssistantId = conversation.value.assistantId.toString(),
                    actorConversationId = _conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

    // #89: 删除一个记忆表文档（抽屉内针对对话级文档的清理）。
    fun deleteMemoryTableDocument(
        documentId: String,
        onDone: (Result<MemoryTableSoftDeleteResult>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                memoryTableRepository.softDeleteDocument(
                    id = documentId,
                    deletedBy = MEMORY_TABLE_DELETED_BY_USER_UI,
                    assistantId = conversation.value.assistantId.toString(),
                    conversationId = _conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

}
