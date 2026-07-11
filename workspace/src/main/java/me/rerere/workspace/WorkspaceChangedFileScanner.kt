package me.rerere.workspace

import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.TreeSet
import java.util.concurrent.TimeUnit

class WorkspaceChangedFileScanner(
    private val timeoutMillis: Long = DEFAULT_SCAN_TIMEOUT_MS,
    private val nanoTime: () -> Long = System::nanoTime,
    private val maxChangedFiles: Int = MAX_WORKSPACE_CHANGED_FILES,
    private val fileTreeWalker: (Path, FileVisitor<Path>) -> Unit = { root, visitor ->
        Files.walkFileTree(root, visitor)
        Unit
    },
) {
    fun scan(filesRoot: File, modifiedSinceMillis: Long): List<String> {
        return try {
            if (!filesRoot.isDirectory) return emptyList()
            val rootPath = filesRoot.toPath()
            val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceAtLeast(0L))
            val resultLimit = maxChangedFiles.coerceAtLeast(0)
            val changedFiles = TreeSet<String>()
            fileTreeWalker(
                rootPath,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        checkDeadline(deadline)
                        return if (dir != rootPath && dir.fileName?.toString() in PRUNED_DIRECTORIES) {
                            FileVisitResult.SKIP_SUBTREE
                        } else {
                            FileVisitResult.CONTINUE
                        }
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        checkDeadline(deadline)
                        if (attrs.isRegularFile && attrs.lastModifiedTime().toMillis() >= modifiedSinceMillis) {
                            val relative = rootPath.relativize(file).joinToString("/") { it.toString() }
                            if (relative.isNotBlank() && resultLimit > 0) {
                                changedFiles += "/workspace/$relative"
                                if (changedFiles.size > resultLimit) changedFiles.pollLast()
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
            changedFiles.toList()
        } catch (_: IOException) {
            emptyList()
        } catch (_: UncheckedIOException) {
            emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: ScanTimeoutException) {
            emptyList()
        }
    }

    private fun checkDeadline(deadline: Long) {
        if (nanoTime() - deadline > 0L) throw ScanTimeoutException()
    }

    private class ScanTimeoutException : RuntimeException()

    companion object {
        const val DEFAULT_SCAN_TIMEOUT_MS = 250L
        private val PRUNED_DIRECTORIES = setOf(".git", "node_modules")
    }
}
