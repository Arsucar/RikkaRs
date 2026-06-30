package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

internal const val SUMMARY_CONTINUATION_PROMPT =
    "Your previous response was too brief. Please provide a more comprehensive summary of your findings and actions taken. " +
        "Include key details, file paths found, and specific conclusions."



internal fun selectContinuationTools(childTools: List<Tool>): List<Tool> = emptyList()

class SubagentHost(
    private val generationHandler: GenerationHandler,
) {
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
        onProgress: ((List<UIMessage>) -> Unit)? = null,
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

        var totalUsage: TokenUsage? = null
        var steps = 0

        return runCatching {
            Log.i(TAG, "spawn: subagent '${profile.name}' (depth=$depth) started")

            var messages = listOf(UIMessage.user(task))
            var run = runToCompletion(
                profile = profile,
                settings = settings,
                model = childModel,
                assistant = childAssistant,
                tools = childTools,
                initialMessages = messages,
                workspaceCwd = workspaceCwd,
                onProgress = onProgress,
            )
            steps += 1
            totalUsage = mergeUsage(totalUsage, run.usage)
            messages = run.messages

            var summary = run.summary
            var remainingContinuations = profile.summaryContinuationAttempts
            val minLength = profile.summaryMinLength
            while (remainingContinuations > 0 && minLength > 0 && summary.length < minLength) {
                remainingContinuations -= 1
                val continuationMessages = messages + UIMessage.user(SUMMARY_CONTINUATION_PROMPT)
                run = runToCompletion(
                    profile = profile.copy(maxSteps = 1),
                    settings = settings,
                    model = childModel,
                    assistant = childAssistant,
                    tools = selectContinuationTools(childTools),
                    initialMessages = continuationMessages,
                    workspaceCwd = workspaceCwd,
                    onProgress = onProgress,
                )
                steps += 1
                totalUsage = mergeUsage(totalUsage, run.usage)
                messages = run.messages
                summary = run.summary
            }

            val transcript = buildTranscript(messages)
            val result = SubagentResult(
                profileName = profile.name,
                summary = summary.ifBlank { "(subagent produced no textual summary)" },
                succeeded = true,
                depth = depth,
                usage = totalUsage,
                steps = steps,
                toolCallCount = countToolCalls(messages),
                transcript = transcript,
            )
            logResult(result)
            result
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(TAG, "spawn: subagent '${profile.name}' failed: ${it.message}", it)
        }.getOrElse {
            SubagentResult(
                profileName = profile.name,
                summary = "",
                succeeded = false,
                error = it.message ?: it.javaClass.name,
                depth = depth,
                usage = totalUsage,
                steps = steps,
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
        workspaceCwd: String?,
        onProgress: ((List<UIMessage>) -> Unit)?,
    ): RunCompletion {
        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                    if (progressScope.coroutineContext.isActive) {
                        progressScope.launch {
                            if (!isActive) return@launch
                            cb(messages)
                        }
                    }
                }
            }
        }
        val finalMessages = try {
            generationHandler.generateText(
                settings = settings,
                model = model,
                messages = initialMessages,
                assistant = assistant,
                tools = tools,
                maxSteps = profile.maxSteps.coerceIn(1, 256),
                memories = emptyList(),
                workspaceCwd = workspaceCwd,
            ).onEach { chunk ->
                if (chunk is GenerationChunk.Messages) {
                    throttledOnProgress?.invoke(chunk.messages)
                }
            }.fold(initialMessages) { _, chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> chunk.messages
                }
            }
        } finally {
            progressScope.cancel()
        }

        return RunCompletion(
            messages = finalMessages,
            summary = lastAssistantText(finalMessages),
            usage = accumulateUsage(finalMessages),
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
            name = profile.displayName,
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

    private data class RunCompletion(
        val messages: List<UIMessage>,
        val summary: String,
        val usage: TokenUsage?,
    )

    companion object {
        fun buildTranscript(
            messages: List<UIMessage>,
            truncateChars: Int = 200,
            truncateToolOutput: Int = 0,
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

        // REVIEWED: no longer clears needsApproval; approval flows through SubagentPermissionBuilder
        fun sandboxToolsForSubagent(tools: List<Tool>): List<Tool> = tools

        private fun truncate(text: String, max: Int): String =
            if (max <= 0 || text.length <= max) text else text.take(max) + "…"
    }
}