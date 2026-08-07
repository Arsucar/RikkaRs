package me.rerere.rikkahub.ui.pages.imggen

import android.content.Context
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageBackgroundOption
import me.rerere.ai.ui.ImageOutputFormatOption
import me.rerere.ai.ui.ImageModerationOption
import me.rerere.ai.ui.ImageQualityOption
import me.rerere.ai.ui.ImageSizeOption
import me.rerere.ai.ui.ImageSizeValidationError
import me.rerere.ai.ui.validateGptImage2Size
import me.rerere.common.android.appTempFolder
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Colors
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.IMAGE_GALLERY_MAX_COLUMNS
import me.rerere.rikkahub.data.datastore.IMAGE_GALLERY_MIN_COLUMNS
import me.rerere.rikkahub.data.datastore.ImageGalleryDisplayMode
import me.rerere.rikkahub.data.datastore.ImageGenerationSettings
import me.rerere.rikkahub.data.datastore.ImageFavoriteCollection
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.ImageUtils
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

private const val IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS = 4

@Composable
private fun ImageSearchTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hadFocus by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (hadFocus && !focusState.isFocused) {
                            onFocusLost()
                        }
                        hadFocus = focusState.isFocused
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.imggen_page_search_keyword_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
fun ImageGenPage(
    modifier: Modifier = Modifier,
    vm: ImgGenVM = koinViewModel()
) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var showTopMenu by remember { mutableStateOf(false) }
    var showRecycleBin by remember { mutableStateOf(false) }
    var showImageQuickMessagesDialog by remember { mutableStateOf(false) }
    var isImageSearchActive by remember { mutableStateOf(false) }
    val imageSearchQuery by vm.imageSearchQuery.collectAsStateWithLifecycle()
    val imageSearchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val trashImages by vm.trashImages.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settingsFlow.collectAsStateWithLifecycle()

    LaunchedEffect(isImageSearchActive) {
        if (isImageSearchActive) {
            imageSearchFocusRequester.requestFocus()
            delay(80)
            keyboardController?.show()
        }
    }

    fun addImageAsReference(imagePath: String, navigateToGeneration: Boolean) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    copyImageToReferenceTemp(context, imagePath)
                }
            }.onSuccess { referencePath ->
                vm.addReferenceImages(listOf(referencePath))
                if (navigateToGeneration) {
                    pagerState.animateScrollToPage(0)
                }
                toaster.show(message = context.getString(R.string.imggen_page_reference_added), type = ToastType.Success)
            }.onFailure { error ->
                toaster.show(
                    message = context.getString(R.string.imggen_page_reference_failed, error.message ?: ""),
                    type = ToastType.Error,
                )
            }
        }
    }
    val useImageAsReference: (String) -> Unit = { imagePath -> addImageAsReference(imagePath, true) }
    val applyImageAsReference: (String) -> Unit = { imagePath -> addImageAsReference(imagePath, false) }
    fun collapseImageSearch() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isImageSearchActive = false
    }
    // 自动收纳：清焦、收键盘，仅在输入框为空时才收起搜索框（有关键词则保持展开）
    fun dismissImageSearchKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (imageSearchQuery.isBlank()) {
            isImageSearchActive = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isImageSearchActive) {
                        ImageSearchTitleField(
                            value = imageSearchQuery,
                            onValueChange = vm::updateImageSearchQuery,
                            focusRequester = imageSearchFocusRequester,
                            onSearch = {
                                dismissImageSearchKeyboard()
                            },
                            onFocusLost = {
                                // 仅在无关键词时收纳，有关键词保持展开
                                if (imageSearchQuery.isBlank()) {
                                    isImageSearchActive = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.imggen_page_title))
                    }
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isImageSearchActive) {
                                collapseImageSearch()
                            } else {
                                isImageSearchActive = true
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = HugeIcons.Search01,
                            contentDescription = stringResource(R.string.imggen_page_search_images),
                        )
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = HugeIcons.MoreVertical,
                                contentDescription = stringResource(R.string.menu)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false },
                        ) {
                            // 图库页：显示模式 + 列数；空间页：独立列数；点击设置项不关闭菜单，可连续调整
                            when (pagerState.currentPage) {
                                0 -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.imggen_page_manage_quick_messages)) },
                                        leadingIcon = { Icon(HugeIcons.Edit01, null) },
                                        onClick = {
                                            showTopMenu = false
                                            showImageQuickMessagesDialog = true
                                        },
                                    )
                                    HorizontalDivider()
                                }

                                1 -> {
                                    MenuSectionLabel(stringResource(R.string.imggen_page_display_mode))
                                    ImageGalleryDisplayMode.entries.forEach { mode ->
                                        val selected = settings.imageGallerySettings.displayMode == mode
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (mode) {
                                                        ImageGalleryDisplayMode.GRID -> stringResource(R.string.imggen_page_display_mode_grid)
                                                        ImageGalleryDisplayMode.GROUPED -> stringResource(R.string.imggen_page_display_mode_grouped)
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Lucide.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                } else {
                                                    Spacer(Modifier.size(24.dp))
                                                }
                                            },
                                            onClick = {
                                                scope.launch {
                                                    vm.settingsStore.update { current ->
                                                        current.copy(
                                                            imageGallerySettings = current.imageGallerySettings.copy(
                                                                displayMode = mode
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    ImageColumnsMenuRow(
                                        columns = settings.imageGallerySettings.columns.coerceIn(
                                            IMAGE_GALLERY_MIN_COLUMNS,
                                            IMAGE_GALLERY_MAX_COLUMNS
                                        ),
                                        onColumnsChange = { value ->
                                            scope.launch {
                                                vm.settingsStore.update { current ->
                                                    current.copy(
                                                        imageGallerySettings = current.imageGallerySettings.copy(columns = value)
                                                    )
                                                }
                                            }
                                        },
                                    )
                                    HorizontalDivider()
                                }

                                2 -> {
                                    ImageColumnsMenuRow(
                                        columns = settings.imageGallerySettings.spaceColumns.coerceIn(
                                            IMAGE_GALLERY_MIN_COLUMNS,
                                            IMAGE_GALLERY_MAX_COLUMNS
                                        ),
                                        onColumnsChange = { value ->
                                            scope.launch {
                                                vm.settingsStore.update { current ->
                                                    current.copy(
                                                        imageGallerySettings = current.imageGallerySettings.copy(spaceColumns = value)
                                                    )
                                                }
                                            }
                                        },
                                    )
                                    HorizontalDivider()
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.imggen_page_trash)) },
                                leadingIcon = { Icon(HugeIcons.Delete01, null) },
                                onClick = {
                                    showTopMenu = false
                                    showRecycleBin = true
                                },
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomBar(pagerState, scope)
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .pointerInput(isImageSearchActive, imageSearchQuery.isBlank()) {
                    if (!isImageSearchActive) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        dismissImageSearchKeyboard()
                    }
                }
        ) { page ->
            when (page) {
                0 -> ImageGenScreen(
                    vm = vm,
                    onUseReference = useImageAsReference,
                    onApplyReference = applyImageAsReference,
                )

                1 -> ImageGalleryScreen(
                    vm = vm,
                    onUseReference = useImageAsReference,
                    onApplyReference = applyImageAsReference,
                )

                2 -> ImageSpaceScreen(
                    vm = vm,
                    onUseReference = useImageAsReference,
                    onApplyReference = applyImageAsReference,
                )
            }
        }
    }

    if (showRecycleBin) {
        RecycleBinScreen(
            images = trashImages,
            onDismiss = { showRecycleBin = false },
            onRestore = vm::restoreImage,
            onPermanentDelete = vm::permanentlyDeleteImage,
            onClear = vm::permanentlyDeleteImages,
        )
    }

    if (showImageQuickMessagesDialog) {
        ImageQuickMessagesDialog(
            quickMessages = settings.imageQuickMessages,
            onDismiss = { showImageQuickMessagesDialog = false },
            onAdd = vm::addImageQuickMessage,
            onUpdate = vm::updateImageQuickMessage,
            onDelete = { vm.deleteImageQuickMessage(it.id) },
        )
    }
}

