package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.WORKSPACE_EDIT_FILE_TOOL
import me.rerere.rikkahub.data.model.WORKSPACE_READ_FILE_TOOL
import me.rerere.rikkahub.data.model.WORKSPACE_SHELL_TOOL
import me.rerere.rikkahub.data.model.WORKSPACE_WRITE_FILE_TOOL
import me.rerere.rikkahub.data.model.WorkspaceEnableDecision
import me.rerere.rikkahub.data.model.WorkspaceUnavailableReason
import me.rerere.rikkahub.data.model.decideWorkspaceEnable
import me.rerere.rikkahub.data.model.WorkspaceSelectionDecision
import me.rerere.rikkahub.data.model.decideWorkspaceSelection
import me.rerere.rikkahub.data.model.resolveMemoryCapabilities
import me.rerere.rikkahub.data.model.resolveWorkspaceToolCapability
import me.rerere.rikkahub.ui.components.ai.WorkspaceSelectSheet
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

/** 单个赋能工具（工具名 + 简述）。 */
private data class ToolInfo(@StringRes val descriptionRes: Int, val name: String)

/** 分类头行状态控件类型。 */
private enum class ToolControlKind { TOGGLE, ALWAYS_ON, NAVIGATE }

/** 页面渲染用的分类模型。 */
private data class ToolGroupUi(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val tools: List<ToolInfo>,
    val enabled: Boolean,
    val checked: Boolean = enabled,
    val controlEnabled: Boolean = true,
    val disabledReason: String? = null,
    val controlKind: ToolControlKind,
    val onToggle: ((Boolean) -> Unit)? = null,
    val badge: String? = null,
    val onNavigate: (() -> Unit)? = null,
)

private val WORKSPACE_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_workspace_read_file_desc, WORKSPACE_READ_FILE_TOOL),
    ToolInfo(R.string.assistant_tools_workspace_write_file_desc, WORKSPACE_WRITE_FILE_TOOL),
    ToolInfo(R.string.assistant_tools_workspace_edit_file_desc, WORKSPACE_EDIT_FILE_TOOL),
    ToolInfo(R.string.assistant_tools_workspace_shell_desc, WORKSPACE_SHELL_TOOL),
)
private val MEMORY_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_memory_desc, "memory_tool"),
)
private val MEMORY_TABLE_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_memory_table_desc, "memory_table_tool"),
)
private val SEARCH_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_search_web_desc, "search_web"),
    ToolInfo(R.string.assistant_tools_scrape_web_desc, "scrape_web"),
)
private val CONVERSATION_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_recent_chats_desc, "recent_chats"),
    ToolInfo(R.string.assistant_tools_conversation_search_desc, "conversation_search"),
)
private val SKILL_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_use_skill_desc, "use_skill"),
)
private val SUBAGENT_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_spawn_subagent_desc, "spawn_subagent"),
    ToolInfo(R.string.assistant_tools_ask_btw_desc, "ask_btw"),
    ToolInfo(R.string.assistant_tools_manage_subagent_profile_desc, "manage_subagent_profile"),
)
private val FINISH_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_finish_work_desc, "finish_work"),
)

private val ALL_TOOL_GROUPS = listOf(
    WORKSPACE_TOOLS, MEMORY_TOOLS, MEMORY_TABLE_TOOLS, SEARCH_TOOLS,
    CONVERSATION_TOOLS, SKILL_TOOLS, SUBAGENT_TOOLS, FINISH_TOOLS,
)

/**
 * 统计助手当前启用的赋能工具数量与总数，供助手配置页入口卡片显示 `已启用/总数`。
 * 搜索与完成工具始终启用。
 */
fun empowermentToolStats(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity> = emptyList(),
    memoryTableGloballyEnabled: Boolean = true,
): Pair<Int, Int> {
    val total = ALL_TOOL_GROUPS.sumOf { it.size }
    var enabled = FINISH_TOOLS.size
    val memoryCapabilities = resolveMemoryCapabilities(
        normalMemoryEnabled = assistant.enableMemory,
        settingsMemoryTableEnabled = memoryTableGloballyEnabled,
        assistantMemoryTableEnabled = assistant.enableMemoryTable,
    )
    if (assistant.enableWebSearch) enabled += SEARCH_TOOLS.size
    enabled += resolveWorkspaceToolCapability(assistant.workspaceId, workspaces).availableToolNames.size
    if (memoryCapabilities.normalMemoryEnabled) enabled += MEMORY_TOOLS.size
    if (memoryCapabilities.memoryTableEnabled) enabled += MEMORY_TABLE_TOOLS.size
    if (assistant.enableRecentChatsReference) enabled += CONVERSATION_TOOLS.size
    if (assistant.enabledSkills.isNotEmpty()) enabled += SKILL_TOOLS.size
    if (assistant.enableSubagents) enabled += SUBAGENT_TOOLS.size
    return enabled to total
}

