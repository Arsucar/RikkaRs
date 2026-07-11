package me.rerere.workspace

import java.util.TreeSet

const val MAX_WORKSPACE_CHANGED_FILES = 100

/**
 * Keeps shell-discovered paths safe for the workspace files chip contract and bounded for persistence.
 */
fun normalizeWorkspaceChangedFiles(paths: Iterable<String>): List<String> {
    val normalized = TreeSet<String>()
    for (path in paths) {
        if (!path.startsWith("/workspace/")) continue
        val relative = path.removePrefix("/workspace/")
        if (relative.isBlank() || relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) continue
        normalized += path
        if (normalized.size > MAX_WORKSPACE_CHANGED_FILES) normalized.pollLast()
    }
    return normalized.toList()
}
