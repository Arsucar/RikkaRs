package me.rerere.rikkahub.ui.pages.log

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.CursorPointer01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.Tick01
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.common.android.redacted
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.compose.koinInject
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.components.ui.ListSelectableItem
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstantPretty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogPage() {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val appScope = koinInject<AppScope>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf(Logging.getRecentLogs()) }
    val requestLoggingEnabled = settings.requestLoggingEnabled
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val exportSuccessText = stringResource(R.string.log_page_export_success)
    val exportFailedText = stringResource(R.string.log_page_export_failed)
    val exportLogsContentDescription = stringResource(R.string.log_page_export_logs)

    var selecting by rememberSaveable { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Uuid>() }
    var pendingExportIds by remember { mutableStateOf<Set<Uuid>?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            pendingExportIds = null
            return@rememberLauncherForActivityResult
        }
        val exportIds = pendingExportIds
        pendingExportIds = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val entries = when {
                        exportIds == null -> logs
                        exportIds.isEmpty() -> emptyList()
                        else -> logs.filter { it.id in exportIds }
                    }
                    val logsJson = JsonInstantPretty.encodeToString(
                        ListSerializer(LogEntry.serializer()),
                        entries.map { it.redacted() },
                    )
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(logsJson.toByteArray())
                    } ?: error("openOutputStream failed")
                }
            }
            val message = if (result.isSuccess) exportSuccessText else exportFailedText
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            selecting = false
            selectedIds.clear()
        }
    }

    val launchExport: (Set<Uuid>?) -> Unit = { ids ->
        pendingExportIds = ids
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        createDocumentLauncher.launch("rikkahub-logs-$timestamp.json")
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Logs") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { launchExport(null) }) {
                        Icon(HugeIcons.Download01, exportLogsContentDescription)
                    }
                    IconButton(
                        onClick = {
                            Logging.clear()
                            logs = Logging.getRecentLogs()
                            selectedIds.clear()
                            selecting = false
                        },
                    ) {
                        Icon(HugeIcons.Delete01, null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        UnifiedLogList(
            logs = logs,
            requestLoggingEnabled = requestLoggingEnabled,
            onRequestLoggingChange = { enabled ->
                Logging.setRequestLoggingEnabled(enabled)
                appScope.launch {
                    settingsStore.update { current -> current.copy(requestLoggingEnabled = enabled) }
                }
            },
            selecting = selecting,
            selectedIds = selectedIds,
            onSelectionToggle = { id ->
                if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
            },
            onSelectAllToggle = {
                val allIds = logs.map { it.id }.toSet()
                if (selectedIds.toSet() == allIds && logs.isNotEmpty()) {
                    selectedIds.clear()
                } else {
                    selectedIds.clear()
                    selectedIds.addAll(logs.map { it.id })
                }
            },
            onConfirmSelection = {
                if (selectedIds.isEmpty()) return@UnifiedLogList
                val ids = selectedIds.toSet()
                selecting = false
                launchExport(ids)
            },
            onCancelSelection = {
                selecting = false
                selectedIds.clear()
            },
            onEnterSelectionWith = { id ->
                selecting = true
                if (id !in selectedIds) selectedIds.add(id)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedLogList(
    logs: List<LogEntry>,
    requestLoggingEnabled: Boolean,
    onRequestLoggingChange: (Boolean) -> Unit,
    selecting: Boolean,
    selectedIds: List<Uuid>,
    onSelectionToggle: (Uuid) -> Unit,
    onSelectAllToggle: () -> Unit,
    onConfirmSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onEnterSelectionWith: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedLog by remember { mutableStateOf<LogEntry.RequestLog?>(null) }
    var sheetInner by remember { mutableStateOf<RequestLogSheetInner>(RequestLogSheetInner.Detail) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.copied)

    val sortedLogs = remember(logs) { logs.sortedByDescending { it.timestamp } }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                RequestLoggingSwitchCard(
                    enabled = requestLoggingEnabled,
                    onEnabledChange = onRequestLoggingChange,
                )
            }

            items(sortedLogs, key = { it.id }, contentType = { it.javaClass.simpleName }) { log ->
                ListSelectableItem(
                    key = log.id,
                    selectedKeys = selectedIds,
                    onSelectChange = { key -> onSelectionToggle(key as Uuid) },
                    enabled = selecting,
                ) {
                    when (log) {
                        is LogEntry.RequestLog -> RequestLogCard(
                            log = log,
                            onClick = {
                                if (selecting) {
                                    onSelectionToggle(log.id)
                                } else {
                                    selectedLog = log
                                    sheetInner = RequestLogSheetInner.Detail
                                    scope.launch { sheetState.show() }
                                }
                            },
                            onLongClick = { onEnterSelectionWith(log.id) },
                        )
                        is LogEntry.TextLog -> TextLogCard(
                            log = log,
                            onClick = {
                                if (selecting) {
                                    onSelectionToggle(log.id)
                                }
                            },
                            onLongClick = { onEnterSelectionWith(log.id) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-48).dp),
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
        ) {
            HorizontalFloatingToolbar(expanded = true) {
                Tooltip(tooltip = { Text(stringResource(R.string.chat_list_clear_selection)) }) {
                    IconButton(onClick = onCancelSelection) {
                        Icon(HugeIcons.Cancel01, contentDescription = null)
                    }
                }
                Tooltip(tooltip = { Text(stringResource(R.string.common_select_all)) }) {
                    IconButton(onClick = onSelectAllToggle) {
                        Icon(HugeIcons.CursorPointer01, contentDescription = null)
                    }
                }
                Tooltip(tooltip = { Text(stringResource(R.string.chat_list_confirm)) }) {
                    FilledIconButton(
                        onClick = onConfirmSelection,
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(HugeIcons.Tick01, contentDescription = null)
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        val detailListState = rememberLazyListState()
        val sheetSnackbarHostState = remember { SnackbarHostState() }
        ModalBottomSheet(
            onDismissRequest = {
                selectedLog = null
                sheetInner = RequestLogSheetInner.Detail
            },
            sheetState = sheetState,
            sheetGesturesEnabled = sheetInner is RequestLogSheetInner.Detail,
        ) {
            val copyText = (sheetInner as? RequestLogSheetInner.Copy)?.text
            BackHandler(enabled = copyText != null) {
                sheetInner = RequestLogSheetInner.Detail
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                when (val inner = sheetInner) {
                    RequestLogSheetInner.Detail -> RequestLogDetail(
                        log = log,
                        listState = detailListState,
                        onStringClick = { value ->
                            sheetInner = RequestLogSheetInner.Copy(value)
                        },
                    )
                    is RequestLogSheetInner.Copy -> LogJsonStringCopyPanel(
                        text = inner.text,
                        onBack = { sheetInner = RequestLogSheetInner.Detail },
                        onCopyAll = {
                            clipboardManager.setText(AnnotatedString(inner.text))
                            scope.launch {
                                sheetSnackbarHostState.showSnackbar(copiedText)
                            }
                            sheetInner = RequestLogSheetInner.Detail
                        },
                    )
                }
                SnackbarHost(
                    hostState = sheetSnackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private sealed interface RequestLogSheetInner {
    data object Detail : RequestLogSheetInner
    data class Copy(val text: String) : RequestLogSheetInner
}

@Composable
private fun LogJsonStringCopyPanel(
    text: String,
    onBack: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(HugeIcons.Cancel01, null)
            }
            Text(
                text = stringResource(R.string.select_and_copy),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onCopyAll) {
                Icon(
                    imageVector = HugeIcons.Copy01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.copy_all))
            }
        }
        SelectionContainer {
            Text(
                text = text,
                fontFamily = JetbrainsMono,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RequestLoggingSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.log_page_record_requests),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.log_page_record_requests_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RequestLogCard(
    log: LogEntry.RequestLog,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val redacted = remember(log.id, log.url) { log.redacted() as LogEntry.RequestLog }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = log.method,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = redacted.url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetbrainsMono,
                maxLines = 2,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                log.responseCode?.let { code ->
                    Text(
                        text = "Status: $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (code in 200..299) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                log.durationMs?.let { duration ->
                    Text(
                        text = "${duration}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            log.error?.let { error ->
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RequestLogDetail(
    log: LogEntry.RequestLog,
    listState: LazyListState,
    onStringClick: (String) -> Unit = {},
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val display = remember(log.id) { log.redacted() as LogEntry.RequestLog }

    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Request Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                DetailSection("Time", dateFormat.format(Date(display.timestamp)))
            }

            item {
                DetailSection("URL", display.url)
            }

            item {
                DetailSection("Method", display.method)
            }

            display.responseCode?.let { code ->
                item {
                    DetailSection("Status Code", code.toString())
                }
            }

            display.durationMs?.let { duration ->
                item {
                    DetailSection("Duration", "${duration}ms")
                }
            }

            display.error?.let { error ->
                item {
                    DetailSection("Error", error)
                }
            }

            if (display.requestHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "Request Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                display.requestHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }

            display.requestBody?.let { body ->
                item {
                    HorizontalDivider()
                    Text(
                        text = "Request Body",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val jsonElement = remember(body) {
                        runCatching { JsonInstantPretty.parseToJsonElement(body) }.getOrNull()
                    }
                    if (jsonElement != null) {
                        JsonTree(
                            json = jsonElement,
                            modifier = Modifier.padding(top = 4.dp),
                            initialExpandLevel = 2,
                            onStringClick = onStringClick,
                        )
                    } else {
                        Text(
                            text = body,
                            fontFamily = JetbrainsMono,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (display.responseHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "Response Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                display.responseHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetbrainsMono,
        )
    }
}

@Composable
private fun HeaderItem(key: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetbrainsMono,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextLogCard(
    log: LogEntry.TextLog,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetbrainsMono,
                )
            }
        }
    }
}