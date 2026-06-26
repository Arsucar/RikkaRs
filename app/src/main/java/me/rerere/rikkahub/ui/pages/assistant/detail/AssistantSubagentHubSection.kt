package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun AssistantSubagentHubControls(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEnableBlockedDialog by remember { mutableStateOf(false) }
    val canEnable = assistantHasSpawnableProfile(assistant)

    Card(
        colors = CustomColors.cardColorsOnSurfaceContainer,
        modifier = modifier,
    ) {
        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_enable_title)) },
            description = {
                Text(
                    if (!canEnable && !assistant.enableSubagents) {
                        stringResource(R.string.subagent_enable_blocked_desc)
                    } else {
                        stringResource(R.string.subagent_enable_desc)
                    }
                )
            },
            tail = {
                Switch(
                    checked = assistant.enableSubagents,
                    onCheckedChange = { enabled ->
                        if (enabled && !canEnable) {
                            showEnableBlockedDialog = true
                        } else {
                            onUpdate(assistant.copy(enableSubagents = enabled))
                        }
                    },
                )
            },
        )

        HorizontalDivider()

        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_max_depth_title)) },
            description = {
                if (assistant.subagentMaxDepth <= 1) {
                    Text(stringResource(R.string.subagent_max_depth_disabled))
                } else {
                    Text(
                        stringResource(
                            R.string.subagent_max_depth_desc,
                            assistant.subagentMaxDepth,
                            assistant.subagentMaxDepth - 1,
                        )
                    )
                }
            },
        ) {
            Slider(
                value = assistant.subagentMaxDepth.toFloat(),
                onValueChange = {
                    onUpdate(
                        assistant.copy(
                            subagentMaxDepth = it.toInt().coerceIn(1, 3)
                        )
                    )
                },
                valueRange = 1f..3f,
                steps = 1,
                enabled = assistant.enableSubagents,
            )
        }
    }

    if (showEnableBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showEnableBlockedDialog = false },
            title = { Text(stringResource(R.string.subagent_enable_blocked_title)) },
            text = { Text(stringResource(R.string.subagent_enable_blocked_desc)) },
            confirmButton = {
                TextButton(onClick = { showEnableBlockedDialog = false }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }
}