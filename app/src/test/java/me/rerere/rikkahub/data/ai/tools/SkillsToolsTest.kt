package me.rerere.rikkahub.data.ai.tools

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillMetadata
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillsToolsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun useSkill_readsDefaultBodyAndSubfile() = runBlocking {
        val skillDir = createSkillDir("demo")
        try {
            File(skillDir, "notes.md").writeText("subfile")
            val tool = createSkillTools(
                enabledSkills = setOf("demo"),
                allSkills = listOf(metadata("demo", skillDir)),
            ).single()

            val body = (tool.execute(json("name" to "demo")).single() as UIMessagePart.Text).text
            val subfile = (
                tool.execute(json("name" to "demo", "path" to "notes.md")).single() as UIMessagePart.Text
                ).text

            assertTrue(body.startsWith("body"))
            assertTrue(body.contains("<skill_files>"))
            assertTrue(subfile.startsWith("subfile"))
            assertTrue(subfile.contains("<skill_files>"))
        } finally {
            skillDir.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun useSkill_rejectsTraversalPath() = runBlocking {
        val skillDir = createSkillDir("demo")
        try {
            val tool = createSkillTools(
                enabledSkills = setOf("demo"),
                allSkills = listOf(metadata("demo", skillDir)),
            ).single()

            val error = runCatching {
                tool.execute(json("name" to "demo", "path" to "../secret.md"))
            }.exceptionOrNull()

            assertTrue(error?.message?.contains("outside the skill directory") == true)
        } finally {
            skillDir.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun useSkill_readsApprovedSymlinkSubfile() = runBlocking {
        val skillDir = createSkillDir("demo")
        val sharedRoot = Files.createTempDirectory("skills-tools-shared").toFile()
        val sharedFile = File(sharedRoot, "guide.md").apply { writeText("shared") }
        try {
            createSymlinkOrSkip(skillDir.toPath().resolve("guide.md"), sharedFile.toPath())
            val tool = createSkillTools(
                enabledSkills = setOf("demo"),
                allSkills = listOf(metadata("demo", skillDir, allowedSymlinkRoots = listOf(sharedRoot))),
            ).single()

            val subfile = (
                tool.execute(json("name" to "demo", "path" to "guide.md")).single() as UIMessagePart.Text
                ).text

            assertTrue(subfile.startsWith("shared"))
            assertTrue(subfile.contains("guide.md"))
        } finally {
            skillDir.parentFile?.deleteRecursively()
            sharedRoot.deleteRecursively()
        }
    }

    @Test
    fun privateSkill_isUnavailableWhenNotInVisibleSkillList() {
        val privateOwner = Uuid.random()
        val otherAssistant = Uuid.random()
        val skillDir = createSkillDir("secret")
        try {
            val tools = createSkillTools(
                enabledSkills = setOf("secret"),
                allSkills = listOf(
                    metadata(
                        name = "secret",
                        skillDir = skillDir,
                        ownerAssistantId = privateOwner,
                    ),
                ).filter { it.ownerAssistantId == otherAssistant },
            )

            assertTrue(tools.isEmpty())
        } finally {
            skillDir.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun use_skill_reads_metadata_directory_when_display_name_differs() = runBlocking {
        val skillDir = tempFolder.newFolder("directory-name")
        skillDir.resolve("SKILL.md").writeText(
            """
                ---
                name: Display Name
                description: Test skill
                ---
                Skill instructions
            """.trimIndent()
        )
        val tool = createSkillTools(
            enabledSkills = setOf("Display Name"),
            allSkills = listOf(
                SkillMetadata(
                    name = "Display Name",
                    description = "Test skill",
                    skillDir = skillDir,
                )
            ),
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("name", "Display Name")
            }
        )

        val text = (result.single() as UIMessagePart.Text).text
        assertTrue(text.startsWith("Skill instructions"))
        assertTrue(text.contains("<skill_files>"))
    }

    @Test
    fun useSkill_fileTreeListsSubfilesInReferences() = runBlocking {
        val skillDir = createSkillDir("demo")
        try {
            val refDir = File(skillDir, "references").apply { mkdirs() }
            File(refDir, "foo.md").writeText("foo")
            File(refDir, "bar.md").writeText("bar")
            val tool = createSkillTools(
                enabledSkills = setOf("demo"),
                allSkills = listOf(metadata("demo", skillDir)),
            ).single()

            val body = (tool.execute(json("name" to "demo")).single() as UIMessagePart.Text).text

            assertTrue(body.contains("<skill_files>"))
            assertTrue(body.contains("references/foo.md"))
            assertTrue(body.contains("references/bar.md"))
            assertTrue(body.contains("SKILL.md"))
        } finally {
            skillDir.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun useSkill_fileTreeAppendedWhenReadingSubfile() = runBlocking {
        val skillDir = createSkillDir("demo")
        try {
            val refDir = File(skillDir, "references").apply { mkdirs() }
            File(refDir, "foo.md").writeText("foo")
            val tool = createSkillTools(
                enabledSkills = setOf("demo"),
                allSkills = listOf(metadata("demo", skillDir)),
            ).single()

            val text = (tool.execute(json("name" to "demo", "path" to "references/foo.md")).single() as UIMessagePart.Text).text

            assertTrue(text.startsWith("foo"))
            assertTrue(text.contains("<skill_files>"))
            assertTrue(text.contains("references/foo.md"))
        } finally {
            skillDir.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun useSkill_fileTreeOnlySkillMdWhenNoSubfiles() = runBlocking {
        val skillDir = createSkillDir("demo")
        try {
            val tool = createSkillTools(
                enabledSkills = setOf("demo"),
                allSkills = listOf(metadata("demo", skillDir)),
            ).single()

            val body = (tool.execute(json("name" to "demo")).single() as UIMessagePart.Text).text

            assertTrue(body.contains("<skill_files>"))
            assertTrue(body.contains("SKILL.md"))
            assertTrue(body.lineSequence().none { it.trim().startsWith("- ") && !it.contains("SKILL.md") })
        } finally {
            skillDir.parentFile?.deleteRecursively()
        }
    }

    private fun createSkillDir(name: String): File {
        val root = Files.createTempDirectory("skills-tools-test").toFile()
        val skillDir = File(root, name).apply { mkdirs() }
        File(skillDir, "SKILL.md").writeText(
            """
            ---
            name: $name
            description: test
            ---

            body
            """.trimIndent(),
        )
        return skillDir
    }

    private fun metadata(
        name: String,
        skillDir: File,
        ownerAssistantId: Uuid? = null,
        allowedSymlinkRoots: List<File> = emptyList(),
    ) = SkillMetadata(
        name = name,
        description = "test",
        skillDir = skillDir,
        ownerAssistantId = ownerAssistantId,
        allowedSymlinkRoots = allowedSymlinkRoots,
    )

    private fun json(vararg values: Pair<String, String>) =
        JsonObject(values.associate { (key, value) -> key to JsonPrimitive(value) })

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
