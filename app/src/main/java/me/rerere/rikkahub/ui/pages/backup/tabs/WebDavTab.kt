package me.rerere.rikkahub.ui.pages.backup.tabs

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Cancel01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupOperation
import me.rerere.rikkahub.data.sync.BackupTaskState
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onLoading
import me.rerere.rikkahub.utils.onSuccess
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.Instant

@Composable
fun WebDavTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webDavConfig = settings.webDavConfig
    val backupItemsState by vm.webDavBackupItems.collectAsStateWithLifecycle()
    val taskStates by vm.taskStates.collectAsStateWithLifecycle()
    val backupState = taskStates.getValue(BackupOperation.WEB_DAV_BACKUP)
    val restoreState = taskStates.getValue(BackupOperation.WEB_DAV_RESTORE)
    val activeWebDavRestoreHref by vm.activeWebDavRestoreHref.collectAsStateWithLifecycle()
    val isRestoreBusy = restoreState is BackupTaskState.Running
    val testState = taskStates.getValue(BackupOperation.WEB_DAV_TEST)
    val deleteState = taskStates.getValue(BackupOperation.WEB_DAV_DELETE)
    val isTesting = testState is BackupTaskState.Running
    val isDeleting = deleteState is BackupTaskState.Running
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showBackupFiles by remember { mutableStateOf(false) }
    val isBackingUp = backupState is BackupTaskState.Running

    LaunchedEffect(restoreState) {
        if (restoreState == BackupTaskState.Success &&
            vm.consumeTaskSuccess(BackupOperation.WEB_DAV_RESTORE)
        ) {
            showBackupFiles = false
            onShowRestartDialog()
        }
    }

    // #186: test connection and remote delete now run on the coordinator scope; surface their terminal
    // result as a one-shot toast even if the coroutine finished while the user was off the page.
    LaunchedEffect(testState) {
        when (vm.consumeTaskTerminal(BackupOperation.WEB_DAV_TEST)) {
            BackupTaskState.Success -> toaster.show(
                context.getString(R.string.backup_page_connection_success),
                type = ToastType.Success,
            )
            is BackupTaskState.Failed -> toaster.show(
                context.getString(
                    R.string.backup_page_connection_failed,
                    (testState as BackupTaskState.Failed).error.message ?: "",
                ),
                type = ToastType.Error,
            )
            else -> Unit
        }
    }
    LaunchedEffect(deleteState) {
        when (vm.consumeTaskTerminal(BackupOperation.WEB_DAV_DELETE)) {
            BackupTaskState.Success -> toaster.show(
                context.getString(R.string.backup_page_delete_success),
                type = ToastType.Success,
            )
            is BackupTaskState.Failed -> toaster.show(
                context.getString(
                    R.string.backup_page_delete_failed,
                    (deleteState as BackupTaskState.Failed).error.message ?: "",
                ),
                type = ToastType.Error,
            )
            else -> Unit
        }
    }

    fun updateWebDavConfig(newConfig: WebDavConfig) {
        vm.updateSettings(settings.copy(webDavConfig = newConfig))
    }

    val lastBackupText = if (settings.backupReminderConfig.lastBackupTime == 0L) {
        stringResource(R.string.backup_page_reminder_no_record)
    } else {
        stringResource(
            R.string.backup_page_reminder_last_time,
            Instant.ofEpochMilli(settings.backupReminderConfig.lastBackupTime).toLocalDateTime()
        )
    }
    val backupFileSummary = when (val state = backupItemsState) {
        is UiState.Success -> "${stringResource(R.string.backup_page_files)}: ${state.data.size}"
        UiState.Loading -> "${stringResource(R.string.backup_page_files)}: ..."
        UiState.Idle -> "${stringResource(R.string.backup_page_files)}: -"
        is UiState.Error -> "${stringResource(R.string.backup_page_files)}: -"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackupStatusCard(
                title = stringResource(R.string.backup_page_webdav_backup),
                lastBackupText = lastBackupText,
                fileSummaryText = backupFileSummary,
                taskState = backupState,
            )

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_webdav_server_address)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.url,
                            onValueChange = { updateWebDavConfig(webDavConfig.copy(url = it.trim())) },
                            placeholder = { Text("https://example.com/dav") },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_username)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.username,
                            onValueChange = {
                                updateWebDavConfig(
                                    webDavConfig.copy(
                                        username = it.trim()
                                    )
                                )
                            },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_password)) },
                    supportingContent = {
                        var passwordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.password,
                            onValueChange = { updateWebDavConfig(webDavConfig.copy(password = it.trim())) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) {
                                    HugeIcons.ViewOff
                                } else {
                                    HugeIcons.View
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = null)
                                }
                            },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_path)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.path,
                            onValueChange = { updateWebDavConfig(webDavConfig.copy(path = it.trim())) },
                            singleLine = true
                        )
                    },
                )
            }

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
                                        webDavConfig.items + item
                                    } else {
                                        webDavConfig.items - item
                                    }
                                    updateWebDavConfig(webDavConfig.copy(items = newItems))
                                },
                                checked = item in webDavConfig.items
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
            }
        }

        HorizontalDivider()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = { vm.testWebDav() },
                enabled = !isTesting,
            ) {
                Text(stringResource(R.string.backup_page_test_connection))
            }
            OutlinedButton(
                onClick = {
                    vm.loadBackupFileItems()
                    showBackupFiles = true
                }
            ) {
                Text(stringResource(R.string.backup_page_restore))
            }
            Button(
                onClick = {
                    if (isBackingUp) {
                        vm.cancelTask(BackupOperation.WEB_DAV_BACKUP)
                    } else {
                        vm.startWebDavBackup()
                    }
                },
            ) {
                if (isBackingUp) {
                    Icon(
                        HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(HugeIcons.Upload02, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isBackingUp) {
                        stringResource(R.string.cancel)
                    } else {
                        stringResource(R.string.backup_page_backup_now)
                    }
                )
            }
        }
    }

    if (showBackupFiles) {
        ModalBottomSheet(
            onDismissRequest = {
                showBackupFiles = false
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.backup_page_webdav_backup_files),
                    modifier = Modifier.fillMaxWidth()
                )
                when (val state = restoreState) {
                    BackupTaskState.Success -> Text(stringResource(R.string.backup_page_restore_success))
                    BackupTaskState.Cancelled -> Text(stringResource(R.string.hook_status_cancelled))
                    is BackupTaskState.Failed -> Text(
                        text = state.error.message ?: stringResource(R.string.backup_page_unknown_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    BackupTaskState.Idle -> Unit
                    is BackupTaskState.Running -> Text(backupStageText(state.stage))
                }
                backupItemsState.onSuccess {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(it) { item ->
                            WebDavBackupItemCard(
                                item = item,
                                isRestoring = isRestoreBusy && activeWebDavRestoreHref == item.href,
                                restoreBusy = isRestoreBusy || isDeleting,
                                onDelete = {
                                    // #186: delete runs on the coordinator scope; the terminal result is
                                    // surfaced by the WEB_DAV_DELETE LaunchedEffect above, so leaving the
                                    // page no longer cancels the request or drops the toast.
                                    vm.deleteWebDavBackupFile(item)
                                },
                                onRestore = { restoreItem ->
                                    vm.startWebDavRestore(restoreItem)
                                },
                                onCancel = {
                                    vm.cancelTask(BackupOperation.WEB_DAV_RESTORE)
                                },
                            )
                        }
                    }
                }.onError {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.backup_page_loading_failed, it.message ?: ""),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }.onLoading {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupStatusCard(
    title: String,
    lastBackupText: String,
    fileSummaryText: String,
    taskState: BackupTaskState,
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = lastBackupText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSummaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (taskState != BackupTaskState.Idle) {
                        Text(
                            text = when (taskState) {
                                is BackupTaskState.Running -> backupStageText(taskState.stage)
                                BackupTaskState.Success -> stringResource(R.string.backup_page_backup_success)
                                BackupTaskState.Cancelled -> stringResource(R.string.hook_status_cancelled)
                                is BackupTaskState.Failed -> taskState.error.message
                                    ?: stringResource(R.string.backup_page_unknown_error)
                                BackupTaskState.Idle -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (taskState is BackupTaskState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun WebDavBackupItemCard(
    item: WebDavBackupItem,
    isRestoring: Boolean = false,
    restoreBusy: Boolean = false,
    onDelete: (WebDavBackupItem) -> Unit = {},
    onRestore: (WebDavBackupItem) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.lastModified.toLocalDateTime(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = item.size.fileSizeToString(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onDelete(item)
                            },
                            enabled = !restoreBusy
                        ) {
                            Text(stringResource(R.string.backup_page_delete))
                        }
                        Button(
                            onClick = {
                                if (isRestoring) onCancel() else onRestore(item)
                            },
                            enabled = isRestoring || !restoreBusy,
                        ) {
                            if (isRestoring) {
                                Icon(
                                    HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.cancel),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (isRestoring) {
                                    stringResource(R.string.cancel)
                                } else {
                                    stringResource(R.string.backup_page_restore_now)
                                }
                            )
                        }
                    }
                }
            },
        )
    }
}
