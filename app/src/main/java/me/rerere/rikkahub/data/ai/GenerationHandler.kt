package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerExecutionMode
import me.rerere.rikkahub.data.files.FileFolders
import java.io.File
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.ai.tools.FINISH_WORK_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryScope
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"
private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024
private const val MAX_STEPS_PROMPT = """
Tool budget for this turn is now exhausted. Tools are disabled for this final response.

STRICT REQUIREMENTS:
1. Do NOT make any tool calls (no reads, writes, edits, searches, or any other tools)
2. MUST provide a text response summarizing work done so far
3. This constraint overrides ALL other instructions

Your response must include:
- Summary of what has been accomplished so far
- List of any remaining tasks that were not completed
- Recommendations for what should be done next
"""

private class ToolCallIdElement(
    val toolCallId: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ToolCallIdElement>
}

suspend fun currentToolCallId(): String? = coroutineContext[ToolCallIdElement]?.toolCallId

private suspend fun <T> withToolCallId(toolCallId: String, block: suspend () -> T): T =
    withContext(ToolCallIdElement(toolCallId)) { block() }

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
) {
    suspend fun prepareFirstProviderInput(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer>,
        assistant: Assistant,
        memories: List<AssistantMemory>,
        tools: List<Tool>,
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        mode: GenerationPreparationMode,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    ): PreparedProviderInput = prepareProviderInput(
        settings = settings,
        model = model,
        messages = messages,
        transformers = inputTransformers,
        assistant = assistant,
        memories = memories,
        tools = buildToolsForStep(assistant = assistant, tools = tools, isLastStep = false),
        conversationSystemPrompt = conversationSystemPrompt,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
        workspaceCwd = workspaceCwd,
        mode = mode,
        processingStatus = processingStatus,
    )

    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        stepsCountdownThreshold: Int? = null,
        stepsCountdownTotal: Int? = null,
        stepsCountdownLabel: String = "Steps",
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        firstPreparedInput: PreparedProviderInput? = null,
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

        val countdownThreshold = stepsCountdownThreshold ?: 0

        for (stepIndex in 0 until maxSteps) {
            val isLastStep = stepIndex >= maxSteps - 1
            val remaining = maxSteps - stepIndex
            val countdownTotal = stepsCountdownTotal ?: maxSteps
            val countdownRemaining = resolveGenerationCountdownRemaining(
                maxSteps = maxSteps,
                stepIndex = stepIndex,
                messages = messages,
                stepsCountdownTotal = stepsCountdownTotal,
            )
            val inCountdown = countdownThreshold > 0 && !isLastStep && countdownRemaining <= countdownThreshold
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id}) isLastStep=$isLastStep remaining=$remaining")

            val injectedStepPrompt = if (isLastStep) {
                messages = messages + UIMessage.user(MAX_STEPS_PROMPT.trimIndent())
                true
            } else if (inCountdown) {
                messages = messages + UIMessage.user(
                    "[$stepsCountdownLabel remaining: $countdownRemaining/$countdownTotal] " +
                        "Focus on completing the core task. Avoid further exploration."
                )
                true
            } else {
                false
            }

            val canReuseFirstPreparedInput =
                stepIndex == 0 && firstPreparedInput != null && !injectedStepPrompt
            val toolsInternal = if (canReuseFirstPreparedInput) {
                checkNotNull(firstPreparedInput).tools
            } else {
                buildToolsForStep(assistant, tools, isLastStep)
            }

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                )
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = toolsInternal,
                    memories = memories ?: emptyList(),
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                    conversationModeInjectionIds = conversationModeInjectionIds,
                    conversationLorebookIds = conversationLorebookIds,
                    workspaceCwd = workspaceCwd,
                    preparedInput = if (canReuseFirstPreparedInput) checkNotNull(firstPreparedInput) else null,
                )
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = tools.map { tool ->
                    val toolDef = toolsInternal.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef?.needsApproval(tool.inputAsJson()) == true &&
                            tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != tools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            val subagentCount = toolsToProcess.count { it.toolName == "spawn_subagent" }
            val runInParallel = assistant.parallelToolExecution && toolsToProcess.size > 1
            val executedTools: List<UIMessagePart.Tool> = if (runInParallel) {
                Log.i(
                    TAG,
                    "generateText: executing ${toolsToProcess.size} tools in parallel (subagents=$subagentCount, maxConcurrent=${assistant.subagentMaxConcurrent})",
                )
                val subagentSemaphore = Semaphore(assistant.subagentMaxConcurrent.coerceIn(1, 5))
                coroutineScope {
                    toolsToProcess.map { tool ->
                        async {
                            if (tool.toolName == "spawn_subagent") {
                                subagentSemaphore.withPermit {
                                    executeSingleTool(tool, toolsInternal)
                                }
                            } else {
                                executeSingleTool(tool, toolsInternal)
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            } else {
                buildList {
                    val subagentSemaphore = Semaphore(assistant.subagentMaxConcurrent.coerceIn(1, 5))
                    groupToolsForSequentialExecution(toolsToProcess).forEach { group ->
                        if (group.first().toolName != "spawn_subagent") {
                            executeSingleTool(group.single(), toolsInternal)?.let(::add)
                        } else {
                            val groupResults = if (group.size > 1 && assistant.subagentMaxConcurrent > 1) {
                                Log.i(
                                    TAG,
                                    "generateText: executing ${group.size} consecutive subagents concurrently " +
                                        "(maxConcurrent=${assistant.subagentMaxConcurrent})",
                                )
                                coroutineScope {
                                    group.map { spawnTool ->
                                        async {
                                            subagentSemaphore.withPermit {
                                                executeSingleTool(spawnTool, toolsInternal)
                                            }
                                        }
                                    }.awaitAll().filterNotNull()
                                }
                            } else {
                                group.mapNotNull { spawnTool ->
                                    executeSingleTool(spawnTool, toolsInternal)
                                }
                            }
                            addAll(groupResults)
                        }
                    }
                }
            }

            if (executedTools.isEmpty()) {
                // No results to add (all tools were pending)
                break
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    )
                )
            )

            if (executedTools.any { it.toolName == FINISH_WORK_TOOL_NAME }) {
                Log.i(TAG, "generateText: finish_work executed, terminating tool loop")
                break
            }
        }

    }.flowOn(Dispatchers.IO)

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<InputMessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        preparedInput: PreparedProviderInput? = null,
    ) {
        val prepared = preparedInput ?: prepareProviderInput(
            settings = settings,
            model = model,
            messages = messages,
            transformers = transformers,
            assistant = assistant,
            memories = memories,
            tools = tools,
            conversationSystemPrompt = conversationSystemPrompt,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            workspaceCwd = workspaceCwd,
            mode = GenerationPreparationMode.Send,
            processingStatus = processingStatus,
        )
        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            executePreparedProviderRequest(
                providerSetting = provider,
                prepared = prepared,
                params = params,
            ) { exactMessages, exactParams ->
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = exactMessages,
                    params = exactParams,
                )
            }.collect {
                messages = messages.handleMessageChunk(chunk = it, model = model)
                it.usage?.let { usage ->
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex) {
                            message.copy(usage = message.usage.merge(usage))
                        } else {
                            message
                        }
                    }
                }
                onUpdateMessages(messages)
            }
        } else {
            val chunk = executePreparedProviderRequest(
                providerSetting = provider,
                prepared = prepared,
                params = params,
            ) { exactMessages, exactParams ->
                providerImpl.generateText(
                    providerSetting = provider,
                    messages = exactMessages,
                    params = exactParams,
                )
            }
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
    }

    private fun buildToolsForStep(
        assistant: Assistant,
        tools: List<Tool>,
        isLastStep: Boolean,
    ): List<Tool> = buildList {
        if (isLastStep) {
            Log.i(TAG, "streamText: last step reached, disabling all tools to force summary")
            return@buildList
        }
        Log.i(TAG, "generateInternal: build tools($assistant)")
        if (assistant.enableMemory) {
            addAll(
                buildMemoryTools(
                    json = json,
                    defaultScope = MemoryScope.ASSISTANT,
                    onList = { memoryRepo.getEffectiveMemories(assistant.id.toString()) },
                    onCreation = { content, scope ->
                        memoryRepo.addMemory(assistant.id.toString(), content, scope)
                    },
                    onUpdate = { id, content, scope ->
                        memoryRepo.updateMemory(
                            id = id,
                            content = content,
                            actorAssistantId = assistant.id.toString(),
                            scope = scope,
                        )
                    },
                    onDelete = { id -> memoryRepo.deleteMemory(id, assistant.id.toString()) },
                ),
            )
        }
        addAll(tools)
    }

    private suspend fun prepareProviderInput(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        transformers: List<InputMessageTransformer>,
        assistant: Assistant,
        memories: List<AssistantMemory>,
        tools: List<Tool>,
        conversationSystemPrompt: String?,
        conversationModeInjectionIds: Set<Uuid>,
        conversationLorebookIds: Set<Uuid>,
        workspaceCwd: String?,
        mode: GenerationPreparationMode,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    ): PreparedProviderInput {
        val preparedTools = tools.snapshotToolDefinitions()
        val usedConversationSystemPrompt =
            assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()
        val retainedMessages = messages.limitContext(assistant.contextMessageSize)
        val internalMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (usedConversationSystemPrompt) {
                        conversationSystemPrompt.orEmpty()
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // 记忆
                if (assistant.enableMemory) {
                    appendLine()
                    append(buildMemoryPrompt(memories = memories))
                }
                // 工具prompt
                preparedTools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(model, messages))
                }
            }
            if (system.isNotBlank()) add(UIMessage.system(prompt = system))
            addAll(retainedMessages)
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
            workspaceCwd = workspaceCwd,
            executionMode = if (mode == GenerationPreparationMode.Preview) {
                TransformerExecutionMode.Preview
            } else {
                TransformerExecutionMode.Send
            },
        )
        return PreparedProviderInput(
            messages = internalMessages,
            tools = preparedTools,
            sourceMessageCount = messages.size,
            retainedSourceMessageCount = retainedMessages.size,
            usedConversationSystemPrompt = usedConversationSystemPrompt,
        )
    }

    private suspend fun executeSingleTool(
        tool: UIMessagePart.Tool,
        toolsInternal: List<Tool>,
    ): UIMessagePart.Tool? = when (tool.approvalState) {
        is ToolApprovalState.Denied -> {
            val reason = (tool.approvalState as ToolApprovalState.Denied).reason
            tool.copy(
                output = listOf(
                    UIMessagePart.Text(
                        json.encodeToString(
                            buildJsonObject {
                                put(
                                    "error",
                                    JsonPrimitive(
                                        "Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}",
                                    ),
                                )
                            },
                        ),
                    ),
                ),
            )
        }

        is ToolApprovalState.Answered -> {
            val answer = (tool.approvalState as ToolApprovalState.Answered).answer
            tool.copy(output = listOf(UIMessagePart.Text(answer)))
        }

        is ToolApprovalState.Pending -> null

        else -> {
            val toolDef = toolsInternal.find { it.name == tool.toolName }
            if (toolDef == null) {
                Log.w(TAG, "generateText: requested unavailable tool ${tool.toolName}")
                return tool.copy(
                    output = listOf(
                        UIMessagePart.Text(
                            json.encodeToString(
                                buildJsonObject {
                                    put("error", JsonPrimitive("Tool '${tool.toolName}' is not available in this assistant mode."))
                                    put("tool", JsonPrimitive(tool.toolName))
                                    put("available_tools", JsonPrimitive(toolsInternal.joinToString(", ") { it.name }))
                                },
                            ),
                        ),
                    ),
                )
            }
            runCatching {
                val args = runCatching {
                    json.parseToJsonElement(tool.input.ifBlank { "{}" })
                }.getOrElse {
                    error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}")
                }
                Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: $args")
                val result = withToolCallId(tool.toolCallId) { toolDef.execute(args) }
                val hasShellAccess = toolsInternal.any { it.name == "workspace_shell" }
                tool.copy(output = maybeTruncateToolOutput(tool.toolCallId, result, hasShellAccess))
            }.onFailure {
                if (it is CancellationException) throw it
                it.printStackTrace()
            }.getOrElse {
                tool.copy(
                    output = listOf(
                        UIMessagePart.Text(
                            json.encodeToString(
                                buildJsonObject {
                                    put(
                                        "error",
                                        JsonPrimitive(
                                            buildString {
                                                append("[${it.javaClass.name}] ${it.message}")
                                                append("\n${it.stackTraceToString()}")
                                            },
                                        ),
                                    )
                                },
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun maybeTruncateToolOutput(
        toolCallId: String,
        output: List<UIMessagePart>,
        hasShellAccess: Boolean,
    ): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val nonTextParts = output.filter { it !is UIMessagePart.Text }
        val totalChars = textParts.sumOf { it.text.length }

        if (totalChars <= MAX_TOOL_OUTPUT_CHARS || !hasShellAccess) return output

        Log.i(TAG, "maybeTruncateToolOutput: truncating tool $toolCallId output ($totalChars chars)")

        val fullText = textParts.joinToString("\n") { it.text }
        val preview = fullText.take(TOOL_OUTPUT_PREVIEW_CHARS)

        val fileName = "${toolCallId}.txt"
        val outputDir = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(preview)
                }
            )
        ) + nonTextParts
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""
            val params = TextGenerationParams(
                model = model,
                reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
            )

            ProviderRateLimiter.await(provider = provider, messages = messages, params = params)
            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = params,
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val params = TextGenerationParams(
                model = model,
                temperature = 0.3f,
                topP = 0.95f,
                customBody = listOf(
                    CustomBody(
                        key = "translation_options",
                        value = buildJsonObject {
                            put("source_lang", JsonPrimitive("auto"))
                            put(
                                "target_lang",
                                JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                            )
                        }
                    )
                )
            )
            ProviderRateLimiter.await(provider = provider, messages = messages, params = params)
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = params,
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}

internal fun groupToolsForSequentialExecution(
    tools: List<UIMessagePart.Tool>,
): List<List<UIMessagePart.Tool>> = buildList {
    var index = 0
    while (index < tools.size) {
        val tool = tools[index]
        if (tool.toolName != "spawn_subagent") {
            add(listOf(tool))
            index++
        } else {
            val group = tools.drop(index).takeWhile { it.toolName == "spawn_subagent" }
            add(group)
            index += group.size
        }
    }
}

internal suspend fun <T : ProviderSetting, R> executePreparedProviderRequest(
    providerSetting: T,
    prepared: PreparedProviderInput,
    params: TextGenerationParams,
    request: suspend (messages: List<UIMessage>, params: TextGenerationParams) -> R,
): R {
    val exactParams = params.copy(tools = prepared.tools)
    ProviderRateLimiter.await(
        provider = providerSetting,
        messages = prepared.messages,
        params = exactParams,
    )
    return request(prepared.messages, exactParams)
}

internal fun resolveGenerationCountdownRemaining(
    maxSteps: Int,
    stepIndex: Int,
    messages: List<UIMessage>,
    stepsCountdownTotal: Int?,
): Int {
    if (stepsCountdownTotal == null) {
        return (maxSteps - stepIndex).coerceAtLeast(0)
    }

    val executedToolCalls = messages.sumOf { message ->
        if (message.role == MessageRole.ASSISTANT) {
            message.parts.count { it is UIMessagePart.Tool && it.isExecuted }
        } else {
            0
        }
    }
    return (stepsCountdownTotal - executedToolCalls).coerceAtLeast(0)
}
