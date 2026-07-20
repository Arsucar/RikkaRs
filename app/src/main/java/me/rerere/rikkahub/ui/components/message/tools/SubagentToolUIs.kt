package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentResult
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.data.ai.tools.WORKSPACE_SHELL_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.workspaceShellTranscriptInput

import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.formatNumber

private const val TRUNCATE_LEN = 120

private fun subagentToolContentKey(context: ToolUIContext): String {
    val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
    val meta = textPart?.metadata
    val streaming = meta?.get("subagent_streaming")?.jsonPrimitive?.contentOrNull
    val steps = meta?.get("subagent_steps")?.jsonPrimitive?.contentOrNull
    val toolCalls = meta?.get("subagent_tool_calls")?.jsonPrimitive?.contentOrNull
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
        append(toolCalls)
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
            val profileLabel = resolveSubagentLabel(profileName)
            val streaming = meta["subagent_streaming"]?.jsonPrimitive?.contentOrNull == "true"
            val loopSteps = meta["subagent_tool_loop_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: meta["subagent_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: 0
            val toolCalls = meta["subagent_tool_calls"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val cancelled = meta["subagent_cancelled"]?.jsonPrimitive?.contentOrNull == "true"
            if (streaming) {
                return if (cancelled) {
                    stringResource(R.string.subagent_tool_ui_cancelled)
                } else if (toolCalls > 0) {
                    "$profileLabel · $toolCalls"
                } else {
                    profileLabel
                }
            }
        }
        val result = parsed.result
        val profileName = result?.profileName
            ?: meta?.get("subagent_profile")?.jsonPrimitive?.contentOrNull
            ?: context.arguments.getStringContent("profile_name")
            ?: "subagent"
        val profileLabel = resolveSubagentLabel(profileName)
        if (result == null) {
            return profileLabel
        }
        val loopSteps = result.toolLoopSteps.takeIf { it > 0 }
            ?: meta?.get("subagent_tool_loop_steps")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: meta?.get("subagent_steps")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: 0
        val toolCalls = result.toolCallCount.takeIf { it > 0 }
            ?: meta?.get("subagent_tool_calls")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: 0
        val usage = result.usage
        val totalTokens = usage?.let {
            when {
                it.totalTokens > 0 -> it.totalTokens
                else -> it.promptTokens + it.completionTokens
            }
        } ?: 0
        return buildString {
            append(profileLabel)
            if (toolCalls > 0) {
                append(" · ")
                append(stringResource(R.string.subagent_tool_ui_tool_calls_count, toolCalls))
            }
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
        val cancelled = meta?.get("subagent_cancelled")?.jsonPrimitive?.contentOrNull == "true"
        val failed = !streaming && (
            cancelled ||
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
                SubagentStreamingStepView(step = step, isFinalSummary = isFinalSummary, cancelled = cancelled)
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
            SubagentTransferredContextSection(context)
            Summary(context)
        }
    }
}

@Composable
private fun SubagentTransferredContextSection(context: ToolUIContext) {
    val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
    val meta = textPart?.metadata
    val task = meta?.get("subagent_task")?.jsonPrimitive?.contentOrNull
        ?: context.arguments.getStringContent("task")
    val description = meta?.get("subagent_description")?.jsonPrimitive?.contentOrNull
        ?: context.arguments.getStringContent("description")
    val profileName = meta?.get("subagent_profile")?.jsonPrimitive?.contentOrNull
        ?: context.arguments.getStringContent("profile_name")
    val systemPrompt = meta?.get("subagent_system_prompt")?.jsonPrimitive?.contentOrNull
    val systemPromptNote = meta?.get("subagent_system_prompt_note")?.jsonPrimitive?.contentOrNull
        ?: stringResource(R.string.subagent_tool_ui_context_system_note)
    val workspaceAccess = meta?.get("subagent_workspace_access")?.jsonPrimitive?.contentOrNull
    val workspaceApproval = meta?.get("subagent_workspace_approval")?.jsonPrimitive?.contentOrNull
    val canSpawn = meta?.metaBoolean("subagent_can_spawn")
    val inheritTools = meta?.metaBoolean("subagent_inherit_tools")
    val maxToolCalls = meta?.get("subagent_max_tool_calls")?.jsonPrimitive?.contentOrNull
    val excludedTools = meta.metaStringList("subagent_excluded_tools")
    val pathPrefixes = meta.metaStringList("subagent_path_prefixes")
    val childTools = meta.metaStringList("subagent_child_tools")
    val skills = meta.metaStringList("subagent_skills")
    val mcpServers = meta.metaStringList("subagent_mcp_servers")
    val depth = meta?.get("subagent_depth")?.jsonPrimitive?.contentOrNull
    val modelId = meta?.get("subagent_model_id")?.jsonPrimitive?.contentOrNull
    val cwd = meta?.get("subagent_cwd")?.jsonPrimitive?.contentOrNull
    val contextId = meta?.get("subagent_context_id")?.jsonPrimitive?.contentOrNull
    val enableMemory = meta?.metaBoolean("subagent_enable_memory")
    val memoryTableIds = meta.metaStringList("subagent_memory_table_ids")
    val includesParentHistory = meta?.metaBoolean("subagent_includes_parent_history")
    val reusedContext = meta?.metaBoolean("subagent_reused_context")

    val hasAny = !task.isNullOrBlank() ||
        !description.isNullOrBlank() ||
        !profileName.isNullOrBlank() ||
        !systemPrompt.isNullOrBlank() ||
        !workspaceAccess.isNullOrBlank() ||
        !workspaceApproval.isNullOrBlank() ||
        canSpawn != null ||
        inheritTools != null ||
        !maxToolCalls.isNullOrBlank() ||
        excludedTools.isNotEmpty() ||
        pathPrefixes.isNotEmpty() ||
        childTools.isNotEmpty() ||
        skills.isNotEmpty() ||
        mcpServers.isNotEmpty() ||
        !depth.isNullOrBlank() ||
        !modelId.isNullOrBlank() ||
        !cwd.isNullOrBlank() ||
        !contextId.isNullOrBlank() ||
        enableMemory != null ||
        memoryTableIds.isNotEmpty() ||
        includesParentHistory != null ||
        reusedContext != null

    if (!hasAny) return

    var expanded by remember(context.tool.toolCallId) { mutableStateOf(false) }
    Text(
        text = if (expanded) {
            stringResource(R.string.subagent_tool_ui_context_collapse)
        } else {
            stringResource(R.string.subagent_tool_ui_context_expand)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Task + description
            SubagentContextSectionTitle(stringResource(R.string.subagent_tool_ui_context_section_task))
            task?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_task), it)
            }
            description?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_description), it)
            }

            // 2. Subagent system
            SubagentContextSectionTitle(stringResource(R.string.subagent_tool_ui_context_section_system))
            if (!systemPrompt.isNullOrBlank()) {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_system_prompt),
                    systemPrompt,
                )
            } else {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_system_prompt),
                    stringResource(R.string.subagent_tool_ui_context_empty),
                )
            }
            SubagentContextField(
                stringResource(R.string.subagent_tool_ui_context_system_note_label),
                systemPromptNote,
            )

            // 3. Profile constraints
            SubagentContextSectionTitle(
                stringResource(R.string.subagent_tool_ui_context_profile_constraints),
            )
            profileName?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_profile), it)
            }
            workspaceAccess?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_workspace), it)
            }
            workspaceApproval?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_workspace_approval),
                    it,
                )
            }
            maxToolCalls?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_max_tool_calls),
                    it,
                )
            }
            canSpawn?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_can_spawn),
                    it.toString(),
                )
            }
            inheritTools?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_inherit_tools),
                    it.toString(),
                )
            }
            if (excludedTools.isNotEmpty()) {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_excluded_tools),
                    excludedTools.joinToString(", "),
                )
            }
            if (pathPrefixes.isNotEmpty()) {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_path_prefixes),
                    pathPrefixes.joinToString("\n"),
                )
            }
            enableMemory?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_enable_memory),
                    it.toString(),
                )
            }

            // 4. Actual child tools
            SubagentContextSectionTitle(stringResource(R.string.subagent_tool_ui_context_tools))
            SubagentContextField(
                stringResource(R.string.subagent_tool_ui_context_child_tools),
                if (childTools.isEmpty()) {
                    stringResource(R.string.subagent_tool_ui_context_empty)
                } else {
                    childTools.joinToString(", ")
                },
            )

            // 5. Skills + MCP
            SubagentContextSectionTitle(stringResource(R.string.subagent_tool_ui_context_skills_mcp))
            SubagentContextField(
                stringResource(R.string.subagent_tool_ui_context_skills),
                if (skills.isEmpty()) {
                    stringResource(R.string.subagent_tool_ui_context_empty)
                } else {
                    skills.joinToString(", ")
                },
            )
            SubagentContextField(
                stringResource(R.string.subagent_tool_ui_context_mcp),
                if (mcpServers.isEmpty()) {
                    stringResource(R.string.subagent_tool_ui_context_empty)
                } else {
                    mcpServers.joinToString("\n")
                },
            )

            // 6. Metadata
            SubagentContextSectionTitle(stringResource(R.string.subagent_tool_ui_context_metadata))
            depth?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_depth), it)
            }
            modelId?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_model), it)
            }
            if (pathPrefixes.isNotEmpty()) {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_path_prefixes),
                    pathPrefixes.joinToString("\n"),
                )
            }
            cwd?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(stringResource(R.string.subagent_tool_ui_context_cwd), it)
            }
            contextId?.takeIf { it.isNotBlank() }?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_context_id),
                    it,
                )
            }

            // 7. Negative notes
            SubagentContextSectionTitle(stringResource(R.string.subagent_tool_ui_context_negative))
            SubagentContextField(
                stringResource(R.string.subagent_tool_ui_context_no_parent_history),
                if (includesParentHistory == true) {
                    stringResource(R.string.subagent_tool_ui_context_false_unexpected)
                } else {
                    stringResource(R.string.subagent_tool_ui_context_no_parent_history_value)
                },
            )
            if (reusedContext == true) {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_reused_context),
                    stringResource(R.string.subagent_tool_ui_context_reused_context_value),
                )
            }
            SubagentContextField(
                stringResource(R.string.subagent_tool_ui_context_memory_table),
                if (memoryTableIds.isEmpty()) {
                    stringResource(R.string.subagent_tool_ui_context_memory_table_empty)
                } else {
                    memoryTableIds.joinToString(", ")
                },
            )
            enableMemory?.let {
                SubagentContextField(
                    stringResource(R.string.subagent_tool_ui_context_enable_memory),
                    it.toString(),
                )
            }
        }
    }
}

