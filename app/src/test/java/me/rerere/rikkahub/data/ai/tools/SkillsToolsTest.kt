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
import org.junit.Assert.assertEquals
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

            assertEquals("body", body)
            assertEquals("subfile", subfile)
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

            assertEquals("shared", subfile)
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

        assertEquals("Skill instructions", (result.single() as UIMessagePart.Text).text)
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
