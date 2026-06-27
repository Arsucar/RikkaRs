package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.currentToolCallId
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.subagent.SubagentHost
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentResult
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.data.ai.subagent.createManageSubagentTool
import me.rerere.rikkahub.data.ai.subagent.createSubagentTools
import me.rerere.rikkahub.data.ai.subagent.mergeSubagentProfiles
import me.rerere.rikkahub.data.ai.subagent.removeSubagentProfile
import me.rerere.rikkahub.data.ai.subagent.upsertSubagentProfile
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceReadOnlyTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.workspace.WorkspaceShellStatus
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

internal fun backgroundTextGenerationParams(
    model: Model,
    reasoningLevel: ReasoningLevel = ReasoningLevel.OFF,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckTitleModelSettings,
}

private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    private val subagentHost: SubagentHost,
    private val json: Json,
) {
    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> _isForeground.value = true
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init {
        // 添加生命周期观察者
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
    }

    // ---- Session 管理 ----

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = appScope,
                onIdle = { removeSession(it) }
            ).also {
                _sessionsVersion.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            _sessionsVersion.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = appScope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        val session = sessions[conversationId] ?: return MutableStateFlow(null)
        return session.processingStatus
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        getOrCreateSession(conversationId) // 确保 session 存在
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
                newConversation = true
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // ---- 发送消息 ----

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        val session = getOrCreateSession(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                Logging.log("ChatService", "Generation done, emitting for conversation $conversationId")
                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e  // 不要吞掉取消异常 — 用户主动打断时不应显示错误
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value
                val newApprovalState = when {
                    answer != null -> ToolApprovalState.Answered(answer)
                    approved -> ToolApprovalState.Approved
                    else -> ToolApprovalState.Denied(reason)
                }

                // Update the tool approval state
                val updatedNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { msg ->
                            msg.copy(
                                parts = msg.parts.map { part ->
                                    when {
                                        part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                            part.copy(approvalState = newApprovalState)
                                        }

                                        else -> part
                                    }
                                }
                            )
                        }
                    )
                }
                val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                saveConversation(conversationId, updatedConversation)

                // Check if there are still pending tools
                val hasPendingTools = updatedNodes.any { node ->
                    node.currentMessage.parts.any { part ->
                        part is UIMessagePart.Tool && part.isPending
                    }
                }

                // Only continue generation when all pending tools are handled
                if (!hasPendingTools) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (settings.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            // start generating
            val session = getOrCreateSession(conversationId)
            val subagentTools = if (assistant.enableSubagents) {
                buildSubagentTools(
                    assistant,
                    settings,
                    conversation.workspaceCwd,
                    depth = 0,
                    assistant.subagentMaxDepth,
                    includeBase = false,
                    conversationId = conversationId,
                )
            } else {
                emptyList()
            }
            generationHandler.generateText(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                },
                assistant = assistant,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = conversation.workspaceCwd,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(workspaceReminderTransformer)
                },
                outputTransformers = outputTransformers,
                tools = buildList {
                    val delegateOnly = assistant.enableSubagents && assistant.subagentDelegateOnly
                    if (delegateOnly) {
                        // 纯决策模式：主代理保留「只读」能力以便快速查看上下文，
                        // 但不持有写入/编辑能力——实际执行（写文件、跑脚本等）交由子代理。
                        // 搜索 / workspace_read_file / workspace_shell(cat,ls,grep) / time_info / clipboard
                        if (settings.enableWebSearch) {
                            addAll(createSearchTools(settings))
                        }
                        addAll(localTools.getTools(assistant.localTools.filter {
                            it == LocalToolOption.AskUser || it == LocalToolOption.TimeInfo || it == LocalToolOption.Clipboard || it == LocalToolOption.Logs
                        }))
                        addAll(createWorkspaceToolsIfReady(
                            assistant.workspaceId?.toString(),
                            conversation.workspaceCwd,
                            readOnly = true,
                        ))
                    } else {
                        when (val result = buildCommonTools(assistant, settings, conversation.workspaceCwd, McpErrorStrategy.STRICT)) {
                            is CommonToolsResult.Ok -> addAll(result.tools)
                            is CommonToolsResult.McpError -> {
                                addError(
                                    error = IllegalStateException(
                                        context.getString(
                                            R.string.error_mcp_invalid_server_name,
                                            result.invalidNames.joinToString(", ")
                                        )
                                    ),
                                    conversationId = conversationId,
                                )
                                return
                            }
                        }
                    }
                    // subagent 委派工具（移植自 kimi-code subagent 体系）
                    addAll(subagentTools)
                },
            ).onCompletion {
                // 取消 Live Update 通知
                cancelLiveUpdateNotification(conversationId)

                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                        node.copy(messages = node.messages.map { it.finishReasoning() })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId, senderName)
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

                        // 如果应用不在前台，发送 Live Update 通知
                        if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration && settings.displaySetting.enableLiveUpdateNotification) {
                            sendLiveUpdateNotification(conversationId, chunk.messages, senderName)
                        }
                    }
                }
            }
        }.onFailure {
            // 取消 Live Update 通知
            cancelLiveUpdateNotification(conversationId)

            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            runCatching {
                val finalConversation = getConversationFlow(conversationId).value
                saveConversation(conversationId, finalConversation)

                launchWithConversationReference(conversationId) {
                    generateTitle(conversationId, finalConversation)
                }
                launchWithConversationReference(conversationId) {
                    generateSuggestion(conversationId, finalConversation)
                }
            }.onFailure { e ->
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
    }

    /**
     * MCP 名非法时的处理策略。
     */
    private enum class McpErrorStrategy {
        /** 主代理：检测到非法名时返回 [CommonToolsResult.McpError]，由调用方上报错误 */
        STRICT,
        /** 子代理：静默过滤非法名，不抛错、不打断，并按 [Assistant.mcpServers] 过滤 */
        SOFT,
    }

    /**
     * [buildCommonTools] 的返回类型。
     */
    private sealed class CommonToolsResult {
        data class Ok(val tools: List<Tool>) : CommonToolsResult()
        data class McpError(val invalidNames: List<String>) : CommonToolsResult()
    }

    /**
     * 构建主代理/子代理共享的基础工具集（搜索 / 本地 / workspace / skills / mcp）。
     *
     * 提取自根代理 buildList 的非 delegateOnly 分支和 buildSubagentBaseTools，
     * 消除两处几乎完全相同的工具装配代码。差异仅在 MCP 非法名称的处理方式：
     * - [McpErrorStrategy.STRICT]：检测到非法名时返回 [CommonToolsResult.McpError]，
     *   由调用方决定如何上报错误。
     * - [McpErrorStrategy.SOFT]：静默过滤非法名，并额外按 [Assistant.mcpServers]
     *   过滤（子代理场景）。
     */
    private suspend fun buildCommonTools(
        assistant: Assistant,
        settings: Settings,
        workspaceCwd: String?,
        mcpStrategy: McpErrorStrategy,
    ): CommonToolsResult {
        val allMcpTools = mcpManager.getAllAvailableTools()

        if (mcpStrategy == McpErrorStrategy.STRICT) {
            val invalidNames = allMcpTools
                .map { it.second }
                .distinct()
                .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
            if (invalidNames.isNotEmpty()) {
                return CommonToolsResult.McpError(invalidNames)
            }
        }

        val tools = buildList {
            if (settings.enableWebSearch) {
                addAll(createSearchTools(settings))
            }
            addAll(localTools.getTools(assistant.localTools))
            addAll(createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), workspaceCwd))

            val allInstalledSkills = skillManager.listSkills()
            if (allInstalledSkills.isNotEmpty()) {
                val installedSkillNames = allInstalledSkills.map { it.name }.toSet()
                val cleanedSkills = cleanStaleEnabledSkills(assistant, installedSkillNames)
                autoEnableNewSkills(assistant, cleanedSkills, installedSkillNames)
                addAll(
                    createSkillTools(
                        enabledSkills = installedSkillNames,
                        allSkills = allInstalledSkills,
                        skillManager = skillManager,
                        workspaceRepository = workspaceRepository,
                        workspaceId = assistant.workspaceId?.toString(),
                    )
                )
            }

            val filteredMcpTools = if (mcpStrategy == McpErrorStrategy.SOFT) {
                allMcpTools
                    .filter { (_, serverName, _) ->
                        serverName.isNotEmpty() && serverName.all {
                            it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9'
                        }
                    }
                    .filter { (serverId, _, _) ->
                        // 按子代理配置的 MCP 服务器过滤；空集 = 不限制（继承父代理的场景）
                        assistant.mcpServers.isEmpty() || serverId in assistant.mcpServers
                    }
            } else {
                allMcpTools
            }

            filteredMcpTools.forEach { (serverId, serverName, tool) ->
                add(
                    Tool(
                        name = "mcp__${serverName}__${tool.name}",
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        needsApproval = { tool.needsApproval },
                        execute = {
                            mcpManager.callTool(serverId, tool.name, it.jsonObject)
                        },
                    )
                )
            }
        }

        return CommonToolsResult.Ok(tools)
    }

    /**
     * 清理 [assistant] 的 [Assistant.enabledSkills] 中已不存在的技能名（幽灵技能）。
     *
     * 与 quickMessageIds / modeInjectionIds / lorebookIds / mcpServers 不同，
     * enabledSkills 在迁移阶段没有被过滤（因为 SkillManager 在 app 层，
     * PreferencesStore 无法访问磁盘上的技能列表）。此方法在每次构建工具前
     * 检查并异步回写清理后的值，确保持久化数据与磁盘一致。
     *
     * @return 清理后的 enabledSkills（如果无变化则返回原值）
     */
    private fun cleanStaleEnabledSkills(
        assistant: Assistant,
        installedSkillNames: Set<String>,
    ): Set<String> {
        if (assistant.enabledSkills.isEmpty()) return assistant.enabledSkills
        val cleaned = assistant.enabledSkills.filter { it in installedSkillNames }.toSet()
        if (cleaned.size == assistant.enabledSkills.size) return assistant.enabledSkills
        // 异步回写清理后的值
        appScope.launch {
            settingsStore.updateAssistantSkills(assistant.id) { cleaned }
        }
        Logging.log(TAG, "cleanStaleEnabledSkills: removed ${assistant.enabledSkills.size - cleaned.size} ghost skill(s) from assistant ${assistant.id}: ${assistant.enabledSkills - cleaned}")
        return cleaned
    }

    /**
     * 自动将磁盘上新安装的技能加入 [Assistant.enabledSkills]。
     *
     * 与 [cleanStaleEnabledSkills] 对称：后者清理已删除的幽灵技能，
     * 本方法发现已安装但未启用的技能并异步回写，使新技能在下一轮消息即可用，
     * 无需用户手动到 UI 中开启。这解决了通过 workspace_shell 直接写入
     * /skills/ 目录的技能无法被发现的问题——autoEnableSkill() 仅在
     * UI 层导入时触发，文件系统直写不走那条路径。
     *
     * @param enabledSkills 当前已启用的技能名集合（已清理幽灵）
     * @param installedSkillNames 磁盘上实际安装的技能名集合
     */
    private fun autoEnableNewSkills(
        assistant: Assistant,
        enabledSkills: Set<String>,
        installedSkillNames: Set<String>,
    ) {
        val newSkills = installedSkillNames - enabledSkills
        if (newSkills.isEmpty()) return
        appScope.launch {
            settingsStore.updateAssistantSkills(assistant.id) { it + newSkills }
        }
        Logging.log(TAG, "autoEnableNewSkills: enabled ${newSkills.size} new skill(s) for assistant ${assistant.id}: $newSkills")
    }

    private suspend fun createWorkspaceToolsIfReady(
        workspaceId: String?,
        cwd: String? = null,
        readOnly: Boolean = false,
    ): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return if (readOnly) {
            createWorkspaceReadOnlyTools(workspaceId, workspaceRepository, cwd)
        } else {
            createWorkspaceTools(workspaceId, workspaceRepository, cwd)
        }
    }

    /**
     * 构建某一层 (子)代理可用的 subagent 相关工具集 —— 移植自 kimi-code 的 subagent 工具继承 / 裁剪逻辑。
     *
     * - 当 [includeBase] = true（子代理场景）：复用父代理同款基础工具（搜索 / 本地 / workspace /
     *   skills / mcp，MCP 名非法项软过滤），并通过 [SubagentHost.sandboxToolsForSubagent] 关闭审批，
     *   使子代理可自主运行。
     * - 当 [includeBase] = false（根代理场景）：根代理的基础工具已在外层 buildList 装配，这里只追加
     *   spawn_subagent / ask_btw 两个委派工具，避免重复。
     * - 当 [depth] + 1 < [maxDepth] 时，递归注入 spawn_subagent / ask_btw，允许子代理再次委派
     *   （对应 kimi-code 的嵌套 subagent）。
     *
     * @param assistant    本层 (子)代理的 Assistant 配置
     * @param settings     当前设置
     * @param workspaceCwd workspace 当前工作目录（继承自根对话）
     * @param depth        当前递归深度（根代理为 0）
     * @param maxDepth     允许的最大嵌套深度
     * @param includeBase  是否包含基础工具（子代理 true / 根代理 false）
     *
     * 深度语义：[depth] 表示当前这层代理所在的深度（根代理为 0）。子代理在 spawn 时位于
     * `depth + 1`，其工具由 [buildChildTools] 以子代理自身的深度构建 —— 不再额外 +1
     * （此前这里多加了一次 +1，导致 maxDepth 与实际嵌套层数对应关系错乱）。
     * 因此实际可嵌套层数 = maxDepth - 1（maxDepth=2 → 1 层子代理）。
     */
    private suspend fun buildSubagentTools(
        assistant: Assistant,
        settings: Settings,
        workspaceCwd: String?,
        depth: Int,
        maxDepth: Int,
        includeBase: Boolean,
        @Suppress("UNUSED_PARAMETER") conversationId: Uuid? = null,
    ): List<Tool> {
        val profiles = mergeSubagentProfiles(assistant.subagentProfiles, assistant.disabledBuiltinSubagents)

        val result = mutableListOf<Tool>()

        // 1) 基础工具（仅子代理需要；根代理已在外层装配）
        if (includeBase) {
            result += SubagentHost.sandboxToolsForSubagent(
                buildSubagentBaseTools(assistant, settings, workspaceCwd)
            )
        }

        // 2) 递归注入 spawn_subagent / ask_btw（受 maxDepth 约束 + 需有可用 profile）
        if (depth + 1 < maxDepth && profiles.isNotEmpty()) {
            result += createSubagentTools(
                profiles = profiles,
                json = json,
                delegateOnly = depth == 0 && assistant.enableSubagents && assistant.subagentDelegateOnly,
                includeAskBtw = assistant.localTools.contains(LocalToolOption.AskBtw),
                spawn = { profileName, task, _ ->
                    val profile = profiles.firstOrNull { it.name == profileName }
                    if (profile == null) {
                        SubagentResult(
                            profileName = profileName,
                            summary = "",
                            succeeded = false,
                            error = "Subagent profile not found: $profileName",
                            depth = depth + 1,
                        )
                    } else {
                        val parentModel = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                            ?: error("Model not found for subagent parent")
                        // 读取当前 toolCallId（由 GenerationHandler.executeSingleTool 经协程上下文注入），
                        // 供并行多子代理时把流式进度精确写到各自的 Tool part，避免互相覆盖。
                        val toolCallId = currentToolCallId()
                        subagentHost.spawn(
                            profile = profile,
                            task = task,
                            settings = settings,
                            parentAssistant = assistant,
                            parentModel = parentModel,
                            buildChildTools = { child, d ->
                                // d 是子代理自身的深度（spawn 已在 depth+1 处运行），
                                // 这里直接用 d 构建子代理工具，不再 +1。
                                buildSubagentTools(
                                    child,
                                    settings,
                                    workspaceCwd,
                                    d,
                                    maxDepth,
                                    includeBase = true,
                                )
                            },
                            depth = depth + 1,
                            maxDepth = maxDepth,
                            onProgress = if (conversationId != null) { subMessages ->
                                // 实时流式更新：把子代理当前的消息构建为部分 transcript，
                                // 按 toolCallId 精确更新到对话中对应的 spawn_subagent 工具 output，
                                // 使 UI 上的 SubagentToolUI 能实时展示子代理的思维链 / 工具调用。
                                updateSubagentProgress(conversationId, toolCallId, profileName, subMessages)
                            } else null,
                        )
                    }
                },
                askBtw = { question ->
                    // 轻量"顺便问一句"：无工具、单轮、继承父系统提示词（对应 kimi-code startBtw）
                    val btwProfile = SubagentProfile(
                        name = "btw",
                        systemPrompt = assistant.systemPrompt,
                        inheritTools = false,
                        maxSteps = 1,
                        summaryMinLength = 0,
                        summaryContinuationAttempts = 0,
                    )
                    val parentModel = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                        ?: return@createSubagentTools "(side agent failed: model not found)"
                    val r = subagentHost.spawn(
                        profile = btwProfile,
                        task = question,
                        settings = settings,
                        parentAssistant = assistant,
                        parentModel = parentModel,
                        buildChildTools = { _, _ -> emptyList() },
                        depth = depth + 1,
                        maxDepth = maxDepth,
                    )
                    if (r.succeeded) r.summary else "(side agent failed: ${r.error})"
                },
            )
        }

        // 3) manage_subagent_profile：根代理（depth==0）**无条件**注册——即使当前没有任何
        // 子代理 profile（profiles 为空，如用户删光了内置子代理）也注册，这样模型才能从零
        // 创建子代理。此前 manage 被夹在 profiles.isEmpty() 早返回和 depth+1<maxDepth 条件里，
        // 导致 profiles 为空时 manage 未注册，模型调用即报 "Tool not found"。
        if (depth == 0) {
            result += createManageSubagentTool(
                profiles = profiles,
                json = json,
                manage = { action, name, profile ->
                    manageSubagentProfile(assistant.id, action, name, profile)
                },
            )
        }

        return result
    }

    /**
     * 子代理的基础工具集（搜索 / 本地 / workspace / skills / mcp）。
     *
     * 委托给 [buildCommonTools]，使用 [McpErrorStrategy.SOFT]：MCP 名非法项
     * 做软过滤（子代理不抛错、不打断），并按 [Assistant.mcpServers] 过滤。
     */
    private suspend fun buildSubagentBaseTools(
        assistant: Assistant,
        settings: Settings,
        workspaceCwd: String?,
    ): List<Tool> = when (val result = buildCommonTools(assistant, settings, workspaceCwd, McpErrorStrategy.SOFT)) {
        is CommonToolsResult.Ok -> result.tools
        is CommonToolsResult.McpError -> emptyList() // SOFT 策略不会返回 Error
    }

    // ---- 检查无效消息 ----

    /**
     * 主代理通过 manage_subagent_profile 工具增删改子代理配置的实际处理。
     * 修改持久化到 [settingsStore] 中对应 assistant，并立即生效。
     *
     * - list：返回当前全部可用子代理 profile 概览。
     * - create/update：upsert [profile] 到 assistant.subagentProfiles（内置同名会被覆盖）。
     * - delete：内置 profile → 加入 disabledBuiltinSubagents（完全禁用）；自定义 → 从列表移除。
     *
     * @param assistantId 当前对话所用 assistant 的 id
     */
    private suspend fun manageSubagentProfile(
        assistantId: Uuid,
        action: String,
        name: String,
        profile: SubagentProfile?,
    ): String {
        val current = settingsStore.settingsFlow.first()
        val target = current.assistants.firstOrNull { it.id == assistantId }
            ?: return "Error: assistant not found"
        val merged = mergeSubagentProfiles(target.subagentProfiles, target.disabledBuiltinSubagents)
        return when (action) {
            "list" -> {
                if (merged.isEmpty()) {
                    "No subagent profiles available."
                } else {
                    "Available subagent profiles (${merged.size}):\n" + merged.joinToString("\n") { p ->
                        val tools = if (p.inheritTools) "inherit all" else {
                            listOfNotNull(
                                p.localTools.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toToolName() },
                                p.enabledSkills.takeIf { it.isNotEmpty() }?.let { "skills:${it.size}" },
                                p.mcpServerIds.takeIf { it.isNotEmpty() }?.let { "mcp:${it.size}" },
                            ).joinToString(" | ").ifBlank { "no tools" }
                        }
                        "- ${p.name}: ${p.description.ifBlank { "(no description)" }} [tools: $tools]"
                    }
                }
            }
            "create", "update" -> {
                val p = profile ?: return "Error: profile data missing"
                settingsStore.update { settings ->
                    settings.copy(
                        assistants = settings.assistants.map { a ->
                            if (a.id == assistantId) {
                                a.copy(subagentProfiles = upsertSubagentProfile(a.subagentProfiles, p))
                            } else a
                        }
                    )
                }
                "$action: subagent profile '${p.name}' saved. It is now available for spawn_subagent."
            }
            "delete" -> {
                if (name.isBlank()) return "Error: name required for delete"
                val isBuiltin = SubagentProfile.BUILTIN.any { it.name == name }
                settingsStore.update { settings ->
                    settings.copy(
                        assistants = settings.assistants.map { a ->
                            if (a.id == assistantId) {
                                a.copy(
                                    subagentProfiles = removeSubagentProfile(a.subagentProfiles, name),
                                    disabledBuiltinSubagents = if (isBuiltin) {
                                        a.disabledBuiltinSubagents + name
                                    } else a.disabledBuiltinSubagents,
                                )
                            } else a
                        }
                    )
                }
                "delete: subagent profile '$name' removed." +
                    if (isBuiltin) " (built-in profile disabled)" else ""
            }
            else -> "Error: unknown action '$action'"
        }
    }

    /** LocalToolOption -> 工具参数用的字符串名（与 toLocalToolOption 对应）。 */
    private fun LocalToolOption.toToolName(): String = when (this) {
        is LocalToolOption.JavascriptEngine -> "javascript_engine"
        is LocalToolOption.TimeInfo -> "time_info"
        is LocalToolOption.Clipboard -> "clipboard"
        is LocalToolOption.Tts -> "tts"
        is LocalToolOption.AskUser -> "ask_user"
        is LocalToolOption.AskBtw -> "ask_btw"
        is LocalToolOption.Fetch -> "fetch"
        is LocalToolOption.Logs -> "logs"
    }

    /**
     * 实时更新对话中 spawn_subagent 工具的 output，使 UI 能流式展示子代理的进度。
     *
     * 子代理运行在工具执行期间（父代理 generateText 流被挂起），此方法直接更新对话状态，
     * 把子代理当前的部分 transcript 写入对应工具的 output metadata。父代理流恢复后会用
     * 最终结果覆盖，因此不会产生冲突。
     *
     * @param conversationId 对话 ID
     * @param profileName    子代理配置档名（用于 UI 标题）
     * @param subMessages    子代理当前的消息列表
     */
    private fun updateSubagentProgress(
        conversationId: Uuid,
        toolCallId: String?,
        profileName: String,
        subMessages: List<UIMessage>,
    ) {
        runCatching {
            // 流式 partial transcript：截断工具输出（UI 本就只展示前 2000 字符），
            // 避免逐 tick 序列化完整搜索结果等大体积输出，把每 tick 序列化体积降到 ~KB 级。
            val transcript = SubagentHost.buildTranscript(subMessages, truncateToolOutput = 2000)
            if (transcript.isEmpty()) return@runCatching

            val listSerializer = kotlinx.serialization.builtins.ListSerializer(
                SubagentTranscriptStep.serializer()
            )
            val transcriptMetadata = buildJsonObject {
                put("subagent_transcript", json.encodeToJsonElement(listSerializer, transcript))
                put("subagent_profile", JsonPrimitive(profileName))
                put("subagent_steps", JsonPrimitive(transcript.size))
                put("subagent_succeeded", JsonPrimitive(false))
                put("subagent_streaming", JsonPrimitive(true))
            }
            val partialOutput = UIMessagePart.Text(
                text = "{\"profile_name\":\"$profileName\",\"succeeded\":false,\"streaming\":true}",
                metadata = transcriptMetadata,
            )

            updateConversationState(conversationId) { conversation ->
                val messages = conversation.currentMessages
                val lastAssistantIndex = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                if (lastAssistantIndex < 0) return@updateConversationState conversation

                val updatedMessages = messages.mapIndexed { index, message ->
                    if (index != lastAssistantIndex) return@mapIndexed message
                    // 精确匹配目标工具：
                    // - 优先按 toolCallId（并行多子代理时各自隔离，互不覆盖）；
                    // - toolCallId 为 null 时回退到任意未完成/流式中的 spawn_subagent。
                    val matchesTool: (UIMessagePart.Tool) -> Boolean = { part ->
                        part.toolName == "spawn_subagent" &&
                            (!part.isExecuted || isStreamingSubagent(part)) &&
                            (toolCallId == null || part.toolCallId == toolCallId)
                    }
                    if (!message.parts.any { it is UIMessagePart.Tool && matchesTool(it) }) {
                        return@mapIndexed message
                    }

                    message.copy(parts = message.parts.map { part ->
                        if (part is UIMessagePart.Tool && matchesTool(part)) {
                            part.copy(output = listOf(partialOutput))
                        } else {
                            part
                        }
                    })
                }
                conversation.updateCurrentMessages(updatedMessages)
            }
        }.onFailure {
            Log.w(TAG, "updateSubagentProgress failed: ${it.message}")
        }
    }

    /** 检查 spawn_subagent 工具是否处于流式更新中（output 中已有 subagent_streaming 标记）。 */
    private fun isStreamingSubagent(part: UIMessagePart.Tool): Boolean {
        val textPart = part.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
        return textPart?.metadata?.get("subagent_streaming")?.jsonPrimitive?.contentOrNull == "true"
    }

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            ),
            approvalState = ToolApprovalState.Denied("Generation cancelled by user")
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.titleModelId, fallback = settings.fastModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                    ),
                ),
                params = backgroundTextGenerationParams(model),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckTitleModelSettings,
            )
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            if (!settings.enableSuggestion) return
            val model = settings.findModelById(settings.suggestionModelId, fallback = settings.fastModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                    )
                ),
                params = backgroundTextGenerationParams(model),
            )
            val suggestions =
                result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessions[conversationId]?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText(maxLength = 2000) }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = backgroundTextGenerationParams(model),
            )

            return result.choices[0].message?.toText()?.trim()
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // ---- 通知 ----

    private fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String) {
        // 先取消 Live Update 通知
        cancelLiveUpdateNotification(conversationId)

        val conversation = getConversationFlow(conversationId).value
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1
        ) {
            title = senderName
            content = conversation.currentMessages.lastOrNull()?.toText()?.take(50)?.trim() ?: ""
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid): Int {
        return conversationId.hashCode() + 10000
    }

    private fun sendLiveUpdateNotification(
        conversationId: Uuid,
        messages: List<UIMessage>,
        senderName: String
    ) {
        val lastMessage = messages.lastOrNull() ?: return
        val parts = lastMessage.parts

        // 确定当前状态
        val (chipText, statusText, contentText) = determineNotificationContent(parts)

        context.sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = getLiveUpdateNotificationId(conversationId)
        ) {
            title = senderName
            content = contentText
            subText = statusText
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            useBigTextStyle = true
            contentIntent = getPendingIntent(context, conversationId)
            requestPromotedOngoing = true
            shortCriticalText = chipText
        }
    }

    private fun determineNotificationContent(parts: List<UIMessagePart>): Triple<String, String, String> {
        // 检查最近的 part 来确定状态
        val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
        val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
        val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()

        return when {
            // 正在执行工具
            lastTool != null && !lastTool.isExecuted -> {
                val toolName = lastTool.toolName.substringAfterLast("__")
                Triple(
                    context.getString(R.string.notification_live_update_chip_tool),
                    context.getString(R.string.notification_live_update_tool, toolName),
                    lastTool.input.take(100)
                )
            }
            // 正在思考（Reasoning 未结束）
            lastReasoning != null && lastReasoning.finishedAt == null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_thinking),
                    context.getString(R.string.notification_live_update_thinking),
                    lastReasoning.reasoning.takeLast(200)
                )
            }
            // 正在写回复
            lastText != null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_writing),
                    lastText.text.takeLast(200)
                )
            }
            // 默认状态
            else -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_title),
                    ""
                )
            }
        }
    }

    private fun cancelLiveUpdateNotification(conversationId: Uuid) {
        context.cancelNotification(getLiveUpdateNotificationId(conversationId))
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        // 原子读-改-写（CAS 循环）—— 避免并行子代理 onProgress 回调并发更新时的 lost-update。
        // 此前实现是「读快照 → 映射 → session.state.value = 直接赋值」的非原子序列：
        // 多个并行 spawn_subagent 的 updateSubagentProgress 在 Dispatchers.IO 上真并发，
        // 两个子代理可能读到同一份旧快照、各自只改自己 toolCallId 的 part 后整体写回，
        // 后写者覆盖前写者 → 一个子代理的流式进度被另一个回退为空（UI 表现为上下文混淆/闪烁）。
        //
        // 用手动 CAS 循环而非 MutableStateFlow.update{}：
        // - id 防御保留（与原 updateConversation 一致：id 不匹配时不写、不删文件）；
        // - checkFilesDelete 只在 CAS 成功后执行一次，避免重试时重复触发删文件副作用
        //   （虽然 deleteChatFiles 对缺失文件幂等，但成功后单次执行更清晰）。
        // update() 的 lambda 经核实为纯映射（无 IO 副作用），在 CAS 重试循环中安全。
        val session = getOrCreateSession(conversationId)
        while (true) {
            val prev = session.state.value
            val updated = update(prev)
            if (updated.id != conversationId) return
            if (session.state.compareAndSet(prev, updated)) {
                checkFilesDelete(updated, prev)
                return
            }
        }
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = Conversation(
            id = Uuid.random(),
            assistantId = currentConversation.assistantId,
            messageNodes = copiedNodes,
            customSystemPrompt = currentConversation.customSystemPrompt,
            modeInjectionIds = currentConversation.modeInjectionIds,
            lorebookIds = currentConversation.lorebookIds,
        )

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val job = sessions[conversationId]?.getJob() ?: return
        job.cancel()
        runCatching { job.join() }
        finishInterruptedPendingTools(conversationId)
    }
}
