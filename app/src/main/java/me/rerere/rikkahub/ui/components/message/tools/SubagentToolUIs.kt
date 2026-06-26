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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChatQuestion
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.SubagentResult
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.formatNumber

private const val TRUNCATE_LEN = 120

object SpawnSubagentToolUI : ToolUIRenderer {
    override val toolName: String = "spawn_subagent"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Connect

    @Composable
    override fun title(context: ToolUIContext): String {
        val result = remember(context.tool) { parseSubagentResult(context) }
        val profileName = result?.profileName
            ?: context.arguments.getStringContent("profile_name")
            ?: "subagent"
        val displayName = resolveSubagentDisplayName(profileName)
        if (result == null) {
            return if (context.loading) displayName else displayName
        }
        val steps = result.steps.coerceAtLeast(result.transcript.size)
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
        val result = parseSubagentResult(context)
        return context.loading ||
            result != null ||
            context.arguments.getStringContent("task") != null
    }

    @Composable
    override fun Summary(context: ToolUIContext) {
        val result = remember(context.tool) { parseSubagentResult(context) }
        val failed = result?.succeeded == false
        val containerColor = if (failed) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (context.loading && result == null) {
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
                result?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    MarkdownBlock(
                        content = summary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (failed) {
                    val errorText = result.error?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.subagent_tool_ui_failed)
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                result?.usage?.let { usage ->
                    SubagentUsageLine(usage = usage)
                }
                val transcript = result?.transcript.orEmpty()
                if (transcript.isNotEmpty()) {
                    SubagentTranscriptSection(steps = transcript)
                } else if (result == null && !context.loading) {
                    val raw = context.tool.output.filterIsInstance<UIMessagePart.Text>()
                        .joinToString("\n") { it.text }
                    if (raw.isNotBlank()) {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
                    contentDescription = null,
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

private fun parseSubagentResult(context: ToolUIContext): SubagentResult? {
    val raw = context.tool.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
    if (raw.isBlank()) return null
    return runCatching {
        JsonInstant.decodeFromString(SubagentResult.serializer(), raw)
    }.getOrElse {
        context.content?.let { element ->
            runCatching {
                JsonInstant.decodeFromJsonElement(SubagentResult.serializer(), element)
            }.getOrNull()
        }
    }
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