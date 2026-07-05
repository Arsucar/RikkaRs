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
import kotlinx.serialization.json.jsonObject
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.ai.subagent.SubagentHost
import me.rerere.rikkahub.data.ai.subagent.SubagentSessionRegistry
import me.rerere.rikkahub.data.ai.subagent.SubagentResult
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.buildSubagentTools
import me.rerere.rikkahub.data.ai.subagent.createManageSubagentTool
import me.rerere.rikkahub.data.ai.subagent.createSubagentTools
import me.rerere.rikkahub.data.ai.subagent.createSubagentWorkspaceTools
import me.rerere.rikkahub.data.ai.subagent.mergeSubagentProfiles
import me.rerere.rikkahub.data.ai.subagent.removeSubagentProfile
import me.rerere.rikkahub.data.ai.subagent.upsertSubagentProfile
import me.rerere.rikkahub.data.datastore.Settings

import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.createConversationTools
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createFinishWorkTool
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.SlashSkillInputTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
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
import me.rerere.rikkahub.data.model.resolveEffectiveWorkspaceCwd
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
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
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
)

internal fun cleanStreamingTextPayload(text: String, json: Json): String {
    val obj = runCatching { json.decodeFromString<JsonObject>(text) }.getOrNull() ?: return text
    val streamingEl = obj["streaming"] ?: return text
    if (streamingEl is JsonPrimitive && streamingEl.contentOrNull == "false") return text
    val updated = buildJsonObject {
        obj.forEach { (key, value) ->
            if (key == "streaming") {
                put("streaming", JsonPrimitive(false))
            } else {
                put(key, value)
            }
        }
    }
    return json.encodeToString(JsonObject.serializer(), updated)
}

internal fun isStreamingSubagentTool(part: UIMessagePart.Tool): Boolean {
    val textPart = part.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
    return textPart?.metadata?.get("subagent_streaming")?.jsonPrimitive?.contentOrNull == "true"
}

internal fun Conversation.cleanStaleSubagentStreaming(json: Json): Conversation {
    val updatedMessages = this.currentMessages.map { message ->
        if (message.role != MessageRole.ASSISTANT) return@map message
        var changed = false
        val updatedParts = message.parts.map { part ->
            if (part is UIMessagePart.Tool && part.toolName == "spawn_subagent" && isStreamingSubagentTool(part)) {
                changed = true
                val cleanedOutput = part.output.map { outputPart ->
                    if (outputPart is UIMessagePart.Text) {
                        val cleanedText = cleanStreamingTextPayload(outputPart.text, json)
                        val sourceMeta = outputPart.metadata
                        val cleanedMeta = if (sourceMeta != null) {
                            buildJsonObject {
                                sourceMeta.forEach { (key, value) ->
                                    if (key == "subagent_streaming") {
                                        put("subagent_streaming", JsonPrimitive(false))
                                    } else if (key == "subagent_cancelled" || key == "subagent_succeeded") {
                                        // skip existing values, will be set below
                                    } else {
                                        put(key, value)
                                    }
                                }
                                put("subagent_cancelled", JsonPrimitive(true))
                                put("subagent_succeeded", JsonPrimitive(false))
                            }
                        } else {
                            null
                        }
                        outputPart.copy(text = cleanedText, metadata = cleanedMeta)
                    } else {
                        outputPart
                    }
                }
                part.copy(output = cleanedOutput)
            } else {
                part
            }
        }
        if (changed) message.copy(parts = updatedParts) else message
    }
    return this.updateCurrentMessages(updatedMessages)
}

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
        SlashSkillInputTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