internal data class MemoryTableToolUiState(
    val checked: Boolean,
    val active: Boolean,
    val controlEnabled: Boolean,
)

internal fun memoryTableToolUiState(
    memoryTableGloballyEnabled: Boolean,
    assistantMemoryTableEnabled: Boolean,
): MemoryTableToolUiState {
    val capabilities = resolveMemoryCapabilities(
        normalMemoryEnabled = false,
        settingsMemoryTableEnabled = memoryTableGloballyEnabled,
        assistantMemoryTableEnabled = assistantMemoryTableEnabled,
    )
    return MemoryTableToolUiState(
        checked = assistantMemoryTableEnabled,
        active = capabilities.memoryTableEnabled,
        controlEnabled = memoryTableGloballyEnabled,
    )
}

@Composable
fun AssistantToolsPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectableWorkspaces by remember { mutableStateOf<List<WorkspaceEntity>?>(null) }
    val bindingSavedMessage = stringResource(R.string.assistant_tools_workspace_binding_saved)
    val bindingSaveFailedMessage = stringResource(R.string.assistant_tools_workspace_binding_save_failed)
    val invalidWorkspaceMessage = stringResource(R.string.assistant_tools_workspace_invalid_id)

    LaunchedEffect(vm, bindingSavedMessage, bindingSaveFailedMessage) {
        vm.workspaceBindingSaveEvents.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is WorkspaceBindingSaveEvent.Success -> bindingSavedMessage
                    WorkspaceBindingSaveEvent.Failure -> bindingSaveFailedMessage
                }
            )
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    val (enabled, total) = empowermentToolStats(
                        assistant = assistant,
                        workspaces = workspaces,
                        memoryTableGloballyEnabled = settings.enableMemoryTable,
                    )
                    Text(stringResource(R.string.assistant_tools_title_with_count, enabled, total))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AssistantToolsContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            onSaveWorkspaceBinding = vm::saveWorkspaceBinding,
            onEnableWorkspace = {
                when (val decision = decideWorkspaceEnable(workspaces)) {
                    WorkspaceEnableDecision.CreateOrManage -> navController.navigate(Screen.Workspaces)
                    WorkspaceEnableDecision.Invalid -> {
                        scope.launch { snackbarHostState.showSnackbar(invalidWorkspaceMessage) }
                    }
                    is WorkspaceEnableDecision.Bind -> vm.saveWorkspaceBinding(decision.workspaceId)
                    is WorkspaceEnableDecision.Select -> selectableWorkspaces = decision.workspaces
                }
            },
            onRepairWorkspace = { workspace ->
                if (workspace == null) navController.navigate(Screen.Workspaces)
                else navController.navigate(Screen.WorkspaceDetail(workspace.id))
            },
            onSetMemoryEnabled = { vm.setMemoryEnabled(it) },
            onSetMemoryTableEnabled = { vm.setMemoryTableEnabled(it) },
            memoryTableGloballyEnabled = settings.enableMemoryTable,
            memoryTableDisabledReason = stringResource(
                R.string.assistant_page_memory_table_disabled_global
            ),
            onNavigateSkills = { navController.navigate(Screen.AssistantInjections(id)) },
        )
    }

    selectableWorkspaces?.let { selectable ->
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = selectable,
            onSelect = { selectedId ->
                selectableWorkspaces = null
                when (val selection = decideWorkspaceSelection(selectedId)) {
                    WorkspaceSelectionDecision.Cancel -> Unit
                    WorkspaceSelectionDecision.Invalid -> {
                        scope.launch { snackbarHostState.showSnackbar(invalidWorkspaceMessage) }
                    }
                    is WorkspaceSelectionDecision.Bind -> {
                        vm.saveWorkspaceBinding(selection.workspaceId)
                    }
                }
            },
            onManage = {
                selectableWorkspaces = null
                navController.navigate(Screen.Workspaces)
            },
            onDismiss = { selectableWorkspaces = null },
        )
    }
}

