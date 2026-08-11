package me.rerere.rikkahub.data.files

import java.io.File
import kotlin.uuid.Uuid

fun SkillManager.findSkillMetadata(identifier: String): SkillMetadata? {
    return findSkillMetadata(identifier, assistantId = null)
}

fun SkillManager.findSkillMetadata(identifier: String, assistantId: Uuid?): SkillMetadata? {
    val key = identifier.trim()
    if (key.isBlank()) return null
    return listSkillsForAssistant(assistantId).firstOrNull { meta ->
        SkillPaths.isVisibleToAssistant(meta.ownerAssistantId, assistantId) &&
            (meta.name == key || meta.skillDir.name == key)
    }
}

fun SkillManager.resolveSkillDirectory(identifier: String): File? {
    val meta = findSkillMetadata(identifier, assistantId = null) ?: return null
    return meta.skillDir
}

fun SkillManager.readSkillBodyByIdentifier(identifier: String, assistantId: Uuid? = null): String? {
    val meta = findSkillMetadata(identifier, assistantId) ?: return null
    return readSkillFile(resolveSkillFile(meta, "SKILL.md"), stripFrontmatter = true)
}

fun SkillManager.skillsAvailableForSlash(enabledSkills: Set<String>, assistantId: Uuid? = null): List<SkillMetadata> {
    if (enabledSkills.isEmpty()) return emptyList()
    return listSkillsForAssistant(assistantId).filter { meta ->
        SkillPaths.isVisibleToAssistant(meta.ownerAssistantId, assistantId) &&
            (meta.name in enabledSkills || meta.skillDir.name in enabledSkills)
    }
}
