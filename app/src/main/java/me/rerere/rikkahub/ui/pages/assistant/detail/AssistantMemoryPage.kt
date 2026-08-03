package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.RestoreBin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.MemoryTableTemplateNameConflictException
import me.rerere.rikkahub.data.model.isEffectiveFor
import me.rerere.rikkahub.data.model.normalizeMemoryTableTemplateName
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
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
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val moveToTrashSuccess = stringResource(R.string.assistant_page_memory_table_move_to_trash_success)
    val moveToTrashError = stringResource(R.string.assistant_page_memory_table_move_to_trash_error)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_memory))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AssistantMemoryTableTrash(id)) }) {
                        Icon(
                            imageVector = HugeIcons.RestoreBin,
                            contentDescription = stringResource(
                                R.string.assistant_page_memory_table_trash_content_description
                            ),
                        )
                    }
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
            onUpsertMemoryTableTemplate = { template, requestedScopeType, onDone ->
                vm.upsertMemoryTableTemplate(template, requestedScopeType, onDone)
            },
            onCopyMemoryTableTemplate = { template, copyName, onDone ->
                vm.copyGlobalMemoryTableTemplate(template, copyName, onDone)
            },
            onDeleteMemoryTableTemplate = { template, onDone ->
                vm.deleteMemoryTableTemplate(template, onDone)
            },
            onDeleteMemoryTableDocument = { document ->
                vm.deleteMemoryTableDocument(document) { result ->
                    toaster.show(
                        if (result.isSuccess) moveToTrashSuccess else moveToTrashError,
                        type = if (result.isSuccess) ToastType.Success else ToastType.Error,
                    )
                }
            },
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
    onUpsertMemoryTableTemplate: (
        MemoryTableTemplate,
        MemoryTableScopeType?,
        (Result<MemoryTableTemplate>) -> Unit,
    ) -> Unit,
    onCopyMemoryTableTemplate: (
        MemoryTableTemplate,
        String,
        (Result<MemoryTableTemplate>) -> Unit,
    ) -> Unit,
    onDeleteMemoryTableTemplate: (MemoryTableTemplate, (Result<Boolean>) -> Unit) -> Unit,
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
    var showAddMemoryTableSheet by remember { mutableStateOf(false) }
    var showMemoryTableBudgetDialog by remember { mutableStateOf(false) }
    val defaultMemoryTableTemplateName = stringResource(R.string.assistant_page_memory_table_default_template)
    val memoryTableBudgetUnlimitedLabel =
        stringResource(R.string.assistant_page_memory_table_budget_unlimited)
    val assistantId = assistant.id.toString()
    val effectiveMemoryTableTemplates = remember(memoryTableTemplates, assistantId) {
        memoryTableTemplates.filter { it.isEffectiveFor(assistantId) }
    }
    val memoryTableDocumentSelection = remember(
        effectiveMemoryTableTemplates,
        memoryTableDocuments,
        assistantId,
    ) {
        deriveAssistantMemoryTableDocuments(
            templates = effectiveMemoryTableTemplates,
            documents = memoryTableDocuments,
            assistantId = assistantId,
        )
    }
    val memoryTableTemplatesById = remember(effectiveMemoryTableTemplates) {
        effectiveMemoryTableTemplates.associateBy { it.id }
    }

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
            // [SemanticMemory Plugin] third memory mode, independent of enableMemory / memory table
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_semantic_memory)) },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_semantic_memory_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableSemanticMemory,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(enableSemanticMemory = it))
                        },
                        enabled = settings.semanticMemoryConfig.enabled,
                    )
                },
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
                onClick = { navController.navigate(Screen.AssistantHooks(assistantId)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_table_auto_sync)) },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_memory_table_auto_sync_desc))
                },
                trailingContent = {
                    Switch(
                        checked = settings.memoryTableAutoSyncEnabled,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(memoryTableAutoSyncEnabled = it))
                        },
                        enabled = settings.enableMemoryTable,
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
            enabled = settings.enableMemoryTable && assistant.enableMemoryTable,
            addEnabled = settings.enableMemoryTable,
            templatesById = memoryTableTemplatesById,
            selection = memoryTableDocumentSelection,
            onAddDocument = { showAddMemoryTableSheet = true },
            onEditDocument = { document ->
                navController.navigate(document.toMemoryTableEditorScreen(assistant))
            },
            onHistoryDocument = { document ->
                navController.navigate(document.toMemoryTableRevisionHistoryScreen(assistant))
            },
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
                    contentDescription = stringResource(R.string.add),
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

    if (showAddMemoryTableSheet) {
        AddMemoryTableSheet(
            templates = effectiveMemoryTableTemplates,
            primaryDocumentsByTemplate = memoryTableDocumentSelection.primaryDocumentsByTemplate,
            assistantId = assistantId,
            defaultTemplateName = defaultMemoryTableTemplateName,
            onDismiss = { showAddMemoryTableSheet = false },
            onOpenDocument = { document ->
                showAddMemoryTableSheet = false
                navController.navigate(document.toMemoryTableEditorScreen(assistant))
            },
            onCreateFromTemplate = { template, onDone ->
                onCreateMemoryTableDocument(template) { result ->
                    result.onSuccess { document ->
                        showAddMemoryTableSheet = false
                        navController.navigate(document.toMemoryTableEditorScreen(assistant))
                    }
                    onDone(result)
                }
            },
            onCreateTemplate = { template, scopeType, onDone ->
                onCreateMemoryTableTemplateAndDocument(template, scopeType) { result ->
                    result.onSuccess { document ->
                        showAddMemoryTableSheet = false
                        navController.navigate(document.toMemoryTableEditorScreen(assistant))
                    }
                    onDone(result)
                }
            },
            onUpsertTemplate = onUpsertMemoryTableTemplate,
            onCopyTemplate = onCopyMemoryTableTemplate,
            onDeleteTemplate = onDeleteMemoryTableTemplate,
        )
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

    pendingDeleteMemoryTableDocument?.let { document ->
        val documentName = memoryTableTemplatesById[document.templateId]
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.assistant_page_memory_table_document)
        AlertDialog(
            onDismissRequest = { pendingDeleteMemoryTableDocument = null },
            title = {
                Text(stringResource(R.string.assistant_page_memory_table_delete_title, documentName))
            },
            text = {
                Text(stringResource(R.string.assistant_page_memory_table_delete_description))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMemoryTableDocument(document)
                        pendingDeleteMemoryTableDocument = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.assistant_page_memory_table_trash))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMemoryTableDocument = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
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
    enabled: Boolean,
    addEnabled: Boolean,
    templatesById: Map<String, MemoryTableTemplate>,
    selection: AssistantMemoryTableDocumentSelection,
    onAddDocument: () -> Unit,
    onEditDocument: (MemoryTableDocument) -> Unit,
    onHistoryDocument: (MemoryTableDocument) -> Unit,
    onDeleteDocument: (MemoryTableDocument) -> Unit,
) {
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
            enabled = addEnabled,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = HugeIcons.Add01,
                contentDescription = stringResource(R.string.assistant_page_memory_table_add_title),
            )
        }
    }

    if (selection.visibleDocuments.isEmpty()) {
        MemoryTableEmptyState(
            addEnabled = addEnabled,
            onAddDocument = onAddDocument,
        )
    }

    selection.visibleDocuments.fastForEach { document ->
        key(document.id) {
            MemoryTableDocumentItem(
                document = document,
                template = templatesById[document.templateId],
                enabled = enabled,
                onEdit = { onEditDocument(document) },
                onHistory = { onHistoryDocument(document) },
                onDelete = { onDeleteDocument(document) },
            )
        }
    }
}

