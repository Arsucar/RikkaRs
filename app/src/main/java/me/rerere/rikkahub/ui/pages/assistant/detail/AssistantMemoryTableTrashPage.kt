package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DeletePutBack
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant

internal sealed interface MemoryTableTrashUiState {
    data object Loading : MemoryTableTrashUiState
    data object Empty : MemoryTableTrashUiState
    data class Success(val documents: List<MemoryTableDocument>) : MemoryTableTrashUiState
    data class Error(val error: Throwable) : MemoryTableTrashUiState
}

internal data class MemoryTableTrashItem(
    val document: MemoryTableDocument,
    val displayName: String,
)

internal fun buildMemoryTableTrashItems(
    documents: List<MemoryTableDocument>,
    templates: List<MemoryTableTemplate>,
): List<MemoryTableTrashItem> {
    val templatesById = templates.associateBy { it.id }
    return documents
        .map { document ->
            MemoryTableTrashItem(
                document = document,
                displayName = templatesById[document.templateId]
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: document.id,
            )
        }
        .sortedWith(
            compareByDescending<MemoryTableTrashItem> { it.document.deletedAt ?: Long.MIN_VALUE }
                .thenBy { it.document.id }
        )
}

@Composable
fun AssistantMemoryTableTrashPage(assistantId: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) })
    val state by vm.memoryTableTrashUiState.collectAsStateWithLifecycle()
    val templates by vm.memoryTableTemplates.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val restoreSuccess = stringResource(R.string.assistant_page_memory_table_trash_restore_success)
    val restoreError = stringResource(R.string.assistant_page_memory_table_trash_restore_error)
    val purgeSuccess = stringResource(R.string.assistant_page_memory_table_trash_purge_success)
    val purgeError = stringResource(R.string.assistant_page_memory_table_trash_purge_error)
    var pendingRestore by remember { mutableStateOf<MemoryTableTrashItem?>(null) }
    var pendingPurge by remember { mutableStateOf<MemoryTableTrashItem?>(null) }
    var operationInProgress by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_memory_table_trash)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when (val current = state) {
            MemoryTableTrashUiState.Loading -> TrashLoadingState(innerPadding)
            MemoryTableTrashUiState.Empty -> TrashEmptyState(innerPadding)
            is MemoryTableTrashUiState.Error -> TrashErrorState(innerPadding, vm::reloadMemoryTableTrash)
            is MemoryTableTrashUiState.Success -> {
                val items = remember(current.documents, templates) {
                    buildMemoryTableTrashItems(current.documents, templates)
                }
                TrashList(
                    innerPadding = innerPadding,
                    items = items,
                    enabled = !operationInProgress,
                    onRestore = { pendingRestore = it },
                    onPurge = { pendingPurge = it },
                )
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingRestore != null,
        title = stringResource(R.string.assistant_page_memory_table_trash_restore_title),
        confirmText = stringResource(R.string.assistant_page_memory_table_trash_restore),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            val item = pendingRestore ?: return@RikkaConfirmDialog
            pendingRestore = null
            operationInProgress = true
            vm.restoreMemoryTableDocument(item.document.id) { result ->
                operationInProgress = false
                toaster.show(if (result.isSuccess) restoreSuccess else restoreError,
                    type = if (result.isSuccess) ToastType.Success else ToastType.Error)
            }
        },
        onDismiss = { if (!operationInProgress) pendingRestore = null },
        text = {
            Text(stringResource(R.string.assistant_page_memory_table_trash_restore_description,
                pendingRestore?.displayName.orEmpty()))
        },
    )

    RikkaConfirmDialog(
        show = pendingPurge != null,
        title = stringResource(R.string.assistant_page_memory_table_trash_purge_title),
        confirmText = stringResource(R.string.assistant_page_memory_table_trash_purge),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            val item = pendingPurge ?: return@RikkaConfirmDialog
            pendingPurge = null
            operationInProgress = true
            vm.purgeMemoryTableDocument(item.document.id) { result ->
                operationInProgress = false
                toaster.show(if (result.getOrDefault(false)) purgeSuccess else purgeError,
                    type = if (result.getOrDefault(false)) ToastType.Success else ToastType.Error)
            }
        },
        onDismiss = { if (!operationInProgress) pendingPurge = null },
        text = {
            Text(stringResource(R.string.assistant_page_memory_table_trash_purge_description,
                pendingPurge?.displayName.orEmpty()))
        },
    )
}

@Composable
private fun TrashLoadingState(innerPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TrashEmptyState(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.assistant_page_memory_table_trash_empty_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.assistant_page_memory_table_trash_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrashErrorState(innerPadding: PaddingValues, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.assistant_page_memory_table_trash_load_error),
            color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.context_inspector_retry)) }
    }
}

@Composable
private fun TrashList(
    innerPadding: PaddingValues,
    items: List<MemoryTableTrashItem>,
    enabled: Boolean,
    onRestore: (MemoryTableTrashItem) -> Unit,
    onPurge: (MemoryTableTrashItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = innerPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.document.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(scopeLabel(item.document.scopeType), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(
                            R.string.assistant_page_memory_table_trash_deleted_meta,
                            Instant.ofEpochMilli(item.document.deletedAt ?: 0).toLocalDateTime(),
                            deletedByLabel(item.document.deletedBy),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(enabled = enabled, onClick = { onRestore(item) }) {
                            Icon(HugeIcons.DeletePutBack,
                                contentDescription = stringResource(R.string.assistant_page_memory_table_trash_restore))
                        }
                        IconButton(enabled = enabled, onClick = { onPurge(item) }) {
                            Icon(HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.assistant_page_memory_table_trash_purge),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun scopeLabel(scopeType: MemoryTableScopeType): String = stringResource(
    when (scopeType) {
        MemoryTableScopeType.GLOBAL -> R.string.assistant_page_memory_scope_global
        MemoryTableScopeType.ASSISTANT -> R.string.assistant_page_memory_scope_assistant
        MemoryTableScopeType.CONVERSATION -> R.string.assistant_page_memory_table_scope_conversation
    }
)

@Composable
private fun deletedByLabel(deletedBy: String?): String = stringResource(
    when (deletedBy) {
        "user_ui" -> R.string.assistant_page_memory_table_trash_actor_user
        "memory_table_tool" -> R.string.assistant_page_memory_table_trash_actor_tool
        "assistant_cascade" -> R.string.assistant_page_memory_table_trash_actor_assistant_cascade
        else -> R.string.assistant_page_memory_table_trash_actor_unknown
    }
)
