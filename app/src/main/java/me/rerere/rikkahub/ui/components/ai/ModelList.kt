package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DragDropHorizontal
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.effectiveProviderTags
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.icons.HeartIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

private const val MODEL_SEARCH_DEBOUNCE_MS = 100L

@Stable
class ModelListState internal constructor(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
) {
    var modelId by mutableStateOf(modelId)
        private set

    var providers by mutableStateOf(providers)
        private set

    var type by mutableStateOf(type)
        private set

    var visible by mutableStateOf(false)
        private set

    val currentModel: Model?
        get() = modelId?.let { providers.findModelById(it) }

    val filteredProviders: List<ProviderSetting>
        get() = providers.fastFilter { provider ->
            provider.enabled && provider.models.fastAny { model -> model.type == type }
        }

    fun open() {
        visible = true
    }

    fun close() {
        visible = false
    }

    internal fun update(
        modelId: Uuid?,
        providers: List<ProviderSetting>,
        type: ModelType,
    ) {
        this.modelId = modelId
        this.providers = providers
        this.type = type
    }
}

@Composable
fun rememberModelListState(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
): ModelListState {
    return remember {
        ModelListState(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }.also {
        it.update(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }
}

internal data class RecentChatModelItem(
    val model: Model,
    val provider: ProviderSetting,
)

internal fun resolveRecentChatModelItems(
    recentChatModelIds: List<Uuid>,
    providers: List<ProviderSetting>,
    type: ModelType,
): List<RecentChatModelItem> {
    if (type != ModelType.CHAT) return emptyList()

    return recentChatModelIds.mapNotNull { modelId ->
        val model = providers.findModelById(modelId) ?: return@mapNotNull null
        val provider = model.findProvider(providers = providers, checkOverwrite = false)
            ?: return@mapNotNull null
        if (!provider.enabled || model.type != ModelType.CHAT) return@mapNotNull null
        RecentChatModelItem(model = model, provider = provider)
    }
}

internal fun searchModelsByProvider(
    providers: List<ProviderSetting>,
    modelType: ModelType,
    searchKeywords: String,
): Map<Uuid, List<Model>> = providers.associate { provider ->
    provider.id to provider.models.fastFilter { model ->
        model.type == modelType && modelMatchesSearch(model, provider, searchKeywords)
    }
}

internal fun resolveVisibleModelProviders(
    providers: List<ProviderSetting>,
    searchFilteredModelsByProvider: Map<Uuid, List<Model>>,
    searchKeywords: String,
): List<ProviderSetting> = if (searchKeywords.trim().isBlank()) {
    providers
} else {
    providers.filter { provider ->
        searchFilteredModelsByProvider[provider.id].orEmpty().isNotEmpty()
    }
}

@Composable
fun ModelSelector(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onSelect: (Model) -> Unit
) {
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = type,
    )
    val model = state.currentModel
    val recentChatModels = remember(settings.recentChatModels, providers, type) {
        resolveRecentChatModelItems(
            recentChatModelIds = settings.recentChatModels,
            providers = providers,
            type = type,
        )
    }
    var recentMenuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    if (!onlyIcon) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    state.open()
                },
                modifier = modifier
            ) {
                model?.modelId?.let {
                    AutoAIIcon(
                        it, Modifier
                            .padding(end = 4.dp)
                            .size(36.dp),
                        color = Color.Transparent
                    )
                }
                Text(
                    text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (allowClear && model != null) {
                IconButton(
                    onClick = {
                        onSelect(Model())
                    }
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.common_clear)
                    )
                }
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Box(
                    modifier = modifier
                        .size(48.dp)
                        .combinedClickable(
                            onClick = { state.open() },
                            onLongClick = {
                                if (recentChatModels.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    recentMenuExpanded = true
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (model != null) {
                        AutoAIIcon(
                            modifier = Modifier.size(36.dp),
                            name = model.modelId,
                            color = Color.Transparent
                        )
                    } else {
                        Icon(
                            imageVector = HugeIcons.Brain02,
                            contentDescription = stringResource(R.string.setting_model_page_chat_model),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = recentMenuExpanded,
                    onDismissRequest = { recentMenuExpanded = false },
                    modifier = Modifier.widthIn(max = 280.dp),
                ) {
                    recentChatModels.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = item.model.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (item.model.id == modelId) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Unspecified
                                        },
                                    )
                                    Text(
                                        text = item.provider.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = {
                                AutoAIIcon(
                                    modifier = Modifier.size(24.dp),
                                    name = item.model.modelId,
                                    color = Color.Transparent,
                                )
                            },
                            onClick = {
                                recentMenuExpanded = false
                                onSelect(item.model)
                            },
                        )
                    }
                }
            }
            if (allowClear && model != null) {
                IconButton(
                    onClick = {
                        onSelect(Model())
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.common_clear),
                    )
                }
            }
        }
    }

    ModelListSheet(
        state = state,
        onSelect = onSelect,
    )
}

