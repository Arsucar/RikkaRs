package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.ShellChangedFilesMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.normalizeWorkspaceChangedFiles
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream
import java.io.File

private const val SHELL_TIMEOUT_MAX_SECONDS = 600L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024
private const val MAX_READ_IMAGE_BYTES = 10L * 1024 * 1024
private const val MAX_WRITE_FILE_BYTES = 5L * 1024 * 1024

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_shell" to true,
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

/**
 * Workspace detail switch is an explicit full override for that tool name.
 * When the user sets approval to false, skip path hard-approval entirely (8e1405d9).
 * ToolPermission.ALLOW still never clears hard approval (separate layer).
 */
fun isWorkspaceToolApprovalExplicitlyDisabled(
    name: String,
    overrides: Map<String, Boolean>,
): Boolean = overrides[name] == false

/**
 * Combined write/edit approval: tool switch first, then path hard approval + trusted roots.
 */
fun workspaceWriteEditNeedsApproval(
    toolName: String,
    path: String,
    approvalOverrides: Map<String, Boolean>,
    trustedWriteRoots: List<String>,
): Boolean {
    if (isWorkspaceToolApprovalExplicitlyDisabled(toolName, approvalOverrides)) return false
    if (resolveWorkspaceToolApproval(toolName, approvalOverrides)) return true
    return needsPathHardApproval(path, trustedWriteRoots)
}

data class WorkspaceKnownMount(
    val target: String,
    val source: File,
    val allowedSymlinkRoots: List<File> = emptyList(),
)

suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
    approvalOverrides: Map<String, Boolean>? = null,
    trustedWriteRoots: List<String>? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val entity = if (approvalOverrides == null || trustedWriteRoots == null) {
        workspaceRepository.getById(workspaceId)
    } else {
        null
    }
    val resolvedApprovalOverrides = approvalOverrides
        ?: entity?.toolApprovalOverrides().orEmpty()
    val resolvedTrustedRoots = trustedWriteRoots
        ?: entity?.trustedWriteRootList().orEmpty()
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, resolvedApprovalOverrides)

    val shellCwd = cwd?.removePrefix("/workspace/")?.removePrefix("/workspace")

    return listOf(
        createReadFileTool(workspaceId, ::needsApproval, workspaceRepository, knownMounts),
        createWriteFileTool(
            workspaceId,
            resolvedApprovalOverrides,
            workspaceRepository,
            extraBindMounts,
            resolvedTrustedRoots,
        ),
        createEditFileTool(
            workspaceId,
            resolvedApprovalOverrides,
            workspaceRepository,
            knownMounts,
            extraBindMounts,
            resolvedTrustedRoots,
        ),
        createShellTool(workspaceId, ::needsApproval, workspaceRepository, shellCwd, extraBindMounts),
    )
}

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "ico",
)

internal fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun createReadFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    knownMounts: List<WorkspaceKnownMount>,
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area. Use /skills for global skill files and /skills_private for this assistant's private skill files.
        User-uploaded files are mounted read-only at /upload; read them as /upload/<file-name> and never modify that path.
        Supports UTF-8 text files and image files (png, jpg, jpeg, gif, webp, bmp, svg, heic, heif, avif, ico).
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
            },
            required = listOf("path"),
        )
    },
    needsApproval = { needsApproval("workspace_read_file") },
    execute = {
        val path = it.jsonObject.absolutePath("path")
        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(workspaceId, path, knownMounts)
        } else {
            val text = workspaceRepository.readTextInRootfs(workspaceId, path, knownMounts)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("path", path)
                        put("text", text)
                    }.toString()
                )
            )
        }
    },
)

