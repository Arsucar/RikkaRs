package me.rerere.rikkahub.data.files

import java.io.File

fun SkillManager.findSkillMetadata(identifier: String): SkillMetadata? {
    val key = identifier.trim()
    if (key.isBlank()) return null
    return listSkills().firstOrNull { it.name == key || it.skillDir.name == key }
}

fun SkillManager.resolveSkillDirectory(identifier: String): File? {
    val meta = findSkillMetadata(identifier) ?: return null
    return meta.skillDir
}

fun SkillManager.readSkillBodyByIdentifier(identifier: String): String? {
    val dir = resolveSkillDirectory(identifier) ?: return null
    val skillFile = dir.resolve("SKILL.md")
    if (!skillFile.exists()) return null
    return SkillFrontmatterParser.extractBody(skillFile.readText())
}

fun SkillManager.skillsAvailableForSlash(enabledSkills: Set<String>): List<SkillMetadata> {
    if (enabledSkills.isEmpty()) return emptyList()
    return listSkills().filter { meta ->
        meta.name in enabledSkills || meta.skillDir.name in enabledSkills
    }
}
