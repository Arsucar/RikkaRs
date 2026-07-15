package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DragDropVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Webhook
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ConversationTagLabel
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
fun AssistantHooksPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        vm.moveHook(from.index, to.index)
    }
    val haptic = LocalHapticFeedback.current
    var hookToDelete by remember { mutableStateOf<ConversationHook?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_hook_settings_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { nav.navigate(Screen.AssistantHookEditor(id)) }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.assistant_hook_add))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        if (assistant.hooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(HugeIcons.Webhook, null, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.assistant_hook_empty_title))
                    Text(
                        stringResource(R.string.assistant_hook_empty_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { nav.navigate(Screen.AssistantHookEditor(id)) }) {
                        Text(stringResource(R.string.assistant_hook_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(assistant.hooks, key = { it.id }) { hook ->
                    ReorderableItem(reorderableState, key = hook.id) { isDragging ->
                        val modelName = settings.providers.findModelById(hook.modelId)?.displayName
                            ?: stringResource(R.string.assistant_hook_deleted_model)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isDragging) 0.98f else 1f)
                                .animateItem(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        imageVector = HugeIcons.DragDropVertical,
                                        contentDescription = stringResource(R.string.assistant_hook_reorder),
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.GestureThresholdActivate
                                                )
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            },
                                        ),
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        hook.name.ifBlank { stringResource(R.string.assistant_hook_unnamed) },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(modelName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.assistant_hook_action_add_tag))
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = hook.enabled,
                                            onCheckedChange = { vm.setHookEnabled(hook.id, it) },
                                            modifier = Modifier.semantics {
                                                contentDescription = hook.name
                                            },
                                        )
                                        IconButton(
                                            onClick = {
                                                nav.navigate(Screen.AssistantHookEditor(id, hook.id.toString()))
                                            }
                                        ) {
                                            Icon(HugeIcons.PencilEdit01, stringResource(R.string.assistant_hook_edit))
                                        }
                                        IconButton(onClick = { hookToDelete = hook }) {
                                            Icon(HugeIcons.Delete01, stringResource(R.string.assistant_hook_delete))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    hookToDelete?.let { hook ->
        AlertDialog(
            onDismissRequest = { hookToDelete = null },
            title = { Text(stringResource(R.string.assistant_hook_delete_title)) },
            text = { Text(stringResource(R.string.assistant_hook_delete_confirm, hook.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteHook(hook.id)
                    hookToDelete = null
                }) { Text(stringResource(R.string.assistant_hook_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { hookToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun AssistantHookEditorPage(id: String, hookId: String?) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tags by vm.conversationTags.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val existing = remember(assistant.hooks, hookId) {
        hookId?.let { value -> runCatching { Uuid.parse(value) }.getOrNull() }
            ?.let { idValue -> assistant.hooks.firstOrNull { it.id == idValue } }
    }
    var name by remember(hookId, existing?.configVersion) { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by remember(hookId, existing?.configVersion) { mutableStateOf(existing?.enabled ?: true) }
    var modelId by remember(hookId, existing?.configVersion) {
        mutableStateOf(existing?.modelId ?: assistant.chatModelId ?: settings.chatModelId)
    }
    var prompt by remember(hookId, existing?.configVersion) { mutableStateOf(existing?.prompt.orEmpty()) }
    var allowedTagIds by remember(hookId, existing?.configVersion) {
        mutableStateOf(
            (existing?.actionConfig as? HookActionConfig.AddConversationTag)?.allowedTagIds.orEmpty()
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val canSave = name.isNotBlank() && prompt.isNotBlank() && allowedTagIds.isNotEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.assistant_hook_add else R.string.assistant_hook_edit
                        )
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            vm.upsertHook(
                                ConversationHook(
                                    id = existing?.id ?: Uuid.random(),
                                    name = name.trim(),
                                    enabled = enabled,
                                    modelId = modelId,
                                    prompt = prompt,
                                    actionConfig = HookActionConfig.AddConversationTag(allowedTagIds),
                                    configVersion = existing?.configVersion ?: 1,
                                )
                            )
                            nav.popBackStack()
                        },
                    ) { Text(stringResource(R.string.common_save)) }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_hook_name)) },
                    singleLine = true,
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.assistant_hook_enabled)) },
                        supportingContent = { Text(stringResource(R.string.assistant_hook_enabled_description)) },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.assistant_hook_trigger), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.assistant_hook_trigger_final_success),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.assistant_hook_model), style = MaterialTheme.typography.titleSmall)
                    ModelSelector(
                        modelId = modelId,
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = { modelId = it.id },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_hook_prompt)) },
                    supportingText = { Text(stringResource(R.string.assistant_hook_prompt_description)) },
                    minLines = 6,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.assistant_hook_allowed_tags),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.assistant_hook_allowed_tags_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tags.isEmpty()) {
                        Text(stringResource(R.string.assistant_hook_no_tags))
                        TextButton(onClick = { nav.navigate(Screen.SettingConversationTags) }) {
                            Text(stringResource(R.string.conversation_tag_create_first))
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                val selected = tag.id in allowedTagIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        allowedTagIds = if (selected) {
                                            allowedTagIds - tag.id
                                        } else {
                                            allowedTagIds + tag.id
                                        }
                                    },
                                    label = { ConversationTagLabel(tag.displayName, tag.colorKey) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
