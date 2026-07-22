package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.GitChangeSection
import me.rerere.rikkahub.data.model.GitFileChange
import me.rerere.rikkahub.data.model.GitRepositoryStatus
import me.rerere.workspace.WorkspaceCommandResult
import java.nio.charset.StandardCharsets

class WorkspaceGitRepository(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend fun readStatus(workspace: WorkspaceEntity): GitRepositoryStatus {
        val result = workspaceRepository.executeProgram(
            id = workspace.id,
            arguments = STATUS_ARGUMENTS,
            timeoutMillis = GIT_TIMEOUT_MILLIS,
        )
        result.requireGitSuccess()
        val parsed = parseGitStatusPorcelain(result.stdout, MAX_STATUS_FILES)
        return GitRepositoryStatus(
            workspaceId = workspace.id,
            projectName = workspace.name,
            branch = parsed.branch,
            detachedHead = parsed.detachedHead,
            changes = parsed.changes,
            truncated = result.truncated || parsed.truncated,
        )
    }

    suspend fun readDiff(
        workspace: WorkspaceEntity,
        path: String,
        section: GitChangeSection,
    ): GitDiffContent {
        val diff = try {
            workspaceRepository.executeProgramWithValidatedPath(
                id = workspace.id,
                path = path,
                buildArguments = { validatedPath -> buildGitDiffArguments(section, validatedPath) },
                timeoutMillis = GIT_TIMEOUT_MILLIS,
            )
        } catch (_: IllegalArgumentException) {
            throw GitReadException.InvalidPath
        }
        diff.requireGitSuccess(allowNoIndexDifference = section == GitChangeSection.UNTRACKED)
        val parsedDiff = parseCombinedGitDiff(diff.stdout)
        if (parsedDiff.binary) {
            return GitDiffContent.Binary
        }

        val truncatedByLimit = parsedDiff.patch.toByteArray(StandardCharsets.UTF_8).size > MAX_DIFF_BYTES
        return GitDiffContent.Text(
            content = truncateUtf8(parsedDiff.patch, MAX_DIFF_BYTES),
            truncated = diff.truncated || truncatedByLimit,
        )
    }

    companion object {
        const val GIT_TIMEOUT_MILLIS = 8_000L
        const val MAX_STATUS_FILES = 300
        const val MAX_DIFF_BYTES = 64 * 1024
        private val STATUS_ARGUMENTS = listOf(
            "git",
            "--no-pager",
            "--no-optional-locks",
            "status",
            "--porcelain=v2",
            "--branch",
            "-z",
            "--untracked-files=all",
        )
    }
}

internal fun buildGitDiffArguments(
    section: GitChangeSection,
    path: String,
): List<String> = buildList {
    add("git")
    add("--no-pager")
    add("--no-optional-locks")
    add("diff")
    addAll(listOf("--numstat", "--no-color", "--no-ext-diff", "--no-textconv", "--unified=3"))
    add("--patch")
    when (section) {
        GitChangeSection.STAGED -> add("--cached")
        GitChangeSection.UNSTAGED -> Unit
        GitChangeSection.UNTRACKED -> {
            add("--no-index")
            add("/dev/null")
        }
    }
    add("--")
    add(path)
}

internal data class ParsedGitDiff(
    val patch: String,
    val binary: Boolean,
)

internal fun parseCombinedGitDiff(output: String): ParsedGitDiff {
    val patchStart = Regex("(?m)^diff --git ").find(output)?.range?.first ?: output.length
    val summary = output.substring(0, patchStart)
    return ParsedGitDiff(
        patch = output.substring(patchStart),
        binary = summary.lineSequence().any(::isBinaryNumStat),
    )
}

internal fun WorkspaceCommandResult.requireGitSuccess(allowNoIndexDifference: Boolean = false) {
    if (timedOut) throw GitReadException.TimedOut
    if (exitCode == 0 || (allowNoIndexDifference && exitCode == 1)) return

    val normalizedError = stderr.lowercase()
    when {
        exitCode == 127 -> throw GitReadException.GitUnavailable
        "not a git repository" in normalizedError -> throw GitReadException.NotRepository
        "permission denied" in normalizedError || "operation not permitted" in normalizedError -> {
            throw GitReadException.PermissionDenied
        }
        else -> throw GitReadException.Failed
    }
}

