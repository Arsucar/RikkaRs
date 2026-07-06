package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_SCHEMA_JSON
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.table.DataTable
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantMemoryTableDocumentEditorPage(
    documentId: String?,
    templateId: String,
    assistantId: String,
    initialScopeType: MemoryTableScopeType,
) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) })
    val memoryTableTemplates by vm.memoryTableTemplates.collectAsStateWithLifecycle()
    val memoryTableDocuments by vm.memoryTableDocuments.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    val resolvedTemplate = remember(memoryTableTemplates, templateId) {
        memoryTableTemplates.firstOrNull { it.id == templateId }
            ?: MemoryTableTemplate(
                id = templateId,
                name = templateId,
                schemaJson = DEFAULT_MEMORY_TABLE_SCHEMA_JSON,
            )
    }

    var draft by remember(documentId, templateId, assistantId, initialScopeType) {
        mutableStateOf<MemoryTableDocument?>(
            if (documentId == null) {
                MemoryTableDocument(
                    templateId = templateId,
                    scopeType = initialScopeType,
                    scopeId = scopeIdFor(initialScopeType, assistantId),
                )
            } else {
                null
            },
        )
    }

    LaunchedEffect(documentId, memoryTableDocuments) {
        if (documentId == null) return@LaunchedEffect
        memoryTableDocuments.firstOrNull { it.id == documentId }?.let { loaded ->
            if (draft == null || draft?.id == loaded.id && draft?.updatedAt != loaded.updatedAt) {
                draft = loaded
            }
        } ?: run {
            if (draft == null) {
                draft = MemoryTableDocument(
                    id = documentId,
                    templateId = templateId,
                    scopeType = initialScopeType,
                    scopeId = scopeIdFor(initialScopeType, assistantId),
                )
            }
        }
    }

    val document = draft
    if (document == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    MemoryTableDocumentEditorScaffold(
        document = document,
        template = resolvedTemplate,
        assistantId = assistantId,
        isNewDocument = documentId == null,
        onDraftChange = { draft = it },
        onUpdateTemplate = { vm.upsertMemoryTableTemplate(it) },
        onSave = { saved ->
            vm.upsertMemoryTableDocument(saved)
            navController.popBackStack()
        },
        onNavigateBack = { navController.popBackStack() },
    )
}