private val DELEGATE_ALLOWED_LOCAL_TOOLS = setOf(
    LocalToolOption.TimeInfo,
    LocalToolOption.Clipboard,
    LocalToolOption.Logs,
    LocalToolOption.AskUser,
)

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val subagentHost: SubagentHost,
    private val json: Json,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    private val folderRepository: FolderRepository,
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
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
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
        val session = getOrCreateSession(conversationId) // 确保 session 存在
        if (shouldSkipInitializeOnGenerating(session)) {
            Log.d(TAG, "initializeConversation: skipped $conversationId (generating)")
            return
        }
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            val hydrated = hydrateConversationFromDb(conversation, session, json)
            updateConversation(conversationId, hydrated)
            if (hydrated != conversation) {
                saveConversation(conversationId, hydrated)
            }
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

    fun sendMessage(
        conversationId: Uuid,
        content: List<UIMessagePart>,
        answer: Boolean = true,
    ) {
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

                _generationDoneFlow.emit(conversationId)
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
                        val visibleIndex = conversation.messageNodes
                            .filter { !it.hidden }
                            .indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<visibleIndex)
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
        messageRange: ClosedRange<Int>? = null,
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
            // 有效 CWD：会话级 > 助手默认 > /workspace，统一规范化
            val effectiveWorkspaceCwd = resolveEffectiveWorkspaceCwd(conversation, assistant)

            // start generating
            val session = getOrCreateSession(conversationId)
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
                assistant = assistant.copy(parallelToolExecution = false),
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = effectiveWorkspaceCwd,
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
                    if (settings.enableWebSearch) {
                        addAll(createSearchTools(settings))
                    }
                    addAll(
                        localTools.getTools(
                            if (delegateOnly) {
                                assistant.localTools.filter { it in DELEGATE_ALLOWED_LOCAL_TOOLS }
                            } else {
                                assistant.localTools
                            }
                        )
                    )
                    if (assistant.enableRecentChatsReference) {
                        addAll(createConversationTools(conversationRepo, assistant.id))
                    }
                    addAll(
                        createWorkspaceToolsIfReady(
                            assistant.workspaceId?.toString(),
                            effectiveWorkspaceCwd,
                            readOnly = delegateOnly,
                        )
                    )
                    if (!delegateOnly && assistant.enabledSkills.isNotEmpty()) {
                        addAll(
                            createSkillTools(
                                enabledSkills = assistant.enabledSkills,
                                allSkills = skillManager.listSkills(),
                                skillManager = skillManager,
                            )
                        )
                    }
                    if (!delegateOnly) {
                        mcpManager.getAllAvailableTools().also { allTools ->
                            val invalidNames = allTools
                                .map { it.second }
                                .distinct()
                                .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
                            if (invalidNames.isNotEmpty()) {
                                addError(
                                    error = IllegalStateException(
                                        context.getString(
                                            R.string.error_mcp_invalid_server_name,
                                            invalidNames.joinToString(", ")
                                        )
                                    ),
                                    conversationId = conversationId,
                                )
                                return
                            }
                        }.forEach { (serverId, serverName, tool) ->
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
                    if (assistant.enableSubagents) {
                        add(createFinishWorkTool())
                        addAll(
                            buildSubagentToolsForChat(
                                assistant = assistant,
                                settings = settings,
                                parentModel = model,
                                parentTools = this@buildList,
                                workspaceCwd = effectiveWorkspaceCwd,
                                conversationId = conversationId,
                                depth = 0,
                                delegateOnly = delegateOnly,
                            ),
                        )
                    }
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
                updateConversationState(conversationId) { prev ->
                    prev.copy(
                        messageNodes = prev.messageNodes.map { node ->
                            node.copy(messages = node.messages.map { it.finishReasoning() })
                        },
                        updateAt = Instant.now(),
                    ).cleanStaleStreamingMetadata()
                }

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId, senderName)
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        updateConversationState(conversationId) { prev ->
                            prev.updateCurrentMessages(chunk.messages)
                        }

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
            cleanupStreamingSubagentMetadata(conversationId)
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)

            launchWithConversationReference(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            launchWithConversationReference(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
            cleanupStreamingSubagentMetadata(conversationId)
        }
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
        val all = createWorkspaceTools(workspaceId, workspaceRepository, cwd)
        return if (readOnly) all.filter { it.name == "workspace_read_file" } else all
    }

    // ---- 检查无效消息 ----

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
        val afterPending = lastMessage.finishPendingTools(::cancelToolByUser)
        val updatedMessage = afterPending.copy(
            parts = afterPending.parts.map { part ->
                if (part is UIMessagePart.Tool && part.isExecuted && isStreamingSubagentTool(part)) {
                    cancelStreamingSubagentTool(part)
                } else {
                    part
                }
            },
        )
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

    private fun cancelStreamingSubagentTool(part: UIMessagePart.Tool): UIMessagePart.Tool {
        val cancelledOutput = listOf(
            UIMessagePart.Text(
                text = """{"status":"cancelled","error":"Generation cancelled by user","succeeded":false}""",
                metadata = buildJsonObject {
                    val existing = part.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()?.metadata
                    existing?.forEach { (key, value) ->
                        if (key != "subagent_streaming" && key != "subagent_cancelled" && key != "subagent_succeeded") {
                            put(key, value)
                        }
                    }
                    put("subagent_streaming", JsonPrimitive(false))
                    put("subagent_cancelled", JsonPrimitive(true))
                    put("subagent_succeeded", JsonPrimitive(false))
                },
            ),
        )
        return part.copy(output = cancelledOutput)
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

        if (allMessages.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        }

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
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

        val keepMessageIds = messagesToKeep.map { it.id }.toSet()
        var hiddenCount = 0
        val nodesWithHidden = conversation.messageNodes.map { node ->
            val messageId = node.currentMessage.id
            if (messageId !in keepMessageIds && !node.hidden) {
                hiddenCount++
                node.copy(hidden = true)
            } else {
                node
            }
        }

        val summaryNodes = compressedSummaries.map { summary ->
            UIMessage.user(summary).toMessageNode().copy(
                compressHiddenCount = hiddenCount.takeIf { it > 0 },
            )
        }

        val insertAt = nodesWithHidden.indexOfFirst { node ->
            !node.hidden && node.currentMessage.id in keepMessageIds
        }.let { if (it < 0) nodesWithHidden.size else it }

        val newMessageNodes = buildList {
            addAll(nodesWithHidden)
            summaryNodes.forEachIndexed { offset, summaryNode ->
                add(insertAt + offset, summaryNode)
            }
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
        commitConversationState(conversationId, conversation)
    }

    private fun commitConversationState(conversationId: Uuid, newState: Conversation) {
        if (newState.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        synchronized(session.stateLock) {
            val prev = session.state.value
            session.state.value = newState
            checkFilesDelete(newState, prev)
        }
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val session = getOrCreateSession(conversationId)
        synchronized(session.stateLock) {
            val prev = session.state.value
            val updated = update(prev)
            if (updated.id != conversationId) return
            session.state.value = updated
            checkFilesDelete(updated, prev)
        }
    }

    private fun updateSubagentProgress(
        conversationId: Uuid,
        toolCallId: String?,
        profileName: String,
        subMessages: List<UIMessage>,
    ) {
        runCatching {
            if (SubagentSessionRegistry.isCancelRequested(conversationId)) return@runCatching

            val transcript = SubagentHost.buildTranscript(
                subMessages,
                truncateChars = 200,
                truncateToolOutput = 2000,
            )
            if (transcript.isEmpty()) return@runCatching

            val listSerializer = ListSerializer(SubagentTranscriptStep.serializer())
            val loopSteps = subMessages.count { it.role == MessageRole.ASSISTANT }
            val toolCalls = subMessages.sumOf { msg ->
                if (msg.role == MessageRole.ASSISTANT) {
                    msg.parts.count { it is UIMessagePart.Tool }
                } else {
                    0
                }
            }
            val transcriptMetadata = buildJsonObject {
                put("subagent_transcript", json.encodeToJsonElement(listSerializer, transcript))
                put("subagent_profile", JsonPrimitive(profileName))
                put("subagent_steps", JsonPrimitive(loopSteps))
                put("subagent_tool_loop_steps", JsonPrimitive(loopSteps))
                put("subagent_tool_calls", JsonPrimitive(toolCalls))
                put("subagent_succeeded", JsonPrimitive(false))
                put("subagent_streaming", JsonPrimitive(true))
            }
            val partialOutputText = buildJsonObject {
                put("profile_name", JsonPrimitive(profileName))
                put("succeeded", JsonPrimitive(false))
                put("streaming", JsonPrimitive(true))
            }.toString()
            val partialOutput = UIMessagePart.Text(
                text = partialOutputText,
                metadata = transcriptMetadata,
            )

            updateConversationState(conversationId) { conversation ->
                val messages = conversation.currentMessages
                val lastAssistantIndex = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                if (lastAssistantIndex < 0) return@updateConversationState conversation

                val updatedMessages = messages.mapIndexed { index, message ->
                    if (index != lastAssistantIndex) return@mapIndexed message
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

    private fun isStreamingSubagent(part: UIMessagePart.Tool): Boolean = isStreamingSubagentTool(part)

    private fun Conversation.cleanStaleStreamingMetadata(): Conversation = cleanStaleSubagentStreaming(json)

    private fun cleanupStreamingSubagentMetadata(conversationId: Uuid) {
        updateConversationState(conversationId) { conversation ->
            conversation.cleanStaleStreamingMetadata()
        }
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessions.containsKey(conversationId)) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessions.values.any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     *
     * 先把内存中归属该文件夹的活跃 session folderId 置空，再删库：
     * 否则 clearFolder 只改了数据库，而活跃 session 内存态仍指向该文件夹，
     * 后续整对象保存会写回一个已被删除的 folder_id，导致会话在列表中悬空。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessions.values
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
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

    suspend fun toggleMessageHidden(
        conversationId: Uuid,
        messageId: Uuid,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                node.copy(hidden = !node.hidden)
            } else {
                node
            }
        }
        val updatedConversation = currentConversation.copy(messageNodes = updatedNodes)
        saveConversation(conversationId, updatedConversation)
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

    private suspend fun buildSubagentToolsForChat(
        assistant: Assistant,
        settings: Settings,
        parentModel: Model,
        parentTools: List<Tool>,
        workspaceCwd: String?,
        conversationId: Uuid?,
        depth: Int,
        delegateOnly: Boolean = false,
    ): List<Tool> {
        val maxDepth = assistant.subagentMaxDepth.coerceAtLeast(1)
        val assistantId = assistant.id
        val workspaceId = assistant.workspaceId?.toString().orEmpty()
        fun liveSettings(): Settings = settingsStore.settingsFlow.value
        fun liveAssistant(): Assistant =
            liveSettings().assistants.firstOrNull { it.id == assistantId } ?: assistant
        fun mergedProfiles(): List<SubagentProfile> {
            val a = liveAssistant()
            val s = liveSettings()
            return mergeSubagentProfiles(
                custom = a.subagentProfiles,
                global = s.globalSubagentProfiles,
                disabledGlobal = a.disabledGlobalSubagents,
            )
        }
        val result = mutableListOf<Tool>()
        result += createSubagentTools(
            json = json,
            getProfiles = { mergedProfiles() },
            spawn = { profileName, task, _ ->
                val live = liveSettings()
                val parent = liveAssistant()
                val profile = SubagentRegistry.resolveProfile(
                    profileName,
                    parent,
                    live.globalSubagentProfiles,
                )
                if (profile == null) {
                    val available = mergedProfiles().joinToString(", ") { it.name }
                    SubagentResult(
                        profileName = profileName,
                        summary = "",
                        succeeded = false,
                        error = "profile not found; available: $available",
                        depth = depth + 1,
                    )
                } else {
                    val toolCallId = currentToolCallId()
                    subagentHost.spawn(
                        profileName = profileName,
                        task = task,
                        settings = live,
                        parentAssistant = parent,
                        parentModel = parentModel,
                        buildChildTools = { _, childDepth ->
                            toolsForSubagentProfile(
                                profile = profile,
                                assistant = parent,
                                settings = live,
                                parentModel = parentModel,
                                parentTools = parentTools,
                                workspaceCwd = workspaceCwd,
                                workspaceId = workspaceId,
                                conversationId = conversationId,
                                depth = childDepth,
                                maxDepth = maxDepth,
                            )
                        },
                        depth = depth + 1,
                        maxDepth = maxDepth,
                        workspaceCwd = workspaceCwd,
                        conversationId = conversationId,
                        onProgress = if (conversationId != null) {
                            { subMessages ->
                                updateSubagentProgress(
                                    conversationId,
                                    toolCallId,
                                    profile.name,
                                    subMessages,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            },
            askBtw = { question ->
                val live = liveSettings()
                subagentHost.askBtw(
                    question = question,
                    settings = live,
                    parentAssistant = liveAssistant(),
                    parentModel = parentModel,
                    workspaceCwd = workspaceCwd,
                )
            },
            delegateOnly = delegateOnly && depth == 0,
            parallelExecutionEnabled = false,
        )
        createManageSubagentTool(
            json = json,
            depth = depth,
            resolveProfile = { name ->
                val parent = liveAssistant()
                SubagentRegistry.resolveProfile(name, parent, liveSettings().globalSubagentProfiles)
            },
            manage = { action, name, profile ->
                manageSubagentProfile(assistantId, action, name, profile)
            },
        )?.let { result += it }
        return result
    }

    private suspend fun toolsForSubagentProfile(
        profile: SubagentProfile,
        assistant: Assistant,
        settings: Settings,
        parentModel: Model,
        parentTools: List<Tool>,
        workspaceCwd: String?,
        workspaceId: String,
        conversationId: Uuid?,
        depth: Int,
        maxDepth: Int,
    ): List<Tool> {
        val workspaceToolsFactory: (me.rerere.rikkahub.data.ai.subagent.WorkspaceAccess) -> List<Tool> = { access ->
            kotlinx.coroutines.runBlocking {
                createSubagentWorkspaceTools(
                    access = access,
                    profile = profile,
                    workspaceRepository = workspaceRepository,
                    workspaceId = workspaceId,
                    workspaceCwd = workspaceCwd,
                )
            }
        }
        val spawnToolBuilder: (() -> Tool)? =
            if (profile.canSpawn && depth + 1 <= maxDepth) {
                {
                    createSubagentTools(
                        json = json,
                        getProfiles = {
                            val live = settingsStore.settingsFlow.value
                            val parent = live.assistants.firstOrNull { it.id == assistant.id } ?: assistant
                            mergeSubagentProfiles(
                                custom = parent.subagentProfiles,
                                global = live.globalSubagentProfiles,
                                disabledGlobal = parent.disabledGlobalSubagents,
                            )
                        },
                        spawn = { nestedProfile, nestedTask, _ ->
                            val live = settingsStore.settingsFlow.value
                            val parent = live.assistants.firstOrNull { it.id == assistant.id } ?: assistant
                            val nested = SubagentRegistry.resolveProfile(
                                nestedProfile,
                                parent,
                                live.globalSubagentProfiles,
                            )
                            if (nested == null) {
                                SubagentResult(
                                    profileName = nestedProfile,
                                    summary = "",
                                    succeeded = false,
                                    error = "profile not found",
                                    depth = depth + 1,
                                )
                            } else {
                                val toolCallId = currentToolCallId()
                                subagentHost.spawn(
                                    profileName = nestedProfile,
                                    task = nestedTask,
                                    settings = live,
                                    parentAssistant = parent,
                                    parentModel = parentModel,
                                    buildChildTools = { _, d ->
                                        toolsForSubagentProfile(
                                            profile = nested,
                                            assistant = parent,
                                            settings = live,
                                            parentModel = parentModel,
                                            parentTools = parentTools,
                                            workspaceCwd = workspaceCwd,
                                            workspaceId = workspaceId,
                                            conversationId = conversationId,
                                            depth = d,
                                            maxDepth = maxDepth,
                                        )
                                    },
                                    depth = depth + 1,
                                    maxDepth = maxDepth,
                                    workspaceCwd = workspaceCwd,
                                    conversationId = conversationId,
                                    onProgress = if (conversationId != null) {
                                        { subMessages ->
                                            updateSubagentProgress(
                                                conversationId,
                                                toolCallId,
                                                nested.name,
                                                subMessages,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        },
                        askBtw = { q ->
                            val live = settingsStore.settingsFlow.value
                            val parent = live.assistants.firstOrNull { it.id == assistant.id } ?: assistant
                            subagentHost.askBtw(q, live, parent, parentModel, workspaceCwd)
                        },
                        delegateOnly = false,
                        parallelExecutionEnabled = assistant.parallelToolExecution,
                    ).first { it.name == "spawn_subagent" }
                }
            } else {
                null
            }
        return SubagentHost.sandboxToolsForSubagent(
            buildSubagentTools(
                profile = profile,
                depth = depth,
                maxDepth = maxDepth,
                parentTools = parentTools,
                workspaceToolsFactory = workspaceToolsFactory,
                spawnToolBuilder = spawnToolBuilder,
                extraLocalToolsProvider = {
                    if (profile.inheritTools) {
                        localTools.getTools(profile.extraLocalTools)
                    } else {
                        emptyList()
                    }
                },
            ),
        )
    }

    private suspend fun manageSubagentProfile(
        assistantId: Uuid,
        action: String,
        name: String,
        profile: SubagentProfile?,
    ): String {
        val current = settingsStore.settingsFlow.first()
        val target = current.assistants.firstOrNull { it.id == assistantId }
            ?: return "Error: assistant not found"
        val merged = mergeSubagentProfiles(
            custom = target.subagentProfiles,
            global = current.globalSubagentProfiles,
            disabledGlobal = target.disabledGlobalSubagents,
        )
        return when (action) {
            "list" -> {
                if (merged.isEmpty()) "No subagent profiles available."
                else merged.joinToString("\n") { p -> "- ${p.name}: ${p.description}" }
            }
            "create", "update" -> {
                val p = profile ?: return "Error: profile data missing"
                settingsStore.update { settings ->
                    settings.copy(
                        assistants = settings.assistants.map { a ->
                            if (a.id == assistantId) {
                                a.copy(subagentProfiles = upsertSubagentProfile(a.subagentProfiles, p))
                            } else {
                                a
                            }
                        },
                    )
                }
                "$action: subagent profile '${p.name}' saved."
            }
            "delete" -> {
                if (name.isBlank()) return "Error: name required for delete"
                val isGlobal = current.globalSubagentProfiles.any { it.name == name }
                settingsStore.update { settings ->
                    settings.copy(
                        assistants = settings.assistants.map { a ->
                            if (a.id == assistantId) {
                                a.copy(
                                    subagentProfiles = removeSubagentProfile(a.subagentProfiles, name),
                                    disabledGlobalSubagents = if (isGlobal) {
                                        a.disabledGlobalSubagents + name
                                    } else {
                                        a.disabledGlobalSubagents
                                    },
                                )
                            } else {
                                a
                            }
                        },
                    )
                }
                "delete: profile '$name' removed or disabled."
            }
            else -> "Error: unknown action $action"
        }
    }


    suspend fun stopGeneration(conversationId: Uuid) {
        subagentHost.requestCancel(conversationId)
        val job = sessions[conversationId]?.getJob() ?: run {
            finishInterruptedPendingTools(conversationId)
            return
        }
        job.cancel()
        runCatching { job.join() }
        finishInterruptedPendingTools(conversationId)
    }
}

internal fun shouldSkipInitializeOnGenerating(session: ConversationSession): Boolean =
    session.isGenerating

internal fun hydrateConversationFromDb(
    loaded: Conversation,
    session: ConversationSession,
    json: Json,
): Conversation {
    if (session.isGenerating) return loaded
    return loaded.cleanStaleSubagentStreaming(json)
}