@Composable
private fun BottomBar(
    pagerState: PagerState,
    scope: CoroutineScope
) {
    NavigationBar {
        NavigationBarItem(
            selected = 0 == pagerState.currentPage,
            label = {
                Text(stringResource(R.string.imggen_page_title))
            },
            icon = {
                Icon(HugeIcons.Colors, null)
            },
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(0)
                }
            }
        )

        NavigationBarItem(
            selected = 1 == pagerState.currentPage,
            label = {
                Text(stringResource(R.string.imggen_page_gallery))
            },
            icon = {
                Icon(HugeIcons.Image03, null)
            },
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }
        )

        NavigationBarItem(
            selected = 2 == pagerState.currentPage,
            label = {
                Text(stringResource(R.string.imggen_page_space))
            },
            icon = {
                Icon(HugeIcons.InLove, null)
            },
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(2)
                }
            }
        )
    }
}

@Composable
private fun ImageGenScreen(
    vm: ImgGenVM,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
) {
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val numberOfImages by vm.numberOfImages.collectAsStateWithLifecycle()
    val aspectRatio by vm.aspectRatio.collectAsStateWithLifecycle()
    val activeJobs by vm.activeJobs.collectAsStateWithLifecycle()
    val jobList = remember(activeJobs) {
        activeJobs.values.sortedByDescending { it.id }
    }
    val imageFavoriteIds by vm.imageFavoriteIds.collectAsStateWithLifecycle()
    val referenceImages by vm.referenceImages.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val onToggleFavorite = rememberImageFavoriteToggler(vm)
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val columns = settings.imageGallerySettings.columns.coerceIn(
        IMAGE_GALLERY_MIN_COLUMNS,
        IMAGE_GALLERY_MAX_COLUMNS,
    )
    val showThumbnailActions = columns <= IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS

    LaunchedEffect(error) {
        error?.let { errorMessage ->
            toaster.show(message = errorMessage, type = ToastType.Error)
            vm.clearError()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (jobList.isEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    GalleryEmptyState()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(jobList, key = { it.id }) { job ->
                        ActiveGenerationJobSection(
                            job = job,
                            columns = columns,
                            showThumbnailActions = showThumbnailActions,
                            imageFavoriteIds = imageFavoriteIds,
                            onToggleFavorite = onToggleFavorite,
                            onUseReference = onUseReference,
                            onApplyReference = onApplyReference,
                            onCancel = { vm.cancelJob(job.id) },
                            onDismiss = { vm.dismissJob(job.id) },
                            onRegenerateJob = { vm.regenerateJob(job.id) },
                            onRegenerateImage = { vm.regenerateFromImage(it) },
                        )
                    }
                }
            }
        }
        val runningJobCount = remember(jobList) { jobList.count { it.isRunning } }
        val maxConcurrentJobs = settings.imageGenerationSettings.maxConcurrentJobs.coerceIn(1, MAX_CONCURRENT_IMAGE_GENERATION_JOBS_CAP)
        InputBar(
            prompt = prompt,
            vm = vm,
            runningJobCount = runningJobCount,
            maxConcurrentJobs = maxConcurrentJobs,
            referenceImages = referenceImages,
            settings = settings,
            onShowSettings = { showSettingsSheet = true },
            modifier = Modifier,
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            vm = vm,
            settings = settings,
            numberOfImages = numberOfImages,
            aspectRatio = aspectRatio,
            scope = scope,
            sheetState = sheetState,
            onDismiss = { showSettingsSheet = false },
        )
    }
}

