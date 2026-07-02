package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}