package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChatQuestion
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tools
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.SubagentResult
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.formatNumber

private const val TRUNCATE_LEN = 120

private fun subagentToolContentKey(context: ToolUIContext): String {
    val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
    val meta = textPart?.metadata
    val streaming = meta?.get("subagent_streaming")?.jsonPrimitive?.contentOrNull
    val steps = meta?.get("subagent_steps")?.jsonPrimitive?.contentOrNull
    val textLen = textPart?.text?.length ?: 0
    return buildString {
        append(context.loading)
        append('|')
        append(context.tool.toolCallId)
        append('|')
        append(context.tool.isExecuted)
        append('|')
        append(streaming)
        append('|')
        append(steps)
        append('|')
        append(textLen)
    }
}

private data class SubagentToolParsedState(
    val meta: JsonObject?,
    val metaTranscript: List<SubagentTranscriptStep>,
    val result: SubagentResult?,
)

private fun parseSubagentToolState(context: ToolUIContext): SubagentToolParsedState {
    val meta = parseSubagentMetadata(context)
    val metaTranscript = transcriptStepsFromMetadata(context)
    val result = parseSubagentResult(context)
    return SubagentToolParsedState(meta, metaTranscript, result)
}

object SpawnSubagentToolUI : ToolUIRenderer {
    override val toolName: String = "spawn_subagent"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Connect

    @Composable
    override fun title(context: ToolUIContext): String {
        val parsed = remember(subagentToolContentKey(context)) { parseSubagentToolState(context) }
        val meta = parsed.meta
        if (meta != null) {
            val profileName = meta["subagent_profile"]?.jsonPrimitive?.contentOrNull ?: "subagent"
            val displayName = resolveSubagentDisplayName(profileName)
            val streaming = meta["subagent_streaming"]?.jsonPrimitive?.contentOrNull == "true"
            val steps = meta["subagent_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            if (streaming) {
                return if (steps > 0) {
                    "$displayName (${stringResource(R.string.subagent_tool_ui_steps, steps)})"
                } else {
                    displayName
                }
            }
        }
        val result = parsed.result
        val profileName = result?.profileName
            ?: meta?.get("subagent_profile")?.jsonPrimitive?.contentOrNull
            ?: context.arguments.getStringContent("profile_name")
            ?: "subagent"
        val displayName = resolveSubagentDisplayName(profileName)
        if (result == null) {
            return displayName
        }
        val steps = result.toolLoopSteps
        val usage = result.usage
        val totalTokens = usage?.let {
            when {
                it.totalTokens > 0 -> it.totalTokens
                else -> it.promptTokens + it.completionTokens
            }
        } ?: 0
        return buildString {
            append(displayName)
            append(" · ")
            append(stringResource(R.string.subagent_tool_ui_steps, steps))
            if (totalTokens > 0) {
                append(" · ")
                append(stringResource(R.string.subagent_tool_ui_token_count, totalTokens.formatNumber()))
            }
        }
    }

    override fun hasSummary(context: ToolUIContext): Boolean {
        val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
        val meta = textPart?.metadata
        val hasTranscript = meta?.containsKey("subagent_transcript") == true
        val streaming = meta?.get("subagent_streaming")?.jsonPrimitive?.contentOrNull == "true"
        val hasTask = context.arguments.getStringContent("task") != null
        val hasOutputText = !textPart?.text.isNullOrBlank()
        return context.loading ||
            streaming ||
            hasTranscript ||
            hasTask ||
            hasOutputText
    }

    @Composable
    override fun Summary(context: ToolUIContext) {
        val parsed = remember(subagentToolContentKey(context)) { parseSubagentToolState(context) }
        val meta = parsed.meta
        val streaming = meta?.get("subagent_streaming")?.jsonPrimitive?.contentOrNull == "true"
        val metaTranscript = parsed.metaTranscript
        val result = parsed.result
        val failed = !streaming && (
            result?.succeeded == false ||
                (result == null && meta?.get("subagent_succeeded")?.jsonPrimitive?.contentOrNull == "false")
        )

        val lastTextStepIndex = remember(metaTranscript) {
            metaTranscript.indexOfLast { it is SubagentTranscriptStep.Text }
        }

        if (metaTranscript.isNotEmpty()) {
            ChainOfThought(
                modifier = Modifier.fillMaxWidth(),
                steps = metaTranscript,
                collapsedVisibleCount = 2,
                collapsedAdaptiveWidth = false,
                cardColors = CardDefaults.cardColors(
                    containerColor = if (failed) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
            ) { step ->
                val stepIndex = metaTranscript.indexOf(step)
                val isFinalSummary = stepIndex == lastTextStepIndex && step is SubagentTranscriptStep.Text
                SubagentStreamingStepView(step = step, isFinalSummary = isFinalSummary)
            }
        } else if ((context.loading || streaming) && result?.summary.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.subagent_tool_ui_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (failed) {
            val errorText = result?.error?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.subagent_tool_ui_failed)
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title(context),
                style = MaterialTheme.typography.headlineSmall,
            )
            Summary(context)
        }
    }
}