@Composable
private fun ActiveGenerationJobSection(
    job: ImgGenActiveJob,
    columns: Int,
    showThumbnailActions: Boolean,
    imageFavoriteIds: Set<Int>,
    onToggleFavorite: (GeneratedImage) -> Unit,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRegenerateJob: () -> Unit,
    onRegenerateImage: (GeneratedImage) -> Unit,
) {
    var previewStartIndex by remember(job.id) { mutableStateOf<Int?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    PromptPreviewText(
                        prompt = job.prompt,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    Text(
                        text = if (job.isEdit) stringResource(R.string.imggen_page_job_edit) else stringResource(R.string.imggen_page_job_generate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (job.isRunning) {
                    IconButton(onClick = onCancel) {
                        Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                } else {
                    IconButton(onClick = onRegenerateJob) {
                        Icon(HugeIcons.Refresh01, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(HugeIcons.Delete01, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (job.isRunning && job.images.isEmpty()) {
                Text(
                    text = if (job.isAwaitingPermit) stringResource(R.string.imggen_page_job_queued) else stringResource(R.string.imggen_page_job_generating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            job.errorMessage?.let { message ->
                JobGenerationErrorText(message = message)
            }
            if (job.images.isNotEmpty()) {
                job.images.chunked(columns).forEach { rowImages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowImages.forEach { image ->
                            val imageIndex = job.images.indexOf(image)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            ) {
                                AsyncImage(
                                    model = File(image.filePath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            previewStartIndex = imageIndex
                                        },
                                    contentScale = ContentScale.Crop,
                                )
                                if (showThumbnailActions) {
                                    ImageThumbnailActions(
                                        image = image,
                                        isFavorited = image.id in imageFavoriteIds,
                                        onToggleFavorite = onToggleFavorite,
                                        onApplyReference = onApplyReference,
                                        onRegenerate = { onRegenerateImage(image) },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                    )
                                }
                            }
                        }
                        repeat(columns - rowImages.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    previewStartIndex?.let { startIndex ->
        ImagePreviewDialog(
            images = job.images.map { it.filePath },
            initialPage = startIndex,
            labels = job.images.map { formatImageDateTime(it.timestamp) },
            models = job.images.map { it.model },
            prompts = job.images.map { it.prompt },
            onUseAsReference = onUseReference,
            onDismissRequest = { previewStartIndex = null },
        )
    }
}

@Composable
private fun InputBar(
    prompt: String,
    vm: ImgGenVM,
    runningJobCount: Int,
    maxConcurrentJobs: Int,
    referenceImages: List<String>,
    settings: Settings,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                scope.launch {
                    val paths = selectedUris.mapNotNull { uri ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bitmap = ImageUtils.loadOptimizedBitmap(context, uri, maxSize = 2048)
                                    ?: error("Failed to decode image")
                                val pngBytes = FileUtils.compressBitmapToPng(bitmap)
                                bitmap.recycle()
                                val file = File(context.appTempFolder, "imggen_ref_${Uuid.random()}.png")
                                file.writeBytes(pngBytes)
                                file.absolutePath
                            }.getOrNull()
                        }
                    }
                    vm.addReferenceImages(paths)
                }
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (referenceImages.isNotEmpty()) {
            ReferenceImagesRow(
                images = referenceImages,
                onRemove = vm::removeReferenceImage
            )
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = vm::updatePrompt,
            placeholder = { Text(stringResource(R.string.imggen_page_prompt_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp),
            minLines = 1,
            maxLines = 5,
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodySmall,
            leadingIcon = if (settings.imageQuickMessages.isNotEmpty()) {
                {
                    ImageQuickMessageButton(
                        quickMessages = settings.imageQuickMessages,
                        onAppend = vm::appendQuickMessage,
                    )
                }
            } else null,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelSelector(
                modelId = settings.imageGenerationModelId,
                providers = settings.providers,
                type = ModelType.IMAGE,
                onlyIcon = true,
                onSelect = { model -> vm.selectImageGenerationModel(model.id) }
            )

            IconButton(
                onClick = onShowSettings
            ) {
                Icon(HugeIcons.Tools, null)
            }

            IconButton(
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = "Add reference image"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val canSend = prompt.isNotBlank()
            val atConcurrentLimit = runningJobCount >= maxConcurrentJobs
            Surface(
                onClick = {
                    if (!canSend || atConcurrentLimit) return@Surface
                    if (referenceImages.isEmpty()) {
                        vm.generateImage()
                    } else {
                        vm.editImage()
                    }
                },
                enabled = canSend && !atConcurrentLimit,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = when {
                    !canSend || atConcurrentLimit -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.primary
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowUp02,
                        contentDescription = stringResource(R.string.imggen_page_generate_image),
                        tint = when {
                            !canSend || atConcurrentLimit -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageQuickMessageButton(
    quickMessages: List<QuickMessage>,
    onAppend: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true }
        ) {
            Icon(HugeIcons.Zap, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(260.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = quickMessage.title.ifBlank { stringResource(R.string.imggen_page_untitled) },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                text = quickMessage.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    },
                    onClick = {
                        onAppend(quickMessage.content)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageQuickMessagesDialog(
    quickMessages: List<QuickMessage>,
    onDismiss: () -> Unit,
    onAdd: (title: String, content: String) -> Unit,
    onUpdate: (QuickMessage) -> Unit,
    onDelete: (QuickMessage) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<QuickMessage?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.imggen_page_image_quick_messages)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quickMessages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.imggen_page_image_quick_messages_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(quickMessages, key = { it.id }) { quickMessage ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = quickMessage.title.ifBlank { stringResource(R.string.imggen_page_untitled) },
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = quickMessage.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }
                                    IconButton(onClick = { editTarget = quickMessage }) {
                                        Icon(HugeIcons.Edit01, null)
                                    }
                                    IconButton(onClick = { onDelete(quickMessage) }) {
                                        Icon(
                                            imageVector = HugeIcons.Delete01,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAddDialog = true }) {
                Text(stringResource(R.string.imggen_page_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showAddDialog) {
        ImageQuickMessageEditDialog(
            title = stringResource(R.string.imggen_page_add_image_quick_message),
            initialQuickMessage = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, content ->
                onAdd(title, content)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { quickMessage ->
        ImageQuickMessageEditDialog(
            title = stringResource(R.string.imggen_page_edit_image_quick_message),
            initialQuickMessage = quickMessage,
            onDismiss = { editTarget = null },
            onConfirm = { title, content ->
                onUpdate(quickMessage.copy(title = title, content = content))
                editTarget = null
            },
        )
    }
}

@Composable
private fun ImageQuickMessageEditDialog(
    title: String,
    initialQuickMessage: QuickMessage?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit,
) {
    var quickMessageTitle by remember(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.title ?: "")
    }
    var quickMessageContent by remember(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.content ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quickMessageTitle,
                    onValueChange = { quickMessageTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.imggen_page_title_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quickMessageContent,
                    onValueChange = { quickMessageContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.imggen_page_content_label)) },
                    minLines = 4,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(quickMessageTitle.trim(), quickMessageContent.trim()) },
                enabled = quickMessageTitle.isNotBlank() && quickMessageContent.isNotBlank(),
            ) {
                Text(stringResource(R.string.assistant_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ReferenceImagesRow(
    images: List<String>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box {
                    AsyncImage(
                        model = File(image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        onClick = { onRemove(image) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Delete01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryScreen(
    vm: ImgGenVM,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
) {
    val settings by vm.settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val generatedImages = vm.generatedImages.collectAsLazyPagingItems()
    val groupedImages by vm.groupedImages.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (settings.imageGallerySettings.displayMode) {
                ImageGalleryDisplayMode.GRID -> ImageGalleryGrid(
                    vm = vm,
                    generatedImages = generatedImages,
                    columns = settings.imageGallerySettings.columns.coerceIn(
                        IMAGE_GALLERY_MIN_COLUMNS,
                        IMAGE_GALLERY_MAX_COLUMNS
                    ),
                    onUseReference = onUseReference,
                    onApplyReference = onApplyReference,
                )

                ImageGalleryDisplayMode.GROUPED -> GroupedImageGallery(
                    vm = vm,
                    groups = groupedImages,
                    columns = settings.imageGallerySettings.columns.coerceIn(
                        IMAGE_GALLERY_MIN_COLUMNS,
                        IMAGE_GALLERY_MAX_COLUMNS
                    ),
                    onUseReference = onUseReference,
                    onApplyReference = onApplyReference,
                )
            }
        }
    }
}

@Composable
private fun ImageGalleryGrid(
    vm: ImgGenVM,
    generatedImages: androidx.paging.compose.LazyPagingItems<GeneratedImage>,
    columns: Int,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val pullToRefreshState = rememberPullToRefreshState()
    val imageFavoriteIds by vm.imageFavoriteIds.collectAsStateWithLifecycle()
    val onToggleFavorite = rememberImageFavoriteToggler(vm)
    val showThumbnailActions = columns <= IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { generatedImages.refresh() },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (generatedImages.itemCount == 0) {
            GalleryEmptyState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = generatedImages.itemCount,
                    key = generatedImages.itemKey { it.id },
                    contentType = generatedImages.itemContentType { "GeneratedImage" }
                ) { index ->
                    val image = generatedImages[index]
                    image?.let {
                        var showPreview by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                ) {
                                    AsyncImage(
                                        model = File(it.filePath),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { showPreview = true },
                                        contentScale = ContentScale.Crop
                                    )
                                    if (showThumbnailActions) {
                                        ImageThumbnailActions(
                                            image = it,
                                            isFavorited = it.id in imageFavoriteIds,
                                            onToggleFavorite = onToggleFavorite,
                                            onApplyReference = onApplyReference,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp),
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = it.model,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        PromptPreviewText(
                                            prompt = it.prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(it.prompt))
                                                toaster.show(
                                                    message = "Prompt copied to clipboard",
                                                    type = ToastType.Success
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.Copy01,
                                                contentDescription = "Copy prompt",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        filesManager.saveMessageImage(context, "file://${it.filePath}")
                                                        toaster.show(
                                                            message = context.getString(
                                                                R.string.imggen_page_image_saved_success
                                                            ),
                                                            type = ToastType.Success
                                                        )
                                                    } catch (e: Exception) {
                                                        toaster.show(
                                                            message = context.getString(
                                                                R.string.imggen_page_save_failed,
                                                                e.message
                                                            ),
                                                            type = ToastType.Error
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.FloppyDisk,
                                                contentDescription = stringResource(R.string.imggen_page_save),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { vm.deleteImage(it) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.Delete01,
                                                contentDescription = stringResource(R.string.imggen_page_delete),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showPreview) {
                            ImagePreviewDialog(
                                images = listOf(it.filePath),
                                labels = listOf(formatImageDateTime(it.timestamp)),
                                models = listOf(it.model),
                                prompts = listOf(it.prompt),
                                onUseAsReference = onUseReference,
                                onDismissRequest = { showPreview = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSpaceScreen(
    vm: ImgGenVM,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
) {
    val favorites by vm.imageFavorites.collectAsStateWithLifecycle()
    val imageFavoriteIds by vm.imageFavoriteIds.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val collections = settings.imageFavoriteCollections
    val onToggleFavorite = rememberImageFavoriteToggler(vm)
    var previewFlatIndex by remember { mutableStateOf<Int?>(null) }
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var renameCollectionTarget by remember { mutableStateOf<ImageFavoriteCollection?>(null) }
    var renameCollectionName by remember { mutableStateOf("") }
    var deleteCollectionTarget by remember { mutableStateOf<ImageFavoriteCollection?>(null) }
    val columns = settings.imageGallerySettings.spaceColumns.coerceIn(
        IMAGE_GALLERY_MIN_COLUMNS,
        IMAGE_GALLERY_MAX_COLUMNS,
    )
    val showThumbnailActions = columns <= IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS
    val ungroupedLabel = stringResource(R.string.imggen_page_ungrouped)
    val deletedCollectionLabel = stringResource(R.string.imggen_page_deleted_collection)

    val sectionKeys = remember(favorites, collections, ungroupedLabel, deletedCollectionLabel) {
        val ungroupedKey = "__ungrouped__"
        val knownIds = collections.map { it.id }.toSet()
        val keys = mutableListOf<Pair<String, String>>()
        keys += ungroupedKey to ungroupedLabel
        collections.forEach { c -> keys += c.id to c.name }
        favorites.mapNotNull { it.collectionId }
            .distinct()
            .filter { it !in knownIds }
            .forEach { orphanId -> keys += orphanId to deletedCollectionLabel }
        keys.filter { (key, _) ->
            favorites.any { item ->
                if (key == ungroupedKey) item.collectionId == null else item.collectionId == key
            }
        }
    }
    val flatFavorites = remember(favorites, sectionKeys) {
        val ungroupedKey = "__ungrouped__"
        sectionKeys.flatMap { (sectionKey, _) ->
            favorites.filter { item ->
                if (sectionKey == ungroupedKey) item.collectionId == null
                else item.collectionId == sectionKey
            }
        }
    }
    var allSectionsExpanded by remember { mutableStateOf(true) }
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(sectionKeys) {
        sectionKeys.forEach { (key, _) ->
            if (sectionExpanded[key] == null) sectionExpanded[key] = true
        }
        sectionExpanded.keys.retainAll(sectionKeys.map { it.first }.toSet())
    }

    fun flatIndexFor(item: ImageFavoriteListItem): Int =
        flatFavorites.indexOfFirst { it.favoriteId == item.favoriteId }

    if (favorites.isEmpty()) {
        SpaceEmptyState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "space-toolbar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.imggen_page_favorites_count, favorites.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            allSectionsExpanded = !allSectionsExpanded
                            sectionKeys.forEach { (key, _) ->
                                sectionExpanded[key] = allSectionsExpanded
                            }
                        },
                    ) {
                        Text(if (allSectionsExpanded) stringResource(R.string.imggen_page_collapse_all) else stringResource(R.string.imggen_page_expand_all))
                    }
                    TextButton(onClick = { showNewCollectionDialog = true }) {
                        Text(stringResource(R.string.imggen_page_new_collection))
                    }
                }
            }
            sectionKeys.forEach { (sectionKey, sectionTitle) ->
                val sectionItems = favorites.filter { item ->
                    if (sectionKey == "__ungrouped__") item.collectionId == null
                    else item.collectionId == sectionKey
                }
                if (sectionItems.isEmpty()) return@forEach
                val expanded = sectionExpanded[sectionKey] != false
                item(key = "space-header-$sectionKey") {
                    val managedCollection = collections.firstOrNull { it.id == sectionKey }
                    CollapsibleSectionHeader(
                        title = sectionTitle,
                        subtitle = stringResource(R.string.imggen_page_images_count, sectionItems.size),
                        expanded = expanded,
                        onExpandedChange = { sectionExpanded[sectionKey] = it },
                        onRename = managedCollection?.let { c ->
                            {
                                renameCollectionTarget = c
                                renameCollectionName = c.name
                            }
                        },
                        onDelete = managedCollection?.let { c ->
                            { deleteCollectionTarget = c }
                        },
                    )
                }
                if (expanded) {
                    item(key = "space-grid-$sectionKey") {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 4000.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            userScrollEnabled = false,
                        ) {
                            items(sectionItems, key = { it.favoriteId }) { item ->
                                SpaceFavoriteGridCell(
                                    item = item,
                                    isFavorited = item.image.id in imageFavoriteIds,
                                    showThumbnailActions = showThumbnailActions,
                                    collections = collections,
                                    onToggleFavorite = onToggleFavorite,
                                    onApplyReference = onApplyReference,
                                    onAssignCollection = { collectionId ->
                                        if (item.image.id > 0) {
                                            vm.setImageFavoriteCollection(item.image.id, collectionId)
                                        }
                                    },
                                    canAssignCollection = item.image.id > 0 && item.image.id in imageFavoriteIds,
                                    onPreview = {
                                        previewFlatIndex = flatIndexFor(item).takeIf { it >= 0 }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewFlatIndex?.let { startIndex ->
        if (flatFavorites.isNotEmpty()) {
            ImagePreviewDialog(
                images = flatFavorites.map { it.image.filePath },
                initialPage = startIndex,
                labels = flatFavorites.map { formatImageDateTime(it.image.timestamp) },
                models = flatFavorites.map { it.image.model },
                prompts = flatFavorites.map { it.image.prompt },
                onUseAsReference = onUseReference,
                onDismissRequest = { previewFlatIndex = null },
            )
        }
    }

    if (showNewCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showNewCollectionDialog = false },
            title = { Text(stringResource(R.string.imggen_page_new_collection)) },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text(stringResource(R.string.imggen_page_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newCollectionName.trim()
                        if (name.isNotBlank()) {
                            vm.addImageFavoriteCollection(name)
                            newCollectionName = ""
                            showNewCollectionDialog = false
                        }
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewCollectionDialog = false }) { Text(stringResource(R.string.imggen_page_cancel)) }
            },
        )
    }

    renameCollectionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameCollectionTarget = null },
            title = { Text(stringResource(R.string.imggen_page_rename_collection)) },
            text = {
                OutlinedTextField(
                    value = renameCollectionName,
                    onValueChange = { renameCollectionName = it },
                    label = { Text(stringResource(R.string.imggen_page_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameCollectionName.trim()
                        if (name.isNotBlank()) {
                            vm.renameImageFavoriteCollection(target.id, name)
                            renameCollectionTarget = null
                        }
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { renameCollectionTarget = null }) { Text(stringResource(R.string.imggen_page_cancel)) }
            },
        )
    }

    deleteCollectionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCollectionTarget = null },
            title = { Text(stringResource(R.string.imggen_page_delete_collection)) },
            text = {
                Text(stringResource(R.string.imggen_page_collection_delete_confirm, target.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteImageFavoriteCollection(target.id)
                        deleteCollectionTarget = null
                    },
                ) {
                    Text(stringResource(R.string.imggen_page_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCollectionTarget = null }) { Text(stringResource(R.string.imggen_page_cancel)) }
            },
        )
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onExpandedChange(!expanded) },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(HugeIcons.MoreVertical, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    onRename?.let { rename ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.imggen_page_rename)) },
                            onClick = {
                                showMenu = false
                                rename()
                            },
                        )
                    }
                    onDelete?.let { delete ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.imggen_page_delete_collection_action), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                delete()
                            },
                        )
                    }
                }
            }
        }
        IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SpaceFavoriteGridCell(
    item: ImageFavoriteListItem,
    isFavorited: Boolean,
    showThumbnailActions: Boolean,
    collections: List<ImageFavoriteCollection>,
    onToggleFavorite: (GeneratedImage) -> Unit,
    onApplyReference: (String) -> Unit,
    onAssignCollection: (String?) -> Unit,
    onPreview: () -> Unit,
    canAssignCollection: Boolean = true,
) {
    val image = item.image
    var showCollectionMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AsyncImage(
                model = File(image.filePath),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPreview() },
                contentScale = ContentScale.Crop,
            )
            if (showThumbnailActions) {
                ImageThumbnailActions(
                    image = image,
                    isFavorited = isFavorited,
                    onToggleFavorite = onToggleFavorite,
                    onApplyReference = onApplyReference,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = collections.firstOrNull { it.id == item.collectionId }?.name ?: stringResource(R.string.imggen_page_ungrouped),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canAssignCollection) {
                Box {
                    TextButton(onClick = { showCollectionMenu = true }) {
                        Text(stringResource(R.string.imggen_page_collection), style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = showCollectionMenu,
                        onDismissRequest = { showCollectionMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.imggen_page_ungrouped)) },
                            onClick = {
                                onAssignCollection(null)
                                showCollectionMenu = false
                            },
                        )
                        collections.forEach { collection ->
                            DropdownMenuItem(
                                text = { Text(collection.name) },
                                onClick = {
                                    onAssignCollection(collection.id)
                                    showCollectionMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = formatImageDateTime(image.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SpaceEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = HugeIcons.InLove,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.imggen_page_no_favorites),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GalleryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = HugeIcons.Image03,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.imggen_page_no_generated_images),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GroupedImageGallery(
    vm: ImgGenVM,
    groups: List<GeneratedImageGroup>,
    columns: Int,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
) {
    if (groups.isEmpty()) {
        GalleryEmptyState()
        return
    }
    val imageFavoriteIds by vm.imageFavoriteIds.collectAsStateWithLifecycle()
    val onToggleFavorite = rememberImageFavoriteToggler(vm)
    val groupKeys = remember(groups) { groups.map { "${it.prompt}:${it.timestamp}" } }
    var allGroupsExpanded by remember { mutableStateOf(true) }
    val groupExpanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(groupKeys) {
        groupKeys.forEach { key ->
            if (groupExpanded[key] == null) {
                groupExpanded[key] = true
            }
        }
        groupExpanded.keys.retainAll(groupKeys.toSet())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "gallery-group-toolbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        allGroupsExpanded = !allGroupsExpanded
                        groupKeys.forEach { groupExpanded[it] = allGroupsExpanded }
                    },
                ) {
                    Text(if (allGroupsExpanded) stringResource(R.string.imggen_page_collapse_all) else stringResource(R.string.imggen_page_expand_all))
                }
            }
        }
        items(groups, key = { "${it.prompt}:${it.timestamp}" }) { group ->
            val groupKey = "${group.prompt}:${group.timestamp}"
            val expanded = groupExpanded[groupKey] != false
            GroupedImageCard(
                group = group,
                columns = columns,
                expanded = expanded,
                onExpandedChange = { next ->
                    groupExpanded[groupKey] = next
                    allGroupsExpanded = groupKeys.all { groupExpanded[it] != false }
                },
                onDelete = vm::deleteImage,
                imageFavoriteIds = imageFavoriteIds,
                onToggleFavorite = onToggleFavorite,
                onUseReference = onUseReference,
                onApplyReference = onApplyReference,
            )
        }
    }
}

@Composable
private fun GroupedImageCard(
    group: GeneratedImageGroup,
    columns: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDelete: (GeneratedImage) -> Unit,
    imageFavoriteIds: Set<Int>,
    onToggleFavorite: (GeneratedImage) -> Unit,
    onUseReference: (String) -> Unit,
    onApplyReference: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current
    val isTemplateGroup = group.variants.isNotEmpty()
    val sections = if (isTemplateGroup) {
        group.variants
    } else {
        listOf(
            GeneratedImageVariantGroup(
                label = group.prompt,
                prompt = group.prompt,
                timestamp = group.timestamp,
                model = group.model,
                images = group.images,
            )
        )
    }
    val groupCopyText = if (isTemplateGroup) {
        group.variants.joinToString(separator = "\n") { it.prompt }
    } else {
        group.prompt
    }
    val groupMeta = if (isTemplateGroup) {
        stringResource(R.string.imggen_page_group_meta_variants, formatImageDate(group.timestamp), group.images.size, group.variants.size)
    } else {
        stringResource(R.string.imggen_page_group_meta, formatImageDate(group.timestamp), group.images.size)
    }
    var previewImages by remember { mutableStateOf<List<GeneratedImage>>(emptyList()) }
    var previewStartIndex by remember { mutableStateOf<Int?>(null) }
    val showThumbnailActions = columns <= IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    PromptPreviewText(
                        prompt = group.prompt,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    Text(
                        text = groupMeta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(groupCopyText))
                        toaster.show(message = "Prompt copied to clipboard", type = ToastType.Success)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(HugeIcons.Copy01, null, modifier = Modifier.size(16.dp))
                }
            }

            if (!expanded) {
                return@Column
            }

            sections.forEach { section ->
                if (isTemplateGroup) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            PromptPreviewText(
                                prompt = section.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                            )
                            Text(
                                text = stringResource(R.string.imggen_page_section_meta, formatImageDate(section.timestamp), section.model, section.images.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(section.prompt))
                                toaster.show(message = "Prompt copied to clipboard", type = ToastType.Success)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(HugeIcons.Copy01, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                section.images.chunked(columns).forEachIndexed { rowIndex, rowImages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowImages.forEachIndexed { columnIndex, image ->
                            val imageIndex = rowIndex * columns + columnIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                AsyncImage(
                                    model = File(image.filePath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            previewImages = section.images
                                            previewStartIndex = imageIndex
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                if (showThumbnailActions) {
                                    Surface(
                                        onClick = { onDelete(image) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = HugeIcons.Delete01,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    ImageThumbnailActions(
                                        image = image,
                                        isFavorited = image.id in imageFavoriteIds,
                                        onToggleFavorite = onToggleFavorite,
                                        onApplyReference = onApplyReference,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }
                        repeat(columns - rowImages.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    previewStartIndex?.let { startIndex ->
        ImagePreviewDialog(
            images = previewImages.map { it.filePath },
            initialPage = startIndex,
            labels = previewImages.map { formatImageDateTime(it.timestamp) },
            models = previewImages.map { it.model },
            prompts = previewImages.map { it.prompt },
            onUseAsReference = onUseReference,
            onDismissRequest = { previewStartIndex = null },
        )
    }
}

@Composable
private fun rememberImageFavoriteToggler(vm: ImgGenVM): (GeneratedImage) -> Unit {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    return remember(vm, toaster) {
        { image: GeneratedImage ->
            vm.toggleImageFavorite(
                image = image,
                onResult = { added ->
                    toaster.show(
                        message = if (added) context.getString(R.string.imggen_page_favorite_added) else context.getString(R.string.imggen_page_favorite_removed),
                        type = ToastType.Success,
                    )
                },
                onError = { error ->
                    toaster.show(
                        message = context.getString(R.string.imggen_page_favorite_failed, error.message ?: context.getString(R.string.imggen_page_unknown_error)),
                        type = ToastType.Error,
                    )
                },
            )
        }
    }
}

@Composable
private fun ImageThumbnailActions(
    image: GeneratedImage,
    isFavorited: Boolean,
    onToggleFavorite: (GeneratedImage) -> Unit,
    onApplyReference: (String) -> Unit,
    onRegenerate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onRegenerate?.let { regenerate ->
            RegenerateImageButton(onClick = regenerate)
        }
        ImageFavoriteButton(
            selected = isFavorited,
            onClick = { onToggleFavorite(image) },
        )
        ReferenceApplyButton(
            onClick = { onApplyReference(image.filePath) },
        )
    }
}

@Composable
private fun JobGenerationErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(message) { mutableStateOf(false) }
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RegenerateImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(30.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.96f),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = HugeIcons.Refresh01,
                contentDescription = "Regenerate",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ImageFavoriteButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(30.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        },
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = HugeIcons.Favourite,
                contentDescription = if (selected) "Remove favorite" else "Add favorite",
                tint = if (selected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ReferenceApplyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(30.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = HugeIcons.Add01,
                contentDescription = "Add as reference",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun PromptPreviewText(
    prompt: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val displayPrompt = prompt.ifBlank { stringResource(R.string.imggen_page_no_prompt) }
    var showPrompt by remember(displayPrompt) { mutableStateOf(false) }

    Text(
        text = displayPrompt,
        modifier = modifier.clickable { showPrompt = true },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text(stringResource(R.string.imggen_page_prompt_title)) },
            text = {
                Text(
                    text = displayPrompt,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrompt = false }) {
                    Text(stringResource(R.string.imggen_page_close))
                }
            },
        )
    }
}

@Composable
private fun RecycleBinScreen(
    images: List<GeneratedImage>,
    onDismiss: () -> Unit,
    onRestore: (GeneratedImage) -> Unit,
    onPermanentDelete: (GeneratedImage) -> Unit,
    onClear: (List<GeneratedImage>) -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var backProgress by remember { mutableStateOf(0f) }
    val animatedBackProgress by animateFloatAsState(
        targetValue = backProgress,
        label = "RecycleBinBackProgress",
    )

    PredictiveBackHandler {
        try {
            it.collect { backEvent ->
                backProgress = backEvent.progress
            }
            onDismiss()
        } finally {
            backProgress = 0f
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = animatedBackProgress.coerceIn(0f, 1f)
                translationX = size.width * progress
                alpha = 1f - 0.28f * progress
                scaleX = 1f - 0.04f * progress
                scaleY = 1f - 0.04f * progress
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.imggen_page_trash)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.imggen_page_close))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            enabled = images.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.imggen_page_trash_clear))
                        }
                    }
                )
            },
        ) { innerPadding ->
            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.imggen_page_trash_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(images, key = { it.id }) { image ->
                        RecycleBinItem(
                            image = image,
                            onRestore = onRestore,
                            onPermanentDelete = onPermanentDelete,
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.imggen_page_trash_clear_title)) },
            text = { Text(stringResource(R.string.imggen_page_trash_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear(images)
                        showClearConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.imggen_page_trash_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RecycleBinItem(
    image: GeneratedImage,
    onRestore: (GeneratedImage) -> Unit,
    onPermanentDelete: (GeneratedImage) -> Unit,
) {
    var showDeleteConfirm by remember(image.id) { mutableStateOf(false) }
    var showPreview by remember(image.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = File(image.filePath),
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPreview = true },
                contentScale = ContentScale.Crop,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatImageDateTime(image.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                PromptPreviewText(
                    prompt = image.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    text = image.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onRestore(image) }) {
                    Text(stringResource(R.string.imggen_page_restore))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = stringResource(R.string.imggen_page_delete_permanently),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showPreview) {
        ImagePreviewDialog(
            images = listOf(image.filePath),
            labels = listOf(formatImageDateTime(image.timestamp)),
            models = listOf(image.model),
            prompts = listOf(image.prompt),
            onDismissRequest = { showPreview = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.imggen_page_delete_permanently_title)) },
            text = { Text(stringResource(R.string.imggen_page_delete_permanently_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPermanentDelete(image)
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.imggen_page_delete_permanently), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun formatImageDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

private fun formatImageDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun copyImageToReferenceTemp(context: Context, imagePath: String): String {
    val source = File(imagePath.removePrefix("file://"))
    require(source.exists()) {
        "Image file does not exist"
    }

    val extension = source.extension.ifBlank { "png" }
    val target = File(context.appTempFolder, "imggen_ref_${Uuid.random()}.$extension")
    source.copyTo(target, overwrite = true)
    return target.absolutePath
}

@Composable
private fun SettingsBottomSheet(
    vm: ImgGenVM,
    settings: Settings,
    numberOfImages: Int,
    aspectRatio: ImageAspectRatio,
    scope: CoroutineScope,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val selectedModel = settings.findModelById(settings.imageGenerationModelId)
    val isGptImage2 = selectedModel?.modelId.equals("gpt-image-2", ignoreCase = true)
    val imageSettings = settings.imageGenerationSettings
    val updateImageSettings: ((ImageGenerationSettings) -> ImageGenerationSettings) -> Unit = { update ->
        scope.launch {
            vm.settingsStore.update { oldSettings ->
                oldSettings.copy(
                    imageGenerationSettings = update(oldSettings.imageGenerationSettings)
                )
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.imggen_page_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            FormItem(
                label = { Text(stringResource(R.string.imggen_page_model_selection)) },
                description = { Text(stringResource(R.string.imggen_page_model_selection_desc)) }
            ) {
                ModelSelector(
                    modelId = settings.imageGenerationModelId,
                    providers = settings.providers,
                    type = ModelType.IMAGE,
                    onlyIcon = false,
                    onSelect = { model -> vm.selectImageGenerationModel(model.id) }
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.imggen_page_generation_count)) },
                description = {
                    Text(stringResource(R.string.imggen_page_generation_count_desc))
                }
            ) {
                CompactStepper(
                    label = stringResource(R.string.imggen_page_unit_images),
                    value = numberOfImages,
                    onValueChange = vm::updateNumberOfImages,
                    min = 1,
                    max = MAX_GENERATION_IMAGES,
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.imggen_page_concurrent_requests)) },
                description = { Text(stringResource(R.string.imggen_page_concurrent_requests_desc)) },
            ) {
                CompactStepper(
                    label = stringResource(R.string.imggen_page_unit_items),
                    value = imageSettings.maxConcurrentJobs.coerceIn(1, MAX_CONCURRENT_IMAGE_GENERATION_JOBS_CAP),
                    onValueChange = { count ->
                        updateImageSettings {
                            it.copy(maxConcurrentJobs = count.coerceIn(1, MAX_CONCURRENT_IMAGE_GENERATION_JOBS_CAP))
                        }
                    },
                    min = 1,
                    max = MAX_CONCURRENT_IMAGE_GENERATION_JOBS_CAP,
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.imggen_page_streaming_preview)) },
                description = { Text(stringResource(R.string.imggen_page_streaming_preview_desc)) }
            ) {
                Switch(
                    checked = imageSettings.imageStreaming,
                    onCheckedChange = { enabled ->
                        updateImageSettings { it.copy(imageStreaming = enabled) }
                    },
                )
            }

            if (isGptImage2) {
                FormItem(
                    label = { Text(stringResource(R.string.imggen_page_gpt_image2_size)) },
                    description = { Text(stringResource(R.string.imggen_page_gpt_image2_size_desc)) }
                ) {
                    ImageSizeSelector(
                        selected = imageSettings.size,
                        customSize = imageSettings.customSize,
                        onSelect = { option ->
                            updateImageSettings { it.copy(size = option) }
                        },
                        onCustomSizeChange = { customSize ->
                            updateImageSettings { it.copy(customSize = customSize) }
                        },
                    )
                }

                FormItem(
                    label = { Text(stringResource(R.string.imggen_page_quality)) },
                    description = { Text(stringResource(R.string.imggen_page_quality_desc)) }
                ) {
                    CompactSegmentedOptions(
                        options = ImageQualityOption.entries,
                        selected = imageSettings.quality,
                        label = { option -> Text(option.label) },
                        onSelect = { option ->
                            updateImageSettings { it.copy(quality = option) }
                        },
                    )
                }

                FormItem(
                    label = { Text(stringResource(R.string.imggen_page_output_format)) },
                    description = { Text(stringResource(R.string.imggen_page_output_format_desc)) }
                ) {
                    CompactSegmentedOptions(
                        options = ImageOutputFormatOption.selectableEntries,
                        selected = imageSettings.outputFormat.selectableFormat(),
                        label = { option -> Text(option.label) },
                        onSelect = { option ->
                            updateImageSettings { it.copy(outputFormat = option) }
                        },
                    )
                }

                if (imageSettings.outputFormat.selectableFormat().supportsCompression) {
                    FormItem(
                        label = { Text(stringResource(R.string.imggen_page_output_compression)) },
                        description = { Text(stringResource(R.string.imggen_page_output_compression_desc)) }
                    ) {
                        CompactStepper(
                            label = "%",
                            value = imageSettings.outputCompression.coerceIn(0, 100),
                            min = 0,
                            max = 100,
                            step = 5,
                            onValueChange = { compression ->
                                updateImageSettings { it.copy(outputCompression = compression) }
                            },
                        )
                    }
                }

                FormItem(
                    label = { Text(stringResource(R.string.imggen_page_background)) },
                    description = { Text(stringResource(R.string.imggen_page_background_desc)) }
                ) {
                    CompactSegmentedOptions(
                        options = ImageBackgroundOption.entries,
                        selected = imageSettings.background,
                        label = { option -> Text(option.label) },
                        onSelect = { option ->
                            updateImageSettings { it.copy(background = option) }
                        },
                    )
                }

                FormItem(
                    label = { Text(stringResource(R.string.imggen_page_moderation)) },
                    description = { Text(stringResource(R.string.imggen_page_moderation_desc)) }
                ) {
                    CompactSegmentedOptions(
                        options = ImageModerationOption.entries,
                        selected = imageSettings.moderation,
                        label = { option -> Text(option.label) },
                        onSelect = { option ->
                            updateImageSettings { it.copy(moderation = option) }
                        },
                    )
                }
            } else {
                FormItem(
                    label = { Text(stringResource(R.string.imggen_page_aspect_ratio)) },
                    description = { Text(stringResource(R.string.imggen_page_aspect_ratio_desc)) }
                ) {
                    CompactSegmentedOptions(
                        options = ImageAspectRatio.entries,
                        selected = aspectRatio,
                        label = { ratio ->
                            Text(
                                stringResource(
                                    when (ratio) {
                                        ImageAspectRatio.SQUARE -> R.string.imggen_page_aspect_ratio_square
                                        ImageAspectRatio.LANDSCAPE -> {
                                            R.string.imggen_page_aspect_ratio_landscape
                                        }
                                        ImageAspectRatio.PORTRAIT -> R.string.imggen_page_aspect_ratio_portrait
                                    }
                                )
                            )
                        },
                        onSelect = vm::updateAspectRatio,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ImageColumnsMenuRow(
    columns: Int,
    onColumnsChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.imggen_page_columns),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                onColumnsChange((columns - 1).coerceIn(IMAGE_GALLERY_MIN_COLUMNS, IMAGE_GALLERY_MAX_COLUMNS))
            },
            enabled = columns > IMAGE_GALLERY_MIN_COLUMNS,
        ) {
            Icon(Lucide.Minus, contentDescription = stringResource(R.string.imggen_page_decrease_columns))
        }
        Text(
            text = columns.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        IconButton(
            onClick = {
                onColumnsChange((columns + 1).coerceIn(IMAGE_GALLERY_MIN_COLUMNS, IMAGE_GALLERY_MAX_COLUMNS))
            },
            enabled = columns < IMAGE_GALLERY_MAX_COLUMNS,
        ) {
            Icon(Lucide.Plus, contentDescription = stringResource(R.string.imggen_page_increase_columns))
        }
    }
}

@Composable
private fun CompactOptionPill(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactOptionPill(
            selected = false,
            text = "-",
            onClick = { onValueChange((value - step).coerceIn(min, max)) },
        )
        Text(
            text = "$value $label",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CompactOptionPill(
            selected = false,
            text = "+",
            onClick = { onValueChange((value + step).coerceIn(min, max)) },
        )
    }
}

@Composable
private fun <T> CompactSegmentedOptions(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> Unit,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                label(option)
            }
        }
    }
}

@Composable
private fun ImageSizeSelector(
    selected: ImageSizeOption,
    customSize: String,
    onSelect: (ImageSizeOption) -> Unit,
    onCustomSizeChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val customValidation = remember(customSize) { validateGptImage2Size(customSize) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = selected.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = selected.detail(customSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(260.dp),
            ) {
                ImageSizeOption.selectableEntries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(option.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    option.detail(customSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (selected == ImageSizeOption.CUSTOM) {
            OutlinedTextField(
                value = customSize,
                onValueChange = onCustomSizeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义尺寸") },
                placeholder = { Text("2048x1152") },
                singleLine = true,
                isError = customValidation.error != null,
                supportingText = {
                    Text(
                        customValidation.error?.label
                            ?: "最长边 <= 3840，宽高为 16 的倍数，比例 <= 3:1",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
        }
    }
}

private val ImageSizeOption.label: String
    get() = when (this) {
        ImageSizeOption.AUTO -> "自动"
        ImageSizeOption.SIZE_1024_1024 -> "方图"
        ImageSizeOption.SIZE_1536_1024 -> "横图"
        ImageSizeOption.SIZE_1024_1536 -> "竖图"
        ImageSizeOption.SIZE_2048_2048 -> "方图高清"
        ImageSizeOption.SIZE_2048_1152 -> "2K 横图"
        ImageSizeOption.SIZE_3840_2160 -> "4K 横图"
        ImageSizeOption.SIZE_2160_3840 -> "4K 竖图"
        ImageSizeOption.CUSTOM -> "自定义"
        ImageSizeOption.SIZE_1792_1024,
        ImageSizeOption.SIZE_2048_1024 -> "横图"
        ImageSizeOption.SIZE_1024_1792 -> "竖图"
    }

private fun ImageSizeOption.detail(customSize: String): String {
    return when (this) {
        ImageSizeOption.AUTO -> "auto"
        ImageSizeOption.CUSTOM -> customSize.ifBlank { "宽x高" }
        else -> apiValue ?: "auto"
    }
}

private val ImageQualityOption.label: String
    get() = when (this) {
        ImageQualityOption.AUTO -> "自动"
        ImageQualityOption.LOW -> "低"
        ImageQualityOption.MEDIUM -> "中"
        ImageQualityOption.HIGH -> "高"
    }

private val ImageOutputFormatOption.label: String
    get() = when (this) {
        ImageOutputFormatOption.PNG -> "PNG"
        ImageOutputFormatOption.JPEG -> "JPEG"
        ImageOutputFormatOption.WEBP -> "WebP"
        ImageOutputFormatOption.URL,
        ImageOutputFormatOption.B64_JSON -> "PNG"
    }

@Suppress("DEPRECATION")
private fun ImageOutputFormatOption.selectableFormat(): ImageOutputFormatOption {
    return when (this) {
        ImageOutputFormatOption.URL,
        ImageOutputFormatOption.B64_JSON -> ImageOutputFormatOption.PNG
        else -> this
    }
}

private val ImageBackgroundOption.label: String
    get() = when (this) {
        ImageBackgroundOption.AUTO -> "自动"
        ImageBackgroundOption.OPAQUE -> "不透明"
    }

private val ImageModerationOption.label: String
    get() = when (this) {
        ImageModerationOption.AUTO -> "自动"
        ImageModerationOption.LOW -> "低"
    }

private val ImageSizeValidationError.label: String
    get() = when (this) {
        ImageSizeValidationError.EMPTY -> "请输入尺寸，例如 2048x1152"
        ImageSizeValidationError.FORMAT -> "格式应为 宽x高，例如 2048x1152"
        ImageSizeValidationError.MAX_EDGE -> "最长边不能超过 3840px"
        ImageSizeValidationError.MULTIPLE_OF_16 -> "宽和高都必须是 16 的倍数"
        ImageSizeValidationError.ASPECT_RATIO -> "长短边比例不能超过 3:1"
        ImageSizeValidationError.TOTAL_PIXELS -> "总像素需在 655360 到 8294400 之间"
    }
