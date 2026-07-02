package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.WorkspaceFilesStorage
import me.rerere.rikkahub.workspace.peekWorkspaceFilesBaseDir
import me.rerere.workspace.WorkspaceGlobalLock
import java.io.File

class WorkspaceStorageMigrator(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val workspaceRepository: WorkspaceRepository,
    private val globalLock: WorkspaceGlobalLock,
) {
    suspend fun migrate(target: WorkspaceFilesStorage): MigrationResult = withContext(Dispatchers.IO) {
        val current = settingsStore.settingsFlow.value.workspaceFilesStorage
        if (current == target) {
            return@withContext MigrationResult.Noop
        }

        val currentBase = peekWorkspaceFilesBaseDir(context, current)
            ?: return@withContext MigrationResult.Failed(context.getString(R.string.workspace_storage_current_unavailable))
        val targetBase = peekWorkspaceFilesBaseDir(context, target)
            ?: return@withContext MigrationResult.Failed(context.getString(R.string.workspace_storage_external_unavailable))
        if (!targetBase.canWrite()) {
            return@withContext MigrationResult.Failed(context.getString(R.string.workspace_storage_not_writable))
        }

        globalLock.lock()
        val migratedRoots = mutableListOf<String>()
        try {
            val roots = workspaceRepository.listWorkspaceRoots()
            for (root in roots) {
                migrateOneWorkspace(
                    root = root,
                    currentBase = currentBase,
                    targetBase = targetBase,
                )
                migratedRoots += root
            }
            settingsStore.update { it.copy(workspaceFilesStorage = target) }
            MigrationResult.Success(roots.size)
        } catch (e: Exception) {
            rollbackMigrated(migratedRoots, currentBase, targetBase)
            MigrationResult.Failed(context.getString(R.string.workspace_storage_migrate_failed, e.message ?: ""))
        } finally {
            globalLock.unlock()
        }
    }

    private fun migrateOneWorkspace(
        root: String,
        currentBase: File,
        targetBase: File,
    ) {
        val srcFiles = File(File(currentBase, root), FILES_DIR_NAME)
        val dstFiles = File(File(targetBase, root), FILES_DIR_NAME)
        if (!srcFiles.exists()) {
            return
        }
        if (dstFiles.exists()) {
            dstFiles.deleteRecursively()
        }
        copyTree(srcFiles, dstFiles)
        if (!verifyEqual(srcFiles, dstFiles)) {
            dstFiles.deleteRecursively()
            throw IllegalStateException(context.getString(R.string.workspace_storage_verify_failed, root))
        }
        if (!srcFiles.deleteRecursively()) {
            throw IllegalStateException(context.getString(R.string.workspace_storage_source_remove_failed, root))
        }
    }

    private fun rollbackMigrated(
        migratedRoots: List<String>,
        currentBase: File,
        targetBase: File,
    ) {
        for (root in migratedRoots.asReversed()) {
            val srcOnTarget = File(File(targetBase, root), FILES_DIR_NAME)
            val dstOnCurrent = File(File(currentBase, root), FILES_DIR_NAME)
            if (!srcOnTarget.exists()) {
                continue
            }
            if (dstOnCurrent.exists()) {
                dstOnCurrent.deleteRecursively()
            }
            runCatching {
                copyTree(srcOnTarget, dstOnCurrent)
                if (verifyEqual(srcOnTarget, dstOnCurrent)) {
                    srcOnTarget.deleteRecursively()
                }
            }
        }
    }

    companion object {
        private const val FILES_DIR_NAME = "files"

        internal fun copyTree(source: File, destination: File) {
            source.walkTopDown().forEach { file ->
                val relative = file.relativeTo(source).path
                val target = if (relative.isEmpty()) destination else File(destination, relative)
                if (file.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                    if (file.canExecute()) {
                        target.setExecutable(true, false)
                    }
                }
            }
        }

        internal fun verifyEqual(source: File, destination: File): Boolean {
            if (!source.exists() && !destination.exists()) {
                return true
            }
            if (!source.exists() || !destination.exists()) {
                return false
            }
            val sourceFiles = source.walkTopDown().filter { it.isFile }.toList()
            val destFiles = destination.walkTopDown().filter { it.isFile }.toList()
            if (sourceFiles.size != destFiles.size) {
                return false
            }
            val destByRelative = destFiles.associateBy { it.relativeTo(destination).path }
            for (file in sourceFiles) {
                val relative = file.relativeTo(source).path
                val other = destByRelative[relative] ?: return false
                if (file.length() != other.length()) {
                    return false
                }
            }
            return true
        }
    }
}

sealed class MigrationResult {
    data object Noop : MigrationResult()
    data class Success(val count: Int) : MigrationResult()
    data class Failed(val message: String) : MigrationResult()
}