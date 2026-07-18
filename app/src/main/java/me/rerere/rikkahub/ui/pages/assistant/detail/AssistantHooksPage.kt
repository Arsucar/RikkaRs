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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Webhook
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookTrigger
import me.rerere.rikkahub.data.model.HookRuntimeRules
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.actionType
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ConversationTagLabel
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
fun AssistantHooksPage(id: String, conversationId: String? = null) {
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
    var expandedHookMenuId by remember { mutableStateOf<Uuid?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_hook_settings_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { nav.navigate(Screen.AssistantHookEditor(id, conversationId = conversationId)) }) {
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
                    TextButton(onClick = { nav.navigate(Screen.AssistantHookEditor(id, conversationId = conversationId)) }) {
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
                        val actionName = stringResource(hookActionLabelRes(hook.actionConfig.actionType))
                        val hookDisplayName = hook.name.ifBlank {
                            stringResource(R.string.assistant_hook_unnamed)
                        }
                        Card(
                            onClick = {
                                nav.navigate(Screen.AssistantHookEditor(id, hook.id.toString(), conversationId))
                            },
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
                                        hookDisplayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "$modelName · $actionName",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = hook.enabled,
                                            onCheckedChange = { vm.setHookEnabled(hook.id, it) },
                                            modifier = Modifier.semantics {
                                                contentDescription = hookDisplayName
                                            },
                                        )
                                        Box {
                                            IconButton(onClick = { expandedHookMenuId = hook.id }) {
                                                Icon(
                                                    HugeIcons.MoreVertical,
                                                    stringResource(R.string.more_options),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = expandedHookMenuId == hook.id,
                                                onDismissRequest = { expandedHookMenuId = null },
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(stringResource(R.string.assistant_hook_delete))
                                                    },
                                                    leadingIcon = {
                                                        Icon(HugeIcons.Delete01, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        expandedHookMenuId = null
                                                        hookToDelete = hook
                                                    },
                                                )
                                            }
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
        val hookDisplayName = hook.name.ifBlank {
            stringResource(R.string.assistant_hook_unnamed)
        }
        AlertDialog(
            onDismissRequest = { hookToDelete = null },
            title = { Text(stringResource(R.string.assistant_hook_delete_title)) },
            text = { Text(stringResource(R.string.assistant_hook_delete_confirm, hookDisplayName)) },
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
fun AssistantHookEditorPage(id: String, hookId: String?, conversationId: String? = null) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tags by vm.conversationTags.collectAsStateWithLifecycle()
    val templates by vm.memoryTableTemplates.collectAsStateWithLifecycle()
    val assistantDocuments by vm.memoryTableDocuments.collectAsStateWithLifecycle()
    var availableDocuments by remember(conversationId) { mutableStateOf(emptyList<MemoryTableDocument>()) }
    LaunchedEffect(conversationId, assistantDocuments) {
        availableDocuments = if (conversationId == null) {
            assistantDocuments
        } else {
            vm.getMemoryTableDocumentsForEditor(conversationId)
        }.filter { it.scopeType != MemoryTableScopeType.GLOBAL }
    }
    val nav = LocalNavController.current
    val existing = remember(assistant.hooks, hookId) {
        hookId?.let { value -> runCatching { Uuid.parse(value) }.getOrNull() }
            ?.let { idValue -> assistant.hooks.firstOrNull { it.id == idValue } }
    }
    var name by remember(hookId, existing?.configVersion) { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by remember(hookId, existing?.configVersion) { mutableStateOf(existing?.enabled ?: true) }
    val initialModelId = existing?.modelId ?: assistant.chatModelId ?: settings.chatModelId
    var modelId by remember(hookId, existing?.configVersion, initialModelId) {
        mutableStateOf(initialModelId)
    }
    var prompt by remember(hookId, existing?.configVersion) { mutableStateOf(existing?.prompt.orEmpty()) }
    val trigger by remember(hookId, existing?.configVersion) {
        mutableStateOf(existing?.trigger ?: HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS)
    }
    var selectedActionType by remember(hookId, existing?.configVersion) {
        mutableStateOf(existing?.actionConfig?.actionType ?: HookActionType.ADD_CONVERSATION_TAG)
    }
    var addTagConfig by remember(hookId, existing?.configVersion) {
        mutableStateOf(
            existing?.actionConfig as? HookActionConfig.AddConversationTag
                ?: HookActionConfig.AddConversationTag()
        )
    }
    var syncConfig by remember(hookId, existing?.configVersion) {
        mutableStateOf(
            existing?.actionConfig as? HookActionConfig.SyncMemoryTable
                ?: HookActionConfig.SyncMemoryTable()
        )
    }
    val actionConfig: HookActionConfig = when (selectedActionType) {
        HookActionType.ADD_CONVERSATION_TAG -> addTagConfig
        HookActionType.SYNC_MEMORY_TABLE -> syncConfig
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val validation = validateHookEditor(
        name = name,
        modelIsValid = settings.providers.findModelById(modelId)?.type == ModelType.CHAT,
        trigger = trigger,
        prompt = prompt,
        actionConfig = actionConfig,
        availableTagIds = tags.mapTo(mutableSetOf()) { it.id },
        availableDocuments = availableDocuments,
        conversationId = conversationId,
    )

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
                        enabled = validation.canSave,
                        onClick = {
                            vm.upsertHook(
                                ConversationHook(
                                    id = existing?.id ?: Uuid.random(),
                                    name = name.trim(),
                                    enabled = enabled,
                                    trigger = trigger,
                                    modelId = modelId,
                                    prompt = prompt,
                                    actionConfig = actionConfig,
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
                HookEditorSectionHeader(stringResource(R.string.assistant_hook_section_basic))
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_hook_name)) },
                    singleLine = true,
                    isError = validation.nameError != null,
                    supportingText = validation.nameError?.let { error ->
                        { Text(stringResource(error.stringRes)) }
                    },
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
                HookEditorSectionHeader(stringResource(R.string.assistant_hook_section_runtime))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.assistant_hook_trigger), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.assistant_hook_trigger_final_success),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    validation.triggerError?.let {
                        HookEditorErrorText(stringResource(it.stringRes))
                    }
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
                    validation.modelError?.let {
                        HookEditorErrorText(stringResource(it.stringRes))
                    }
                }
            }
            item {
                HookEditorSectionHeader(stringResource(R.string.assistant_hook_section_rules))
            }
            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_hook_prompt)) },
                    supportingText = {
                        Text(
                            stringResource(
                                validation.promptError?.stringRes
                                    ?: R.string.assistant_hook_prompt_description
                            )
                        )
                    },
                    isError = validation.promptError != null,
                    minLines = 6,
                    maxLines = 10,
                )
            }
            item {
                HookEditorSectionHeader(stringResource(R.string.assistant_hook_section_action))
            }
            item {
                HookActionEditor(
                    actionConfig = actionConfig,
                    tags = tags,
                    documents = availableDocuments,
                    templates = templates,
                    conversationId = conversationId,
                    error = validation.actionError,
                    onActionTypeChange = { selectedActionType = it },
                    onActionConfigChange = { updated ->
                        when (updated) {
                            is HookActionConfig.AddConversationTag -> addTagConfig = updated
                            is HookActionConfig.SyncMemoryTable -> syncConfig = updated
                        }
                    },
                    onCreateFirstTag = { nav.navigate(Screen.SettingConversationTags) },
                )
            }
        }
    }
}

