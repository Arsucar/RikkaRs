package me.rerere.rikkahub.data.files

import java.io.File

/** Resolves a relative archive path without allowing it to escape [root]. */
fun resolveContainedFile(root: File, relativePath: String): File? {
    if (relativePath.isBlank() || relativePath.startsWith('/') || relativePath.contains('\\')) {
        return null
    }
    val canonicalRoot = root.canonicalFile
    val target = File(canonicalRoot, relativePath).canonicalFile
    val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
    return target.path.startsWith(rootPrefix)
        .takeIf { it }
        ?.let { target }
}