internal fun deriveMemoryTablePickerTemplates(
    templates: List<MemoryTableTemplate>,
    primaryDocumentsByTemplate: Map<String, MemoryTableDocument?>,
    assistantId: String,
): List<MemoryTableTemplate> = templates
    .groupBy { normalizeMemoryTableTemplateName(it.name) }
    .values
    .map { duplicates ->
        duplicates.minWith(
            compareByDescending<MemoryTableTemplate> { primaryDocumentsByTemplate[it.id] != null }
                .thenByDescending {
                    it.scopeType == MemoryTableScopeType.ASSISTANT && it.scopeId == assistantId
                }
                .thenByDescending { it.scopeType == MemoryTableScopeType.GLOBAL }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id }
        )
    }

internal fun hasMemoryTableTemplateNameConflict(
    name: String,
    scopeType: MemoryTableScopeType,
    templates: List<MemoryTableTemplate>,
    assistantId: String,
    excludedTemplateId: String? = null,
): Boolean {
    val normalizedName = normalizeMemoryTableTemplateName(name)
    if (normalizedName.isEmpty()) return false
    return templates.any { template ->
        template.id != excludedTemplateId &&
            normalizeMemoryTableTemplateName(template.name) == normalizedName &&
            when (scopeType) {
                MemoryTableScopeType.GLOBAL -> true
                MemoryTableScopeType.ASSISTANT ->
                    template.scopeType == MemoryTableScopeType.GLOBAL ||
                        (template.scopeType == MemoryTableScopeType.ASSISTANT && template.scopeId == assistantId)
                MemoryTableScopeType.CONVERSATION -> false
            }
    }
}