sealed interface GitDiffContent {
    data object Binary : GitDiffContent
    data class Text(val content: String, val truncated: Boolean) : GitDiffContent
}

sealed class GitReadException : Exception() {
    data object InvalidPath : GitReadException()
    data object PermissionDenied : GitReadException()
    data object NotRepository : GitReadException()
    data object TimedOut : GitReadException()
    data object GitUnavailable : GitReadException()
    data object Failed : GitReadException()
}

internal data class ParsedGitStatus(
    val branch: String?,
    val detachedHead: String?,
    val changes: List<GitFileChange>,
    val truncated: Boolean,
)

internal fun parseGitStatusPorcelain(
    output: String,
    maxFiles: Int,
): ParsedGitStatus {
    require(maxFiles > 0) { "maxFiles must be positive" }
    val records = output.split('\u0000')
    val changes = mutableListOf<GitFileChange>()
    var branch: String? = null
    var oid: String? = null
    var detached = false
    var truncated = false
    var index = 0

    fun addChange(change: GitFileChange) {
        if (changes.size < maxFiles) changes += change else truncated = true
    }

    while (index < records.size) {
        val record = records[index]
        when {
            record.startsWith("# branch.head ") -> {
                val head = record.removePrefix("# branch.head ")
                detached = head == "(detached)"
                branch = head.takeUnless { detached || it == "(unknown)" }
            }
            record.startsWith("# branch.oid ") -> {
                oid = record.removePrefix("# branch.oid ").takeUnless { it == "(initial)" }
            }
            record.startsWith("1 ") -> parseOrdinaryRecord(record)?.let(::addChange)
            record.startsWith("2 ") -> {
                val originalPath = records.getOrNull(index + 1)
                parseRenameRecord(record, originalPath)?.let(::addChange)
                index += 1
            }
            record.startsWith("u ") -> parseUnmergedRecord(record)?.let(::addChange)
            record.startsWith("? ") -> addChange(
                GitFileChange(
                    path = record.removePrefix("? "),
                    indexStatus = '?',
                    workTreeStatus = '?',
                )
            )
        }
        index += 1
    }

    return ParsedGitStatus(
        branch = branch,
        detachedHead = if (detached) oid?.take(8) else null,
        changes = changes,
        truncated = truncated,
    )
}

private fun parseOrdinaryRecord(record: String): GitFileChange? {
    val fields = record.split(' ', limit = 9)
    return parseChange(fields.getOrNull(1), fields.getOrNull(8))
}

private fun parseRenameRecord(record: String, originalPath: String?): GitFileChange? {
    val fields = record.split(' ', limit = 10)
    return parseChange(fields.getOrNull(1), fields.getOrNull(9), originalPath)
}

private fun parseUnmergedRecord(record: String): GitFileChange? {
    val fields = record.split(' ', limit = 11)
    return parseChange(fields.getOrNull(1), fields.getOrNull(10), isUnmerged = true)
}

private fun parseChange(
    xy: String?,
    path: String?,
    originalPath: String? = null,
    isUnmerged: Boolean = false,
): GitFileChange? {
    if (xy == null || xy.length != 2 || path.isNullOrEmpty()) return null
    return GitFileChange(
        path = path,
        originalPath = originalPath,
        indexStatus = xy[0],
        workTreeStatus = xy[1],
        isUnmerged = isUnmerged,
    )
}

internal fun isBinaryNumStat(line: String): Boolean {
    val fields = line.split('\t', limit = 3)
    return fields.size >= 2 && fields[0] == "-" && fields[1] == "-"
}

internal fun truncateUtf8(text: String, maxBytes: Int): String {
    if (text.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return text
    var byteCount = 0
    var endIndex = 0
    while (endIndex < text.length) {
        val codePoint = text.codePointAt(endIndex)
        val charCount = Character.charCount(codePoint)
        val nextBytes = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8).size
        if (byteCount + nextBytes > maxBytes) break
        byteCount += nextBytes
        endIndex += charCount
    }
    return text.substring(0, endIndex)
}
