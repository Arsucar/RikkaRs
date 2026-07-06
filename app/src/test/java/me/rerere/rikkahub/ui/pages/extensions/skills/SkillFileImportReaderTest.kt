package me.rerere.rikkahub.ui.pages.extensions.skills

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SkillFileImportReaderTest {
    @Test
    fun readMarkdown_importsSingleSkillBundle() {
        val bytes = skillMarkdown("demo", "demo description").toByteArray()

        val bundles = SkillFileImportReader.read("SKILL.md", bytes)

        assertEquals(1, bundles.size)
        assertEquals("demo", bundles.single().name)
        assertEquals("body", bundles.single().files.getValue("SKILL.md").toString(Charsets.UTF_8).substringAfter("\n\n"))
    }

    @Test
    fun readZip_splitsMultipleSkillsAndRejectsTraversalEntries() {
        val zip = zipBytes(
            "alpha/SKILL.md" to skillMarkdown("alpha", "alpha description"),
            "alpha/guide.md" to "alpha guide",
            "beta/SKILL.md" to skillMarkdown("beta", "beta description"),
            "../secret.md" to "secret",
        )

        val bundles = SkillFileImportReader.read("skills.zip", zip)

        assertEquals(listOf("alpha", "beta"), bundles.map { it.name })
        assertEquals("alpha guide", bundles.first { it.name == "alpha" }.files.getValue("guide.md").toString(Charsets.UTF_8))
        assertFalse(bundles.any { bundle -> bundle.files.keys.any { it.contains("secret") } })
    }

    private fun skillMarkdown(name: String, description: String): String {
        return """
            ---
            name: $name
            description: $description
            ---

            body
        """.trimIndent()
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