@Composable
private fun MemoryTableEmptyState(
    addEnabled: Boolean,
    onAddDocument: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = HugeIcons.Database02,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.assistant_page_memory_table_no_documents),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.assistant_page_memory_table_empty_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAddDocument, enabled = addEnabled) {
            Icon(imageVector = HugeIcons.Add01, contentDescription = null)
            Text(stringResource(R.string.assistant_page_memory_table_add_title))
        }
    }
}

@Composable
private fun MemoryTableDocumentItem(
    document: MemoryTableDocument,
    template: MemoryTableTemplate?,
    enabled: Boolean,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val documentName = template?.name
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.assistant_page_memory_table_document)
    val documentDescription = template?.description
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.assistant_page_memory_table_no_description)
    val scopeLabel = memoryTableScopeLabel(document.scopeType)

    Card(
        onClick = onEdit,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = HugeIcons.Database02,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                Text(
                    text = documentName,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = HugeIcons.MoreVertical,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = HugeIcons.Delete01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Text(
                text = documentDescription,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tag(type = TagType.INFO, onClick = onHistory) {
                    Text(
                        stringResource(
                            R.string.assistant_page_memory_table_document_meta,
                            scopeLabel,
                            document.revision,
                        )
                    )
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

private fun MemoryTableDocument.toMemoryTableRevisionHistoryScreen(assistant: Assistant) =
    Screen.AssistantMemoryTableRevisionHistory(
        documentId = id,
        assistantId = assistant.id.toString(),
    )

private enum class AddMemoryTableSheetMode {
    TEMPLATE_PICKER,
    CREATE_TEMPLATE,
    MANAGE_TEMPLATES,
    EDIT_TEMPLATE,
}

private data class PendingMemoryTableTemplateMigration(
    val draft: MemoryTableTemplate,
    val targetScopeType: MemoryTableScopeType,
)

private data class PendingMemoryTableTemplateCopy(
    val template: MemoryTableTemplate,
    val name: String,
)

@Composable
private fun AddMemoryTableSheet(
    templates: List<MemoryTableTemplate>,
    primaryDocumentsByTemplate: Map<String, MemoryTableDocument?>,
    assistantId: String,
    defaultTemplateName: String,
    onDismiss: () -> Unit,
    onOpenDocument: (MemoryTableDocument) -> Unit,
    onCreateFromTemplate: (MemoryTableTemplate, (Result<MemoryTableDocument>) -> Unit) -> Unit,
    onCreateTemplate: (
        MemoryTableTemplate,
        MemoryTableScopeType,
        (Result<MemoryTableDocument>) -> Unit,
    ) -> Unit,
    onUpsertTemplate: (
        MemoryTableTemplate,
        MemoryTableScopeType?,
        (Result<MemoryTableTemplate>) -> Unit,
    ) -> Unit,
    onCopyTemplate: (MemoryTableTemplate, String, (Result<MemoryTableTemplate>) -> Unit) -> Unit,
    onDeleteTemplate: (MemoryTableTemplate, (Result<Boolean>) -> Unit) -> Unit,
) {
    val toaster = LocalToaster.current
    var mode by remember { mutableStateOf(AddMemoryTableSheetMode.TEMPLATE_PICKER) }
    var name by remember(defaultTemplateName) { mutableStateOf(defaultTemplateName) }
    var description by remember { mutableStateOf("") }
    var scopeType by remember { mutableStateOf(MemoryTableScopeType.ASSISTANT) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editingTemplate by remember { mutableStateOf<MemoryTableTemplate?>(null) }
    var pendingDeleteTemplate by remember { mutableStateOf<MemoryTableTemplate?>(null) }
    var pendingScopeMigration by remember { mutableStateOf<PendingMemoryTableTemplateMigration?>(null) }
    var pendingCopyTemplate by remember { mutableStateOf<PendingMemoryTableTemplateCopy?>(null) }
    val genericError = stringResource(R.string.assistant_page_memory_table_create_failed)
    val conflictError = stringResource(R.string.assistant_page_memory_table_name_conflict)
    val updateError = stringResource(R.string.assistant_page_memory_table_template_update_failed)
    val deleteError = stringResource(R.string.assistant_page_memory_table_template_delete_failed)
    val migrationSuccess = stringResource(R.string.assistant_page_memory_table_template_migration_success)
    val migrationError = stringResource(R.string.assistant_page_memory_table_template_migration_failed)
    val copySuccess = stringResource(R.string.assistant_page_memory_table_template_copy_success)
    val copyError = stringResource(R.string.assistant_page_memory_table_template_copy_failed)
    val pickerTemplates = remember(templates, primaryDocumentsByTemplate, assistantId) {
        deriveMemoryTablePickerTemplates(templates, primaryDocumentsByTemplate, assistantId)
    }
    val handleResult: (Result<MemoryTableDocument>) -> Unit = { result ->
        isSaving = false
        errorMessage = result.exceptionOrNull()?.let { error ->
            if (error is MemoryTableTemplateNameConflictException) conflictError else genericError
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetGesturesEnabled = !isSaving,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            when (mode) {
                AddMemoryTableSheetMode.TEMPLATE_PICKER -> {
                    MemoryTableTemplatePicker(
                        templates = pickerTemplates,
                        primaryDocumentsByTemplate = primaryDocumentsByTemplate,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                        onSelectTemplate = { template ->
                            val existingDocument = primaryDocumentsByTemplate[template.id]
                            if (existingDocument != null) {
                                onOpenDocument(existingDocument)
                            } else {
                                isSaving = true
                                errorMessage = null
                                onCreateFromTemplate(template, handleResult)
                            }
                        },
                        onCreateTemplate = {
                            name = defaultTemplateName
                            description = ""
                            scopeType = MemoryTableScopeType.ASSISTANT
                            editingTemplate = null
                            errorMessage = null
                            mode = AddMemoryTableSheetMode.CREATE_TEMPLATE
                        },
                        onManageTemplates = {
                            errorMessage = null
                            mode = AddMemoryTableSheetMode.MANAGE_TEMPLATES
                        },
                        onDismiss = onDismiss,
                    )
                }

                AddMemoryTableSheetMode.CREATE_TEMPLATE -> {
                    val hasConflict = hasMemoryTableTemplateNameConflict(
                        name = name,
                        scopeType = scopeType,
                        templates = templates,
                        assistantId = assistantId,
                    )
                    MemoryTableTemplateForm(
                        name = name,
                        description = description,
                        scopeType = scopeType,
                        isSaving = isSaving,
                        errorMessage = if (hasConflict) conflictError else errorMessage,
                        hasNameConflict = hasConflict,
                        title = stringResource(R.string.assistant_page_memory_table_create_new_template),
                        submitLabel = stringResource(R.string.assistant_page_memory_table_create),
                        scopeEditable = true,
                        onNameChange = { name = it; errorMessage = null },
                        onDescriptionChange = { description = it },
                        onScopeChange = {
                            scopeType = it
                            errorMessage = null
                        },
                        onBack = {
                            errorMessage = null
                            mode = AddMemoryTableSheetMode.TEMPLATE_PICKER
                        },
                        onCreate = {
                            if (!hasConflict) {
                                isSaving = true
                                errorMessage = null
                                onCreateTemplate(
                                    MemoryTableTemplate(
                                        name = name.trim(),
                                        description = description.trim(),
                                    ),
                                    scopeType,
                                    handleResult,
                                )
                            }
                        },
                    )
                }

                AddMemoryTableSheetMode.MANAGE_TEMPLATES -> {
                    MemoryTableTemplateManager(
                        templates = templates,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                        onBack = { mode = AddMemoryTableSheetMode.TEMPLATE_PICKER },
                        onEdit = { template ->
                            editingTemplate = template
                            name = template.name
                            description = template.description
                            scopeType = template.scopeType
                            errorMessage = null
                            mode = AddMemoryTableSheetMode.EDIT_TEMPLATE
                        },
                        onCopy = { template, copyName ->
                            errorMessage = null
                            pendingCopyTemplate = PendingMemoryTableTemplateCopy(template, copyName)
                        },
                        onDelete = { pendingDeleteTemplate = it },
                    )
                }

                AddMemoryTableSheetMode.EDIT_TEMPLATE -> editingTemplate?.let { template ->
                    val isScopeChanging = shouldConfirmMemoryTableTemplateScopeMigration(
                        currentScopeType = template.scopeType,
                        targetScopeType = scopeType,
                    )
                    val shouldCheckConflict = isScopeChanging ||
                        normalizeMemoryTableTemplateName(name) != normalizeMemoryTableTemplateName(template.name)
                    val hasConflict = shouldCheckConflict &&
                        hasMemoryTableTemplateNameConflict(
                            name = name,
                            scopeType = scopeType,
                            templates = templates,
                            assistantId = assistantId,
                            excludedTemplateId = template.id,
                        )
                    MemoryTableTemplateForm(
                        name = name,
                        description = description,
                        scopeType = scopeType,
                        isSaving = isSaving,
                        errorMessage = if (hasConflict) conflictError else errorMessage,
                        hasNameConflict = hasConflict,
                        title = stringResource(R.string.assistant_page_memory_table_edit_template),
                        submitLabel = stringResource(R.string.assistant_page_memory_table_save_template),
                        scopeEditable = true,
                        onNameChange = { name = it; errorMessage = null },
                        onDescriptionChange = { description = it; errorMessage = null },
                        onScopeChange = {
                            scopeType = it
                            errorMessage = null
                        },
                        onBack = { errorMessage = null; mode = AddMemoryTableSheetMode.MANAGE_TEMPLATES },
                        onCreate = {
                            if (!hasConflict) {
                                errorMessage = null
                                val draft = template.copy(
                                    name = name.trim(),
                                    description = description.trim(),
                                    scopeType = scopeType,
                                )
                                if (isScopeChanging) {
                                    pendingScopeMigration = PendingMemoryTableTemplateMigration(
                                        draft = draft,
                                        targetScopeType = scopeType,
                                    )
                                } else {
                                    isSaving = true
                                    onUpsertTemplate(draft, null) { result ->
                                        isSaving = false
                                        result.onSuccess {
                                            editingTemplate = null
                                            mode = AddMemoryTableSheetMode.MANAGE_TEMPLATES
                                        }.onFailure { error ->
                                            errorMessage = if (error is MemoryTableTemplateNameConflictException) {
                                                conflictError
                                            } else updateError
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDeleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { if (!isSaving) pendingDeleteTemplate = null },
            title = {
                Text(stringResource(R.string.assistant_page_memory_table_delete_template_title, template.name))
            },
            text = {
                val isGlobalTemplate = template.scopeType == MemoryTableScopeType.GLOBAL
                Text(
                    stringResource(
                        if (isGlobalTemplate) {
                            R.string.assistant_page_memory_table_delete_global_template_description
                        } else {
                            R.string.assistant_page_memory_table_delete_template_description
                        }
                    ),
                    color = if (isGlobalTemplate) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        isSaving = true
                        onDeleteTemplate(template) { result ->
                            isSaving = false
                            pendingDeleteTemplate = null
                            errorMessage = result.fold(
                                onSuccess = { deleted -> if (deleted) null else deleteError },
                                onFailure = { deleteError },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(enabled = !isSaving, onClick = { pendingDeleteTemplate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingScopeMigration?.let { migration ->
        val isMovingToGlobal = migration.targetScopeType == MemoryTableScopeType.GLOBAL
        AlertDialog(
            onDismissRequest = { if (!isSaving) pendingScopeMigration = null },
            title = {
                Text(stringResource(R.string.assistant_page_memory_table_template_migration_title))
            },
            text = {
                Text(
                    stringResource(
                        if (isMovingToGlobal) {
                            R.string.assistant_page_memory_table_template_migration_to_global_description
                        } else {
                            R.string.assistant_page_memory_table_template_migration_to_assistant_description
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        errorMessage = null
                        onUpsertTemplate(
                            migration.draft,
                            migration.targetScopeType,
                        ) { result ->
                            isSaving = false
                            result.onSuccess {
                                pendingScopeMigration = null
                                editingTemplate = null
                                mode = AddMemoryTableSheetMode.MANAGE_TEMPLATES
                                toaster.show(migrationSuccess, type = ToastType.Success)
                            }.onFailure { error ->
                                pendingScopeMigration = null
                                errorMessage = if (error is MemoryTableTemplateNameConflictException) {
                                    conflictError
                                } else migrationError
                                toaster.show(migrationError, type = ToastType.Error)
                            }
                        }
                    },
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.assistant_page_memory_table_template_migrate))
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isSaving, onClick = { pendingScopeMigration = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingCopyTemplate?.let { pendingCopy ->
        val hasConflict = hasMemoryTableTemplateNameConflict(
            name = pendingCopy.name,
            scopeType = MemoryTableScopeType.ASSISTANT,
            templates = templates,
            assistantId = assistantId,
        )
        AlertDialog(
            onDismissRequest = { if (!isSaving) pendingCopyTemplate = null },
            title = {
                Text(stringResource(R.string.assistant_page_memory_table_template_copy_to_assistant))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.assistant_page_memory_table_template_copy_description))
                    OutlinedTextField(
                        value = pendingCopy.name,
                        onValueChange = { newName ->
                            pendingCopyTemplate = pendingCopy.copy(name = newName)
                            errorMessage = null
                        },
                        enabled = !isSaving,
                        isError = hasConflict,
                        singleLine = true,
                        label = { Text(stringResource(R.string.assistant_page_memory_table_template_name)) },
                    )
                    if (hasConflict) {
                        Text(
                            text = conflictError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving && pendingCopy.name.trim().isNotEmpty() && !hasConflict,
                    onClick = {
                        isSaving = true
                        errorMessage = null
                        onCopyTemplate(pendingCopy.template, pendingCopy.name.trim()) { result ->
                            isSaving = false
                            result.onSuccess {
                                pendingCopyTemplate = null
                                toaster.show(copySuccess, type = ToastType.Success)
                            }.onFailure { error ->
                                errorMessage = if (error is MemoryTableTemplateNameConflictException) {
                                    conflictError
                                } else copyError
                                toaster.show(copyError, type = ToastType.Error)
                            }
                        }
                    },
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.assistant_page_memory_table_template_copy_to_assistant))
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isSaving, onClick = { pendingCopyTemplate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryTableTemplatePicker(
    templates: List<MemoryTableTemplate>,
    primaryDocumentsByTemplate: Map<String, MemoryTableDocument?>,
    isSaving: Boolean,
    errorMessage: String?,
    onSelectTemplate: (MemoryTableTemplate) -> Unit,
    onCreateTemplate: () -> Unit,
    onManageTemplates: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.assistant_page_memory_table_add_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.assistant_page_memory_table_choose_template),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = templates, key = { it.id }) { template ->
                MemoryTableTemplateRow(
                    template = template,
                    isAdded = primaryDocumentsByTemplate[template.id] != null,
                    enabled = !isSaving,
                    onClick = { onSelectTemplate(template) },
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = !isSaving, onClick = onCreateTemplate)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .alpha(if (isSaving) 0.38f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = HugeIcons.Add01, contentDescription = null)
            Text(
                text = stringResource(R.string.assistant_page_memory_table_create_new_template),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = !isSaving, onClick = onManageTemplates)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .alpha(if (isSaving) 0.38f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = HugeIcons.PencilEdit01, contentDescription = null)
            Text(
                text = stringResource(R.string.assistant_page_memory_table_manage_templates),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (isSaving) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun MemoryTableTemplateRow(
    template: MemoryTableTemplate,
    isAdded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Database02,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = template.description.ifBlank {
                    stringResource(R.string.assistant_page_memory_table_no_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tag(type = TagType.INFO) {
                    Text(memoryTableScopeLabel(template.scopeType))
                }
                if (isAdded) {
                    Tag(type = TagType.SUCCESS) {
                        Icon(
                            imageVector = HugeIcons.Tick02,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(stringResource(R.string.assistant_page_memory_table_added))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTableTemplateManager(
    templates: List<MemoryTableTemplate>,
    isSaving: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onEdit: (MemoryTableTemplate) -> Unit,
    onCopy: (MemoryTableTemplate, String) -> Unit,
    onDelete: (MemoryTableTemplate) -> Unit,
) {
    val duplicateNames = remember(templates) {
        templates.groupingBy { normalizeMemoryTableTemplateName(it.name) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !isSaving) {
                Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.assistant_page_memory_table_manage_templates),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(templates, key = { it.id }) { template ->
                var menuExpanded by remember { mutableStateOf(false) }
                val copyName = stringResource(
                    R.string.assistant_page_memory_table_template_copy_name,
                    template.name,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = template.description.ifBlank {
                                stringResource(R.string.assistant_page_memory_table_no_description)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(type = TagType.INFO) { Text(memoryTableScopeLabel(template.scopeType)) }
                            if (normalizeMemoryTableTemplateName(template.name) in duplicateNames) {
                                Tag(type = TagType.WARNING) {
                                    Text(stringResource(R.string.assistant_page_memory_table_duplicate_name))
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, enabled = !isSaving) {
                            Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                                onClick = { menuExpanded = false; onEdit(template) },
                            )
                            if (template.scopeType == MemoryTableScopeType.GLOBAL) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.assistant_page_memory_table_template_copy_to_assistant))
                                    },
                                    leadingIcon = { Icon(HugeIcons.Add01, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onCopy(template, copyName)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = HugeIcons.Delete01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = { menuExpanded = false; onDelete(template) },
                            )
                        }
                    }
                }
            }
        }
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun MemoryTableTemplateForm(
    name: String,
    description: String,
    scopeType: MemoryTableScopeType,
    isSaving: Boolean,
    errorMessage: String?,
    hasNameConflict: Boolean,
    title: String,
    submitLabel: String,
    scopeEditable: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onScopeChange: (MemoryTableScopeType) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !isSaving) {
                Icon(
                    imageVector = HugeIcons.ArrowLeft01,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.assistant_page_memory_table_template_name)) },
            enabled = !isSaving,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = hasNameConflict,
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.assistant_page_memory_table_template_description)) },
            enabled = !isSaving,
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (scopeEditable) {
            MemoryTableScopeOption(
                scopeType = MemoryTableScopeType.ASSISTANT,
                selected = scopeType == MemoryTableScopeType.ASSISTANT,
                enabled = !isSaving,
                description = stringResource(R.string.assistant_page_memory_table_scope_assistant_desc),
                onSelect = onScopeChange,
            )
            MemoryTableScopeOption(
                scopeType = MemoryTableScopeType.GLOBAL,
                selected = scopeType == MemoryTableScopeType.GLOBAL,
                enabled = !isSaving,
                description = stringResource(R.string.assistant_page_memory_table_scope_global_desc),
                onSelect = onScopeChange,
            )
        } else {
            Tag(type = TagType.INFO) { Text(memoryTableScopeLabel(scopeType)) }
        }
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onCreate,
            enabled = !isSaving && name.trim().isNotEmpty() && !hasNameConflict,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(submitLabel)
            }
        }
    }
}

@Composable
private fun MemoryTableScopeOption(
    scopeType: MemoryTableScopeType,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onSelect: (MemoryTableScopeType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled) { onSelect(scopeType) }
            .padding(12.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { onSelect(scopeType) },
            enabled = enabled,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = memoryTableScopeLabel(scopeType),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun memoryTableScopeLabel(scopeType: MemoryTableScopeType): String =
    stringResource(
        when (scopeType) {
            MemoryTableScopeType.GLOBAL -> R.string.assistant_page_memory_scope_global
            MemoryTableScopeType.ASSISTANT -> R.string.assistant_page_memory_scope_assistant
            MemoryTableScopeType.CONVERSATION -> R.string.assistant_page_memory_table_scope_conversation
        }
    )

internal fun shouldConfirmMemoryTableTemplateScopeMigration(
    currentScopeType: MemoryTableScopeType,
    targetScopeType: MemoryTableScopeType,
): Boolean = currentScopeType != targetScopeType

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
                    Icon(
                        imageVector = HugeIcons.PencilEdit01,
                        contentDescription = stringResource(R.string.edit),
                    )
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