private fun createWriteFileTool(
    workspaceId: String,
    approvalOverrides: Map<String, Boolean>,
    workspaceRepository: WorkspaceRepository,
    extraBindMounts: List<WorkspaceBindMount>,
    trustedWriteRoots: List<String>,
) = Tool(
    name = "workspace_write_file",
    description = """
        Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = listOf("path", "text"),
        )
    },
    needsApproval = {
        it.workspaceWriteEditNeedsApproval(
            toolName = "workspace_write_file",
            approvalOverrides = approvalOverrides,
            trustedWriteRoots = trustedWriteRoots,
        )
    },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, text, overwrite, extraBindMounts)
        listOf(UIMessagePart.Text(entry.toJson().toString()))
    },
)

private fun createEditFileTool(
    workspaceId: String,
    approvalOverrides: Map<String, Boolean>,
    workspaceRepository: WorkspaceRepository,
    knownMounts: List<WorkspaceKnownMount>,
    extraBindMounts: List<WorkspaceBindMount>,
    trustedWriteRoots: List<String>,
) = Tool(
    name = "workspace_edit_file",
    description = """
        Edit a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
        Provide old_text and new_text. By default old_text must occur exactly once; set replace_all=true to replace every occurrence.
        If no exact match is found, whitespace-tolerant line matching is attempted automatically.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("old_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact text to replace")
                })
                put("new_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Replacement text")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to replace every occurrence. Defaults to false.")
                })
            },
            required = listOf("path", "old_text", "new_text"),
        )
    },
    needsApproval = {
        it.workspaceWriteEditNeedsApproval(
            toolName = "workspace_edit_file",
            approvalOverrides = approvalOverrides,
            trustedWriteRoots = trustedWriteRoots,
        )
    },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val oldText = params.string("old_text") ?: error("old_text is required")
        val newText = params.string("new_text") ?: error("new_text is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        require(oldText.isNotEmpty()) { "old_text must not be empty" }

        val original = workspaceRepository.readTextInRootfs(workspaceId, path, knownMounts)
        // 逐级尝试 exact -> line_trimmed -> block_anchor 替换器, 见 TextReplacers.kt
        val result = try {
            replaceText(original, oldText, newText, replaceAll)
        } catch (e: IllegalArgumentException) {
            error("${e.message} (path: $path)")
        }
        val entry = workspaceRepository.writeTextInRootfs(
            workspaceId = workspaceId,
            path = path,
            text = result.updated,
            overwrite = true,
            extraBindMounts = extraBindMounts,
        )
        val diff = generateUnifiedDiff(original, result.updated, entry.path)
        listOf(
            UIMessagePart.Text(
                text = buildJsonObject {
                    put("path", entry.path)
                    put("replacements", result.replacements)
                    if (result.strategy != ExactReplacer.name) put("matchStrategy", result.strategy)
                    put("sizeBytes", entry.sizeBytes)
                    put("updatedAt", entry.updatedAt)
                }.toString(),
                // diff 存入 metadata 供 UI 渲染 diff view, 不会随工具结果发送给 API
                metadata = diff?.let { d -> DiffMetadata(diff = d).toMetadata() },
            )
        )
    },
)

private fun createShellTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    defaultCwd: String? = null,
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
) = Tool(
    name = "workspace_shell",
    description = buildString {
        append("Run a shell command in the assistant's bound workspace Rootfs. The workspace files area is mounted at /workspace. ")
        append("Global skills are mounted at /skills and this assistant's private skills at /skills_private. ")
        append("Use cwd for a path relative to the workspace files root. ")
        if (!defaultCwd.isNullOrBlank()) {
            append("Defaults to '$defaultCwd'. ")
        }
        append("Requires Rootfs to be installed and ready.")
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to run")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        if (!defaultCwd.isNullOrBlank()) {
                            "Working directory relative to the workspace files root. Defaults to '$defaultCwd'."
                        } else {
                            "Working directory relative to the workspace files root. Defaults to root."
                        }
                    )
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Command timeout in seconds. Defaults to 30, max $SHELL_TIMEOUT_MAX_SECONDS."
                    )
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { needsApproval("workspace_shell") },
    execute = {
        val params = it.jsonObject
        val command = params.string("command") ?: error("command is required")
        val cwd = (params.string("cwd") ?: defaultCwd.orEmpty())
            .removePrefix("/workspace/").removePrefix("/workspace")
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val result = workspaceRepository.executeCommand(
            id = workspaceId,
            command = command,
            cwd = cwd,
            timeoutMillis = timeoutMillis,
            extraBindMounts = extraBindMounts,
        )
        listOf(workspaceShellResultPart(result))
    },
)

internal fun workspaceShellResultPart(result: WorkspaceCommandResult): UIMessagePart.Text = UIMessagePart.Text(
    buildJsonObject {
        put("exitCode", result.exitCode)
        put("stdout", result.stdout)
        put("stderr", result.stderr)
        put("timedOut", result.timedOut)
        if (result.truncated) put("truncated", true)
    }.toString(),
    metadata = ShellChangedFilesMetadata(normalizeWorkspaceChangedFiles(result.changedFiles)).toMetadata(),
)

private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
): String {
    resolveKnownMountFile(path, knownMounts)?.let { file ->
        require(file.isFile) { "Path is not a file: $path" }
        require(file.length() <= MAX_READ_FILE_BYTES) {
            fileTooLargeMessage(path, file.length())
        }
        return file.readText()
    }
    return readRootfsBuffer(workspaceId, path).toString(Charsets.UTF_8.name())
}

