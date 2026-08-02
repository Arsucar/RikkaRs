package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
fun SettingClashPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val clashConfig = settings.clashConfig
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
        }
    }
}