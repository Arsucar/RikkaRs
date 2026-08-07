package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.MessageRole
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DragDropHorizontal
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.model.DEFAULT_DRAFT_CONTEXT
import me.rerere.rikkahub.data.model.DraftContextConfig
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.effectivePosition
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetDetailPage(
    presetId: String,
    vm: PromptVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val targetId = remember(presetId) { Uuid.parse(presetId) }
    val storedPreset = settings.presets.firstOrNull { it.id == targetId }
    var editablePreset by remember(targetId, storedPreset?.id) { mutableStateOf(storedPreset) }
    val preset = editablePreset ?: storedPreset
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun updatePreset(transform: (Preset) -> Preset) {
        val current = editablePreset ?: storedPreset ?: return
        editablePreset = transform(current)
        vm.updatePreset(targetId, transform)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    Text(
                        text = preset?.name?.ifBlank { stringResource(R.string.prompt_page_unnamed_preset) }
                            ?: stringResource(R.string.prompt_page_unnamed_preset),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        if (preset == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.preset_detail_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PresetDetailContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                preset = preset,
                modeInjections = settings.modeInjections,
                onMutatePreset = ::updatePreset,
            )
        }
    }
}

@Composable
private fun PresetDetailContent(
    modifier: Modifier,
    preset: Preset,
    modeInjections: List<PromptInjection.ModeInjection>,
    onMutatePreset: ((Preset) -> Preset) -> Unit,
) {
    var editingEntry by remember { mutableStateOf<PresetEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<PresetEntry?>(null) }

    // 按类型分组并按 order 升序展示（同 Block B 注入顺序）
    val builtins = remember(preset.entries) {
        preset.entries.filterIsInstance<PresetEntry.Builtin>().sortedBy { it.order }
    }
    val customs = remember(preset.entries) {
        preset.entries.filterIsInstance<PresetEntry.Custom>().sortedBy { it.order }
    }

    val lazyListState = rememberLazyListState()
    // 拖拽重排：仅在同一子类型组内重排（跨组由纯函数 no-op）。
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? Uuid ?: return@rememberReorderableLazyListState
        val toId = to.key as? Uuid ?: return@rememberReorderableLazyListState
        onMutatePreset { current ->
            val updated = reorderPresetEntryByTarget(current.entries, fromId, toId)
            if (updated == current.entries) current else current.copy(entries = updated)
        }
    }

    fun deleteEntry(entry: PresetEntry) {
        onMutatePreset { current ->
            current.copy(entries = current.entries.filterNot { it.id == entry.id })
        }
    }

    fun addEntry(entry: PresetEntry) {
        onMutatePreset { current -> current.copy(entries = current.entries + entry) }
    }

    fun toggleEntry(entry: PresetEntry, enabled: Boolean) {
        onMutatePreset { current ->
            current.copy(
                entries = current.entries.map { currentEntry ->
                    if (currentEntry.id == entry.id) currentEntry.withEnabled(enabled) else currentEntry
                }
            )
        }
    }

    fun moveEntry(entry: PresetEntry, delta: Int) {
        onMutatePreset { current ->
            val updated = movePresetEntryInGroup(current.entries, entry.id, delta)
            if (updated == current.entries) current else current.copy(entries = updated)
        }
    }

    val availableKeys = remember(preset.entries) { availableBuiltinKeys(preset.entries) }
    var builtinMenuExpanded by remember { mutableStateOf(false) }
    // #196: computed outside LazyListScope (not a @Composable receiver).
    val hasReplyDraft = remember(preset.entries) {
        preset.entries.any {
            it is PresetEntry.Builtin && it.builtinKey == BuiltinPromptRegistry.KEY_REPLY_DRAFT
        }
    }

    // #246: 名称/描述文本字段局部缓冲——输入过程只更新本地 state，失焦/退栈时提交，
    // 避免每击键一次 DataStore 写导致快速返回时最后几次击键随 viewModelScope 取消而丢失。
    var nameText by remember(preset.name) { mutableStateOf(preset.name) }
    var descriptionText by remember(preset.description) { mutableStateOf(preset.description) }
    val latestNameText by rememberUpdatedState(nameText)
    val latestDescriptionText by rememberUpdatedState(descriptionText)

    fun commitName() {
        if (latestNameText != preset.name) {
            onMutatePreset { it.copy(name = latestNameText) }
        }
    }

    fun commitDescription() {
        if (latestDescriptionText != preset.description) {
            onMutatePreset { it.copy(description = latestDescriptionText) }
        }
    }

    // 退栈/销毁前 flush 未提交的文本缓冲（composition dispose 先于 ViewModel onCleared，
    // 给最后一次写留出窗口）。与 AssistantMemoryTableDocumentEditorPage 的 DisposableEffect 模式一致。
    DisposableEffect(Unit) {
        onDispose {
            commitName()
            commitDescription()
        }
    }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "name") {
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.prompt_page_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            commitName()
                        }
                    },
                singleLine = true,
            )
        }
        item(key = "description") {
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text(stringResource(R.string.prompt_page_description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            commitDescription()
                        }
                    },
                minLines = 2,
            )
        }

        // Builtin 区：加号弹出去重后的 key 选择菜单
        item(key = "header_builtin") {
            Box {
                SectionHeader(
                    title = stringResource(R.string.preset_detail_section_builtin),
                    addEnabled = availableKeys.isNotEmpty(),
                    onAdd = { builtinMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = builtinMenuExpanded,
                    onDismissRequest = { builtinMenuExpanded = false },
                ) {
                    availableKeys.forEach { key ->
                        val def = BuiltinPromptRegistry[key]
                        DropdownMenuItem(
                            text = { Text(key) },
                            onClick = {
                                builtinMenuExpanded = false
                                addEntry(
                                    PresetEntry.Builtin(
                                        order = builtins.size,
                                        builtinKey = key,
                                        position = def?.defaultPosition
                                            ?: InjectionPosition.AFTER_SYSTEM_PROMPT,
                                        role = def?.defaultRole ?: MessageRole.USER,
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
        if (builtins.isEmpty()) {
            item(key = "empty_builtin") { EmptySectionHint() }
        } else {
            itemsIndexed(builtins, key = { _, entry -> entry.id }) { index, entry ->
                ReorderableItem(state = reorderState, key = entry.id) { isDragging ->
                    PresetEntryCard(
                        modifier = Modifier.scale(if (isDragging) 0.95f else 1f),
                        entry = entry,
                        modeInjections = modeInjections,
                        reorderScope = this,
                        onEdit = { editingEntry = entry },
                        onDelete = { deleteTarget = entry },
                        onToggle = { toggleEntry(entry, it) },
                        canMoveUp = index > 0,
                        canMoveDown = index < builtins.lastIndex,
                        onMoveUp = { moveEntry(entry, -1) },
                        onMoveDown = { moveEntry(entry, 1) },
                    )
                }
            }
        }

        // #196: reply-draft context assembly — only when this preset has a reply_draft Builtin.
        if (hasReplyDraft) {
            item(key = "draft_context") {
                DraftContextSection(
                    draftContext = preset.draftContext,
                    onChange = { next ->
                        onMutatePreset { current -> current.copy(draftContext = next) }
                    },
                )
            }
        }

        // Custom 区：加号直接添加空条目（允许多条，不去重）
        item(key = "header_custom") {
            SectionHeader(
                title = stringResource(R.string.preset_detail_section_custom),
                addEnabled = true,
                onAdd = { addEntry(PresetEntry.Custom(order = customs.size)) },
            )
        }
        if (customs.isEmpty()) {
            item(key = "empty_custom") { EmptySectionHint() }
        } else {
            itemsIndexed(customs, key = { _, entry -> entry.id }) { index, entry ->
                ReorderableItem(state = reorderState, key = entry.id) { isDragging ->
                    PresetEntryCard(
                        modifier = Modifier.scale(if (isDragging) 0.95f else 1f),
                        entry = entry,
                        modeInjections = modeInjections,
                        reorderScope = this,
                        onEdit = { editingEntry = entry },
                        onDelete = { deleteTarget = entry },
                        onToggle = { toggleEntry(entry, it) },
                        canMoveUp = index > 0,
                        canMoveDown = index < customs.lastIndex,
                        onMoveUp = { moveEntry(entry, -1) },
                        onMoveDown = { moveEntry(entry, 1) },
                    )
                }
            }
        }
    }

    editingEntry?.let { entry ->
        PresetEntryEditSheet(
            entry = entry,
            builtinKeys = availableBuiltinKeys(preset.entries, entry.id),
            modeInjections = modeInjections,
            onDismiss = { editingEntry = null },
            onConfirm = { edited ->
                onMutatePreset { current ->
                    current.copy(entries = current.entries.replaceEntry(edited))
                }
                editingEntry = null
            },
        )
    }

    val deleteTargetTitle = deleteTarget?.let { presetEntryTitle(it, modeInjections) }.orEmpty()
    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.preset_detail_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let(::deleteEntry)
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.preset_detail_delete_message, deleteTargetTitle))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    addEnabled: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onAdd, enabled = addEnabled) {
            Icon(HugeIcons.Add01, stringResource(R.string.prompt_page_add))
        }
    }
}

@Composable
private fun EmptySectionHint() {
    Text(
        text = stringResource(R.string.preset_detail_empty_section),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Reply-draft context knobs (#196). Displayed only when the preset contains a reply_draft Builtin.
 * [draftContext] null → show [DEFAULT_DRAFT_CONTEXT] values + "默认" tag; any edit materializes non-null.
 */
@Composable
private fun DraftContextSection(
    draftContext: DraftContextConfig?,
    onChange: (DraftContextConfig) -> Unit,
) {
    val effective = draftContext ?: DEFAULT_DRAFT_CONTEXT
    val isDefault = draftContext == null
    // Local text buffers so partial typing (e.g. empty field) does not thrash persisted state.
    var messageCountText by remember(effective.messageCount, isDefault) {
        mutableStateOf(effective.messageCount.toString())
    }
    var maxCharsText by remember(effective.maxCharsPerMessage, isDefault) {
        mutableStateOf(effective.maxCharsPerMessage.toString())
    }

    fun mutate(transform: (DraftContextConfig) -> DraftContextConfig) {
        onChange(transform(draftContext ?: DEFAULT_DRAFT_CONTEXT))
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.preset_detail_draft_context_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isDefault) {
                    Tag(type = TagType.INFO) {
                        Text(stringResource(R.string.preset_detail_draft_context_default_tag))
                    }
                }
            }

            OutlinedTextField(
                value = messageCountText,
                onValueChange = { raw ->
                    messageCountText = raw
                    val parsed = raw.toIntOrNull()
                    val next = when {
                        parsed == null || parsed < 0 -> DEFAULT_DRAFT_CONTEXT.messageCount
                        else -> parsed
                    }
                    mutate { it.copy(messageCount = next) }
                },
                label = { Text(stringResource(R.string.preset_detail_draft_context_message_count)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            OutlinedTextField(
                value = maxCharsText,
                onValueChange = { raw ->
                    maxCharsText = raw
                    val parsed = raw.toIntOrNull()
                    val next = when {
                        parsed == null || parsed < 0 -> DEFAULT_DRAFT_CONTEXT.maxCharsPerMessage
                        else -> parsed
                    }
                    mutate { it.copy(maxCharsPerMessage = next) }
                },
                label = { Text(stringResource(R.string.preset_detail_draft_context_max_chars)) },
                supportingText = {
                    Text(stringResource(R.string.preset_detail_draft_context_max_chars_hint))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Text(
                text = stringResource(R.string.preset_detail_draft_context_include_title),
                style = MaterialTheme.typography.titleSmall,
            )

            FormItem(
                label = { Text(stringResource(R.string.preset_detail_draft_context_include_media)) },
                tail = {
                    Switch(
                        checked = effective.includeMedia,
                        onCheckedChange = { checked -> mutate { it.copy(includeMedia = checked) } },
                    )
                },
            )
            FormItem(
                label = { Text(stringResource(R.string.preset_detail_draft_context_include_tools)) },
                tail = {
                    Switch(
                        checked = effective.includeTools,
                        onCheckedChange = { checked -> mutate { it.copy(includeTools = checked) } },
                    )
                },
            )
            FormItem(
                label = { Text(stringResource(R.string.preset_detail_draft_context_include_reasoning)) },
                tail = {
                    Switch(
                        checked = effective.includeReasoning,
                        onCheckedChange = { checked ->
                            mutate { it.copy(includeReasoning = checked) }
                        },
                    )
                },
            )
            FormItem(
                label = { Text(stringResource(R.string.preset_detail_draft_context_keep_latest)) },
                tail = {
                    Switch(
                        checked = effective.keepLatestMessageIntact,
                        onCheckedChange = { checked ->
                            mutate { it.copy(keepLatestMessageIntact = checked) }
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun PresetEntryCard(
    modifier: Modifier = Modifier,
    entry: PresetEntry,
    modeInjections: List<PromptInjection.ModeInjection>,
    reorderScope: ReorderableCollectionItemScope,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val title = presetEntryTitle(entry, modeInjections)
    val invalidReference = entry is PresetEntry.Reference &&
        modeInjections.none { it.id == entry.modeInjectionId }
    // Builtin 条目当前一律 config-only（injectable=false）：可见可编辑但不注入当前对话。
    val configOnly = entry is PresetEntry.Builtin &&
        BuiltinPromptRegistry[entry.builtinKey]?.injectable != true
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val dragHandleDescription = stringResource(R.string.preset_detail_drag_handle)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
    ) {
        // 单行布局：拖拽手柄 + 标题区（weight 1）+ 溢出菜单 + Switch。
        // 拖拽手柄长按可重排，编辑/删除收进 DropdownMenu，省出横向空间避免窄屏截断。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = with(reorderScope) {
                    Modifier
                        .size(48.dp)
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        )
                }.semantics {
                    contentDescription = dragHandleDescription
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    HugeIcons.DragDropHorizontal,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Tag(type = TagType.INFO) {
                        Text(presetPositionLabel(entry.effectivePosition()))
                    }
                    if (configOnly) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.preset_detail_config_only_tag))
                        }
                    }
                    if (invalidReference) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.preset_detail_invalid_reference))
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(HugeIcons.MoreVertical, stringResource(R.string.preset_detail_more_actions))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preset_detail_move_up)) },
                        leadingIcon = { Icon(HugeIcons.ArrowUp01, contentDescription = null) },
                        enabled = canMoveUp,
                        onClick = {
                            menuExpanded = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preset_detail_move_down)) },
                        leadingIcon = { Icon(HugeIcons.ArrowDown01, contentDescription = null) },
                        enabled = canMoveDown,
                        onClick = {
                            menuExpanded = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.prompt_page_edit)) },
                        leadingIcon = { Icon(HugeIcons.Tools, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.prompt_page_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                HugeIcons.Delete01,
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
            Switch(
                checked = entry.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PresetEntryEditSheet(
    entry: PresetEntry,
    builtinKeys: List<String>,
    modeInjections: List<PromptInjection.ModeInjection>,
    onDismiss: () -> Unit,
    onConfirm: (PresetEntry) -> Unit,
) {
    var draft by remember { mutableStateOf(entry) }

    // Reference 条目必须指向一个仍存在的全局注入才允许保存，避免静默保留死 id。
    val draftValid = when (val current = draft) {
        is PresetEntry.Reference -> current.hasValidTarget(modeInjections)
        else -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Expanded,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        contentWindowInsets = { WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.prompt_page_edit),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.prompt_page_cancel))
                    }
                    TextButton(onClick = { onConfirm(draft) }, enabled = draftValid) {
                        Text(stringResource(R.string.prompt_page_confirm))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_enabled)) },
                    tail = {
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.withEnabled(it) },
                        )
                    },
                )

                when (val current = draft) {
                    is PresetEntry.Custom -> {
                        OutlinedTextField(
                            value = current.name,
                            onValueChange = { draft = current.copy(name = it) },
                            label = { Text(stringResource(R.string.prompt_page_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        PositionAndDepthFields(
                            position = current.position,
                            injectDepth = current.injectDepth,
                            role = current.role,
                            onPositionChange = { draft = current.copy(position = it) },
                            onDepthChange = { draft = current.copy(injectDepth = it) },
                            onRoleChange = { draft = current.copy(role = it) },
                        )
                        OutlinedTextField(
                            value = current.content,
                            onValueChange = { draft = current.copy(content = it) },
                            label = { Text(stringResource(R.string.prompt_page_injection_content)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            minLines = 5,
                        )
                    }

                    is PresetEntry.Builtin -> {
                        val def = BuiltinPromptRegistry[current.builtinKey]
                        // 当前内置条目一律 config-only：可编辑维护，但不注入当前对话。
                        if (def?.injectable != true) {
                            Tag(type = TagType.INFO) {
                                Text(stringResource(R.string.preset_detail_config_only_desc))
                            }
                        }
                        Text(
                            stringResource(R.string.preset_detail_builtin_key),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Select(
                            options = builtinKeys,
                            selectedOption = current.builtinKey,
                            onOptionSelected = { newKey ->
                                // 切换 key 时清空旧 key 的覆盖内容/位置，并用新 def 的默认
                                // role/position 重置，避免展示或注入残留旧 key 的覆盖。
                                val newDef = BuiltinPromptRegistry[newKey]
                                draft = current.switchBuiltinKey(newKey, newDef)
                            },
                            optionToString = { it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PositionAndDepthFields(
                            position = current.overridePosition ?: current.position,
                            injectDepth = current.injectDepth,
                            role = current.role,
                            onPositionChange = { draft = current.copy(overridePosition = it) },
                            onDepthChange = { draft = current.copy(injectDepth = it) },
                            onRoleChange = { draft = current.copy(role = it) },
                        )
                        // 魔法变量 chips（点击追加到 override 内容）
                        if (def != null && def.hasEditorVariables()) {
                            Text(
                                stringResource(R.string.preset_detail_variables),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                def.supportedVariables.forEach { variable ->
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            val base = current.overrideContent ?: def.defaultContent
                                            draft = current.copy(overrideContent = base + variable)
                                        },
                                        label = { Text(variable) },
                                    )
                                }
                            }
                        }
                        if (def == null || def.overridable) {
                            OutlinedTextField(
                                value = current.overrideContent ?: (def?.defaultContent ?: ""),
                                onValueChange = { draft = current.copy(overrideContent = it) },
                                label = { Text(stringResource(R.string.preset_detail_override_content)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                minLines = 5,
                            )
                        } else {
                            Text(
                                text = def.defaultContent,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is PresetEntry.Reference -> {
                        Text(
                            stringResource(R.string.preset_detail_reference_target),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (modeInjections.isEmpty()) {
                            Text(
                                text = stringResource(R.string.prompt_page_mode_injection_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // 当前引用是否命中有效全局注入；失效时不伪装选中首项，
                            // 而是提示重选并禁用确认，避免静默保留死 id。
                            val selected = modeInjections.firstOrNull { it.id == current.modeInjectionId }
                            Select<PromptInjection.ModeInjection?>(
                                options = modeInjections,
                                selectedOption = selected,
                                onOptionSelected = { option ->
                                    option?.let { draft = current.copy(modeInjectionId = it.id) }
                                },
                                optionToString = { option ->
                                    option?.name?.ifBlank { stringResource(R.string.prompt_page_unnamed) }
                                        ?: stringResource(R.string.preset_detail_reference_reselect)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (selected == null) {
                                Text(
                                    text = stringResource(R.string.preset_detail_reference_reselect),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        PositionAndDepthFields(
                            position = current.position,
                            injectDepth = current.injectDepth,
                            role = current.role,
                            onPositionChange = { draft = current.copy(position = it) },
                            onDepthChange = { draft = current.copy(injectDepth = it) },
                            onRoleChange = { draft = current.copy(role = it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionAndDepthFields(
    position: InjectionPosition,
    injectDepth: Int,
    role: MessageRole,
    onPositionChange: (InjectionPosition) -> Unit,
    onDepthChange: (Int) -> Unit,
    onRoleChange: (MessageRole) -> Unit,
) {
    Text(
        stringResource(R.string.prompt_page_injection_position),
        style = MaterialTheme.typography.titleSmall,
    )
    Select(
        options = InjectionPosition.entries,
        selectedOption = position,
        onOptionSelected = onPositionChange,
        optionToString = { presetPositionLabel(it) },
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedVisibility(visible = position == InjectionPosition.AT_DEPTH) {
        OutlinedTextField(
            value = injectDepth.toString(),
            onValueChange = { it.toIntOrNull()?.let(onDepthChange) },
            label = { Text(stringResource(R.string.prompt_page_inject_depth)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    AnimatedVisibility(visible = position.usesStandaloneMessage()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.prompt_page_injection_role),
                style = MaterialTheme.typography.titleSmall,
            )
            Select(
                options = listOf(MessageRole.USER, MessageRole.ASSISTANT),
                selectedOption = role,
                onOptionSelected = onRoleChange,
                optionToString = { presetRoleLabel(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---- helpers ----

private fun List<PresetEntry>.replaceEntry(edited: PresetEntry): List<PresetEntry> {
    val index = indexOfFirst { it.id == edited.id }
    return if (index >= 0) {
        toMutableList().apply { set(index, edited) }
    } else {
        this + edited
    }
}

private fun PresetEntry.withEnabled(enabled: Boolean): PresetEntry = when (this) {
    is PresetEntry.Custom -> copy(enabled = enabled)
    is PresetEntry.Builtin -> copy(enabled = enabled)
    is PresetEntry.Reference -> copy(enabled = enabled)
}

@Composable
private fun presetEntryTitle(
    entry: PresetEntry,
    modeInjections: List<PromptInjection.ModeInjection>,
): String = when (entry) {
    is PresetEntry.Custom -> entry.name.ifBlank { stringResource(R.string.prompt_page_unnamed) }
    is PresetEntry.Builtin -> entry.builtinKey.ifBlank { stringResource(R.string.prompt_page_unnamed) }
    is PresetEntry.Reference -> modeInjections.firstOrNull { it.id == entry.modeInjectionId }
        ?.name?.ifBlank { stringResource(R.string.prompt_page_unnamed) }
        ?: stringResource(R.string.prompt_page_unnamed)
}

private fun InjectionPosition.usesStandaloneMessage(): Boolean = when (this) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT,
    InjectionPosition.AFTER_SYSTEM_PROMPT -> false

    InjectionPosition.TOP_OF_CHAT,
    InjectionPosition.BOTTOM_OF_CHAT,
    InjectionPosition.AT_DEPTH -> true
}

@Composable
private fun presetPositionLabel(position: InjectionPosition): String = when (position) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT -> stringResource(R.string.prompt_page_position_before_system)
    InjectionPosition.AFTER_SYSTEM_PROMPT -> stringResource(R.string.prompt_page_position_after_system)
    InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.prompt_page_position_top_of_chat)
    InjectionPosition.BOTTOM_OF_CHAT -> stringResource(R.string.prompt_page_position_bottom_of_chat)
    InjectionPosition.AT_DEPTH -> stringResource(R.string.prompt_page_position_at_depth)
}

@Composable
private fun presetRoleLabel(role: MessageRole): String = when (role) {
    MessageRole.USER -> stringResource(R.string.prompt_page_role_user)
    MessageRole.ASSISTANT -> stringResource(R.string.prompt_page_role_assistant)
    else -> role.name
}
