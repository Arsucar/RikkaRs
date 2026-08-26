package me.rerere.rikkahub.ui.pages.chat

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ContextPreview
import me.rerere.rikkahub.data.ai.ContextPreviewTool
import me.rerere.rikkahub.data.ai.toCopyJson
import me.rerere.rikkahub.utils.UiState

private val contextPreviewJson = Json { prettyPrint = true }

@Composable
fun ConversationContextInspector(
    state: UiState<ContextPreview>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {
                IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.context_inspector_back))
                }
                Text(
                    text = stringResource(R.string.context_inspector_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Lucide.RefreshCw, contentDescription = stringResource(R.string.context_inspector_refresh))
                }
                IconButton(
                    enabled = state is UiState.Success,
                    onClick = {
                        val preview = (state as? UiState.Success)?.data ?: return@IconButton
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "context-preview.json",
                                        preview.toCopyJson(contextPreviewJson),
                                    ),
                                ),
                            )
                        }
                    },
                ) {
                    Icon(Lucide.Copy, contentDescription = stringResource(R.string.context_inspector_copy_all))
                }
            }
        }

        when (state) {
            UiState.Idle, UiState.Loading -> InspectorStatus(stringResource(R.string.context_inspector_loading))
            is UiState.Error -> InspectorError(state.error, onRefresh)
            is UiState.Success -> InspectorContent(state.data)
        }
    }
}

@Composable
private fun InspectorStatus(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun InspectorError(error: Throwable, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(error.message ?: stringResource(R.string.context_inspector_unknown_error))
        Button(onClick = onRefresh) { Text(stringResource(R.string.context_inspector_retry)) }
    }
}

@Composable
private fun InspectorContent(preview: ContextPreview) {
    if (preview.messages.isEmpty() && preview.tools.isEmpty()) {
        InspectorStatus(stringResource(R.string.context_inspector_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ContextOverview(preview) }
        itemsIndexed(preview.messages, key = { index, message -> "${message.id}:$index" }) { index, message ->
            ContextMessageCard(index, message)
        }
        if (preview.tools.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.context_inspector_tools, preview.tools.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            itemsIndexed(preview.tools, key = { index, tool -> "${tool.name}:$index" }) { _, tool ->
                ContextToolCard(tool)
            }
        }
    }
}

@Composable
private fun ContextOverview(preview: ContextPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.context_inspector_overview), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.context_inspector_messages, preview.messages.size))
            Text(
                stringResource(
                    R.string.context_inspector_history,
                    preview.retainedSourceMessageCount,
                    preview.sourceMessageCount,
                ),
            )
            Text(stringResource(R.string.context_inspector_truncated, preview.truncated.toYesNo()))
            Text(
                stringResource(
                    R.string.context_inspector_conversation_prompt,
                    preview.usedConversationSystemPrompt.toYesNo(),
                ),
            )
            Text(stringResource(R.string.context_inspector_characters, preview.characterCount))
            Text(stringResource(R.string.context_inspector_tools, preview.tools.size))
        }
    }
}

@Composable
private fun Boolean.toYesNo(): String = if (this) {
    stringResource(R.string.context_inspector_yes)
} else {
    stringResource(R.string.context_inspector_no)
}

@Composable
private fun ContextMessageCard(index: Int, message: UIMessage) {
    var expanded by remember(message.id) { mutableStateOf(message.role == MessageRole.SYSTEM) }
    val container = if (message.role == MessageRole.SYSTEM) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.chat_context_inspector_item_format, index + 1, message.role.name.lowercase()), fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Lucide.ChevronDown else Lucide.ChevronRight, contentDescription = null)
            }
            if (expanded) {
                message.parts.forEachIndexed { partIndex, part ->
                    Text(
                        text = stringResource(R.string.chat_context_inspector_part_format, partIndex + 1, contextPartText(part)),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextToolCard(tool: ContextPreviewTool) {
    var expanded by remember(tool.name) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tool.name, fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Lucide.ChevronDown else Lucide.ChevronRight, contentDescription = null)
            }
            if (expanded) {
                Text(tool.description)
                Text(
                    tool.parameters?.let(::prettyJson) ?: "null",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun prettyJson(element: JsonElement): String =
    contextPreviewJson.encodeToString(JsonElement.serializer(), element)

@Suppress("DEPRECATION")
internal fun contextPartText(part: UIMessagePart): String = when (part) {
    is UIMessagePart.Text -> "Text\n${part.text}"
    is UIMessagePart.Image -> "Image: ${part.url}"
    is UIMessagePart.Video -> "Video: ${part.url}"
    is UIMessagePart.Audio -> "Audio: ${part.url}"
    is UIMessagePart.Document -> "Document: ${part.fileName} (${part.mime}) ${part.url}"
    is UIMessagePart.SlashSkill -> "SlashSkill: ${part.name}"
    is UIMessagePart.Reasoning -> "Reasoning\n${part.reasoning}"
    UIMessagePart.Search -> "Search"
    is UIMessagePart.ToolCall -> "ToolCall: ${part.toolName} id=${part.toolCallId}\n${part.arguments}"
    is UIMessagePart.ToolResult -> "ToolResult: ${part.toolName} id=${part.toolCallId}\n${part.content}"
    is UIMessagePart.Tool -> buildString {
        append("Tool: ${part.toolName} id=${part.toolCallId}\ninput=${part.input}")
        if (part.output.isNotEmpty()) {
            append("\noutput=")
            append(part.output.joinToString("\n") { contextPartText(it) })
        }
        append("\napproval=${part.approvalState::class.simpleName}")
    }
    is UIMessagePart.ServerTool -> buildString {
        append("ServerTool: ${part.toolName} id=${part.toolCallId}\nstatus=${part.status.name}")
        part.input?.let { append("\ninput=").append(it) }
        part.output?.let { append("\noutput=").append(it) }
    }
}
