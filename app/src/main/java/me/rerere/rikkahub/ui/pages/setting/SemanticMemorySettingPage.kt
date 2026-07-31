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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig
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

@Composable
fun SemanticMemorySettingPage(vm: SemanticMemoryVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.semanticMemoryConfig
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    val isProcessing by vm.isProcessing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = LocalNavController.current

    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let {
            pendingExportUri = it
            val cacheFile = java.io.File(context.cacheDir, "semantic_memory_export.gz")
            vm.exportData(cacheFile.absolutePath)
        }
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
            title = { Text("替换全部语义记忆？") },
            text = {
                Text("导入将删除现有全部语义记忆后写入文件内容，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportPath?.let { vm.importData(it) }
                        showImportConfirm = false
                        pendingImportPath = null
                    },
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        pendingImportPath = null
                    },
                ) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("语义记忆设置") },
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
                    title = { Text("语义记忆") },
                ) {
                    item(
                        headlineContent = { Text("启用语义记忆") },
                        supportingContent = { Text("开启后, 每次对话前会通过向量相似度召回相关记忆并注入到系统提示词中") },
                        trailingContent = {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { vm.updateConfig(config.copy(enabled = it)) },
                            )
                        }
                    )
                    // [SemanticMemory Plugin] 记忆浏览器入口
                    item(
                        onClick = { navController.navigate(Screen.SemanticMemoryBrowser) },
                        leadingContent = { Icon(HugeIcons.Brain02, contentDescription = null) },
                        headlineContent = { Text("记忆浏览器") },
                        supportingContent = { Text("查看、编辑、删除语义记忆, 手动触发总结") },
                    )
                }
            }

            // ---- 嵌入模型 (复用提供商 ModelType.EMBEDDING) ----
            item("embeddingApiConfig") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("嵌入模型") },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "从已配置的提供商中选择 Embedding 模型（与提供商页一致）",
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
                                    vm.updateConfig(
                                        config.copy(
                                            embeddingModelId = model.id.takeUnless { model.modelId.isBlank() },
                                        ),
                                    )
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
                                Text("测试连接")
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
                    title = { Text("召回设置") },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            OutlinedTextField(
                                value = config.topK.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { vm.updateConfig(config.copy(topK = it.coerceIn(1, 100))) } },
                                label = { Text("每次召回最大记忆条数 (1-100)") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Text(
                                text = "相似度阈值: ${String.format("%.2f", config.similarityThreshold)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Slider(
                                value = config.similarityThreshold,
                                onValueChange = { vm.updateConfig(config.copy(similarityThreshold = it)) },
                                valueRange = 0f..1f,
                            )
                            OutlinedTextField(
                                value = config.maxCoreInject?.toString().orEmpty(),
                                onValueChange = { v ->
                                    if (v.isBlank()) {
                                        vm.updateConfig(config.copy(maxCoreInject = null))
                                    } else {
                                        v.toIntOrNull()?.let {
                                            vm.updateConfig(config.copy(maxCoreInject = it.takeIf { n -> n > 0 }))
                                        }
                                    }
                                },
                                label = { Text("注入核心记忆上限") },
                                supportingText = { Text("空 = 不限制；注入 system 时最多带入多少条 core") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = config.maxInjectChars?.toString().orEmpty(),
                                onValueChange = { v ->
                                    if (v.isBlank()) {
                                        vm.updateConfig(config.copy(maxInjectChars = null))
                                    } else {
                                        v.toIntOrNull()?.let {
                                            vm.updateConfig(config.copy(maxInjectChars = it.takeIf { n -> n > 0 }))
                                        }
                                    }
                                },
                                label = { Text("注入总字符预算") },
                                supportingText = { Text("空 = 不限制；记忆正文总长度上限") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = config.maxMemoryContentLen?.toString().orEmpty(),
                                onValueChange = { v ->
                                    if (v.isBlank()) {
                                        vm.updateConfig(config.copy(maxMemoryContentLen = null))
                                    } else {
                                        v.toIntOrNull()?.let {
                                            vm.updateConfig(config.copy(maxMemoryContentLen = it.takeIf { n -> n > 0 }))
                                        }
                                    }
                                },
                                label = { Text("单条记忆内容上限") },
                                supportingText = { Text("空 = 不截断；sanitize 后单条最长字符") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                singleLine = true,
                            )
                        }
                    }
                    item(
                        headlineContent = { Text("关键词回退") },
                        supportingContent = { Text("当向量 API 调用失败时, 使用关键词匹配作为回退方案") },
                        trailingContent = {
                            Switch(
                                checked = config.enableFallbackKeyword,
                                onCheckedChange = { vm.updateConfig(config.copy(enableFallbackKeyword = it)) },
                            )
                        }
                    )
                }
            }

            // ---- 总结设置 ----
            item("summarizeSettings") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("总结设置") },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // 总结状态指示器 (AC14: StateFlow-backed stats)
                            val status = stats.summarizeStatus
                            val statusText = when (status) {
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.IDLE -> "空闲"
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.RUNNING -> "正在总结..."
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.SUCCESS -> "上次总结成功"
                                me.rerere.rikkahub.data.memory.semantic.SummarizeStatus.FAILED -> "上次总结失败"
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
                                Text("总结状态", style = MaterialTheme.typography.bodyMedium)
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
                                Text("自动总结", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = config.autoSummarizeEnabled,
                                    onCheckedChange = { vm.updateConfig(config.copy(autoSummarizeEnabled = it)) },
                                )
                            }
                            OutlinedTextField(
                                value = config.summarizeInterval.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { v ->
                                        vm.updateConfig(config.copy(summarizeInterval = v.coerceAtLeast(1)))
                                    }
                                },
                                label = { Text("总结间隔 (对话轮数)") },
                                supportingText = { Text("每 N 轮用户消息自动触发一次总结") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            OutlinedTextField(
                                value = config.autoSummarizeMessageCount.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { v ->
                                        vm.updateConfig(config.copy(autoSummarizeMessageCount = v.coerceIn(4, 100)))
                                    }
                                },
                                label = { Text("自动总结取最近消息条数 (4-100)") },
                                supportingText = { Text("自动总结时从最新消息往上取多少条对话") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            OutlinedTextField(
                                value = config.maxMemoriesPerSummary.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { v ->
                                        vm.updateConfig(config.copy(maxMemoriesPerSummary = v.coerceAtLeast(1)))
                                    }
                                },
                                label = { Text("每次总结最多提取记忆数") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            OutlinedTextField(
                                value = config.maxMemoriesPerAssistant.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { v ->
                                        vm.updateConfig(config.copy(maxMemoriesPerAssistant = v.coerceAtLeast(100)))
                                    }
                                },
                                label = { Text("每个助手最大记忆数") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            // 总结用模型选择
                            Text(
                                text = "总结用模型 (不选则用默认聊天模型)",
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
                                onSelect = { vm.updateConfig(config.copy(summarizeModelId = it.id)) },
                            )
                            // 提示词编辑器
                            Text(
                                text = "总结提示词 ({{conversation}}会被替换为对话内容)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            )
                            var promptText by remember { mutableStateOf(config.summarizePrompt ?: "") }
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = {
                                    promptText = it
                                    vm.updateConfig(config.copy(summarizePrompt = it.ifBlank { null }))
                                },
                                placeholder = { Text(me.rerere.rikkahub.data.memory.semantic.MemorySummarizer.DEFAULT_PROMPT.take(100) + "...", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
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
                                        vm.updateConfig(config.copy(summarizePrompt = promptText))
                                    },
                                ) {
                                    Text("载入默认")
                                }
                                OutlinedButton(
                                    onClick = {
                                        promptText = ""
                                        vm.updateConfig(config.copy(summarizePrompt = null))
                                    },
                                ) {
                                    Text("恢复默认")
                                }
                            }
                        }
                    }
                    item(
                        headlineContent = { Text("自动清理超限记忆") },
                        supportingContent = { Text("记忆数超过上限时, 在记忆浏览器中提示清理低重要性记忆 (需手动确认)") },
                        trailingContent = {
                            Switch(
                                checked = config.autoEvictionEnabled,
                                onCheckedChange = { vm.updateConfig(config.copy(autoEvictionEnabled = it)) },
                            )
                        }
                    )
                }
            }

            // ---- 调试统计 (AC14: reactive via stats StateFlow) ----
            item("debugStats") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("调试统计") },
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            StatRow("向量 API 调用次数", stats.embeddingCallCount.toString())
                            StatRow("成功次数", stats.embeddingSuccessCount.toString())
                            StatRow("失败次数", stats.embeddingFailCount.toString())
                            stats.lastEmbeddingDim?.let { StatRow("最近向量维度", it.toString()) }
                            stats.lastEmbeddingError?.let { StatRow("最近向量错误", it) }
                            StatRow("记忆总数", stats.totalMemories.toString())
                            StatRow("召回次数", stats.recallCount.toString())
                            StatRow("最近召回记忆数", stats.lastRecallCount.toString())
                            if (stats.lastRecallAt > 0) {
                                StatRow("最近召回时间", dateFormat.format(Date(stats.lastRecallAt)))
                            }
                            StatRow("最近召回降级", if (stats.lastRecallFallback) "是 (关键词匹配)" else "否 (向量匹配)")
                            stats.lastRecallQuery?.let { StatRow("最近召回查询", it) }
                            StatRow("自动总结次数", stats.autoSummarizeCount.toString())
                            if (stats.lastAutoSummarizeAt > 0) {
                                StatRow(
                                    "最近自动总结",
                                    "${dateFormat.format(Date(stats.lastAutoSummarizeAt))} " +
                                        "(新增${stats.lastAutoSummarizeNew}/更新${stats.lastAutoSummarizeUpdated})",
                                )
                            }
                            stats.lastAutoSummarizeError?.let { StatRow("自动总结错误", it) }
                            OutlinedButton(
                                onClick = { vm.resetStats() },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text("重置统计")
                            }
                        }
                    }
                }
            }

            // ---- 数据管理 ----
            item("dataManagement") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("数据管理") },
                ) {
                    item(
                        headlineContent = { Text("导出数据") },
                        supportingContent = { Text("将所有语义记忆导出为 gzip 压缩文件") },
                        trailingContent = {
                            OutlinedButton(onClick = { exportLauncher.launch("semantic_memory_${System.currentTimeMillis()}.gz") }) {
                                Text("导出")
                            }
                        }
                    )
                    item(
                        headlineContent = { Text("导入数据") },
                        supportingContent = { Text("从压缩文件导入记忆 (会替换现有数据)") },
                        trailingContent = {
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) }) {
                                Text("导入")
                            }
                        }
                    )
                    item(
                        headlineContent = { Text("迁移旧记忆") },
                        supportingContent = { Text("将原有记忆功能的所有记忆迁移为语义记忆 (含全局和各助手)") },
                        trailingContent = {
                            OutlinedButton(onClick = { vm.migrateAllOldMemories() }) {
                                Text("迁移")
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
                            Text("处理中...", style = MaterialTheme.typography.bodyMedium)
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

    // 自动清除消息
    if (message != null) {
        androidx.compose.runtime.LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            vm.clearMessage()
        }
    }

    // 导出完成后, 将缓存文件复制到用户选择的 URI (VM 消息为 "Export OK")
    LaunchedEffect(message) {
        if (message == "Export OK") {
            pendingExportUri?.let { uri ->
                val cacheFile = java.io.File(context.cacheDir, "semantic_memory_export.gz")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        cacheFile.inputStream().use { input -> input.copyTo(output) }
                    }
                }
                cacheFile.delete()
                pendingExportUri = null
            }
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
