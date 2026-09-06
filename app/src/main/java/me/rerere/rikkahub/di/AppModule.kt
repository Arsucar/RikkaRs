package me.rerere.rikkahub.di

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.sync.BackupTaskCoordinator
import me.rerere.rikkahub.service.ChatKeepAliveController
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.hooks.HookActionRegistry
import me.rerere.rikkahub.service.hooks.HookDispatcher
import me.rerere.rikkahub.service.hooks.ConversationTagHookCommitter
import me.rerere.rikkahub.service.hooks.ManageConversationTagsHookAction
import me.rerere.rikkahub.service.hooks.ProviderHookModelExecutor
import me.rerere.rikkahub.service.hooks.MemoryTableHookSyncCommitter
import me.rerere.rikkahub.service.hooks.SyncMemoryTableHookAction
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import me.rerere.rikkahub.ui.pages.imggen.ImgGenSession
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single { ProviderHookModelExecutor(settingsStore = get(), providerManager = get()) }
    single {
        ConversationTagHookCommitter(
            database = get(),
            hookDao = get<me.rerere.rikkahub.data.db.AppDatabase>().hookDao(),
            messageNodeDao = get<me.rerere.rikkahub.data.db.AppDatabase>().messageNodeDao(),
            tagDao = get<me.rerere.rikkahub.data.db.AppDatabase>().conversationTagDao(),
            tagRepository = get(),
        )
    }
    single {
        MemoryTableHookSyncCommitter(
            database = get(),
            memoryTableDao = get<me.rerere.rikkahub.data.db.AppDatabase>().memoryTableDao(),
            snapshotDao = get<me.rerere.rikkahub.data.db.AppDatabase>().memoryTableSnapshotDao(),
            hookDao = get<me.rerere.rikkahub.data.db.AppDatabase>().hookDao(),
            json = get(),
        )
    }
    single {
        HookActionRegistry(
            listOf(
                ManageConversationTagsHookAction(
                    tagRepository = get(),
                    committer = get(),
                ),
                SyncMemoryTableHookAction(
                    settingsStore = get(),
                    memoryTableRepository = get(),
                    hookDao = get<me.rerere.rikkahub.data.db.AppDatabase>().hookDao(),
                    committer = get(),
                    json = get(),
                ),
            )
        )
    }
    single {
        HookDispatcher(
            hookRepository = get(),
            modelExecutor = get<ProviderHookModelExecutor>(),
            actionRegistry = get(),
        )
    }
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    single { BackupTaskCoordinator(scope = get<AppScope>()) }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        ChatKeepAliveController(
            context = get(),
            settingsStore = get(),
        )
    }

    single {
        val context: android.content.Context = get()
        val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore = get()
        WorkspaceTerminalSessionManager(
            context = context,
            appScope = get(),
            filesBaseDirProvider = {
                me.rerere.rikkahub.workspace.resolveWorkspaceFilesBaseDir(
                    context,
                    settingsStore.settingsFlow.value.workspaceFilesStorage,
                )
            },
        )
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
            keepAliveController = get(),
        )
    }

    single {
        ChatToolFactory(
            json = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            memoryTableRepository = get(),
            generationHandler = get(),
            subagentHost = get(),
            json = get(),
            translationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            chatToolFactory = get(),
            mcpManager = get(),
            keepAliveController = get(),
            filesManager = get(),
            workspaceRepository = get(),
            folderRepository = get(),
            hookRepository = get(),
            hookDispatcher = get(),
            apiCallRecorder = get(),
            // [SemanticMemory Plugin]
            semanticMemoryTransformer = get(),
            semanticMemoryManager = get(),
        )
    }

    single {
        ImgGenSession(
            appScope = get(),
            settingsStore = get(),
            providerManager = get(),
            genMediaRepository = get(),
            filesManager = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
