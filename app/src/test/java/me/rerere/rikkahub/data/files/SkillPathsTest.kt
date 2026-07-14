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
    fun `parse preserves literal block scalar newlines and colons`() {
        val content = """
            ---
            name: literal-skill
            description: |-
              First line: with a colon
              第二行
            compatibility: Android
            ---
            body
        """.trimIndent()

        val frontmatter = SkillFrontmatterParser.parse(content)

        assertEquals("First line: with a colon\n第二行", frontmatter["description"])
        assertEquals("Android", frontmatter["compatibility"])
        assertEquals("body", SkillFrontmatterParser.extractBody(content))
    }

    @Test
    fun `parse folds adjacent lines and preserves blank line paragraphs`() {
        val content = """
            ---
            name: folded-skill
            description: >-
              First line
              continues here

              New paragraph
            allowed-tools: Read Write
            ---
        """.trimIndent()

        val frontmatter = SkillFrontmatterParser.parse(content)

        assertEquals("First line continues here\nNew paragraph", frontmatter["description"])
        assertEquals("Read Write", frontmatter["allowed-tools"])
    }

    @Test
    fun `parse applies clip strip and keep chomping`() {
        fun description(marker: String): String? = SkillFrontmatterParser.parse(
            "---\ndescription: $marker\n  line\n\n---\n"
        )["description"]

        assertEquals("line\n", description("|"))
        assertEquals("line", description("|-"))
        assertEquals("line\n\n", description("|+"))
    }

    @Test
    fun `parse supports CRLF folded block without consuming following key`() {
        val content = "---\r\nname: crlf-skill\r\ndescription: >-\r\n  first\r\n  second: value\r\ncompatibility: JVM\r\n---\r\nbody"

        val frontmatter = SkillFrontmatterParser.parse(content)

        assertEquals("first second: value", frontmatter["description"])
        assertEquals("JVM", frontmatter["compatibility"])
    }

    @Test
    fun `parse keeps single line and quoted block markers compatible`() {
        val plain = SkillFrontmatterParser.parse("---\ndescription: plain text\n---\n")
        val quoted = SkillFrontmatterParser.parse("---\ndescription: \"|\"\n---\n")

        assertEquals("plain text", plain["description"])
        assertEquals("|", quoted["description"])
    }

    @Test
    fun `parse honors explicit block indentation`() {
        val content = "---\ndescription: |2-\n    indented content\nnext: value\n---\n"

        val frontmatter = SkillFrontmatterParser.parse(content)

        assertEquals("  indented content", frontmatter["description"])
        assertEquals("value", frontmatter["next"])
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
    fun `resolve mounted skill file maps assistant private skills path`() {
        val root = Files.createTempDirectory("assistant-skills-root").toFile()
        val skillDir = File(root, "foo").apply { mkdirs() }
        val skillFile = File(skillDir, "SKILL.md").apply { writeText("body") }

        try {
            val resolved = SkillPaths.resolveMountedSkillFile(
                skillsRoot = root,
                rootfsPath = "/skills_private/foo/SKILL.md",
                mountTarget = "/skills_private",
            )
            val globalPath = SkillPaths.resolveMountedSkillFile(
                skillsRoot = root,
                rootfsPath = "/skills/foo/SKILL.md",
                mountTarget = "/skills_private",
            )
            val traversal = SkillPaths.resolveMountedSkillFile(
                skillsRoot = root,
                rootfsPath = "/skills_private/foo/../bar/SKILL.md",
                mountTarget = "/skills_private",
            )

            assertEquals(skillFile.canonicalFile, resolved)
            assertNull(globalPath)
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
