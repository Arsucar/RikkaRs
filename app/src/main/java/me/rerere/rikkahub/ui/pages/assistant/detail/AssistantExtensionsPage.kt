package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
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
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.AssistantSkillsContent
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.PresetsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.ai.SkillCard
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.extensions.LorebookEditFullscreen
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
                        if (settings.presets.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_presets),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                PresetsContent(
                                    modifier = Modifier.weight(1f),
                                    presets = settings.presets,
                                    selectedIds = assistant.presetIds,
                                    onToggle = { presetId, checked ->
                                        // #218: partial ASSISTANTS write via store
                                        vm.toggleAssistantPreset(presetId, checked)
                                    },
                                    onEdit = {
                                        navController.navigate(Screen.PresetDetail(it.id.toString()))
                                    },
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
                            onOpenGlobalSkills = { navController.navigate(Screen.Skills()) },
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

