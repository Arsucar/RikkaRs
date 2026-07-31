package me.rerere.rikkahub.data.files

import android.content.Context
import android.util.Log
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

class SkillManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAG = "SkillManager"
        private const val LIST_CACHE_TTL_MS = 5_000L
    }

    @Volatile
    private var listCache: List<SkillMetadata>? = null
    @Volatile
    private var listCacheTimestamp: Long = 0L

    fun listSkills(createIfMissing: Boolean = true): List<SkillMetadata> {
        if (!createIfMissing) {
            return listSkillsUncached(createIfMissing = false)
        }
        val now = System.currentTimeMillis()
        val cached = listCache
        if (cached != null && now - listCacheTimestamp < LIST_CACHE_TTL_MS) {
            return cached
        }
        val result = listSkillsUncached(createIfMissing = true)
        listCache = result
        listCacheTimestamp = now
        return result
    }

    fun listSkillsForAssistant(
        assistantId: Uuid?,
        createIfMissing: Boolean = true,
    ): List<SkillMetadata> {
        val global = listSkills(createIfMissing)
        if (assistantId == null) return global
        return (listAssistantSkills(assistantId, createIfMissing) + global).distinctBy { it.name }
    }

    fun invalidateListCache() {
        listCache = null
    }

    fun getSkillsDir(createIfMissing: Boolean = true): File {
        val dir = context.filesDir.resolve(FileFolders.SKILLS)
        if (createIfMissing && !dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAssistantSkillsDir(assistantId: Uuid, createIfMissing: Boolean = true): File {
        val dir = context.filesDir
            .resolve(FileFolders.ASSISTANT_SKILLS)
            .resolve(assistantId.toString())
        if (createIfMissing && !dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSkillSharedDir(createIfMissing: Boolean = true): File {
        val dir = context.filesDir.resolve(FileFolders.SKILL_SHARED)
        if (createIfMissing && !dir.exists()) dir.mkdirs()
        return dir
    }

    private fun listSkillsUncached(createIfMissing: Boolean): List<SkillMetadata> {
        val skillsDir = getSkillsDir(createIfMissing)
        return listSkillsInDir(
            skillsDir = skillsDir,
            ownerAssistantId = null,
            allowedSymlinkRoots = listOf(getSkillSharedDir(createIfMissing)),
        )
    }

    fun listAssistantSkills(
        assistantId: Uuid,
        createIfMissing: Boolean = true,
    ): List<SkillMetadata> {
        val skillsDir = getAssistantSkillsDir(assistantId, createIfMissing)
        return listSkillsInDir(
            skillsDir = skillsDir,
            ownerAssistantId = assistantId,
            allowedSymlinkRoots = listOf(getSkillSharedDir(createIfMissing)),
        )
    }

    private fun listSkillsInDir(
        skillsDir: File,
        ownerAssistantId: Uuid?,
        allowedSymlinkRoots: List<File>,
    ): List<SkillMetadata> {
        return skillsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val skillFile = SkillPaths.resolveSkillFile(
                    skillDir = dir,
                    relativePath = "SKILL.md",
                    allowedSymlinkRoots = allowedSymlinkRoots,
                ) ?: return@mapNotNull null
                if (!skillFile.exists()) return@mapNotNull null
                parseSkillFile(skillFile, dir, ownerAssistantId, allowedSymlinkRoots)
            }
            ?: emptyList()
    }

    fun readSkillBody(skillName: String, assistantId: Uuid? = null): String? {
        val skillFile = resolveSkillFile(skillName, "SKILL.md", assistantId) ?: return null
        if (!skillFile.exists()) return null
        return SkillFrontmatterParser.extractBody(skillFile.readText())
    }

    fun readSkillContent(skillName: String, assistantId: Uuid? = null): String? {
        val skillFile = resolveSkillFile(skillName, "SKILL.md", assistantId) ?: return null
        if (!skillFile.exists()) return null
        return skillFile.readText()
    }

    fun saveSkill(name: String, content: String): SkillMetadata? {
        if (!saveSkillFilesAtomically(name, mapOf("SKILL.md" to content))) {
            return null
        }
        val skillDir = resolveSkillDir(name) ?: return null
        return parseSkillFile(skillDir.resolve("SKILL.md"), skillDir, ownerAssistantId = null)
    }

    fun saveAssistantSkill(assistantId: Uuid, name: String, content: String): SkillMetadata? {
        if (!saveAssistantSkillFilesAtomically(assistantId, name, mapOf("SKILL.md" to content))) {
            return null
        }
        val skillDir = resolveAssistantSkillDir(assistantId, name) ?: return null
        return parseSkillFile(skillDir.resolve("SKILL.md"), skillDir, ownerAssistantId = assistantId)
    }

    fun copyGlobalSkillToAssistant(skillName: String, assistantId: Uuid): Boolean {
        val skill = findGlobalSkillMetadata(skillName) ?: return false
        val files = SkillCopyFiles.collect(skill)
        if (!files.containsKey("SKILL.md")) return false
        return saveAssistantSkillFileBytesAtomically(
            assistantId = assistantId,
            skillName = skill.name,
            files = files,
        )
    }

    fun saveAssistantSkillFilesAtomically(
        assistantId: Uuid,
        skillName: String,
        files: Map<String, String>,
    ): Boolean {
        return saveSkillFilesAtomically(
            skillsDir = getAssistantSkillsDir(assistantId),
            skillName = skillName,
            files = files,
        )
    }

    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val skillDir = resolveSkillDir(name) ?: return@withContext false
        val deleted = skillDir.deleteRecursively()
        if (deleted) {
            invalidateListCache()
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.enabledSkills.contains(name)) {
                            assistant.copy(enabledSkills = assistant.enabledSkills - name)
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
        deleted
    }

    suspend fun deleteAssistantSkill(assistantId: Uuid, name: String): Boolean = withContext(Dispatchers.IO) {
        val skillDir = resolveAssistantSkillDir(assistantId, name) ?: return@withContext false
        val deleted = skillDir.deleteRecursively()
        if (deleted) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.id == assistantId && assistant.enabledSkills.contains(name)) {
                            assistant.copy(enabledSkills = assistant.enabledSkills - name)
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
        deleted
    }

    /**
     * 清理所有助手 enabledSkills 中已不存在于磁盘的技能名。
     *
     * 当用户在 App 外直接删除 /skills/ 目录下的技能时，不会走 [deleteSkill] 的清理逻辑，
     * 导致 enabledSkills 残留"幽灵"技能名，使扩展入口角标计数偏大。
     */
    suspend fun pruneOrphanedEnabledSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        val skills = listSkills()
        val globalExisting = skills.mapTo(HashSet()) { it.name }
        settingsStore.update { settings ->
            var changed = false
            val newAssistants = settings.assistants.map { assistant ->
                val visible = (globalExisting + listAssistantSkills(assistant.id).map { it.name }).toHashSet()
                val pruned = assistant.enabledSkills.filterTo(LinkedHashSet()) { it in visible }
                if (pruned.size != assistant.enabledSkills.size) {
                    changed = true
                    assistant.copy(enabledSkills = pruned)
                } else {
                    assistant
                }
            }
            if (changed) settings.copy(assistants = newAssistants) else settings
        }
        skills
    }

    fun getSkillDir(skillName: String): File? = resolveSkillDir(skillName)

    fun getAssistantSkillDir(assistantId: Uuid, skillName: String): File? =
        resolveAssistantSkillDir(assistantId, skillName)

    fun saveSkillFile(skillName: String, relativePath: String, content: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        invalidateListCache()
        return true
    }

    fun saveAssistantSkillFile(
        assistantId: Uuid,
        skillName: String,
        relativePath: String,
        content: String,
    ): Boolean {
        val skillDir = resolveAssistantSkillDir(assistantId, skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        return true
    }

    fun saveSkillFilesAtomically(skillName: String, files: Map<String, String>): Boolean {
        return saveSkillFilesAtomically(
            skillsDir = getSkillsDir(),
            skillName = skillName,
            files = files,
        )
    }

    private fun saveSkillFilesAtomically(
        skillsDir: File,
        skillName: String,
        files: Map<String, String>,
    ): Boolean {
        return saveSkillFileBytesAtomically(
            skillsDir = skillsDir,
            skillName = skillName,
            files = files.mapValues { it.value.toByteArray() },
        )
    }

    fun saveSkillFileBytesAtomically(skillName: String, files: Map<String, ByteArray>): Boolean {
        return saveSkillFileBytesAtomically(getSkillsDir(), skillName, files)
    }

    fun saveAssistantSkillFileBytesAtomically(
        assistantId: Uuid,
        skillName: String,
        files: Map<String, ByteArray>,
    ): Boolean {
        return saveSkillFileBytesAtomically(getAssistantSkillsDir(assistantId), skillName, files)
    }

    private fun saveSkillFileBytesAtomically(
        skillsDir: File,
        skillName: String,
        files: Map<String, ByteArray>,
    ): Boolean {
        val targetDir = SkillPaths.resolveSkillDir(skillsDir, skillName) ?: return false
        val stagingDir = createTempSkillDir(skillsDir, skillName, "staging") ?: return false
        var backupDir: File? = null

        try {
            for ((relativePath, content) in files) {
                val target = SkillPaths.resolveSkillFile(stagingDir, relativePath) ?: return false
                target.parentFile?.mkdirs()
                target.writeBytes(content)
            }

            if (!stagingDir.resolve("SKILL.md").exists()) return false

            if (targetDir.exists()) {
                backupDir = createTempSkillDir(skillsDir, skillName, "backup") ?: return false
                if (!targetDir.renameTo(backupDir)) return false
            }

            if (!stagingDir.renameTo(targetDir)) {
                if (backupDir != null && !targetDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                return false
            }

            backupDir?.deleteRecursively()
            invalidateListCache()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "saveSkillFilesAtomically: Failed to save $skillName", e)
            if (backupDir != null && !targetDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            return false
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            if (backupDir?.exists() == true && targetDir.exists()) {
                backupDir.deleteRecursively()
            }
        }
    }

    fun deleteSkillFile(skillName: String, relativePath: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        val deleted = target.delete()
        if (deleted) invalidateListCache()
        return deleted
    }

    fun deleteAssistantSkillFile(assistantId: Uuid, skillName: String, relativePath: String): Boolean {
        val skillDir = resolveAssistantSkillDir(assistantId, skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        return target.delete()
    }

    fun resolveSkillFile(skillName: String, relativePath: String, assistantId: Uuid? = null): File? {
        val metadata = findVisibleSkillMetadata(skillName, assistantId) ?: return null
        return resolveSkillFile(metadata, relativePath)
    }

    fun resolveMountedSkillFile(rootfsPath: String, assistantId: Uuid? = null): File? {
        if (assistantId != null && rootfsPath.replace('\\', '/').trimEnd('/').startsWith("/skills_private/")) {
            return SkillPaths.resolveMountedSkillFile(
                skillsRoot = getAssistantSkillsDir(assistantId),
                rootfsPath = rootfsPath,
                allowedSymlinkRoots = listOf(getSkillSharedDir()),
                mountTarget = "/skills_private",
            )
        }
        return SkillPaths.resolveMountedSkillFile(
            skillsRoot = getSkillsDir(),
            rootfsPath = rootfsPath,
            allowedSymlinkRoots = listOf(getSkillSharedDir()),
        )
    }

    fun resolveSkillFile(skill: SkillMetadata, relativePath: String): File? {
        return SkillPaths.resolveSkillFile(
            skillDir = skill.skillDir,
            relativePath = relativePath,
            allowedSymlinkRoots = skill.allowedSymlinkRoots,
        )
    }

    private fun resolveSkillDir(skillName: String): File? {
        return SkillPaths.resolveSkillDir(getSkillsDir(), skillName)
    }

    private fun resolveAssistantSkillDir(assistantId: Uuid, skillName: String): File? {
        return SkillPaths.resolveSkillDir(getAssistantSkillsDir(assistantId), skillName)
    }

    private fun findGlobalSkillMetadata(skillName: String): SkillMetadata? {
        return listSkills().firstOrNull { it.name == skillName || it.skillDir.name == skillName }
    }

    private fun findVisibleSkillMetadata(skillName: String, assistantId: Uuid?): SkillMetadata? {
        if (assistantId != null) {
            listAssistantSkills(assistantId).firstOrNull {
                it.name == skillName || it.skillDir.name == skillName
            }?.let { return it }
        }
        return findGlobalSkillMetadata(skillName)
    }

    private fun createTempSkillDir(skillsRoot: File, skillName: String, suffix: String): File? {
        repeat(100) { attempt ->
            val candidate = skillsRoot.resolve(".$skillName.$suffix.$attempt.tmp")
            if (!candidate.exists() && candidate.mkdirs()) {
                return candidate
            }
        }
        return null
    }

    private fun parseSkillFile(
        skillFile: File,
        skillDir: File,
        ownerAssistantId: Uuid?,
        allowedSymlinkRoots: List<File> = listOf(getSkillSharedDir()),
    ): SkillMetadata? {
        return runCatching {
            val content = skillFile.readText()
            val frontmatter = SkillFrontmatterParser.parse(content)
            val name = frontmatter["name"]?.takeIf { it.isNotBlank() } ?: return null
            val description = frontmatter["description"]?.takeIf { it.isNotBlank() } ?: return null
            SkillMetadata(
                name = name,
                description = description,
                compatibility = frontmatter["compatibility"],
                allowedTools = frontmatter["allowed-tools"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                skillDir = skillDir,
                ownerAssistantId = ownerAssistantId,
                allowedSymlinkRoots = allowedSymlinkRoots,
            )
        }.getOrElse {
            Log.w(TAG, "parseSkillFile: Failed to parse ${skillFile.absolutePath}", it)
            null
        }
    }
}

internal object SkillCopyFiles {
    fun collect(skill: SkillMetadata): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        skill.skillDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(skill.skillDir).path.replace(File.separatorChar, '/')
                val resolved = SkillPaths.resolveSkillFile(
                    skillDir = skill.skillDir,
                    relativePath = relativePath,
                    allowedSymlinkRoots = skill.allowedSymlinkRoots,
                ) ?: return@forEach
                files[relativePath] = resolved.readBytes()
            }
        return files
    }
}

data class SkillMetadata(
    val name: String,
    val description: String,
    val compatibility: String? = null,
    val allowedTools: List<String> = emptyList(),
    val skillDir: File,
    val ownerAssistantId: Uuid? = null,
    val allowedSymlinkRoots: List<File> = emptyList(),
) {
    val skillFile: File get() = skillDir.resolve("SKILL.md")
    val isAssistantPrivate: Boolean get() = ownerAssistantId != null
}
