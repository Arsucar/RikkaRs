package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class WorkspaceChangedFileScannerTest {
    @Test
    fun scanReturnsStableWorkspacePathsForRecentRegularFiles() {
        val root = Files.createTempDirectory("workspace-scan").toFile()
        val startedAt = System.currentTimeMillis()
        File(root, "z.txt").apply { writeText("z"); setLastModified(startedAt) }
        File(root, "nested/a.txt").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("a")
            setLastModified(startedAt + 1)
        }
        File(root, "old.txt").apply { writeText("old"); setLastModified(startedAt - 1) }

        assertEquals(
            listOf("/workspace/nested/a.txt", "/workspace/z.txt"),
            WorkspaceChangedFileScanner().scan(root, startedAt),
        )
    }

    @Test
    fun scanPrunesLargeIgnoredDirectories() {
        val root = Files.createTempDirectory("workspace-scan-pruned").toFile()
        val startedAt = System.currentTimeMillis()
        File(root, ".git/objects/object").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("git")
        }
        File(root, "node_modules/pkg/index.js").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("node")
        }
        File(root, "src/main.txt").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("ok")
            setLastModified(startedAt)
        }

        assertEquals(
            listOf("/workspace/src/main.txt"),
            WorkspaceChangedFileScanner().scan(root, startedAt),
        )
    }

    @Test
    fun scanTimeoutReturnsNoPartialResults() {
        val root = Files.createTempDirectory("workspace-scan-timeout").toFile()
        File(root, "file.txt").writeText("content")
        var now = 0L
        val scanner = WorkspaceChangedFileScanner(timeoutMillis = 1L, nanoTime = { now.also { now += 2_000_000L } })

        assertEquals(emptyList<String>(), scanner.scan(root, 0L))
    }

    @Test
    fun scanExcludesSymbolicLinks() {
        val root = Files.createTempDirectory("workspace-scan-symlink").toFile()
        val target = File(root, "target.txt").apply { writeText("target") }
        val link = File(root, "link.txt").toPath()
        try {
            Files.createSymbolicLink(link, target.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
        }

        assertEquals(
            listOf("/workspace/target.txt"),
            WorkspaceChangedFileScanner().scan(root, 0L),
        )
    }

    @Test
    fun scanAppliesStableChangedFileLimit() {
        val root = Files.createTempDirectory("workspace-scan-limit").toFile()
        listOf("c.txt", "a.txt", "b.txt").forEach { name -> File(root, name).writeText(name) }

        assertEquals(
            listOf("/workspace/a.txt", "/workspace/b.txt"),
            WorkspaceChangedFileScanner(maxChangedFiles = 2).scan(root, 0L),
        )
    }

    @Test
    fun scanDowngradesExpectedIoFailureButDoesNotSwallowFatalErrors() {
        val root = Files.createTempDirectory("workspace-scan-errors").toFile()
        val ioScanner = WorkspaceChangedFileScanner(fileTreeWalker = { _, _ -> throw IOException("failed") })
        val fatalScanner = WorkspaceChangedFileScanner(fileTreeWalker = { _, _ -> throw OutOfMemoryError("fatal") })

        assertEquals(emptyList<String>(), ioScanner.scan(root, 0L))
        assertThrows(OutOfMemoryError::class.java) { fatalScanner.scan(root, 0L) }
    }

    @Test
    fun managerAddsChangedFilesOnlyToSuccessfulCommands() {
        val baseDir = Files.createTempDirectory("workspace-manager-scan").toFile()
        val runner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                File(context.filesDir, "generated.txt").apply {
                    writeText("generated")
                    setLastModified(System.currentTimeMillis() + 1_000L)
                }
                return WorkspaceCommandResult(exitCode = 0, stdout = "ok", stderr = "")
            }
        }
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws")

        val result = manager.executeCommand("ws", "generate")

        assertEquals(listOf("/workspace/generated.txt"), result.changedFiles)
        assertEquals("ok", result.stdout)
    }

    @Test
    fun managerReportsModifiedExistingFileFromNestedWorkingDirectory() {
        val baseDir = Files.createTempDirectory("workspace-manager-modified-scan").toFile()
        val runner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                File(context.workingDir, "existing.txt").apply {
                    writeText("after")
                    setLastModified(System.currentTimeMillis() + 1_000L)
                }
                return WorkspaceCommandResult(exitCode = 0, stdout = "ok", stderr = "")
            }
        }
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws")
        File(manager.filesDir("ws"), "nested/existing.txt").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("before")
            setLastModified(0L)
        }

        val result = manager.executeCommand("ws", "modify", cwd = "nested")

        assertEquals(listOf("/workspace/nested/existing.txt"), result.changedFiles)
    }

    @Test
    fun managerPreservesFailedCommandResultWithoutScanning() {
        val baseDir = Files.createTempDirectory("workspace-manager-failed-scan").toFile()
        val runner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                File(context.filesDir, "side-effect.txt").writeText("created")
                return WorkspaceCommandResult(exitCode = 7, stdout = "out", stderr = "failed", truncated = true)
            }
        }
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws")

        val result = manager.executeCommand("ws", "fail")

        assertEquals(7, result.exitCode)
        assertEquals("out", result.stdout)
        assertEquals("failed", result.stderr)
        assertEquals(true, result.truncated)
        assertEquals(emptyList<String>(), result.changedFiles)
    }

    @Test
    fun managerPreservesTimedOutCommandResultWithoutScanning() {
        val baseDir = Files.createTempDirectory("workspace-manager-timeout-scan").toFile()
        val runner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
                File(context.filesDir, "side-effect.txt").writeText("created")
                return WorkspaceCommandResult(
                    exitCode = -1,
                    stdout = "partial",
                    stderr = "",
                    timedOut = true,
                )
            }
        }
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws")

        val result = manager.executeCommand("ws", "timeout")

        assertEquals(-1, result.exitCode)
        assertEquals("partial", result.stdout)
        assertEquals(true, result.timedOut)
        assertEquals(emptyList<String>(), result.changedFiles)
    }

    @Test
    fun managerPreservesSuccessfulCommandResultWhenScanFails() {
        val baseDir = Files.createTempDirectory("workspace-manager-scan-failure").toFile()
        val runner = object : WorkspaceShellRunner {
            override fun execute(context: WorkspaceShellContext) = WorkspaceCommandResult(
                exitCode = 0,
                stdout = "out",
                stderr = "warning",
                truncated = true,
            )
        }
        val scanner = WorkspaceChangedFileScanner(fileTreeWalker = { _, _ -> throw IOException("failed") })
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner, changedFileScanner = scanner)
        manager.ensureWorkspace("ws")

        val result = manager.executeCommand("ws", "generate")

        assertEquals(0, result.exitCode)
        assertEquals("out", result.stdout)
        assertEquals("warning", result.stderr)
        assertEquals(true, result.truncated)
        assertEquals(emptyList<String>(), result.changedFiles)
    }
}
