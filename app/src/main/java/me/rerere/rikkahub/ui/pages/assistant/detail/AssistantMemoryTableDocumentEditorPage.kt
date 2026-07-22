package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.OrientationLandscapeToPotrait
import me.rerere.hugeicons.stroke.OrientationPotraitToLandscape
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.readMemoryTableInjectionToggles
import me.rerere.rikkahub.data.model.setMemoryTableInjectionEnabled
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
    conversationId: String? = null,
) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) })
    val navController = LocalNavController.current
    val editorScopeId = remember(initialScopeType, assistantId, conversationId) {
        memoryTableEditorScopeId(initialScopeType, assistantId, conversationId)
    }
    var resolvedTemplate by remember(documentId, templateId, assistantId, initialScopeType, conversationId) {
        mutableStateOf<MemoryTableTemplate?>(null)
    }
    var templateLookupComplete by remember(documentId, templateId, assistantId, initialScopeType, conversationId) {
        mutableStateOf(false)
    }
    var draft by remember(documentId, templateId, assistantId, initialScopeType, conversationId) {
        mutableStateOf<MemoryTableDocument?>(null)
    }
    var lookupComplete by remember(documentId, templateId, assistantId, initialScopeType, conversationId) {
        mutableStateOf(false)
    }
    var isNewDocument by remember(documentId, templateId, assistantId, initialScopeType, conversationId) {
        mutableStateOf(false)
    }

    LaunchedEffect(documentId, templateId, assistantId, initialScopeType, conversationId) {
        val scopeId = editorScopeId
        if (scopeId == null) {
            lookupComplete = true
            navController.popBackStack()
            return@LaunchedEffect
        }
        val template = vm.getMemoryTableTemplateForEditor(templateId)
        resolvedTemplate = template
        templateLookupComplete = true
        if (shouldCloseMemoryTableEditor(templateLookupComplete, template)) {
            navController.popBackStack()
            return@LaunchedEffect
        }
        val scopedDocuments = vm.getMemoryTableDocumentsForEditor(conversationId)
        val resolution = resolveMemoryTableEditorDocument(
            documents = scopedDocuments,
            documentId = documentId,
            templateId = templateId,
            scopeType = initialScopeType,
            scopeId = scopeId,
        )
        if (resolution == null) {
            lookupComplete = true
            navController.popBackStack()
            return@LaunchedEffect
        }
        draft = resolution.document
        isNewDocument = resolution.isNewDocument
        lookupComplete = true
    }

    val document = draft
    val template = resolvedTemplate
    if (!templateLookupComplete || !lookupComplete || document == null || template == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    MemoryTableDocumentEditorScaffold(
        document = document,
        template = template,
        assistantId = assistantId,
        isNewDocument = isNewDocument,
        onDraftChange = { draft = it },
        onUpdateTemplate = { vm.upsertMemoryTableTemplate(it) },
        onSave = { saved ->
            draft = saved
            vm.upsertMemoryTableDocument(saved, conversationId = conversationId)
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
    val activity = LocalActivity.current
    var draft by remember(document.id, document.templateId) { mutableStateOf(document) }
    var selectedTab by remember(document.id) { mutableIntStateOf(0) }
    var templateDraft by remember(template.id, template.updatedAt) { mutableStateOf(template) }
    val initialTables = remember(document.id, templateDraft.schemaJson, document.payloadJson) {
        parseMemoryTableEditorTables(templateDraft.schemaJson, document.payloadJson)
    }
    val initialPayloadJson = remember(document.id, document.payloadJson, initialTables) {
        normalizedMemoryTablePayload(document.payloadJson, initialTables)
    }
    var tableState by remember(document.id, templateDraft.schemaJson, document.payloadJson) {
        mutableStateOf(initialTables.getOrNull().orEmpty())
    }
    var editorError by remember(document.id, templateDraft.schemaJson, document.payloadJson) {
        mutableStateOf(initialTables.exceptionOrNull()?.message)
    }
    var baselineFingerprint by remember(document.id, initialPayloadJson, template.id, template.updatedAt) {
        mutableStateOf(editorFingerprint(document, initialPayloadJson, template))
    }
    var payloadJson by remember(document.id, initialPayloadJson) { mutableStateOf(initialPayloadJson) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showAddColumnDialog by remember { mutableStateOf(false) }
    var addColumnTableIndex by remember { mutableIntStateOf(-1) }
    var columnActionTarget by remember { mutableStateOf<Pair<Int, MemoryTableSchemaColumn>?>(null) }
    var columnToDelete by remember { mutableStateOf<Pair<Int, MemoryTableSchemaColumn>?>(null) }
    val enteringRequestedOrientation = remember(activity) { activity?.requestedOrientation }
    val systemUiSnapshotState = remember(activity) {
        mutableStateOf<MemoryTableEditorSystemUiSnapshot?>(null)
    }
    val configurationOrientation = LocalConfiguration.current.orientation
    val isLandscape = configurationOrientation == Configuration.ORIENTATION_LANDSCAPE

    fun currentFingerprint(): String = editorFingerprint(draft, payloadJson, templateDraft)
    val hasUnsavedChanges = currentFingerprint() != baselineFingerprint

    fun restoreSystemUi() {
        val hostActivity = activity ?: return
        val snapshot = systemUiSnapshotState.value ?: return
        val controller = WindowCompat.getInsetsController(hostActivity.window, hostActivity.window.decorView)
        if (snapshot.statusBarVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        controller.systemBarsBehavior = snapshot.systemBarsBehavior
    }

    fun restoreEditorWindowState() {
        val hostActivity = activity ?: return
        restoreSystemUi()
        enteringRequestedOrientation?.let { orientation ->
            if (hostActivity.requestedOrientation != orientation) {
                hostActivity.requestedOrientation = orientation
            }
        }
    }

    LaunchedEffect(activity) {
        val hostActivity = activity ?: return@LaunchedEffect
        val decorView = hostActivity.window.decorView
        while (systemUiSnapshotState.value == null) {
            val rootInsets = ViewCompat.getRootWindowInsets(decorView)
            if (rootInsets != null) {
                val controller = WindowCompat.getInsetsController(hostActivity.window, decorView)
                systemUiSnapshotState.value = MemoryTableEditorSystemUiSnapshot(
                    statusBarVisible = rootInsets.isVisible(WindowInsetsCompat.Type.statusBars()),
                    systemBarsBehavior = controller.systemBarsBehavior,
                )
                break
            }
            withFrameNanos { }
        }
    }

    LaunchedEffect(activity, isLandscape, systemUiSnapshotState.value) {
        val hostActivity = activity ?: return@LaunchedEffect
        val snapshot = systemUiSnapshotState.value ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(hostActivity.window, hostActivity.window.decorView)
        if (isLandscape) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            if (snapshot.statusBarVisible) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
            controller.systemBarsBehavior = snapshot.systemBarsBehavior
        }
    }

    DisposableEffect(activity) {
        onDispose { restoreEditorWindowState() }
    }

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
        templateDraft = updatedTemplate
    }

    // #93: flip a single table's injectPolicy.enabled in the template schema JSON so the
    // per-table injection gate can be toggled from the editor without hand-editing JSON.
    fun setTableInjectionEnabled(tableName: String, enabled: Boolean) {
        val updatedSchema = setMemoryTableInjectionEnabled(templateDraft.schemaJson, tableName, enabled)
        if (updatedSchema != templateDraft.schemaJson) {
            updateTemplateSchema(templateDraft.copy(schemaJson = updatedSchema))
        }
    }

    fun addColumn(tableIndex: Int, columnName: String): Boolean {
        val targetTable = tableState.getOrNull(tableIndex) ?: return false
        if (columnName.isBlank()) {
            editorError = "Column name cannot be empty"
            return false
        }
        if (targetTable.columns.any { it.name == columnName }) {
            editorError = "Column '$columnName' already exists"
            return false
        }
        val newColumn = MemoryTableSchemaColumn(name = columnName, type = MEMORY_TABLE_COLUMN_TYPE)
        val updatedTemplate = templateDraft.withColumnAdded(targetTable.name, newColumn)
        updateTemplateSchema(updatedTemplate)
        updateTables(
            tableState.mapIndexed { index, table ->
                if (index == tableIndex) table.addColumn(newColumn) else table
            },
        )
        return true
    }

    fun deleteColumn(tableIndex: Int, column: MemoryTableSchemaColumn) {
        val targetTable = tableState.getOrNull(tableIndex) ?: return
        val updatedTemplate = templateDraft.withColumnRemoved(targetTable.name, column.name)
        updateTemplateSchema(updatedTemplate)
        updateTables(
            tableState.mapIndexed { index, table ->
                if (index == tableIndex) table.deleteColumn(column.name) else table
            },
        )
    }

    fun renameColumn(tableIndex: Int, column: MemoryTableSchemaColumn, newName: String): Boolean {
        val targetTable = tableState.getOrNull(tableIndex) ?: return false
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            editorError = "Column name cannot be empty"
            return false
        }
        if (trimmedName == column.name) return true
        if (targetTable.columns.any { it.name == trimmedName }) {
            editorError = "Column '$trimmedName' already exists"
            return false
        }
        val updatedTemplate = templateDraft.withColumnRenamed(targetTable.name, column.name, trimmedName)
        updateTemplateSchema(updatedTemplate)
        updateTables(
            tableState.mapIndexed { index, table ->
                if (index == tableIndex) table.renameColumn(column.name, trimmedName) else table
            },
        )
        return true
    }

    fun switchToTableMode(): Boolean {
        return parseMemoryTableEditorTables(templateDraft.schemaJson, payloadJson)
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
        if (draft.scopeType == MemoryTableScopeType.GLOBAL &&
            templateDraft.scopeType != MemoryTableScopeType.GLOBAL
        ) {
            editorError = "Assistant-scoped memory table templates cannot be saved as global documents"
            return false
        }
        validateMemoryTableSchemaJson(templateDraft.schemaJson)
            .onFailure {
                editorError = it.message
                return false
            }
        val payloadResult = if (selectedTab == 1) {
            validateMemoryTablePayloadJson(payloadJson).map { payloadJson.trim() }
        } else {
            serializeMemoryTablePayload(payloadJson, tableState)
        }
        return payloadResult
            .onSuccess { payload ->
                val saved = draft.copy(payloadJson = payload)
                draft = saved
                onDraftChange(saved)
                onUpdateTemplate(templateDraft)
                baselineFingerprint = editorFingerprint(saved, payload, templateDraft)
                restoreEditorWindowState()
                onSave(saved)
            }
            .onFailure { editorError = it.message }
            .isSuccess
    }

    fun requestBack() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            restoreEditorWindowState()
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
    val contentSpacing = if (isLandscape) 8.dp else 12.dp
    val landscapeActionSize = 48.dp
    val landscapeActionTopPadding = 4.dp
    val landscapeCutoutInsets = WindowInsets.displayCutout.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
    val landscapeHorizontalCutoutInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
    val imeVisible = WindowInsets.isImeVisible
    val orientationTarget = memoryTableEditorTargetOrientation(configurationOrientation)
    val orientationContentDescription = stringResource(
        if (isLandscape) {
            R.string.assistant_page_memory_table_switch_to_portrait
        } else {
            R.string.assistant_page_memory_table_switch_to_landscape
        },
    )
    val modeTabs: @Composable () -> Unit = {
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
    }
    val scopeControls: @Composable () -> Unit = {
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
                if (!isLandscape) {
                    Text(
                        text = stringResource(R.string.assistant_page_memory_scope_global_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = draft.scopeType == MemoryTableScopeType.GLOBAL,
                onCheckedChange = { enabled ->
                    if (enabled && templateDraft.scopeType != MemoryTableScopeType.GLOBAL) {
                        editorError =
                            "Assistant-scoped memory table templates cannot be saved as global documents"
                        return@Switch
                    }
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
                enabled = draft.scopeType != MemoryTableScopeType.CONVERSATION &&
                    templateDraft.scopeType == MemoryTableScopeType.GLOBAL,
            )
        }

        if (draft.scopeType == MemoryTableScopeType.CONVERSATION) {
            Text(
                text = stringResource(R.string.assistant_page_memory_table_conversation_scope_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Scaffold(
        topBar = {
            if (!isLandscape) {
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
                                    templateDraft.name.ifBlank { templateDraft.id },
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
            }
        },
        floatingActionButton = {
            if (activity != null && !imeVisible) {
                FloatingActionButton(
                    onClick = { activity.requestedOrientation = orientationTarget },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (isLandscape) {
                            HugeIcons.OrientationLandscapeToPotrait
                        } else {
                            HugeIcons.OrientationPotraitToLandscape
                        },
                        contentDescription = orientationContentDescription,
                    )
                }
            }
        },
        contentWindowInsets = if (isLandscape) WindowInsets.navigationBars else ScaffoldDefaults.contentWindowInsets,
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!isLandscape) {
                    modeTabs()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isLandscape) {
                                Modifier.windowInsetsPadding(landscapeHorizontalCutoutInsets)
                            } else {
                                Modifier
                            },
                        )
                        .padding(
                            start = 16.dp,
                            top = contentSpacing,
                            end = 16.dp,
                            bottom = contentSpacing,
                        ),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                ) {
                    if (!isLandscape) {
                        scopeControls()
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
                            verticalArrangement = Arrangement.spacedBy(contentSpacing),
                        ) {
                            if (isLandscape) {
                                scopeControls()
                            }
                            if (tableState.isEmpty() && editorError == null) {
                                Text(
                                    text = stringResource(R.string.assistant_page_memory_table_empty_schema),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val injectionToggles = remember(templateDraft.schemaJson) {
                                readMemoryTableInjectionToggles(templateDraft.schemaJson)
                            }
                            tableState.forEachIndexed { tableIndex, table ->
                                val injectEnabled = injectionToggles
                                    .firstOrNull { it.name == table.name }
                                    ?.injectEnabled
                                    ?: true
                                MemoryTableEditableTable(
                                    table = table,
                                    injectEnabled = injectEnabled,
                                    onInjectEnabledChange = { enabled ->
                                        setTableInjectionEnabled(table.name, enabled)
                                    },
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
                                    onColumnAction = { column ->
                                        columnActionTarget = tableIndex to column
                                    },
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(contentSpacing),
                        ) {
                            if (isLandscape) {
                                scopeControls()
                            }
                            Text(
                                text = stringResource(R.string.assistant_page_memory_table_template_settings),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextField(
                                value = templateDraft.name,
                                onValueChange = { value ->
                                    templateDraft = templateDraft.copy(name = value)
                                },
                                label = { Text(stringResource(R.string.assistant_page_memory_table_template_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = templateDraft.description,
                                onValueChange = { value ->
                                    templateDraft = templateDraft.copy(description = value)
                                },
                                label = {
                                    Text(stringResource(R.string.assistant_page_memory_table_template_description))
                                },
                                minLines = 2,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = templateDraft.schemaJson,
                                onValueChange = { value ->
                                    templateDraft = templateDraft.copy(schemaJson = value)
                                    editorError = validateMemoryTableSchemaJson(value).exceptionOrNull()?.message
                                },
                                label = { Text(stringResource(R.string.assistant_page_memory_table_schema_json)) },
                                minLines = 6,
                                maxLines = 14,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = payloadJson,
                                onValueChange = { value ->
                                    payloadJson = value
                                    editorError = validateMemoryTablePayloadJson(value).exceptionOrNull()?.message
                                },
                                label = { Text(stringResource(R.string.assistant_page_memory_table_payload_json)) },
                                minLines = 6,
                                maxLines = 14,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(landscapeCutoutInsets)
                        .padding(start = 8.dp, top = landscapeActionTopPadding),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { requestBack() },
                        modifier = Modifier.size(landscapeActionSize),
                    ) {
                        Icon(
                            imageVector = HugeIcons.ArrowLeft01,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    IconButton(
                        onClick = { persistDraft() },
                        modifier = Modifier.size(landscapeActionSize),
                        enabled = editorError == null,
                    ) {
                        Icon(
                            imageVector = HugeIcons.FloppyDisk,
                            contentDescription = stringResource(R.string.common_save),
                        )
                    }
                }
            }
        }
    }

    if (showAddColumnDialog) {
        AddColumnDialog(
            onDismiss = { showAddColumnDialog = false },
            onConfirm = { name ->
                if (addColumn(addColumnTableIndex, name)) {
                    showAddColumnDialog = false
                }
            },
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.assistant_page_memory_table_unsaved_title)) },
            text = { Text(stringResource(R.string.assistant_page_memory_table_unsaved_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        restoreEditorWindowState()
                        onNavigateBack()
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_memory_table_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    columnActionTarget?.let { target ->
        ColumnActionDialog(
            column = target.second,
            onDismiss = { columnActionTarget = null },
            onRename = { name ->
                if (renameColumn(target.first, target.second, name)) {
                    columnActionTarget = null
                }
            },
            onDelete = {
                columnActionTarget = null
                columnToDelete = target
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
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ColumnActionDialog(
    column: MemoryTableSchemaColumn,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var columnName by remember(column.name) { mutableStateOf(column.name) }
    var error by remember(column.name) { mutableStateOf<String?>(null) }
    val columnNameEmptyError = stringResource(R.string.assistant_page_memory_table_column_name_empty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(column.name) },
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
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.assistant_page_memory_table_delete_column))
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
                    onRename(columnName)
                },
            ) {
                Text(stringResource(R.string.common_rename))
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
private fun AddColumnDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    var columnName by remember { mutableStateOf("") }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (columnName.isBlank()) {
                        error = columnNameEmptyError
                        return@TextButton
                    }
                    onConfirm(columnName.trim())
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
    injectEnabled: Boolean,
    onInjectEnabledChange: (Boolean) -> Unit,
    onChange: (MemoryTableEditorTable) -> Unit,
    onAddColumn: () -> Unit,
    onColumnAction: (MemoryTableSchemaColumn) -> Unit,
) {
    val columnMinWidths = List(table.columns.size) { 88.dp } + 48.dp
    val columnMaxWidths = List(table.columns.size) { 280.dp } + 48.dp

    val headers = buildList<@Composable () -> Unit> {
        table.columns.forEach { column ->
            add(@Composable {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clickable { onColumnAction(column) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = column.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            })
        }
        add(@Composable {
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp),
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
                    TableCellTextField(
                        value = row[column.name].orEmpty(),
                        onValueChange = { value ->
                            onChange(table.updateCell(rowIndex, column.name, value))
                        },
                    )
                })
            }
            add(@Composable {
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = table.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (injectEnabled) {
                        "注入到提示词"
                    } else {
                        "不注入到提示词"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = injectEnabled,
                onCheckedChange = onInjectEnabledChange,
            )
        }
        DataTable(
            headers = headers,
            rows = rows,
            columnMinWidths = columnMinWidths,
            columnMaxWidths = columnMaxWidths,
            cellPadding = 0.dp,
            stretchToFillWidth = false,
        )
        TextButton(onClick = { onChange(table.addRow()) }) {
            Text(stringResource(R.string.assistant_page_memory_table_add_row))
        }
    }
}

@Composable
private fun TableCellTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        modifier = modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 72.dp, max = 280.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

internal fun shouldCloseMemoryTableEditor(
    templateLookupComplete: Boolean,
    template: MemoryTableTemplate?,
): Boolean = templateLookupComplete && template == null

internal fun memoryTableEditorTargetOrientation(configurationOrientation: Int): Int {
    return if (configurationOrientation == Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

internal fun memoryTableEditorScopeId(
    scopeType: MemoryTableScopeType,
    assistantId: String,
    conversationId: String?,
): String? = when (scopeType) {
    MemoryTableScopeType.GLOBAL -> MemoryRepository.GLOBAL_MEMORY_ID
    MemoryTableScopeType.ASSISTANT -> assistantId
    MemoryTableScopeType.CONVERSATION -> conversationId
}

internal fun findMemoryTableEditorDocument(
    documents: List<MemoryTableDocument>,
    documentId: String?,
    templateId: String,
    scopeType: MemoryTableScopeType,
    scopeId: String,
): MemoryTableDocument? = documents.firstOrNull { document ->
    (documentId == null || document.id == documentId) &&
        document.templateId == templateId &&
        document.scopeType == scopeType &&
        (scopeType == MemoryTableScopeType.GLOBAL || document.scopeId == scopeId)
}

internal data class MemoryTableEditorDocumentResolution(
    val document: MemoryTableDocument,
    val isNewDocument: Boolean,
)

internal fun resolveMemoryTableEditorDocument(
    documents: List<MemoryTableDocument>,
    documentId: String?,
    templateId: String,
    scopeType: MemoryTableScopeType,
    scopeId: String,
): MemoryTableEditorDocumentResolution? {
    val matchingDocument = findMemoryTableEditorDocument(
        documents = documents,
        documentId = documentId,
        templateId = templateId,
        scopeType = scopeType,
        scopeId = scopeId,
    )
    if (documentId != null && matchingDocument == null) return null
    return MemoryTableEditorDocumentResolution(
        document = matchingDocument ?: MemoryTableDocument(
            templateId = templateId,
            scopeType = scopeType,
            scopeId = scopeId,
        ),
        isNewDocument = matchingDocument == null,
    )
}

private fun editorFingerprint(
    document: MemoryTableDocument,
    payloadJson: String,
    template: MemoryTableTemplate,
): String {
    return listOf(
        document.scopeType.name,
        document.scopeId,
        payloadJson.trim(),
        template.name.trim(),
        template.description.trim(),
        template.schemaJson.trim(),
    ).joinToString("|")
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

private fun MemoryTableTemplate.withColumnRenamed(
    tableName: String,
    oldColumnName: String,
    newColumnName: String,
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
                    columns.map { columnElement ->
                        val columnObject = columnElement as? JsonObject ?: return@map columnElement
                        val columnName = columnObject["name"]?.jsonPrimitive?.contentOrNull
                        if (columnName != oldColumnName) return@map columnElement
                        JsonObject(
                            columnObject.toMutableMap().apply {
                                put("name", JsonPrimitive(newColumnName))
                            },
                        )
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

private data class MemoryTableEditorSystemUiSnapshot(
    val statusBarVisible: Boolean,
    val systemBarsBehavior: Int,
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

    fun renameColumn(oldColumnName: String, newColumnName: String): MemoryTableEditorTable {
        return copy(
            schema = schema.copy(
                columns = columns.map { column ->
                    if (column.name == oldColumnName) column.copy(name = newColumnName) else column
                },
            ),
            rows = rows.map { row ->
                row.toMutableMap().apply {
                    put(newColumnName, remove(oldColumnName).orEmpty())
                }
            },
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

private const val MEMORY_TABLE_COLUMN_TYPE = "string"

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

private fun normalizedMemoryTablePayload(
    payloadJson: String,
    tables: Result<List<MemoryTableEditorTable>>,
): String {
    return tables.getOrNull()
        ?.let { serializeMemoryTablePayload(payloadJson, it).getOrDefault(payloadJson) }
        ?: payloadJson
}

private fun validateMemoryTablePayloadJson(payloadJson: String): Result<Unit> = runCatching {
    val payload = memoryTableEditorJson.parseToJsonElement(payloadJson)
    require(payload is JsonObject) { "Payload JSON must be an object" }
}

private fun validateMemoryTableSchemaJson(schemaJson: String): Result<Unit> = runCatching {
    parseMemoryTableSchema(schemaJson)
}

private fun jsonElementToCellText(element: JsonElement?): String {
    return when (element) {
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        null -> ""
        else -> element.toString()
    }
}
