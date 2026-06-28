package me.rerere.workspace

/**
 * Heuristic shell command policy for workspace tools.
 *
 * **Heuristic interception only — not a security boundary.** Real isolation depends on
 * [WorkspaceManager] working-directory limits and process permissions.
 */
sealed class ShellCommandVerdict {
    data object Allowed : ShellCommandVerdict()
    data class Rejected(val userMessage: String) : ShellCommandVerdict()
}

private const val NOT_ALLOWED = "This command is not allowed in the workspace shell"
private const val PATH_NOT_ALLOWED = "Access to that path is not allowed from the workspace shell"
private const val TRAVERSAL_NOT_ALLOWED = "Path traversal outside the workspace is not allowed"

/** Workspace cwd should not reference these absolute prefixes. */
private val SENSITIVE_ABSOLUTE_PATHS = listOf(
    "/data/data/",
    "/data/user/",
    "/sdcard/",
    "/storage/",
    "/system/",
)

private val RM_RF_ROOT_CLASS = Regex(
    """rm\s+-rf\s+(/\*|/\s*(${'$'}|;)|/(home|etc|data|system|sdcard|storage)(\s|${'$'}|;|/)|~|\${'$'}HOME|\*)""",
    RegexOption.IGNORE_CASE,
)

private val FORK_BOMB_HEURISTIC = Regex(
    """:\s*\(\s*\)\s*\{|:.*\|\s*:.*&""",
    RegexOption.IGNORE_CASE,
)

private val CHMOD_WORLD_ROOT = Regex(
    """chmod\s+-R\s+777\s+/($|\s|;|\*)""",
    RegexOption.IGNORE_CASE,
)

private val POWER_COMMAND = Regex(
    """^(shutdown|reboot|halt)\b|(?:;|&&|\|\|)\s*(shutdown|reboot|halt)\b|^(init\s+[06])\b|(?:;|&&|\|\|)\s*init\s+[06]\b""",
    RegexOption.IGNORE_CASE,
)

fun evaluateShellCommand(command: String): ShellCommandVerdict {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) {
        return ShellCommandVerdict.Rejected("Command is required")
    }
    val lower = trimmed.lowercase()

    if (RM_RF_ROOT_CLASS.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (FORK_BOMB_HEURISTIC.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (CHMOD_WORLD_ROOT.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (POWER_COMMAND.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (lower.contains("mkfs.") || Regex("""dd\s+if=""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (Regex(""">/dev/""").containsMatchIn(lower)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    for (path in SENSITIVE_ABSOLUTE_PATHS) {
        if (lower.contains(path)) {
            return ShellCommandVerdict.Rejected(PATH_NOT_ALLOWED)
        }
    }

    if (trimmed.contains("../")) {
        for (path in SENSITIVE_ABSOLUTE_PATHS) {
            if (lower.contains(path)) {
                return ShellCommandVerdict.Rejected(TRAVERSAL_NOT_ALLOWED)
            }
        }
        if (Regex("""\.\./.*(/data/|/sdcard/|/system/)""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return ShellCommandVerdict.Rejected(TRAVERSAL_NOT_ALLOWED)
        }
    }

    return ShellCommandVerdict.Allowed
}