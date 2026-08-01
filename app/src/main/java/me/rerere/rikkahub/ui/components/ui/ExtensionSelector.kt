package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.datastore.withModeInjectionsPreservingPresetSnapshots
import me.rerere.rikkahub.data.datastore.boundPresetInjectionIds
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.ModeInjectionsContent
import me.rerere.rikkahub.ui.components.ai.PresetsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.ai.SkillsContent
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.extensions.EditQuickMessageDialog
import me.rerere.rikkahub.ui.pages.extensions.LorebookEditFullscreen
import me.rerere.rikkahub.ui.pages.extensions.ModeInjectionEditSheet
import org.koin.compose.koinInject


@Composable
fun ExtensionSelector(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit,
    conversation: Conversation? = null,
    onUpdateConversation: ((Conversation) -> Unit)? = null,
    onNavigateToQuickMessages: () -> Unit = {},
    onNavigateToPrompts: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
) {
    val skillManager: SkillManager = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var skills by remember { mutableStateOf<List<SkillMetadata>>(emptyList()) }

    // 点击单个条目 -> 直接打开该条目的编辑弹窗（全局条目改动落到 SettingsStore）
    val modeInjectionEditState = useEditState<PromptInjection.ModeInjection> { edited ->
        val newInjections = if (settings.modeInjections.any { it.id == edited.id }) {
            settings.modeInjections.map { if (it.id == edited.id) edited else it }
        } else {
            settings.modeInjections + edited
        }
        scope.launch {
            settingsStore.update(settings.withModeInjectionsPreservingPresetSnapshots(newInjections))
        }
    }
    val lorebookEditState = useEditState<Lorebook> { edited ->
        val newLorebooks = if (settings.lorebooks.any { it.id == edited.id }) {
            settings.lorebooks.map { if (it.id == edited.id) edited else it }
        } else {
            settings.lorebooks + edited
        }
        scope.launch { settingsStore.update(settings.copy(lorebooks = newLorebooks)) }
    }
    var editQuickMessageTarget by remember { mutableStateOf<QuickMessage?>(null) }

    LaunchedEffect(assistant.id) {
        // 打开扩展面板时清理运行时被删除的技能（残留的 enabledSkills 引用），
        // 展示列表必须包含当前助手的私有技能，和 use_skill / slash completion 保持一致。
        skillManager.pruneOrphanedEnabledSkills()
        skills = skillManager.listSkillsForAssistant(assistant.id)
    }

    val useConversationInjections =
        assistant.allowConversationPromptInjection && conversation != null && onUpdateConversation != null
    val selectedLorebookIds = if (useConversationInjections) {
        conversation.lorebookIds
    } else {
        assistant.lorebookIds
    }
    val selectedModeInjectionIds = if (useConversationInjections) {
        conversation.modeInjectionIds
    } else {
        assistant.modeInjectionIds
    }

    val pagerState = rememberPagerState { 5 }

    Column(
        modifier = modifier
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 4.dp,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(0) }
                },
                text = { Text(stringResource(R.string.extension_selector_tab_presets)) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(1) }
                },
                text = { Text(stringResource(R.string.extension_selector_tab_skills)) }
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(2) }
                },
                text = { Text(stringResource(R.string.extension_selector_tab_lorebooks)) }
            )
            Tab(
                selected = pagerState.currentPage == 3,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(3) }
                },
                text = { Text(stringResource(R.string.extension_selector_tab_quick_messages)) }
            )
            Tab(
                selected = pagerState.currentPage == 4,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(4) }
                },
                text = { Text(stringResource(R.string.extension_selector_tab_mode_injections)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> {
                    if (settings.presets.isNotEmpty()) {
                        PresetsContent(
                            presets = settings.presets,
                            selectedIds = assistant.presetIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    setOf(id)
                                } else {
                                    assistant.presetIds - id
                                }
                                onUpdate(assistant.copy(presetIds = newIds))
                            },
                            onManage = onNavigateToPrompts,
                            onEdit = {
                                navController.navigate(Screen.PresetDetail(it.id.toString()))
                            },
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_presets_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_extensions),
                            onAction = onNavigateToPrompts,
                        )
                    }
                }

                1 -> {
                    if (skills.isNotEmpty()) {
                        SkillsContent(
                            skills = skills,
                            enabledSkills = assistant.enabledSkills,
                            onToggle = { name, checked ->
                                val newSkills = if (checked) {
                                    assistant.enabledSkills + name
                                } else {
                                    assistant.enabledSkills - name
                                }
                                onUpdate(assistant.copy(enabledSkills = newSkills))
                            },
                            onManage = onNavigateToSkills,
                            onEdit = { skill ->
                                // 私有技能带 assistantId 打开，全局技能不带；
                                // 与列表 listSkillsForAssistant 一致地保留私有技能可见性
                                navController.navigate(
                                    Screen.SkillDetail(skill.name, skill.ownerAssistantId?.toString())
                                )
                            },
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_skills_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_skills),
                            onAction = onNavigateToSkills,
                        )
                    }
                }

                2 -> {
                    if (settings.lorebooks.isNotEmpty()) {
                        LorebooksContent(
                            lorebooks = settings.lorebooks,
                            selectedIds = selectedLorebookIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    selectedLorebookIds + id
                                } else {
                                    selectedLorebookIds - id
                                }
                                if (useConversationInjections) {
                                    onUpdateConversation(conversation.copy(lorebookIds = newIds))
                                } else {
                                    onUpdate(assistant.copy(lorebookIds = newIds))
                                }
                            },
                            onManage = onNavigateToPrompts,
                            onEdit = { lorebookEditState.open(it) },
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_lorebooks_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_extensions),
                            onAction = onNavigateToPrompts,
                        )
                    }
                }

                3 -> {
                    if (settings.quickMessages.isNotEmpty()) {
                        QuickMessagesContent(
                            quickMessages = settings.quickMessages,
                            selectedIds = assistant.quickMessageIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    assistant.quickMessageIds + id
                                } else {
                                    assistant.quickMessageIds - id
                                }
                                onUpdate(assistant.copy(quickMessageIds = newIds))
                            },
                            onManage = onNavigateToQuickMessages,
                            onEdit = { editQuickMessageTarget = it },
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_quick_messages_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_extensions),
                            onAction = onNavigateToQuickMessages,
                        )
                    }
                }

                4 -> {
                    // #201: 已由已绑定预设「实际投递」的注入会被 settings 清理去重，勾选必然被回滚。
                    // 这里在非会话模式下把它们过滤出「独立注入」列表，避免出现「勾了不生效」。
                    val visibleInjections = if (useConversationInjections) {
                        settings.modeInjections
                    } else {
                        val presetManagedIds = boundPresetInjectionIds(assistant.presetIds, settings.presets)
                        settings.modeInjections.filter { it.id !in presetManagedIds }
                    }
                    if (visibleInjections.isNotEmpty()) {
                        ModeInjectionsContent(
                            modeInjections = visibleInjections,
                            selectedIds = selectedModeInjectionIds,
                            onToggle = { id, checked ->
                                val newIds = if (checked) {
                                    selectedModeInjectionIds + id
                                } else {
                                    selectedModeInjectionIds - id
                                }
                                if (useConversationInjections) {
                                    onUpdateConversation(conversation.copy(modeInjectionIds = newIds))
                                } else {
                                    onUpdate(assistant.copy(modeInjectionIds = newIds))
                                }
                            },
                            onManage = onNavigateToPrompts,
                            onEdit = { modeInjectionEditState.open(it) },
                        )
                    } else {
                        ExtensionEmptyState(
                            message = stringResource(R.string.extension_selector_mode_injections_empty),
                            buttonText = stringResource(R.string.extension_selector_go_to_extensions),
                            onAction = onNavigateToPrompts,
                        )
                    }
                }
            }
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
                scope.launch {
                    settingsStore.update(
                        settings.copy(
                            quickMessages = settings.quickMessages.map {
                                if (it.id == updated.id) updated else it
                            }
                        )
                    )
                }
                editQuickMessageTarget = null
            },
        )
    }
}
