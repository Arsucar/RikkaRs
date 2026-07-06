package me.rerere.rikkahub.ui.pages.extensions.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceTerminalSessionTest {
    @Test
    fun terminalProotArgsDoNotEnableLink2Symlink() {
        val skillsDir = tempDir("skills").apply { mkdirs() }

        val args = buildWorkspaceTerminalProotArgs(
            linuxDir = File("/linux"),
            filesDir = File("/workspace-files"),
            skillsDir = skillsDir,
        )

        assertFalse(args.contains("--link2symlink"))
    }

    @Test
    fun terminalProotArgsBindWorkspaceAndSkills() {
        val skillsDir = tempDir("skills").apply { mkdirs() }

        val args = buildWorkspaceTerminalProotArgs(
            linuxDir = File("/linux"),
            filesDir = File("/workspace-files"),
            skillsDir = skillsDir,
        )

        assertTrue(args.contains("${File("/workspace-files").absolutePath}:/workspace"))
        assertTrue(args.contains("${skillsDir.absolutePath}:/skills"))
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "terminal-proot-$name-${System.nanoTime()}")
}
