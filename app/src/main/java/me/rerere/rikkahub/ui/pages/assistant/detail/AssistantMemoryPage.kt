package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_SCHEMA_JSON
import me.rerere.rikkahub.data.model.MemoryScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val memoryTableTemplates by vm.memoryTableTemplates.collectAsStateWithLifecycle()
    val memoryTableDocuments by vm.memoryTableDocuments.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_memory))
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
    ) { innerPadding ->
        AssistantMemoryContent(
            modifier = Modifier.padding(innerPadding),
            settings = settings,
            assistant = assistant,
            memories = memories,
            memoryTableTemplates = memoryTableTemplates,
            memoryTableDocuments = memoryTableDocuments,
            onUpdateSettings = { vm.updateSettings(it) },
            onUpdateAssistant = { vm.update(it) },
            onDeleteMemory = { vm.deleteMemory(it) },
            onAddMemory = { vm.addMemory(it) },
            onUpdateMemory = { vm.updateMemory(it) },
            onUpsertMemoryTableTemplate = { vm.upsertMemoryTableTemplate(it) },
            onDeleteMemoryTableTemplate = { vm.deleteMemoryTableTemplate(it) },
            onUpsertMemoryTableDocument = { vm.upsertMemoryTableDocument(it) },
            onDeleteMemoryTableDocument = { vm.deleteMemoryTableDocument(it) },
        )
    }
}

