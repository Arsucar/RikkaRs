package me.rerere.rikkahub.ui.pages.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.clash.ClashRetryTrace
import me.rerere.rikkahub.data.ai.clash.ClashRetryTracer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingClashPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val clashConfig = settings.clashConfig
    val clashRetryTracer: ClashRetryTracer = koinInject()
    val traces by clashRetryTracer.traces.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    var apiBaseUrlText by remember(clashConfig.apiBaseUrl) {
        mutableStateOf(clashConfig.apiBaseUrl)
    }
    var groupNameText by remember(clashConfig.groupName) {
        mutableStateOf(clashConfig.groupName)
    }
    var maxRetriesText by remember(clashConfig.maxRetries) {
        mutableStateOf(clashConfig.maxRetries.toString())
    }
    var switchDelayMsText by remember(clashConfig.switchDelayMs) {
        mutableStateOf(clashConfig.switchDelayMs.toString())
    }

    val apiBaseUrlError = apiBaseUrlText.isNotBlank() &&
        !apiBaseUrlText.trim().startsWith("http://") &&
        !apiBaseUrlText.trim().startsWith("https://")

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_clash_page_title)) },
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
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_clash_page_zone_title)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_clash_page_api_base_url)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_clash_page_api_base_url_desc))
                        },
                        trailingContent = {
                            TextField(
                                value = apiBaseUrlText,
                                onValueChange = { value ->
                                    apiBaseUrlText = value
                                    val trimmed = value.trim()
                                    if (trimmed.isNotBlank() &&
                                        (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                                    ) {
                                        scope.launch {
                                            settingsStore.updateClashProxyConfig {
                                                it.copy(apiBaseUrl = trimmed)
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                isError = apiBaseUrlError,
                                modifier = Modifier.width(180.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_clash_page_group_name)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_clash_page_group_name_desc))
                        },
                        trailingContent = {
                            TextField(
                                value = groupNameText,
                                onValueChange = { value ->
                                    groupNameText = value
                                    if (value.isNotBlank()) {
                                        scope.launch {
                                            settingsStore.updateClashProxyConfig {
                                                it.copy(groupName = value.trim())
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_clash_page_max_retries)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_clash_page_max_retries_desc))
                        },
                        trailingContent = {
                            TextField(
                                value = maxRetriesText,
                                onValueChange = { value ->
                                    maxRetriesText = value.filter { it.isDigit() }
                                    val retries = maxRetriesText.toIntOrNull()
                                    if (retries != null && retries in 1..5) {
                                        scope.launch {
                                            settingsStore.updateClashProxyConfig {
                                                it.copy(maxRetries = retries)
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = maxRetriesText.toIntOrNull()?.let { it !in 1..5 } ?: true,
                                modifier = Modifier.width(100.dp),
                                supportingText = {
                                    val error =
                                        maxRetriesText.toIntOrNull()?.let { it !in 1..5 } ?: true
                                    if (error) {
                                        Text(stringResource(R.string.setting_clash_page_max_retries_desc))
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_clash_page_switch_delay_ms)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_clash_page_switch_delay_ms_desc))
                        },
                        trailingContent = {
                            TextField(
                                value = switchDelayMsText,
                                onValueChange = { value ->
                                    switchDelayMsText = value.filter { it.isDigit() }
                                    val delay = switchDelayMsText.toLongOrNull()
                                    if (delay != null && delay in 100..2000) {
                                        scope.launch {
                                            settingsStore.updateClashProxyConfig {
                                                it.copy(switchDelayMs = delay)
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = switchDelayMsText.toLongOrNull()?.let { it !in 100..2000 } ?: true,
                                modifier = Modifier.width(100.dp),
                                supportingText = {
                                    val error = switchDelayMsText.toLongOrNull()?.let {
                                        it !in 100..2000
                                    } ?: true
                                    if (error) {
                                        Text(stringResource(R.string.setting_clash_page_switch_delay_ms_desc))
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_clash_page_experimental_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_clash_page_known_limit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_clash_page_debug_title)) },
                ) {
                    when {
                        traces.isEmpty() -> item(
                            headlineContent = { Text(stringResource(R.string.setting_clash_page_debug_empty)) },
                            supportingContent = {
                                Text(stringResource(R.string.setting_clash_page_debug_empty_desc))
                            },
                        )
                        else -> {
                            item(
                                headlineContent = {
                                    Text(stringResource(R.string.setting_clash_page_debug_records, traces.size))
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = {
                                            val clipText = buildString {
                                                traces.reversed().forEach { t ->
                                                    appendLine(
                                                        "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(t.timestamp)} ${t.requestHost} ${t.finalCode ?: t.responseCode}"
                                                    )
                                                    appendLine(
                                                        "  ${t.matchedProvider ?: "-"} rotation=${t.rotationEnabled} maxRetries=${t.maxRetries}"
                                                    )
                                                    t.switches.forEach { s ->
                                                        appendLine(
                                                            "  switch: ${s.nodeName ?: "?"} success=${s.success} replayedCode=${s.replayedCode ?: "?"} error=${s.error ?: ""}"
                                                        )
                                                    }
                                                    appendLine(
                                                        "  final=${t.finalCode ?: t.responseCode} skipped=${t.skippedReason ?: "-"} exhausted=${t.exhausted}"
                                                    )
                                                }
                                            }
                                            val clipboard =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("clash-429-traces", clipText.toString()))
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.setting_clash_page_debug_copied),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }) {
                                            Text(stringResource(R.string.setting_clash_page_debug_copy))
                                        }
                                        TextButton(onClick = { scope.launch { clashRetryTracer.clear() } }) {
                                            Text(stringResource(R.string.setting_clash_page_debug_clear))
                                        }
                                    }
                                },
                            )
                            traces.reversed().forEach { trace ->
                                val expanded = remember(trace) { mutableStateOf(false) }
                                item(
                                    onClick = { expanded.value = !expanded.value },
                                    headlineContent = { Text(traceSummaryHeader(trace)) },
                                    supportingContent = {
                                        if (expanded.value) {
                                            // 决策链完整文本（多行）
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                traceDecisionLines(trace).forEach { line ->
                                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    },
                                    trailingContent = { Text(if (expanded.value) "▾" else "▸") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun traceSummaryHeader(trace: ClashRetryTrace): String {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(trace.timestamp)
    val code = trace.finalCode ?: trace.responseCode
    return "$time  ${trace.requestHost} · $code"
}

@Composable
private fun traceDecisionLines(trace: ClashRetryTrace): List<String> {
    val skipReason = trace.skippedReason
    return if (skipReason != null) {
        val reason = when (skipReason) {
            "SKIP_MAX_RETRIES" -> stringResource(R.string.setting_clash_page_debug_skip_max_retries)
            "SKIP_NO_PROVIDER" -> stringResource(R.string.setting_clash_page_debug_skip_no_provider)
            "SKIP_ROTATION_DISABLED" -> stringResource(R.string.setting_clash_page_debug_skip_rotation_disabled)
            else -> skipReason
        }
        listOf(
            "429 → $reason",
            stringResource(R.string.setting_clash_page_debug_decision_skipped, (trace.finalCode ?: trace.responseCode).toString()),
        )
    } else {
        buildList {
            add(
                stringResource(
                    R.string.setting_clash_page_debug_decision_match,
                    trace.matchedProvider ?: "?",
                    if (trace.rotationEnabled) {
                        stringResource(R.string.setting_clash_page_debug_decision_rotation_on)
                    } else {
                        stringResource(R.string.setting_clash_page_debug_decision_rotation_off)
                    },
                    trace.maxRetries,
                )
            )
            trace.switches.forEach { s ->
                when {
                    !s.success -> add(
                        stringResource(R.string.setting_clash_page_debug_decision_switch_failed, s.error ?: "?")
                    )
                    s.replayedCode != null -> add(
                        stringResource(
                            R.string.setting_clash_page_debug_decision_switch,
                            s.nodeName ?: "?",
                            s.replayedCode.toString(),
                        )
                    )
                    else -> add(
                        stringResource(R.string.setting_clash_page_debug_decision_replay_failed, s.error ?: "?")
                    )
                }
            }
            val exhaustedSuffix =
                if (trace.exhausted) stringResource(R.string.setting_clash_page_debug_decision_exhausted) else ""
            add(
                stringResource(
                    R.string.setting_clash_page_debug_decision_final,
                    (trace.finalCode ?: trace.responseCode).toString(),
                    exhaustedSuffix,
                )
            )
        }
    }
}