package me.rerere.rikkahub.service

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.ContextPreview
import me.rerere.rikkahub.data.ai.GenerationPreparationMode
import me.rerere.rikkahub.data.ai.GenerationPreparationException
import me.rerere.rikkahub.data.ai.PreparedProviderInput
import me.rerere.rikkahub.data.ai.ProviderRateLimiter
import me.rerere.rikkahub.data.ai.estimatePromptTokens
import me.rerere.rikkahub.data.ai.currentToolCallId
import me.rerere.rikkahub.data.ai.toContextPreview
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
import me.rerere.rikkahub.data.ai.subagent.SubagentStatus
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.SUBAGENT_USER_CANCEL_REASON
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
import me.rerere.rikkahub.data.ai.tools.buildSkillManagementTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.ai.tools.buildMemoryTableToolsIfEnabled
import me.rerere.rikkahub.data.ai.tools.WorkspaceKnownMount
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.SlashSkillInputTransformer
import me.rerere.rikkahub.data.ai.transformers.MemoryTableInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.event.NoticeKind
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.resolveAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.shouldEnableMemoryTable
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.resolveEffectiveWorkspaceCwd
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.HookRepository
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookDispatchPersistenceResult
import me.rerere.rikkahub.data.model.HookExecutionMetadata
import me.rerere.rikkahub.data.model.HookTrigger
import me.rerere.rikkahub.data.model.actionType
import me.rerere.rikkahub.data.model.configurationHash
import me.rerere.rikkahub.service.hooks.FrozenHookExecution
import me.rerere.rikkahub.service.hooks.FrozenHookModelRequest
import me.rerere.rikkahub.service.hooks.HookActionContext
import me.rerere.rikkahub.service.hooks.HookDispatcher
import me.rerere.rikkahub.service.hooks.evaluateHookFinalSuccess
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceShellStatus
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

private data class AssistantSkillMounts(
    val knownMounts: List<WorkspaceKnownMount>,
    val bindMounts: List<WorkspaceBindMount>,
)

private data class PreparedGenerationRequest(
    val conversation: Conversation,
    val assistant: Assistant,
    val model: Model,
    val settings: Settings,
    val messages: List<UIMessage>,
    val memories: List<AssistantMemory>,
    val inputTransformers: List<InputMessageTransformer>,
    val tools: List<Tool>,
    val workspaceCwd: String?,
    val providerInput: PreparedProviderInput?,
)

private data class ResolvedGenerationTarget(
    val assistant: Assistant,
    val model: Model,
)

private fun Settings.resolveGenerationTarget(conversation: Conversation): ResolvedGenerationTarget? {
    val assistant = resolveAssistant(conversation)
    val model = getCurrentChatModel(conversation) ?: return null
    return ResolvedGenerationTarget(assistant = assistant, model = model)
}

internal fun sanitizeInvalidMessages(conversation: Conversation): Conversation {
    val sanitizedNodes = conversation.messageNodes.mapNotNull { originalNode ->
        if (originalNode.messages.isEmpty()) return@mapNotNull null
        val node = if (originalNode.selectIndex in originalNode.messages.indices) {
            originalNode
        } else {
            originalNode.copy(selectIndex = 0)
        }
        val currentMessage = node.currentMessage
        val unresolvedTools = currentMessage.getTools().filter { !it.isExecuted }
        if (unresolvedTools.isEmpty() || unresolvedTools.any { it.canResumeExecution }) {
            return@mapNotNull node
        }

        val remaining = node.messages.filter { it.id != currentMessage.id }
        if (remaining.isEmpty()) return@mapNotNull null
        node.copy(
            messages = remaining,
            selectIndex = (node.selectIndex - 1).coerceIn(remaining.indices),
        )
    }
    return if (sanitizedNodes == conversation.messageNodes) {
        conversation
    } else {
        conversation.copy(messageNodes = sanitizedNodes)
    }
}

internal fun List<UIMessage>.hasResumablePendingTool(): Boolean =
    lastOrNull()?.getTools()?.any { it.canResumeExecution } == true

