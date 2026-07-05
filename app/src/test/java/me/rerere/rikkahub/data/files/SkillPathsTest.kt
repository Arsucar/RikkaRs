package me.rerere.rikkahub.data.files

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import kotlin.uuid.Uuid

class SkillPathsTest {
    @Test
    fun `parse supports CRLF frontmatter`() {
        val content = "---\r\nname: test-skill\r\ndescription: test\r\n---\r\n\r\nbody"

        val frontmatter = SkillFrontmatterParser.parse(content)

        assertEquals("test-skill", frontmatter["name"])
        assertEquals("test", frontmatter["description"])
        assertEquals("body", SkillFrontmatterParser.extractBody(content))
    }

    @Test
    fun `resolve skill dir rejects traversal and nested names`() {
        val skillsRoot = Files.createTempDirectory("skills-root").toFile()

        try {
            assertNull(SkillPaths.resolveSkillDir(skillsRoot, "../upload"))
            assertNull(SkillPaths.resolveSkillDir(skillsRoot, "foo/bar"))
            assertNull(SkillPaths.resolveSkillDir(skillsRoot, "foo\\bar"))
            assertNotNull(SkillPaths.resolveSkillDir(skillsRoot, "valid-skill"))
        } finally {
            skillsRoot.deleteRecursively()
        }
    }

    @Test
    fun `resolve skill file rejects sibling prefix escape`() {
        val skillsRoot = Files.createTempDirectory("skills-root").toFile()
        val skillDir = File(skillsRoot, "foo").apply { mkdirs() }
        File(skillsRoot, "foobar").apply { mkdirs() }

        try {
            val safeFile = SkillPaths.resolveSkillFile(skillDir, "notes.md")
            val escapedFile = SkillPaths.resolveSkillFile(skillDir, "../foobar/secret.md")

            assertEquals(File(skillDir, "notes.md").canonicalFile, safeFile)
            assertNull(escapedFile)
        } finally {
            skillsRoot.deleteRecursively()
        }
    }

    @Test
    fun `resolve skill file allows symlink target under explicit allowed root`() {
        val root = Files.createTempDirectory("skills-root").toFile()
        val skillDir = File(root, "foo").apply { mkdirs() }
        val sharedDir = File(root, "shared").apply { mkdirs() }
        val sharedFile = File(sharedDir, "guide.md").apply { writeText("shared") }

        try {
            createSymlinkOrSkip(skillDir.toPath().resolve("guide.md"), sharedFile.toPath())

            val allowed = SkillPaths.resolveSkillFile(
                skillDir = skillDir,
                relativePath = "guide.md",
                allowedSymlinkRoots = listOf(sharedDir),
            )
            val rejected = SkillPaths.resolveSkillFile(skillDir, "guide.md")

            assertEquals(sharedFile.canonicalFile, allowed)
            assertNull(rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `resolve skill file rejects traversal even when target root is allowed`() {
        val root = Files.createTempDirectory("skills-root").toFile()
        val skillDir = File(root, "foo").apply { mkdirs() }
        val sharedDir = File(root, "shared").apply { mkdirs() }

        try {
            val escaped = SkillPaths.resolveSkillFile(
                skillDir = skillDir,
                relativePath = "../shared/guide.md",
                allowedSymlinkRoots = listOf(sharedDir),
            )

            assertNull(escaped)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `resolve mounted skill file maps global skills path`() {
        val root = Files.createTempDirectory("skills-root").toFile()
        val skillDir = File(root, "foo").apply { mkdirs() }
        val skillFile = File(skillDir, "SKILL.md").apply { writeText("body") }

        try {
            val resolved = SkillPaths.resolveMountedSkillFile(root, "/skills/foo/SKILL.md")
            val traversal = SkillPaths.resolveMountedSkillFile(root, "/skills/foo/../bar/SKILL.md")

            assertEquals(skillFile.canonicalFile, resolved)
            assertNull(traversal)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `skill visibility allows global and owner only`() {
        val owner = Uuid.random()
        val other = Uuid.random()

        assertTrue(SkillPaths.isVisibleToAssistant(ownerAssistantId = null, requesterAssistantId = null))
        assertTrue(SkillPaths.isVisibleToAssistant(ownerAssistantId = null, requesterAssistantId = other))
        assertTrue(SkillPaths.isVisibleToAssistant(ownerAssistantId = owner, requesterAssistantId = owner))
        assertFalse(SkillPaths.isVisibleToAssistant(ownerAssistantId = owner, requesterAssistantId = other))
        assertFalse(SkillPaths.isVisibleToAssistant(ownerAssistantId = owner, requesterAssistantId = null))
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
