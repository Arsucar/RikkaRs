package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.serialization.json.JsonElement
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.repository.MemoryTableDocumentSnapshot
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.DiffView
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant

@Composable
fun AssistantMemoryTableRevisionHistoryPage(
    documentId: String,
    assistantId: String,
    conversationId: String? = null,
) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) })
    val toaster = LocalToaster.current
    var state by remember(documentId) { mutableStateOf<UiState<MemoryTableRevisionHistory>>(UiState.Loading) }
    var reloadRequest by remember(documentId) { mutableStateOf(0) }
    var selectedRevision by remember(documentId) { mutableStateOf<Int?>(null) }
    var pendingRollbackRevision by remember(documentId) { mutableStateOf<Int?>(null) }
    var rollingBack by remember(documentId) { mutableStateOf(false) }
    val restoreSuccess = androidx.compose.ui.res.stringResource(
        R.string.assistant_page_memory_table_revision_restore_success
    )
    val restoreError = androidx.compose.ui.res.stringResource(
        R.string.assistant_page_memory_table_revision_restore_error
    )

    LaunchedEffect(documentId, conversationId, reloadRequest) {
        state = UiState.Loading
        state = vm.getMemoryTableRevisionHistory(documentId, conversationId).fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it) },
        )
    }

    val history = (state as? UiState.Success)?.data
    val items = remember(history) { history?.let(::buildMemoryTableRevisionItems).orEmpty() }
    val selectedItem = selectedRevision?.let { revision -> items.firstOrNull { it.revision == revision } }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.assistant_page_memory_table_revision_history_title)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when (val currentState = state) {
            UiState.Idle,
            UiState.Loading -> RevisionLoadingState(innerPadding)

            is UiState.Error -> RevisionErrorState(
                innerPadding = innerPadding,
                onRetry = { reloadRequest++ },
            )

            is UiState.Success -> RevisionHistoryList(
                innerPadding = innerPadding,
                history = currentState.data,
                items = items,
                onSelect = { selectedRevision = it.revision },
            )
        }
    }

    selectedItem?.let { item ->
        MemoryTableRevisionDetailSheet(
            item = item,
            items = items,
            rollingBack = rollingBack,
            onDismiss = { selectedRevision = null },
            onRollback = { pendingRollbackRevision = item.revision },
        )
    }

    val pendingRollback = pendingRollbackRevision?.let { revision ->
        items.firstOrNull { it.revision == revision && !it.isCurrent }
    }
    RikkaConfirmDialog(
        show = pendingRollback != null,
        title = androidx.compose.ui.res.stringResource(R.string.assistant_page_memory_table_revision_restore_title),
        confirmText = androidx.compose.ui.res.stringResource(R.string.assistant_page_memory_table_revision_restore),
        dismissText = androidx.compose.ui.res.stringResource(R.string.cancel),
        onConfirm = {
            val revision = pendingRollback?.revision ?: return@RikkaConfirmDialog
            rollingBack = true
            pendingRollbackRevision = null
            vm.rollbackMemoryTableDocument(
                documentId = documentId,
                revision = revision,
                conversationId = conversationId,
            ) { result ->
                rollingBack = false
                result.onSuccess {
                    selectedRevision = null
                    reloadRequest++
                    toaster.show(restoreSuccess, type = ToastType.Success)
                }.onFailure {
                    toaster.show(restoreError, type = ToastType.Error)
                }
            }
        },
        onDismiss = { pendingRollbackRevision = null },
        text = {
            Text(
                androidx.compose.ui.res.stringResource(
                    R.string.assistant_page_memory_table_revision_restore_description,
                    pendingRollback?.revision ?: 0,
                )
            )
        },
    )
}

@Composable
private fun RevisionLoadingState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RevisionErrorState(
    innerPadding: PaddingValues,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.assistant_page_memory_table_revision_load_error),
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text(androidx.compose.ui.res.stringResource(R.string.context_inspector_retry))
        }
    }
}