/**
 * 按 Rootfs 内绝对路径读入内存。路径映射交给 WorkspaceManager, 由它统一处理
 * /workspace、bind mount 与 Rootfs 内部路径。
 */
private suspend fun WorkspaceRepository.readRootfsBuffer(
    workspaceId: String,
    path: String,
): ByteArrayOutputStream {
    val size = rootfsFileSize(workspaceId, path)
    require(size <= MAX_READ_FILE_BYTES) {
        fileTooLargeMessage(path, size)
    }
    return ByteArrayOutputStream(size.toInt()).also { exportRootfsFile(workspaceId, path, it) }
}

private fun fileTooLargeMessage(path: String, sizeBytes: Long): String =
    "File is too large to read: $path (${sizeBytes / 1024 / 1024}MB, " +
        "max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). " +
        "Use shell commands like head, tail, or grep to read parts of it."

private fun imageTooLargeMessage(path: String, sizeBytes: Long): String =
    "Image is too large to read: $path (${sizeBytes / 1024 / 1024}MB, " +
        "max ${MAX_READ_IMAGE_BYTES / 1024 / 1024}MB)."

internal fun resolveKnownMountFile(path: String, knownMounts: List<WorkspaceKnownMount>): File? {
    val normalized = normalizeRootfsAbsolutePath(path) ?: return null
    for (mount in knownMounts) {
        val target = normalizeRootfsAbsolutePath(mount.target)?.trimEnd('/') ?: continue
        val relative = when {
            normalized == target -> ""
            normalized.startsWith("$target/") -> normalized.removePrefix("$target/").trimStart('/')
            else -> continue
        }
        val sourceRoot = mount.source.canonicalFile
        val lexicalFile = sourceRoot.toPath().resolve(relative).normalize().toFile()
        if (!lexicalFile.isSameOrInsidePath(sourceRoot)) continue
        val file = lexicalFile.canonicalFile
        val allowedRoots = listOf(sourceRoot) + mount.allowedSymlinkRoots.map { it.canonicalFile }
        if (allowedRoots.any { root -> file.isSameOrInside(root) }) return file
    }
    return null
}

private fun normalizeRootfsAbsolutePath(path: String): String? =
    normalizeRootfsToolPath(path)

private fun File.isSameOrInside(root: File): Boolean {
    val rootPath = root.canonicalFile.path
    val currentPath = canonicalFile.path
    return currentPath == rootPath || currentPath.startsWith(rootPath + File.separator)
}

private fun File.isSameOrInsidePath(root: File): Boolean {
    val rootPath = root.absoluteFile.path
    val currentPath = absoluteFile.path
    return currentPath == rootPath || currentPath.startsWith(rootPath + File.separator)
}

private suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
): List<UIMessagePart> {
    val bytes = resolveKnownMountFile(path, knownMounts)?.let { file ->
        require(file.isFile) { "Path is not a file: $path" }
        require(file.length() <= MAX_READ_IMAGE_BYTES) {
            imageTooLargeMessage(path, file.length())
        }
        file.readBytes()
    } ?: run {
        val size = rootfsFileSize(workspaceId, path)
        require(size <= MAX_READ_IMAGE_BYTES) {
            imageTooLargeMessage(path, size)
        }
        ByteArrayOutputStream(size.toInt()).also { exportRootfsFile(workspaceId, path, it) }.toByteArray()
    }

    val filesManager = getKoin().get<FilesManager>()
    val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
    return listOf(
        UIMessagePart.Image(url = uris.first().toString()),
        UIMessagePart.Text(
            buildJsonObject {
                put("path", path)
                put("description", "Image file read successfully")
            }.toString()
        ),
    )
}

