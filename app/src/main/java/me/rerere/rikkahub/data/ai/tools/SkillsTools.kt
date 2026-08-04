package me.rerere.rikkahub.data.ai.tools

import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.files.SkillPaths

fun createSkillTools(
    enabledSkills: Set<String>,
    allSkills: List<SkillMetadata>,
): List<Tool> {
    val available = allSkills.filter { it.name in enabledSkills }
    if (available.isEmpty()) return emptyList()
    val availableByName = available.associateBy { it.name }

    return listOf(
        Tool(
            name = "use_skill",
            description = """
                Load and apply a skill to get specialized instructions or capabilities.
                Call this tool when the user's request matches one of the available skills.
            """.trimIndent(),
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("**Skills**")
                    appendLine("You have access to the following skills. Use the `use_skill` tool to load a skill's instructions when the user's request matches.")
                    appendLine("<available_skills>")
                    available.forEach { skill ->
                        appendLine("  <skill>")
                        appendLine("    <name>${skill.name}</name>")
                        appendLine("    <description>${skill.description}</description>")
                        appendLine("  </skill>")
                    }
                    append("</available_skills>")
                    appendLine()
                }
            },
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "The name of the skill to use")
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Optional relative path to a file inside the skill directory. Omit to read the default SKILL.md instructions. Only use paths extracted from Markdown links in the SKILL.md content. Do NOT guess or infer paths."
                            )
                        })
                    },
                    required = listOf("name")
                )
            },
            execute = {
                val name = it.jsonObject["name"]?.jsonPrimitive?.content
                    ?: error("name is required")
                val skill = availableByName[name]
                    ?: error(
                        "Skill '$name' is not available. Available skills: ${available.joinToString { it.name }}"
                    )
                val path = it.jsonObject["path"]?.jsonPrimitive?.content
                val content = if (path.isNullOrBlank()) {
                    require(skill.skillFile.exists()) { "Skill '$name' not found" }
                    SkillFrontmatterParser.extractBody(skill.skillFile.readText())
                } else {
                    val target = SkillPaths.resolveSkillFile(
                        skillDir = skill.skillDir,
                        relativePath = path,
                        allowedSymlinkRoots = skill.allowedSymlinkRoots,
                    )
                        ?: error("Path '$path' is outside the skill directory")
                    require(target.exists()) { "File '$path' not found in skill '$name'" }
                    target.readText()
                }
                val fileTree = skill.skillDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.relativeTo(skill.skillDir).path.replace(File.separatorChar, '/') }
                    .sorted()
                    .joinToString("\n") { "  - $it" }
                val contentWithTree = content + "\n\n<skill_files>\n$fileTree\n</skill_files>"
                listOf(UIMessagePart.Text(contentWithTree))
            }
        )
    )
}