@Composable
private fun RevisionHistoryList(
    innerPadding: PaddingValues,
    history: MemoryTableRevisionHistory,
    items: List<MemoryTableRevisionItem>,
    onSelect: (MemoryTableRevisionItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (history.snapshots.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            R.string.assistant_page_memory_table_revision_empty_title
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            R.string.assistant_page_memory_table_revision_empty_description
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(items, key = { it.revision }) { item ->
            Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                R.string.assistant_page_memory_table_revision_meta,
                                item.revision,
                                Instant.ofEpochMilli(item.timestamp).toLocalDateTime(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (item.isCurrent) {
                        Tag(type = TagType.SUCCESS) {
                            Text(
                                androidx.compose.ui.res.stringResource(
                                    R.string.assistant_page_memory_table_revision_current
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTableRevisionDetailSheet(
    item: MemoryTableRevisionItem,
    items: List<MemoryTableRevisionItem>,
    rollingBack: Boolean,
    onDismiss: () -> Unit,
    onRollback: () -> Unit,
) {
    val comparison = remember(item, items) { memoryTableRevisionComparison(item, items) }
    val diff = remember(comparison) {
        comparison?.let { (oldPayload, newPayload) ->
            generateUnifiedDiff(
                oldText = formatMemoryTableRevisionPayload(oldPayload),
                newText = formatMemoryTableRevisionPayload(newPayload),
                path = "memory-table.json",
            )
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(
                    R.string.assistant_page_memory_table_revision_meta,
                    item.revision,
                    Instant.ofEpochMilli(item.timestamp).toLocalDateTime(),
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (comparison != null) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.assistant_page_memory_table_revision_compare_current
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (diff == null) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            R.string.assistant_page_memory_table_revision_no_changes
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DiffView(diff = diff, showFileHeader = false, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.assistant_page_memory_table_revision_payload),
                style = MaterialTheme.typography.titleSmall,
            )
            SelectionContainer {
                Text(
                    text = formatMemoryTableRevisionPayload(item.payloadJson),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!item.isCurrent) {
                Button(
                    onClick = onRollback,
                    enabled = !rollingBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (rollingBack) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(2.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                R.string.assistant_page_memory_table_revision_restore
                            )
                        )
                    }
                }
            }
        }
    }
}

internal data class MemoryTableRevisionItem(
    val revision: Int,
    val payloadJson: String,
    val timestamp: Long,
    val isCurrent: Boolean,
)

internal fun buildMemoryTableRevisionItems(history: MemoryTableRevisionHistory): List<MemoryTableRevisionItem> =
    buildList {
        add(
            MemoryTableRevisionItem(
                revision = history.currentDocument.revision,
                payloadJson = history.currentDocument.payloadJson,
                timestamp = history.currentDocument.updatedAt,
                isCurrent = true,
            )
        )
        history.snapshots.forEach { snapshot ->
            add(snapshot.toRevisionItem())
        }
    }.sortedWith(
        compareByDescending<MemoryTableRevisionItem> { it.revision }
            .thenByDescending { it.isCurrent }
            .thenByDescending { it.timestamp }
    ).distinctBy { it.revision }

internal fun memoryTableRevisionComparison(
    selected: MemoryTableRevisionItem,
    items: List<MemoryTableRevisionItem>,
): Pair<String, String>? {
    val current = items.firstOrNull { it.isCurrent } ?: return null
    if (!selected.isCurrent) return selected.payloadJson to current.payloadJson
    val previous = items.firstOrNull { !it.isCurrent } ?: return null
    return previous.payloadJson to current.payloadJson
}

internal fun formatMemoryTableRevisionPayload(payloadJson: String): String = runCatching {
    val element = JsonInstant.parseToJsonElement(payloadJson)
    JsonInstantPretty.encodeToString(JsonElement.serializer(), element)
}.getOrElse { payloadJson }

private fun MemoryTableDocumentSnapshot.toRevisionItem() = MemoryTableRevisionItem(
    revision = revision,
    payloadJson = payloadJson,
    timestamp = createdAt,
    isCurrent = false,
)
