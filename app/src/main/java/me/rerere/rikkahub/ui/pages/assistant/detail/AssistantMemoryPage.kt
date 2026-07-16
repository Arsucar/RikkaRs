package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.isEffectiveFor
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
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
            innerPadding = innerPadding,
            settings = settings,
            assistant = assistant,
            memories = memories,
            memoryTableTemplates = memoryTableTemplates,
            memoryTableDocuments = memoryTableDocuments,
            onUpdateSettings = { vm.updateSettings(it) },
            onUpdateAssistant = { vm.update(it) },
            onSetMemoryEnabled = { vm.setMemoryEnabled(it) },
            onSetMemoryTableEnabled = { vm.setMemoryTableEnabled(it) },
            onDeleteMemory = { vm.deleteMemory(it) },
            onAddMemory = { vm.addMemory(it) },
            onUpdateMemory = { vm.updateMemory(it) },
            onCreateMemoryTableDocument = { template, onDone ->
                vm.createMemoryTableDocument(template, onDone)
            },
            onCreateMemoryTableTemplateAndDocument = { template, scopeType, onDone ->
                vm.createMemoryTableTemplateAndDocument(template, scopeType, onDone)
            },
            onDeleteMemoryTableDocument = { vm.deleteMemoryTableDocument(it) },
        )
    }
}

