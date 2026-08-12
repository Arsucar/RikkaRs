package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
private const val SETTING_MESSAGE_CLEAR_DELAY_MS = 3000L

@Composable
fun SemanticMemorySettingPage(vm: SemanticMemoryVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.semanticMemoryConfig
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    val isProcessing by vm.isProcessing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val exportResult by vm.exportResult.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = LocalNavController.current

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let { vm.exportData(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            pendingImportPath = it.toSafePath(context)
            showImportConfirm = true
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportPath = null
            },
            title = { Text(stringResource(R.string.semantic_memory_setting_replace_confirm_title)) },
            text = {
                Text(stringResource(R.string.semantic_memory_setting_replace_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportPath?.let { vm.importData(it) }
                        showImportConfirm = false
                        pendingImportPath = null
                    },
                ) { Text(stringResource(R.string.semantic_memory_setting_import)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        pendingImportPath = null
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.semantic_memory_setting_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 总开关 ----
            item("embeddingConfig") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_setting_section_main)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_enable)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_enable_desc)) },
                        trailingContent = {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { newValue -> vm.updateConfig { it.copy(enabled = newValue) } },
                            )
                        }
                    )
                    // [SemanticMemory Plugin] 记忆浏览器入口
                    item(
                        onClick = { navController.navigate(Screen.SemanticMemoryBrowser) },
                        leadingContent = { Icon(HugeIcons.Brain02, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_browser)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_browser_desc)) },
                    )
                }
            }

            // ---- 嵌入模型 (复用提供商 ModelType.EMBEDDING) ----
            item("embeddingApiConfig") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_setting_embedding_model)) },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.semantic_memory_setting_embedding_model_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            )
                            me.rerere.rikkahub.ui.components.ai.ModelSelector(
                                modelId = config.embeddingModelId,
                                providers = settings.providers,
                                type = me.rerere.ai.provider.ModelType.EMBEDDING,
                                allowClear = true,
                                onSelect = { model ->
                                    // ModelSelector clear calls onSelect(Model()) with blank modelId
                                    vm.updateConfig {
                                        it.copy(
                                            embeddingModelId = model.id.takeUnless { model.modelId.isBlank() },
                                        )
                                    }
                                },
                            )
                            Button(
                                onClick = { vm.testConnection(config) },
                                enabled = !isProcessing,
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                }
                                Text(stringResource(R.string.semantic_memory_setting_test_connection))
                            }
                            testResult?.let { result ->
                                Text(
                                    text = result,
                                    color = if (result.startsWith("OK")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ---- 召回设置 ----
            item("recallSettings") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_setting_recall_settings)) },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // 数字输入局部缓冲: 只更新本地文本, 失焦时解析并提交 (避免每次按键全量写)
                            var topKText by remember(config.topK) { mutableStateOf(config.topK.toString()) }
                            OutlinedTextField(
                                value = topKText,
                                onValueChange = { topKText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_recall_top_k)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            topKText.toIntOrNull()?.let { parsed ->
                                                vm.updateConfig { it.copy(topK = parsed.coerceIn(1, 100)) }
                                            }
                                        }
                                    },
                                singleLine = true,
                            )
                            Text(
                                text = stringResource(R.string.semantic_memory_setting_similarity_threshold, String.format("%.2f", config.similarityThreshold)),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            // Slider: 拖动只更新本地缓冲, 松手时才持久化 (#202)
                            var localThreshold by remember(config.similarityThreshold) { mutableStateOf(config.similarityThreshold) }
                            Slider(
                                value = localThreshold,
                                onValueChange = { localThreshold = it },
                                onValueChangeFinished = {
                                    vm.updateConfig { it.copy(similarityThreshold = localThreshold) }
                                },
                                valueRange = 0f..1f,
                            )
                            var maxCoreInjectText by remember(config.maxCoreInject) { mutableStateOf(config.maxCoreInject?.toString().orEmpty()) }
                            OutlinedTextField(
                                value = maxCoreInjectText,
                                onValueChange = { maxCoreInjectText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_max_core_inject)) },
                                supportingText = { Text(stringResource(R.string.semantic_memory_setting_max_core_inject_desc)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            if (maxCoreInjectText.isBlank()) {
                                                vm.updateConfig { it.copy(maxCoreInject = null) }
                                            } else {
                                                maxCoreInjectText.toIntOrNull()?.let { parsed ->
                                                    vm.updateConfig { it.copy(maxCoreInject = parsed.takeIf { n -> n > 0 }) }
                                                }
                                            }
                                        }
                                    },
                                singleLine = true,
                            )
                            var maxInjectCharsText by remember(config.maxInjectChars) { mutableStateOf(config.maxInjectChars?.toString().orEmpty()) }
                            OutlinedTextField(
                                value = maxInjectCharsText,
                                onValueChange = { maxInjectCharsText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_max_inject_chars)) },
                                supportingText = { Text(stringResource(R.string.semantic_memory_setting_max_inject_chars_desc)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            if (maxInjectCharsText.isBlank()) {
                                                vm.updateConfig { it.copy(maxInjectChars = null) }
                                            } else {
                                                maxInjectCharsText.toIntOrNull()?.let { parsed ->
                                                    vm.updateConfig { it.copy(maxInjectChars = parsed.takeIf { n -> n > 0 }) }
                                                }
                                            }
                                        }
                                    },
                                singleLine = true,
                            )
                            var maxMemoryContentLenText by remember(config.maxMemoryContentLen) { mutableStateOf(config.maxMemoryContentLen?.toString().orEmpty()) }
                            OutlinedTextField(
                                value = maxMemoryContentLenText,
                                onValueChange = { maxMemoryContentLenText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_max_memory_content_len)) },
                                supportingText = { Text(stringResource(R.string.semantic_memory_setting_max_memory_content_len_desc)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            if (maxMemoryContentLenText.isBlank()) {
                                                vm.updateConfig { it.copy(maxMemoryContentLen = null) }
                                            } else {
                                                maxMemoryContentLenText.toIntOrNull()?.let { parsed ->
                                                    vm.updateConfig { it.copy(maxMemoryContentLen = parsed.takeIf { n -> n > 0 }) }
                                                }
                                            }
                                        }
                                    },
                                singleLine = true,
                            )
                        }
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_keyword_fallback)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_keyword_fallback_desc)) },
                        trailingContent = {
                            Switch(
                                checked = config.enableFallbackKeyword,
                                onCheckedChange = { newValue -> vm.updateConfig { it.copy(enableFallbackKeyword = newValue) } },
                            )
                        }
                    )
                }
            }

            // ---- 总结设置 ----
            item("summarizeSettings") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_setting_summarize_settings)) },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // 总结状态指示器 (AC14: StateFlow-backed stats)
                            val status = stats.summarizeStatus
                            val statusText = when (status) {
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.IDLE -> stringResource(R.string.semantic_memory_setting_summarize_status_idle)
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.RUNNING -> stringResource(R.string.semantic_memory_setting_summarize_status_running)
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.SUCCESS -> stringResource(R.string.semantic_memory_setting_summarize_status_success)
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.FAILED -> stringResource(R.string.semantic_memory_setting_summarize_status_failed)
                            }
                            val statusColor = when (status) {
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.IDLE -> MaterialTheme.colorScheme.outline
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.FAILED -> MaterialTheme.colorScheme.error
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.semantic_memory_setting_summarize_status), style = MaterialTheme.typography.bodyMedium)
                                Text(statusText, color = statusColor, style = MaterialTheme.typography.bodyMedium)
                            }
                            // 自动总结开关
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.semantic_memory_setting_auto_summarize), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = config.autoSummarizeEnabled,
                                    onCheckedChange = { newValue -> vm.updateConfig { it.copy(autoSummarizeEnabled = newValue) } },
                                )
                            }
                            var summarizeIntervalText by remember(config.summarizeInterval) { mutableStateOf(config.summarizeInterval.toString()) }
                            OutlinedTextField(
                                value = summarizeIntervalText,
                                onValueChange = { summarizeIntervalText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_summarize_interval)) },
                                supportingText = { Text(stringResource(R.string.semantic_memory_setting_summarize_interval_desc)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            summarizeIntervalText.toIntOrNull()?.let { parsed ->
                                                vm.updateConfig { it.copy(summarizeInterval = parsed.coerceAtLeast(1)) }
                                            }
                                        }
                                    },
                            )
                            var autoSummarizeMessageCountText by remember(config.autoSummarizeMessageCount) { mutableStateOf(config.autoSummarizeMessageCount.toString()) }
                            OutlinedTextField(
                                value = autoSummarizeMessageCountText,
                                onValueChange = { autoSummarizeMessageCountText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_auto_summarize_message_count)) },
                                supportingText = { Text(stringResource(R.string.semantic_memory_setting_auto_summarize_message_count_desc)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            autoSummarizeMessageCountText.toIntOrNull()?.let { parsed ->
                                                vm.updateConfig { it.copy(autoSummarizeMessageCount = parsed.coerceIn(4, 100)) }
                                            }
                                        }
                                    },
                            )
                            var maxMemoriesPerSummaryText by remember(config.maxMemoriesPerSummary) { mutableStateOf(config.maxMemoriesPerSummary.toString()) }
                            OutlinedTextField(
                                value = maxMemoriesPerSummaryText,
                                onValueChange = { maxMemoriesPerSummaryText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_max_memories_per_summary)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            maxMemoriesPerSummaryText.toIntOrNull()?.let { parsed ->
                                                vm.updateConfig { it.copy(maxMemoriesPerSummary = parsed.coerceAtLeast(1)) }
                                            }
                                        }
                                    },
                            )
                            var maxMemoriesPerAssistantText by remember(config.maxMemoriesPerAssistant) { mutableStateOf(config.maxMemoriesPerAssistant.toString()) }
                            OutlinedTextField(
                                value = maxMemoriesPerAssistantText,
                                onValueChange = { maxMemoriesPerAssistantText = it },
                                label = { Text(stringResource(R.string.semantic_memory_setting_max_memories_per_assistant)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            maxMemoriesPerAssistantText.toIntOrNull()?.let { parsed ->
                                                vm.updateConfig { it.copy(maxMemoriesPerAssistant = parsed.coerceAtLeast(100)) }
                                            }
                                        }
                                    },
                            )
                            // 总结用模型选择
                            Text(
                                text = stringResource(R.string.semantic_memory_setting_summarize_model_hint),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            )
                            me.rerere.rikkahub.ui.components.ai.ModelSelector(
                                modelId = config.summarizeModelId,
                                providers = settings.providers,
                                type = me.rerere.ai.provider.ModelType.CHAT,
                                allowClear = true,
                                onSelect = { model -> vm.updateConfig { it.copy(summarizeModelId = model.id) } },
                            )
                            // 提示词编辑器
                            Text(
                                text = stringResource(R.string.semantic_memory_setting_summarize_prompt_hint),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            )
                            var promptText by remember(config.summarizePrompt) { mutableStateOf(config.summarizePrompt ?: "") }
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = { promptText = it },
                                placeholder = { Text(me.rerere.rikkahub.data.memory.semantic.MemorySummarizer.DEFAULT_PROMPT.take(100) + "...", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            vm.updateConfig { it.copy(summarizePrompt = promptText.ifBlank { null }) }
                                        }
                                    },
                                minLines = 3,
                                maxLines = 8,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        promptText = me.rerere.rikkahub.data.memory.semantic.MemorySummarizer.DEFAULT_PROMPT
                                        vm.updateConfig { it.copy(summarizePrompt = promptText) }
                                    },
                                ) {
                                    Text(stringResource(R.string.semantic_memory_setting_load_default))
                                }
                                OutlinedButton(
                                    onClick = {
                                        promptText = ""
                                        vm.updateConfig { it.copy(summarizePrompt = null) }
                                    },
                                ) {
                                    Text(stringResource(R.string.semantic_memory_setting_restore_default))
                                }
                            }
                        }
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_auto_eviction)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_auto_eviction_desc)) },
                        trailingContent = {
                            Switch(
                                checked = config.autoEvictionEnabled,
                                onCheckedChange = { newValue -> vm.updateConfig { it.copy(autoEvictionEnabled = newValue) } },
                            )
                        }
                    )
                }
            }

            // ---- 调试统计 (AC14: reactive via stats StateFlow) ----
            item("debugStats") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_setting_debug_stats)) },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_embedding_calls), stats.embeddingCallCount.toString())
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_embedding_success), stats.embeddingSuccessCount.toString())
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_embedding_fail), stats.embeddingFailCount.toString())
                            stats.lastEmbeddingDim?.let { StatRow(stringResource(R.string.semantic_memory_setting_stat_embedding_dim), it.toString()) }
                            stats.lastEmbeddingError?.let { StatRow(stringResource(R.string.semantic_memory_setting_stat_embedding_error), it) }
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_total_memories), stats.totalMemories.toString())
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_recall_count), stats.recallCount.toString())
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_last_recall_count), stats.lastRecallCount.toString())
                            if (stats.lastRecallAt > 0) {
                                StatRow(stringResource(R.string.semantic_memory_setting_stat_last_recall_at), dateFormat.format(Date(stats.lastRecallAt)))
                            }
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_last_recall_fallback), if (stats.lastRecallFallback) stringResource(R.string.semantic_memory_setting_stat_last_recall_fallback_yes) else stringResource(R.string.semantic_memory_setting_stat_last_recall_fallback_no))
                            stats.lastRecallQuery?.let { StatRow(stringResource(R.string.semantic_memory_setting_stat_last_recall_query), it) }
                            StatRow(stringResource(R.string.semantic_memory_setting_stat_auto_summarize_count), stats.autoSummarizeCount.toString())
                            if (stats.lastAutoSummarizeAt > 0) {
                                StatRow(
                                    stringResource(R.string.semantic_memory_setting_stat_last_auto_summarize),
                                    stringResource(
                                        R.string.semantic_memory_setting_stat_last_auto_summarize_value,
                                        dateFormat.format(Date(stats.lastAutoSummarizeAt)),
                                        stats.lastAutoSummarizeNew,
                                        stats.lastAutoSummarizeUpdated,
                                    ),
                                )
                            }
                            stats.lastAutoSummarizeError?.let { StatRow(stringResource(R.string.semantic_memory_setting_stat_auto_summarize_error), it) }
                            OutlinedButton(
                                onClick = { vm.resetStats() },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.semantic_memory_setting_reset_stats))
                            }
                        }
                    }
                }
            }

            // ---- 数据管理 ----
            item("dataManagement") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.semantic_memory_setting_data_management)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_export_data)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_export_data_desc)) },
                        trailingContent = {
                            OutlinedButton(onClick = { exportLauncher.launch("semantic_memory_${System.currentTimeMillis()}.gz") }) {
                                Text(stringResource(R.string.semantic_memory_setting_export))
                            }
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_import_data)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_import_data_desc)) },
                        trailingContent = {
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) }) {
                                Text(stringResource(R.string.semantic_memory_setting_import))
                            }
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.semantic_memory_setting_migrate_old_memories)) },
                        supportingContent = { Text(stringResource(R.string.semantic_memory_setting_migrate_old_memories_desc)) },
                        trailingContent = {
                            OutlinedButton(onClick = { vm.migrateAllOldMemories() }) {
                                Text(stringResource(R.string.semantic_memory_setting_migrate))
                            }
                        }
                    )
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
                            Text(stringResource(R.string.semantic_memory_setting_processing), style = MaterialTheme.typography.bodyMedium)
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

            // 导出结果提示 (#206): 由 VM 的 exportResult 事件驱动, 不复用 message/魔数
            exportNotice?.let { notice ->
                item("exportNotice") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = notice,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (exportResult?.success == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }

    // 自动清除消息 (仅作用于提示文案, 与导出任务解耦)
    if (message != null) {
        androidx.compose.runtime.LaunchedEffect(message) {
            kotlinx.coroutines.delay(SETTING_MESSAGE_CLEAR_DELAY_MS)
            vm.clearMessage()
        }
    }

    // 导出完成事件 (#206): 只消费 VM 的 exportResult 并展示, 不再依赖 "Export OK" 魔数
    LaunchedEffect(exportResult) {
        exportResult?.let { res ->
            exportNotice = res.message
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

private fun Uri.toSafePath(context: Context): String {
    val cursor = context.contentResolver.query(this, null, null, null, null)
    return cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        val name = if (nameIndex >= 0) it.getString(nameIndex) else "temp_${System.currentTimeMillis()}"
        val file = java.io.File(context.cacheDir, name)
        context.contentResolver.openInputStream(this)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } ?: ""
}
