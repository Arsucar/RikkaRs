package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.pages.extensions.displayEntryCount
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.uuid.Uuid

@Composable
fun PresetsContent(
    presets: List<Preset>,
    selectedIds: Set<Uuid>,
    onToggle: (Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    onEdit: ((Preset) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(presets, key = { it.id }) { preset ->
            ListItem(
                modifier = Modifier.clickable(enabled = onEdit != null || onManage != null) {
                    if (onEdit != null) onEdit(preset) else onManage?.invoke()
                },
                headlineContent = {
                    Text(preset.name.ifBlank { stringResource(R.string.extension_content_unnamed_preset) })
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (preset.description.isNotBlank()) {
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 2,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.extension_content_preset_entries_count,
                                preset.displayEntryCount(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = selectedIds.contains(preset.id),
                        onCheckedChange = { checked -> onToggle(preset.id, checked) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (onManage != null) {
            item {
                ManageButton(onClick = onManage)
            }
        }
    }
}

@Composable
fun LorebooksContent(
    lorebooks: List<Lorebook>,
    selectedIds: Set<Uuid>,
    onToggle: (Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    onEdit: ((Lorebook) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(lorebooks) { lorebook ->
            ListItem(
                modifier = Modifier.clickable(enabled = onEdit != null || onManage != null) {
                    if (onEdit != null) onEdit(lorebook) else onManage?.invoke()
                },
                headlineContent = {
                    Text(lorebook.name.ifBlank { stringResource(R.string.extension_content_unnamed_lorebook) })
                },
                supportingContent = if (lorebook.description.isNotBlank()) {
                    {
                        Text(
                            text = lorebook.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else null,
                trailingContent = {
                    Switch(
                        checked = selectedIds.contains(lorebook.id),
                        onCheckedChange = { checked -> onToggle(lorebook.id, checked) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (onManage != null) {
            item {
                ManageButton(onClick = onManage)
            }
        }
    }
}

@Composable
fun SkillsContent(
    skills: List<SkillMetadata>,
    enabledSkills: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    onEdit: ((SkillMetadata) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(skills, key = { it.skillDir.absolutePath }) { skill ->
            ListItem(
                modifier = Modifier.clickable(enabled = onEdit != null || onManage != null) {
                    if (onEdit != null) onEdit(skill) else onManage?.invoke()
                },
                headlineContent = { Text(skill.name) },
                supportingContent = if (skill.description.isNotBlank()) {
                    {
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else null,
                trailingContent = {
                    Switch(
                        checked = enabledSkills.contains(skill.name),
                        onCheckedChange = { checked -> onToggle(skill.name, checked) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (onManage != null) {
            item {
                ManageButton(onClick = onManage)
            }
        }
    }
}

@Composable
fun QuickMessagesContent(
    quickMessages: List<QuickMessage>,
    selectedIds: Set<Uuid>,
    onToggle: (Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    onEdit: ((QuickMessage) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(quickMessages, key = { it.id }) { quickMessage ->
            ListItem(
                modifier = Modifier.clickable(enabled = onEdit != null || onManage != null) {
                    if (onEdit != null) onEdit(quickMessage) else onManage?.invoke()
                },
                headlineContent = {
                    Text(quickMessage.title.ifBlank { stringResource(R.string.extension_content_unnamed) })
                },
                supportingContent = if (quickMessage.content.isNotBlank()) {
                    {
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                        )
                    }
                } else null,
                trailingContent = {
                    Switch(
                        checked = selectedIds.contains(quickMessage.id),
                        onCheckedChange = { checked -> onToggle(quickMessage.id, checked) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (onManage != null) {
            item {
                ManageButton(onClick = onManage)
            }
        }
    }
}

@Composable
private fun ManageButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.extension_content_manage),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun ExtensionEmptyState(
    message: String,
    buttonText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (buttonText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(HugeIcons.Link01, contentDescription = null)
                Text(buttonText)
            }
        }
    }
}

@Composable
fun SkillCard(
    skill: SkillMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    badgeText: String? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val showTrailing = onDelete != null || enabled != null

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Puzzle,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
                if (skill.description.isNotBlank()) {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SkillMetaPill(text = "SKILL.md")
                    skill.compatibility?.takeIf { it.isNotBlank() }?.let {
                        SkillMetaPill(text = it)
                    }
                }
            }
            if (showTrailing) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (onDelete != null) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = HugeIcons.MoreVertical,
                                    contentDescription = stringResource(R.string.skills_page_more_actions),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = HugeIcons.Delete01,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                    if (enabled != null && onToggle != null) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = onToggle,
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkillMetaPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
