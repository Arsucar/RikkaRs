package me.rerere.rikkahub.ui.pages.backup.tabs

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FileImport
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val isExporting = exportState == BackupTaskState.Running
    val isRestoring = importState == BackupTaskState.Running

    LaunchedEffect(importState) {
        if (importState == BackupTaskState.Success) {
            onShowRestartDialog()
        }
    }

    // 导入类型：local 为本地备份，chatbox 为 Chatbox 导入，cherry 为 Cherry Studio 导入
    var importType by remember { mutableStateOf("local") }

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
                                running = stringResource(R.string.backup_page_exporting),
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
                )

                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = "local"
                            openDocumentLauncher.launch(arrayOf("application/zip"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_local_backup_import)) },
                    supportingContent = {
                        Text(
                            taskDescription(
                                state = importState,
                                running = stringResource(R.string.backup_page_importing),
                                idle = stringResource(R.string.backup_page_import_desc),
                                success = stringResource(R.string.backup_page_restore_success),
                                cancelled = stringResource(R.string.hook_status_cancelled),
                            )
                        )
                    },
                    leadingContent = {
                        if (isRestoring) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
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
                    onClick = if (!isRestoring) {
                        {
                            importType = "chatbox"
                            openDocumentLauncher.launch(arrayOf("application/json"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_from_chatbox)) },
                    supportingContent = { Text(stringResource(R.string.backup_page_import_chatbox_desc)) },
                    leadingContent = {
                        if (isRestoring && importType == "chatbox") {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                )

                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = "cherry"
                            openDocumentLauncher.launch(arrayOf("application/zip"))
                        }
                    } else null,
                    headlineContent = { Text(stringResource(R.string.backup_page_import_from_cherry_studio)) },
                    supportingContent = { Text(stringResource(R.string.backup_page_import_cherry_studio_desc)) },
                    leadingContent = {
                        if (isRestoring && importType == "cherry") {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                )
            }
        }
    }
}

private fun taskDescription(
    state: BackupTaskState,
    running: String,
    idle: String,
    success: String,
    cancelled: String,
): String = when (state) {
    BackupTaskState.Running -> running
    BackupTaskState.Success -> success
    is BackupTaskState.Failed -> state.error.message ?: idle
    BackupTaskState.Cancelled -> cancelled
    BackupTaskState.Idle -> idle
}
