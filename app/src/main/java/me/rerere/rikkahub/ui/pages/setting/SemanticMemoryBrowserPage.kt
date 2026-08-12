package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
private const val MEMORY_MESSAGE_CLEAR_DELAY_MS = 5000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemanticMemoryBrowserPage(vm: SemanticMemoryVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistants = settings.assistants
    val selectedAssistantId by vm.selectedAssistantId.collectAsStateWithLifecycle()
    val memories by vm.memoriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isProcessing by vm.isProcessing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val evictionCandidates by vm.evictionCandidates.collectAsStateWithLifecycle()

    var editingMemory by remember { mutableStateOf<EpisodicMemoryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EpisodicMemoryEntity?>(null) }
    var messageCount by remember { mutableStateOf(50) }

    LaunchedEffect(selectedAssistantId, memories.size) {
        selectedAssistantId?.let { vm.checkEvictionCandidates(it) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.semantic_memory_browser_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- 助手选择器 ----
            item("assistantSelector") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_browser_select_assistant, assistants.size)) },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // 可滚动的助手 chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                assistants.forEach { assistant ->
                                    FilterChip(
                                        selected = selectedAssistantId == assistant.id.toString(),
                                        onClick = { vm.selectAssistant(assistant.id.toString()) },
                                        label = { Text(assistant.name) },
                                    )
                                }
                            }

                            // 手动总结: 消息条数输入 + 触发按钮
                            OutlinedTextField(
                                value = messageCount.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { messageCount = it.coerceIn(1, 500) } },
                                label = { Text(stringResource(R.string.semantic_memory_browser_summarize_recent_count)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                singleLine = true,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        selectedAssistantId?.let {
                                            vm.triggerSummarize(it, messageCount)
                                        }
                                    },
                                    enabled = selectedAssistantId != null && !isProcessing,
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }
                                    Text(stringResource(R.string.semantic_memory_browser_trigger_summarize))
                                }
                                OutlinedButton(onClick = { showAddDialog = true }) {
                                    Text(stringResource(R.string.semantic_memory_browser_add_memory))
                                }
                            }

                            // 一键召回测试
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        selectedAssistantId?.let { vm.testRecall(it) }
                                    },
                                    enabled = selectedAssistantId != null && !isProcessing,
                                ) {
                                    Text(stringResource(R.string.semantic_memory_browser_recall_test))
                                }
                            }
                        }
                    }
                }
            }

            // ---- 记忆列表 ----
            if (selectedAssistantId != null) {
                item("memoryHeader") {
                    Text(
                        text = stringResource(R.string.semantic_memory_browser_memory_list, memories.size),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                    )
                }

                if (memories.isEmpty()) {
                    item("emptyState") {
                        Text(
                            text = stringResource(R.string.semantic_memory_browser_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    items(
                        count = memories.size,
                        key = { memories[it].id },
                    ) { index ->
                        val memory = memories[index]
                        MemoryCard(
                            memory = memory,
                            onEdit = { editingMemory = memory },
                            onDelete = { pendingDelete = memory },
                        )
                    }
                }
            }

            // ---- 处理中指示 ----
            if (isProcessing) {
                item("processing") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.semantic_memory_browser_processing), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ---- 消息提示 ----
            message?.let { msg ->
                item("message") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    // ---- 弹窗 ----

    if (evictionCandidates.isNotEmpty()) {
        EvictionConfirmDialog(
            candidates = evictionCandidates,
            onConfirm = { candidates ->
                selectedAssistantId?.let { aid ->
                    vm.confirmEviction(candidates.map { it.id }, aid)
                }
            },
            onDismiss = { vm.dismissEviction() },
        )
    }

    pendingDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.semantic_memory_browser_delete_title)) },
            text = { Text(stringResource(R.string.semantic_memory_browser_delete_message, memory.content.take(100))) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMemory(memory.id, memory.assistantId)
                    pendingDelete = null
                }) { Text(stringResource(R.string.semantic_memory_browser_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    editingMemory?.let { memory ->
        MemoryEditDialog(
            memory = memory,
            onConfirm = { updated ->
                val contentChanged = updated.content != memory.content || updated.summary != memory.summary
                vm.updateMemory(updated, contentChanged = contentChanged)
                editingMemory = null
            },
            onDismiss = { editingMemory = null },
        )
    }

    if (showAddDialog && selectedAssistantId != null) {
        MemoryEditDialog(
            memory = EpisodicMemoryEntity(
                assistantId = selectedAssistantId!!,
                content = "",
                summary = "",
                importance = 3,
                isCore = false,
            ),
            isNew = true,
            onConfirm = { newMemory ->
                vm.addMemory(
                    assistantId = newMemory.assistantId,
                    content = newMemory.content,
                    summary = newMemory.summary,
                    importance = newMemory.importance,
                    isCore = newMemory.isCore,
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (message != null) {
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(MEMORY_MESSAGE_CLEAR_DELAY_MS)
            vm.clearMessage()
        }
    }
}

@Composable
private fun MemoryCard(
    memory: EpisodicMemoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "\u2605".repeat(memory.importance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (memory.importance >= 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    if (memory.isCore) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.semantic_memory_browser_core_badge), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(HugeIcons.PencilEdit01, contentDescription = stringResource(R.string.semantic_memory_browser_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.semantic_memory_browser_delete))
                    }
                }
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (memory.summary.isNotBlank()) {
                Text(
                    text = memory.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.semantic_memory_browser_created_at, dateFormat.format(Date(memory.createdAt))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (memory.recallCount > 0) {
                    Text(
                        text = stringResource(R.string.semantic_memory_browser_recall_count, memory.recallCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryEditDialog(
    memory: EpisodicMemoryEntity,
    isNew: Boolean = false,
    onConfirm: (EpisodicMemoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf(memory.content) }
    var summary by remember { mutableStateOf(memory.summary) }
    var importance by remember { mutableStateOf(memory.importance.toFloat()) }
    var isCore by remember { mutableStateOf(memory.isCore) }

    val configuration = LocalConfiguration.current
    val formMaxHeight = (configuration.screenHeightDp * 0.6f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.semantic_memory_browser_add_title) else stringResource(R.string.semantic_memory_browser_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = formMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.semantic_memory_browser_content_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.semantic_memory_browser_summary_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.semantic_memory_browser_importance, importance.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 2f..5f,
                    steps = 2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.semantic_memory_browser_core_memory))
                    Switch(
                        checked = isCore,
                        onCheckedChange = { isCore = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    memory.copy(
                        content = content,
                        summary = summary,
                        importance = importance.toInt(),
                        isCore = isCore,
                    )
                )
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EvictionConfirmDialog(
    candidates: List<EpisodicMemoryEntity>,
    onConfirm: (List<EpisodicMemoryEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val listMaxHeight = (configuration.screenHeightDp * 0.5f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.semantic_memory_browser_eviction_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.semantic_memory_browser_eviction_hint, candidates.size))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    candidates.forEach { memory ->
                        Text(
                            text = "- ${"\u2605".repeat(memory.importance)} ${memory.content.take(60)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(candidates) }) { Text(stringResource(R.string.semantic_memory_browser_eviction_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.semantic_memory_browser_eviction_keep)) }
        },
    )
}