@Composable
private fun MemoryTableDocumentEditorScaffold(
    document: MemoryTableDocument,
    template: MemoryTableTemplate,
    assistantId: String,
    isNewDocument: Boolean,
    onDraftChange: (MemoryTableDocument) -> Unit,
    onUpdateTemplate: (MemoryTableTemplate) -> Unit,
    onSave: (MemoryTableDocument) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var draft by remember(document.id, document.templateId) { mutableStateOf(document) }
    var selectedTab by remember(document.id) { mutableIntStateOf(0) }
    var payloadJson by remember(document.id, document.payloadJson) { mutableStateOf(document.payloadJson) }
    val initialTables = remember(document.id, template.schemaJson, document.payloadJson) {
        parseMemoryTableEditorTables(template.schemaJson, document.payloadJson)
    }
    var tableState by remember(document.id, template.schemaJson, document.payloadJson) {
        mutableStateOf(initialTables.getOrNull().orEmpty())
    }
    var editorError by remember(document.id, template.schemaJson, document.payloadJson) {
        mutableStateOf(initialTables.exceptionOrNull()?.message)
    }
    var baselineFingerprint by remember(document.id) {
        mutableStateOf(editorFingerprint(document, document.payloadJson))
    }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showAddColumnDialog by remember { mutableStateOf(false) }
    var addColumnTableIndex by remember { mutableIntStateOf(-1) }
    var columnToDelete by remember { mutableStateOf<Pair<Int, MemoryTableSchemaColumn>?>(null) }

    fun currentFingerprint(): String = editorFingerprint(draft, payloadJson)
    val hasUnsavedChanges = currentFingerprint() != baselineFingerprint

    fun updateTables(tables: List<MemoryTableEditorTable>) {
        tableState = tables
        serializeMemoryTablePayload(payloadJson, tables)
            .onSuccess {
                payloadJson = it
                editorError = null
            }
            .onFailure {
                editorError = it.message
            }
    }

    fun updateTemplateSchema(updatedTemplate: MemoryTableTemplate) {
        onUpdateTemplate(updatedTemplate)
    }

    fun addColumn(tableIndex: Int, columnName: String, columnType: String) {
        val targetTable = tableState.getOrNull(tableIndex) ?: return
        if (columnName.isBlank()) {
            editorError = "Column name cannot be empty"
            return
        }
        if (targetTable.columns.any { it.name == columnName }) {
            editorError = "Column '$columnName' already exists"
            return
        }
        val newColumn = MemoryTableSchemaColumn(name = columnName, type = columnType)
        val updatedTemplate = template.withColumnAdded(targetTable.name, newColumn)
        updateTemplateSchema(updatedTemplate)
        updateTables(
            tableState.mapIndexed { index, table ->
                if (index == tableIndex) table.addColumn(newColumn) else table
            },
        )
    }

    fun deleteColumn(tableIndex: Int, column: MemoryTableSchemaColumn) {
        val targetTable = tableState.getOrNull(tableIndex) ?: return
        val updatedTemplate = template.withColumnRemoved(targetTable.name, column.name)
        updateTemplateSchema(updatedTemplate)
        updateTables(
            tableState.mapIndexed { index, table ->
                if (index == tableIndex) table.deleteColumn(column.name) else table
            },
        )
    }

    fun switchToTableMode(): Boolean {
        return parseMemoryTableEditorTables(template.schemaJson, payloadJson)
            .onSuccess {
                tableState = it
                editorError = null
                selectedTab = 0
            }
            .onFailure {
                editorError = it.message
            }
            .isSuccess
    }

    fun persistDraft(): Boolean {
        val payloadResult = if (selectedTab == 1) {
            validateMemoryTablePayloadJson(payloadJson).map { payloadJson.trim() }
        } else {
            serializeMemoryTablePayload(payloadJson, tableState)
        }
        return payloadResult
            .onSuccess { payload ->
                onSave(draft.copy(payloadJson = payload))
            }
            .onFailure { editorError = it.message }
            .isSuccess
    }

    fun requestBack() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler { requestBack() }

    val titleText = if (isNewDocument) {
        stringResource(R.string.assistant_page_memory_table_editor_new)
    } else {
        stringResource(
            R.string.assistant_page_memory_table_document_meta,
            draft.scopeType.name,
            draft.revision,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = { requestBack() },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CustomColors.listItemColors.containerColor,
                        ),
                    ) {
                        Icon(
                            imageVector = HugeIcons.ArrowLeft01,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { persistDraft() },
                        enabled = editorError == null,
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { switchToTableMode() },
                    text = { Text(stringResource(R.string.assistant_page_memory_table_mode_table)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        serializeMemoryTablePayload(payloadJson, tableState)
                            .onSuccess {
                                payloadJson = it
                                editorError = null
                            }
                            .onFailure {
                                editorError = it.message
                            }
                        selectedTab = 1
                    },
                    text = { Text(stringResource(R.string.assistant_page_memory_table_mode_json)) },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                            val updated = draft.copy(
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
                            draft = updated
                            onDraftChange(updated)
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

                editorError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (selectedTab == 0) {
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
                                        },
                                    )
                                },
                                onAddColumn = {
                                    addColumnTableIndex = tableIndex
                                    showAddColumnDialog = true
                                },
                                onDeleteColumn = { column ->
                                    columnToDelete = tableIndex to column
                                },
                            )
                        }
                    }
                } else {
                    TextField(
                        value = payloadJson,
                        onValueChange = { value ->
                            payloadJson = value
                            editorError = validateMemoryTablePayloadJson(value).exceptionOrNull()?.message
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

    if (showAddColumnDialog) {
        AddColumnDialog(
            onDismiss = { showAddColumnDialog = false },
            onConfirm = { name, type ->
                addColumn(addColumnTableIndex, name, type)
                showAddColumnDialog = false
            },
        )
    }

    columnToDelete?.let { (tableIndex, column) ->
        AlertDialog(
            onDismissRequest = { columnToDelete = null },
            title = { Text(stringResource(R.string.assistant_page_memory_table_delete_column)) },
            text = {
                Text(
                    stringResource(
                        R.string.assistant_page_memory_table_delete_column_confirm,
                        column.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteColumn(tableIndex, column)
                        columnToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { columnToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AddColumnDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit,
) {
    var columnName by remember { mutableStateOf("") }
    var columnType by remember { mutableStateOf("string") }
    var error by remember { mutableStateOf<String?>(null) }
    val columnNameEmptyError = stringResource(R.string.assistant_page_memory_table_column_name_empty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assistant_page_memory_table_add_column)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = columnName,
                    onValueChange = {
                        columnName = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.assistant_page_memory_table_column_name)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                Text(
                    text = stringResource(R.string.assistant_page_memory_table_column_type),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = columnType == "string",
                            onClick = { columnType = "string" },
                        )
                        Text(stringResource(R.string.assistant_page_memory_table_column_type_string))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = columnType == "text",
                            onClick = { columnType = "text" },
                        )
                        Text(stringResource(R.string.assistant_page_memory_table_column_type_text))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (columnName.isBlank()) {
                        error = columnNameEmptyError
                        return@TextButton
                    }
                    onConfirm(columnName.trim(), columnType)
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun MemoryTableEditableTable(
    table: MemoryTableEditorTable,
    onChange: (MemoryTableEditorTable) -> Unit,
    onAddColumn: () -> Unit,
    onDeleteColumn: (MemoryTableSchemaColumn) -> Unit,
) {
    val columnMinWidths = table.columns.map { column ->
        if (column.type == "text") 120.dp else 80.dp
    } + 48.dp
    val columnMaxWidths = table.columns.map { column ->
        if (column.type == "text") 280.dp else 160.dp
    } + 48.dp

    val headers = buildList<@Composable () -> Unit> {
        table.columns.forEach { column ->
            add(@Composable {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = column.name,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onDeleteColumn(column) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = stringResource(R.string.assistant_page_memory_table_delete_column),
                        )
                    }
                }
            })
        }
        add(@Composable {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onAddColumn) {
                    Icon(
                        imageVector = HugeIcons.Add01,
                        contentDescription = stringResource(R.string.assistant_page_memory_table_add_column),
                    )
                }
            }
        })
    }

    val rows = table.rows.mapIndexed { rowIndex, row ->
        buildList<@Composable () -> Unit> {
            table.columns.forEach { column ->
                add(@Composable {
                    OutlinedTextField(
                        value = row[column.name].orEmpty(),
                        onValueChange = { value ->
                            onChange(table.updateCell(rowIndex, column.name, value))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = column.type != "text",
                        minLines = if (column.type == "text") 3 else 1,
                        maxLines = if (column.type == "text") 6 else 1,
                    )
                })
            }
            add(@Composable {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { onChange(table.deleteRow(rowIndex)) }) {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = stringResource(R.string.assistant_page_delete),
                        )
                    }
                }
            })
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = table.name,
            style = MaterialTheme.typography.titleSmall,
        )
        DataTable(
            headers = headers,
            rows = rows,
            columnMinWidths = columnMinWidths,
            columnMaxWidths = columnMaxWidths,
            cellPadding = 8.dp,
            stretchToFillWidth = true,
        )
        TextButton(onClick = { onChange(table.addRow()) }) {
            Text(stringResource(R.string.assistant_page_memory_table_add_row))
        }
    }
}

private fun scopeIdFor(scopeType: MemoryTableScopeType, assistantId: String): String {
    return when (scopeType) {
        MemoryTableScopeType.GLOBAL -> MemoryRepository.GLOBAL_MEMORY_ID
        MemoryTableScopeType.ASSISTANT -> assistantId
        MemoryTableScopeType.CONVERSATION -> assistantId
    }
}

private fun editorFingerprint(document: MemoryTableDocument, payloadJson: String): String {
    return "${document.scopeType}|${document.scopeId}|${payloadJson.trim()}"
}

private fun MemoryTableTemplate.withColumnAdded(
    tableName: String,
    column: MemoryTableSchemaColumn,
): MemoryTableTemplate = copy(
    schemaJson = runCatching {
        val root = memoryTableEditorJson.parseToJsonElement(schemaJson) as? JsonObject
            ?: error("Schema JSON must be an object")
        val tables = root["tables"] as? JsonArray ?: error("Schema JSON must contain tables[]")
        val updatedTables = JsonArray(
            tables.map { tableElement ->
                val tableObject = tableElement as? JsonObject ?: return@map tableElement
                val currentName = tableObject["name"]?.jsonPrimitive?.contentOrNull
                if (currentName != tableName) return@map tableElement
                val columns = tableObject["columns"] as? JsonArray ?: JsonArray(emptyList())
                val newColumn = buildJsonObject {
                    put("name", column.name)
                    put("type", column.type)
                }
                JsonObject(
                    tableObject.toMutableMap().apply {
                        put("columns", JsonArray(columns + newColumn))
                    },
                )
            },
        )
        JsonObject(root.toMutableMap().apply { put("tables", updatedTables) }).let {
            memoryTableEditorJson.encodeToString(JsonObject.serializer(), it)
        }
    }.getOrDefault(schemaJson),
)

private fun MemoryTableTemplate.withColumnRemoved(
    tableName: String,
    columnName: String,
): MemoryTableTemplate = copy(
    schemaJson = runCatching {
        val root = memoryTableEditorJson.parseToJsonElement(schemaJson) as? JsonObject
            ?: error("Schema JSON must be an object")
        val tables = root["tables"] as? JsonArray ?: error("Schema JSON must contain tables[]")
        val updatedTables = JsonArray(
            tables.map { tableElement ->
                val tableObject = tableElement as? JsonObject ?: return@map tableElement
                val currentName = tableObject["name"]?.jsonPrimitive?.contentOrNull
                if (currentName != tableName) return@map tableElement
                val columns = tableObject["columns"] as? JsonArray ?: JsonArray(emptyList())
                val updatedColumns = JsonArray(
                    columns.filter { columnElement ->
                        val columnObject = columnElement as? JsonObject ?: return@filter true
                        columnObject["name"]?.jsonPrimitive?.contentOrNull != columnName
                    },
                )
                JsonObject(
                    tableObject.toMutableMap().apply {
                        put("columns", updatedColumns)
                    },
                )
            },
        )
        JsonObject(root.toMutableMap().apply { put("tables", updatedTables) }).let {
            memoryTableEditorJson.encodeToString(JsonObject.serializer(), it)
        }
    }.getOrDefault(schemaJson),
)

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

    fun addColumn(column: MemoryTableSchemaColumn): MemoryTableEditorTable {
        return copy(
            schema = schema.copy(columns = columns + column),
            rows = rows.map { it.toMutableMap().apply { put(column.name, "") } },
        )
    }

    fun deleteColumn(columnName: String): MemoryTableEditorTable {
        return copy(
            schema = schema.copy(columns = columns.filter { it.name != columnName }),
            rows = rows.map { it.toMutableMap().apply { remove(columnName) } },
        )
    }

    fun updateCell(rowIndex: Int, columnName: String, value: String): MemoryTableEditorTable {
        return copy(
            rows = rows.mapIndexed { index, row ->
                if (index == rowIndex) {
                    row.toMutableMap().apply { put(columnName, value) }
                } else {
                    row
                }
            },
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
                    },
                )
            },
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