internal fun backgroundTextGenerationParams(
    model: Model,
    reasoningLevel: ReasoningLevel = ReasoningLevel.OFF,
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
            } else if (key == "context_status") {
                put("context_status", JsonPrimitive(SubagentStatus.INTERRUPTED.name))
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
                                    } else if (
                                        key == "subagent_cancelled" ||
                                        key == "subagent_succeeded" ||
                                        key == "subagent_context_status"
                                    ) {
                                        // skip existing values, will be set below
                                    } else {
                                        put(key, value)
                                    }
                                }
                                put("subagent_cancelled", JsonPrimitive(true))
                                put("subagent_succeeded", JsonPrimitive(false))
                                put("subagent_context_status", JsonPrimitive(SubagentStatus.INTERRUPTED.name))
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
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryTableRepository: MemoryTableRepository,
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
    private val hookRepository: HookRepository,
    private val hookDispatcher: HookDispatcher,
    private val conversationTagRepository: ConversationTagRepository,
) {
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

    fun cleanup() = runCatching {
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
                    handleMessageComplete(conversationId, GenerationInvocationKind.NormalSend)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e
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
                    handleMessageComplete(conversationId, GenerationInvocationKind.Regenerate)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val visibleIndex = conversation.messageNodes
                            .filter { !it.hidden }
                            .indexOf(node)
                        handleMessageComplete(
                            conversationId = conversationId,
                            invocationKind = GenerationInvocationKind.Regenerate,
                            messageRange = 0..<visibleIndex,
                        )
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e
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
                    val logicalTurnId = hookRepository
                        .findActiveTurnByPendingToolCall(toolCallId)
                        ?.logicalTurnId
                    handleMessageComplete(
                        conversationId,
                        GenerationInvocationKind.ToolContinuation,
                        logicalTurnId = logicalTurnId,
                    )
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        invocationKind: GenerationInvocationKind,
        messageRange: ClosedRange<Int>? = null,
        logicalTurnId: Uuid? = null,
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val target = settings.resolveGenerationTarget(initialConversation) ?: return
        val assistant = target.assistant
        val model = target.model
        val sourceNode = initialConversation.messageNodes.lastOrNull { !it.hidden }
        val sourceMessage = sourceNode?.messages?.getOrNull(sourceNode.selectIndex)
        val activeLogicalTurnId = logicalTurnId
            ?: if (invocationKind == GenerationInvocationKind.ToolContinuation) {
                hookRepository.findActiveTurnByConversation(conversationId)?.logicalTurnId
            } else {
                null
            }
            ?: hookRepository.createLogicalTurn(
                conversationId = conversationId,
                assistantId = assistant.id,
                sourceNodeId = sourceNode?.id,
                sourceMessageId = sourceMessage?.id,
                invocationKind = invocationKind.name,
            )

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
                if (assistant.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // Keep send cleanup behavior, but derive it through the same pure projection used by preview.
            val currentConversation = getConversationFlow(conversationId).value
            val conversation = sanitizeInvalidMessages(currentConversation)
            if (conversation != currentConversation) {
                updateConversation(conversationId, conversation)
            }
            val session = getOrCreateSession(conversationId)
            val preparedOriginal = prepareGenerationRequest(
                settings = settings,
                assistant = assistant,
                model = model,
                conversation = conversation,
                messageRange = messageRange,
                mode = GenerationPreparationMode.Send,
                processingStatus = session.processingStatus,
            )
            val prepared = maybeAutoCompressBeforeSend(
                conversationId = conversationId,
                invocationKind = invocationKind,
                preparedOriginal = preparedOriginal,
                session = session,
            )

            // start generating
            generationHandler.generateText(
                settings = prepared.settings,
                model = prepared.model,
                processingStatus = session.processingStatus,
                messages = prepared.messages,
                assistant = prepared.assistant,
                conversationSystemPrompt = prepared.conversation.customSystemPrompt,
                conversationModeInjectionIds = prepared.conversation.modeInjectionIds,
                conversationLorebookIds = prepared.conversation.lorebookIds,
                workspaceCwd = prepared.workspaceCwd,
                memories = prepared.memories,
                inputTransformers = prepared.inputTransformers,
                outputTransformers = outputTransformers,
                tools = prepared.tools,
                firstPreparedInput = prepared.providerInput,
            ).onCompletion {
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

                // 生成结束：取消 Live Update 通知，后台时发送完成通知
                appEventBus.emit(
                    AppEvent.ChatGenerationEnded(
                        conversationId = conversationId,
                        senderName = senderName,
                        contentPreview = updatedConversation.currentMessages.lastOrNull()
                            ?.toText()?.take(50)?.trim() ?: "",
                    )
                )
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        updateConversationState(conversationId) { prev ->
                            prev.updateCurrentMessages(chunk.messages)
                        }

                        // 通知等边缘副作用由 ChatNotificationManager 消费；
                        // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                        chunk.messages.lastOrNull()?.let { lastMessage ->
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                            )
                        }
                    }
                }
            }
        }.onFailure {
            if (it is CancellationException) {
                hookRepository.markTurnCancelled(activeLogicalTurnId)
                throw it
            }
            hookRepository.markTurnFailed(activeLogicalTurnId)
            // 兜底取消 Live Update 通知（生成开始前失败时 onCompletion 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))

            it.printStackTrace()
            if (it is GenerationPreparationException.InvalidMcpServerName) {
                // Preserve the pre-refactor send behavior: report the MCP validation error directly.
                addError(it, conversationId)
            } else {
                addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            }
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
            cleanupStreamingSubagentMetadata(conversationId)
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)

            dispatchFinalResponseHooks(
                logicalTurnId = activeLogicalTurnId,
                conversation = finalConversation,
                assistant = assistant,
            )

            launchWithConversationReference(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            launchWithConversationReference(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
            cleanupStreamingSubagentMetadata(conversationId)
        }
    }

    private suspend fun dispatchFinalResponseHooks(
        logicalTurnId: Uuid,
        conversation: Conversation,
        assistant: Assistant,
    ) {
        val pendingToolIds = conversation.currentMessages
            .flatMap { it.parts.filterIsInstance<UIMessagePart.Tool>() }
            .filter { it.isPending || !it.isExecuted }
            .mapTo(linkedSetOf()) { it.toolCallId }
        if (pendingToolIds.isNotEmpty()) {
            hookRepository.updatePendingTools(logicalTurnId, pendingToolIds)
            return
        }
        val snapshot = evaluateHookFinalSuccess(conversation)
        if (snapshot == null) {
            hookRepository.markTurnFailed(logicalTurnId)
            return
        }
        val hooks = assistant.hooks.filter {
            it.enabled && it.trigger == HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS
        }
        val metadata = hooks.mapIndexed { index, hook ->
            HookExecutionMetadata(
                hookId = hook.id,
                hookOrder = index,
                hookConfigVersion = hook.configVersion,
                hookConfigHash = hook.configurationHash(),
                modelId = hook.modelId,
                actionType = hook.actionConfig.actionType,
            )
        }
        val persistence = hookRepository.finalizeAndCreateRunExactlyOnce(
            logicalTurnId = logicalTurnId,
            trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
            nodeId = snapshot.nodeId,
            messageId = snapshot.messageId,
            messageModelId = snapshot.messageModelId,
            hooks = metadata,
        )
        if (persistence !is HookDispatchPersistenceResult.Created) return

        val frozenExecutions = hooks.zip(metadata).map { (hook, execution) ->
            val allowedTagIds = (hook.actionConfig as HookActionConfig.AddConversationTag).allowedTagIds
            val allowedTags = allowedTagIds.mapNotNull { tagId ->
                conversationTagRepository.getTag(tagId)?.let { tagId to it.displayName }
            }.toMap()
            FrozenHookExecution(
                executionId = execution.executionId,
                hook = hook,
                request = FrozenHookModelRequest(
                    modelId = hook.modelId,
                    prompt = hook.prompt,
                    messageTextSnapshot = snapshot.text,
                    allowedTags = allowedTags,
                ),
                actionContext = HookActionContext(
                    conversationId = conversation.id,
                    sourceNodeId = snapshot.nodeId,
                    sourceMessageId = snapshot.messageId,
                    executionId = execution.executionId,
                    leaseToken = 0,
                    allowedTagIds = allowedTagIds,
                ),
            )
        }
        appScope.launch {
            hookDispatcher.dispatch(persistence.runId, frozenExecutions)
            runCatching { hookRepository.cleanupHistory(conversation.id) }
        }
    }

    private suspend fun maybeAutoCompressBeforeSend(
        conversationId: Uuid,
        invocationKind: GenerationInvocationKind,
        preparedOriginal: PreparedGenerationRequest,
        session: ConversationSession,
    ): PreparedGenerationRequest {
        // The eligibility gate encloses every policy mutation and compression side effect.
        return runAutoCompressionIfEligible(
            invocationKind = invocationKind,
            providerInputAvailable = preparedOriginal.providerInput != null,
            preparedOriginal = preparedOriginal,
        ) eligible@{
            val providerInput = checkNotNull(preparedOriginal.providerInput)
            val config = AutoCompressionConfig(
                enabled = preparedOriginal.assistant.autoCompressEnabled,
                thresholdTokens = preparedOriginal.assistant.autoCompressThresholdTokens,
                targetTokens = preparedOriginal.settings.compressTargetTokens,
                keepRecentMessages = preparedOriginal.assistant.autoCompressKeepRecentMessages,
                identity = preparedOriginal.assistant.id.toString(),
            )
            val promptTokens = estimatePromptTokens(providerInput.messages)
            val visibleMessageCount = preparedOriginal.conversation.currentMessages.size
            val evaluation = session.evaluateAutoCompression(
                AutoCompressionPolicyInput(
                    config = config,
                    invocationKind = invocationKind,
                    providerInputAvailable = true,
                    promptTokens = promptTokens,
                    visibleMessageCount = visibleMessageCount,
                    fingerprint = buildAutoCompressionFingerprint(preparedOriginal.conversation, config),
                    busy = session.compressionCoordinator.isBusy,
                )
            )
            if (evaluation.decision != AutoCompressionDecision.Trigger) return@eligible preparedOriginal

            when (val result = session.compressionCoordinator.tryRun {
                // Evaluation is optimistic. Disarm only after winning the per-conversation coordinator.
                if (!session.commitAutoCompressionTrigger(evaluation)) return@tryRun preparedOriginal

                resolveAfterAutoCompressionAttempt(
                    preparedOriginal = preparedOriginal,
                    compress = {
                        compressConversationLocked(
                            session = session,
                            additionalPrompt = "",
                            targetTokens = config.targetTokens,
                            keepRecentMessages = config.keepRecentMessages,
                        )
                    },
                    reload = {
                        conversationRepo.getConversationById(conversationId)
                            ?: throw IllegalStateException("Compressed conversation could not be reloaded")
                        // A state update after atomic publication wins over the just-persisted snapshot.
                        session.snapshotState().conversation
                    },
                    prepareReloaded = { refreshedConversation ->
                        prepareGenerationRequest(
                            settings = preparedOriginal.settings,
                            assistant = preparedOriginal.assistant,
                            model = preparedOriginal.model,
                            conversation = refreshedConversation,
                            messageRange = null,
                            mode = GenerationPreparationMode.Send,
                            processingStatus = session.processingStatus,
                        )
                    },
                    onSuccess = { rebuilt ->
                        rebuilt.providerInput?.let { rebuiltInput ->
                            session.observeAutoCompressionPreparedInput(
                                config = config,
                                promptTokens = estimatePromptTokens(rebuiltInput.messages),
                            )
                        }
                        appEventBus.tryEmit(
                            AppEvent.Notice(
                                message = context.getString(R.string.chat_auto_compress_success),
                                kind = NoticeKind.Success,
                            )
                        )
                    },
                    onFailure = {
                        session.recordAutoCompressionFailure(visibleMessageCount)
                        appEventBus.tryEmit(
                            AppEvent.Notice(
                                message = context.getString(R.string.chat_auto_compress_failed),
                                kind = NoticeKind.Error,
                            )
                        )
                    },
                )
            }) {
                CompressionLockResult.Busy -> preparedOriginal
                is CompressionLockResult.Acquired -> result.value
            }
        }
    }

    suspend fun buildContextPreview(conversationId: Uuid): ContextPreview {
        val settings = settingsStore.settingsFlow.first()
        val conversation = sanitizeInvalidMessages(getConversationFlow(conversationId).value)
        val target = settings.resolveGenerationTarget(conversation)
            ?: throw IllegalStateException(context.getString(R.string.context_inspector_error_model))
        return prepareGenerationRequest(
            settings = settings,
            assistant = target.assistant,
            model = target.model,
            conversation = conversation,
            messageRange = null,
            mode = GenerationPreparationMode.Preview,
        ).providerInput?.toContextPreview(json)
            ?: error("Preview preparation did not produce provider input")
    }

    private suspend fun prepareGenerationRequest(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        conversation: Conversation,
        messageRange: ClosedRange<Int>?,
        mode: GenerationPreparationMode,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    ): PreparedGenerationRequest {
        val effectiveWorkspaceCwd = resolveEffectiveWorkspaceCwd(conversation, assistant)
        val workspace = assistant.workspaceId
            ?.toString()
            ?.let { workspaceRepository.getById(it) }
        val memoryTableEnabled = shouldEnableMemoryTable(
            settingsEnabled = settings.enableMemoryTable,
            assistantEnabled = assistant.enableMemoryTable,
        )
        val memoryTableTemplates = if (memoryTableEnabled) {
            memoryTableRepository.getEffectiveTemplates(assistant.id.toString())
        } else {
            emptyList()
        }
        val memoryTableDocuments = if (memoryTableEnabled) {
            memoryTableRepository.getEffectiveDocuments(
                assistantId = assistant.id.toString(),
                conversationId = conversation.id.toString(),
            ).let { documents ->
                val visibleTemplateIds = memoryTableTemplates.mapTo(mutableSetOf()) { it.id }
                val templateScopedDocuments = documents.filter { it.templateId in visibleTemplateIds }
                if (conversation.memoryTableIsolation) {
                    templateScopedDocuments.filter { it.scopeType == MemoryTableScopeType.CONVERSATION }
                } else {
                    templateScopedDocuments
                }
            }
        } else {
            emptyList()
        }
        val messages = if (messageRange != null) {
            conversation.currentMessages.subList(messageRange.start, messageRange.endInclusive + 1)
        } else {
            conversation.currentMessages
        }
        val hasResumablePendingTool = messages.hasResumablePendingTool()
        if (mode == GenerationPreparationMode.Preview && hasResumablePendingTool) {
            throw GenerationPreparationException.PendingToolExecution(
                context.getString(R.string.context_inspector_error_pending_tool),
            )
        }
        val transformers = buildList {
            addAll(inputTransformers)
            if (memoryTableEnabled) {
                add(
                    MemoryTableInjectionTransformer(
                        templates = memoryTableTemplates,
                        documents = memoryTableDocuments,
                        maxDocuments = settings.memoryTableMaxInjectDocuments,
                        maxTokens = settings.memoryTableMaxInjectTokens,
                        maxChars = settings.memoryTableMaxInjectChars,
                    )
                )
            }
            add(templateTransformer)
            add(WorkspaceReminderTransformer(workspace))
        }
        val tools = buildGenerationTools(
            settings = settings,
            assistant = assistant,
            model = model,
            conversation = conversation,
            memoryTableEnabled = memoryTableEnabled,
            effectiveWorkspaceCwd = effectiveWorkspaceCwd,
            workspace = workspace,
            mode = mode,
        )
        val memories = memoryRepository.getEffectiveMemories(assistant.id.toString())
        val providerInput = if (hasResumablePendingTool) {
            // The send loop must execute the approved/denied/answered tool first. Its output changes
            // the next provider input, so preparing/transformation here would be both stale and unsafe.
            null
        } else {
            generationHandler.prepareFirstProviderInput(
                settings = settings,
                model = model,
                messages = messages,
                inputTransformers = transformers,
                assistant = assistant,
                memories = memories,
                tools = tools,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = effectiveWorkspaceCwd,
                mode = mode,
                processingStatus = processingStatus,
            )
        }
        return PreparedGenerationRequest(
            conversation = conversation,
            assistant = assistant,
            model = model,
            settings = settings,
            messages = messages,
            memories = memories,
            inputTransformers = transformers,
            tools = tools,
            workspaceCwd = effectiveWorkspaceCwd,
            providerInput = providerInput,
        )
    }

    private suspend fun buildGenerationTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        conversation: Conversation,
        memoryTableEnabled: Boolean,
        effectiveWorkspaceCwd: String?,
        workspace: WorkspaceEntity?,
        mode: GenerationPreparationMode,
    ): List<Tool> = buildList {
        val delegateOnly = assistant.enableSubagents && assistant.subagentDelegateOnly
        if (assistant.enableWebSearch) addAll(createSearchTools(settings))
        addAll(
            localTools.getTools(
                if (delegateOnly) assistant.localTools.filter { it in DELEGATE_ALLOWED_LOCAL_TOOLS }
                else assistant.localTools,
            ),
        )
        if (assistant.enableRecentChatsReference) {
            addAll(createConversationTools(conversationRepo, assistant.id))
        }
        addAll(
            buildMemoryTableToolsIfEnabled(
                enabled = memoryTableEnabled,
                json = json,
                assistantId = assistant.id.toString(),
                conversationId = conversation.id.toString(),
                readDocuments = {
                    val templates = memoryTableRepository.getEffectiveTemplates(assistant.id.toString())
                    val visibleTemplateIds = templates.mapTo(mutableSetOf()) { it.id }
                    memoryTableRepository.getEffectiveDocuments(
                        assistantId = assistant.id.toString(),
                        conversationId = conversation.id.toString(),
                    ).filter { it.templateId in visibleTemplateIds }
                },
                getDocument = { documentId ->
                    memoryTableRepository.getEffectiveDocument(
                        id = documentId,
                        assistantId = assistant.id.toString(),
                        conversationId = conversation.id.toString(),
                    )
                },
                upsertDocument = { document ->
                    memoryTableRepository.upsertDocument(
                        document = document,
                        actorAssistantId = assistant.id.toString(),
                        actorConversationId = conversation.id.toString(),
                    )
                },
                deleteDocument = { documentId ->
                    memoryTableRepository.deleteDocument(
                        id = documentId,
                        assistantId = assistant.id.toString(),
                        conversationId = conversation.id.toString(),
                    )
                },
                readTemplates = { memoryTableRepository.getEffectiveTemplates(assistant.id.toString()) },
                upsertTemplate = { template ->
                    memoryTableRepository.upsertTemplate(
                        template = template,
                        actorAssistantId = assistant.id.toString(),
                    )
                },
                deleteTemplate = { templateId ->
                    memoryTableRepository.deleteTemplate(
                        id = templateId,
                        actorAssistantId = assistant.id.toString(),
                    )
                },
            ),
        )
        addAll(
            createWorkspaceToolsIfReady(
                workspace = workspace,
                assistantId = assistant.id,
                cwd = effectiveWorkspaceCwd,
                readOnly = delegateOnly,
                createSkillDirectories = mode == GenerationPreparationMode.Send,
            ),
        )
        if (!delegateOnly && assistant.enabledSkills.isNotEmpty()) {
            addAll(
                createSkillTools(
                    assistant.enabledSkills,
                    skillManager.listSkillsForAssistant(
                        assistantId = assistant.id,
                        createIfMissing = mode == GenerationPreparationMode.Send,
                    ),
                ),
            )
        }
        if (!delegateOnly) {
            addAll(
                buildSkillManagementTools(
                    assistantId = assistant.id,
                    skillManager = skillManager,
                    autoEnable = true,
                    onSkillEnabled = { skillName ->
                        settingsStore.update { current ->
                            current.copy(
                                assistants = current.assistants.map { item ->
                                    if (item.id == assistant.id) {
                                        item.copy(enabledSkills = item.enabledSkills + skillName)
                                    } else {
                                        item
                                    }
                                },
                            )
                        }
                    },
                ),
            )
        }
        if (!delegateOnly) {
            val allMcpTools = mcpManager.getAllAvailableTools()
            val invalidNames = allMcpTools.map { it.second }.distinct().filter { name ->
                name.isEmpty() || !name.all {
                    it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9'
                }
            }
            if (invalidNames.isNotEmpty()) {
                throw GenerationPreparationException.InvalidMcpServerName(
                    invalidNames = invalidNames,
                    message = context.getString(
                        R.string.error_mcp_invalid_server_name,
                        invalidNames.joinToString(", "),
                    ),
                )
            }
            allMcpTools.forEach { (serverId, serverName, tool) ->
                add(
                    Tool(
                        name = "mcp__${serverName}__${tool.name}",
                        description = tool.description.orEmpty(),
                        parameters = { tool.inputSchema },
                        needsApproval = { tool.needsApproval },
                        execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                    ),
                )
            }
        }
        if (assistant.enableSubagents) {
            addAll(
                buildSubagentToolsForChat(
                    assistant = assistant,
                    settings = settings,
                    parentModel = model,
                    parentTools = this@buildList,
                    workspaceCwd = effectiveWorkspaceCwd,
                    conversationId = conversation.id,
                    depth = 0,
                    delegateOnly = delegateOnly,
                ),
            )
        }
    }

    private suspend fun createWorkspaceToolsIfReady(
        workspace: WorkspaceEntity?,
        assistantId: Uuid,
        cwd: String? = null,
        readOnly: Boolean = false,
        createSkillDirectories: Boolean = true,
    ): List<Tool> {
        workspace ?: return emptyList()
        val workspaceId = workspace.id
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        val privateSkillMounts = assistantPrivateSkillMounts(
            assistantId = assistantId,
            createDirectories = createSkillDirectories,
        )
        val all = createWorkspaceTools(
            workspaceId = workspaceId,
            workspaceRepository = workspaceRepository,
            cwd = cwd,
            knownMounts = listOf(
                WorkspaceKnownMount(
                    target = "/skills",
                    source = skillManager.getSkillsDir(createIfMissing = createSkillDirectories),
                    allowedSymlinkRoots = listOf(
                        skillManager.getSkillSharedDir(createIfMissing = createSkillDirectories),
                    ),
                )
            ) + privateSkillMounts.knownMounts,
            extraBindMounts = privateSkillMounts.bindMounts,
            approvalOverrides = workspace.toolApprovalOverrides(),
        )
        return if (readOnly) all.filter { it.name == "workspace_read_file" } else all
    }

    private fun assistantPrivateSkillMounts(
        assistantId: Uuid,
        createDirectories: Boolean = true,
    ): AssistantSkillMounts {
        val assistantSkillsDir = skillManager.getAssistantSkillsDir(assistantId, createDirectories)
        val skillSharedDir = skillManager.getSkillSharedDir(createDirectories)
        return AssistantSkillMounts(
            knownMounts = listOf(
                WorkspaceKnownMount(
                    target = "/skills_private",
                    source = assistantSkillsDir,
                    allowedSymlinkRoots = listOf(skillSharedDir),
                )
            ),
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = assistantSkillsDir,
                    target = "/skills_private",
                )
            ),
        )
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
            val messages = listOf(
                UIMessage.user(
                    prompt = settings.titlePrompt.applyPlaceholders(
                        "locale" to Locale.getDefault().displayName,
                        "content" to conversation.currentMessages
                            .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                ),
            )
            val params = backgroundTextGenerationParams(model)
            ProviderRateLimiter.await(provider = provider, messages = messages, params = params)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = params,
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
            val messages = listOf(
                UIMessage.user(
                    settings.suggestionPrompt.applyPlaceholders(
                        "locale" to Locale.getDefault().displayName,
                        "content" to conversation.currentMessages
                            .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                )
            )
            val params = backgroundTextGenerationParams(model)
            ProviderRateLimiter.await(provider = provider, messages = messages, params = params)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = params,
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
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32,
    ): Result<Unit> {
        val session = getOrCreateSession(conversationId)
        return try {
            session.compressionCoordinator.tryRun {
                compressConversationLocked(
                    session = session,
                    additionalPrompt = additionalPrompt,
                    targetTokens = targetTokens,
                    keepRecentMessages = keepRecentMessages,
                )
            }.toManualCompressionResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun compressConversationLocked(
        session: ConversationSession,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int,
    ) {
        require(targetTokens >= 0) { "Compression target tokens must be nonnegative" }
        require(keepRecentMessages >= 0) { "Compression keep-recent count must be nonnegative" }

        val sourceSnapshot = session.snapshotState()
        val conversation = sourceSnapshot.conversation
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel(conversation)
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)
        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        if (allMessages.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        }

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
            return splitMessages(messages.subList(0, mid)) + splitMessages(messages.subList(mid, messages.size))
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText(maxLength = 2000) }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName,
            )
            val requestMessages = listOf(UIMessage.user(prompt))
            val params = backgroundTextGenerationParams(model)
            ProviderRateLimiter.await(provider = provider, messages = requestMessages, params = params)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = requestMessages,
                params = params,
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
        val compressedConversation = conversation.copy(
            messageNodes = buildList {
                addAll(nodesWithHidden)
                summaryNodes.forEachIndexed { offset, summaryNode -> add(insertAt + offset, summaryNode) }
            },
            chatSuggestions = emptyList(),
        )

        persistCompressedConversation(
            session = session,
            sourceSnapshot = sourceSnapshot,
            compressedConversation = compressedConversation,
        )
    }

    private suspend fun persistCompressedConversation(
        session: ConversationSession,
        sourceSnapshot: ConversationStateSnapshot,
        compressedConversation: Conversation,
    ) {
        session.persistenceMutex.withLock {
            if (!session.matchesSnapshot(sourceSnapshot)) throw CompressionSourceChangedException()

            withContext(NonCancellable) {
                var published = false
                try {
                    persistConversationOnly(compressedConversation)
                    val persistedConversation = conversationRepo.getConversationById(compressedConversation.id)
                        ?: throw IllegalStateException("Compressed conversation could not be reloaded")
                    val previous = session.compareAndSetState(sourceSnapshot, persistedConversation)
                        ?: throw CompressionSourceChangedException()
                    published = true
                    checkFilesDelete(persistedConversation, previous)
                } catch (error: Throwable) {
                    if (!published) {
                        restoreLatestSessionState(session)
                    }
                    throw error
                }
            }
        }
    }

    private suspend fun restoreLatestSessionState(session: ConversationSession) {
        while (true) {
            val latest = session.snapshotState()
            persistConversationOnly(latest.conversation)
            if (session.matchesSnapshot(latest)) return
        }
    }

    private suspend fun persistConversationOnly(conversation: Conversation) {
        if (conversationRepo.existsConversationById(conversation.id)) {
            conversationRepo.updateConversation(conversation)
        } else {
            conversationRepo.insertConversation(conversation)
        }
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        commitConversationState(conversationId, conversation)
    }

    private fun commitConversationState(conversationId: Uuid, newState: Conversation) {
        if (newState.id != conversationId) return
        val previous = getOrCreateSession(conversationId).replaceState(newState) ?: return
        checkFilesDelete(newState, previous)
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val result = getOrCreateSession(conversationId).updateState(update) ?: return
        val (previous, updated) = result
        checkFilesDelete(updated, previous)
    }

    private fun updateSubagentProgress(
        conversationId: Uuid,
        toolCallId: String?,
        profileName: String,
        contextId: String,
        subMessages: List<UIMessage>,
    ) {
        runCatching {
            if (SubagentSessionRegistry.isCancelRequested(conversationId)) return@runCatching

            val transcript = SubagentHost.buildTranscript(
                subMessages,
                truncateChars = 200,
                truncateToolOutput = 2000,
            )
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
                put("subagent_context_id", JsonPrimitive(contextId))
                put("subagent_context_status", JsonPrimitive(SubagentStatus.RUNNING.name))
            }
            val partialOutputText = buildJsonObject {
                put("profile_name", JsonPrimitive(profileName))
                put("succeeded", JsonPrimitive(false))
                put("streaming", JsonPrimitive(true))
                put("context_id", JsonPrimitive(contextId))
                put("context_status", JsonPrimitive(SubagentStatus.RUNNING.name))
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
     * #89: 把会话移动到另一个助手。除了改 assistantId 之外，
     * 文件夹是助手内分组，切换助手后原文件夹在新助手下不可见，需清空 folderId 避免会话丢失。
     * 同时把该会话中 followSource=true 的对话级记忆文档重绑到新助手的同模板文档；
     * 重绑失败不阻断移动本身。
     */
    suspend fun moveConversationToAssistant(conversationId: Uuid, targetAssistantId: Uuid) {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return
        val oldAssistantId = conversation.assistantId
        val updated = conversation.copy(
            assistantId = targetAssistantId,
            folderId = null,
        )

        if (sessions.containsKey(conversationId)) {
            saveConversation(conversationId, updated)
        } else {
            conversationRepo.updateConversation(updated)
        }

        runCatching {
            memoryTableRepository.relinkFollowReferences(
                conversationId = conversationId.toString(),
                oldAssistantId = oldAssistantId.toString(),
                newAssistantId = targetAssistantId.toString(),
            )
        }.onFailure {
            Log.w(TAG, "moveConversationToAssistant: relink memory table references failed: ${it.message}")
        }
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
        val session = getOrCreateSession(conversationId)
        session.persistenceMutex.withLock {
            val exists = conversationRepo.existsConversationById(conversation.id)
            if (
                !exists &&
                conversation.title.isBlank() &&
                conversation.messageNodes.isEmpty() &&
                conversation.chatModelId == null
            ) {
                return@withLock // 新会话且为空时不保存
            }

            val updatedConversation = conversation.copy()
            updateConversation(conversationId, updatedConversation)
            if (!exists) {
                conversationRepo.insertConversation(updatedConversation)
            } else {
                conversationRepo.updateConversation(updatedConversation)
            }
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
            chatModelId = currentConversation.chatModelId,
            messageNodes = copiedNodes,
            customSystemPrompt = currentConversation.customSystemPrompt,
            modeInjectionIds = currentConversation.modeInjectionIds,
            lorebookIds = currentConversation.lorebookIds,
        )

        conversationRepo.insertForkConversation(
            sourceConversationId = conversationId,
            fork = forkConversation,
        )
        updateConversation(forkConversation.id, forkConversation)

        // #89: fork 时把源会话的对话级记忆表文档复制到新会话，复制失败不影响 fork 本身。
        runCatching {
            memoryTableRepository.copyDocumentsToScope(
                fromScopeType = MemoryTableScopeType.CONVERSATION,
                fromScopeId = conversationId.toString(),
                toScopeType = MemoryTableScopeType.CONVERSATION,
                toScopeId = forkConversation.id.toString(),
            )
        }.onFailure {
            Log.w(TAG, "forkConversationAtMessage: copy memory table documents failed: ${it.message}")
        }

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
            spawn = { profileName, task, _, reuseContextId ->
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
                        reuseContextId = reuseContextId,
                        onProgress = if (conversationId != null) {
                            { contextId, subMessages ->
                                updateSubagentProgress(
                                    conversationId,
                                    toolCallId,
                                    profile.name,
                                    contextId,
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
            parallelExecutionEnabled = assistant.enableSubagents && assistant.subagentMaxConcurrent > 1,
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
                val privateSkillMounts = assistantPrivateSkillMounts(assistant.id)
                createSubagentWorkspaceTools(
                    access = access,
                    profile = profile,
                    workspaceRepository = workspaceRepository,
                    workspaceId = workspaceId,
                    workspaceCwd = workspaceCwd,
                    knownMounts = listOf(
                        WorkspaceKnownMount(
                            target = "/skills",
                            source = skillManager.getSkillsDir(),
                            allowedSymlinkRoots = listOf(skillManager.getSkillSharedDir()),
                        )
                    ) + privateSkillMounts.knownMounts,
                    extraBindMounts = privateSkillMounts.bindMounts,
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
                        spawn = { nestedProfile, nestedTask, _, reuseContextId ->
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
                                    reuseContextId = reuseContextId,
                                    onProgress = if (conversationId != null) {
                                        { contextId, subMessages ->
                                            updateSubagentProgress(
                                                conversationId,
                                                toolCallId,
                                                nested.name,
                                                contextId,
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
                        parallelExecutionEnabled = assistant.enableSubagents && assistant.subagentMaxConcurrent > 1,
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
        subagentHost.requestCancel(conversationId, SUBAGENT_USER_CANCEL_REASON)
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
