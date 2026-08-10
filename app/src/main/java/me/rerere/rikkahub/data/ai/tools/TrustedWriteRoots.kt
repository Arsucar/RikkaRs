package me.rerere.rikkahub.data.ai.tools

/** Built-in free write roots: no hard path approval required. */
val BUILTIN_WRITABLE_ROOT_PREFIXES: List<String> = listOf("/workspace", "/tmp")

private val TRUSTABLE_WRITE_TOOL_NAMES: Set<String> = setOf(
    "workspace_write_file",
    "workspace_edit_file",
)

/** Tools that may show "always allow this directory" and use trusted write roots. */
fun isTrustableWriteTool(toolName: String): Boolean = toolName in TRUSTABLE_WRITE_TOOL_NAMES

/**
 * Normalize an absolute Rootfs tool path.
 * Rejects blank, non-absolute, NUL, and any `..` segment (no traversal collapse).
 * Returns `"/" + parts` or `"/"` when only empty/`.` segments remain.
 */
fun normalizeRootfsToolPath(path: String): String? {
    val normalized = path.replace('\\', '/').trim()
    if (normalized.isBlank()) return null
    if (!normalized.startsWith("/")) return null
    if (normalized.contains('\u0000')) return null
    val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
    if (parts.any { it == ".." }) return null
    return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
}

/**
 * Boundary-safe prefix match: `/skills` must not match `/skills-private` or `/skills_private`.
 * Equality or `$prefix/` separator only.
 * Paths that fail [normalizeRootfsToolPath] never match.
 */
fun matchesRootPrefix(path: String, prefix: String): Boolean {
    val n = normalizeRootfsToolPath(path)?.trimEnd('/')?.ifBlank { "/" } ?: return false
    val p = normalizeRootfsToolPath(prefix)?.trimEnd('/')?.ifBlank { "/" } ?: return false
    return n == p || n.startsWith("$p/")
}

fun isOutsideBuiltinWritableRoots(path: String): Boolean {
    val normalized = normalizeRootfsToolPath(path)?.trimEnd('/')?.ifBlank { "/" } ?: return true
    return BUILTIN_WRITABLE_ROOT_PREFIXES.none { prefix -> matchesRootPrefix(normalized, prefix) }
}

fun isUnderAnyTrustedRoot(path: String, trustedRoots: List<String>): Boolean {
    val normalized = normalizeRootfsToolPath(path) ?: return false
    return trustedRoots.any { matchesRootPrefix(normalized, it) }
}

/**
 * True when a write/edit path still needs hard approval after builtin + trusted roots.
 * Tool-name overrides are applied by the caller.
 * Invalid / traversal paths always require approval (defense in depth).
 */
fun needsPathHardApproval(path: String, trustedRoots: List<String>): Boolean {
    val normalized = normalizeRootfsToolPath(path) ?: return true
    return isOutsideBuiltinWritableRoots(normalized) && !isUnderAnyTrustedRoot(normalized, trustedRoots)
}

/**
 * Normalize a trusted root for persistence.
 * Absolute path, trim trailing `/`, reject blank / `..`.
 */
fun normalizeTrustedWriteRoot(path: String): String? {
    val n = normalizeRootfsToolPath(path) ?: return null
    // Root alone is not a useful trusted write root for persistence.
    if (n == "/") return null
    return n
}

/**
 * Derive the directory prefix to trust from a write path.
 * - Under `/skills/<name>/...` or `/skills_private/<name>/...` → skill root (two segments)
 * - Otherwise → parent directory of the file (or the path itself if single segment)
 */
fun deriveTrustedWriteRoot(path: String): String? {
    val n = normalizeTrustedWriteRoot(path) ?: return null
    if (n == "/") return null
    val segments = n.trim('/').split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    if (segments.size >= 2 && (segments[0] == "skills" || segments[0] == "skills_private")) {
        return "/${segments[0]}/${segments[1]}"
    }
    if (segments.size == 1) return n
    return "/" + segments.dropLast(1).joinToString("/")
}
