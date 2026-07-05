package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

private const val TAG = "SubagentHost"
private const val SUBAGENT_INTERNAL_GENERATION_LOOP_LIMIT = 257
private const val SUBAGENT_SUMMARY_GENERATION_LOOP_LIMIT = 2
private const val DEFAULT_SUBAGENT_COUNTDOWN_THRESHOLD = 4

internal const val SUMMARY_CONTINUATION_PROMPT =
    "Your previous response was too brief. Please provide a more comprehensive summary of your findings and actions taken. " +
        "Include key details, file paths found, and specific conclusions."

private const val TOOL_BUDGET_SUMMARY_PROMPT =
    "The subagent tool-call budget has been reached. Do not call any more tools. " +
        "Provide a concise final text summary of the work completed so far, including key findings, actions taken, " +
        "and any limitations caused by the budget."

private object ToolCallBudgetStop : Exception()

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

    fun requestCancel(conversationId: Uuid, reason: String = "Generation cancelled by user") {
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

class SubagentHost(
    private val generationHandler: GenerationHandler,
) {
    fun requestCancel(conversationId: Uuid, reason: String = "Generation cancelled by user") {
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
        onProgress: ((List<UIMessage>) -> Unit)? = null,
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
        onProgress: ((List<UIMessage>) -> Unit)?,
    ): SubagentResult {
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

        val childModel = profile.chatModelId
            ?.let { settings.findModelById(it) }
            ?: parentModel

        val childAssistant = buildChildAssistant(profile, parentAssistant, depth, maxDepth)
        val childTools = runCatching {
            sandboxToolsForSubagent(buildChildTools(childAssistant, depth))
        }.getOrElse {
            Log.w(TAG, "spawn: buildChildTools failed: ${it.message}")
            emptyList()
        }
        val effectiveMaxToolCalls = effectiveMaxToolCalls(profile)

        var totalUsage: TokenUsage? = null
        var steps = 0
        var totalToolLoopSteps = 0
        var lastMessages = listOf<UIMessage>()

        return runCatching {
            Log.i(TAG, "spawn: subagent '${profile.name}' (depth=$depth) started")

            var generationLimitReached = false
            var messages = listOf(UIMessage.user(task))

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
                onProgress = onProgress,
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
                    val transcript = buildTranscript(messages)
                    val reason = SubagentSessionRegistry.cancelReason(conversationId)
                        ?: "Generation cancelled by user"
                    return@runCatching SubagentResult(
                        profileName = profile.name,
                        summary = "Task cancelled by user",
                        succeeded = false,
                        error = reason,
                        depth = depth,
                        usage = totalUsage,
                        steps = steps,
                        toolCallCount = countToolCalls(messages),
                        toolLoopSteps = totalToolLoopSteps,
                        maxToolCalls = effectiveMaxToolCalls,
                        transcript = transcript,
                    )
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
            )
            logResult(result)
            result
        }.onFailure {
            if (it is CancellationException) {
                if (SubagentSessionRegistry.isCancelRequested(conversationId)) return@onFailure
                throw it
            }
            Log.e(TAG, "spawn: subagent '${profile.name}' failed: ${it.message}", it)
        }.getOrElse { failure ->
            if (failure is CancellationException && SubagentSessionRegistry.isCancelRequested(conversationId)) {
                val transcript = buildTranscript(lastMessages)
                val reason = SubagentSessionRegistry.cancelReason(conversationId)
                    ?: "Generation cancelled by user"
                return SubagentResult(
                    profileName = profile.name,
                    summary = "Task cancelled by user",
                    succeeded = false,
                    error = reason,
                    depth = depth,
                    usage = totalUsage,
                    steps = steps,
                    toolCallCount = countToolCalls(lastMessages),
                    toolLoopSteps = totalToolLoopSteps,
                    maxToolCalls = effectiveMaxToolCalls,
                    transcript = transcript,
                )
            }
            if (failure is CancellationException) throw failure
            SubagentResult(
                profileName = profile.name,
                summary = "",
                succeeded = false,
                error = failure.message ?: failure.javaClass.name,
                depth = depth,
                usage = totalUsage,
                steps = steps,
                toolCallCount = countToolCalls(lastMessages),
                toolLoopSteps = totalToolLoopSteps,
                maxToolCalls = effectiveMaxToolCalls,
            )
        }
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
        onProgress: ((List<UIMessage>) -> Unit)?,
        enforceToolBudget: Boolean = true,
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
                    cb(messages)
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
            ).onEach { chunk ->
                if (chunk is GenerationChunk.Messages) {
                    finalMessages = chunk.messages
                    throttledOnProgress?.invoke(chunk.messages)
                    if (SubagentSessionRegistry.isCancelRequested(conversationId)) {
                        throw CancellationException(
                            SubagentSessionRegistry.cancelReason(conversationId)
                                ?: "Generation cancelled by user",
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
        onProgress?.invoke(finalMessages)

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
            contextMessageSize = 0,
            streamOutput = profile.streamOutput || parent.streamOutput,
            enableMemory = profile.enableMemory,
            useGlobalMemory = false,
            enableRecentChatsReference = false,
            allowConversationSystemPrompt = false,
            allowConversationPromptInjection = false,
            enableTimeReminder = false,
            modeInjectionIds = emptySet(),
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
                            val outputText = part.output
                                .filterIsInstance<UIMessagePart.Text>()
                                .joinToString("\n") { it.text }
                            val toolOutputLimit = when {
                                truncateToolOutput > 0 -> truncateToolOutput
                                truncateChars > 0 -> truncateChars
                                else -> 0
                            }
                            val output = when {
                                toolOutputLimit > 0 -> truncate(outputText, toolOutputLimit)
                                else -> outputText
                            }
                            steps.add(
                                SubagentTranscriptStep.ToolCall(
                                    toolName = part.toolName,
                                    input = truncate(part.input, truncateChars),
                                    output = output,
                                    executed = part.isExecuted,
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

        private fun truncate(text: String, max: Int): String =
            if (max <= 0 || text.length <= max) text else text.take(max) + "…"
    }
}
