package me.rerere.rikkahub.data.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillCopyFilesTest {
    @Test
    fun `collect preserves skill files with normalized relative paths`() {
        val root = Files.createTempDirectory("skill-copy-test").toFile()
        val skillDir = File(root, "demo").apply { mkdirs() }

        try {
            File(skillDir, "SKILL.md").writeText(
                """
                ---
                name: demo
                description: demo
                ---

                body
                """.trimIndent(),
            )
            File(skillDir, "docs").mkdirs()
            File(skillDir, "docs/guide.md").writeText("guide")

            val files = SkillCopyFiles.collect(
                SkillMetadata(
                    name = "demo",
                    description = "demo",
                    skillDir = skillDir,
                )
            )

            assertEquals(setOf("SKILL.md", "docs/guide.md"), files.keys)
            assertTrue(files.getValue("SKILL.md").toString(Charsets.UTF_8).contains("name: demo"))
            assertEquals("guide", files.getValue("docs/guide.md").toString(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }
}
