package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.ScanEye
import com.composables.icons.lucide.Trash2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookExecutionRecord
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRunHistory
import me.rerere.rikkahub.data.model.HookRunStatus
import me.rerere.rikkahub.data.model.actionType
import me.rerere.rikkahub.data.ai.ContextPreview
import me.rerere.rikkahub.ui.components.table.DataTable
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.service.hooks.MemoryTableHookPreview
import me.rerere.rikkahub.service.hooks.HookOutputException
import me.rerere.rikkahub.utils.toLocalString
import java.time.ZoneId
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.WorkHistory

// #89: 对话级记忆表右侧抽屉。
// - 查看当前对话生效的记忆表（CONVERSATION 及继承的 ASSISTANT/GLOBAL）
// - 助手级/全局 → 对话级同步（复制内容生成 conversation scope 文档）
// - 新建对话级记忆表（基于已有模板）
// - 对对话级文档进行行级编辑并保存（打通 #84 缺失的对话级写入闭环）
private val drawerJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

// 从 schema JSON 解析出表结构（表名 + 列名列表）。解析失败返回空列表，UI 优雅降级。
private data class DrawerSchemaTable(
    val name: String,
    val columns: List<String>,
)

private fun parseDrawerSchemaTables(schemaJson: String): List<DrawerSchemaTable> {
    return runCatching {
        val root = drawerJson.parseToJsonElement(schemaJson) as? JsonObject ?: return@runCatching emptyList()
        val tables = root["tables"] as? JsonArray ?: return@runCatching emptyList()
        tables.mapNotNull { element ->
            val table = element as? JsonObject ?: return@mapNotNull null
            val name = (table["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val columns = (table["columns"] as? JsonArray)
                ?.mapNotNull { columnElement ->
                    val column = columnElement as? JsonObject ?: return@mapNotNull null
                    (column["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
                .orEmpty()
            DrawerSchemaTable(name = name, columns = columns)
        }
    }.getOrDefault(emptyList())
}

// 单个可编辑表：列 + 行（行是 列名->单元格字符串）。
private data class DrawerEditorTable(
    val name: String,
    val columns: List<String>,
    val rows: List<Map<String, String>>,
) {
    fun addRow(): DrawerEditorTable = copy(rows = rows + columns.associateWith { "" })

    fun deleteRow(index: Int): DrawerEditorTable =
        copy(rows = rows.filterIndexed { i, _ -> i != index })

    fun updateCell(rowIndex: Int, column: String, value: String): DrawerEditorTable = copy(
        rows = rows.mapIndexed { i, row ->
            if (i == rowIndex) row.toMutableMap().apply { put(column, value) } else row
        },
    )
}

// 结合 schema 与 payload 生成可编辑表列表。
private fun buildDrawerEditorTables(
    schemaJson: String,
    payloadJson: String,
): List<DrawerEditorTable> {
    val schemaTables = parseDrawerSchemaTables(schemaJson)
    val payload = runCatching { drawerJson.parseToJsonElement(payloadJson) as? JsonObject }.getOrNull()
    return schemaTables.map { table ->
        val rows = (payload?.get(table.name) as? JsonArray)
            ?.map { rowElement ->
                val rowObject = rowElement as? JsonObject ?: JsonObject(emptyMap())
                table.columns.associateWith { column ->
                    (rowObject[column] as? JsonPrimitive)?.contentOrNull
                        ?: rowObject[column]?.let { if (it is JsonPrimitive) it.contentOrNull else it.toString() }
                        ?: ""
                }
            }
            .orEmpty()
        DrawerEditorTable(name = table.name, columns = table.columns, rows = rows)
    }
}

// 将编辑后的表回写进原 payload JSON，保留 payload 中 schema 之外的字段。
private fun serializeDrawerTables(
    originalPayloadJson: String,
    tables: List<DrawerEditorTable>,
): String {
    val root = runCatching { drawerJson.parseToJsonElement(originalPayloadJson) as? JsonObject }
        .getOrNull()
        ?.toMutableMap()
        ?: mutableMapOf<String, JsonElement>()
    tables.forEach { table ->
        root[table.name] = JsonArray(
            table.rows.map { row ->
                JsonObject(table.columns.associateWith { column -> JsonPrimitive(row[column].orEmpty()) })
            },
        )
    }
    return drawerJson.encodeToString(JsonObject.serializer(), JsonObject(root))
}

private fun scopeLabel(scopeType: MemoryTableScopeType): String = when (scopeType) {
    MemoryTableScopeType.CONVERSATION -> "对话级"
    MemoryTableScopeType.ASSISTANT -> "助手级"
    MemoryTableScopeType.GLOBAL -> "全局"
}

// #89: 右侧抽屉内部的导航目标。中转菜单作为入口，后续新增功能只需在此扩展一项。
private enum class ConversationDrawerScreen {
    Menu,
    MemoryTable,
    ContextInspector,
    HookHistory,
}

/**
 * 对话侧滑抽屉的顶层内容：先呈现一层中转导航菜单，点击具体入口再进入对应功能页
 * （目前仅「对话记忆表」）。作为右侧抽屉（RTL 包裹的 ModalNavigationDrawer）的
 * drawerContent 主体。抽屉关闭时（drawerOpen 变为 false）自动重置回菜单，
 * 下次打开总是从菜单开始。
 *
 * @param drawerOpen 抽屉当前是否打开（用于关闭后重置到菜单）
 * @param onDismiss 关闭整个抽屉
 * 其余参数透传给 [ConversationMemoryTableDrawerContent]。
 */
@Composable
fun ConversationDrawerContent(
    drawerOpen: Boolean,
    documents: List<MemoryTableDocument>,
    templates: List<MemoryTableTemplate>,
    conversationId: String,
    assistantId: String,
    isolationEnabled: Boolean,
    onIsolationChange: (Boolean) -> Unit,
    onSyncToConversation: (MemoryTableDocument) -> Unit,
    onSaveConversationDocument: (MemoryTableDocument) -> Unit,
    onCreateConversationDocument: (templateId: String) -> Unit,
    onDeleteDocument: (documentId: String) -> Unit,
    onSetFollow: (documentId: String, follow: Boolean) -> Unit,
    contextPreviewState: UiState<ContextPreview>,
    onLoadContextPreview: () -> Unit,
    onClearContextPreview: () -> Unit,
    hookHistoryState: UiState<List<HookRunHistory>>,
    hookPreviewState: UiState<MemoryTableHookPreview>,
    hookManualRunState: UiState<HookExecutionRecord>,
    hooks: List<ConversationHook>,
    conversationTags: List<ConversationTag>,
    modelNames: Map<Uuid, String>,
    onPreviewHook: (Uuid) -> Unit,
    onApplyPreview: (MemoryTableHookPreview) -> Unit,
    onRunHook: (Uuid) -> Unit,
    onRetryExecution: (Uuid) -> Unit,
) {
    // onDismiss 已由中转菜单移除（不再有关闭按钮），关闭统一走遮罩点击/返回键。
    var screen by remember { mutableStateOf(ConversationDrawerScreen.Menu) }

    // 抽屉关闭后重置回菜单，保证每次打开都从中转层开始。
    LaunchedEffect(drawerOpen) {
        if (!drawerOpen) {
            screen = ConversationDrawerScreen.Menu
            onClearContextPreview()
        }
    }

    when (screen) {
        ConversationDrawerScreen.Menu -> ConversationDrawerMenu(
            onOpenMemoryTable = { screen = ConversationDrawerScreen.MemoryTable },
            onOpenHookHistory = { screen = ConversationDrawerScreen.HookHistory },
            onOpenContextInspector = {
                screen = ConversationDrawerScreen.ContextInspector
                onLoadContextPreview()
            },
        )

        ConversationDrawerScreen.MemoryTable -> ConversationMemoryTableDrawerContent(
            documents = documents,
            templates = templates,
            conversationId = conversationId,
            assistantId = assistantId,
            isolationEnabled = isolationEnabled,
            onIsolationChange = onIsolationChange,
            onBack = { screen = ConversationDrawerScreen.Menu },
            onSyncToConversation = onSyncToConversation,
            onSaveConversationDocument = onSaveConversationDocument,
            onCreateConversationDocument = onCreateConversationDocument,
            onDeleteDocument = onDeleteDocument,
            onSetFollow = onSetFollow,
        )

        ConversationDrawerScreen.ContextInspector -> ConversationContextInspector(
            state = contextPreviewState,
            onBack = {
                onClearContextPreview()
                screen = ConversationDrawerScreen.Menu
            },
            onRefresh = onLoadContextPreview,
        )

        ConversationDrawerScreen.HookHistory -> ConversationHookHistory(
            state = hookHistoryState,
            hooks = hooks,
            conversationTags = conversationTags,
            modelNames = modelNames,
            conversationId = conversationId,
            assistantId = assistantId,
            previewState = hookPreviewState,
            manualRunState = hookManualRunState,
            onPreviewHook = onPreviewHook,
            onApplyPreview = onApplyPreview,
            onRunHook = onRunHook,
            onRetryExecution = onRetryExecution,
            onBack = { screen = ConversationDrawerScreen.Menu },
        )
    }
}

@Composable
private fun ConversationHookHistory(
    state: UiState<List<HookRunHistory>>,
    hooks: List<ConversationHook>,
    conversationTags: List<ConversationTag>,
    modelNames: Map<Uuid, String>,
    conversationId: String,
    assistantId: String,
    previewState: UiState<MemoryTableHookPreview>,
    manualRunState: UiState<HookExecutionRecord>,
    onPreviewHook: (Uuid) -> Unit,
    onApplyPreview: (MemoryTableHookPreview) -> Unit,
    onRunHook: (Uuid) -> Unit,
    onRetryExecution: (Uuid) -> Unit,
    onBack: () -> Unit,
) {
    val hooksById = remember(hooks) { hooks.associateBy { it.id } }
    val tagsById = remember(conversationTags) { conversationTags.associateBy { it.id } }
    val nav = LocalNavController.current
    val syncHooks = remember(hooks) {
        hooks.filter { it.actionConfig.actionType == HookActionType.SYNC_MEMORY_TABLE }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, stringResource(R.string.context_inspector_back))
            }
            Text(stringResource(R.string.hook_history_title), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { nav.navigate(Screen.AssistantHooks(assistantId, conversationId)) }) {
                Icon(HugeIcons.WorkHistory, contentDescription = stringResource(R.string.assistant_hook_settings_title))
            }
        }
        if (syncHooks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                syncHooks.forEach { hook ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            hook.name.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.assistant_hook_unnamed),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onPreviewHook(hook.id) }) {
                            Text(stringResource(R.string.hook_sync_preview))
                        }
                        TextButton(onClick = { onRunHook(hook.id) }) {
                            Text(stringResource(R.string.hook_sync_run_now))
                        }
                    }
                }
                when (previewState) {
                    UiState.Idle -> Unit
                    UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    is UiState.Error -> Text(
                        hookSyncActionError(previewState.error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is UiState.Success -> {
                        Text(
                            stringResource(
                                R.string.hook_sync_preview_summary,
                                previewState.data.operationCount,
                                previewState.data.baseRevision,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.hook_sync_history_diff, previewState.data.diffSummaryJson),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onApplyPreview(previewState.data) }) {
                            Text(stringResource(R.string.hook_sync_apply_preview))
                        }
                    }
                }
                when (manualRunState) {
                    UiState.Idle -> Unit
                    UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    is UiState.Error -> Text(
                        hookSyncActionError(manualRunState.error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is UiState.Success -> Text(
                        stringResource(
                            R.string.hook_sync_action_success,
                            hookExecutionStatusLabel(manualRunState.data.status),
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        when (state) {
            UiState.Idle,
            UiState.Loading,
            -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.hook_history_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(HugeIcons.WorkHistory, null, modifier = Modifier.size(36.dp))
                            Text(stringResource(R.string.hook_history_empty_title))
                            Text(
                                stringResource(R.string.hook_history_empty_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.data, key = { it.run.runId }) { history ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                history.run.startedAt.atZone(ZoneId.systemDefault())
                                                    .toLocalDateTime().toLocalString(),
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.hook_history_execution_count,
                                                    history.executions.size,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        HookStatusLabel(
                                            hookRunStatusLabel(history.run.status),
                                            history.run.status.isFailure(),
                                        )
                                    }
                                    history.executions.forEach { execution ->
                                        HorizontalDivider()
                                        val hookName = hooksById[execution.hookId]?.name
                                            ?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.hook_history_deleted_hook)
                                        val modelName = modelNames[execution.modelId]
                                            ?: stringResource(R.string.hook_history_deleted_model)
                                        val tagName = execution.tagId?.let { tagsById[it]?.displayName }
                                            ?: if (execution.tagId != null) {
                                                stringResource(R.string.hook_history_deleted_tag)
                                            } else {
                                                null
                                            }
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    hookName,
                                                    modifier = Modifier.weight(1f),
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                HookStatusLabel(
                                                    hookExecutionStatusLabel(execution.status),
                                                    execution.status.isFailure(),
                                                )
                                            }
                                            Text(
                                                modelName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            execution.decision?.let { decision ->
                                                Text(
                                                    stringResource(
                                                        R.string.hook_history_decision,
                                                        hookDecisionLabel(decision),
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            tagName?.let {
                                                Text(
                                                    stringResource(R.string.hook_history_tag, it),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            execution.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                                                Text(reason, style = MaterialTheme.typography.bodySmall)
                                                if (execution.reasonTruncated) {
                                                    Text(
                                                        stringResource(R.string.hook_history_reason_truncated),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                    )
                                                }
                                            }
                                            execution.errorCode?.let { errorCode ->
                                                Text(
                                                    stringResource(
                                                        R.string.hook_history_error_detail,
                                                        hookErrorMessage(errorCode),
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            execution.sanitizedError?.takeIf { it.isNotBlank() }?.let { error ->
                                                Text(
                                                    error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            execution.duration?.let { duration ->
                                                Text(
                                                    stringResource(
                                                        R.string.hook_history_duration_ms,
                                                        duration.toMillis(),
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (execution.actionType == HookActionType.SYNC_MEMORY_TABLE) {
                                                execution.targetDocumentId?.let { targetId ->
                                                    Text(
                                                        stringResource(
                                                            R.string.hook_sync_history_target_revision,
                                                            targetId,
                                                            execution.baseRevision ?: 0,
                                                            execution.resultRevision ?: execution.baseRevision ?: 0,
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                                execution.operationSummaryJson?.let { summary ->
                                                    Text(
                                                        stringResource(R.string.hook_sync_history_operations, summary),
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                                execution.diffSummaryJson?.let { diff ->
                                                    Text(
                                                        stringResource(R.string.hook_sync_history_diff, diff),
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                                if (execution.status == HookExecutionStatus.FAILED) {
                                                    TextButton(onClick = { onRetryExecution(execution.executionId) }) {
                                                        Text(stringResource(R.string.hook_sync_retry))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun hookErrorMessage(errorCode: HookErrorCode): String = when (errorCode) {
    HookErrorCode.MODEL_NOT_FOUND -> stringResource(R.string.hook_error_model_not_found)
    HookErrorCode.PROVIDER_NOT_FOUND -> stringResource(R.string.hook_error_provider_not_found)
    HookErrorCode.MODEL_REQUEST_FAILED -> stringResource(R.string.hook_error_model_request_failed)
    HookErrorCode.HOOK_TIMEOUT -> stringResource(R.string.hook_error_hook_timeout)
    HookErrorCode.INVALID_JSON -> stringResource(R.string.hook_error_invalid_json)
    HookErrorCode.SCHEMA_MISMATCH -> stringResource(R.string.hook_error_schema_mismatch)
    HookErrorCode.TAG_NOT_ALLOWED -> stringResource(R.string.hook_error_tag_not_allowed)
    HookErrorCode.TAG_NOT_FOUND -> stringResource(R.string.hook_error_tag_not_found)
    HookErrorCode.CONVERSATION_NOT_FOUND -> stringResource(R.string.hook_error_conversation_not_found)
    HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE -> stringResource(R.string.hook_error_source_message_not_active)
    HookErrorCode.HOOK_DISABLED -> stringResource(R.string.hook_error_hook_disabled)
    HookErrorCode.MEMORY_TABLE_DISABLED -> stringResource(R.string.hook_error_memory_table_disabled)
    HookErrorCode.MEMORY_TABLE_AUTO_SYNC_DISABLED ->
        stringResource(R.string.hook_error_memory_table_auto_sync_disabled)
    HookErrorCode.MEMORY_TABLE_AUTOMATIC_DISABLED ->
        stringResource(R.string.hook_error_memory_table_automatic_disabled)
    HookErrorCode.MEMORY_TABLE_FREQUENCY_LIMIT ->
        stringResource(R.string.hook_error_memory_table_frequency_limit)
    HookErrorCode.MEMORY_TABLE_TARGET_NOT_FOUND ->
        stringResource(R.string.hook_error_memory_table_target_not_found)
    HookErrorCode.MEMORY_TABLE_TARGET_DELETED ->
        stringResource(R.string.hook_error_memory_table_target_deleted)
    HookErrorCode.MEMORY_TABLE_TARGET_CHANGED ->
        stringResource(R.string.hook_error_memory_table_target_changed)
    HookErrorCode.MEMORY_TABLE_SCOPE_FORBIDDEN ->
        stringResource(R.string.hook_error_memory_table_scope_forbidden)
    HookErrorCode.MEMORY_TABLE_REVISION_CONFLICT ->
        stringResource(R.string.hook_error_memory_table_revision_conflict)
    HookErrorCode.MEMORY_TABLE_INVALID_OPERATIONS ->
        stringResource(R.string.hook_error_memory_table_invalid_operations)
    HookErrorCode.IDEMPOTENT_REPLAY -> stringResource(R.string.hook_error_idempotent_replay)
    HookErrorCode.RETRY_NOT_ALLOWED -> stringResource(R.string.hook_error_retry_not_allowed)
    HookErrorCode.ACTION_FAILED -> stringResource(R.string.hook_error_action_failed)
}

@Composable
private fun hookSyncActionError(error: Throwable): String {
    val code = (error as? HookOutputException)?.code ?: HookErrorCode.ACTION_FAILED
    return stringResource(R.string.hook_sync_action_failed, hookErrorMessage(code))
}

@Composable
private fun HookStatusLabel(label: String, failure: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (failure) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (failure) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
private fun hookRunStatusLabel(status: HookRunStatus): String = stringResource(
    when (status) {
        HookRunStatus.QUEUED -> R.string.hook_status_queued
        HookRunStatus.RUNNING -> R.string.hook_status_running
        HookRunStatus.SUCCESS -> R.string.hook_status_success
        HookRunStatus.SKIPPED -> R.string.hook_status_skipped
        HookRunStatus.FAILED -> R.string.hook_status_failed
        HookRunStatus.CANCELLED -> R.string.hook_status_cancelled
        HookRunStatus.INTERRUPTED -> R.string.hook_status_interrupted
    }
)

@Composable
private fun hookExecutionStatusLabel(status: HookExecutionStatus): String = stringResource(
    when (status) {
        HookExecutionStatus.QUEUED -> R.string.hook_status_queued
        HookExecutionStatus.RUNNING -> R.string.hook_status_running
        HookExecutionStatus.SUCCESS -> R.string.hook_status_success
        HookExecutionStatus.SKIPPED -> R.string.hook_status_skipped
        HookExecutionStatus.FAILED -> R.string.hook_status_failed
        HookExecutionStatus.CANCELLED -> R.string.hook_status_cancelled
        HookExecutionStatus.INTERRUPTED -> R.string.hook_status_interrupted
    }
)

@Composable
private fun hookDecisionLabel(decision: HookDecision): String = stringResource(
    when (decision) {
        HookDecision.APPLY -> R.string.hook_decision_apply
        HookDecision.SKIP -> R.string.hook_decision_skip
    }
)

private fun HookRunStatus.isFailure(): Boolean = when (this) {
    HookRunStatus.FAILED,
    HookRunStatus.CANCELLED,
    HookRunStatus.INTERRUPTED,
    -> true
    else -> false
}

private fun HookExecutionStatus.isFailure(): Boolean = when (this) {
    HookExecutionStatus.FAILED,
    HookExecutionStatus.CANCELLED,
    HookExecutionStatus.INTERRUPTED,
    -> true
    else -> false
}

// #89: 中转导航菜单。列出抽屉内可进入的功能入口，后续拓展在此追加条目即可。
@Composable
private fun ConversationDrawerMenu(
    onOpenMemoryTable: () -> Unit,
    onOpenContextInspector: () -> Unit,
    onOpenHookHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConversationDrawerMenuItem(
            icon = Lucide.Database,
            title = "对话记忆表",
            subtitle = "查看并管理当前对话生效的记忆表",
            onClick = onOpenMemoryTable,
        )
        ConversationDrawerMenuItem(
            icon = Lucide.ScanEye,
            title = stringResource(R.string.context_inspector_menu_title),
            subtitle = stringResource(R.string.context_inspector_menu_subtitle),
            onClick = onOpenContextInspector,
        )
        ConversationDrawerMenuItem(
            icon = HugeIcons.WorkHistory,
            title = stringResource(R.string.hook_history_menu_title),
            subtitle = stringResource(R.string.hook_history_menu_subtitle),
            onClick = onOpenHookHistory,
        )
    }
}

@Composable
private fun ConversationDrawerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Lucide.ChevronRight, contentDescription = null)
        }
    }
}

/**
 * 对话级记忆表抽屉内容主体。作为右侧抽屉（RTL 包裹的 ModalNavigationDrawer）的
 * drawerContent 使用，不再自带遮罩/滑动覆盖层壳。
 *
 * @param documents 当前对话生效的记忆表文档（含继承的助手级/全局）
 * @param templates 可用模板（用于新建对话级文档时选择）
 * @param conversationId 当前对话 id（编辑对话级文档跳转全屏编辑页时透传）
 * @param assistantId 当前对话所属助手 id（全屏编辑页构建 AssistantDetailVM 需要）
 * @param onSyncToConversation 将某个助手级/全局文档同步到对话级
 * @param onSaveConversationDocument 保存（编辑后的）对话级文档
 * @param onCreateConversationDocument 基于模板新建对话级文档
 * @param onDeleteDocument 删除对话级文档
 * @param onSetFollow 设置对话级文档是否跟随其来源助手级文档（false = 断开跟随）
 */
@Composable
fun ConversationMemoryTableDrawerContent(
    documents: List<MemoryTableDocument>,
    templates: List<MemoryTableTemplate>,
    conversationId: String,
    assistantId: String,
    isolationEnabled: Boolean,
    onIsolationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSyncToConversation: (MemoryTableDocument) -> Unit,
    onSaveConversationDocument: (MemoryTableDocument) -> Unit,
    onCreateConversationDocument: (templateId: String) -> Unit,
    onDeleteDocument: (documentId: String) -> Unit,
    onSetFollow: (documentId: String, follow: Boolean) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    // 助手级/全局（继承来源，仅列名称 + 同步按钮）与对话级（可编辑）分区展示。
    val conversationDocuments = documents.filter { it.scopeType == MemoryTableScopeType.CONVERSATION }
    val inheritedDocuments = documents.filter { it.scopeType != MemoryTableScopeType.CONVERSATION }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 返回中转菜单
                IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = "返回")
                }
                Icon(Lucide.Database, contentDescription = null)
                Text(
                    text = "对话记忆表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        // 隔离开关：开启后仅注入对话级记忆表，屏蔽助手级/全局，避免双侧重复注入。
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "仅本对话记忆表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "开启后只注入对话级记忆表，助手级/全局需手动同步，避免重复注入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isolationEnabled,
                    onCheckedChange = onIsolationChange,
                )
            }
        }

        OutlinedButton(
            onClick = { showCreateDialog = true },
            enabled = templates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Lucide.Plus, contentDescription = null)
            Text(
                text = if (templates.isEmpty()) "暂无可用模板" else "新建对话级记忆表",
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        HorizontalDivider()

        if (documents.isEmpty()) {
            Text(
                text = "当前对话没有生效的记忆表。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 助手级/全局：仅列名称 + 同步按钮（隔离开启时灰显提示不再注入）。
                if (inheritedDocuments.isNotEmpty()) {
                    Text(
                        text = if (isolationEnabled) "助手级/全局（已隔离，需同步后生效）" else "助手级/全局（可同步）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    inheritedDocuments.forEach { document ->
                        val template = templates.firstOrNull { it.id == document.templateId }
                        InheritedMemoryTableRow(
                            document = document,
                            template = template,
                            onSyncToConversation = { onSyncToConversation(document) },
                        )
                    }
                }

                // 对话级：可编辑卡片。
                if (conversationDocuments.isNotEmpty()) {
                    Text(
                        text = "对话级（可编辑）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    conversationDocuments.forEach { document ->
                        val template = templates.firstOrNull { it.id == document.templateId }
                        MemoryTableDocumentCard(
                            document = document,
                            template = template,
                            conversationId = conversationId,
                            assistantId = assistantId,
                            onSyncToConversation = { onSyncToConversation(document) },
                            onSaveConversationDocument = onSaveConversationDocument,
                            onDeleteDocument = { onDeleteDocument(document.id) },
                            onSetFollow = { follow -> onSetFollow(document.id, follow) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateConversationMemoryTableDialog(
            templates = templates,
            onDismiss = { showCreateDialog = false },
            onConfirm = { templateId ->
                onCreateConversationDocument(templateId)
                showCreateDialog = false
            },
        )
    }
}

// #89: 助手级/全局记忆表在对话抽屉里只列名称 + 同步按钮，不展开表格内容；
// 用户点「同步到对话」才把内容复制成对话级文档并暴露编辑。
@Composable
private fun InheritedMemoryTableRow(
    document: MemoryTableDocument,
    template: MemoryTableTemplate?,
    onSyncToConversation: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Lucide.Database, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template?.name?.ifBlank { template.id } ?: document.templateId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${scopeLabel(document.scopeType)} · rev ${document.revision}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSyncToConversation) {
                Icon(Lucide.Copy, contentDescription = "同步到对话")
            }
        }
    }
}

@Composable
private fun MemoryTableDocumentCard(
    document: MemoryTableDocument,
    template: MemoryTableTemplate?,
    conversationId: String,
    assistantId: String,
    onSyncToConversation: () -> Unit,
    onSaveConversationDocument: (MemoryTableDocument) -> Unit,
    onDeleteDocument: () -> Unit,
    onSetFollow: (follow: Boolean) -> Unit,
) {
    val navController = LocalNavController.current
    val isConversationScope = document.scopeType == MemoryTableScopeType.CONVERSATION
    // 跟随来源的对话级文档：payload 只是助手级来源的镜像，只读，可断开跟随后独立编辑。
    val isFollowing = isConversationScope && document.followSource && document.sourceDocumentId != null
    // 可行级编辑的条件：对话级 且 未处于跟随状态。
    val editable = isConversationScope && !isFollowing
    val schemaJson = template?.schemaJson ?: "{}"

    // 对话级文档可编辑：维护本地可编辑表状态；其它 scope（含跟随中的对话级）只读展示。
    var editorTables by remember(document.id, document.updatedAt, schemaJson) {
        mutableStateOf(buildDrawerEditorTables(schemaJson, document.payloadJson))
    }
    var dirty by remember(document.id, document.updatedAt) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template?.name?.ifBlank { template.id } ?: document.templateId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${scopeLabel(document.scopeType)} · rev ${document.revision}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isConversationScope) {
                    // 对话级：可跳转全屏编辑页（携带 conversationId）+ 删除
                    IconButton(
                        onClick = {
                            navController.navigate(
                                Screen.AssistantMemoryTableDocumentEditor(
                                    documentId = document.id,
                                    templateId = document.templateId,
                                    assistantId = assistantId,
                                    scopeType = MemoryTableScopeType.CONVERSATION,
                                    conversationId = conversationId,
                                )
                            )
                        },
                    ) {
                        Icon(Lucide.Pencil, contentDescription = "全屏编辑")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Lucide.Trash2, contentDescription = "删除")
                    }
                } else {
                    // 助手级/全局 → 对话级同步
                    IconButton(onClick = onSyncToConversation) {
                        Icon(Lucide.Copy, contentDescription = "同步到对话")
                    }
                }
            }

            // 对话级 + 有来源：显示跟随状态与断开跟随开关。
            if (isConversationScope && document.sourceDocumentId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Lucide.Link,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            text = if (isFollowing) "跟随助手级（只读镜像）" else "已独立",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isFollowing,
                        onCheckedChange = { checked -> onSetFollow(checked) },
                    )
                }
            }

            if (editorTables.isEmpty()) {
                Text(
                    text = if (template == null) {
                        "模板缺失，无法解析表结构。"
                    } else {
                        "该记忆表暂无可展示的表结构。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            editorTables.forEachIndexed { tableIndex, table ->
                MemoryTableView(
                    table = table,
                    editable = editable,
                    onChange = { updated ->
                        editorTables = editorTables.mapIndexed { i, current ->
                            if (i == tableIndex) updated else current
                        }
                        dirty = true
                    },
                )
            }

            if (editable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            val payload = serializeDrawerTables(document.payloadJson, editorTables)
                            onSaveConversationDocument(document.copy(payloadJson = payload))
                            dirty = false
                        },
                        enabled = dirty,
                    ) {
                        Icon(Lucide.Save, contentDescription = null)
                        Text("保存", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.assistant_page_memory_table_move_to_trash_title)) },
            text = { Text(stringResource(R.string.assistant_page_memory_table_move_to_trash_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteDocument()
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_memory_table_trash))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
@Composable
private fun MemoryTableView(
    table: DrawerEditorTable,
    editable: Boolean,
    onChange: (DrawerEditorTable) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = table.name,
            style = MaterialTheme.typography.labelLarge,
        )

        if (table.columns.isEmpty()) {
            Text(
                text = "（无列定义）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val extraColumn = if (editable) 1 else 0
        val columnMinWidths = List(table.columns.size) { 88.dp } + List(extraColumn) { 40.dp }
        val columnMaxWidths = List(table.columns.size) { 240.dp } + List(extraColumn) { 40.dp }

        val headers = buildList<@Composable () -> Unit> {
            table.columns.forEach { column ->
                add {
                    Text(
                        text = column,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            if (editable) {
                add { Box(modifier = Modifier.padding(4.dp)) }
            }
        }

        val rows = table.rows.mapIndexed { rowIndex, row ->
            buildList<@Composable () -> Unit> {
                table.columns.forEach { column ->
                    add {
                        if (editable) {
                            DrawerCellTextField(
                                value = row[column].orEmpty(),
                                onValueChange = { value ->
                                    onChange(table.updateCell(rowIndex, column, value))
                                },
                            )
                        } else {
                            Text(
                                text = row[column].orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                if (editable) {
                    add {
                        IconButton(onClick = { onChange(table.deleteRow(rowIndex)) }) {
                            Icon(Lucide.Trash2, contentDescription = "删除行")
                        }
                    }
                }
            }
        }

        DataTable(
            headers = headers,
            rows = rows,
            columnMinWidths = columnMinWidths,
            columnMaxWidths = columnMaxWidths,
            cellPadding = 0.dp,
            stretchToFillWidth = false,
        )

        if (editable) {
            TextButton(onClick = { onChange(table.addRow()) }) {
                Icon(Lucide.Plus, contentDescription = null)
                Text("新增行", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun DrawerCellTextField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 72.dp, max = 240.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun CreateConversationMemoryTableDialog(
    templates: List<MemoryTableTemplate>,
    onDismiss: () -> Unit,
    onConfirm: (templateId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建对话级记忆表") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "选择一个模板，为当前对话创建一个空的对话级记忆表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                templates.forEach { template ->
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onConfirm(template.id) },
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = template.name.ifBlank { template.id },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (template.description.isNotBlank()) {
                                Text(
                                    text = template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