@Composable
fun ModelListSheet(
    state: ModelListState,
    onSelect: (Model) -> Unit,
) {
    if (!state.visible) return

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    fun dismiss() {
        coroutineScope.launch {
            sheetState.hide()
            state.close()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            state.close()
        },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(top = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModelList(
                currentModel = state.modelId,
                providers = state.filteredProviders,
                modelType = state.type,
                onSelect = {
                    onSelect(it)
                    dismiss()
                },
                onDismiss = {
                    dismiss()
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.ModelList(
    currentModel: Uuid? = null,
    providers: List<ProviderSetting>,
    modelType: ModelType,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val settings = settingsStore.settingsFlow
        .collectAsStateWithLifecycle()

    var favoriteCollapsed by remember { mutableStateOf(false) }
    var searchKeywords by remember { mutableStateOf("") }
    var providerTabsExpanded by remember { mutableStateOf(false) }
    var selectedModelListTag by remember { mutableStateOf<String?>(null) }

    val favoriteModels = remember(
        settings.value.favoriteModels,
        settings.value.providers,
        providers,
        modelType,
        selectedModelListTag,
        searchKeywords,
    ) {
        settings.value.favoriteModels.mapNotNull { modelId ->
            val model = settings.value.providers.findModelById(modelId) ?: return@mapNotNull null
            if (model.type != modelType) return@mapNotNull null
            val provider =
                model.findProvider(providers = settings.value.providers, checkOverwrite = false)
                    ?: return@mapNotNull null
            val tag = selectedModelListTag
            if (tag != null && !provider.tags.contains(tag)) return@mapNotNull null
            if (!modelMatchesSearch(model, provider, searchKeywords)) return@mapNotNull null
            model to provider
        }
    }
    val providerGroupExpanded = remember { mutableStateMapOf<Uuid, Boolean>() }

    val tagFilteredProviders = remember(providers, selectedModelListTag) {
        if (selectedModelListTag == null) providers
        else providers.filter { it.tags.contains(selectedModelListTag) }
    }

    val searchFilteredModelsByProvider = remember(tagFilteredProviders, modelType, searchKeywords) {
        searchModelsByProvider(tagFilteredProviders, modelType, searchKeywords)
    }

    val visibleProviders = remember(tagFilteredProviders, searchFilteredModelsByProvider, searchKeywords) {
        resolveVisibleModelProviders(tagFilteredProviders, searchFilteredModelsByProvider, searchKeywords)
    }

    // 计算当前选中模型的位置
    val selectedModelPosition = remember(
        currentModel,
        favoriteModels,
        visibleProviders,
        searchFilteredModelsByProvider,
        providerGroupExpanded.toMap(),
    ) {
        if (currentModel == null) return@remember 0

        var position = 0

        // 跳过无providers提示
        if (tagFilteredProviders.isEmpty()) {
            position += 1
        }

        // 检查是否在收藏列表中
        val favoriteIndex = favoriteModels.indexOfFirst { it.first.id == currentModel }
        if (favoriteIndex >= 0) {
            if (favoriteModels.isNotEmpty()) {
                position += 1 // favorite header
            }
            position += favoriteIndex
            return@remember position
        }

        // 跳过收藏列表
        if (favoriteModels.isNotEmpty()) {
            position += 1 // favorite header
            position += favoriteModels.size
        }

        // 在providers中查找
        for (provider in visibleProviders) {
            val models = searchFilteredModelsByProvider[provider.id].orEmpty()
            val modelIndex = models.indexOfFirst { it.id == currentModel }
            val isExpanded = providerGroupExpanded[provider.id] != false
            position += 1
            if (modelIndex >= 0) {
                position += if (isExpanded) modelIndex else 0
                return@remember position
            }
            if (isExpanded) {
                position += models.size
            }
        }

        0
    }

    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedModelPosition
    )

    LaunchedEffect(currentModel, visibleProviders, searchFilteredModelsByProvider) {
        if (currentModel == null) return@LaunchedEffect
        for (provider in visibleProviders) {
            val models = searchFilteredModelsByProvider[provider.id].orEmpty()
            if (models.any { it.id == currentModel }) {
                if (providerGroupExpanded[provider.id] == false) {
                    providerGroupExpanded[provider.id] = true
                }
                break
            }
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 计算favorite models在列表中的位置偏移
        var favoriteStartIndex = 0
        if (tagFilteredProviders.isEmpty()) {
            favoriteStartIndex = 1 // no providers item
        }
        if (favoriteModels.isNotEmpty()) {
            favoriteStartIndex += 1 // favorite header
        }

        val fromIndex = from.index - favoriteStartIndex
        val toIndex = to.index - favoriteStartIndex

        // 只处理favorite models范围内的拖拽
        if (fromIndex >= 0 && toIndex >= 0 &&
            fromIndex < favoriteModels.size && toIndex < favoriteModels.size
        ) {
            val fromModelId = favoriteModels[fromIndex].first.id
            val toModelId = favoriteModels[toIndex].first.id
            val newFavoriteModels = settings.value.favoriteModels.toMutableList().apply {
                val fromFullIndex = indexOf(fromModelId)
                val toFullIndex = indexOf(toModelId)
                if (fromFullIndex < 0 || toFullIndex < 0) return@apply
                add(toFullIndex.coerceIn(0, size - 1), removeAt(fromFullIndex))
            }
            coroutineScope.launch {
                settingsStore.update { oldSettings ->
                    oldSettings.copy(favoriteModels = newFavoriteModels)
                }
            }
        }
    }
    val haptic = LocalHapticFeedback.current

    val providerPositions = remember(
        visibleProviders,
        favoriteModels,
        favoriteCollapsed,
        searchFilteredModelsByProvider,
        providerGroupExpanded.toMap(),
    ) {
        var currentIndex = 0
        if (tagFilteredProviders.isEmpty()) {
            currentIndex = 1
        }
        if (favoriteModels.isNotEmpty()) {
            currentIndex += 1
            if (!favoriteCollapsed) {
                currentIndex += favoriteModels.size
            }
        }

        visibleProviders.map { provider ->
            val position = currentIndex
            currentIndex += 1
            if (providerGroupExpanded[provider.id] != false) {
                currentIndex += searchFilteredModelsByProvider[provider.id].orEmpty().size
            }
            provider.id to position
        }.toMap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = searchKeywords,
                onValueChange = { searchKeywords = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.model_list_search_placeholder),
                    )
                },
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                leadingIcon = {
                    Icon(
                        HugeIcons.Search01,
                        contentDescription = stringResource(R.string.model_list_search),
                    )
                },
                maxLines = 1,
            )
        }
        val allCollapsed = visibleProviders.all { providerGroupExpanded[it.id] == false } &&
            favoriteCollapsed
        IconButton(
            onClick = {
                visibleProviders.forEach { provider ->
                    providerGroupExpanded[provider.id] = allCollapsed
                }
                favoriteCollapsed = !allCollapsed
            },
        ) {
            Icon(
                imageVector = if (allCollapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
                contentDescription = stringResource(
                    if (allCollapsed) R.string.model_list_expand_all else R.string.model_list_collapse_all,
                ),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = { providerTabsExpanded = !providerTabsExpanded },
        ) {
            Icon(
                imageVector = if (providerTabsExpanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                contentDescription = stringResource(
                    if (providerTabsExpanded) {
                        R.string.model_list_collapse_provider_tabs
                    } else {
                        R.string.model_list_expand_provider_tabs
                    },
                ),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    val allTags = remember(
        settings.value.providers,
        settings.value.providerTagOrder,
        settings.value.hiddenProviderTags,
    ) {
        settings.value.effectiveProviderTags()
    }
    if (allTags.isNotEmpty()) {
        val filterAllDescription = stringResource(R.string.filter_all)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedModelListTag == null,
                    onClick = { selectedModelListTag = null },
                    label = { Text(filterAllDescription) },
                    modifier = Modifier.semantics {
                        contentDescription = filterAllDescription
                    },
                )
            }
            items(allTags) { tag ->
                val tagFilterDescription = stringResource(R.string.model_list_filter_by_tag, tag)
                FilterChip(
                    selected = selectedModelListTag == tag,
                    onClick = { selectedModelListTag = if (selectedModelListTag == tag) null else tag },
                    label = { Text(tag) },
                    modifier = Modifier.semantics {
                        contentDescription = tagFilterDescription
                    },
                )
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        if (tagFilteredProviders.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.model_list_no_providers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendColors.gray6,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (favoriteModels.isNotEmpty()) {
            stickyHeader {
                val favoriteSectionDescription = stringResource(
                    if (favoriteCollapsed) {
                        R.string.model_list_expand_favorites
                    } else {
                        R.string.model_list_collapse_favorites
                    },
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp, top = 8.dp)
                        .clickable { favoriteCollapsed = !favoriteCollapsed }
                        .semantics(mergeDescendants = true) {
                            contentDescription = favoriteSectionDescription
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (favoriteCollapsed) HugeIcons.ArrowRight01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.model_list_favorite) + " (${favoriteModels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            if (!favoriteCollapsed) {
                items(
                    items = favoriteModels,
                    key = { "favorite:" + it.first.id.toString() },
                ) { (model, provider) ->
                ReorderableItem(
                    state = reorderableState,
                    key = "favorite:" + model.id.toString()
                ) { isDragging ->
                    ModelItem(
                        model = model,
                        onSelect = onSelect,
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .animateItem(),
                        providerSetting = provider,
                        select = model.id == currentModel,
                        onDismiss = {
                            onDismiss()
                        },
                        tail = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.update { settings ->
                                            settings.copy(
                                                favoriteModels = settings.favoriteModels.filter { it != model.id }
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    HeartIcon,
                                    contentDescription = stringResource(R.string.chat_message_remove_favorite),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        dragHandle = {
                            Icon(
                                imageVector = HugeIcons.DragDropHorizontal,
                                contentDescription = stringResource(R.string.model_list_reorder_favorite),
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    }
                                )
                            )
                        }
                    )
                }
            }
            }
        }

        visibleProviders.fastForEach { providerSetting ->
            val isProviderExpanded = providerGroupExpanded[providerSetting.id] != false
            stickyHeader(key = "header:${providerSetting.id}") {
                val providerSectionDescription = stringResource(
                    if (isProviderExpanded) {
                        R.string.model_list_collapse_provider
                    } else {
                        R.string.model_list_expand_provider
                    },
                    providerSetting.name,
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp, top = 8.dp)
                        .clickable {
                            providerGroupExpanded[providerSetting.id] = !isProviderExpanded
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = providerSectionDescription
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isProviderExpanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = providerSetting.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    ProviderBalanceText(
                        providerSetting = providerSetting,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isProviderExpanded) {
                items(
                    items = searchFilteredModelsByProvider[providerSetting.id].orEmpty(),
                    key = { it.id }
                ) { model ->
                    val favorite = settings.value.favoriteModels.contains(model.id)
                    ModelItem(
                        model = model,
                        onSelect = onSelect,
                        modifier = Modifier.animateItem(),
                        providerSetting = providerSetting,
                        select = currentModel == model.id,
                        onDismiss = {
                            onDismiss()
                        },
                        tail = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.update { settings ->
                                            if (favorite) {
                                                settings.copy(
                                                    favoriteModels = settings.favoriteModels.filter { it != model.id }
                                                )

                                            } else {
                                                settings.copy(
                                                    favoriteModels = settings.favoriteModels + model.id
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                if (favorite) {
                                    Icon(
                                        HeartIcon,
                                        contentDescription = stringResource(R.string.chat_message_remove_favorite),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = HugeIcons.Favourite,
                                        contentDescription = stringResource(R.string.chat_message_add_favorite),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // 供应商Badge行
    val providerBadgeListState = rememberLazyListState()
    LaunchedEffect(lazyListState, providerPositions, visibleProviders) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(MODEL_SEARCH_DEBOUNCE_MS)
            .collect { index ->
                if (index > 0) {
                    val currentProvider = providerPositions.entries.findLast {
                        index > it.value
                    }
                    val idx = visibleProviders.indexOfFirst { it.id == currentProvider?.key }
                    if (idx >= 0) {
                        providerBadgeListState.animateScrollToItem(idx)
                    } else {
                        providerBadgeListState.requestScrollToItem(0)
                    }
                } else {
                    providerBadgeListState.requestScrollToItem(0)
                }
            }
    }
    if (visibleProviders.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (providerTabsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        visibleProviders.forEach { provider ->
                            val scrollToProviderDescription = stringResource(
                                R.string.model_list_scroll_to_provider,
                                provider.name,
                            )
                            AssistChip(
                                onClick = {
                                    val position = providerPositions[provider.id] ?: 0
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(position)
                                    }
                                    providerTabsExpanded = false
                                },
                                label = {
                                    Text(provider.name)
                                },
                                leadingIcon = {
                                    AutoAIIcon(name = provider.name, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = scrollToProviderDescription
                                },
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                    state = providerBadgeListState
                ) {
                    items(visibleProviders, key = { it.id }) { provider ->
                        val scrollToProviderDescription = stringResource(
                            R.string.model_list_scroll_to_provider,
                            provider.name,
                        )
                        AssistChip(
                            onClick = {
                                val position = providerPositions[provider.id] ?: 0
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(position)
                                }
                            },
                            label = {
                                Text(provider.name)
                            },
                            leadingIcon = {
                                AutoAIIcon(name = provider.name, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.semantics {
                                contentDescription = scrollToProviderDescription
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: Model,
    providerSetting: ProviderSetting,
    select: Boolean,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tail: @Composable RowScope.() -> Unit = {},
    dragHandle: @Composable (RowScope.() -> Unit)? = null
) {
    val navController = LocalNavController.current
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (select) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (select) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        enabled = true,
                        onLongClick = {
                            onDismiss()
                            navController.navigate(
                                Screen.SettingProviderDetail(
                                    providerSetting.id.toString()
                                )
                            )
                        },
                        onClick = { onSelect(model) },
                        interactionSource = interactionSource,
                        indication = LocalIndication.current
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    AutoAIIcon(
                        name = model.modelId,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(32.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ModelTypeTag(model = model)

                        ModelModalityTag(model = model)

                        ModelAbilityTag(model = model)
                    }
                }
                tail()
            }
            dragHandle?.let { it() }
        }
    }
}

@Composable
fun ModelTypeTag(model: Model) {
    Tag(
        type = TagType.INFO
    ) {
        Text(
            text = stringResource(
                when (model.type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            )
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    Tag(
        type = TagType.SUCCESS
    ) {
        model.inputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
        )
        model.outputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
    }
}

@Composable
fun ModelAbilityTag(model: Model) {
    model.abilities.fastForEach { ability ->
        when (ability) {
            ModelAbility.TOOL -> {
                Tag(
                    type = TagType.WARNING
                ) {
                    Icon(
                        imageVector = HugeIcons.Tools,
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
                    )
                }
            }

            ModelAbility.REASONING -> {
                Tag(
                    type = TagType.INFO
                ) {
                    Icon(
                        painter = painterResource(R.drawable.deepthink),
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                    )
                }
            }
        }
    }
}

private fun modelMatchesSearch(
    model: Model,
    provider: ProviderSetting,
    searchKeywords: String,
): Boolean {
    val keyword = searchKeywords.trim()
    return keyword.isBlank() ||
        model.displayName.contains(keyword, ignoreCase = true) ||
        provider.name.contains(keyword, ignoreCase = true)
}