@Composable
private fun AssistantMemoryContent(
    innerPadding: PaddingValues,
    settings: Settings,
    assistant: Assistant,
    memories: List<AssistantMemory>,
    memoryTableTemplates: List<MemoryTableTemplate>,
    memoryTableDocuments: List<MemoryTableDocument>,
    onUpdateSettings: (Settings) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onSetMemoryEnabled: (Boolean) -> Unit,
    onSetMemoryTableEnabled: (Boolean) -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
    onCreateMemoryTableDocument: (MemoryTableTemplate, (Result<MemoryTableDocument>) -> Unit) -> Unit,
    onCreateMemoryTableTemplateAndDocument: (
        MemoryTableTemplate,
        MemoryTableScopeType,
        (Result<MemoryTableDocument>) -> Unit,
    ) -> Unit,
    onDeleteMemoryTableDocument: (MemoryTableDocument) -> Unit,
) {
    val navController = LocalNavController.current
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    var pendingDeleteMemory by remember { mutableStateOf<AssistantMemory?>(null) }
    var pendingDeleteMemoryTableDocument by remember { mutableStateOf<MemoryTableDocument?>(null) }
    var showAddMemoryTableDialog by remember { mutableStateOf(false) }
    var showMemoryTableBudgetDialog by remember { mutableStateOf(false) }
    val defaultMemoryTableTemplateName = stringResource(R.string.assistant_page_memory_table_default_template)
    val memoryTableBudgetUnlimitedLabel =
        stringResource(R.string.assistant_page_memory_table_budget_unlimited)

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

    if (showMemoryTableBudgetDialog) {
        MemoryTableBudgetDialog(
            maxDocuments = settings.memoryTableMaxInjectDocuments,
            maxTokens = settings.memoryTableMaxInjectTokens,
            maxChars = settings.memoryTableMaxInjectChars,
            onDismiss = { showMemoryTableBudgetDialog = false },
            onSave = { maxDocuments, maxTokens, maxChars ->
                onUpdateSettings(
                    settings.copy(
                        memoryTableMaxInjectDocuments = maxDocuments,
                        memoryTableMaxInjectTokens = maxTokens,
                        memoryTableMaxInjectChars = maxChars,
                    )
                )
                showMemoryTableBudgetDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
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
                        onCheckedChange = onSetMemoryEnabled,
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
                                )
                            )
                        }
                    )
                }
            )
            item(
                onClick = if (settings.enableMemoryTable) {
                    { showMemoryTableBudgetDialog = true }
                } else {
                    null
                },
                modifier = Modifier.alpha(if (settings.enableMemoryTable) 1f else 0.38f),
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_memory_table_budget_title))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.assistant_page_memory_table_budget_summary,
                            formatMemoryTableBudgetValue(
                                settings.memoryTableMaxInjectDocuments,
                                memoryTableBudgetUnlimitedLabel,
                            ),
                            formatMemoryTableBudgetValue(
                                settings.memoryTableMaxInjectTokens,
                                memoryTableBudgetUnlimitedLabel,
                            ),
                            formatMemoryTableBudgetValue(
                                settings.memoryTableMaxInjectChars,
                                memoryTableBudgetUnlimitedLabel,
                            ),
                        )
                    )
                },
                trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_table_assistant)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.assistant_page_memory_table_assistant_desc))
                        if (!settings.enableMemoryTable) {
                            Text(
                                text = stringResource(R.string.assistant_page_memory_table_disabled_global),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableMemoryTable,
                        onCheckedChange = onSetMemoryTableEnabled,
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
                        checked = settings.memoryTableAutoSyncEnabled,
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
            onAddDocument = { showAddMemoryTableDialog = true },
            onEditDocument = { document ->
                navController.navigate(
                    Screen.AssistantMemoryTableDocumentEditor(
                        documentId = document.id,
                        templateId = document.templateId,
                        assistantId = assistant.id.toString(),
                        scopeType = document.scopeType,
                    ),
                )
            },
            onDeleteDocument = { pendingDeleteMemoryTableDocument = it },
        )

        if (showAddMemoryTableDialog) {
            AddMemoryTableDialog(
                templates = memoryTableTemplates.filter { it.isEffectiveFor(assistant.id.toString()) },
                defaultTemplateName = defaultMemoryTableTemplateName,
                onDismiss = { showAddMemoryTableDialog = false },
                onCreateFromTemplate = { template, onDone ->
                    onCreateMemoryTableDocument(template) { result ->
                        result.onSuccess { document ->
                            showAddMemoryTableDialog = false
                            navController.navigate(document.toMemoryTableEditorScreen(assistant))
                        }
                        onDone(result)
                    }
                },
                onCreateTemplate = { template, scopeType, onDone ->
                    onCreateMemoryTableTemplateAndDocument(template, scopeType) { result ->
                        result.onSuccess { document ->
                            showAddMemoryTableDialog = false
                            navController.navigate(document.toMemoryTableEditorScreen(assistant))
                        }
                        onDone(result)
                    }
                },
            )
        }

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

internal data class ParsedMemoryTableBudgetInput(
    val value: Int?,
    val isValid: Boolean,
)

internal fun parseMemoryTableBudgetInput(text: String): ParsedMemoryTableBudgetInput {
    if (text.isEmpty()) return ParsedMemoryTableBudgetInput(value = null, isValid = true)
    if (!text.all { it in '0'..'9' }) {
        return ParsedMemoryTableBudgetInput(value = null, isValid = false)
    }
    val value = text.toIntOrNull()
        ?: return ParsedMemoryTableBudgetInput(value = null, isValid = false)
    return ParsedMemoryTableBudgetInput(value = value, isValid = true)
}

internal fun formatMemoryTableBudgetValue(value: Int?, unlimitedLabel: String): String =
    value?.toString() ?: unlimitedLabel

@Composable
private fun MemoryTableBudgetDialog(
    maxDocuments: Int?,
    maxTokens: Int?,
    maxChars: Int?,
    onDismiss: () -> Unit,
    onSave: (maxDocuments: Int?, maxTokens: Int?, maxChars: Int?) -> Unit,
) {
    var maxDocumentsText by remember(maxDocuments) { mutableStateOf(maxDocuments?.toString().orEmpty()) }
    var maxTokensText by remember(maxTokens) { mutableStateOf(maxTokens?.toString().orEmpty()) }
    var maxCharsText by remember(maxChars) { mutableStateOf(maxChars?.toString().orEmpty()) }
    val parsedDocuments = parseMemoryTableBudgetInput(maxDocumentsText)
    val parsedTokens = parseMemoryTableBudgetInput(maxTokensText)
    val parsedChars = parseMemoryTableBudgetInput(maxCharsText)
    val allValid = parsedDocuments.isValid && parsedTokens.isValid && parsedChars.isValid
    val validationError = stringResource(R.string.assistant_page_memory_table_budget_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assistant_page_memory_table_budget_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.assistant_page_memory_table_budget_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = maxDocumentsText,
                    onValueChange = { maxDocumentsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(stringResource(R.string.assistant_page_memory_table_budget_documents))
                    },
                    supportingText = if (parsedDocuments.isValid) null else {
                        { Text(validationError) }
                    },
                    isError = !parsedDocuments.isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_memory_table_budget_tokens)) },
                    supportingText = if (parsedTokens.isValid) null else {
                        { Text(validationError) }
                    },
                    isError = !parsedTokens.isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = maxCharsText,
                    onValueChange = { maxCharsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_memory_table_budget_chars)) },
                    supportingText = if (parsedChars.isValid) null else {
                        { Text(validationError) }
                    },
                    isError = !parsedChars.isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(parsedDocuments.value, parsedTokens.value, parsedChars.value)
                },
                enabled = allValid,
            ) {
                Text(stringResource(R.string.assistant_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.assistant_page_cancel))
            }
        },
    )
}

