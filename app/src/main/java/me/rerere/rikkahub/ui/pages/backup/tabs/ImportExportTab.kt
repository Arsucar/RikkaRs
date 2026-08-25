package me.rerere.rikkahub.ui.pages.backup.tabs

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Cancel01
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupOperation
import me.rerere.rikkahub.data.sync.BackupTaskState
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.StickyHeader
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ImportExportTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val taskStates by vm.taskStates.collectAsStateWithLifecycle()
    val exportState = taskStates.getValue(BackupOperation.LOCAL_EXPORT)
    val importState = taskStates.getValue(BackupOperation.LOCAL_IMPORT)
    val isExporting = exportState is BackupTaskState.Running
    val isImportBusy = importState is BackupTaskState.Running
    val selectedBackupItems by vm.localBackupItems.collectAsStateWithLifecycle()
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(importState) {
        if (importState == BackupTaskState.Success &&
            vm.consumeTaskSuccess(BackupOperation.LOCAL_IMPORT)
        ) {
            onShowRestartDialog()
        }
    }

    // 导入类型：local 为本地备份，chatbox 为 Chatbox 导入，cherry 为 Cherry Studio 导入
    var importType by remember { mutableStateOf("local") }
    val isLocalImporting = isImportBusy && importType == "local"
    val isChatboxImporting = isImportBusy && importType == "chatbox"
    val isCherryImporting = isImportBusy && importType == "cherry"

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { targetUri ->
            vm.startLocalExport(targetUri)
        }
    }

    // 创建文件选择的launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { sourceUri ->
            vm.startLocalImport(sourceUri, importType)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        stickyHeader {
            StickyHeader {
                Text(stringResource(R.string.backup_page_local_backup_export))
            }
        }

        item {
            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_backup_items)) },
                    supportingContent = {
                        MultiChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WebDavConfig.BackupItem.entries.forEachIndexed { index, item ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = WebDavConfig.BackupItem.entries.size
                                    ),
                                    onCheckedChange = { checked ->
                                        val newItems = if (checked) {
                                            selectedBackupItems + item
                                        } else {
                                            selectedBackupItems - item
                                        }
                                        vm.updateLocalBackupItems(newItems)
                                    },
                                    checked = item in selectedBackupItems
                                ) {
                                    Text(
                                        when (item) {
                                            WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                                            WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                                        }
                                    )
                                }
                            }
                        }
                    },
                )
                item(
                    onClick = if (!isExporting) {
                        {
                            val timestamp = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            createDocumentLauncher.launch("rikkahub_backup_$timestamp.zip")
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_local_backup_export)) },
                    supportingContent = {
                        Text(
                            taskDescription(
                                state = exportState,
                                idle = stringResource(R.string.backup_page_export_desc),
                                success = stringResource(R.string.backup_page_backup_success),
                                cancelled = stringResource(R.string.hook_status_cancelled),
                            )
                        )
                    },
                    leadingContent = {
                        if (isExporting) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.File01, null)
                        }
                    },
                    trailingContent = if (isExporting) {
                        {
                            IconButton(onClick = { vm.cancelTask(BackupOperation.LOCAL_EXPORT) }) {
                                Icon(
                                    HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    } else null,
                )

                item(
                    onClick = if (!isImportBusy) {
                        {
                            showImportConfirmDialog = true
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_local_backup_import)) },
                    supportingContent = {
                        Text(
                            if (isLocalImporting || (!isImportBusy && importType == "local")) {
                                taskDescription(
                                    state = importState,
                                    idle = stringResource(R.string.backup_page_import_desc),
                                    success = stringResource(R.string.backup_page_restore_success),
                                    cancelled = stringResource(R.string.hook_status_cancelled),
                                )
                            } else {
                                stringResource(R.string.backup_page_import_desc)
                            }
                        )
                    },
                    leadingContent = {
                        if (isLocalImporting) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                    trailingContent = if (isLocalImporting) {
                        {
                            IconButton(onClick = { vm.cancelTask(BackupOperation.LOCAL_IMPORT) }) {
                                Icon(
                                    HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    } else null,
                )
            }
        }

        stickyHeader {
            StickyHeader {
                Text(stringResource(R.string.backup_page_import_from_other_app))
            }
        }

        item {
            CardGroup {
                item(
                    onClick = if (!isImportBusy) {
                        {
                            importType = "chatbox"
                            openDocumentLauncher.launch(arrayOf("application/json"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_from_chatbox)) },
                    supportingContent = {
                        Text(
                            if (isChatboxImporting) {
                                taskDescription(
                                    state = importState,
                                    idle = stringResource(R.string.backup_page_import_chatbox_desc),
                                    success = stringResource(R.string.backup_page_restore_success),
                                    cancelled = stringResource(R.string.hook_status_cancelled),
                                )
                            } else {
                                stringResource(R.string.backup_page_import_chatbox_desc)
                            }
                        )
                    },
                    leadingContent = {
                        if (isChatboxImporting) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                    trailingContent = if (isChatboxImporting) {
                        {
                            IconButton(onClick = { vm.cancelTask(BackupOperation.LOCAL_IMPORT) }) {
                                Icon(
                                    HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    } else null,
                )

                item(
                    onClick = if (!isImportBusy) {
                        {
                            importType = "cherry"
                            openDocumentLauncher.launch(arrayOf("application/zip"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_from_cherry_studio)) },
                    supportingContent = {
                        Text(
                            if (isCherryImporting) {
                                taskDescription(
                                    state = importState,
                                    idle = stringResource(R.string.backup_page_import_cherry_studio_desc),
                                    success = stringResource(R.string.backup_page_restore_success),
                                    cancelled = stringResource(R.string.hook_status_cancelled),
                                )
                            } else {
                                stringResource(R.string.backup_page_import_cherry_studio_desc)
                            }
                        )
                    },
                    leadingContent = {
                        if (isCherryImporting) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                    trailingContent = if (isCherryImporting) {
                        {
                            IconButton(onClick = { vm.cancelTask(BackupOperation.LOCAL_IMPORT) }) {
                                Icon(
                                    HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    } else null,
                )
            }
        }
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text(stringResource(R.string.backup_page_local_backup_import)) },
            text = { Text(stringResource(R.string.backup_page_import_overwrite_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        importType = "local"
                        openDocumentLauncher.launch(arrayOf("application/zip"))
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun taskDescription(
    state: BackupTaskState,
    idle: String,
    success: String,
    cancelled: String,
): String = when (state) {
    is BackupTaskState.Running -> backupStageText(state.stage)
    BackupTaskState.Success -> success
    is BackupTaskState.Failed -> state.error.message ?: idle
    BackupTaskState.Cancelled -> cancelled
    BackupTaskState.Idle -> idle
}
