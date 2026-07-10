package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.R
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.ModeInjectionsContent
import me.rerere.rikkahub.ui.components.ai.PresetsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.extensions.LorebookEditFullscreen
import me.rerere.rikkahub.ui.pages.extensions.ModeInjectionEditSheet
import me.rerere.rikkahub.ui.pages.extensions.PresetEditSheet
import me.rerere.rikkahub.ui.pages.extensions.EditQuickMessageDialog
import me.rerere.rikkahub.ui.pages.extensions.skills.AddSkillDialog
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantExtensionsPage(id: String, initialPage: Int = 0) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val assistantPrivateSkills by vm.assistantPrivateSkills.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 3)) { 4 }
    var showAddPrivateSkillDialog by rememberSaveable { mutableStateOf(false) }
    var deletePrivateSkillTarget by remember { mutableStateOf<SkillMetadata?>(null) }

    // 点击单个扩展条目 -> 直接打开对应编辑弹窗（复用扩展管理页的编辑组件）
    val presetEditState = useEditState<Preset> { edited ->
        val newPresets = if (settings.presets.any { it.id == edited.id }) {
            settings.presets.map { if (it.id == edited.id) edited else it }
        } else {
            settings.presets + edited
        }
        vm.updateSettings(settings.copy(presets = newPresets))
    }
    val modeInjectionEditState = useEditState<PromptInjection.ModeInjection> { edited ->
        val newInjections = if (settings.modeInjections.any { it.id == edited.id }) {
            settings.modeInjections.map { if (it.id == edited.id) edited else it }
        } else {
            settings.modeInjections + edited
        }
        vm.updateSettings(settings.copy(modeInjections = newInjections))
    }
    val lorebookEditState = useEditState<Lorebook> { edited ->
        val newLorebooks = if (settings.lorebooks.any { it.id == edited.id }) {
            settings.lorebooks.map { if (it.id == edited.id) edited else it }
        } else {
            settings.lorebooks + edited
        }
        vm.updateSettings(settings.copy(lorebooks = newLorebooks))
    }
    var editQuickMessageTarget by remember { mutableStateOf<QuickMessage?>(null) }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.importAssistantSkillFromFile(context, uri) { success, message ->
            if (success) {
                toaster.show(resources.getString(R.string.skills_page_import_success, message))
            } else {
                toaster.show(resources.getString(R.string.skills_page_import_failed, message))
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_extensions_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_quick_messages)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_presets)) }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_lorebooks)) }
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_skills)) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        if (settings.quickMessages.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_quick_messages),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
                                onAction = { navController.navigate(Screen.QuickMessages) },
                            )
                        } else {
                            Column {
                                QuickMessagesContent(
                                    modifier = Modifier.weight(1f),
                                    quickMessages = settings.quickMessages,
                                    selectedIds = assistant.quickMessageIds,
                                    onToggle = { quickMessageId, checked ->
                                        val newIds = if (checked) assistant.quickMessageIds + quickMessageId
                                        else assistant.quickMessageIds - quickMessageId
                                        vm.update(assistant.copy(quickMessageIds = newIds))
                                    },
                                    onEdit = { editQuickMessageTarget = it },
                                )
                                TextButton(
                                    onClick = { navController.navigate(Screen.QuickMessages) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_extensions))
                                }
                            }
                        }
                    }

                    1 -> {
                        if (settings.presets.isEmpty() && settings.modeInjections.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_presets),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                if (settings.presets.isNotEmpty()) {
                                    PresetsContent(
                                        modifier = Modifier.weight(1f),
                                        presets = settings.presets,
                                        selectedIds = assistant.presetIds,
                                        onToggle = { presetId, checked ->
                                            val newIds = if (checked) setOf(presetId)
                                            else assistant.presetIds - presetId
                                            vm.update(assistant.copy(presetIds = newIds))
                                        },
                                        onEdit = { presetEditState.open(it) },
                                    )
                                }
                                if (settings.modeInjections.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.assistant_extensions_page_independent_injections),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ModeInjectionsContent(
                                        modifier = Modifier.weight(1f),
                                        modeInjections = settings.modeInjections,
                                        selectedIds = assistant.modeInjectionIds,
                                        onToggle = { injId, checked ->
                                            val newIds = if (checked) assistant.modeInjectionIds + injId
                                            else assistant.modeInjectionIds - injId
                                            vm.update(assistant.copy(modeInjectionIds = newIds))
                                        },
                                        onEdit = { modeInjectionEditState.open(it) },
                                    )
                                }
                                TextButton(
                                    onClick = { navController.navigate(Screen.Prompts) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
                                }
                            }
                        }
                    }

                    2 -> {
                        if (settings.lorebooks.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_lorebooks),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                LorebooksContent(
                                    modifier = Modifier.weight(1f),
                                    lorebooks = settings.lorebooks,
                                    selectedIds = assistant.lorebookIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.lorebookIds + injId
                                        else assistant.lorebookIds - injId
                                        vm.update(assistant.copy(lorebookIds = newIds))
                                    },
                                    onEdit = { lorebookEditState.open(it) },
                                )
                                TextButton(
                                    onClick = { navController.navigate(Screen.Prompts) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
                                }
                            }
                        }
                    }

                    3 -> {
                        AssistantSkillsContent(
                            skills = skills,
                            assistantPrivateSkills = assistantPrivateSkills,
                            enabledSkills = assistant.enabledSkills,
                            onToggle = { name, checked ->
                                val newSkills = if (checked) assistant.enabledSkills + name
                                else assistant.enabledSkills - name
                                vm.update(assistant.copy(enabledSkills = newSkills))
                            },
                            onCreatePrivateSkill = { showAddPrivateSkillDialog = true },
                            onImportPrivateSkill = {
                                fileImportLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    )
                                )
                            },
                            onOpenSkill = { skill ->
                                // 私有技能带 assistantId 打开，全局技能不带；保证私有技能可见且可编辑
                                navController.navigate(
                                    Screen.SkillDetail(skill.name, skill.ownerAssistantId?.toString())
                                )
                            },
                            onOpenPrivateSkill = { skill ->
                                navController.navigate(Screen.SkillDetail(skill.name, id))
                            },
                            onDeletePrivateSkill = { skill -> deletePrivateSkillTarget = skill },
                            onOpenGlobalSkills = { navController.navigate(Screen.Skills) },
                        )
                    }
                }
            }
        }
    }

    if (showAddPrivateSkillDialog) {
        AddSkillDialog(
            onDismiss = { showAddPrivateSkillDialog = false },
            onConfirm = { name, content ->
                vm.saveAssistantSkill(name, content) { success ->
                    showAddPrivateSkillDialog = false
                    if (!success) {
                        toaster.show(resources.getString(R.string.skills_page_save_failed))
                    }
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = deletePrivateSkillTarget != null,
        title = stringResource(R.string.assistant_private_skills_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deletePrivateSkillTarget?.let { vm.deleteAssistantSkill(it.name) }
            deletePrivateSkillTarget = null
        },
        onDismiss = { deletePrivateSkillTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_message, deletePrivateSkillTarget?.name ?: ""))
    }

    if (presetEditState.isEditing) {
        presetEditState.currentState?.let { state ->
            PresetEditSheet(
                preset = state,
                modeInjections = settings.modeInjections,
                onDismiss = { presetEditState.dismiss() },
                onConfirm = { presetEditState.confirm() },
                onEditPreset = { presetEditState.currentState = it },
                onUpdateModeInjections = { vm.updateSettings(settings.copy(modeInjections = it)) },
            )
        }
    }

    if (modeInjectionEditState.isEditing) {
        modeInjectionEditState.currentState?.let { state ->
            ModeInjectionEditSheet(
                injection = state,
                onDismiss = { modeInjectionEditState.dismiss() },
                onConfirm = { modeInjectionEditState.confirm() },
                onEdit = { modeInjectionEditState.currentState = it },
            )
        }
    }

    if (lorebookEditState.isEditing) {
        lorebookEditState.currentState?.let { state ->
            LorebookEditFullscreen(
                book = state,
                onDismiss = { lorebookEditState.dismiss() },
                onConfirm = { lorebookEditState.confirm() },
                onEdit = { lorebookEditState.currentState = it },
            )
        }
    }

    editQuickMessageTarget?.let { quickMessage ->
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_edit_title),
            initialQuickMessage = quickMessage,
            onDismiss = { editQuickMessageTarget = null },
            onConfirm = { title, content ->
                val updated = quickMessage.copy(title = title, content = content)
                vm.updateSettings(
                    settings.copy(
                        quickMessages = settings.quickMessages.map {
                            if (it.id == updated.id) updated else it
                        }
                    )
                )
                editQuickMessageTarget = null
            },
        )
    }
}

