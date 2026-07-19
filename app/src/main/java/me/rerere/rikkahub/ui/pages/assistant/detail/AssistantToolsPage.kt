package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
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
import me.rerere.rikkahub.data.model.ToolCapabilityReason
import me.rerere.rikkahub.data.model.ToolCapabilitySnapshot
import me.rerere.rikkahub.data.model.ToolCapabilitySource
import me.rerere.rikkahub.data.model.ToolPermission
import me.rerere.rikkahub.data.model.ToolPermissionPreset
import me.rerere.rikkahub.data.model.builtInToolPermissionPresets
import me.rerere.rikkahub.data.model.applyToolPermissionPreset
import me.rerere.rikkahub.data.model.diffToolPermissionPreset
import me.rerere.rikkahub.data.model.permissionPresetFromAssistant
import me.rerere.rikkahub.data.model.orphanToolPermissionIds
import me.rerere.rikkahub.data.model.stableCapabilityIdForRuntimeName
import me.rerere.rikkahub.data.model.assistantToolCapabilitySnapshot
import me.rerere.rikkahub.data.model.diagnostics
import me.rerere.rikkahub.data.model.ToolConnectionState
import me.rerere.rikkahub.data.model.ToolDiagnosticTarget
import me.rerere.rikkahub.data.model.workspaceConnectionStatus
import me.rerere.rikkahub.data.model.toToolConnectionStatus
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.files.SkillMetadata
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
import kotlin.uuid.Uuid

/** 单个赋能工具（工具名 + 简述）。 */
private data class ToolInfo(
    @StringRes val descriptionRes: Int,
    val name: String,
    val reasonCode: ToolCapabilityReason? = null,
    val capabilityId: String = "",
)

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
private val SUBAGENT_TOOLS = listOf(
    ToolInfo(R.string.assistant_tools_spawn_subagent_desc, "spawn_subagent"),
    ToolInfo(R.string.assistant_tools_ask_btw_desc, "ask_btw"),
    ToolInfo(R.string.assistant_tools_manage_subagent_profile_desc, "manage_subagent_profile"),
)

/**
 * User-facing total excludes pure catalog noise (unconfigured & ineffective rows).
 */
internal fun userFacingToolStats(snapshot: ToolCapabilitySnapshot): Pair<Int, Int> {
    val total = snapshot.capabilities.count { it.configured || it.effective }
    return snapshot.effectiveCount to total.coerceAtLeast(snapshot.effectiveCount)
}

/**
 * 统计助手当前启用的赋能工具数量与总数，供助手配置页入口卡片显示 `已启用/总数`。
 */
fun empowermentToolStats(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity> = emptyList(),
    memoryTableGloballyEnabled: Boolean = true,
    visibleSkills: List<SkillMetadata> = emptyList(),
    mcpServerConfigs: List<McpServerConfig> = emptyList(),
    mcpStatuses: Map<kotlin.uuid.Uuid, McpStatus> = emptyMap(),
): Pair<Int, Int> {
    val snapshot = assistantToolCapabilitySnapshot(
        assistant, memoryTableGloballyEnabled, workspaces, visibleSkills, mcpServerConfigs, mcpStatuses,
    )
    return userFacingToolStats(snapshot)
}

internal fun selectionModeAfterLongPress(
    selectionMode: Boolean,
    selectedIds: Set<String>,
    capabilityId: String,
): Pair<Boolean, Set<String>> {
    return true to (if (selectionMode) {
        if (capabilityId in selectedIds) selectedIds - capabilityId else selectedIds + capabilityId
    } else {
        selectedIds + capabilityId
    })
}

internal fun exitSelectionMode(): Pair<Boolean, Set<String>> = false to emptySet()

private val BUILTIN_PRESET_NAME_RES = mapOf(
    Uuid.parse("00000000-0000-0000-0000-000000000001") to R.string.assistant_tools_preset_readonly_research,
    Uuid.parse("00000000-0000-0000-0000-000000000002") to R.string.assistant_tools_preset_approval_workspace,
    Uuid.parse("00000000-0000-0000-0000-000000000003") to R.string.assistant_tools_preset_least_privilege,
)

private val BUILTIN_PRESET_DESC_RES = mapOf(
    Uuid.parse("00000000-0000-0000-0000-000000000001") to R.string.assistant_tools_preset_readonly_research_desc,
    Uuid.parse("00000000-0000-0000-0000-000000000002") to R.string.assistant_tools_preset_approval_workspace_desc,
    Uuid.parse("00000000-0000-0000-0000-000000000003") to R.string.assistant_tools_preset_least_privilege_desc,
)

