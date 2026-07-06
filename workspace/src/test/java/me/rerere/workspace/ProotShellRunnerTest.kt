package me.rerere.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProotShellRunnerTest {
    @Test
    fun buildCommandDoesNotEnableLink2SymlinkByDefault() {
        val command = ProotShellRunner(nativeLibraryDir = File("/native"))
            .buildCommand(context = shellContext(), proot = File("/native/libproot_exec.so"))

        assertFalse(command.contains("--link2symlink"))
    }

    @Test
    fun buildCommandCanEnableLink2SymlinkForCompatibility() {
        val command = ProotShellRunner(
            nativeLibraryDir = File("/native"),
            emulateHardLinksWithSymlinks = true,
        ).buildCommand(context = shellContext(), proot = File("/native/libproot_exec.so"))

        assertTrue(command.contains("--link2symlink"))
    }

    @Test
    fun buildCommandMergesStaticAndContextBindMounts() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "proot-mount-${System.nanoTime()}")
        val staticMount = File(baseDir, "static").apply { mkdirs() }
        val contextMount = File(baseDir, "context").apply { mkdirs() }

        try {
            val command = ProotShellRunner(
                nativeLibraryDir = File("/native"),
                extraBindMounts = listOf(WorkspaceBindMount(staticMount, "/static")),
            ).buildCommand(
                context = shellContext(
                    extraBindMounts = listOf(WorkspaceBindMount(contextMount, "/context")),
                ),
                proot = File("/native/libproot_exec.so"),
            )

            assertTrue(command.contains("${staticMount.absolutePath}:/static"))
            assertTrue(command.contains("${contextMount.absolutePath}:/context"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    private fun shellContext(
        extraBindMounts: List<WorkspaceBindMount> = emptyList(),
    ): WorkspaceShellContext {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "proot-command-${System.nanoTime()}")
        val filesDir = File(baseDir, "files")
        return WorkspaceShellContext(
            root = "root",
            command = "git clone https://example.invalid/repo.git",
            cwd = "",
            filesDir = filesDir,
            linuxDir = File(baseDir, "linux"),
            tempDir = File(baseDir, "tmp"),
            workingDir = filesDir,
            timeoutMillis = 30_000L,
            extraBindMounts = extraBindMounts,
        )
    }
}
