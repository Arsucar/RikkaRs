package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.readSkillBodyByIdentifier

fun SkillManager.buildActiveSkillSystemAppend(skillNames: Collection<String>): String? {
    val names = skillNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (names.isEmpty()) return null
    val blocks = names.mapNotNull { name ->
        val body = readSkillBodyByIdentifier(name)?.trim().orEmpty()
        if (body.isBlank()) return@mapNotNull null
        buildString {
            appendLine("<active_skill name=\"$name\">")
            append(body)
            if (!body.endsWith('\n')) {
                appendLine()
            }
            append("</active_skill>")
        }
    }
    if (blocks.isEmpty()) return null
    return buildString {
        appendLine()
        appendLine("## User-activated skills (mandatory for this user message)")
        appendLine(
            "The user chose these skills(`<active_skill>`) with /. Their instructions override default assistant behavior, "
        )
        blocks.forEach { block ->
            append(block)
            appendLine()
        }
    }.trimEnd() + "\n"
}