@Composable
private fun toolPermissionPresetDisplayName(preset: ToolPermissionPreset): String {
    val res = BUILTIN_PRESET_NAME_RES[preset.id]
    return if (res != null) stringResource(res) else preset.name
}

@Composable
private fun toolPermissionPresetDisplayDescription(preset: ToolPermissionPreset): String {
    val res = BUILTIN_PRESET_DESC_RES[preset.id]
    return if (res != null) stringResource(res) else preset.description
}

@StringRes
private fun ToolConnectionState.stringRes(): Int = when (this) {
    ToolConnectionState.IDLE -> R.string.assistant_tools_connection_state_idle
    ToolConnectionState.CONNECTING -> R.string.assistant_tools_connection_state_connecting
    ToolConnectionState.SUCCESS -> R.string.assistant_tools_connection_state_success
    ToolConnectionState.EMPTY -> R.string.assistant_tools_connection_state_empty
    ToolConnectionState.NEEDS_AUTHORIZATION -> R.string.assistant_tools_connection_state_needs_authorization
    ToolConnectionState.NETWORK_ERROR -> R.string.assistant_tools_connection_state_network_error
    ToolConnectionState.PROTOCOL_ERROR -> R.string.assistant_tools_connection_state_protocol_error
    ToolConnectionState.ERROR -> R.string.assistant_tools_connection_state_error
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
    val vmSettings by vm.settings.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val mcpServerConfigs by vm.mcpServerConfigs.collectAsStateWithLifecycle()
    val mcpStatuses by vm.mcpStatuses.collectAsStateWithLifecycle()
    val toolConnectionStatuses by vm.toolConnectionStatuses.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val capabilitySnapshot = remember(
        assistant,
        settings.enableMemoryTable,
        workspaces,
        skills,
        mcpServerConfigs,
        mcpStatuses,
    ) {
        assistantToolCapabilitySnapshot(
            assistant,
            settings.enableMemoryTable,
            workspaces,
            skills,
            mcpServerConfigs,
            mcpStatuses,
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectableWorkspaces by remember { mutableStateOf<List<WorkspaceEntity>?>(null) }
    var selectedCapability by remember { mutableStateOf<me.rerere.rikkahub.data.model.ToolCapability?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedCapabilityIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var presetToPreview by remember { mutableStateOf<ToolPermissionPreset?>(null) }
    var showPresetSheet by remember { mutableStateOf(false) }
    var showSavePresetSheet by remember { mutableStateOf(false) }
    var showCopyTargetsSheet by remember { mutableStateOf(false) }
    var showBatchPermissionSheet by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var editingPreset by remember { mutableStateOf<ToolPermissionPreset?>(null) }
    var selectedTargetIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }
    val bindingSavedMessage = stringResource(R.string.assistant_tools_workspace_binding_saved)
    val bindingSaveFailedMessage = stringResource(R.string.assistant_tools_workspace_binding_save_failed)
    val invalidWorkspaceMessage = stringResource(R.string.assistant_tools_workspace_invalid_id)
    val permissionSavedMessage = stringResource(R.string.assistant_tools_permission_saved)
    val permissionSaveFailedMessage = stringResource(R.string.assistant_tools_permission_save_failed)
    val permissionPresets = remember(vmSettings.toolPermissionPresets) {
        builtInToolPermissionPresets() + vmSettings.toolPermissionPresets
    }
    val orphanPermissionIds = remember(assistant.toolPermissions, capabilitySnapshot) {
        orphanToolPermissionIds(
            assistant.toolPermissions,
            capabilitySnapshot.capabilities.map { it.id }.toSet(),
        )
    }

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

    LaunchedEffect(vm, permissionSavedMessage, permissionSaveFailedMessage) {
        vm.toolPermissionSaveEvents.collect { event ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (event is ToolPermissionSaveEvent.Success) permissionSavedMessage else permissionSaveFailedMessage,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    val (enabled, total) = userFacingToolStats(capabilitySnapshot)
                    Text(stringResource(R.string.assistant_tools_title_with_count, enabled, total))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (selectionMode) {
                        TextButton(onClick = {
                            val exited = exitSelectionMode()
                            selectionMode = exited.first
                            selectedCapabilityIds = exited.second
                        }) {
                            Text(stringResource(R.string.assistant_tools_selection_done))
                        }
                    } else {
                        TextButton(onClick = { showPresetSheet = true }) {
                            Text(stringResource(R.string.assistant_tools_presets_title))
                        }
                    }
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
            snapshot = capabilitySnapshot,
            memoryTableDisabledReason = stringResource(
                R.string.assistant_page_memory_table_disabled_global
            ),
            onNavigateSkills = { navController.navigate(Screen.AssistantInjections(id)) },
            onSelectCapability = { selectedCapability = it },
            selectionMode = selectionMode,
            selectedCapabilityIds = selectedCapabilityIds,
            onToggleCapabilitySelected = { capabilityId ->
                selectedCapabilityIds = if (capabilityId in selectedCapabilityIds) {
                    selectedCapabilityIds - capabilityId
                } else selectedCapabilityIds + capabilityId
            },
            onLongPressCapability = { capabilityId ->
                val next = selectionModeAfterLongPress(selectionMode, selectedCapabilityIds, capabilityId)
                selectionMode = next.first
                selectedCapabilityIds = next.second
            },
            onExitSelectionMode = {
                val exited = exitSelectionMode()
                selectionMode = exited.first
                selectedCapabilityIds = exited.second
            },
            onBatchEdit = { showBatchPermissionSheet = true },
            orphanPermissionIds = orphanPermissionIds,
            onClearOrphanPermissions = { vm.clearOrphanToolPermissions(orphanPermissionIds) },
            mcpServerConfigs = mcpServerConfigs,
            liveMcpStatuses = mcpStatuses,
            connectionStatuses = toolConnectionStatuses,
            onTestConnection = vm::testMcpConnection,
            onOpenMcp = { navController.navigate(Screen.AssistantMcp(id)) },
            permissionPresets = permissionPresets,
            onApplyPreset = { preset -> presetToPreview = preset },
            onRepairDiagnostic = { target ->
                when (target) {
                    ToolDiagnosticTarget.WORKSPACES -> navController.navigate(Screen.Workspaces)
                    ToolDiagnosticTarget.SKILLS -> navController.navigate(Screen.AssistantInjections(id))
                    ToolDiagnosticTarget.MEMORY, ToolDiagnosticTarget.MEMORY_TABLE ->
                        navController.navigate(Screen.AssistantMemory(id))
                    ToolDiagnosticTarget.MCP -> navController.navigate(Screen.AssistantMcp(id))
                    ToolDiagnosticTarget.ASSISTANT_TOOLS, ToolDiagnosticTarget.NONE -> Unit
                }
            },
        )
    }

    selectedCapability?.let { capability ->
        val currentPermission = assistant.toolPermissions[capability.id] ?: ToolPermission.INHERIT
        val sourceDefault = if (capability.approval == me.rerere.rikkahub.data.model.ToolApproval.USER) {
            stringResource(R.string.assistant_tools_permission_ask)
        } else {
            stringResource(R.string.assistant_tools_permission_allow)
        }
        val effectivePermission = when (currentPermission) {
            ToolPermission.INHERIT -> sourceDefault
            else -> toolPermissionLabel(currentPermission)
        }
        ModalBottomSheet(onDismissRequest = { selectedCapability = null }) {
            Column(Modifier.padding(16.dp)) {
                Text(capability.displayName, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.assistant_tools_permission_source_default, sourceDefault))
                Text(stringResource(R.string.assistant_tools_permission_effective, effectivePermission))
                Text(stringResource(R.string.assistant_tools_permission_next_generation))
                ToolPermission.entries.forEach { permission ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.saveToolPermission(capability.id, permission)
                            selectedCapability = null
                        }.padding(vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = permission == currentPermission, onClick = {
                            vm.saveToolPermission(capability.id, permission)
                            selectedCapability = null
                        })
                        Text(toolPermissionLabel(permission), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
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

    if (showPresetSheet) {
        ModalBottomSheet(onDismissRequest = { showPresetSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.assistant_tools_presets_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    editingPreset = null
                    presetName = ""
                    showSavePresetSheet = true
                    showPresetSheet = false
                }) {
                    Text(stringResource(R.string.assistant_tools_preset_save))
                }
                TextButton(onClick = { showCopyTargetsSheet = true; showPresetSheet = false }) {
                    Text(stringResource(R.string.assistant_tools_preset_copy))
                }
                permissionPresets.forEach { preset ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(toolPermissionPresetDisplayName(preset))
                            Text(toolPermissionPresetDisplayDescription(preset), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { presetToPreview = preset; showPresetSheet = false }) {
                            Text(stringResource(R.string.assistant_tools_preset_apply))
                        }
                        if (preset !in builtInToolPermissionPresets()) {
                            TextButton(onClick = {
                                editingPreset = preset
                                presetName = preset.name
                                showSavePresetSheet = true
                                showPresetSheet = false
                            }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { vm.deleteToolPermissionPreset(preset.id) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }

    if (showSavePresetSheet) {
        ModalBottomSheet(onDismissRequest = {
            showSavePresetSheet = false
            editingPreset = null
            presetName = ""
        }) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it.take(128) },
                    label = { Text(stringResource(R.string.assistant_tools_preset_name)) },
                    singleLine = true,
                )
                Button(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        val existing = editingPreset
                        vm.saveToolPermissionPreset(
                            if (existing == null) {
                                permissionPresetFromAssistant(
                                    presetName.trim(),
                                    assistant,
                                    capabilitySnapshot.capabilities.map { it.id }.toSet(),
                                )
                            } else existing.copy(name = presetName.trim())
                        )
                        presetName = ""
                        editingPreset = null
                        showSavePresetSheet = false
                    },
                ) { Text(stringResource(R.string.assistant_tools_preset_save)) }
            }
        }
    }

    presetToPreview?.let { preset ->
        val knownIds = capabilitySnapshot.capabilities.map { it.id }.toSet()
        val diff = diffToolPermissionPreset(
            preset,
            assistant.toolPermissions,
            knownIds,
        )
        val appliedMessage = stringResource(R.string.assistant_tools_preset_applied)
        val rejectedMessage = stringResource(R.string.assistant_tools_preset_rejected)
        ModalBottomSheet(onDismissRequest = { presetToPreview = null }) {
            Column(Modifier.padding(16.dp)) {
                Text(toolPermissionPresetDisplayName(preset), style = MaterialTheme.typography.titleMedium)
                Text(toolPermissionPresetDisplayDescription(preset), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.assistant_tools_preset_preview, diff.changed.size, diff.unknownKeys.size))
                if (diff.widensAccess) {
                    Text(stringResource(R.string.assistant_tools_preset_relaxation), color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { presetToPreview = null }) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        val result = applyToolPermissionPreset(
                            preset,
                            assistant,
                            knownIds,
                            confirmRelaxation = diff.widensAccess,
                        )
                        when (result.status) {
                            me.rerere.rikkahub.data.model.ToolPresetApplyStatus.APPLIED,
                            me.rerere.rikkahub.data.model.ToolPresetApplyStatus.SKIPPED_UNKNOWN,
                            -> {
                                vm.update(assistant.copy(toolPermissions = assistant.toolPermissions + result.changed))
                                scope.launch { snackbarHostState.showSnackbar(appliedMessage) }
                            }
                            me.rerere.rikkahub.data.model.ToolPresetApplyStatus.REJECTED_RELAXATION -> {
                                scope.launch { snackbarHostState.showSnackbar(rejectedMessage) }
                            }
                            else -> {
                                scope.launch { snackbarHostState.showSnackbar(rejectedMessage) }
                            }
                        }
                        presetToPreview = null
                    }) { Text(stringResource(R.string.assistant_tools_preset_apply)) }
                }
            }
        }
    }

    if (showBatchPermissionSheet) {
        ModalBottomSheet(onDismissRequest = { showBatchPermissionSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.assistant_tools_preset_batch), style = MaterialTheme.typography.titleMedium)
                ToolPermission.entries.forEach { permission ->
                    TextButton(onClick = {
                        scope.launch {
                            vm.batchSetToolPermissions(
                                selectedCapabilityIds,
                                permission,
                                capabilitySnapshot.capabilities.map { it.id }.toSet(),
                            )
                        }
                        showBatchPermissionSheet = false
                        val exited = exitSelectionMode()
                        selectionMode = exited.first
                        selectedCapabilityIds = exited.second
                    }) { Text(toolPermissionLabel(permission)) }
                }
            }
        }
    }

    if (showCopyTargetsSheet) {
        ModalBottomSheet(onDismissRequest = { showCopyTargetsSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.assistant_tools_preset_targets), style = MaterialTheme.typography.titleMedium)
                val resultFormat = stringResource(R.string.assistant_tools_preset_results)
                val copyPreset = permissionPresetFromAssistant(
                    "copy",
                    assistant,
                    capabilitySnapshot.capabilities.map { it.id }.toSet(),
                )
                val selectedTargets = vmSettings.assistants.filter { it.id in selectedTargetIds }
                val copyWidensAccess = selectedTargets.any { target ->
                    diffToolPermissionPreset(
                        copyPreset,
                        target.toolPermissions,
                        capabilitySnapshot.capabilities.map { it.id }.toSet(),
                    ).widensAccess
                }
                vmSettings.assistants.filter { it.id != assistant.id }.forEach { target ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedTargetIds = if (target.id in selectedTargetIds) selectedTargetIds - target.id else selectedTargetIds + target.id
                        },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(target.id in selectedTargetIds, null)
                        Text(target.name, Modifier.padding(start = 8.dp))
                    }
                }
                if (copyWidensAccess) {
                    Text(stringResource(R.string.assistant_tools_preset_relaxation), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = {
                    scope.launch {
                        val results = vm.copyToolPermissionsToAssistants(
                            assistant.id,
                            selectedTargetIds,
                            capabilitySnapshot.capabilities.map { it.id }.toSet(),
                            confirmRelaxation = copyWidensAccess,
                        )
                        snackbarHostState.showSnackbar(
                            resultFormat.format(
                                results.count { it.status in setOf(
                                    me.rerere.rikkahub.data.model.ToolPresetApplyStatus.APPLIED,
                                    me.rerere.rikkahub.data.model.ToolPresetApplyStatus.SKIPPED_UNKNOWN,
                                ) },
                                results.count { it.status !in setOf(
                                    me.rerere.rikkahub.data.model.ToolPresetApplyStatus.APPLIED,
                                    me.rerere.rikkahub.data.model.ToolPresetApplyStatus.SKIPPED_UNKNOWN,
                                ) },
                            )
                        )
                    }
                    showCopyTargetsSheet = false
                    selectedTargetIds = emptySet()
                }) { Text(stringResource(R.string.assistant_tools_preset_copy)) }
            }
        }
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
    snapshot: ToolCapabilitySnapshot,
    memoryTableDisabledReason: String,
    onNavigateSkills: () -> Unit,
    onSelectCapability: (me.rerere.rikkahub.data.model.ToolCapability) -> Unit,
    selectionMode: Boolean,
    selectedCapabilityIds: Set<String>,
    onToggleCapabilitySelected: (String) -> Unit,
    onLongPressCapability: (String) -> Unit,
    onExitSelectionMode: () -> Unit,
    onBatchEdit: () -> Unit,
    orphanPermissionIds: Set<String>,
    onClearOrphanPermissions: () -> Unit,
    mcpServerConfigs: List<McpServerConfig>,
    liveMcpStatuses: Map<kotlin.uuid.Uuid, McpStatus>,
    connectionStatuses: Map<kotlin.uuid.Uuid, me.rerere.rikkahub.data.model.ToolConnectionStatus>,
    onTestConnection: (kotlin.uuid.Uuid) -> Unit,
    onOpenMcp: () -> Unit,
    permissionPresets: List<ToolPermissionPreset>,
    onApplyPreset: (ToolPermissionPreset) -> Unit,
    onRepairDiagnostic: (ToolDiagnosticTarget) -> Unit,
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
    fun dynamicTools(
        source: ToolCapabilitySource,
        description: Int,
        configuredOnly: Boolean = true,
    ) = snapshot.capabilities
        .filter { it.source == source && (!configuredOnly || it.configured) }
        .map { ToolInfo(description, it.displayName, it.reasonCode.takeUnless { reason -> reason == ToolCapabilityReason.AVAILABLE }, it.id) }
    fun catalogTools(source: ToolCapabilitySource, tools: List<ToolInfo>) = tools.map { tool ->
        val capability = snapshot.capabilities.firstOrNull {
            it.source == source && it.runtimeName == tool.name
        }
        tool.copy(
            reasonCode = capability?.reasonCode?.takeUnless { it == ToolCapabilityReason.AVAILABLE },
            capabilityId = capability?.id.orEmpty(),
        )
    }
    val visibleEnabledSkills = snapshot.capabilities.count {
        it.source == ToolCapabilitySource.SKILL && it.id != "skill:management" && it.effective
    }
    val groups = listOf(
        ToolGroupUi(
            titleRes = R.string.assistant_page_workspace,
            icon = HugeIcons.Folder01,
            tools = catalogTools(ToolCapabilitySource.WORKSPACE, WORKSPACE_TOOLS),
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
            tools = catalogTools(ToolCapabilitySource.MEMORY, MEMORY_TOOLS),
            enabled = assistant.enableMemory,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = onSetMemoryEnabled,
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_page_memory_table_title,
            icon = HugeIcons.Database02,
            tools = catalogTools(ToolCapabilitySource.MEMORY_TABLE, MEMORY_TABLE_TOOLS),
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
            tools = catalogTools(ToolCapabilitySource.BUILTIN, SEARCH_TOOLS),
            enabled = assistant.enableWebSearch,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableWebSearch = on)) },
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_tools_group_conversations,
            icon = HugeIcons.Message02,
            tools = catalogTools(ToolCapabilitySource.BUILTIN, CONVERSATION_TOOLS),
            enabled = assistant.enableRecentChatsReference,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableRecentChatsReference = on)) },
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_extensions_page_tab_skills,
            icon = HugeIcons.Puzzle,
            tools = dynamicTools(ToolCapabilitySource.SKILL, R.string.assistant_tools_use_skill_desc),
            enabled = visibleEnabledSkills > 0,
            controlKind = ToolControlKind.NAVIGATE,
            badge = visibleEnabledSkills.takeIf { it > 0 }?.toString(),
            onNavigate = onNavigateSkills,
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_tools_group_local,
            icon = HugeIcons.Database02,
            tools = dynamicTools(ToolCapabilitySource.LOCAL, R.string.assistant_tools_local_desc) +
                dynamicTools(ToolCapabilitySource.CALENDAR, R.string.assistant_tools_local_desc),
            enabled = snapshot.capabilities.any {
                it.source in setOf(ToolCapabilitySource.LOCAL, ToolCapabilitySource.CALENDAR) && it.effective
            },
            controlKind = ToolControlKind.ALWAYS_ON,
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_tools_group_mcp,
            icon = HugeIcons.Connect,
            tools = emptyList(), // filled below after show-all toggle
            enabled = snapshot.capabilities.any { it.source == ToolCapabilitySource.MCP && it.effective },
            controlKind = ToolControlKind.ALWAYS_ON,
        ),
        ToolGroupUi(
            titleRes = R.string.assistant_page_tab_subagent,
            icon = HugeIcons.Connect,
            tools = catalogTools(ToolCapabilitySource.SUBAGENT, SUBAGENT_TOOLS),
            enabled = assistant.enableSubagents,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableSubagents = on)) },
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val clipboard = LocalClipboardManager.current
        val diagnostics = snapshot.diagnostics()
        var advancedExpanded by remember { mutableStateOf(false) }
        var diagnosticsExpanded by remember { mutableStateOf(false) }
        var problemsOnly by remember { mutableStateOf(false) }
        var showAllMcpTools by remember { mutableStateOf(false) }
        val (effectiveCount, userTotal) = userFacingToolStats(snapshot)
        val problemCount = diagnostics.diagnostics.count { !it.capability.effective }
        val diagnosticSummary = diagnostics.copySummary()
        val readableDiagnostics = stringResource(
            R.string.assistant_tools_diagnostics_summary,
            effectiveCount,
            userTotal,
        )

        if (selectionMode && selectedCapabilityIds.isNotEmpty()) {
            CardGroup(title = { Text(stringResource(R.string.assistant_tools_selected_count, selectedCapabilityIds.size)) }) {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_tools_preset_batch)) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onBatchEdit) {
                                Text(stringResource(R.string.assistant_tools_preset_batch))
                            }
                            TextButton(onClick = onExitSelectionMode) {
                                Text(stringResource(R.string.assistant_tools_selection_done))
                            }
                        }
                    },
                )
            }
        }

        groups.forEach { group ->
            val tools = if (group.titleRes == R.string.assistant_tools_group_mcp) {
                dynamicTools(
                    ToolCapabilitySource.MCP,
                    R.string.assistant_tools_mcp_desc,
                    configuredOnly = !showAllMcpTools,
                )
            } else {
                group.tools
            }
            ToolGroupCard(
                group = group.copy(tools = tools),
                snapshot = snapshot,
                onSelectCapability = onSelectCapability,
                selectionMode = selectionMode,
                selectedCapabilityIds = selectedCapabilityIds,
                onToggleCapabilitySelected = onToggleCapabilitySelected,
                onLongPressCapability = onLongPressCapability,
                footer = if (group.titleRes == R.string.assistant_tools_group_mcp) {
                    {
                        TextButton(onClick = { showAllMcpTools = !showAllMcpTools }) {
                            Text(
                                stringResource(
                                    if (showAllMcpTools) {
                                        R.string.assistant_tools_mcp_show_configured
                                    } else {
                                        R.string.assistant_tools_mcp_show_all
                                    },
                                ),
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        CardGroup(title = { Text(stringResource(R.string.assistant_tools_advanced_title)) }) {
            item(
                onClick = { advancedExpanded = !advancedExpanded },
                headlineContent = {
                    Text(
                        if (advancedExpanded) {
                            stringResource(R.string.assistant_tools_advanced_collapse)
                        } else {
                            stringResource(R.string.assistant_tools_advanced_expand)
                        },
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_tools_advanced_hint))
                },
            )
        }

        if (advancedExpanded) {
            CardGroup(title = { Text(stringResource(R.string.assistant_tools_diagnostics_title)) }) {
                item(
                    headlineContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = readableDiagnostics,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (problemCount > 0) {
                                Text(
                                    stringResource(
                                        R.string.assistant_tools_diagnostics_problems_count,
                                        problemCount,
                                    ),
                                )
                            }
                        }
                    },
                    supportingContent = {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }) {
                                Text(stringResource(R.string.assistant_tools_diagnostics_details))
                            }
                            TextButton(onClick = { problemsOnly = !problemsOnly }) {
                                Text(stringResource(R.string.assistant_tools_diagnostics_problems_only))
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(diagnosticSummary)) }) {
                                Text(stringResource(R.string.assistant_tools_diagnostics_copy))
                            }
                        }
                    },
                )
            }
            if (diagnosticsExpanded) {
                diagnostics.diagnostics
                    .filterNot { problemsOnly && it.capability.effective }
                    .forEach { diagnostic ->
                        CardGroup(title = { Text(diagnostic.capability.displayName) }) {
                            item(
                                onClick = { onRepairDiagnostic(diagnostic.repairTarget) },
                                headlineContent = {
                                    Text(stringResource(diagnostic.primaryReason.stringResource()))
                                },
                                supportingContent = {
                                    Text(
                                        diagnostic.reasonChain.joinToString(" → ") { step ->
                                            "${step.check.name}:${if (step.passed) "ok" else "no"}"
                                        },
                                    )
                                },
                            )
                        }
                    }
            }
            if (permissionPresets.isNotEmpty()) {
                CardGroup(title = { Text(stringResource(R.string.assistant_tools_presets_title)) }) {
                    permissionPresets.forEach { preset ->
                        item(
                            headlineContent = { Text(toolPermissionPresetDisplayName(preset)) },
                            supportingContent = { Text(toolPermissionPresetDisplayDescription(preset)) },
                            trailingContent = {
                                TextButton(onClick = { onApplyPreset(preset) }) {
                                    Text(stringResource(R.string.assistant_tools_preset_apply))
                                }
                            },
                        )
                    }
                }
            }
            val selectedMcpServers = mcpServerConfigs.filter { it.id in assistant.mcpServers }
            if (selectedMcpServers.isNotEmpty()) {
                CardGroup(title = { Text(stringResource(R.string.assistant_tools_connection_title)) }) {
                    selectedMcpServers.forEach { server ->
                        val enabledToolCount = server.commonOptions.tools.count { it.enable }
                        val status = connectionStatuses[server.id]
                            ?: liveMcpStatuses[server.id]?.toToolConnectionStatus(enabledToolCount)
                        val state = status?.state ?: ToolConnectionState.IDLE
                        item(
                            onClick = onOpenMcp,
                            headlineContent = { Text(server.commonOptions.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.assistant_tools_connection_status,
                                        stringResource(state.stringRes()),
                                    ) + " · " + stringResource(
                                        R.string.assistant_tools_tool_count,
                                        status?.toolCount ?: 0,
                                    ),
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    enabled = status?.state != ToolConnectionState.CONNECTING,
                                    onClick = { onTestConnection(server.id) },
                                ) { Text(stringResource(R.string.assistant_tools_connection_test)) }
                            },
                        )
                    }
                }
            }
            val workspaceStatus = workspaces.firstOrNull { it.id == assistant.workspaceId?.toString() }
                ?.let { workspaceConnectionStatus(it.shellStatus) }
            if (workspaceStatus != null) {
                CardGroup(title = { Text(stringResource(R.string.assistant_page_workspace)) }) {
                    item(
                        onClick = { onRepairWorkspace(workspaces.firstOrNull { it.id == assistant.workspaceId?.toString() }) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_workspace)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.assistant_tools_connection_status,
                                    stringResource(workspaceStatus.state.stringRes()),
                                ),
                            )
                        },
                    )
                }
            }
            if (orphanPermissionIds.isNotEmpty()) {
                CardGroup(title = { Text(stringResource(R.string.assistant_tools_permission_orphans_title)) }) {
                    item(
                        headlineContent = { Text(orphanPermissionIds.sorted().joinToString()) },
                        supportingContent = {
                            Text(stringResource(R.string.assistant_tools_permission_orphans_description))
                        },
                        trailingContent = {
                            androidx.compose.material3.TextButton(onClick = onClearOrphanPermissions) {
                                Text(stringResource(R.string.assistant_tools_permission_orphans_clear))
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolGroupCard(
    group: ToolGroupUi,
    snapshot: ToolCapabilitySnapshot,
    onSelectCapability: (me.rerere.rikkahub.data.model.ToolCapability) -> Unit,
    selectionMode: Boolean = false,
    selectedCapabilityIds: Set<String> = emptySet(),
    onToggleCapabilitySelected: (String) -> Unit = {},
    onLongPressCapability: (String) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
) {
    val showToolRows = when (group.controlKind) {
        ToolControlKind.TOGGLE -> group.checked || group.enabled
        ToolControlKind.ALWAYS_ON, ToolControlKind.NAVIGATE -> true
    }
    CardGroup(
        title = { Text(stringResource(group.titleRes)) },
    ) {
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
        if (showToolRows) {
            group.tools.forEach { tool ->
                item(
                    onClick = null,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onToggleCapabilitySelected(tool.capabilityId)
                            } else {
                                snapshot.capabilities.firstOrNull { it.id == tool.capabilityId }
                                    ?.let(onSelectCapability)
                            }
                        },
                        onLongClick = { onLongPressCapability(tool.capabilityId) },
                    ),
                    leadingContent = if (selectionMode) {
                        {
                            Checkbox(
                                checked = tool.capabilityId in selectedCapabilityIds,
                                onCheckedChange = { onToggleCapabilitySelected(tool.capabilityId) },
                            )
                        }
                    } else {
                        null
                    },
                    headlineContent = {
                        Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                    },
                    supportingContent = {
                        Column {
                            Text(
                                stringResource(tool.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            tool.reasonCode?.let { reason ->
                                Tag(type = TagType.WARNING) {
                                    Text(stringResource(reason.stringResource()))
                                }
                            }
                        }
                    },
                )
            }
        }
        footer?.let { content ->
            item(headlineContent = content)
        }
    }
}

@StringRes
private fun ToolCapabilityReason.stringResource(): Int = when (this) {
    ToolCapabilityReason.AVAILABLE -> R.string.assistant_tools_reason_available
    ToolCapabilityReason.DISABLED -> R.string.assistant_tools_reason_disabled
    ToolCapabilityReason.GLOBAL_DISABLED -> R.string.assistant_tools_reason_global_disabled
    ToolCapabilityReason.NOT_SELECTED -> R.string.assistant_tools_reason_not_selected
    ToolCapabilityReason.MISSING -> R.string.assistant_tools_reason_missing
    ToolCapabilityReason.UNAVAILABLE -> R.string.assistant_tools_reason_unavailable
    ToolCapabilityReason.CONNECTING -> R.string.assistant_tools_reason_connecting
    ToolCapabilityReason.CONNECTION_ERROR -> R.string.assistant_tools_reason_connection_error
    ToolCapabilityReason.NEEDS_AUTHORIZATION -> R.string.assistant_tools_reason_needs_authorization
    ToolCapabilityReason.DELEGATE_ONLY -> R.string.assistant_tools_reason_delegate_only
    ToolCapabilityReason.INVALID_CONFIGURATION -> R.string.assistant_tools_reason_invalid_configuration
    ToolCapabilityReason.POLICY_DENIED -> R.string.assistant_tools_reason_policy_denied
}

@Composable
private fun toolPermissionLabel(permission: ToolPermission): String = stringResource(
    when (permission) {
        ToolPermission.INHERIT -> R.string.assistant_tools_permission_inherit
        ToolPermission.ALLOW -> R.string.assistant_tools_permission_allow
        ToolPermission.ASK -> R.string.assistant_tools_permission_ask
        ToolPermission.DENY -> R.string.assistant_tools_permission_deny
    },
)
