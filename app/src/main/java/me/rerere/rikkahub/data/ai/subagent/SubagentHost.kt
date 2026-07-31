package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ShellChangedFilesMetadata
import me.rerere.ai.ui.metadataAs
import me.rerere.ai.util.HttpException
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.WORKSPACE_SHELL_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.workspaceShellTranscriptInput
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.workspace.normalizeWorkspaceChangedFiles
import kotlin.uuid.Uuid

private const val TAG = "SubagentHost"
private const val SUBAGENT_INTERNAL_GENERATION_LOOP_LIMIT = 257
private const val SUBAGENT_SUMMARY_GENERATION_LOOP_LIMIT = 2
private const val DEFAULT_SUBAGENT_COUNTDOWN_THRESHOLD = 4
internal const val SUBAGENT_USER_CANCEL_REASON = "Generation cancelled by user"
internal const val SUBAGENT_STOPPED_REASON = "Generation stopped before subagent completion"

internal const val SUMMARY_CONTINUATION_PROMPT =
    "Your previous response was too brief. Please provide a more comprehensive summary of your findings and actions taken. " +
        "Include key details, file paths found, and specific conclusions."

private const val TOOL_BUDGET_SUMMARY_PROMPT =
    "The subagent tool-call budget has been reached. Do not call any more tools. " +
        "Provide a concise final text summary of the work completed so far, including key findings, actions taken, " +
        "and any limitations caused by the budget."

private object ToolCallBudgetStop : Exception()

internal enum class SubagentFailureDisposition {
    INTERRUPTED,
    FAILED,
    CONTEXT_TOO_LONG,
    TIMED_OUT,
}

internal fun classifySubagentFailure(
    error: Throwable,
    reusedContext: Boolean,
): SubagentFailureDisposition {
    if (reusedContext && isContextTooLongError(error)) {
        return SubagentFailureDisposition.CONTEXT_TOO_LONG
    }
    if (error is TimeoutCancellationException || error.javaClass.simpleName.contains("Timeout")) {
        return SubagentFailureDisposition.TIMED_OUT
    }
    if (error is CancellationException) return SubagentFailureDisposition.INTERRUPTED

    var current: Throwable? = error
    while (current != null) {
        if (current is TimeoutCancellationException || current.javaClass.simpleName.contains("Timeout")) {
            return SubagentFailureDisposition.TIMED_OUT
        }
        if (current is IOException || current is HttpException) {
            return SubagentFailureDisposition.INTERRUPTED
        }
        current = current.cause
    }
    return SubagentFailureDisposition.FAILED
}

