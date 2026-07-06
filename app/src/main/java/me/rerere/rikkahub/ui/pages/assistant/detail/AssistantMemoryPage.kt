package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
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
    val memoryTableTemplateDialogState = useEditState<MemoryTableTemplate> {
        onUpsertMemoryTableTemplate(it)
    }
    var pendingDeleteMemory by remember { mutableStateOf<AssistantMemory?>(null) }
    var pendingDeleteMemoryTableTemplate by remember { mutableStateOf<MemoryTableTemplate?>(null) }
    var pendingDeleteMemoryTableDocument by remember { mutableStateOf<MemoryTableDocument?>(null) }
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
                        value = template.description,
                        onValueChange = { update(template.copy(description = it)) },
                        label = { Text(stringResource(R.string.assistant_page_memory_table_template_description)) },
                        minLines = 2,
                        maxLines = 4,
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
            onDeleteTemplate = { pendingDeleteMemoryTableTemplate = it },
            onAddDocument = { template ->
                navController.navigate(
                    Screen.AssistantMemoryTableDocumentEditor(
                        documentId = null,
                        templateId = template.id,
                        assistantId = assistant.id.toString(),
                        scopeType = MemoryTableScopeType.ASSISTANT,
                    ),
                )
            },
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

@Composable
private fun MemoryTableSection(
    settings: Settings,
    assistant: Assistant,
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    onAddTemplate: () -> Unit,
    onDeleteTemplate: (MemoryTableTemplate) -> Unit,
    onAddDocument: (MemoryTableTemplate) -> Unit,
    onEditDocument: (MemoryTableDocument) -> Unit,
    onDeleteDocument: (MemoryTableDocument) -> Unit,
) {
    val enabled = settings.enableMemoryTable && assistant.enableMemoryTable
    val documentsByTemplate = documents.groupBy { it.templateId }
    val primaryDocumentsByTemplate = templates.associate { template ->
        val document = documentsByTemplate[template.id]
            ?.firstOrNull {
                it.scopeType == MemoryTableScopeType.ASSISTANT &&
                    it.scopeId == assistant.id.toString()
            }
            ?: documentsByTemplate[template.id]
                ?.firstOrNull { it.scopeType == MemoryTableScopeType.GLOBAL }
            ?: documentsByTemplate[template.id]?.firstOrNull()
        template.id to document
    }
    val primaryDocumentIds = primaryDocumentsByTemplate.values
        .filterNotNull()
        .map { it.id }
        .toSet()
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
        val document = primaryDocumentsByTemplate[template.id]
        key(template.id) {
            MemoryTableTemplateItem(
                template = template,
                document = document,
                enabled = settings.enableMemoryTable,
                onDelete = {
                    if (document != null) {
                        onDeleteDocument(document)
                    } else {
                        onDeleteTemplate(template)
                    }
                },
                onOpenDocument = {
                    if (document != null) {
                        onEditDocument(document)
                    } else {
                        onAddDocument(template)
                    }
                },
            )
        }
    }

    documents.filter { it.id !in primaryDocumentIds }.fastForEach { document ->
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
    document: MemoryTableDocument?,
    enabled: Boolean,
    onDelete: () -> Unit,
    onOpenDocument: () -> Unit,
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
                    text = template.description.ifBlank {
                        stringResource(R.string.assistant_page_memory_table_no_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                document?.let {
                    Text(
                        text = stringResource(
                            R.string.assistant_page_memory_table_document_meta,
                            it.scopeType.name,
                            it.revision,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onOpenDocument, enabled = enabled) {
                    Icon(if (document == null) HugeIcons.Add01 else HugeIcons.PencilEdit01, null)
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(HugeIcons.Delete01, null)
                }
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
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(HugeIcons.PencilEdit01, null)
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(HugeIcons.Delete01, null)
                }
            }
        }
    }
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
