package me.rerere.rikkahub.ui.components.ui

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
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.PresetsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.ai.SkillsContent
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.extensions.EditQuickMessageDialog
import me.rerere.rikkahub.ui.pages.extensions.LorebookEditFullscreen
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
        skillManager.pruneOrphanedEnabledSkills()
        skills = skillManager.listSkillsForAssistant(assistant.id)
    }

    // #259: conversation-level mode injection removed; lorebook still supports conversation binding
    // via conversation.lorebookIds when conversation is provided.
    val useConversationLorebooks = conversation != null && onUpdateConversation != null
    val selectedLorebookIds = if (useConversationLorebooks) {
        conversation.lorebookIds
    } else {
        assistant.lorebookIds
    }

    // #259: 5 → 4 tabs (Independent Injections removed)
    val pagerState = rememberPagerState { 4 }

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
                                scope.launch {
                                    settingsStore.toggleAssistantPreset(
                                        assistantId = assistant.id,
                                        presetId = id,
                                        enabled = checked,
                                    )
                                }
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
                                if (useConversationLorebooks) {
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
            }
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