private fun isContextTooLongError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val message = current.message.orEmpty().lowercase()
        if (
            "context_length_exceeded" in message ||
            "maximum context length" in message ||
            "context window" in message && ("exceed" in message || "too long" in message) ||
            "input is too long" in message
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun subagentPermissionFingerprint(profile: SubagentProfile, parent: Assistant): String = buildString {
    append(profile.workspaceAccess.name)
    append('|').append(profile.workspaceApproval.name)
    append('|').append(profile.allowedPathPrefixes.sorted().joinToString(","))
    append('|').append(profile.toolApprovalOverrides.toSortedMap().entries.joinToString(","))
    append('|').append(profile.inheritTools)
    append('|').append(profile.excludedTools.sorted().joinToString(","))
    append('|').append(profile.canSpawn)
    val localTools = if (profile.inheritTools) {
        parent.localTools + profile.extraLocalTools
    } else {
        profile.localTools
    }
    append('|').append(localTools.map { it.toString() }.sorted().joinToString(","))
    val skills = if (profile.inheritTools) parent.enabledSkills else profile.enabledSkills
    append('|').append(skills.sorted().joinToString(","))
    val mcpServers = if (profile.inheritTools) parent.mcpServers else profile.mcpServerIds
    append('|').append(mcpServers.map { it.toString() }.sorted().joinToString(","))
}

internal suspend fun persistSubagentFailure(
    cache: SubagentContextCache,
    contextId: String,
    status: SubagentStatus,
    fallbackMessages: List<UIMessage>,
    fallbackUsage: TokenUsage?,
    error: String,
): SubagentContext? = withContext(NonCancellable) {
    require(status != SubagentStatus.RUNNING) { "failure status must be terminal" }
    val cached = cache.snapshot(contextId)
    cache.finish(
        contextId = contextId,
        status = status,
        messages = cached?.messages ?: fallbackMessages,
        usage = cached?.usage ?: fallbackUsage,
        error = error,
    )
}

internal object SubagentSessionRegistry {
    private val activeSessions = ConcurrentHashMap<Uuid, AtomicInteger>()
    private val cancelFlags = ConcurrentHashMap<Uuid, AtomicBoolean>()
    private val cancelReasons = ConcurrentHashMap<Uuid, String>()

    fun register(conversationId: Uuid) {
        activeSessions.compute(conversationId) { _, existing ->
            if (existing == null) {
                cancelReasons.remove(conversationId)
                cancelFlags[conversationId] = AtomicBoolean(false)
                AtomicInteger(1)
            } else {
                existing.incrementAndGet()
                existing
            }
        }
    }

    fun unregister(conversationId: Uuid) {
        activeSessions.computeIfPresent(conversationId) { _, count ->
            if (count.decrementAndGet() <= 0) {
                cancelFlags.remove(conversationId)
                cancelReasons.remove(conversationId)
                null
            } else {
                count
            }
        }
    }

    fun requestCancel(conversationId: Uuid, reason: String = SUBAGENT_STOPPED_REASON) {
        var applied = false
        activeSessions.computeIfPresent(conversationId) { _, count ->
            cancelReasons[conversationId] = reason
            cancelFlags.computeIfAbsent(conversationId) { AtomicBoolean(false) }.set(true)
            applied = true
            count
        }
        if (!applied) {
            cancelFlags.remove(conversationId)
            cancelReasons.remove(conversationId)
        }
    }

    fun isCancelRequested(conversationId: Uuid?): Boolean =
        conversationId != null && cancelFlags[conversationId]?.get() == true

    fun cancelReason(conversationId: Uuid?): String? =
        conversationId?.let { cancelReasons[it] }
}

internal fun selectContinuationTools(childTools: List<Tool>): List<Tool> = emptyList()

internal fun resolveSubagentCountdownThreshold(maxToolCalls: Int, configuredThreshold: Int?): Int? {
    val threshold = when {
        configuredThreshold == null -> DEFAULT_SUBAGENT_COUNTDOWN_THRESHOLD
        configuredThreshold <= 0 -> return null
        else -> configuredThreshold
    }
    return threshold.coerceAtMost(maxToolCalls.coerceIn(1, 256))
}

internal data class SubagentContextAcquisition(
    val context: SubagentContext,
    val reusedContext: Boolean,
)

internal suspend fun acquireSubagentContext(
    cache: SubagentContextCache,
    scope: SubagentContextScope,
    task: String,
    reuseContextId: String?,
): SubagentContextAcquisition {
    val taskMessage = listOf(UIMessage.user(task))
    if (reuseContextId != null) {
        return SubagentContextAcquisition(
            context = cache.acquireForReuse(
                contextId = reuseContextId,
                scope = scope,
                messagesToAppend = taskMessage,
            ),
            reusedContext = true,
        )
    }

    cache.acquireLatestCompleted(scope, taskMessage)?.let { context ->
        return SubagentContextAcquisition(context = context, reusedContext = true)
    }
    return SubagentContextAcquisition(
        context = cache.createAndAcquire(scope, taskMessage),
        reusedContext = false,
    )
}

class SubagentHost(
    private val generationHandler: GenerationHandler,
    internal val contextCache: SubagentContextCache = SubagentContextCache(),
    private val memoryTableInjectionLoader: (suspend (
        parentAssistant: Assistant,
        conversationId: Uuid?,
        selectedDocumentIds: Set<String>,
        settings: Settings,
    ) -> SubagentMemoryTableInjectLoad)? = null,
) {
    fun requestCancel(conversationId: Uuid, reason: String = SUBAGENT_STOPPED_REASON) {
        SubagentSessionRegistry.requestCancel(conversationId, reason)
    }

    suspend fun spawn(
        profileName: String,
        task: String,
        settings: Settings,
        parentAssistant: Assistant,
        parentModel: Model,
        buildChildTools: suspend (childAssistant: Assistant, depth: Int) -> List<Tool>,
        depth: Int = 0,
        maxDepth: Int = parentAssistant.subagentMaxDepth,
        workspaceCwd: String? = null,
        conversationId: Uuid? = null,
        reuseContextId: String? = null,
        onProgress: ((String, List<UIMessage>) -> Unit)? = null,
    ): SubagentResult {
        if (conversationId != null) {
            SubagentSessionRegistry.register(conversationId)
        }
        try {
            return spawnBody(
                profileName,
                task,
                settings,
                parentAssistant,
                parentModel,
                buildChildTools,
                depth,
                maxDepth,
                workspaceCwd,
                conversationId,
                reuseContextId,
                onProgress,
            )
        } finally {
            if (conversationId != null) {
                SubagentSessionRegistry.unregister(conversationId)
            }
        }
    }

    private suspend fun spawnBody(
        profileName: String,
        task: String,
        settings: Settings,
        parentAssistant: Assistant,
        parentModel: Model,
        buildChildTools: suspend (childAssistant: Assistant, depth: Int) -> List<Tool>,
        depth: Int,
        maxDepth: Int,
        workspaceCwd: String?,
        conversationId: Uuid?,
        reuseContextId: String?,
        onProgress: ((String, List<UIMessage>) -> Unit)?,
    ): SubagentResult {
        val startedAtEpochMillis = System.currentTimeMillis()
        val profile = SubagentRegistry.resolveProfile(
            profileName,
            parentAssistant,
            settings.globalSubagentProfiles,
        )
            ?: return SubagentResult(
                profileName = profileName,
                summary = "",
                succeeded = false,
                error = "profile not found",
                depth = depth,
            )

        if (depth > maxDepth) {
            return SubagentResult(
                profileName = profile.name,
                summary = "",
                succeeded = false,
                error = "depth limit",
                depth = depth,
            )
        }

        val scope = SubagentContextScope(
            conversationId = conversationId,
            parentAssistantId = parentAssistant.id,
            workspaceId = parentAssistant.workspaceId,
            workspaceCwd = workspaceCwd,
            depth = depth,
            profileName = profile.name,
            workspaceAccess = profile.workspaceAccess,
            permissionFingerprint = subagentPermissionFingerprint(profile, parentAssistant),
        )
        val contextAcquisition = try {
            acquireSubagentContext(
                cache = contextCache,
                scope = scope,
                task = task,
                reuseContextId = reuseContextId,
            )
        } catch (error: SubagentContextException) {
            return SubagentResult(
                profileName = profile.name,
                summary = "",
                succeeded = false,
                error = "${error.code}: ${error.message}",
                depth = depth,
                contextId = reuseContextId,
                contextStatus = if (error.code == SubagentContextErrorCode.CONTEXT_IN_USE) {
                    SubagentStatus.RUNNING
                } else {
                    null
                },
            )
        }
        val acquiredContext = contextAcquisition.context
        val contextId = acquiredContext.contextId
        val effectiveMaxToolCalls = effectiveMaxToolCalls(profile)

        var totalUsage: TokenUsage? = null
        var steps = 0
        var totalToolLoopSteps = 0
        var lastMessages = acquiredContext.messages
        var transferredContext: SubagentTransferredContext? = null

        return runCatching {
            onProgress?.invoke(contextId, acquiredContext.messages)
            val childModel = profile.chatModelId
                ?.let { settings.findModelById(it) }
                ?: parentModel
            val childAssistant = buildChildAssistant(profile, parentAssistant, depth, maxDepth)
            val childTools = sandboxToolsForSubagent(buildChildTools(childAssistant, depth))
            transferredContext = captureTransferredContext(
                profile = profile,
                childAssistant = childAssistant,
                childTools = childTools,
                childModel = childModel,
                workspaceCwd = workspaceCwd,
                reusedContext = contextAcquisition.reusedContext,
            )
            Log.i(TAG, "spawn: subagent '${profile.name}' (depth=$depth) started")

            var generationLimitReached = false
            var messages = acquiredContext.messages
            val memoryTableLoad = if (
                !contextAcquisition.reusedContext &&
                profile.injectedMemoryTableDocumentIds.isNotEmpty()
            ) {
                memoryTableInjectionLoader?.invoke(
                    parentAssistant,
                    conversationId,
                    profile.injectedMemoryTableDocumentIds,
                    settings,
                ) ?: SubagentMemoryTableInjectLoad()
            } else {
                SubagentMemoryTableInjectLoad()
            }
            val memoryTableTransformers = memoryTableLoad.transformers
            val memoryTableInjected = memoryTableTransformers.isNotEmpty()
            val memoryTableSkipReason = when {
                memoryTableInjected -> null
                contextAcquisition.reusedContext -> "reused_context"
                profile.injectedMemoryTableDocumentIds.isEmpty() -> "not_configured"
                else -> "not_resolved" // parent memory table off, isolation, missing docs, loader empty
            }
            transferredContext = checkNotNull(transferredContext).copy(
                memoryTableInjected = memoryTableInjected,
                memoryTableSkipReason = memoryTableSkipReason,
                memoryTableLabels = memoryTableLoad.labels,
            )

            var preAssistantCount = messages.count { it.role == MessageRole.ASSISTANT }
            var run = runToCompletion(
                profile = profile,
                settings = settings,
                model = childModel,
                assistant = childAssistant,
                tools = childTools,
                initialMessages = messages,
                workspaceCwd = workspaceCwd,
                conversationId = conversationId,
                contextId = contextId,
                onProgress = onProgress,
                inputTransformers = memoryTableTransformers,
            )
            steps += 1
            generationLimitReached = run.generationLimitReached
            totalToolLoopSteps += run.messages.count { it.role == MessageRole.ASSISTANT } - preAssistantCount
            totalUsage = mergeUsage(totalUsage, run.usage)
            messages = run.messages
            lastMessages = messages

            var summary = run.summary
            var truncated = run.truncated
            if (run.truncated) {
                preAssistantCount = messages.count { it.role == MessageRole.ASSISTANT }
                val budgetSummaryMessages = messages + UIMessage.user(TOOL_BUDGET_SUMMARY_PROMPT)
                run = runToCompletion(
                    profile = profile,
                    settings = settings,
                    model = childModel,
                    assistant = childAssistant,
                    tools = selectContinuationTools(childTools),
                    initialMessages = budgetSummaryMessages,
                    generationLoopLimit = SUBAGENT_SUMMARY_GENERATION_LOOP_LIMIT,
                    workspaceCwd = workspaceCwd,
                    conversationId = conversationId,
                    contextId = contextId,
                    onProgress = onProgress,
                    enforceToolBudget = false,
                )
                steps += 1
                totalToolLoopSteps += run.messages.count { it.role == MessageRole.ASSISTANT } - preAssistantCount
                totalUsage = mergeUsage(totalUsage, run.usage)
                messages = run.messages
                lastMessages = messages
                summary = run.summary
                truncated = true
            }
            var remainingContinuations = profile.summaryContinuationAttempts
            val minLength = profile.summaryMinLength
            while (remainingContinuations > 0 && minLength > 0 && summary.length < minLength) {
                if (SubagentSessionRegistry.isCancelRequested(conversationId)) {
                    val reason = SubagentSessionRegistry.cancelReason(conversationId)
                        ?: SUBAGENT_STOPPED_REASON
                    throw CancellationException(reason)
                }
                remainingContinuations -= 1
                preAssistantCount = messages.count { it.role == MessageRole.ASSISTANT }
                val continuationMessages = messages + UIMessage.user(SUMMARY_CONTINUATION_PROMPT)
                run = runToCompletion(
                    profile = profile,
                    settings = settings,
                    model = childModel,
                    assistant = childAssistant,
                    tools = selectContinuationTools(childTools),
                    initialMessages = continuationMessages,
                    generationLoopLimit = SUBAGENT_SUMMARY_GENERATION_LOOP_LIMIT,
                    workspaceCwd = workspaceCwd,
                    conversationId = conversationId,
                    contextId = contextId,
                    onProgress = onProgress,
                    enforceToolBudget = false,
                )
                steps += 1
                totalToolLoopSteps += run.messages.count { it.role == MessageRole.ASSISTANT } - preAssistantCount
                totalUsage = mergeUsage(totalUsage, run.usage)
                messages = run.messages
                lastMessages = messages
                summary = run.summary
                truncated = truncated || run.truncated
            }

            val transcript = buildTranscript(messages)
            val result = SubagentResult(
                profileName = profile.name,
                summary = summary.ifBlank { buildFallbackSummary(transcript, generationLimitReached) },
                succeeded = true,
                depth = depth,
                usage = totalUsage,
                steps = steps,
                toolCallCount = countToolCalls(messages),
                toolLoopSteps = totalToolLoopSteps,
                truncated = truncated || generationLimitReached,
                maxToolCalls = effectiveMaxToolCalls,
                transcript = transcript,
                contextId = contextId,
                contextStatus = SubagentStatus.COMPLETED,
                contextCompleteness = if (truncated || generationLimitReached) {
                    SubagentContextCompleteness.BOUNDED_FULL
                } else {
                    SubagentContextCompleteness.FULL
                },
                truncationReason = when {
                    generationLimitReached -> "GENERATION_LIMIT"
                    truncated -> "MODEL_OR_TOOL_BUDGET"
                    else -> null
                },
                startedAtEpochMillis = startedAtEpochMillis,
                endedAtEpochMillis = System.currentTimeMillis(),
                transferredContext = transferredContext,
            )
            contextCache.finish(
                contextId = contextId,
                status = SubagentStatus.COMPLETED,
                messages = messages,
                usage = totalUsage,
            )
            logResult(result)
            result
        }.onFailure {
            if (it !is CancellationException) {
                Log.e(TAG, "spawn: subagent '${profile.name}' failed (${it.javaClass.simpleName})")
            }
        }.getOrElse { failure ->
            val disposition = classifySubagentFailure(
                failure,
                reusedContext = contextAcquisition.reusedContext,
            )
            val status = when (disposition) {
                SubagentFailureDisposition.INTERRUPTED -> SubagentStatus.INTERRUPTED
                SubagentFailureDisposition.TIMED_OUT -> SubagentStatus.TIMED_OUT
                else -> SubagentStatus.FAILED
            }
            val error = if (disposition == SubagentFailureDisposition.CONTEXT_TOO_LONG) {
                "${SubagentContextErrorCode.CONTEXT_TOO_LONG}: cached subagent context is too long for the provider"
            } else {
                failure.message ?: failure.javaClass.name
            }
            val persisted = persistSubagentFailure(
                cache = contextCache,
                contextId = contextId,
                status = status,
                fallbackMessages = lastMessages,
                fallbackUsage = totalUsage,
                error = error,
            )
            lastMessages = persisted?.messages ?: lastMessages
            totalUsage = persisted?.usage ?: totalUsage
            if (failure is CancellationException && disposition != SubagentFailureDisposition.TIMED_OUT) {
                throw failure
            }
            SubagentResult(
                profileName = profile.name,
                summary = "",
                succeeded = false,
                error = error,
                depth = depth,
                usage = totalUsage,
                steps = steps,
                toolCallCount = countToolCalls(lastMessages),
                toolLoopSteps = totalToolLoopSteps,
                maxToolCalls = effectiveMaxToolCalls,
                transcript = buildTranscript(lastMessages),
                contextId = contextId,
                contextStatus = status,
                contextCompleteness = if (lastMessages.isEmpty()) {
                    SubagentContextCompleteness.UNAVAILABLE
                } else {
                    SubagentContextCompleteness.PARTIAL
                },
                truncationReason = error,
                startedAtEpochMillis = startedAtEpochMillis,
                endedAtEpochMillis = System.currentTimeMillis(),
                transferredContext = transferredContext,
            )
        }
    }

    private fun captureTransferredContext(
        profile: SubagentProfile,
        childAssistant: Assistant,
        childTools: List<Tool>,
        childModel: Model,
        workspaceCwd: String?,
        reusedContext: Boolean,
    ): SubagentTransferredContext {
        val childCanSpawn = childAssistant.enableSubagents
        return SubagentTransferredContext(
            systemPrompt = childAssistant.systemPrompt,
            workspaceAccess = profile.workspaceAccess.name,
            workspaceApproval = profile.workspaceApproval.name,
            canSpawn = childCanSpawn,
            inheritTools = profile.inheritTools,
            excludedTools = profile.excludedTools.sorted(),
            allowedPathPrefixes = profile.allowedPathPrefixes,
            childToolNames = childTools.map { it.name }.distinct().sorted(),
            skills = childAssistant.enabledSkills.sorted(),
            mcpServerIds = childAssistant.mcpServers.map { it.toString() }.sorted(),
            modelId = childModel.modelId.takeIf { it.isNotBlank() }
                ?: childModel.id.toString(),
            cwd = workspaceCwd,
            enableMemory = profile.enableMemory,
            injectedMemoryTableDocumentIds = profile.injectedMemoryTableDocumentIds.sorted(),
            includesParentHistory = false,
            reusedContext = reusedContext,
        )
    }

    suspend fun askBtw(
        question: String,
        settings: Settings,
        parentAssistant: Assistant,
        parentModel: Model,
        workspaceCwd: String? = null,
    ): String {
        val assistant = parentAssistant.copy(
            systemPrompt = parentAssistant.systemPrompt,
            presetMessages = emptyList(),
        )
        val messages = listOf(UIMessage.user(question))
        val finalMessages = generationHandler.generateText(
            settings = settings,
            model = parentModel,
            messages = messages,
            assistant = assistant,
            tools = emptyList(),
            maxSteps = 1,
            workspaceCwd = workspaceCwd,
        ).fold(messages) { _, chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> chunk.messages
            }
        }
        return lastAssistantText(finalMessages).ifBlank { "(no answer)" }
    }

    private suspend fun runToCompletion(
        profile: SubagentProfile,
        settings: Settings,
        model: Model,
        assistant: Assistant,
        tools: List<Tool>,
        initialMessages: List<UIMessage>,
        generationLoopLimit: Int = effectiveGenerationLoopLimit(profile),
        workspaceCwd: String?,
        conversationId: Uuid? = null,
        contextId: String,
        onProgress: ((String, List<UIMessage>) -> Unit)?,
        enforceToolBudget: Boolean = true,
        inputTransformers: List<me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer> = emptyList(),
    ): RunCompletion {
        var lastEmitTime = 0L
        var lastSignature = -1
        val minIntervalMs = 120L
        val throttledOnProgress: ((List<UIMessage>) -> Unit)? = onProgress?.let { cb ->
            { messages ->
                val signature = messages.sumOf { msg ->
                    if (msg.role == MessageRole.ASSISTANT) msg.parts.size else 0
                }
                val now = System.currentTimeMillis()
                if (signature != lastSignature || now - lastEmitTime >= minIntervalMs) {
                    lastSignature = signature
                    lastEmitTime = now
                    cb(contextId, messages)
                }
            }
        }
        var finalMessages = initialMessages
        var truncated = false
        val maxToolCalls = effectiveMaxToolCalls(profile)
        try {
            generationHandler.generateText(
                settings = settings,
                model = model,
                messages = initialMessages,
                assistant = assistant,
                tools = tools,
                maxSteps = generationLoopLimit,
                stepsCountdownThreshold = if (enforceToolBudget) {
                    subagentCountdownThreshold(profile, assistant)
                } else {
                    null
                },
                stepsCountdownTotal = effectiveMaxToolCalls(profile),
                stepsCountdownLabel = "Tool calls",
                memories = emptyList(),
                workspaceCwd = workspaceCwd,
                inputTransformers = inputTransformers,
            ).onEach { chunk ->
                if (chunk is GenerationChunk.Messages) {
                    finalMessages = chunk.messages
                    contextCache.updateProgress(
                        contextId = contextId,
                        messages = chunk.messages,
                        usage = accumulateUsage(chunk.messages),
                    )
                    throttledOnProgress?.invoke(chunk.messages)
                    if (SubagentSessionRegistry.isCancelRequested(conversationId)) {
                        throw CancellationException(
                            SubagentSessionRegistry.cancelReason(conversationId)
                                ?: SUBAGENT_STOPPED_REASON,
                        )
                    }
                    if (enforceToolBudget &&
                        !profile.disableToolBudgetStop &&
                        countExecutedToolCalls(finalMessages) >= maxToolCalls
                    ) {
                        truncated = true
                        throw ToolCallBudgetStop
                    }
                }
            }.collect { }
        } catch (_: ToolCallBudgetStop) {
        }
        contextCache.updateProgress(
            contextId = contextId,
            messages = finalMessages,
            usage = accumulateUsage(finalMessages),
        )
        onProgress?.invoke(contextId, finalMessages)

        val assistantDelta = finalMessages.count { it.role == MessageRole.ASSISTANT } -
            initialMessages.count { it.role == MessageRole.ASSISTANT }
        val generationLimitReached = assistantDelta >= generationLoopLimit

        return RunCompletion(
            messages = finalMessages,
            summary = lastAssistantText(finalMessages),
            usage = accumulateUsage(finalMessages),
            truncated = truncated,
            generationLimitReached = generationLimitReached,
        )
    }

    private fun buildChildAssistant(
        profile: SubagentProfile,
        parent: Assistant,
        depth: Int,
        maxDepth: Int,
    ): Assistant {
        val localTools = buildList {
            if (profile.inheritTools) {
                addAll(parent.localTools)
                addAll(profile.extraLocalTools)
            } else {
                addAll(profile.localTools)
            }
            removeAll { it == LocalToolOption.AskUser }
        }.distinct()

        val childCanSpawn = profile.canSpawn && (depth + 1) <= maxDepth

        return parent.copy(
            id = Uuid.random(),
            name = profile.name,
            systemPrompt = profile.systemPrompt,
            chatModelId = profile.chatModelId ?: parent.chatModelId,
            temperature = profile.temperature ?: parent.temperature,
            topP = profile.topP ?: parent.topP,
            maxTokens = profile.maxTokens ?: parent.maxTokens,
            reasoningLevel = profile.reasoningLevel,
            contextMessageLimit = 0,
            streamOutput = profile.streamOutput || parent.streamOutput,
            enableMemory = profile.enableMemory,
            useGlobalMemory = false,
            enableRecentChatsReference = false,
            allowConversationSystemPrompt = false,
            allowConversationPromptInjection = false,
            enableTimeReminder = false,
            modeInjectionIds = emptySet(),
            presetIds = profile.presetIds,
            lorebookIds = emptySet(),
            workspaceId = parent.workspaceId,
            enableSubagents = childCanSpawn,
            subagentMaxDepth = maxDepth,
            parallelToolExecution = parent.parallelToolExecution,
            subagentMaxConcurrent = parent.subagentMaxConcurrent,
            stepsCountdownThreshold = parent.stepsCountdownThreshold,
            localTools = localTools,
            mcpServers = if (profile.inheritTools) parent.mcpServers else profile.mcpServerIds,
            enabledSkills = if (profile.inheritTools) parent.enabledSkills else profile.enabledSkills,
            presetMessages = emptyList(),
            quickMessageIds = emptySet(),
            regexes = emptyList(),
        )
    }

    private fun lastAssistantText(messages: List<UIMessage>): String {
        for (message in messages.asReversed()) {
            if (message.role != MessageRole.ASSISTANT) continue
            val text = message.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }
            if (text.isNotBlank()) return text.trim()
        }
        return ""
    }

    /**
     * 统计子代理本次运行累计调用的工具次数（供父代理审计"是否真干了活"）。
     * 计所有 assistant 消息中是 [UIMessagePart.Tool] 的 part 数；区别于 generation 轮次 [steps]。
     */
    private fun countToolCalls(messages: List<UIMessage>): Int {
        var n = 0
        for (message in messages) {
            if (message.role != MessageRole.ASSISTANT) continue
            n += message.parts.count { it is UIMessagePart.Tool }
        }
        return n
    }

    private fun countExecutedToolCalls(messages: List<UIMessage>): Int {
        var n = 0
        for (message in messages) {
            if (message.role != MessageRole.ASSISTANT) continue
            n += message.parts.count { it is UIMessagePart.Tool && it.isExecuted }
        }
        return n
    }

    private fun accumulateUsage(messages: List<UIMessage>): TokenUsage? {
        var acc: TokenUsage? = null
        for (message in messages) {
            val u = message.usage ?: continue
            acc = acc.merge(u)
        }
        return acc
    }

    private fun mergeUsage(acc: TokenUsage?, other: TokenUsage?): TokenUsage? {
        if (acc == null) return other
        if (other == null) return acc
        return acc.merge(other)
    }

    private fun logResult(result: SubagentResult) {
        val u = result.usage
        if (u != null) {
            Log.i(
                TAG,
                "spawn: subagent '${result.profileName}' (depth=${result.depth}) " +
                    "finished in ${result.steps} step(s); total=${u.totalTokens}",
            )
        } else {
            Log.i(
                TAG,
                "spawn: subagent '${result.profileName}' (depth=${result.depth}) " +
                    "finished in ${result.steps} step(s)",
            )
        }
    }

    private fun effectiveMaxToolCalls(profile: SubagentProfile): Int =
        (profile.maxToolCalls ?: 32).coerceIn(1, 256)

    private fun effectiveGenerationLoopLimit(profile: SubagentProfile): Int =
        if (profile.disableToolBudgetStop) {
            SUBAGENT_INTERNAL_GENERATION_LOOP_LIMIT
        } else {
            effectiveMaxToolCalls(profile) + 1
        }

    private fun subagentCountdownThreshold(profile: SubagentProfile, assistant: Assistant): Int? =
        resolveSubagentCountdownThreshold(effectiveMaxToolCalls(profile), assistant.stepsCountdownThreshold)

    private data class RunCompletion(
        val messages: List<UIMessage>,
        val summary: String,
        val usage: TokenUsage?,
        val truncated: Boolean = false,
        val generationLimitReached: Boolean = false,
    )

    companion object {
        fun buildTranscript(
            messages: List<UIMessage>,
            truncateChars: Int = 200,
            truncateToolOutput: Int = 2000,
        ): List<SubagentTranscriptStep> {
            val steps = mutableListOf<SubagentTranscriptStep>()
            for (message in messages) {
                if (message.role != MessageRole.ASSISTANT) continue
                for (part in message.parts) {
                    when (part) {
                        is UIMessagePart.Reasoning -> {
                            if (part.reasoning.isNotBlank()) {
                                steps.add(
                                    SubagentTranscriptStep.Reasoning(
                                        text = part.reasoning,
                                        createdAt = part.createdAt.toEpochMilliseconds(),
                                    ),
                                )
                            }
                        }

                        is UIMessagePart.Tool -> {
                            val textOutputs = part.output.filterIsInstance<UIMessagePart.Text>()
                            val outputText = textOutputs
                                .joinToString("\n") { it.text }
                            val changedFiles = if (part.toolName == WORKSPACE_SHELL_TOOL_NAME) {
                                normalizeWorkspaceChangedFiles(textOutputs.flatMap { outputPart ->
                                    outputPart.metadataAs<ShellChangedFilesMetadata>()?.changedFiles.orEmpty()
                                })
                            } else {
                                emptyList()
                            }
                            val toolOutputLimit = when {
                                truncateToolOutput > 0 -> truncateToolOutput
                                truncateChars > 0 -> truncateChars
                                else -> 0
                            }
                            val output = when {
                                part.toolName == WORKSPACE_SHELL_TOOL_NAME ->
                                    truncateShellOutputJson(outputText, toolOutputLimit)
                                toolOutputLimit > 0 -> truncate(outputText, toolOutputLimit)
                                else -> outputText
                            }
                            steps.add(
                                SubagentTranscriptStep.ToolCall(
                                    toolName = part.toolName,
                                    input = compactTranscriptToolInput(part.toolName, part.input, truncateChars),
                                    output = output,
                                    executed = part.isExecuted,
                                    changedFiles = changedFiles,
                                ),
                            )
                        }

                        is UIMessagePart.Text -> {
                            if (part.text.isNotBlank()) {
                                steps.add(SubagentTranscriptStep.Text(part.text.trim()))
                            }
                        }

                        else -> {}
                    }
                }
            }
            return steps
        }

        fun buildFallbackSummary(
            transcript: List<SubagentTranscriptStep>,
            generationLimitReached: Boolean = false,
        ): String {
            if (transcript.isEmpty()) {
                return if (generationLimitReached) {
                    "(subagent reached its internal generation limit with no output)"
                } else {
                    "(subagent produced no output)"
                }
            }
            val sb = StringBuilder()
            if (generationLimitReached) {
                sb.appendLine("(Subagent reached its internal generation limit — auto-generated summary from transcript)")
            } else {
                sb.appendLine("(Subagent produced no text summary — auto-generated from transcript)")
            }
            sb.appendLine()
            var toolCount = 0
            for (step in transcript) {
                when (step) {
                    is SubagentTranscriptStep.Text -> {
                        val trimmed = step.content.take(500)
                        sb.appendLine(trimmed)
                        sb.appendLine()
                    }
                    is SubagentTranscriptStep.ToolCall -> {
                        toolCount++
                        if (toolCount <= 15) {
                            val outputPreview = step.output.take(300)
                            sb.appendLine("[${step.toolName}] ${step.input.take(200)} → $outputPreview")
                        }
                    }
                    is SubagentTranscriptStep.Reasoning -> {
                    }
                }
            }
            if (toolCount > 15) {
                sb.appendLine("... and ${toolCount - 15} more tool calls")
            }
            return sb.toString().take(4000)
        }

        // REVIEWED: no longer clears needsApproval; approval flows through SubagentPermissionBuilder
        fun sandboxToolsForSubagent(tools: List<Tool>): List<Tool> = tools

        private fun compactTranscriptToolInput(toolName: String, input: String, maxChars: Int): String =
            if (toolName == WORKSPACE_SHELL_TOOL_NAME) {
                workspaceShellTranscriptInput(input, maxChars)
            } else {
                truncate(input, maxChars)
            }

        /**
         * workspace_shell 的输出是含 stdout/stderr 的 JSON。若像普通文本那样按字符数硬截断,
         * 会截在 JSON 中间产生非法 JSON, 使 UI 解析失败而彻底丢失 stdout (见 issue #66)。
         * 改为解析后对 stdout/stderr 分字段各自截断再重新序列化, 保证结果始终是合法 JSON,
         * 这样 ShellToolUI 仍能读取 stdout/stderr/exitCode 字段并正确展示。
         */
        private fun truncateShellOutputJson(outputText: String, limit: Int): String {
            if (limit <= 0 || outputText.length <= limit) return outputText
            val obj = runCatching { Json.parseToJsonElement(outputText).jsonObject }.getOrNull()
                ?: return truncate(outputText, limit)
            if (!obj.containsKey("stdout") && !obj.containsKey("stderr")) {
                return truncate(outputText, limit)
            }
            return buildJsonObject {
                obj["exitCode"]?.let { put("exitCode", it) }
                obj["stdout"]?.jsonPrimitive?.contentOrNull?.let { put("stdout", truncate(it, limit)) }
                obj["stderr"]?.jsonPrimitive?.contentOrNull?.let { put("stderr", truncate(it, limit)) }
                obj["timedOut"]?.let { put("timedOut", it) }
                obj["truncated"]?.let { put("truncated", it) }
            }.toString()
        }

        private fun truncate(text: String, max: Int): String =
            if (max <= 0 || text.length <= max) text else text.take(max) + "…"
    }
}
