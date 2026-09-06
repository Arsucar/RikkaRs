package me.rerere.rikkahub.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.tools.normalizeTrustedWriteRoot
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.workspace.SkillsPrivateEntryAssistant
import me.rerere.rikkahub.data.workspace.resolveSkillsPrivateEntryAssistant
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.uuid.Uuid

class WorkspaceRepository(
    private val dao: WorkspaceDAO,
    private val manager: WorkspaceManager,
    private val rootfsInstaller: RootfsInstaller,
    private val settingsStore: SettingsStore,
    private val skillManager: SkillManager,
) {
    fun listFlow(): Flow<List<WorkspaceEntity>> = dao.listFlow()

    fun managerFilesBaseDir() = manager.filesBaseDir()

    suspend fun listWorkspaceRoots(): List<String> = dao.getAll().map { it.root }

    suspend fun checkIntegrity() = withContext(Dispatchers.IO) {
        val workspaces = dao.getAll()
        for (workspace in workspaces) {
            val dir = manager.workspaceDir(workspace.root)
            if (!dir.exists()) {
                // 目录缺失时不删除记录(例如恢复备份后工作区文件未随数据库一起恢复),
                // 仅标记为 BROKEN 以保留记录与助手绑定, 避免误删用户工作区
                Log.w(TAG, "Workspace directory missing, marking as broken: id=${workspace.id}, root=${workspace.root}")
                if (workspace.shellStatus != WorkspaceShellStatus.BROKEN.name) {
                    updateShellState(workspace.id, WorkspaceShellStatus.BROKEN.name)
                }
                continue
            }
            val statusName = workspace.shellStatus
            if ((statusName == WorkspaceShellStatus.READY.name || statusName == WorkspaceShellStatus.INSTALLING.name)
                && !manager.hasRootfs(workspace.root)
            ) {
                Log.w(TAG, "Rootfs missing, resetting shell status: id=${workspace.id}")
                updateShellState(workspace.id, WorkspaceShellStatus.DISABLED.name)
            }
        }
    }

    suspend fun getById(id: String): WorkspaceEntity? = dao.getById(id)

    suspend fun create(name: String): WorkspaceEntity {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()
        val finalName = name.trim().ifBlank { "Workspace" }
        require(!isNameTaken(finalName, excludeId = null)) {
            "Workspace name already exists: $finalName"
        }
        val workspace = WorkspaceEntity(
            id = id,
            name = finalName,
            root = id,
            createdAt = now,
            updatedAt = now,
            lastAccessAt = null,
        )
        manager.ensureWorkspace(workspace.root)
        dao.upsert(workspace)
        return workspace
    }

    suspend fun rename(id: String, name: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        val finalName = name.trim().ifBlank { workspace.name }
        require(!isNameTaken(finalName, excludeId = id)) {
            "Workspace name already exists: $finalName"
        }
        dao.upsert(
            workspace.copy(
                name = finalName,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** 名字是否已被其他 workspace 占用（trim 后精确匹配，排除 [excludeId] 自身） */
    suspend fun isNameTaken(name: String, excludeId: String?): Boolean {
        val target = name.trim()
        return dao.getAll().any { it.id != excludeId && it.name.trim() == target }
    }

    suspend fun setToolApproval(id: String, toolName: String, needsApproval: Boolean): Boolean {
        val workspace = dao.getById(id) ?: return false
        val overrides = workspace.toolApprovalOverrides() + (toolName to needsApproval)
        dao.upsert(
            workspace.copy(
                toolApprovals = JsonInstant.encodeToString(overrides),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /**
     * Add a trusted write root prefix for this workspace (#258).
     * Returns false if workspace missing or root invalid / already present after normalize.
     */
    suspend fun addTrustedWriteRoot(id: String, root: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        val normalized = normalizeTrustedWriteRoot(root) ?: return false
        val current = workspace.trustedWriteRootList()
        if (current.any { it == normalized }) return true
        val updated = current + normalized
        dao.upsert(
            workspace.copy(
                trustedWriteRoots = JsonInstant.encodeToString(updated),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** Remove a trusted write root prefix (#258). Matches by normalized equality. */
    suspend fun removeTrustedWriteRoot(id: String, root: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        val normalized = normalizeTrustedWriteRoot(root) ?: root.trimEnd('/')
        val current = workspace.trustedWriteRootList()
        val updated = current.filterNot {
            normalizeTrustedWriteRoot(it) == normalized || it == root
        }
        if (updated.size == current.size) return false
        dao.upsert(
            workspace.copy(
                trustedWriteRoots = JsonInstant.encodeToString(updated),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun installRootfs(
        id: String,
        url: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean {
        val workspace = dao.getById(id) ?: return false
        updateShellState(workspace, WorkspaceShellStatus.INSTALLING.name)
        try {
            // runInterruptible 让协程取消转成线程中断, 打断 install 内阻塞的下载/解压循环
            runInterruptible(Dispatchers.IO) {
                rootfsInstaller.install(workspace.root, url, onProgress)
            }
            updateShellState(workspace, WorkspaceShellStatus.READY.name)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw e
        } catch (e: InterruptedException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw CancellationException("Rootfs install cancelled").also { it.initCause(e) }
        } catch (e: Throwable) {
            Log.e(TAG, "installRootfs failed: workspace=${workspace.id}, root=${workspace.root}, url=$url", e)
            updateShellState(workspace, WorkspaceShellStatus.BROKEN.name)
            throw e
        }
    }

    /**
     * 解析 /skills_private 入口助手 (0/1/many 绑定策略).
     * [selectedAssistantId] 仅在多绑时由 UI 传入; 不做过期缓存.
     */
    fun resolveSkillsPrivateEntry(
        workspaceId: String,
        selectedAssistantId: Uuid? = null,
    ): SkillsPrivateEntryAssistant =
        resolveSkillsPrivateEntryAssistant(
            settings = settingsStore.settingsFlow.value,
            workspaceId = workspaceId,
            selectedAssistantId = selectedAssistantId,
        )

    suspend fun listFiles(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        skillsPrivateAssistantId: Uuid? = null,
    ): List<WorkspaceFileEntry> = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext emptyList()
        manager.ensureWorkspace(workspace.root)
        val extra = skillsPrivateExtraMounts(id, area, path, skillsPrivateAssistantId)
        manager.listFiles(workspace.root, path, area, extraBindMounts = extra)
    }

    suspend fun readText(
        id: String,
        path: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
        skillsPrivateAssistantId: Uuid? = null,
    ): String = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        val extra = skillsPrivateExtraMounts(id, area, path, skillsPrivateAssistantId)
        manager.readText(
            root = workspace.root,
            path = path,
            area = area,
            extraBindMounts = extra,
        )
    }

    suspend fun writeText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        requireWritableArea(area)
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.writeText(root = workspace.root, path = path, text = text, overwrite = overwrite, area = area)
    }

    /**
     * 读取文本用于应用内预览/编辑, 支持两个存储区.
     * FILES 区走 [WorkspaceManager.readText] (自带大小保护); LINUX 区通过 exportFile 读入内存,
     * 因此这里对 LINUX 区显式做大小限制, 避免大文件撑爆内存.
     */
    suspend fun readTextForPreview(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        skillsPrivateAssistantId: Uuid? = null,
    ): String = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        when (area) {
            WorkspaceStorageArea.FILES -> manager.readText(workspace.root, path)
            WorkspaceStorageArea.LINUX -> {
                val extra = skillsPrivateExtraMounts(id, area, path, skillsPrivateAssistantId)
                val size = manager.fileSize(workspace.root, path, area, extraBindMounts = extra)
                require(size <= MAX_PREVIEW_BYTES) {
                    "文件过大, 无法预览 (${size} bytes)"
                }
                ByteArrayOutputStream().use { out ->
                    manager.exportFile(workspace.root, path, area, out, extraBindMounts = extra)
                    out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    suspend fun importFile(
        id: String,
        area: WorkspaceStorageArea,
        destinationPath: String,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        requireWritableArea(area)
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.importFile(workspace.root, destinationPath, area, fileName, inputStream)
    }

    suspend fun fileSize(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        skillsPrivateAssistantId: Uuid? = null,
    ): Long = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        val extra = skillsPrivateExtraMounts(id, area, path, skillsPrivateAssistantId)
        manager.fileSize(workspace.root, path, area, extraBindMounts = extra)
    }

    suspend fun resolveFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.resolveFile(workspace.root, path, area)
    }

    suspend fun exportFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        outputStream: OutputStream,
        skillsPrivateAssistantId: Uuid? = null,
    ) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        val extra = skillsPrivateExtraMounts(id, area, path, skillsPrivateAssistantId)
        manager.exportFile(workspace.root, path, area, outputStream, extraBindMounts = extra)
    }

    /** 按 Rootfs 内绝对路径读取文件大小, 支持 /workspace、bind mount 与 Rootfs 内部路径 */
    suspend fun rootfsFileSize(
        id: String,
        path: String,
    ): Long = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.rootfsFileSize(workspace.root, path)
    }

    /** 按 Rootfs 内绝对路径导出文件内容, 支持 /workspace、bind mount 与 Rootfs 内部路径 */
    suspend fun exportRootfsFile(
        id: String,
        path: String,
        outputStream: OutputStream,
    ) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.exportRootfsFile(workspace.root, path, outputStream)
    }

    suspend fun deleteFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        recursive: Boolean,
    ): Boolean {
        requireWritableArea(area)
        val deleted = withContext(Dispatchers.IO) {
            val workspace = dao.getById(id) ?: return@withContext false
            manager.deleteFile(workspace.root, path, recursive, area)
        }
        return deleted
    }

    suspend fun moveFile(
        id: String,
        source: String,
        target: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.moveFile(workspace.root, source, target, overwrite)
    }

    suspend fun executeCommand(
        id: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
        extraBindMounts: List<WorkspaceBindMount> = emptyList(),
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        // runInterruptible 让协程取消转化为线程中断，从而打断阻塞的 Process.waitFor 并杀掉进程
        return runInterruptible(Dispatchers.IO) {
            manager.ensureWorkspace(workspace.root)
            manager.executeCommand(workspace.root, command, cwd, timeoutMillis, stdin, extraBindMounts)
        }
    }

    suspend fun executeProgram(
        id: String,
        arguments: List<String>,
        cwd: String = "",
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        return runInterruptible(Dispatchers.IO) {
            manager.executeProgram(workspace.root, arguments, cwd, timeoutMillis)
        }
    }

    suspend fun validateRelativePath(id: String, path: String): String = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.validateRelativePath(workspace.root, path)
    }

    suspend fun executeProgramWithValidatedPath(
        id: String,
        path: String,
        buildArguments: (String) -> List<String>,
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        cwd: String = "",
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        return runInterruptible(Dispatchers.IO) {
            manager.executeProgramWithValidatedPath(
                root = workspace.root,
                path = path,
                buildArguments = buildArguments,
                cwd = cwd,
                timeoutMillis = timeoutMillis,
            )
        }
    }

    suspend fun workspaceFilesExist(id: String): Boolean = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext false
        manager.filesDir(workspace.root).isDirectory
    }

    suspend fun delete(id: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        dao.deleteById(id)
        withContext(Dispatchers.IO) {
            manager.deleteWorkspace(workspace.root)
        }
        cleanupAssistantReferences(id)
        return true
    }

    private suspend fun cleanupAssistantReferences(workspaceId: String) {
        settingsStore.update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.workspaceId?.toString() == workspaceId) {
                        assistant.copy(workspaceId = null)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    /**
     * LINUX 路径落在 /skills_private 时注入会话级挂载; 其它路径不注入.
     * [skillsPrivateAssistantId] 为 null 时按 0/1/many 策略自动解析入口助手.
     */
    private fun skillsPrivateExtraMounts(
        workspaceId: String,
        area: WorkspaceStorageArea,
        path: String,
        skillsPrivateAssistantId: Uuid?,
    ): List<WorkspaceBindMount> {
        if (area != WorkspaceStorageArea.LINUX) return emptyList()
        if (!isSkillsPrivatePath(path)) return emptyList()
        val entry = resolveSkillsPrivateEntry(workspaceId, skillsPrivateAssistantId)
        // 浏览器只读浏览: 源目录缺失时不强制创建, 由 Manager 返回空列表
        val source = skillManager.getAssistantSkillsDir(
            assistantId = entry.selectedAssistant.id,
            createIfMissing = false,
        )
        return listOf(
            WorkspaceBindMount(
                source = source,
                target = SKILLS_PRIVATE_TARGET,
            ),
        )
    }

    private fun isSkillsPrivatePath(path: String): Boolean {
        val absolute = path.replace('\\', '/').trim().trimStart('/').trimEnd('/')
        return absolute == SKILLS_PRIVATE_NAME ||
            absolute.startsWith("$SKILLS_PRIVATE_NAME/") ||
            absolute.isEmpty() // 根列表也需合成 skills_private 占位
    }

    private suspend fun restoreShellState(workspace: WorkspaceEntity) {
        updateShellState(workspace.id, workspace.shellStatus)
    }

    private suspend fun updateShellState(
        workspace: WorkspaceEntity,
        shellStatus: String,
    ) = updateShellState(workspace.id, shellStatus)

    private suspend fun updateShellState(
        workspaceId: String,
        shellStatus: String,
    ) {
        dao.updateShellStatus(
            id = workspaceId,
            shellStatus = shellStatus,
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "WorkspaceRepository"
        private const val MAX_PREVIEW_BYTES = 512L * 1024
        private const val SKILLS_PRIVATE_NAME = "skills_private"
        private const val SKILLS_PRIVATE_TARGET = "/skills_private"
    }
}

internal fun requireWritableArea(area: WorkspaceStorageArea) {
    require(area != WorkspaceStorageArea.LINUX) { "LINUX workspace area is read-only" }
}
