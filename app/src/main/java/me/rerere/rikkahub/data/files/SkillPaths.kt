package me.rerere.rikkahub.data.files

import java.io.File
import kotlin.uuid.Uuid

internal object SkillPaths {
    fun resolveSkillDir(skillsRoot: File, skillName: String): File? {
        if (skillName.isBlank()) return null
        if (skillName == "." || skillName == "..") return null
        if (skillName.contains('/') || skillName.contains('\\')) return null

        val canonicalRoot = skillsRoot.canonicalFile
        val canonicalDir = canonicalRoot.resolve(skillName).canonicalFile
        val parent = canonicalDir.parentFile ?: return null

        if (parent != canonicalRoot) return null
        if (!canonicalDir.isSameOrInside(canonicalRoot)) return null

        return canonicalDir
    }

    fun resolveSkillFile(
        skillDir: File,
        relativePath: String,
        allowedSymlinkRoots: List<File> = emptyList(),
    ): File? {
        val normalizedRelativePath = normalizeRelativePath(relativePath) ?: return null

        val canonicalSkillDir = skillDir.canonicalFile
        val lexicalTarget = canonicalSkillDir.toPath()
            .resolve(normalizedRelativePath)
            .normalize()
            .toFile()
        if (!lexicalTarget.isSameOrInsidePath(canonicalSkillDir)) return null

        val canonicalTarget = lexicalTarget.canonicalFile
        val allowedRoots = listOf(canonicalSkillDir) + allowedSymlinkRoots.map { it.canonicalFile }

        return canonicalTarget.takeIf { target ->
            allowedRoots.any { root -> target.isSameOrInside(root) }
        }
    }

    fun resolveMountedSkillFile(
        skillsRoot: File,
        rootfsPath: String,
        allowedSymlinkRoots: List<File> = emptyList(),
    ): File? {
        val normalized = rootfsPath.replace('\\', '/').trimEnd('/')
        if (normalized == "/skills") return null
        if (!normalized.startsWith("/skills/")) return null

        val relative = normalized.removePrefix("/skills/")
        val skillName = relative.substringBefore('/')
        val skillRelativePath = relative.substringAfter('/', missingDelimiterValue = "")
        if (skillRelativePath.isBlank()) return null

        val skillDir = resolveSkillDir(skillsRoot, skillName) ?: return null
        return resolveSkillFile(skillDir, skillRelativePath, allowedSymlinkRoots)
    }

    fun isVisibleToAssistant(ownerAssistantId: Uuid?, requesterAssistantId: Uuid?): Boolean {
        return ownerAssistantId == null || ownerAssistantId == requesterAssistantId
    }

    private fun normalizeRelativePath(relativePath: String): String? {
        val path = relativePath.replace('\\', '/').trim()
        if (path.isBlank()) return null
        if (path.startsWith("/")) return null
        if (Regex("^[A-Za-z]:[\\\\/].*").matches(path)) return null
        if (path.contains('\u0000')) return null
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun File.isSameOrInside(root: File): Boolean {
        val rootPath = root.canonicalFile.path
        val currentPath = canonicalFile.path
        return currentPath == rootPath || currentPath.startsWith(rootPath + File.separator)
    }

    private fun File.isSameOrInsidePath(root: File): Boolean {
        val rootPath = root.absoluteFile.path
        val currentPath = absoluteFile.path
        return currentPath == rootPath || currentPath.startsWith(rootPath + File.separator)
    }
}
