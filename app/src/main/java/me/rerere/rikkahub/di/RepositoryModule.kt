package me.rerere.rikkahub.di

import android.content.Context
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.HookRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.WorkspaceGitRepository
import me.rerere.rikkahub.data.repository.WorkspaceStorageMigrator
import me.rerere.rikkahub.domain.git.GetAssistantGitStatusUseCase
import me.rerere.rikkahub.domain.git.GetGitFileDiffUseCase
import me.rerere.rikkahub.workspace.resolveWorkspaceFilesBaseDir
import me.rerere.workspace.WorkspaceGlobalLock
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        HookRepository(get())
    }

    single {
        ConversationTagRepository(get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        MemoryTableRepository(get(), get(), get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single { WorkspaceGlobalLock() }

    single {
        val context: Context = get()
        val settingsStore: SettingsStore = get()
        val globalLock: WorkspaceGlobalLock = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            filesBaseDirProvider = {
                resolveWorkspaceFilesBaseDir(
                    context,
                    settingsStore.settingsFlow.value.workspaceFilesStorage,
                )
            },
            globalLock = globalLock,
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            ),
            // 同一份挂载表既用于 PRoot 的 -b 参数, 也用于文件工具的路径解析, 避免两处漂移
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single { WorkspaceGitRepository(get()) }
    single { GetAssistantGitStatusUseCase(get(), get()) }
    single { GetGitFileDiffUseCase(get(), get()) }

    single {
        WorkspaceStorageMigrator(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }
}
