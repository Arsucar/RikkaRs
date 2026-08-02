package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.resolveAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.resolveChatModelId
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.resolveEffectiveWorkspaceCwd
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.hasInputDraftReplyTarget
import me.rerere.rikkahub.ui.components.ai.completion.DefaultModelCompletionProvider
import me.rerere.rikkahub.ui.components.ai.completion.SlashCompletionProvider
import me.rerere.rikkahub.ui.components.ai.completion.PresetCompletionProvider
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.useCropLauncher
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.HorizontalGestureExclusionState
import me.rerere.rikkahub.ui.context.LocalHorizontalGestureExclusionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.resolveChatFileUploadMetadata
import me.rerere.rikkahub.utils.isAllowedFileType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val inputDraftLoading by vm.inputDraftLoading.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val toaster = LocalToaster.current
    val moveToTrashSuccess = stringResource(R.string.assistant_page_memory_table_move_to_trash_success)
    val moveToTrashError = stringResource(R.string.assistant_page_memory_table_move_to_trash_error)
    val checkpointRecoveryGeneric = stringResource(R.string.chat_page_checkpoint_recovered)
    val resources = LocalResources.current
    val checkpointRecoveryHint by vm.checkpointRecoveryHint.collectAsStateWithLifecycle()

    // #220: show recovery hint once after hydrate loads a mid-generation checkpoint
    LaunchedEffect(checkpointRecoveryHint) {
        val hint = checkpointRecoveryHint ?: return@LaunchedEffect
        val message = if (hint.checkpointStep != null) {
            resources.getString(
                R.string.chat_page_checkpoint_recovered_with_step,
                hint.checkpointStep,
            )
        } else {
            checkpointRecoveryGeneric
        }
        toaster.show(message = message, type = ToastType.Info)
        vm.consumeCheckpointRecoveryHintUi()
    }

    // #89: 右侧对话级记忆表抽屉。Compose 无原生右侧抽屉，用 RTL 包裹 ModalNavigationDrawer 实现，
    // drawerContent 与主内容都翻回 LTR 防止整页镜像。
    val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val horizontalGestureExclusionState = remember { HorizontalGestureExclusionState() }
    val memoryTableDocuments by vm.memoryTableDocuments.collectAsStateWithLifecycle()
    val memoryTableTemplates by vm.memoryTableTemplates.collectAsStateWithLifecycle()
    val contextPreviewState by vm.contextPreviewState.collectAsStateWithLifecycle()
    val hookHistoryState by vm.hookHistoryState.collectAsStateWithLifecycle()
    val hookPreviewState by vm.hookPreviewState.collectAsStateWithLifecycle()
    val hookManualRunState by vm.hookManualRunState.collectAsStateWithLifecycle()
    val gitStatusState by vm.gitStatusState.collectAsStateWithLifecycle()
    val gitDiffState by vm.gitDiffState.collectAsStateWithLifecycle()
    val gitStatusWorkspaceId by vm.gitStatusWorkspaceId.collectAsStateWithLifecycle()
    val conversationTags by vm.conversationTags.collectAsStateWithLifecycle()
    val currentAssistant = remember(setting.assistants, conversation.assistantId) {
        setting.assistants.firstOrNull { it.id == conversation.assistantId }
    }
    val currentAssistantWorkspaceCwd = currentAssistant?.let { assistant ->
        resolveEffectiveWorkspaceCwd(conversation, assistant)
    }
    val configuredHooks = remember(setting.assistants, conversation.assistantId) {
        setting.assistants.firstOrNull { it.id == conversation.assistantId }?.hooks.orEmpty()
    }
    val modelNames = remember(setting.providers) {
        setting.providers.flatMap { it.models }.associate { model ->
            model.id to (model.displayName.ifBlank { model.modelId })
        }
    }

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // #89: 右抽屉打开时 back 键先关右抽屉
    BackHandler(enabled = rightDrawerState.isOpen) {
        scope.launch {
            rightDrawerState.close()
        }
    }

    // Hide keyboard when drawer is open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.messageNodes.lastIndex + 10)
            }
            vm.chatListInitialized = true
        }
    }

    // #89: 用 RTL 包裹的 ModalNavigationDrawer 承载右侧对话级记忆表抽屉。
    // 两种屏（大屏/小屏）都由这个右抽屉包裹整体，保证都能开。
    // 全宽方向分流手势层：内容区任意位置从右向左拖即打开右抽屉（与左抽屉全宽右滑对称），
    // 彻底绕开国产 ROM 屏幕右缘的系统返回手势区。只接管“向左拖”，其余（向右拖/垂直滚动/点击）
    // 一律不 consume，事件穿透给下层（左抽屉右滑打开、内容点击/滚动照常）。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(rightDrawerState, drawerState) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    // 用 Initial pass 读事件：父节点先于子节点拿到，才能在内层左抽屉/内容
                    // 的拖拽检测之前拦截“向左拖”。Main pass 是子先父后，会被内层抢先消费，
                    // 导致父层手势层完全收不到（表现为“完全没反应”）。
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var totalX = 0f
                    var totalY = 0f
                    var decided = false
                    var claim = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) break
                        val pc = change.positionChange()
                        totalX += pc.x
                        totalY += pc.y
                        if (!decided && (kotlin.math.abs(totalX) > slop || kotlin.math.abs(totalY) > slop)) {
                            decided = true
                            // 仅当水平向左拖、两个抽屉关闭且起点不在可横向滚动区域时接管手势
                            claim = shouldClaimRightDrawerGesture(
                                totalX = totalX,
                                totalY = totalY,
                                touchSlop = slop,
                                drawersClosed = !rightDrawerState.isOpen && !drawerState.isOpen,
                                gestureExcluded = horizontalGestureExclusionState.isExcluded(down.id.value),
                            )
                        }
                        if (claim) {
                            // 在 Initial pass 消费，子节点（左抽屉/内容）后续拿不到该事件
                            change.consume()
                        }
                    }
                    if (claim && totalX < -slop * 2) {
                        scope.launch { rightDrawerState.open() }
                    }
                }
            }
    ) {
    CompositionLocalProvider(
        LocalHorizontalGestureExclusionState provides horizontalGestureExclusionState,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        ModalNavigationDrawer(
            drawerState = rightDrawerState,
            // 打开后可滑动关闭；打开动作改用右缘手势条（见下方 Box），因为内层左抽屉会拦截关闭态的水平拖拽
            gesturesEnabled = rightDrawerState.isOpen,
            drawerContent = {
                // 宽度与左侧抽屉一致（300dp），圆角沿用 ModalDrawerSheet 默认 shape
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp)
                ) {
                    // 内容翻回 LTR，避免整块镜像
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ConversationDrawerContent(
                            drawerOpen = rightDrawerState.isOpen,
                            documents = memoryTableDocuments,
                            templates = memoryTableTemplates,
                            conversationId = conversation.id.toString(),
                            assistantId = conversation.assistantId.toString(),
                            assistantWorkspaceId = currentAssistant?.workspaceId?.toString(),
                            assistantWorkspaceCwd = currentAssistantWorkspaceCwd,
                            isolationEnabled = conversation.memoryTableIsolation,
                            onIsolationChange = { enabled ->
                                vm.setMemoryTableIsolation(enabled)
                            },
                            onSyncToConversation = { document ->
                                vm.syncMemoryTableDocumentToConversation(document) { result ->
                                    result.onSuccess {
                                        toaster.show("已同步到对话级", type = ToastType.Success)
                                    }.onFailure {
                                        toaster.show(it.message ?: "同步失败", type = ToastType.Error)
                                    }
                                }
                            },
                            onSaveConversationDocument = { document ->
                                vm.upsertConversationMemoryTableDocument(document) { result ->
                                    result.onSuccess {
                                        toaster.show("已保存", type = ToastType.Success)
                                    }.onFailure {
                                        toaster.show(it.message ?: "保存失败", type = ToastType.Error)
                                    }
                                }
                            },
                            onCreateConversationDocument = { templateId ->
                                vm.upsertConversationMemoryTableDocument(
                                    me.rerere.rikkahub.data.model.MemoryTableDocument(
                                        templateId = templateId,
                                        scopeType = me.rerere.rikkahub.data.model.MemoryTableScopeType.CONVERSATION,
                                        scopeId = conversation.id.toString(),
                                    )
                                ) { result ->
                                    result.onSuccess {
                                        toaster.show("已创建对话级记忆表", type = ToastType.Success)
                                    }.onFailure {
                                        toaster.show(it.message ?: "创建失败", type = ToastType.Error)
                                    }
                                }
                            },
                            onDeleteDocument = { documentId ->
                                vm.deleteMemoryTableDocument(documentId) { result ->
                                    result.onSuccess {
                                        toaster.show(moveToTrashSuccess, type = ToastType.Success)
                                    }.onFailure {
                                        toaster.show(moveToTrashError, type = ToastType.Error)
                                    }
                                }
                            },
                            onSetFollow = { documentId, follow ->
                                vm.setMemoryTableDocumentFollow(documentId, follow) { result ->
                                    result.onSuccess {
                                        toaster.show(
                                            if (follow) "已恢复跟随助手级" else "已断开跟随，可独立编辑",
                                            type = ToastType.Success,
                                        )
                                    }.onFailure {
                                        toaster.show(it.message ?: "操作失败", type = ToastType.Error)
                                    }
                                }
                            },
                            contextPreviewState = contextPreviewState,
                            onLoadContextPreview = vm::loadContextPreview,
                            onClearContextPreview = vm::clearContextPreview,
                            hookHistoryState = hookHistoryState,
                            hookPreviewState = hookPreviewState,
                            hookManualRunState = hookManualRunState,
                            hooks = configuredHooks,
                            conversationTags = conversationTags,
                            modelNames = modelNames,
                            onPreviewHook = vm::previewMemoryTableHook,
                            onApplyPreview = vm::applyMemoryTableHookPreview,
                            onRunHook = vm::runMemoryTableHookNow,
                            onRetryExecution = vm::retryMemoryTableHookExecution,
                            gitStatusState = gitStatusState,
                            gitStatusWorkspaceId = gitStatusWorkspaceId,
                            gitDiffState = gitDiffState,
                            onLoadGitStatus = {
                                vm.loadGitStatus(
                                    workspaceId = currentAssistant?.workspaceId?.toString(),
                                    workspaceCwd = currentAssistantWorkspaceCwd,
                                )
                            },
                            // #180: Git 详情页手动刷新强制全量重载，忽略缓存
                            onRefreshGitStatus = {
                                vm.loadGitStatus(
                                    workspaceId = currentAssistant?.workspaceId?.toString(),
                                    workspaceCwd = currentAssistantWorkspaceCwd,
                                    forceRefresh = true,
                                )
                            },
                            onLoadGitDiff = { path, section ->
                                currentAssistant?.workspaceId?.toString()?.let { workspaceId ->
                                    vm.loadGitDiff(
                                        workspaceId = workspaceId,
                                        path = path,
                                        section = section,
                                        workspaceCwd = currentAssistantWorkspaceCwd,
                                    )
                                }
                            },
                            onClearGitDiff = vm::clearGitDiff,
                            onNavigateWorkspaceBinding = {
                                scope.launch { rightDrawerState.close() }
                                navController.navigate(Screen.AssistantDetail(conversation.assistantId.toString()))
                            },
                        )
                    }
                }
            },
        ) {
            // 主内容翻回 LTR
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                when {
                    isBigScreen -> {
                        PermanentNavigationDrawer(
                            drawerContent = {
                                ChatDrawerContent(
                                    navController = navController,
                                    current = conversation,
                                    vm = vm,
                                    settings = setting
                                )
                            }
                        ) {
                            ChatPageContent(
                                inputState = inputState,
                                loadingJob = loadingJob,
                                processingStatus = processingStatus,
                                setting = setting,
                                conversation = conversation,
                                drawerState = drawerState,
                                navController = navController,
                                vm = vm,
                                chatListState = chatListState,
                                enableWebSearch = enableWebSearch,
                                currentChatModel = currentChatModel,
                                inputDraftLoading = inputDraftLoading,
                                bigScreen = true,
                                errors = errors,
                                onDismissError = { vm.dismissError(it) },
                                onClearAllErrors = { vm.clearAllErrors() },
                            )
                        }
                    }

                    else -> {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ChatDrawerContent(
                                    navController = navController,
                                    current = conversation,
                                    vm = vm,
                                    settings = setting
                                )
                            }
                        ) {
                            ChatPageContent(
                                inputState = inputState,
                                loadingJob = loadingJob,
                                processingStatus = processingStatus,
                                setting = setting,
                                conversation = conversation,
                                drawerState = drawerState,
                                navController = navController,
                                vm = vm,
                                chatListState = chatListState,
                                enableWebSearch = enableWebSearch,
                                currentChatModel = currentChatModel,
                                inputDraftLoading = inputDraftLoading,
                                bigScreen = false,
                                errors = errors,
                                onDismissError = { vm.dismissError(it) },
                                onClearAllErrors = { vm.clearAllErrors() },
                            )
                        }
                        BackHandler(drawerState.isOpen) {
                            scope.launch { drawerState.close() }
                        }
                    }
                }
                // #89: 右抽屉打开时按返回先关右抽屉。放在主内容作用域内（深层、后注册），
                // 与左抽屉的深层 BackHandler 对称，避免在嵌套抽屉结构中被盖过而漏到 Activity 退出。
                BackHandler(enabled = rightDrawerState.isOpen) {
                    scope.launch { rightDrawerState.close() }
                }
            }
        }
    }
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    inputDraftLoading: Boolean,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val skillManager: SkillManager = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val assistant = setting.resolveAssistant(conversation)
    var showFilesSheet by remember { mutableStateOf(false) }
    val unknownModelName = stringResource(R.string.chat_input_default_model_unknown)
    val defaultModelName = currentChatModel?.let { model ->
        model.displayName.ifBlank { model.modelId }.ifBlank { unknownModelName }
    }
    val defaultModelDetail = defaultModelName?.let { modelName ->
        stringResource(R.string.chat_input_default_model_command_detail, modelName)
    } ?: stringResource(R.string.chat_input_default_model_unavailable)

    val completionProviders = remember(
        assistant.id,
        assistant.workspaceId,
        resolveEffectiveWorkspaceCwd(conversation, assistant),
        workspaceRepository,
        assistant.enabledSkills,
        setting.presets,
        skillManager,
        currentChatModel,
        defaultModelDetail,
        unknownModelName,
    ) {
        buildList {
            // Add workspace file completion (@)
            assistant.workspaceId?.let { workspaceId ->
                add(
                    WorkspaceCompletionProvider(
                        workspaceId = workspaceId.toString(),
                        repository = workspaceRepository,
                        currentCwd = resolveEffectiveWorkspaceCwd(conversation, assistant),
                    )
                )
            }
            // Add skill completion (/)
            if (assistant.enabledSkills.isNotEmpty()) {
                add(
                    SlashCompletionProvider(
                        enabledSkills = assistant.enabledSkills,
                        assistantId = assistant.id,
                        skillManager = skillManager,
                    )
                )
            }
            add(
                DefaultModelCompletionProvider(
                    model = currentChatModel,
                    detail = defaultModelDetail,
                    unknownModelName = unknownModelName,
                )
            )
            if (setting.presets.isNotEmpty()) {
                add(PresetCompletionProvider(setting.presets))
            }
        }
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    // #181: 编辑态草稿生成成功后，用现有 Toaster 提示可基于原内容继续修改
    val inputDraftEditSuccessMsg = stringResource(R.string.input_draft_edit_success)
    LaunchedEffect(Unit) {
        vm.inputDraftSuccessFlow.collect {
            toaster.show(message = inputDraftEditSuccessMsg, type = ToastType.Success)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    }
                )
            },
            bottomBar = {
                ChatInput(
                    state = inputState,
                    loading = loadingJob != null,
                    settings = setting,
                    assistant = assistant,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    chatModelId = setting.resolveChatModelId(conversation),
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    enableSearch = enableWebSearch,
                    inputDraftLoading = inputDraftLoading,
                    inputDraftEnabled = hasInputDraftReplyTarget(
                        latestMessageRole = conversation.currentMessages.lastOrNull()?.role,
                        mainGenerationActive = loadingJob != null,
                    ),
                    onGenerateInputDraft = {
                        vm.generateInputDraft(
                            conversation = conversation,
                            userInstruction = inputState.textContent.text.toString().trim(),
                        )
                    },
                    onCancelInputDraft = vm::cancelInputDraft,
                    onToggleSearch = {
                        vm.toggleWebSearch()
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        vm.finishInputDraft()
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents())
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.messageNodes.lastIndex + 10)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        vm.finishInputDraft()
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.messageNodes.lastIndex + 10)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = {
                        vm.setChatModel(model = it)
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                }
                            )
                        )
                    },
                    onUpdateSearchService = { index ->
                        vm.updateSettings(
                            setting.copy(
                                searchServiceSelected = index
                            )
                        )
                    },
                    onMoreClick = {
                        showFilesSheet = true
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onToggleHidden = {
                    vm.toggleMessageHidden(it.id)
                },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(
                        conversation.copy(
                            messageNodes = conversation.messageNodes.map { node ->
                                if (node.id == newNode.id) {
                                    newNode
                                } else {
                                    node
                                }
                            }
                        ))
                    vm.saveConversationAsync()
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onTranslate = { message, locale ->
                    vm.translateMessage(message, locale)
                },
                onClearTranslation = { message ->
                    vm.clearTranslationField(message.id)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.requestScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onToggleFavorite = { node ->
                    vm.toggleMessageFavorite(node)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                    vm.saveConversationAsync()
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val (_, launchCameraCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            dismissAll()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                inputState.addImages(filesManager.createChatFilesByContents(listOf(cameraOutputUri!!)))
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                dismissAll()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onLaunchCamera: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    var preCropTempFile by remember { mutableStateOf<File?>(null) }
    val (_, launchImageCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            dismissAll()
        },
        onCleanup = {
            preCropTempFile?.delete()
            preCropTempFile = null
        }
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (setting.displaySetting.skipCropImage) {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
                    dismissAll()
                } else if (selectedUris.size == 1) {
                    val tempFile = File(context.appTempFolder, "pick_temp_${System.currentTimeMillis()}.jpg")
                    runCatching {
                        val source = selectedUris.first()
                        // HEIF/HEIC（尤其 HDR HEIF）交给 UCrop 前先解码转为 JPEG，规避裁剪解码失败
                        val converted = ImageUtils.isHeifImage(context, source) &&
                            ImageUtils.convertHeifToJpeg(context, source, tempFile)
                        if (!converted) {
                            context.contentResolver.openInputStream(source)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        preCropTempFile = tempFile
                        launchImageCrop(tempFile.toUri())
                    }.onFailure {
                        Log.e("ImagePickButton", "Failed to copy image to temp, falling back", it)
                        launchImageCrop(selectedUris.first())
                    }
                } else {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
                    dismissAll()
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addVideos(filesManager.createChatFilesByContents(selectedUris))
                dismissAll()
            }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addAudios(filesManager.createChatFilesByContents(selectedUris))
                dismissAll()
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val metadata = resolveChatFileUploadMetadata(
                        fileName = filesManager.getFileNameFromUri(uri),
                        mimeType = filesManager.getFileMimeType(uri),
                    )
                    if (!isAllowedFileType(metadata.fileName, metadata.mimeType)) {
                        toaster.show(
                            resources.getString(
                                R.string.chat_input_unsupported_file_type,
                                metadata.fileName,
                            ),
                            type = ToastType.Error,
                        )
                        return@mapNotNull null
                    }
                    val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                        ?: run {
                            toaster.show(
                                context.getString(R.string.chat_input_file_read_failed, metadata.fileName),
                                type = ToastType.Error
                            )
                            return@mapNotNull null
                        }
                    UIMessagePart.Document(
                        url = localUri.toString(),
                        fileName = metadata.fileName,
                        mime = metadata.mimeType,
                    )
                }
                if (documents.isNotEmpty()) {
                    inputState.addFiles(documents)
                    dismissAll()
                }
            }
        }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = {
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { assistant ->
                            if (assistant.id == it.id) {
                                it
                            } else {
                                assistant
                            }
                        }
                    )
                )
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = onLaunchCamera,
            onPickImage = { imagePickerLauncher.launch("image/*") },
            onPickVideo = { videoPickerLauncher.launch("video/*") },
            onPickAudio = { audioPickerLauncher.launch("audio/*") },
            onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(HugeIcons.Menu03, "Messages")
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    val assistant = settings.resolveAssistant(conversation)
                    val model = settings.getCurrentChatModel(conversation)
                    val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            IconButton(
                onClick = {
                    onNewChat()
                }
            ) {
                Icon(HugeIcons.MessageAdd01, "New Message")
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}