@Composable
private fun AssistantMemoryContent(
    modifier: Modifier = Modifier,
    settings: Settings,
    assistant: Assistant,
    memories: List<AssistantMemory>,
    memoryTableTemplates: List<MemoryTableTemplate>,
    memoryTableDocuments: List<MemoryTableDocument>,
    onUpdateSettings: (Settings) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
    onUpsertMemoryTableTemplate: (MemoryTableTemplate) -> Unit,
    onDeleteMemoryTableTemplate: (MemoryTableTemplate) -> Unit,
    onUpsertMemoryTableDocument: (MemoryTableDocument) -> Unit,
    onDeleteMemoryTableDocument: (MemoryTableDocument) -> Unit,
) {
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    val memoryTableTemplateDialogState = useEditState<MemoryTableTemplate> {
        onUpsertMemoryTableTemplate(it)
    }
    var pendingDeleteMemory by remember { mutableStateOf<AssistantMemory?>(null) }
    var pendingDeleteMemoryTableTemplate by remember { mutableStateOf<MemoryTableTemplate?>(null) }
    var pendingDeleteMemoryTableDocument by remember { mutableStateOf<MemoryTableDocument?>(null) }
    var editingMemoryTableDocument by remember { mutableStateOf<MemoryTableDocument?>(null) }
    val defaultMemoryTableTemplateName = stringResource(R.string.assistant_page_memory_table_default_template)

    // 记忆对话框
    memoryDialogState.EditStateContent { memory, update ->
        AlertDialog(
            onDismissRequest = {
                memoryDialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.assistant_page_manage_memory_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = memory.content,
                        onValueChange = {
                            update(memory.copy(content = it))
                        },
                        label = {
                            Text(stringResource(R.string.assistant_page_manage_memory_title))
                        },
                        minLines = 2,
                        maxLines = 8
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.assistant_page_memory_scope_global),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.assistant_page_memory_scope_global_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = memory.scope == MemoryScope.GLOBAL,
                            onCheckedChange = { enabled ->
                                update(
                                    memory.copy(
                                        scope = if (enabled) MemoryScope.GLOBAL else MemoryScope.ASSISTANT,
                                    )
                                )
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    memoryTableTemplateDialogState.EditStateContent { template, update ->
        AlertDialog(
            onDismissRequest = { memoryTableTemplateDialogState.dismiss() },
            title = {
                Text(stringResource(R.string.assistant_page_memory_table_template))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = template.name,
                        onValueChange = { update(template.copy(name = it)) },
                        label = { Text(stringResource(R.string.assistant_page_memory_table_template_name)) },
                        singleLine = true,
                    )
                    TextField(
                        value = template.schemaJson,
                        onValueChange = { update(template.copy(schemaJson = it)) },
                        label = { Text(stringResource(R.string.assistant_page_memory_table_schema_json)) },
                        minLines = 6,
                        maxLines = 12,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { memoryTableTemplateDialogState.confirm() }) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryTableTemplateDialogState.dismiss() }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    editingMemoryTableDocument?.let { document ->
        val template = memoryTableTemplates.firstOrNull { it.id == document.templateId }
            ?: MemoryTableTemplate(
                id = document.templateId,
                name = document.templateId,
                schemaJson = DEFAULT_MEMORY_TABLE_SCHEMA_JSON,
            )
        MemoryTableDocumentEditorDialog(
            document = document,
            template = template,
            assistantId = assistant.id.toString(),
            onDismiss = { editingMemoryTableDocument = null },
            onSave = {
                onUpsertMemoryTableDocument(it)
                editingMemoryTableDocument = null
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_memory_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableMemory,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableMemory = it
                                )
                            )
                        }
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_table_global)) },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_memory_table_global_desc))
                },
                trailingContent = {
                    Switch(
                        checked = settings.enableMemoryTable,
                        onCheckedChange = {
                            onUpdateSettings(
                                settings.copy(
                                    enableMemoryTable = it,
                                    memoryTableAutoSyncEnabled = false,
                                )
                            )
                        }
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_table_assistant)) },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_memory_table_assistant_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableMemoryTable,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableMemoryTable = it
                                )
                            )
                        },
                        enabled = settings.enableMemoryTable,
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_table_auto_sync)) },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_memory_table_auto_sync_desc))
                },
                trailingContent = {
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_global_memory)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_global_memory_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.useGlobalMemory,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    useGlobalMemory = it
                                )
                            )
                        },
                        enabled = assistant.enableMemory
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_recent_chats)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_recent_chats_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableRecentChatsReference,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableRecentChatsReference = it
                                )
                            )
                        }
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_time_reminder_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableTimeReminder,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableTimeReminder = it
                                )
                            )
                        }
                    )
                }
            )
        }

        MemoryTableSection(
            settings = settings,
            assistant = assistant,
            templates = memoryTableTemplates,
            documents = memoryTableDocuments,
            onAddTemplate = {
                memoryTableTemplateDialogState.open(
                    MemoryTableTemplate(name = defaultMemoryTableTemplateName)
                )
            },
            onEditTemplate = { memoryTableTemplateDialogState.open(it) },
            onDeleteTemplate = { pendingDeleteMemoryTableTemplate = it },
            onAddDocument = { template ->
                editingMemoryTableDocument =
                    MemoryTableDocument(
                        templateId = template.id,
                        scopeType = MemoryTableScopeType.ASSISTANT,
                        scopeId = assistant.id.toString(),
                    )
            },
            onEditDocument = { editingMemoryTableDocument = it },
            onDeleteDocument = { pendingDeleteMemoryTableDocument = it },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_page_manage_memory_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterStart)
            )

            IconButton(
                onClick = {
                    memoryDialogState.open(
                        AssistantMemory(
                            id = 0,
                            content = "",
                            scope = if (assistant.useGlobalMemory) MemoryScope.GLOBAL else MemoryScope.ASSISTANT,
                        )
                    )
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = null
                )
            }
        }

        memories.fastForEach { memory ->
            key(memory.id) {
                MemoryItem(
                    memory = memory,
                    onEditMemory = {
                        memoryDialogState.open(it)
                    },
                    onDeleteMemory = {
                        pendingDeleteMemory = it
                    }
                )
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.content.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )

    RikkaConfirmDialog(
        show = pendingDeleteMemoryTableTemplate != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemoryTableTemplate?.let(onDeleteMemoryTableTemplate)
            pendingDeleteMemoryTableTemplate = null
        },
        onDismiss = { pendingDeleteMemoryTableTemplate = null },
        text = {
            Text(pendingDeleteMemoryTableTemplate?.name.orEmpty())
        }
    )

    RikkaConfirmDialog(
        show = pendingDeleteMemoryTableDocument != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemoryTableDocument?.let(onDeleteMemoryTableDocument)
            pendingDeleteMemoryTableDocument = null
        },
        onDismiss = { pendingDeleteMemoryTableDocument = null },
        text = {
            Text(
                text = pendingDeleteMemoryTableDocument?.payloadJson.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryTableDocumentEditorDialog(
    document: MemoryTableDocument,
    template: MemoryTableTemplate,
    assistantId: String,
    onDismiss: () -> Unit,
    onSave: (MemoryTableDocument) -> Unit,
) {
    var draft by remember(document.id) { mutableStateOf(document) }
    var mode by remember(document.id) { mutableStateOf(MemoryTableEditorMode.Table) }
    var jsonText by remember(document.id) { mutableStateOf(document.payloadJson) }
    val initialTables = remember(document.id, template.schemaJson, document.payloadJson) {
        parseMemoryTableEditorTables(template.schemaJson, document.payloadJson)
    }
    var tableState by remember(document.id, template.schemaJson, document.payloadJson) {
        mutableStateOf(initialTables.getOrNull().orEmpty())
    }
    var editorError by remember(document.id, template.schemaJson, document.payloadJson) {
        mutableStateOf(initialTables.exceptionOrNull()?.message)
    }

    fun updateTables(tables: List<MemoryTableEditorTable>) {
        tableState = tables
        serializeMemoryTablePayload(jsonText, tables)
            .onSuccess {
                jsonText = it
                editorError = null
            }
            .onFailure {
                editorError = it.message
            }
    }

    fun switchToTableMode() {
        parseMemoryTableEditorTables(template.schemaJson, jsonText)
            .onSuccess {
                tableState = it
                editorError = null
                mode = MemoryTableEditorMode.Table
            }
            .onFailure {
                editorError = it.message
                mode = MemoryTableEditorMode.Json
            }
    }

    fun saveDraft() {
        val payloadResult = if (mode == MemoryTableEditorMode.Json) {
            validateMemoryTablePayloadJson(jsonText).map { jsonText.trim() }
        } else {
            serializeMemoryTablePayload(jsonText, tableState)
        }
        payloadResult
            .onSuccess { onSave(draft.copy(payloadJson = it)) }
            .onFailure { editorError = it.message }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.assistant_page_memory_table_document),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.assistant_page_memory_table_template_ref,
                                    template.name.ifBlank { template.id },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(onClick = { saveDraft() }) {
                            Text(stringResource(R.string.common_save))
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = mode == MemoryTableEditorMode.Table,
                            onClick = { switchToTableMode() },
                            label = { Text(stringResource(R.string.assistant_page_memory_table_mode_table)) },
                        )
                        FilterChip(
                            selected = mode == MemoryTableEditorMode.Json,
                            onClick = {
                                serializeMemoryTablePayload(jsonText, tableState)
                                    .onSuccess {
                                        jsonText = it
                                        editorError = null
                                    }
                                    .onFailure {
                                        editorError = it.message
                                    }
                                mode = MemoryTableEditorMode.Json
                            },
                            label = { Text(stringResource(R.string.assistant_page_memory_table_mode_json)) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.assistant_page_memory_scope_global),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.assistant_page_memory_scope_global_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.scopeType == MemoryTableScopeType.GLOBAL,
                            onCheckedChange = { enabled ->
                                draft = draft.copy(
                                    scopeType = if (enabled) {
                                        MemoryTableScopeType.GLOBAL
                                    } else {
                                        MemoryTableScopeType.ASSISTANT
                                    },
                                    scopeId = if (enabled) {
                                        MemoryRepository.GLOBAL_MEMORY_ID
                                    } else {
                                        assistantId
                                    },
                                )
                            },
                            enabled = draft.scopeType != MemoryTableScopeType.CONVERSATION,
                        )
                    }

                    if (draft.scopeType == MemoryTableScopeType.CONVERSATION) {
                        Text(
                            text = stringResource(R.string.assistant_page_memory_table_conversation_scope_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    editorError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (mode == MemoryTableEditorMode.Table) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (tableState.isEmpty() && editorError == null) {
                                Text(
                                    text = stringResource(R.string.assistant_page_memory_table_empty_schema),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            tableState.forEachIndexed { tableIndex, table ->
                                MemoryTableEditableTable(
                                    table = table,
                                    onChange = { updated ->
                                        updateTables(
                                            tableState.mapIndexed { index, current ->
                                                if (index == tableIndex) updated else current
                                            }
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        TextField(
                            value = jsonText,
                            onValueChange = {
                                jsonText = it
                                editorError = validateMemoryTablePayloadJson(it).exceptionOrNull()?.message
                            },
                            label = { Text(stringResource(R.string.assistant_page_memory_table_payload_json)) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTableEditableTable(
    table: MemoryTableEditorTable,
    onChange: (MemoryTableEditorTable) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = table.name,
            style = MaterialTheme.typography.titleSmall,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                table.columns.forEach { column ->
                    Text(
                        text = column.name,
                        modifier = Modifier.width(column.editorWidth()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(modifier = Modifier.width(48.dp))
            }
            table.rows.forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    table.columns.forEach { column ->
                        OutlinedTextField(
                            value = row[column.name].orEmpty(),
                            onValueChange = { value ->
                                onChange(table.updateCell(rowIndex, column.name, value))
                            },
                            modifier = Modifier
                                .width(column.editorWidth())
                                .heightIn(min = if (column.type == "text") 96.dp else 56.dp),
                            minLines = if (column.type == "text") 3 else 1,
                            maxLines = if (column.type == "text") 6 else 1,
                        )
                    }
                    IconButton(
                        onClick = { onChange(table.deleteRow(rowIndex)) },
                        modifier = Modifier.width(48.dp),
                    ) {
                        Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.assistant_page_delete))
                    }
                }
            }
        }
        TextButton(onClick = { onChange(table.addRow()) }) {
            Text(stringResource(R.string.assistant_page_memory_table_add_row))
        }
    }
}

@Composable
private fun MemoryTableSection(
    settings: Settings,
    assistant: Assistant,
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    onAddTemplate: () -> Unit,
    onEditTemplate: (MemoryTableTemplate) -> Unit,
    onDeleteTemplate: (MemoryTableTemplate) -> Unit,
    onAddDocument: (MemoryTableTemplate) -> Unit,
    onEditDocument: (MemoryTableDocument) -> Unit,
    onDeleteDocument: (MemoryTableDocument) -> Unit,
) {
    val enabled = settings.enableMemoryTable && assistant.enableMemoryTable
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.assistant_page_memory_table_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterStart)
        )
        IconButton(
            onClick = onAddTemplate,
            enabled = settings.enableMemoryTable,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(HugeIcons.Add01, null)
        }
    }

    templates.fastForEach { template ->
        key(template.id) {
            MemoryTableTemplateItem(
                template = template,
                enabled = settings.enableMemoryTable,
                onEdit = { onEditTemplate(template) },
                onDelete = { onDeleteTemplate(template) },
                onAddDocument = { onAddDocument(template) },
            )
        }
    }

    documents.fastForEach { document ->
        key(document.id) {
            MemoryTableDocumentItem(
                document = document,
                enabled = enabled,
                onEdit = { onEditDocument(document) },
                onDelete = { onDeleteDocument(document) },
            )
        }
    }
}

@Composable
private fun MemoryTableTemplateItem(
    template: MemoryTableTemplate,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddDocument: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = template.schemaJson,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAddDocument, enabled = enabled) {
                Icon(HugeIcons.Add01, null)
            }
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(HugeIcons.PencilEdit01, null)
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(HugeIcons.Delete01, null)
            }
        }
    }
}

@Composable
private fun MemoryTableDocumentItem(
    document: MemoryTableDocument,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.assistant_page_memory_table_document_meta,
                        document.scopeType.name,
                        document.revision,
                    ),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = document.payloadJson,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(HugeIcons.PencilEdit01, null)
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(HugeIcons.Delete01, null)
            }
        }
    }
}