object AskBtwToolUI : ToolUIRenderer {
    override val toolName: String = "ask_btw"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.BubbleChatQuestion

    @Composable
    override fun title(context: ToolUIContext): String {
        val tokens = context.content?.getStringContent("usage_tokens")?.toIntOrNull()
            ?: context.content?.getStringContent("tokens")?.toIntOrNull()
        return if (tokens != null && tokens > 0) {
            stringResource(R.string.subagent_tool_ui_ask_btw_title_with_tokens, tokens.formatNumber())
        } else {
            stringResource(R.string.subagent_tool_ui_ask_btw_title)
        }
    }

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val question = context.arguments.getStringContent("question").orEmpty()
        val answer = context.content?.getStringContent("answer")
            ?: context.tool.output.filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
                .takeIf { it.isNotBlank() }
            ?: "..."
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.subagent_tool_ui_ask_q, question.ifBlank { "..." }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.subagent_tool_ui_ask_a, answer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title(context),
                style = MaterialTheme.typography.headlineSmall,
            )
            Summary(context)
        }
    }
}

@Composable
private fun SubagentUsageLine(usage: TokenUsage) {
    val total = when {
        usage.totalTokens > 0 -> usage.totalTokens
        else -> usage.promptTokens + usage.completionTokens
    }
    if (total <= 0) return
    val parts = buildList {
        if (usage.promptTokens > 0) {
            add(
                stringResource(
                    R.string.subagent_tool_ui_usage_prompt,
                    usage.promptTokens.formatNumber(),
                ),
            )
        }
        if (usage.completionTokens > 0) {
            add(
                stringResource(
                    R.string.subagent_tool_ui_usage_completion,
                    usage.completionTokens.formatNumber(),
                ),
            )
        }
        if (usage.cachedTokens > 0) {
            add(
                stringResource(
                    R.string.subagent_tool_ui_usage_cached,
                    usage.cachedTokens.formatNumber(),
                ),
            )
        }
    }
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubagentTranscriptSection(steps: List<SubagentTranscriptStep>) {
    var expanded by remember(steps) { mutableStateOf(false) }
    Text(
        text = if (expanded) {
            stringResource(R.string.subagent_tool_ui_collapse_details)
        } else {
            stringResource(R.string.subagent_tool_ui_expand_details)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEach { step ->
                SubagentTranscriptStepRow(step = step)
            }
        }
    }
}

@Composable
private fun SubagentTranscriptStepRow(step: SubagentTranscriptStep) {
    when (step) {
        is SubagentTranscriptStep.Reasoning -> {
            var showReasoning by remember(step.text) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.subagent_tool_ui_reasoning_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.clickable { showReasoning = !showReasoning },
                )
                AnimatedVisibility(visible = showReasoning) {
                    Text(
                        text = step.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (!showReasoning) {
                    Text(
                        text = step.text.take(TRUNCATE_LEN).let {
                            if (step.text.length > TRUNCATE_LEN) "$it…" else it
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        is SubagentTranscriptStep.ToolCall -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = subagentToolStepIcon(step.toolName),
                    contentDescription = stringResource(R.string.subagent_step_tool),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            R.string.subagent_tool_ui_tool_call,
                            step.toolName,
                            truncate(step.input),
                            truncate(step.output),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is SubagentTranscriptStep.Text -> {
            MarkdownBlock(
                content = step.content,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


private fun parseSubagentMetadata(context: ToolUIContext): JsonObject? {
    val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
    return textPart?.metadata
}

private fun transcriptStepsFromMetadata(context: ToolUIContext): List<SubagentTranscriptStep> {
    val meta = parseSubagentMetadata(context) ?: return emptyList()
    val transcriptJson = meta["subagent_transcript"] ?: return emptyList()
    return runCatching {
        val listSerializer = ListSerializer(SubagentTranscriptStep.serializer())
        JsonInstant.decodeFromJsonElement(listSerializer, transcriptJson)
    }.getOrElse { emptyList() }
}

@Composable
private fun ChainOfThoughtScope.SubagentStreamingStepView(
    step: SubagentTranscriptStep,
    isFinalSummary: Boolean = false,
) {
    when (step) {
        is SubagentTranscriptStep.Reasoning -> {
            ChainOfThoughtStep(
                icon = {
                    Icon(
                        imageVector = HugeIcons.Sparkles,
                        contentDescription = stringResource(R.string.subagent_step_thinking),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.subagent_step_thinking),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                content = {
                    Text(
                        text = step.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }

        is SubagentTranscriptStep.ToolCall -> {
            ChainOfThoughtStep(
                icon = {
                    Icon(
                        imageVector = subagentToolStepIcon(step.toolName),
                        contentDescription = stringResource(R.string.subagent_step_tool),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                label = {
                    Text(
                        text = step.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                extra = {
                    if (!step.executed) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (step.input.isNotBlank()) {
                            Text(
                                text = truncate(step.input),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (step.output.isNotBlank()) {
                            Text(
                                text = step.output.take(2000),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        }

        is SubagentTranscriptStep.Text -> {
            if (isFinalSummary) {
                var showFullScreen by remember { mutableStateOf(false) }
                ChainOfThoughtStep(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Connect,
                            contentDescription = stringResource(R.string.subagent_step_text),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    label = {
                        Text(
                            text = "Summary",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        showFullScreen = true
                    },
                    content = null,
                )
                if (showFullScreen) {
                    SubagentSummarySheet(
                        content = step.content,
                        onDismiss = { showFullScreen = false },
                    )
                }
            } else {
                ChainOfThoughtStep(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Connect,
                            contentDescription = stringResource(R.string.subagent_step_text),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.subagent_step_text),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    content = {
                        MarkdownBlock(
                            content = step.content,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SubagentSummarySheet(
    content: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Expanded,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var showRaw by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !showRaw,
                    onClick = { showRaw = false },
                    label = { Text("预览") },
                )
                FilterChip(
                    selected = showRaw,
                    onClick = { showRaw = true },
                    label = { Text("原生") },
                )
            }
            if (showRaw) {
                SelectionContainer {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    MarkdownBlock(
                        content = content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun parseSubagentResult(context: ToolUIContext): SubagentResult? {
    val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
    val raw = textPart?.text
    if (raw.isNullOrBlank()) return null

    return runCatching {
        JsonInstant.decodeFromString(SubagentResult.serializer(), raw)
    }.recoverCatching {
        val obj = JsonInstant.parseToJsonElement(raw).jsonObject
        val transcript = textPart.metadata?.get("subagent_transcript")?.let {
            JsonInstant.decodeFromJsonElement(
                ListSerializer(SubagentTranscriptStep.serializer()),
                it,
            )
        }.orEmpty()
        SubagentResult(
            profileName = obj["profile_name"]?.jsonPrimitive?.contentOrNull ?: "",
            summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: "",
            succeeded = obj["succeeded"]?.jsonPrimitive?.contentOrNull == "true",
            error = obj["error"]?.jsonPrimitive?.contentOrNull,
            steps = obj["steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            toolLoopSteps = obj["tool_loop_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            transcript = transcript,
        )
    }.getOrNull()
}

private fun resolveSubagentDisplayName(profileName: String): String =
    SubagentRegistry.BUILTIN_PROFILES.firstOrNull { it.name == profileName }?.displayName
        ?: profileName

private fun truncate(text: String): String {
    val t = text.trim()
    if (t.length <= TRUNCATE_LEN) return t
    return t.take(TRUNCATE_LEN) + "…"
}

private fun subagentToolStepIcon(name: String): ImageVector = when {
    name.contains("search", ignoreCase = true) -> HugeIcons.Search01
    name.contains("workspace", ignoreCase = true) -> HugeIcons.Tools
    else -> HugeIcons.Tools
}