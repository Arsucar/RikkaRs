package me.rerere.rikkahub.data.ai.tools

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeNoException
import org.junit.Test

class WorkspaceKnownMountTest {
    @Test
    fun resolveKnownMountFile_mapsSkillsPathToSource() {
        val root = Files.createTempDirectory("known-mount").toFile()
        val skillFile = File(root, "demo/SKILL.md").apply {
            parentFile?.mkdirs()
            writeText("skill")
        }

        try {
            val resolved = resolveKnownMountFile(
                path = "/skills/demo/SKILL.md",
                knownMounts = listOf(WorkspaceKnownMount("/skills", root)),
            )

            assertEquals(skillFile.canonicalFile, resolved)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveKnownMountFile_rejectsTraversal() {
        val root = Files.createTempDirectory("known-mount").toFile()
        try {
            val resolved = resolveKnownMountFile(
                path = "/skills/../secret.txt",
                knownMounts = listOf(WorkspaceKnownMount("/skills", root)),
            )

            assertNull(resolved)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveKnownMountFile_mapsUploadPathToSource() {
        val root = Files.createTempDirectory("known-mount-upload").toFile()
        val uploadFile = File(root, "a.txt").apply { writeText("uploaded") }

        try {
            val resolved = resolveKnownMountFile(
                path = "/upload/a.txt",
                knownMounts = listOf(WorkspaceKnownMount("/upload", root)),
            )

            assertEquals(uploadFile.canonicalFile, resolved)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveKnownMountFile_rejectsUploadTraversal() {
        val root = Files.createTempDirectory("known-mount-upload").toFile()
        try {
            val resolved = resolveKnownMountFile(
                path = "/upload/../secret",
                knownMounts = listOf(WorkspaceKnownMount("/upload", root)),
            )

            assertNull(resolved)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveKnownMountFile_mapsUploadUuidFileAfterFork() {
        val root = Files.createTempDirectory("known-mount-upload-fork").toFile()
        val forkedName = "a1b2c3d4-e5f6-7890-abcd-ef1234567890.txt"
        val forkedFile = File(root, forkedName).apply { writeText("forked-content") }

        try {
            val resolved = resolveKnownMountFile(
                path = "/upload/$forkedName",
                knownMounts = listOf(WorkspaceKnownMount("/upload", root)),
            )

            assertEquals(forkedFile.canonicalFile, resolved)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveKnownMountFile_allowsSymlinkUnderExplicitRoot() {
        val root = Files.createTempDirectory("known-mount").toFile()
        val skillDir = File(root, "demo").apply { mkdirs() }
        val sharedRoot = Files.createTempDirectory("known-mount-shared").toFile()
        val sharedFile = File(sharedRoot, "guide.md").apply { writeText("shared") }

        try {
            createSymlinkOrSkip(skillDir.toPath().resolve("guide.md"), sharedFile.toPath())

            val allowed = resolveKnownMountFile(
                path = "/skills/demo/guide.md",
                knownMounts = listOf(
                    WorkspaceKnownMount(
                        target = "/skills",
                        source = root,
                        allowedSymlinkRoots = listOf(sharedRoot),
                    )
                ),
            )
            val rejected = resolveKnownMountFile(
                path = "/skills/demo/guide.md",
                knownMounts = listOf(WorkspaceKnownMount("/skills", root)),
            )

            assertEquals(sharedFile.canonicalFile, allowed)
            assertNull(rejected)
        } finally {
            root.deleteRecursively()
            sharedRoot.deleteRecursively()
        }
    }

    private fun createSymlinkOrSkip(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        } catch (e: IOException) {
            assumeNoException(e)
        }
    }
}