private enum class MemoryTableEditorMode {
    Table,
    Json,
}

private data class MemoryTableSchemaTable(
    val name: String,
    val columns: List<MemoryTableSchemaColumn>,
)

private data class MemoryTableSchemaColumn(
    val name: String,
    val type: String,
)

private data class MemoryTableEditorTable(
    val schema: MemoryTableSchemaTable,
    val rows: List<Map<String, String>>,
) {
    val name: String = schema.name
    val columns: List<MemoryTableSchemaColumn> = schema.columns

    fun addRow(): MemoryTableEditorTable {
        return copy(rows = rows + columns.associate { it.name to "" })
    }

    fun deleteRow(index: Int): MemoryTableEditorTable {
        return copy(rows = rows.filterIndexed { rowIndex, _ -> rowIndex != index })
    }

    fun updateCell(rowIndex: Int, columnName: String, value: String): MemoryTableEditorTable {
        return copy(
            rows = rows.mapIndexed { index, row ->
                if (index == rowIndex) {
                    row.toMutableMap().apply {
                        put(columnName, value)
                    }
                } else {
                    row
                }
            }
        )
    }
}

private val memoryTableEditorJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

private fun parseMemoryTableEditorTables(
    schemaJson: String,
    payloadJson: String,
): Result<List<MemoryTableEditorTable>> = runCatching {
    val schemaTables = parseMemoryTableSchema(schemaJson)
    val payload = memoryTableEditorJson.parseToJsonElement(payloadJson) as? JsonObject
        ?: error("Payload JSON must be an object")
    schemaTables.map { table ->
        val rows = (payload[table.name] as? JsonArray)
            ?.map { rowElement ->
                val rowObject = rowElement as? JsonObject ?: JsonObject(emptyMap())
                table.columns.associate { column ->
                    column.name to jsonElementToCellText(rowObject[column.name])
                }
            }
            .orEmpty()
        MemoryTableEditorTable(schema = table, rows = rows)
    }
}