internal data class AssistantMemoryTableDocumentSelection(
    val visibleDocuments: List<MemoryTableDocument>,
    val primaryDocumentsByTemplate: Map<String, MemoryTableDocument?>,
    val extraDocuments: List<MemoryTableDocument>,
)

internal fun deriveAssistantMemoryTableDocuments(
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    assistantId: String,
): AssistantMemoryTableDocumentSelection {
    val visibleTemplateIds = templates.mapTo(mutableSetOf()) { it.id }
    val visibleDocuments = documents.filter {
        it.templateId in visibleTemplateIds && it.isEffectiveFor(assistantId = assistantId)
    }
    val documentsByTemplate = visibleDocuments.groupBy { it.templateId }
    val primaryDocumentsByTemplate = templates.associate { template ->
        val documentsForTemplate = documentsByTemplate[template.id].orEmpty()
        val primaryDocument = documentsForTemplate.firstOrNull {
            it.scopeType == MemoryTableScopeType.ASSISTANT
        } ?: documentsForTemplate.firstOrNull {
            it.scopeType == MemoryTableScopeType.GLOBAL
        }
        template.id to primaryDocument
    }
    val primaryDocumentIds = primaryDocumentsByTemplate.values
        .filterNotNull()
        .mapTo(mutableSetOf()) { it.id }
    return AssistantMemoryTableDocumentSelection(
        visibleDocuments = visibleDocuments,
        primaryDocumentsByTemplate = primaryDocumentsByTemplate,
        extraDocuments = visibleDocuments.filter { it.id !in primaryDocumentIds },
    )
}