@Composable
private fun HookEditorSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun HookEditorErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun HookActionEditor(
    actionConfig: HookActionConfig,
    tags: List<ConversationTag>,
    documents: List<MemoryTableDocument>,
    templates: List<MemoryTableTemplate>,
    conversationId: String?,
    error: HookEditorFieldError?,
    onActionTypeChange: (HookActionType) -> Unit,
    onActionConfigChange: (HookActionConfig) -> Unit,
    onCreateFirstTag: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HookActionType.entries.forEach { actionType ->
                FilterChip(
                    selected = actionConfig.actionType == actionType,
                    onClick = { onActionTypeChange(actionType) },
                    label = { Text(stringResource(hookActionLabelRes(actionType))) },
                )
            }
        }
        when (actionConfig) {
            is HookActionConfig.AddConversationTag -> {
                val availableTagIds = tags.mapTo(mutableSetOf()) { it.id }
                val unavailableTagIds = actionConfig.allowedTagIds - availableTagIds
                Text(
                    stringResource(R.string.assistant_hook_allowed_tags_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.assistant_hook_no_tags))
                    TextButton(onClick = onCreateFirstTag) {
                        Text(stringResource(R.string.conversation_tag_create_first))
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            val selected = tag.id in actionConfig.allowedTagIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val currentIds = actionConfig.allowedTagIds intersect availableTagIds
                                    val updatedIds = if (selected) currentIds - tag.id else currentIds + tag.id
                                    onActionConfigChange(HookActionConfig.AddConversationTag(updatedIds))
                                },
                                label = { ConversationTagLabel(tag.displayName, tag.colorKey) },
                            )
                        }
                    }
                }
                if (unavailableTagIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onActionConfigChange(
                                HookActionConfig.AddConversationTag(
                                    actionConfig.allowedTagIds intersect availableTagIds
                                )
                            )
                        },
                    ) {
                        Text(stringResource(R.string.assistant_hook_remove_unavailable_tags))
                    }
                }
            }

            is HookActionConfig.SyncMemoryTable -> {
                val candidates = documents.filter { document ->
                    document.scopeType == MemoryTableScopeType.ASSISTANT ||
                        (document.scopeType == MemoryTableScopeType.CONVERSATION &&
                            conversationId != null && document.scopeId == conversationId)
                }
                val selected = candidates.firstOrNull { it.id == actionConfig.targetDocumentId }
                Text(
                    stringResource(R.string.assistant_hook_sync_target),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.assistant_hook_sync_target_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.assistant_hook_sync_no_targets))
                } else {
                    Select(
                        options = candidates,
                        selectedOption = selected ?: candidates.first(),
                        onOptionSelected = { document ->
                            onActionConfigChange(
                                actionConfig.copy(
                                    targetDocumentId = document.id,
                                    targetScopeType = document.scopeType,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        optionToString = { document ->
                            templates.firstOrNull { it.id == document.templateId }?.name
                                ?.takeIf { it.isNotBlank() }
                                ?: document.id
                        },
                        optionDescription = { document ->
                            stringResource(
                                R.string.assistant_hook_sync_target_revision,
                                stringResource(hookMemoryTableScopeLabelRes(document.scopeType)),
                                document.revision,
                            )
                        },
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.assistant_hook_sync_roles),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FilterChip(
                        selected = actionConfig.includeUserMessages,
                        onClick = {
                            onActionConfigChange(
                                actionConfig.copy(includeUserMessages = !actionConfig.includeUserMessages)
                            )
                        },
                        label = { Text(stringResource(R.string.prompt_page_role_user)) },
                    )
                    FilterChip(
                        selected = actionConfig.includeAssistantMessages,
                        onClick = {
                            onActionConfigChange(
                                actionConfig.copy(includeAssistantMessages = !actionConfig.includeAssistantMessages)
                            )
                        },
                        label = { Text(stringResource(R.string.prompt_page_role_assistant)) },
                    )
                }
                HookNumberField(
                    value = actionConfig.recentMessageCount,
                    label = stringResource(R.string.assistant_hook_sync_recent_messages),
                    onValueChange = { onActionConfigChange(actionConfig.copy(recentMessageCount = it)) },
                )
                HookNumberField(
                    value = actionConfig.maxContextChars,
                    label = stringResource(R.string.assistant_hook_sync_max_context_chars),
                    onValueChange = { onActionConfigChange(actionConfig.copy(maxContextChars = it)) },
                )
                HookNumberField(
                    value = actionConfig.maxOperations,
                    label = stringResource(R.string.assistant_hook_sync_max_operations),
                    onValueChange = { onActionConfigChange(actionConfig.copy(maxOperations = it)) },
                )
                HookNumberField(
                    value = actionConfig.minimumIntervalSeconds,
                    label = stringResource(R.string.assistant_hook_sync_min_interval_seconds),
                    onValueChange = { onActionConfigChange(actionConfig.copy(minimumIntervalSeconds = it)) },
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.assistant_hook_sync_automatic)) },
                        supportingContent = {
                            Text(stringResource(R.string.assistant_hook_sync_automatic_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = actionConfig.automatic,
                                onCheckedChange = {
                                    onActionConfigChange(actionConfig.copy(automatic = it))
                                },
                            )
                        },
                    )
                }
            }
        }
        error?.let { HookEditorErrorText(stringResource(it.stringRes)) }
    }
}