private fun parseMemoryTableSchema(schemaJson: String): List<MemoryTableSchemaTable> {
    val root = memoryTableEditorJson.parseToJsonElement(schemaJson) as? JsonObject
        ?: error("Schema JSON must be an object")
    val tables = root["tables"] as? JsonArray ?: error("Schema JSON must contain tables[]")
    return tables.mapNotNull { tableElement ->
        val tableObject = tableElement as? JsonObject ?: return@mapNotNull null
        val tableName = tableObject["name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val columns = (tableObject["columns"] as? JsonArray)
            ?.mapNotNull { columnElement ->
                val columnObject = columnElement as? JsonObject ?: return@mapNotNull null
                val columnName = columnObject["name"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val columnType = columnObject["type"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
                MemoryTableSchemaColumn(
                    name = columnName,
                    type = columnType.ifBlank { "string" },
                )
            }
            .orEmpty()
        MemoryTableSchemaTable(name = tableName, columns = columns)
    }.also {
        require(it.isNotEmpty()) { "Schema JSON must define at least one table" }
    }
}

private fun serializeMemoryTablePayload(
    originalPayloadJson: String,
    tables: List<MemoryTableEditorTable>,
): Result<String> = runCatching {
    val root = (memoryTableEditorJson.parseToJsonElement(originalPayloadJson) as? JsonObject)
        ?.toMutableMap()
        ?: mutableMapOf<String, JsonElement>()
    tables.forEach { table ->
        root[table.name] = JsonArray(
            table.rows.map { row ->
                JsonObject(
                    table.columns.associate { column ->
                        column.name to JsonPrimitive(row[column.name].orEmpty())
                    }
                )
            }
        )
    }
    memoryTableEditorJson.encodeToString(JsonObject.serializer(), JsonObject(root))
}

private fun validateMemoryTablePayloadJson(payloadJson: String): Result<Unit> = runCatching {
    val payload = memoryTableEditorJson.parseToJsonElement(payloadJson)
    require(payload is JsonObject) { "Payload JSON must be an object" }
}

private fun jsonElementToCellText(element: JsonElement?): String {
    return when (element) {
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        null -> ""
        else -> element.toString()
    }
}

private fun MemoryTableSchemaColumn.editorWidth() = if (type == "text") 260.dp else 160.dp

@Composable
private fun MemoryItem(
    memory: AssistantMemory,
    onEditMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "#${memory.id} · ${
                        stringResource(
                            if (memory.scope == MemoryScope.GLOBAL) {
                                R.string.assistant_page_memory_scope_global
                            } else {
                                R.string.assistant_page_memory_scope_assistant
                            }
                        )
                    }",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = memory.content,

                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = { onEditMemory(memory) }
            ) {
                Icon(HugeIcons.PencilEdit01, null)
            }
            IconButton(
                onClick = { onDeleteMemory(memory) }
            ) {
                Icon(
                    HugeIcons.Delete01,
                    stringResource(R.string.assistant_page_delete)
                )
            }
        }
    }
}