@Composable
private fun AssistantSkillsContent(
    skills: List<SkillMetadata>,
    assistantPrivateSkills: List<SkillMetadata>,
    enabledSkills: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreatePrivateSkill: () -> Unit,
    onImportPrivateSkill: () -> Unit,
    onOpenSkill: (SkillMetadata) -> Unit,
    onOpenPrivateSkill: (SkillMetadata) -> Unit,
    onDeletePrivateSkill: (SkillMetadata) -> Unit,
    onOpenGlobalSkills: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            SkillSectionHeader(
                title = stringResource(R.string.assistant_skills_available_section),
                description = stringResource(R.string.assistant_skills_available_section_desc),
            )
        }

        if (skills.isEmpty()) {
            item {
                ExtensionEmptyState(
                    message = stringResource(R.string.assistant_extensions_page_empty_skills),
                    buttonText = stringResource(R.string.assistant_private_skills_create),
                    onAction = onCreatePrivateSkill,
                )
            }
        } else {
            items(skills, key = { "${it.ownerAssistantId ?: "global"}:${it.name}" }) { skill ->
                SkillToggleItem(
                    skill = skill,
                    enabled = enabledSkills.contains(skill.name),
                    onToggle = { checked -> onToggle(skill.name, checked) },
                    onOpen = { onOpenSkill(skill) },
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.assistant_private_skills_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.assistant_private_skills_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onImportPrivateSkill) {
                    Icon(
                        imageVector = HugeIcons.FileImport,
                        contentDescription = stringResource(R.string.skills_page_import_from_file),
                    )
                }
                IconButton(onClick = onCreatePrivateSkill) {
                    Icon(
                        imageVector = HugeIcons.Add01,
                        contentDescription = stringResource(R.string.assistant_private_skills_create),
                    )
                }
            }
        }

        if (assistantPrivateSkills.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.assistant_private_skills_empty),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(assistantPrivateSkills, key = { it.name }) { skill ->
                PrivateSkillItem(
                    skill = skill,
                    onOpen = { onOpenPrivateSkill(skill) },
                    onDelete = { onDeletePrivateSkill(skill) },
                )
            }
        }

        item {
            TextButton(
                onClick = onOpenGlobalSkills,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.assistant_private_skills_manage_global))
            }
        }
    }
}

@Composable
private fun SkillSectionHeader(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SkillToggleItem(
    skill: SkillMetadata,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(HugeIcons.Puzzle, contentDescription = null) },
        headlineContent = { Text(skill.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (skill.isAssistantPrivate) {
                        stringResource(R.string.assistant_private_skills_badge)
                    } else {
                        stringResource(R.string.assistant_global_skills_badge)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun PrivateSkillItem(
    skill: SkillMetadata,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(HugeIcons.Puzzle, contentDescription = null) },
        headlineContent = { Text(skill.name) },
        supportingContent = {
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
