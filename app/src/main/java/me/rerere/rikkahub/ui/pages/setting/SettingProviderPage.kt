package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DragDropHorizontal
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Clipboard
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Cancel01
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.RECOMMENDED_PROVIDERS
import me.rerere.rikkahub.data.datastore.deleteProviderTag
import me.rerere.rikkahub.data.datastore.effectiveProviderTags
import me.rerere.rikkahub.data.datastore.renameProviderTag
import me.rerere.rikkahub.data.datastore.reorderProviderTags
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.data.sync.importer.ProviderImportResult
import me.rerere.rikkahub.data.sync.importer.decodeProviderImportText
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import com.dokar.sonner.ToasterState
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
fun SettingProviderPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTag by remember { mutableStateOf<String?>(null) }
    var showTagManager by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.providers.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vm.updateSettings(settings.copy(providers = newProviders))
    }

    val filteredProviders = remember(settings.providers, searchQuery, selectedFilterTag) {
        settings.providers.filter { provider ->
            val matchesSearch = searchQuery.isBlank() ||
                provider.name.contains(searchQuery, ignoreCase = true)
            val matchesTag = selectedFilterTag == null || provider.tags.contains(selectedFilterTag)
            matchesSearch && matchesTag
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.setting_provider_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    RecommendProviderButton { provider ->
                        vm.updateSettings(
                            settings.copy(
                                providers = listOf(provider.copyProvider(Uuid.random())) + settings.providers
                            )
                        )
                    }
                    ImportProviderButton {
                        vm.updateSettings(
                            settings.copy(
                                providers = listOf(it.copyProvider(Uuid.random())) + settings.providers
                            )
                        )
                    }
                    AddButton {
                        vm.updateSettings(
                            settings.copy(
                                providers = listOf(it) + settings.providers
                            )
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.setting_provider_page_search_providers)) },
                leadingIcon = {
                    Icon(HugeIcons.Search01, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
            )

            val suggestedProviderTags = stringArrayResource(R.array.provider_suggested_tags).toList()
            val allTags = remember(
                settings.providers,
                settings.providerTagOrder,
                settings.hiddenProviderTags,
                suggestedProviderTags,
            ) {
                settings.effectiveProviderTags(suggestedProviderTags)
            }
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilterTag == null,
                                onClick = { selectedFilterTag = null },
                                label = { Text(stringResource(R.string.filter_all)) }
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedFilterTag == tag,
                                onClick = { selectedFilterTag = if (selectedFilterTag == tag) null else tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                    TextButton(onClick = { showTagManager = true }) {
                        Text(stringResource(R.string.setting_provider_page_manage_tags))
                    }
                }
            }
            if (showTagManager) {
                ProviderTagManagerSheet(
                    tags = allTags,
                    onDismiss = { showTagManager = false },
                    onRename = { oldTag, newTag ->
                        if (selectedFilterTag == oldTag) selectedFilterTag = newTag.trim()
                        vm.updateSettings(settings.renameProviderTag(oldTag, newTag, suggestedProviderTags))
                    },
                    onDelete = { tag ->
                        if (selectedFilterTag == tag) selectedFilterTag = null
                        vm.updateSettings(settings.deleteProviderTag(tag))
                    },
                    onReorder = { reorderedTags ->
                        vm.updateSettings(settings.reorderProviderTags(reorderedTags))
                    },
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) +
                    PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                items(filteredProviders, key = { it.id }) { provider ->
                    ReorderableItem(
                        state = reorderableState,
                        key = provider.id
                    ) { isDragging ->
                        ProviderItem(
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth(),
                            provider = provider,
                            dragHandle = {
                                val haptic = LocalHapticFeedback.current
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            }
                                        )
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.DragDropHorizontal,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                navController.navigate(Screen.SettingProviderDetail(providerId = provider.id.toString()))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTagManagerSheet(
    tags: List<String>,
    onDismiss: () -> Unit,
    onRename: (oldTag: String, newTag: String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    var editingTag by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var deletingTag by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_provider_tags),
                style = MaterialTheme.typography.titleLarge,
            )
            tags.forEachIndexed { index, tag ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = tag,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onReorder(tags.moveTag(index, index - 1)) },
                        enabled = index > 0,
                    ) {
                        Icon(HugeIcons.ArrowUp01, contentDescription = null)
                    }
                    IconButton(
                        onClick = { onReorder(tags.moveTag(index, index + 1)) },
                        enabled = index < tags.lastIndex,
                    ) {
                        Icon(HugeIcons.ArrowDown01, contentDescription = null)
                    }
                    TextButton(
                        onClick = {
                            editingTag = tag
                            editingText = tag
                        }
                    ) {
                        Text(stringResource(R.string.setting_provider_page_rename_tag))
                    }
                    IconButton(onClick = { deletingTag = tag }) {
                        Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }

    editingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { editingTag = null },
            title = { Text(stringResource(R.string.setting_provider_page_rename_tag)) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.setting_provider_page_new_tag_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editingText.isNotBlank(),
                    onClick = {
                        onRename(tag, editingText)
                        editingTag = null
                    },
                ) {
                    Text(stringResource(R.string.setting_provider_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTag = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(tag) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(tag)
                        deletingTag = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun List<String>.moveTag(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply {
        add(to, removeAt(from))
    }
}

@Composable
private fun RecommendProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    var showSheet by remember { mutableStateOf(false) }
    val importSuccessMessage = stringResource(R.string.setting_provider_page_import_success)

    IconButton(
        onClick = { showSheet = true }
    ) {
        Icon(HugeIcons.Sparkles, contentDescription = stringResource(R.string.setting_provider_page_recommend))
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_recommend),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RECOMMENDED_PROVIDERS.forEach { provider ->
                    RecommendProviderItem(
                        provider = provider,
                        onAdd = {
                            onAdd(provider)
                            toaster.show(
                                importSuccessMessage,
                                type = ToastType.Success
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendProviderItem(
    provider: ProviderSetting,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(
                name = provider.name,
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.description()
                    }
                }
            }
            IconButton(onClick = onAdd) {
                Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.setting_provider_page_add))
            }
        }
    }
}

@Composable
private fun ImportProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingNameSetting by remember { mutableStateOf<ProviderSetting?>(null) }
    var importNameInput by remember { mutableStateOf("") }

    val onNeedsName: (ProviderSetting) -> Unit = { setting ->
        pendingNameSetting = setting
        importNameInput = defaultImportProviderName(setting)
    }

    val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        handleQRResult(result, onAdd, toaster, context, onNeedsName)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            handleImageQRCode(it, onAdd, toaster, context, onNeedsName)
        }
    }

    IconButton(
        onClick = {
            showImportDialog = true
        }
    ) {
        Icon(HugeIcons.FileImport, null)
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.setting_provider_page_import_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_import_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 主要操作：扫描二维码
                        Button(
                            onClick = {
                                showImportDialog = false
                                scanQrCodeLauncher.launch(null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Camera01,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_scan_qr_code),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // 次要操作：从相册选择
                        OutlinedButton(
                            onClick = {
                                showImportDialog = false
                                pickImageLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Image02,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_select_from_gallery),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val text = context.readClipboardText().trim()
                                if (text.isEmpty()) {
                                    toaster.show(
                                        context.getString(R.string.setting_provider_page_clipboard_empty),
                                        type = ToastType.Error,
                                    )
                                    return@OutlinedButton
                                }
                                showImportDialog = false
                                processProviderImportText(text, onAdd, toaster, context, onNeedsName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Clipboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_paste_from_clipboard),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    pendingNameSetting?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingNameSetting = null },
            title = {
                Text(
                    text = stringResource(R.string.setting_provider_page_import_name_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                OutlinedTextField(
                    value = importNameInput,
                    onValueChange = { importNameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(stringResource(R.string.setting_provider_page_import_name_hint))
                    },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = importNameInput.trim()
                        if (name.isBlank()) {
                            toaster.show(
                                context.getString(R.string.setting_provider_page_import_name_hint),
                                type = ToastType.Error,
                            )
                            return@TextButton
                        }
                        onAdd(pending.copyProvider(name = name))
                        pendingNameSetting = null
                        toaster.show(
                            context.getString(R.string.setting_provider_page_import_success),
                            type = ToastType.Success,
                        )
                    },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingNameSetting = null },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
    }
}

private fun importFormatErrorMessage(context: android.content.Context, error: Throwable): String {
    val msg = error.message.orEmpty()
    return if (msg == "Invalid import format") {
        context.getString(R.string.setting_provider_page_import_invalid_format)
    } else {
        context.getString(R.string.setting_provider_page_qr_decode_failed, msg)
    }
}

private fun defaultImportProviderName(setting: ProviderSetting): String {
    if (setting.name.isNotBlank() && setting.name != "NewAPI") {
        return setting.name
    }
    val openAi = setting as? ProviderSetting.OpenAI ?: return setting.name.ifBlank { "NewAPI" }
    return runCatching {
        val host = java.net.URI(openAi.baseUrl.trim()).host
        host?.ifBlank { null } ?: "NewAPI"
    }.getOrDefault("NewAPI")
}

private fun applyProviderImportResult(
    result: ProviderImportResult,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: android.content.Context,
    onNeedsName: (ProviderSetting) -> Unit,
) {
    when (result) {
        is ProviderImportResult.Complete -> {
            onAdd(result.setting)
            toaster.show(
                context.getString(R.string.setting_provider_page_import_success),
                type = ToastType.Success,
            )
        }

        is ProviderImportResult.NeedsName -> onNeedsName(result.setting)
    }
}

private fun processProviderImportText(
    raw: String,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: android.content.Context,
    onNeedsName: (ProviderSetting) -> Unit,
) {
    runCatching {
        applyProviderImportResult(
            decodeProviderImportText(raw),
            onAdd,
            toaster,
            context,
            onNeedsName,
        )
    }.onFailure { error ->
        toaster.show(importFormatErrorMessage(context, error), type = ToastType.Error)
    }
}

private fun handleQRResult(
    result: QRResult,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: android.content.Context,
    onNeedsName: (ProviderSetting) -> Unit,
) {
    when (result) {
        is QRResult.QRError -> {
            toaster.show(
                context.getString(
                    R.string.setting_provider_page_scan_error,
                    result,
                ),
                type = ToastType.Error,
            )
        }

        QRResult.QRMissingPermission -> {
            toaster.show(
                context.getString(R.string.setting_provider_page_no_permission),
                type = ToastType.Error,
            )
        }

        is QRResult.QRSuccess -> {
            processProviderImportText(
                result.content.rawValue ?: "",
                onAdd,
                toaster,
                context,
                onNeedsName,
            )
        }

        QRResult.QRUserCanceled -> {}
    }
}

private fun handleImageQRCode(
    uri: Uri,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: android.content.Context,
    onNeedsName: (ProviderSetting) -> Unit,
) {
    runCatching {
        val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri)

        if (qrContent.isNullOrEmpty()) {
            toaster.show(
                context.getString(R.string.setting_provider_page_no_qr_found),
                type = ToastType.Error,
            )
            return
        }

        processProviderImportText(qrContent, onAdd, toaster, context, onNeedsName)
    }.onFailure { error ->
        toaster.show(
            context.getString(R.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error,
        )
    }
}


@Composable
private fun AddButton(onAdd: (ProviderSetting) -> Unit) {
    val dialogState = useEditState<ProviderSetting> {
        onAdd(it)
    }

    IconButton(
        onClick = {
            dialogState.open(ProviderSetting.OpenAI())
        }
    ) {
        Icon(HugeIcons.Add01, "Add")
    }

    if (dialogState.isEditing) {
        AlertDialog(
            onDismissRequest = {
                dialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.setting_provider_page_add_provider))
            },
            text = {
                dialogState.currentState?.let {
                    ProviderConfigure(it) { newState ->
                        dialogState.currentState = newState
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderItem(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (provider.enabled) {
                CustomColors.listItemColors.containerColor
            } else MaterialTheme.colorScheme.errorContainer,
        ),
        onClick = {
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(
                name = provider.name,
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.shortDescription()
                    }
                }
                val baseUrlSummary = remember(provider) {
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.baseUrl
                        is ProviderSetting.Google -> provider.baseUrl
                        is ProviderSetting.Claude -> provider.baseUrl
                    }.let { url ->
                        try {
                            url.removePrefix("https://").removePrefix("http://").split("/").first()
                        } catch (_: Exception) {
                            url
                        }
                    }
                }
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        Text(baseUrlSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(type = if (provider.enabled) TagType.SUCCESS else TagType.WARNING) {
                        Text(stringResource(if (provider.enabled) R.string.setting_provider_page_enabled else R.string.setting_provider_page_disabled))
                    }
                    Tag(type = TagType.INFO) {
                        val chatCount = provider.models.count { it.type == ModelType.CHAT }
                        Text(stringResource(R.string.setting_provider_page_model_count_chat, chatCount, provider.models.size))
                    }
                    if (provider.name == "AiHubMix") {
                        Tag(type = TagType.INFO) {
                            Text(stringResource(R.string.setting_provider_page_aihubmix_discount))
                        }
                    }
                    provider.tags.forEach { tag ->
                        Tag(type = TagType.INFO) {
                            Text(tag)
                        }
                    }
                }
            }
            dragHandle()
        }
    }
}
