package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.utils.JsonInstant

/**
 * spawn_subagent 工具的自定义渲染器。
 *
 * 子代理运行完成后，其完整 transcript（思维链 / 工具调用 / 正文）序列化存放在
 * `Tool.output` 的 `Text.metadata["subagent_transcript"]` 中。
 *
 * 渲染策略：
 * - 折叠态标题：`↳ Explorer · 5 steps`（子代理名 + 步数）
 * - 展开态：嵌套 [ChainOfThought]，按时间线展示子代理的每个步骤
 *   - Reasoning → 思考步骤（Sparkles 图标）
 *   - ToolCall  → 工具调用步骤（工具名 + 入参 + 输出摘要）
 *   - Text      → 正文（Markdown 渲染）
 * - 最多展示一层：孙代理的 ToolCall 内部不再展开其 transcript，
 *   仅显示工具名和入参。
 */
object SubagentToolUI : ToolUIRenderer {
    override val toolName: String = "spawn_subagent"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Connect

    @Composable
    override fun title(context: ToolUIContext): String {
        // 优先从 metadata（已完成/流式中）取 profile 名
        val meta = subagentMetadata(context)
        if (meta != null) {
            val profile = meta["subagent_profile"]?.jsonPrimitive?.contentOrNull ?: "subagent"
            val streaming = meta["subagent_streaming"]?.jsonPrimitive?.contentOrNull == "true"
            val succeeded = meta["subagent_succeeded"]?.jsonPrimitive?.contentOrNull == "true"
            return when {
                streaming -> profile
                !succeeded -> "$profile ✗"
                else -> profile
            }
        }
        // 执行中（无 output）：从工具入参取 profile_name
        val profileName = context.arguments.let { args ->
            (args as? JsonObject)?.get("profile_name")?.let {
                runCatching { it.jsonPrimitive.content }.getOrNull()
            }
        } ?: "subagent"
        return profileName
    }

    override fun hasSummary(context: ToolUIContext): Boolean = transcriptSteps(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        val steps = transcriptSteps(context)
        if (steps.isEmpty()) return

        // 嵌套 ChainOfThought：子代理的完整思维链 / 工具调用 / 正文
        ChainOfThought(
            modifier = Modifier.fillMaxWidth(),
            steps = steps,
            collapsedVisibleCount = 2,
            collapsedAdaptiveWidth = false,
            cardColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) { step ->
            SubagentStepView(step)
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title(context),
                style = MaterialTheme.typography.headlineSmall,
            )
            Summary(context)
        }
    }

    // ---- 内部工具 ----

    private fun subagentMetadata(context: ToolUIContext): JsonObject? {
        val textPart = context.tool.output.filterIsInstance<UIMessagePart.Text>().firstOrNull()
        return textPart?.metadata
    }

    private fun transcriptSteps(context: ToolUIContext): List<SubagentTranscriptStep> {
        val meta = subagentMetadata(context) ?: return emptyList()
        val transcriptJson = meta["subagent_transcript"] ?: return emptyList()
        return runCatching {
            val listSerializer = kotlinx.serialization.builtins.ListSerializer(
                SubagentTranscriptStep.serializer()
            )
            JsonInstant.decodeFromString(listSerializer, transcriptJson.toString())
        }.getOrElse { emptyList() }
    }
}

/**
 * 在 ChainOfThoughtScope 内渲染子代理 transcript 的一个步骤。
 */
@Composable
private fun ChainOfThoughtScope.SubagentStepView(step: SubagentTranscriptStep) {
    when (step) {
        is SubagentTranscriptStep.Reasoning -> {
            ChainOfThoughtStep(
                icon = {
                    Icon(
                        imageVector = HugeIcons.Sparkles,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                label = {
                    Text(
                        text = "Thinking",
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
                        imageVector = toolIcon(step.name),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                label = {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                extra = {
                    if (!step.executed) {
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (step.input.isNotBlank()) {
                            Text(
                                text = formatToolInput(step.input),
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
            ChainOfThoughtStep(
                icon = {
                    Icon(
                        imageVector = HugeIcons.Connect,
                        contentDescription = null,
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
                content = {
                    MarkdownBlock(
                        content = step.text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

private fun toolIcon(name: String): ImageVector = when {
    name.contains("search") -> HugeIcons.Search01
    name.contains("workspace") -> HugeIcons.Tools
    else -> HugeIcons.Tools
}

private fun formatToolInput(input: String): String {
    return runCatching {
        val json = JsonInstant.parseToJsonElement(input)
        when (json) {
            is JsonObject -> {
                json.entries.joinToString(", ") { (key, value) ->
                    val v = when (value) {
                        is JsonPrimitive -> value.contentOrNull ?: ""
                        else -> value.toString()
                    }
                    "$key: ${v.take(80)}"
                }
            }
            else -> json.toString().take(200)
        }
    }.getOrElse { input.take(200) }
}
