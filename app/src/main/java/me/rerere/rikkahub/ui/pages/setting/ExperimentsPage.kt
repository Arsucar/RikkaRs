package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.CHECKPOINT_STEP_INTERVAL_OPTIONS
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.UiTypographyFamily
import me.rerere.rikkahub.data.datastore.UiTypographyWeightBias
import me.rerere.rikkahub.data.datastore.coerceCheckpointStepInterval
import me.rerere.rikkahub.data.datastore.coerceUiTypographyLetterSpacingScale
import me.rerere.rikkahub.data.datastore.coerceUiTypographyLineHeightScale
import me.rerere.rikkahub.data.datastore.coerceUiTypographyScale
import me.rerere.rikkahub.data.experimental.ExperimentalFeatureRegistry
import me.rerere.rikkahub.data.experimental.ExperimentalFeatureScope
import me.rerere.rikkahub.data.experimental.FEATURE_CHECKPOINT_CACHE
import me.rerere.rikkahub.data.experimental.FEATURE_UI_TYPOGRAPHY
import me.rerere.rikkahub.data.experimental.FeatureSpec
import me.rerere.rikkahub.data.experimental.resolveExperimentalFeature
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.resolveAppTypography
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

    fun updateDisplaySetting(transform: (DisplaySetting) -> DisplaySetting) {
        vm.updateSettings(settings.copy(displaySetting = transform(settings.displaySetting)))
    }

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
                    ExperimentsFeaturesCard(
                        specs = specs,
                        settings = settings,
                        onToggleFeature = { spec, checked ->
                            if (checked) {
                                pendingEnable = spec
                            } else {
                                vm.updateExperimentalFeature(spec.id, false)
                            }
                        },
                        onCheckpointInterval = { interval ->
                            vm.updateCheckpointCache(stepInterval = interval)
                        },
                        onUpdateDisplaySetting = { transform ->
                            updateDisplaySetting(transform)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentsFeaturesCard(
    specs: List<FeatureSpec>,
    settings: Settings,
    onToggleFeature: (FeatureSpec, Boolean) -> Unit,
    onCheckpointInterval: (Int) -> Unit,
    onUpdateDisplaySetting: ((DisplaySetting) -> DisplaySetting) -> Unit,
) {
    val displaySetting = settings.displaySetting
    val scale = displaySetting.uiTypographyScale.coerceUiTypographyScale()
    val lineHeightScale = displaySetting.uiTypographyLineHeightScale.coerceUiTypographyLineHeightScale()
    val letterSpacingScale =
        displaySetting.uiTypographyLetterSpacingScale.coerceUiTypographyLetterSpacingScale()

    // Local drafts: slider drag stays off the DataStore / global theme path.
    var scaleDraft by remember { mutableFloatStateOf(scale) }
    var scaleDragging by remember { mutableStateOf(false) }
    LaunchedEffect(scale) {
        if (!scaleDragging) scaleDraft = scale
    }

    var lineHeightDraft by remember { mutableFloatStateOf(lineHeightScale) }
    var lineHeightDragging by remember { mutableStateOf(false) }
    LaunchedEffect(lineHeightScale) {
        if (!lineHeightDragging) lineHeightDraft = lineHeightScale
    }

    var letterSpacingDraft by remember { mutableFloatStateOf(letterSpacingScale) }
    var letterSpacingDragging by remember { mutableStateOf(false) }
    LaunchedEffect(letterSpacingScale) {
        if (!letterSpacingDragging) letterSpacingDraft = letterSpacingScale
    }

    val family = displaySetting.uiTypographyFamily
    val weightBias = displaySetting.uiTypographyWeightBias
    val previewTypography = remember(
        family,
        weightBias,
        scaleDraft,
        lineHeightDraft,
        letterSpacingDraft,
    ) {
        resolveAppTypography(
            family = family,
            weightBias = weightBias,
            scale = scaleDraft,
            lineHeightScale = lineHeightDraft,
            letterSpacingScale = letterSpacingDraft,
        )
    }

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
                        onCheckedChange = { checked -> onToggleFeature(spec, checked) },
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
                                            if (enabled) onCheckpointInterval(interval)
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
            if (spec.id == FEATURE_UI_TYPOGRAPHY) {
                UiTypographyExperimentItems(
                    displaySetting = displaySetting,
                    enabled = enabled,
                    scaleDraft = scaleDraft,
                    lineHeightDraft = lineHeightDraft,
                    letterSpacingDraft = letterSpacingDraft,
                    previewTypography = previewTypography,
                    onScaleDraftChange = { value, dragging ->
                        scaleDragging = dragging
                        scaleDraft = value
                    },
                    onLineHeightDraftChange = { value, dragging ->
                        lineHeightDragging = dragging
                        lineHeightDraft = value
                    },
                    onLetterSpacingDraftChange = { value, dragging ->
                        letterSpacingDragging = dragging
                        letterSpacingDraft = value
                    },
                    onUpdate = { if (enabled) onUpdateDisplaySetting(it) },
                )
            }
        }
    }
}