@Composable
private fun MemoryTableSection(
    settings: Settings,
    assistant: Assistant,
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    onAddDocument: () -> Unit,
    onEditDocument: (MemoryTableDocument) -> Unit,
    onDeleteDocument: (MemoryTableDocument) -> Unit,
) {
    val enabled = settings.enableMemoryTable && assistant.enableMemoryTable
    val visibleTemplates = templates.filter { it.isEffectiveFor(assistant.id.toString()) }
    val selection = deriveAssistantMemoryTableDocuments(
        templates = visibleTemplates,
        documents = documents,
        assistantId = assistant.id.toString(),
    )
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
            onClick = onAddDocument,
            enabled = settings.enableMemoryTable,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(HugeIcons.Add01, null)
        }
    }

    if (selection.visibleDocuments.isEmpty()) {
        Text(
            text = stringResource(R.string.assistant_page_memory_table_no_documents),
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    selection.visibleDocuments.fastForEach { document ->
        key(document.id) {
            MemoryTableDocumentItem(
                document = document,
                template = visibleTemplates.firstOrNull { it.id == document.templateId },
                enabled = enabled,
                onEdit = { onEditDocument(document) },
                onDelete = { onDeleteDocument(document) },
            )
        }
    }
}

@Composable
private fun MemoryTableDocumentItem(
    document: MemoryTableDocument,
    template: MemoryTableTemplate?,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onEdit),
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
                    text = template?.name ?: stringResource(R.string.assistant_page_memory_table_document),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = stringResource(
                        R.string.assistant_page_memory_table_document_meta,
                        stringResource(
                            when (document.scopeType) {
                                MemoryTableScopeType.GLOBAL -> R.string.assistant_page_memory_scope_global
                                MemoryTableScopeType.ASSISTANT -> R.string.assistant_page_memory_scope_assistant
                                MemoryTableScopeType.CONVERSATION -> {
                                    R.string.assistant_page_memory_table_scope_conversation
                                }
                            }
                        ),
                        document.revision,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(HugeIcons.Delete01, null)
                }
            }
        }
    }
}

private fun MemoryTableDocument.toMemoryTableEditorScreen(assistant: Assistant) =
    Screen.AssistantMemoryTableDocumentEditor(
        documentId = id,
        templateId = templateId,
        assistantId = assistant.id.toString(),
        scopeType = scopeType,
    )

@Composable
private fun AddMemoryTableDialog(
    templates: List<MemoryTableTemplate>,
    defaultTemplateName: String,
    onDismiss: () -> Unit,
    onCreateFromTemplate: (MemoryTableTemplate, (Result<MemoryTableDocument>) -> Unit) -> Unit,
    onCreateTemplate: (
        MemoryTableTemplate,
        MemoryTableScopeType,
        (Result<MemoryTableDocument>) -> Unit,
    ) -> Unit,
) {
    var name by remember(defaultTemplateName) { mutableStateOf(defaultTemplateName) }
    var description by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val genericError = stringResource(R.string.assistant_page_memory_table_create_failed)
    val handleResult: (Result<MemoryTableDocument>) -> Unit = { result ->
        isSaving = false
        errorMessage = result.exceptionOrNull()?.let { genericError }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.assistant_page_memory_table_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (templates.isNotEmpty()) {
                    Text(stringResource(R.string.assistant_page_memory_table_choose_template))
                    templates.fastForEach { template ->
                        TextButton(
                            onClick = {
                                isSaving = true
                                errorMessage = null
                                onCreateFromTemplate(template, handleResult)
                            },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(stringResource(R.string.assistant_page_memory_table_create_template))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.assistant_page_memory_table_template_name)) },
                    enabled = !isSaving,
                    singleLine = true,
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.assistant_page_memory_table_template_description)) },
                    enabled = !isSaving,
                )
                errorMessage?.let {
                    Text(it.ifBlank { genericError }, color = MaterialTheme.colorScheme.error)
                }
                if (isSaving) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSaving = true
                    errorMessage = null
                    onCreateTemplate(
                        MemoryTableTemplate(name = name, description = description),
                        MemoryTableScopeType.ASSISTANT,
                        handleResult,
                    )
                },
                enabled = !isSaving && name.isNotBlank(),
            ) { Text(stringResource(R.string.assistant_page_memory_table_create_private)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        isSaving = true
                        errorMessage = null
                        onCreateTemplate(
                            MemoryTableTemplate(name = name, description = description),
                            MemoryTableScopeType.GLOBAL,
                            handleResult,
                        )
                    },
                    enabled = !isSaving && name.isNotBlank(),
                ) { Text(stringResource(R.string.assistant_page_memory_table_create_global)) }
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        },
    )
}

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
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
}