private suspend fun WorkspaceRepository.writeTextInRootfs(
    workspaceId: String,
    path: String,
    text: String,
    overwrite: Boolean,
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
): WorkspaceFileEntry {
    val stdin = text.toByteArray(Charsets.UTF_8)
    require(stdin.size <= MAX_WRITE_FILE_BYTES) {
        "Content is too large to write: ${stdin.size} bytes " +
            "(max ${MAX_WRITE_FILE_BYTES / 1024 / 1024}MB)"
    }
    val pathArg = path.shellQuote()
    val result = runRootfsCommand(
        workspaceId = workspaceId,
        action = "Write file",
        command = """
            if [ -e $pathArg ] && [ ${(!overwrite).shellFlag()} = 1 ]; then
              printf '%s\n' ${"File already exists: $path".shellQuote()} >&2
              exit 1
            fi
            if [ -e $pathArg ] && [ ! -f $pathArg ]; then
              printf '%s\n' ${"Path is not a file: $path".shellQuote()} >&2
              exit 1
            fi
            parent=${'$'}(dirname -- $pathArg) || exit 1
            mkdir -p -- "${'$'}parent" || exit 1
            cat > $pathArg || exit 1
            ${statEntryCommand(path)}
        """.trimIndent(),
        stdin = stdin,
        extraBindMounts = extraBindMounts,
    )
    return result.stdout.parseRootfsEntry()
}

private suspend fun WorkspaceRepository.runRootfsCommand(
    workspaceId: String,
    action: String,
    command: String,
    stdin: ByteArray? = null,
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
): WorkspaceCommandResult {
    val result = executeCommand(
        id = workspaceId,
        command = command,
        timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin = stdin,
        extraBindMounts = extraBindMounts,
    )
    if (result.timedOut) {
        error("$action timed out")
    }
    if (result.exitCode != 0) {
        val message = result.stderr.ifBlank { result.stdout }.trim()
        error(if (message.isBlank()) "$action failed with exit code ${result.exitCode}" else message)
    }
    if (result.truncated) {
        error("$action output is too large")
    }
    return result
}

private fun statEntryCommand(path: String): String {
    val pathArg = path.shellQuote()
    return """
        if [ -d $pathArg ]; then entry_type=d; else entry_type=f; fi
        entry_size=${'$'}(stat -c '%s' -- $pathArg) || exit 1
        entry_mtime=${'$'}(stat -c '%Y' -- $pathArg) || exit 1
        printf '%s\0%s\0%s\0%s\0' "${'$'}entry_type" "${'$'}entry_size" "${'$'}entry_mtime" $pathArg
    """.trimIndent()
}

private fun String.parseRootfsEntry(): WorkspaceFileEntry =
    parseRootfsEntries().singleOrNull() ?: error("Invalid file metadata output")

private fun String.parseRootfsEntries(): List<WorkspaceFileEntry> {
    val fields = split('\u0000').dropLastWhile { it.isEmpty() }
    require(fields.size % 4 == 0) { "Invalid file metadata output" }
    return fields.chunked(4).map { chunk ->
        val type = chunk[0]
        val size = chunk[1].toLongOrNull() ?: error("Invalid file size: ${chunk[1]}")
        val updatedAt = (chunk[2].toLongOrNull() ?: error("Invalid file mtime: ${chunk[2]}")) * 1_000L
        val path = chunk[3]
        WorkspaceFileEntry(
            path = path,
            name = path.rootfsName(),
            isDirectory = type == "d",
            sizeBytes = size,
            updatedAt = updatedAt,
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/')?.trim() ?: error("$name is required")
    return normalizeRootfsToolPath(path)
        ?: error("$name must be an absolute Rootfs path without '..' segments")
}

// 免强制审批的可写安全区: builtin `/workspace` `/tmp` + 工作区受信目录 (TrustedWriteRoots)
// 工作区详情开关显式关闭时完全跳过（含 path hard approval）
private fun kotlinx.serialization.json.JsonElement.workspaceWriteEditNeedsApproval(
    toolName: String,
    approvalOverrides: Map<String, Boolean>,
    trustedWriteRoots: List<String>,
): Boolean {
    if (isWorkspaceToolApprovalExplicitlyDisabled(toolName, approvalOverrides)) return false
    if (resolveWorkspaceToolApproval(toolName, approvalOverrides)) return true
    return runCatching {
        needsPathHardApproval(jsonObject.absolutePath("path"), trustedWriteRoots)
    }.getOrDefault(true)
}

private fun String.rootfsName(): String =
    trimEnd('/').substringAfterLast('/').ifBlank { "/" }

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private fun Boolean.shellFlag(): Int = if (this) 1 else 0

private fun JsonObjectBuilder.putPathProperty(required: Boolean) {
    put("path", buildJsonObject {
        put("type", "string")
        put(
            "description",
            if (required) {
                "Absolute path inside Rootfs. Use /workspace for the workspace files area, /skills for global skills, and /skills_private for assistant-private skills."
            } else {
                "Optional absolute path inside Rootfs. Use /workspace for the workspace files area, /skills for global skills, and /skills_private for assistant-private skills."
            }
        )
    })
}

private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}