private fun CardGroupScope.UiTypographyExperimentItems(
    displaySetting: DisplaySetting,
    enabled: Boolean,
    scaleDraft: Float,
    lineHeightDraft: Float,
    letterSpacingDraft: Float,
    previewTypography: Typography,
    onScaleDraftChange: (value: Float, dragging: Boolean) -> Unit,
    onLineHeightDraftChange: (value: Float, dragging: Boolean) -> Unit,
    onLetterSpacingDraftChange: (value: Float, dragging: Boolean) -> Unit,
    onUpdate: ((DisplaySetting) -> DisplaySetting) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    val family = displaySetting.uiTypographyFamily
    val weightBias = displaySetting.uiTypographyWeightBias

    item(
        modifier = Modifier.alpha(alpha),
        headlineContent = { Text(stringResource(R.string.experiments_ui_typography_family)) },
        supportingContent = {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = family == UiTypographyFamily.SYSTEM,
                    onClick = {
                        onUpdate { it.copy(uiTypographyFamily = UiTypographyFamily.SYSTEM) }
                    },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.experiments_ui_typography_family_system)) },
                )
                FilterChip(
                    selected = family == UiTypographyFamily.BRAND,
                    onClick = {
                        onUpdate { it.copy(uiTypographyFamily = UiTypographyFamily.BRAND) }
                    },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.experiments_ui_typography_family_brand)) },
                )
            }
        },
    )
    item(
        modifier = Modifier.alpha(alpha),
        headlineContent = { Text(stringResource(R.string.experiments_ui_typography_weight)) },
        supportingContent = {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UiTypographyWeightBias.entries.forEach { bias ->
                    FilterChip(
                        selected = weightBias == bias,
                        onClick = { onUpdate { it.copy(uiTypographyWeightBias = bias) } },
                        enabled = enabled,
                        label = { Text(bias.label()) },
                    )
                }
            }
        },
    )
    item(
        modifier = Modifier.alpha(alpha),
        headlineContent = { Text(stringResource(R.string.experiments_ui_typography_scale)) },
        supportingContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = scaleDraft,
                    onValueChange = {
                        onScaleDraftChange(it.coerceUiTypographyScale(), true)
                    },
                    onValueChangeFinished = {
                        val committed = scaleDraft.coerceUiTypographyScale()
                        onScaleDraftChange(committed, false)
                        onUpdate { setting ->
                            setting.copy(uiTypographyScale = committed)
                        }
                    },
                    valueRange = 0.85f..1.25f,
                    steps = 7,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "${(scaleDraft * 100).toInt()}%")
            }
        },
    )
    item(
        modifier = Modifier.alpha(alpha),
        headlineContent = { Text(stringResource(R.string.experiments_ui_typography_line_height)) },
        supportingContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = lineHeightDraft,
                    onValueChange = {
                        onLineHeightDraftChange(it.coerceUiTypographyLineHeightScale(), true)
                    },
                    onValueChangeFinished = {
                        val committed = lineHeightDraft.coerceUiTypographyLineHeightScale()
                        onLineHeightDraftChange(committed, false)
                        onUpdate { setting ->
                            setting.copy(uiTypographyLineHeightScale = committed)
                        }
                    },
                    valueRange = 0.9f..1.3f,
                    steps = 7,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "${(lineHeightDraft * 100).toInt()}%")
            }
        },
    )
    item(
        modifier = Modifier.alpha(alpha),
        headlineContent = { Text(stringResource(R.string.experiments_ui_typography_letter_spacing)) },
        supportingContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = letterSpacingDraft,
                    onValueChange = {
                        onLetterSpacingDraftChange(it.coerceUiTypographyLetterSpacingScale(), true)
                    },
                    onValueChangeFinished = {
                        val committed = letterSpacingDraft.coerceUiTypographyLetterSpacingScale()
                        onLetterSpacingDraftChange(committed, false)
                        onUpdate { setting ->
                            setting.copy(uiTypographyLetterSpacingScale = committed)
                        }
                    },
                    valueRange = 0f..1.5f,
                    steps = 14,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "${(letterSpacingDraft * 100).toInt()}%")
            }
        },
    )
    item(
        modifier = Modifier.alpha(alpha),
        headlineContent = { Text(stringResource(R.string.experiments_ui_typography_preview_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.experiments_ui_typography_preview),
                    style = previewTypography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.experiments_ui_typography_preview),
                    style = previewTypography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.experiments_ui_typography_preview),
                    style = previewTypography.labelMedium,
                )
            }
        },
    )
}

@Composable
private fun UiTypographyWeightBias.label(): String = when (this) {
    UiTypographyWeightBias.LIGHT -> stringResource(R.string.experiments_ui_typography_weight_light)
    UiTypographyWeightBias.DEFAULT -> stringResource(R.string.experiments_ui_typography_weight_default)
    UiTypographyWeightBias.MEDIUM -> stringResource(R.string.experiments_ui_typography_weight_medium)
    UiTypographyWeightBias.BOLD -> stringResource(R.string.experiments_ui_typography_weight_bold)
}