@Composable
private fun AssistantToolsContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    onSaveWorkspaceBinding: (kotlin.uuid.Uuid?) -> Unit,
    onEnableWorkspace: () -> Unit,
    onRepairWorkspace: (WorkspaceEntity?) -> Unit,
    onSetMemoryEnabled: (Boolean) -> Unit,
    onSetMemoryTableEnabled: (Boolean) -> Unit,
    memoryTableGloballyEnabled: Boolean,
    memoryTableDisabledReason: String,
    onNavigateSkills: () -> Unit,
) {
    val memoryTableState = memoryTableToolUiState(
        memoryTableGloballyEnabled = memoryTableGloballyEnabled,
        assistantMemoryTableEnabled = assistant.enableMemoryTable,
    )
    val workspaceCapability = resolveWorkspaceToolCapability(assistant.workspaceId, workspaces)
    val workspaceReason = when (workspaceCapability.unavailableReason) {
        null -> null
        WorkspaceUnavailableReason.UNCONFIGURED -> if (workspaces.isEmpty()) {
            stringResource(R.string.assistant_tools_workspace_create)
        } else null
        WorkspaceUnavailableReason.MISSING -> stringResource(R.string.assistant_tools_workspace_missing)
        WorkspaceUnavailableReason.DISABLED -> stringResource(R.string.assistant_tools_workspace_disabled)
        WorkspaceUnavailableReason.INSTALLING -> stringResource(R.string.assistant_tools_workspace_installing)
        WorkspaceUnavailableReason.BROKEN -> stringResource(R.string.assistant_tools_workspace_broken)
        WorkspaceUnavailableReason.UNKNOWN -> stringResource(R.string.assistant_tools_workspace_unknown)
    }
    val groups = listOf(
        ToolGroupUi(
            titleRes = R.string.assistant_page_workspace,
            icon = HugeIcons.Folder01,
            tools = WORKSPACE_TOOLS.filter { it.name in workspaceCapability.availableToolNames },
            enabled = workspaceCapability.available,
            checked = workspaceCapability.configured,
            disabledReason = workspaceReason,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on ->
                if (on) onEnableWorkspace() else onSaveWorkspaceBinding(null)
            },
            onNavigate = workspaceReason?.let { { onRepairWorkspace(workspaceCapability.workspace) } },
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_page_memory,
            icon = HugeIcons.Brain02,
            tools = MEMORY_TOOLS,
            enabled = assistant.enableMemory,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = onSetMemoryEnabled,
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_page_memory_table_title,
            icon = HugeIcons.Database02,
            tools = MEMORY_TABLE_TOOLS,
            enabled = memoryTableState.active,
            checked = memoryTableState.checked,
            controlEnabled = memoryTableState.controlEnabled,
            disabledReason = if (memoryTableState.controlEnabled) null else memoryTableDisabledReason,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = onSetMemoryTableEnabled,
        ),
        ToolGroupUi(
            titleRes = R.string.common_search,
            icon = HugeIcons.Search01,
            tools = SEARCH_TOOLS,
            enabled = assistant.enableWebSearch,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableWebSearch = on)) },
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_tools_group_conversations,
            icon = HugeIcons.Message02,
            tools = CONVERSATION_TOOLS,
            enabled = assistant.enableRecentChatsReference,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableRecentChatsReference = on)) },
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_extensions_page_tab_skills,
            icon = HugeIcons.Puzzle,
            tools = SKILL_TOOLS,
            enabled = assistant.enabledSkills.isNotEmpty(),
            controlKind = ToolControlKind.NAVIGATE,
            badge = assistant.enabledSkills.size.takeIf { it > 0 }?.toString(),
            onNavigate = onNavigateSkills,
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_page_tab_subagent,
            icon = HugeIcons.Connect,
            tools = SUBAGENT_TOOLS,
            enabled = assistant.enableSubagents,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableSubagents = on)) },
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_tools_group_finish,
            icon = HugeIcons.Tick02,
            tools = FINISH_TOOLS,
            enabled = true,
            controlKind = ToolControlKind.ALWAYS_ON,
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            ToolGroupCard(group)
        }
    }
}

@Composable
private fun ToolGroupCard(group: ToolGroupUi) {
    CardGroup(
        title = { Text(stringResource(group.titleRes)) },
    ) {
        // 分类头行：图标 + 名称 + 状态控件
        item(
            onClick = group.onNavigate,
            leadingContent = { Icon(group.icon, null) },
            headlineContent = { Text(stringResource(group.titleRes)) },
            supportingContent = {
                Text(
                    group.disabledReason
                        ?: stringResource(R.string.assistant_tools_tool_count, group.tools.size)
                )
            },
            trailingContent = {
                when (group.controlKind) {
                    ToolControlKind.TOGGLE -> Switch(
                        checked = group.checked,
                        onCheckedChange = group.onToggle,
                        enabled = group.controlEnabled,
                    )

                    ToolControlKind.ALWAYS_ON -> Tag(type = TagType.INFO) {
                        Text(stringResource(R.string.assistant_tools_always_enabled))
                    }

                    ToolControlKind.NAVIGATE -> {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (group.badge != null) {
                                Tag(type = TagType.INFO) { Text(group.badge) }
                            }
                            Icon(HugeIcons.ArrowRight01, null)
                        }
                    }
                }
            },
        )
        // 工具行：仅在分类启用时展开
        if (group.enabled) {
            group.tools.forEach { tool ->
                item(
                    headlineContent = {
                        Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                    },
                    supportingContent = {
                        Text(stringResource(tool.descriptionRes), style = MaterialTheme.typography.bodySmall)
                    },
                )
            }
        }
    }
}
