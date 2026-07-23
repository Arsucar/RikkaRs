package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceManagerFilesDirTest {
    @Test
    fun filesDirUsesFilesBaseDirProviderWhileLinuxStaysOnBaseDir() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-base-${System.nanoTime()}")
        val externalFilesBase = File(System.getProperty("java.io.tmpdir"), "ws-ext-${System.nanoTime()}")
        val manager = WorkspaceManager(
            baseDir = baseDir,
            filesBaseDirProvider = { externalFilesBase },
        )

        val root = "abc123"
        assertEquals(File(File(externalFilesBase, root), "files"), manager.filesDir(root))
        assertEquals(File(File(baseDir, root), "linux"), manager.linuxDir(root))
        assertEquals(File(File(baseDir, root), "tmp"), manager.tempDir(root))
        assertNotEquals(manager.filesDir(root).parentFile?.parentFile, manager.linuxDir(root).parentFile?.parentFile)
    }

    @Test
    fun executeCommandRejectedWhenGlobalLockEngaged() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-lock-${System.nanoTime()}")
        val lock = WorkspaceGlobalLock()
        lock.lock()
        val manager = WorkspaceManager(baseDir = baseDir, globalLock = lock)
        manager.ensureWorkspace("ws1")

        val result = manager.executeCommand("ws1", "pwd")
        assertEquals(1, result.exitCode)
        assertEquals("Workspace storage migration in progress, please retry shortly", result.stderr)
    }

    @Test
    fun executeProgramPreservesArgumentsAsSeparateValues() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-program-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws1")

        val arguments = listOf("git", "diff", "--", "name; echo unsafe.txt")
        manager.executeProgram("ws1", arguments)

        assertEquals(arguments, runner.context?.programArguments)
        assertEquals("", runner.context?.command)
    }

    @Test
    fun validatedProgramBuildsArgumentsFromNormalizedPathAtExecutionBoundary() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-validated-program-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws1")

        manager.executeProgramWithValidatedPath(
            root = "ws1",
            path = "./folder/file name.txt",
            buildArguments = { path -> listOf("git", "diff", "--", path) },
        )

        assertEquals(
            listOf("git", "diff", "--", "folder/file name.txt"),
            runner.context?.programArguments,
        )
    }

    @Test
    fun validatedProgramUsesNestedCwdWhileKeepingArgumentRelativeToRepository() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-nested-program-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws1")
        File(manager.filesDir("ws1"), "nested/repo/folder").mkdirs()

        manager.executeProgramWithValidatedPath(
            root = "ws1",
            path = "./folder/file name.txt",
            buildArguments = { path -> listOf("git", "diff", "--", path) },
            cwd = "nested/repo",
        )

        assertEquals("nested/repo", runner.context?.cwd)
        assertEquals(
            listOf("git", "diff", "--", "folder/file name.txt"),
            runner.context?.programArguments,
        )
    }

    @Test
    fun validatedProgramRejectsUnsafeNestedCwdBeforeRunnerInvocation() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-invalid-cwd-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws1")

        assertThrows(IllegalArgumentException::class.java) {
            manager.executeProgramWithValidatedPath(
                root = "ws1",
                path = "file.txt",
                buildArguments = { path -> listOf("git", "diff", "--", path) },
                cwd = "../outside",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.executeProgramWithValidatedPath(
                root = "ws1",
                path = "file.txt",
                buildArguments = { path -> listOf("git", "diff", "--", path) },
                cwd = "/workspace/nested",
            )
        }
        assertNull(runner.context)
    }

    @Test
    fun validatedProgramReportsMissingCwdSeparatelyFromInvalidPath() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-missing-cwd-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws1")

        assertThrows(WorkspaceWorkingDirectoryException::class.java) {
            manager.executeProgramWithValidatedPath(
                root = "ws1",
                path = "file.txt",
                buildArguments = { path -> listOf("git", "diff", "--", path) },
                cwd = "missing",
            )
        }
        assertNull(runner.context)
    }

    @Test
    fun readOnlyProgramValidationDoesNotCreateMissingWorkspaceRoot() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-missing-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        val filesDir = manager.filesDir("ws1")

        assertEquals("file.txt", manager.validateRelativePath("ws1", "file.txt"))
        assertFalse(filesDir.exists())
        assertThrows(IllegalArgumentException::class.java) {
            manager.executeProgram("ws1", listOf("git", "status"))
        }
        assertFalse(filesDir.exists())
        assertNull(runner.context)
    }

    @Test
    fun invalidProgramArgumentsFailBeforeRunnerInvocation() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-invalid-program-${System.nanoTime()}")
        val runner = RecordingShellRunner()
        val manager = WorkspaceManager(baseDir = baseDir, shellRunner = runner)
        manager.ensureWorkspace("ws1")

        assertThrows(IllegalArgumentException::class.java) {
            manager.executeProgram("ws1", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.executeProgram("ws1", listOf("git", "bad\u0000argument"))
        }
        assertThrows(WorkspaceWorkingDirectoryException::class.java) {
            manager.executeProgram("ws1", listOf("git", "status"), cwd = "missing")
        }
        assertNull(runner.context)
    }

    @Test
    fun validateRelativePathRejectsTraversalAndSymlinkEscape() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ws-path-${System.nanoTime()}")
        val manager = WorkspaceManager(baseDir = baseDir)
        val filesDir = manager.ensureWorkspace("ws1").let { manager.filesDir("ws1") }
        File(filesDir, "safe.txt").writeText("safe")

        assertEquals("safe.txt", manager.validateRelativePath("ws1", "./safe.txt"))
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateRelativePath("ws1", "../outside.txt")
        }

        val outside = File(baseDir, "outside.txt").apply { writeText("outside") }
        val link = File(filesDir, "outside-link")
        val linked = runCatching {
            java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
        }.isSuccess
        if (linked) {
            assertThrows(IllegalArgumentException::class.java) {
                manager.validateRelativePath("ws1", "outside-link")
            }
        } else {
            assertTrue(!link.exists())
        }

        val realDirectory = File(filesDir, "real-directory").apply { mkdirs() }
        File(realDirectory, "nested.txt").writeText("nested")
        val directoryLink = File(filesDir, "directory-link")
        val directoryLinked = runCatching {
            java.nio.file.Files.createSymbolicLink(directoryLink.toPath(), realDirectory.toPath())
        }.isSuccess
        if (directoryLinked) {
            assertThrows(IllegalArgumentException::class.java) {
                manager.validateRelativePath("ws1", "directory-link/nested.txt")
            }
        } else {
            assertFalse(directoryLink.exists())
        }
    }

    private class RecordingShellRunner : WorkspaceShellRunner {
        var context: WorkspaceShellContext? = null

        override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
            this.context = context
            return WorkspaceCommandResult(exitCode = 0, stdout = "", stderr = "")
        }
    }
}
