package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.CHECKPOINT_STEP_INTERVAL_OPTIONS
import me.rerere.rikkahub.data.datastore.coerceCheckpointStepInterval
import me.rerere.rikkahub.data.experimental.ExperimentalFeatureRegistry
import me.rerere.rikkahub.data.experimental.ExperimentalFeatureScope
import me.rerere.rikkahub.data.experimental.FEATURE_CHECKPOINT_CACHE
import me.rerere.rikkahub.data.experimental.FeatureSpec
import me.rerere.rikkahub.data.experimental.resolveExperimentalFeature
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExperimentsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val specs = remember { ExperimentalFeatureRegistry.byScope(ExperimentalFeatureScope.Global) }
    var pendingEnable by remember { mutableStateOf<FeatureSpec?>(null) }

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(PermissionNotification)
        } else {
            emptySet()
        },
    )
    PermissionManager(permissionState = permissionState)

    pendingEnable?.let { spec ->
        AlertDialog(
            onDismissRequest = { pendingEnable = null },
            title = { Text(stringResource(R.string.experiments_enable_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.experiments_enable_confirm_message,
                        stringResource(spec.titleRes),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (spec.id == me.rerere.rikkahub.data.experimental.FEATURE_CHAT_KEEPALIVE &&
                            !permissionState.allPermissionsGranted
                        ) {
                            permissionState.requestPermissions()
                        }
                        vm.updateExperimentalFeature(spec.id, true)
                        pendingEnable = null
                    },
                ) {
                    Text(stringResource(R.string.experiments_enable_confirm_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEnable = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.experiments_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.experiments_page_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (specs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.experiments_page_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            } else {
                item {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        specs.forEach { spec ->
                            val enabled = resolveExperimentalFeature(spec.id, settings)
                            item(
                                headlineContent = { Text(stringResource(spec.titleRes)) },
                                supportingContent = { Text(stringResource(spec.descriptionRes)) },
                                trailingContent = {
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                pendingEnable = spec
                                            } else {
                                                vm.updateExperimentalFeature(spec.id, false)
                                            }
                                        },
                                    )
                                },
                            )
                            if (spec.id == FEATURE_CHECKPOINT_CACHE) {
                                item(
                                    modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                                    headlineContent = {
                                        Text(stringResource(R.string.setting_display_page_checkpoint_interval))
                                    },
                                    supportingContent = {
                                        Column {
                                            Text(stringResource(R.string.setting_display_page_checkpoint_interval_desc))
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                val selected = coerceCheckpointStepInterval(
                                                    settings.checkpointStepInterval,
                                                )
                                                CHECKPOINT_STEP_INTERVAL_OPTIONS.forEach { interval ->
                                                    FilterChip(
                                                        selected = selected == interval,
                                                        onClick = {
                                                            if (enabled) {
                                                                vm.updateCheckpointCache(stepInterval = interval)
                                                            }
                                                        },
                                                        enabled = enabled,
                                                        label = { Text(interval.toString()) },
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
