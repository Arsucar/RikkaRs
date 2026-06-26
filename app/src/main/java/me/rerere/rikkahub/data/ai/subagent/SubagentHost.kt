package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.fold
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

private val NO_APPROVAL: (JsonElement) -> Boolean = { false }

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
        val profile = SubagentRegistry.resolveProfile(profileName, parentAssistant)
            ?: return SubagentResult(
                profileName = profileName,
                summary = "",
                succeeded = false,
                error = "profile not found",
                depth = depth,
            )

        if (depth >= maxDepth) {
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
        val finalMessages = generationHandler.generateText(
            settings = settings,
            model = model,
            messages = initialMessages,
            assistant = assistant,
            tools = tools,
            maxSteps = profile.maxSteps.coerceIn(1, 256),
            memories = emptyList(),
            workspaceCwd = workspaceCwd,
        ).fold(initialMessages) { _, chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> {
                    onProgress?.invoke(chunk.messages)
                    chunk.messages
                }
            }
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
            } else {
                addAll(profile.localTools)
            }
            removeAll { it == LocalToolOption.AskUser }
        }.distinct()

        val childCanSpawn = profile.canSpawn && (depth + 1) < maxDepth

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
        ): List<SubagentTranscriptStep> {
            val steps = mutableListOf<SubagentTranscriptStep>()
            for (message in messages) {
                if (message.role != MessageRole.ASSISTANT) continue
                for (part in message.parts) {
                    when (part) {
                        is UIMessagePart.Reasoning -> {
                            if (part.reasoning.isNotBlank()) {
                                steps.add(SubagentTranscriptStep.Reasoning(part.reasoning))
                            }
                        }

                        is UIMessagePart.Tool -> {
                            val outputText = part.output
                                .filterIsInstance<UIMessagePart.Text>()
                                .joinToString("\n") { it.text }
                            steps.add(
                                SubagentTranscriptStep.ToolCall(
                                    toolName = part.toolName,
                                    input = truncate(part.input, truncateChars),
                                    output = truncate(outputText, truncateChars),
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

        fun sandboxToolsForSubagent(tools: List<Tool>): List<Tool> =
            tools.map { tool -> tool.copy(needsApproval = NO_APPROVAL) }

        private fun truncate(text: String, max: Int): String =
            if (max <= 0 || text.length <= max) text else text.take(max) + "…"
    }
}