@Composable
private fun SubagentContextSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SubagentContextField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun JsonObject.metaBoolean(key: String): Boolean? {
    val el = this[key] ?: return null
    val primitive = el as? JsonPrimitive ?: return null
    return primitive.booleanOrNull
        ?: primitive.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonObject?.metaStringList(key: String): List<String> {
    val el = this?.get(key) ?: return emptyList()
    return when (el) {
        is JsonArray -> el.mapNotNull { item ->
            (item as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        is JsonPrimitive -> {
            val raw = el.contentOrNull.orEmpty()
            if (raw.isBlank()) emptyList() else raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        else -> emptyList()
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

private const val SUBAGENT_TOOL_OUTPUT_PREVIEW_LINES = 10

@Composable
private fun SubagentTranscriptToolOutputInline(
    outputText: String,
    modifier: Modifier = Modifier,
) {
    if (outputText.isBlank()) return
    var outputExpanded by rememberSaveable(outputText) { mutableStateOf(false) }
    val lineCount = remember(outputText) { outputText.lineSequence().count() }
    val needsToggle = lineCount > SUBAGENT_TOOL_OUTPUT_PREVIEW_LINES
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = outputText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (outputExpanded) Int.MAX_VALUE else SUBAGENT_TOOL_OUTPUT_PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (needsToggle || outputExpanded) {
            TextButton(
                onClick = { outputExpanded = !outputExpanded },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(
                        if (outputExpanded) {
                            R.string.tool_output_collapse
                        } else {
                            R.string.tool_output_expand
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SubagentTranscriptToolCallCompactRow(
    step: SubagentTranscriptStep.ToolCall,
    cancelled: Boolean = false,
) {
    val renderer = remember(step.toolName) { ToolUIRegistry.resolve(step.toolName) }
    val arguments = remember(step.toolName, step.input) {
        parseTranscriptToolArguments(step.toolName, step.input)
    }
    val content = remember(step.output) {
        if (step.output.isBlank()) {
            null
        } else {
            runCatching { JsonInstant.parseToJsonElement(step.output) }.getOrElse {
                kotlinx.serialization.json.buildJsonObject {
                    put("output", kotlinx.serialization.json.JsonPrimitive(step.output))
                }
            }
        }
    }
    val syntheticTool = remember(step) {
        UIMessagePart.Tool(
            toolCallId = "subagent-transcript-row-${step.toolName}-${step.input.hashCode()}",
            toolName = step.toolName,
            input = step.input,
            output = if (step.executed && step.output.isNotBlank()) {
                listOf(UIMessagePart.Text(step.output))
            } else {
                emptyList()
            },
        )
    }
    val context = remember(syntheticTool, step.executed, cancelled) {
        ToolUIContext(
            tool = syntheticTool,
            arguments = arguments,
            content = content,
            loading = !step.executed && !cancelled,
        )
    }
    var showPreview by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPreview = true },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = renderer.icon(context),
                contentDescription = stringResource(R.string.subagent_step_tool),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = renderer.title(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!step.executed && !cancelled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        SubagentTranscriptToolOutputInline(
            outputText = step.output,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
    if (showPreview) {
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Expanded,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            onDismissRequest = { showPreview = false },
        ) {
            renderer.Preview(
                context = context,
                onDismissRequest = { showPreview = false },
            )
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
            SubagentTranscriptToolCallCompactRow(step = step)
        }

        is SubagentTranscriptStep.Text -> {
            MarkdownBlock(
                content = step.content,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun parseTranscriptToolArguments(toolName: String, input: String) =
    if (toolName == WORKSPACE_SHELL_TOOL_NAME) {
        runCatching {
            JsonInstant.parseToJsonElement(workspaceShellTranscriptInput(input, TRUNCATE_LEN))
        }.getOrElse {
            fallbackTranscriptToolArguments(input)
        }
    } else {
        runCatching { JsonInstant.parseToJsonElement(input) }.getOrElse {
            fallbackTranscriptToolArguments(input)
        }
    }

private fun fallbackTranscriptToolArguments(input: String) =
    kotlinx.serialization.json.buildJsonObject {
        put("input", kotlinx.serialization.json.JsonPrimitive(input))
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
private fun ChainOfThoughtScope.SubagentTranscriptToolCallStep(
    step: SubagentTranscriptStep.ToolCall,
    cancelled: Boolean = false,
) {
    val renderer = remember(step.toolName) { ToolUIRegistry.resolve(step.toolName) }
    val arguments = remember(step.toolName, step.input) {
        parseTranscriptToolArguments(step.toolName, step.input)
    }
    val content = remember(step.output) {
        if (step.output.isBlank()) {
            null
        } else {
            runCatching { JsonInstant.parseToJsonElement(step.output) }.getOrElse {
                kotlinx.serialization.json.buildJsonObject {
                    put("output", kotlinx.serialization.json.JsonPrimitive(step.output))
                }
            }
        }
    }
    val syntheticTool = remember(step) {
        UIMessagePart.Tool(
            toolCallId = "subagent-transcript-${step.toolName}-${step.input.hashCode()}",
            toolName = step.toolName,
            input = step.input,
            output = if (step.executed && step.output.isNotBlank()) {
                listOf(UIMessagePart.Text(step.output))
            } else {
                emptyList()
            },
        )
    }
    val context = remember(syntheticTool, step.executed, cancelled) {
        ToolUIContext(
            tool = syntheticTool,
            arguments = arguments,
            content = content,
            loading = !step.executed && !cancelled,
        )
    }
    var showPreview by remember { mutableStateOf(false) }
    ChainOfThoughtStep(
        icon = {
            Icon(
                imageVector = renderer.icon(context),
                contentDescription = stringResource(R.string.subagent_step_tool),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        label = {
            Text(
                text = renderer.title(context),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        extra = {
            if (!step.executed && !cancelled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
            }
        },
        onClick = { showPreview = true },
        content = {
            // 优先用该工具专属的 Summary 渲染 (如 ShellToolUI 能正确分离 stdout/stderr/exit),
            // 避免把原始 JSON blob 直接铺开导致 stdout 被淹没 (见 issue #66); 无 Summary 时回退到纯文本输出
            if (renderer.hasSummary(context)) {
                renderer.Summary(context)
            } else {
                SubagentTranscriptToolOutputInline(
                    outputText = step.output,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
    if (showPreview) {
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Expanded,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            onDismissRequest = { showPreview = false },
        ) {
            renderer.Preview(
                context = context,
                onDismissRequest = { showPreview = false },
            )
        }
    }
}

@Composable
private fun ChainOfThoughtScope.SubagentStreamingStepView(
    step: SubagentTranscriptStep,
    isFinalSummary: Boolean = false,
    cancelled: Boolean = false,
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
            SubagentTranscriptToolCallStep(step = step, cancelled = cancelled)
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

    val metadataResult: (SubagentResult) -> SubagentResult = { result ->
        val meta = textPart.metadata
        result.copy(
            steps = result.steps.takeIf { it > 0 }
                ?: meta?.get("subagent_steps")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: result.steps,
            toolLoopSteps = result.toolLoopSteps.takeIf { it > 0 }
                ?: meta?.get("subagent_tool_loop_steps")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: meta?.get("subagent_steps")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: result.toolLoopSteps,
            toolCallCount = result.toolCallCount.takeIf { it > 0 }
                ?: meta?.get("subagent_tool_calls")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: result.toolCallCount,
        )
    }

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
            usage = obj["usage"]?.let {
                runCatching {
                    JsonInstant.decodeFromJsonElement(TokenUsage.serializer(), it)
                }.getOrNull()
            },
            steps = obj["steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            toolLoopSteps = obj["tool_loop_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            toolCallCount = obj["tool_call_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: obj["tool_calls"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: 0,
            transcript = transcript,
        )
    }.mapCatching(metadataResult).getOrNull()
}

private fun resolveSubagentLabel(profileName: String): String =
    profileName

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
