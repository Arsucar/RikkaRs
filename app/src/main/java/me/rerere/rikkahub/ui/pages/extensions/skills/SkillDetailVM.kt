package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import java.io.File
import kotlin.uuid.Uuid

data class SkillFile(
    val file: File,
    val relativePath: String,
)

data class SkillAssistantTarget(
    val assistant: Assistant,
    val hasPrivateSkill: Boolean,
)

sealed class SkillFileNode {
    data class FileNode(val skillFile: SkillFile) : SkillFileNode()
    data class DirNode(
        val name: String,
        val relativePath: String,
        val children: List<SkillFileNode>,
    ) : SkillFileNode()
}

class SkillDetailVM(
    private val skillManager: SkillManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _tree = MutableStateFlow<List<SkillFileNode>>(emptyList())
    val tree = _tree.asStateFlow()

    private val _assistantTargets = MutableStateFlow<List<SkillAssistantTarget>>(emptyList())
    val assistantTargets = _assistantTargets.asStateFlow()

    private var skillName = ""
    private var assistantId: Uuid? = null

    fun init(name: String, assistantId: Uuid? = null) {
        if (skillName == name && this.assistantId == assistantId) return
        skillName = name
        this.assistantId = assistantId
        loadFiles()
        if (assistantId == null) {
            loadAssistantTargets()
        } else {
            _assistantTargets.value = emptyList()
        }
    }

    fun loadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val ownerAssistantId = assistantId
            val dir = if (ownerAssistantId != null) {
                skillManager.getAssistantSkillDir(ownerAssistantId, skillName)
            } else {
                skillManager.getSkillDir(skillName)
            }
                ?: return@launch
            _tree.value = buildTree(dir, dir)
        }
    }

    private fun loadAssistantTargets() {
        viewModelScope.launch(Dispatchers.IO) {
            loadAssistantTargetsNow()
        }
    }

    private fun loadAssistantTargetsNow() {
        val assistants = settingsStore.settingsFlow.value.assistants
        val privateSkillNamesByAssistant = assistants.associate { assistant ->
            assistant.id to skillManager.listAssistantSkills(assistant.id).mapTo(HashSet()) { it.name }
        }
        _assistantTargets.value = SkillAssistantTargets.build(
            assistants = assistants,
            privateSkillNamesByAssistant = privateSkillNamesByAssistant,
            skillName = skillName,
        )
    }

    private fun buildTree(root: File, dir: File): List<SkillFileNode> {
        val items = dir.listFiles()?.toList() ?: return emptyList()
        val files = items
            .filter { it.isFile }
            .sortedWith(compareBy({ it.name != "SKILL.md" }, { it.name }))
            .map { f -> SkillFileNode.FileNode(SkillFile(f, f.relativeTo(root).path)) }
        val dirs = items
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { d -> SkillFileNode.DirNode(d.name, d.relativeTo(root).path, buildTree(root, d)) }
        return dirs + files
    }

    fun readFile(skillFile: SkillFile): String = skillFile.file.readText()

    // Returns null on success, error message on failure
    fun saveFile(relativePath: String, content: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (relativePath == "SKILL.md") {
                val name = SkillFrontmatterParser.parse(content)["name"]
                if (name != skillName) {
                    withContext(Dispatchers.Main) { onResult("不允许修改技能名称（name 字段必须为 \"$skillName\"）") }
                    return@launch
                }
            }
            val success = assistantId?.let { id ->
                skillManager.saveAssistantSkillFile(id, skillName, relativePath, content)
            } ?: skillManager.saveSkillFile(skillName, relativePath, content)
            loadFiles()
            withContext(Dispatchers.Main) { onResult(if (success) null else "保存失败") }
        }
    }

    fun copyGlobalSkillToAssistant(assistantId: Uuid, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = skillManager.copyGlobalSkillToAssistant(skillName, assistantId)
            if (success) {
                settingsStore.update { settings ->
                    settings.copy(
                        assistants = settings.assistants.map { assistant ->
                            if (assistant.id == assistantId) {
                                assistant.copy(enabledSkills = assistant.enabledSkills + skillName)
                            } else {
                                assistant
                            }
                        }
                    )
                }
                loadAssistantTargetsNow()
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    fun deleteFile(skillFile: SkillFile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = assistantId?.let { id ->
                skillManager.deleteAssistantSkillFile(id, skillName, skillFile.relativePath)
            } ?: skillManager.deleteSkillFile(skillName, skillFile.relativePath)
            if (success) loadFiles()
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }
}

internal object SkillAssistantTargets {
    fun build(
        assistants: List<Assistant>,
        privateSkillNamesByAssistant: Map<Uuid, Set<String>>,
        skillName: String,
    ): List<SkillAssistantTarget> {
        return assistants.map { assistant ->
            SkillAssistantTarget(
                assistant = assistant,
                hasPrivateSkill = privateSkillNamesByAssistant[assistant.id]?.contains(skillName) == true,
            )
        }
    }
}