@Composable
private fun HookNumberField(
    value: Int,
    label: String,
    onValueChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.let(onValueChange) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

internal data class HookEditorValidation(
    val nameError: HookEditorFieldError? = null,
    val modelError: HookEditorFieldError? = null,
    val triggerError: HookEditorFieldError? = null,
    val promptError: HookEditorFieldError? = null,
    val actionError: HookEditorFieldError? = null,
) {
    val canSave: Boolean
        get() = nameError == null && modelError == null && triggerError == null &&
            promptError == null && actionError == null
}

internal enum class HookEditorFieldError(val stringRes: Int) {
    NAME_REQUIRED(R.string.assistant_hook_error_name_required),
    MODEL_REQUIRED(R.string.assistant_hook_error_model_required),
    TRIGGER_UNSUPPORTED(R.string.assistant_hook_error_trigger_unsupported),
    PROMPT_REQUIRED(R.string.assistant_hook_error_prompt_required),
    TAG_REQUIRED(R.string.assistant_hook_error_tag_required),
    TAG_UNAVAILABLE(R.string.assistant_hook_error_tag_unavailable),
    TARGET_REQUIRED(R.string.assistant_hook_sync_error_target_required),
    TARGET_UNAVAILABLE(R.string.assistant_hook_sync_error_target_unavailable),
    TARGET_GLOBAL_FORBIDDEN(R.string.assistant_hook_sync_error_global_forbidden),
    TARGET_SCOPE_MISMATCH(R.string.assistant_hook_sync_error_scope_mismatch),
    ROLE_REQUIRED(R.string.assistant_hook_sync_error_role_required),
    LIMIT_INVALID(R.string.assistant_hook_sync_error_limit_invalid),
}

internal fun validateHookEditor(
    name: String,
    modelIsValid: Boolean,
    trigger: HookTrigger,
    prompt: String,
    actionConfig: HookActionConfig,
    availableTagIds: Set<Uuid>,
    availableDocuments: List<MemoryTableDocument> = emptyList(),
    conversationId: String? = null,
): HookEditorValidation {
    val actionError = when (actionConfig) {
        is HookActionConfig.AddConversationTag -> when {
            actionConfig.allowedTagIds.isEmpty() -> HookEditorFieldError.TAG_REQUIRED
            !availableTagIds.containsAll(actionConfig.allowedTagIds) -> HookEditorFieldError.TAG_UNAVAILABLE
            else -> null
        }
        is HookActionConfig.SyncMemoryTable -> when {
            actionConfig.targetDocumentId.isBlank() -> HookEditorFieldError.TARGET_REQUIRED
            actionConfig.targetScopeType == MemoryTableScopeType.GLOBAL ->
                HookEditorFieldError.TARGET_GLOBAL_FORBIDDEN
            availableDocuments.none { it.id == actionConfig.targetDocumentId } ->
                HookEditorFieldError.TARGET_UNAVAILABLE
            availableDocuments.first { it.id == actionConfig.targetDocumentId }.scopeType !=
                actionConfig.targetScopeType -> HookEditorFieldError.TARGET_SCOPE_MISMATCH
            actionConfig.targetScopeType == MemoryTableScopeType.CONVERSATION &&
                availableDocuments.first { it.id == actionConfig.targetDocumentId }.scopeId != conversationId ->
                HookEditorFieldError.TARGET_SCOPE_MISMATCH
            !actionConfig.includeUserMessages && !actionConfig.includeAssistantMessages ->
                HookEditorFieldError.ROLE_REQUIRED
            actionConfig.recentMessageCount !in
                HookRuntimeRules.MIN_SYNC_MESSAGE_COUNT..HookRuntimeRules.MAX_SYNC_MESSAGE_COUNT ->
                HookEditorFieldError.LIMIT_INVALID
            actionConfig.maxContextChars !in
                HookRuntimeRules.MIN_SYNC_CONTEXT_CHARS..HookRuntimeRules.MAX_SYNC_CONTEXT_CHARS ->
                HookEditorFieldError.LIMIT_INVALID
            actionConfig.maxOperations !in
                HookRuntimeRules.MIN_SYNC_MAX_OPERATIONS..HookRuntimeRules.MAX_SYNC_MAX_OPERATIONS ->
                HookEditorFieldError.LIMIT_INVALID
            actionConfig.minimumIntervalSeconds < 0 -> HookEditorFieldError.LIMIT_INVALID
            else -> null
        }
    }
    return HookEditorValidation(
        nameError = HookEditorFieldError.NAME_REQUIRED.takeIf { name.isBlank() },
        modelError = HookEditorFieldError.MODEL_REQUIRED.takeUnless { modelIsValid },
        triggerError = HookEditorFieldError.TRIGGER_UNSUPPORTED.takeUnless {
            trigger == HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS
        },
        promptError = HookEditorFieldError.PROMPT_REQUIRED.takeIf { prompt.isBlank() },
        actionError = actionError,
    )
}

internal fun hookActionLabelRes(actionType: HookActionType): Int = when (actionType) {
    HookActionType.ADD_CONVERSATION_TAG -> R.string.assistant_hook_action_add_tag
    HookActionType.SYNC_MEMORY_TABLE -> R.string.assistant_hook_action_sync_memory_table
}

internal fun hookMemoryTableScopeLabelRes(scopeType: MemoryTableScopeType): Int = when (scopeType) {
    MemoryTableScopeType.GLOBAL -> R.string.assistant_page_memory_scope_global
    MemoryTableScopeType.ASSISTANT -> R.string.assistant_page_memory_scope_assistant
    MemoryTableScopeType.CONVERSATION -> R.string.assistant_page_memory_table_scope_conversation
}
