package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.pruneExtensionIds
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MEMORY_TABLE_DELETED_BY_USER_UI
import me.rerere.rikkahub.data.repository.MemoryTableSoftDeleteResult
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import me.rerere.rikkahub.data.repository.MemoryTableDocumentSnapshot
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillFileImportReader
import kotlin.uuid.Uuid

private const val TAG = "AssistantDetailVM"

class AssistantDetailVM(
    private val id: String,
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val memoryTableRepository: MemoryTableRepository,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    conversationTagRepository: ConversationTagRepository,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    private val _assistantPrivateSkills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val assistantPrivateSkills = _assistantPrivateSkills.asStateFlow()

    init {
        reloadSkills()
    }

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val mcpServerConfigs = settingsStore
        .settingsFlow.map { settings ->
            settings.mcpServers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant()
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Assistant()
        )

    val memories = assistant
        .flatMapLatest {
            memoryRepository.getEffectiveMemoriesFlow(assistantId.toString())
        }
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val memoryTableTemplates = memoryTableRepository
        .getEffectiveTemplatesFlow(assistantId.toString())
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val memoryTableDocuments = memoryTableRepository
        .getAssistantMemoryDocumentsFlow(assistantId.toString())
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    private val memoryTableTrashReloadRequest = MutableStateFlow(0)

    internal val memoryTableTrashUiState = memoryTableTrashReloadRequest
        .flatMapLatest {
            memoryTableRepository
                .getDeletedDocumentsForAssistantFlow(assistantId.toString())
                .map<List<MemoryTableDocument>, MemoryTableTrashUiState> { documents ->
                    if (documents.isEmpty()) {
                        MemoryTableTrashUiState.Empty
                    } else {
                        MemoryTableTrashUiState.Success(documents)
                    }
                }
                .catch { emit(MemoryTableTrashUiState.Error(it)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MemoryTableTrashUiState.Loading,
        )

    fun reloadMemoryTableTrash() {
        memoryTableTrashReloadRequest.value++
    }

    suspend fun getMemoryTableDocumentsForEditor(conversationId: String?): List<MemoryTableDocument> =
        memoryTableRepository.getEffectiveDocuments(
            assistantId = assistantId.toString(),
            conversationId = conversationId,
        )

    val providers = settingsStore
        .settingsFlow
        .map { settings ->
            settings.providers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val tags = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistantTags
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val conversationTags = conversationTagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository
        .listFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun updateTags(tagIds: List<Uuid>, tags: List<Tag>) {
        viewModelScope.launch {
            val settings = settings.value
            settingsStore.update(
                settings = settings.copy(
                    assistantTags = tags
                )
            )
            update(
                assistant.value.copy(
                    tags = tagIds.toList()
                )
            )
            Log.d(TAG, "updateTags: ${tagIds.joinToString(",")}")
            cleanupUnusedTags()
        }
    }

    fun cleanupUnusedTags() {
        viewModelScope.launch {
            val settings = settings.value
            val validTagIds = settings.assistantTags.map { it.id }.toSet()

            // 清理 assistant 中的无效 tag id
            val cleanedAssistants = settings.assistants.map { assistant ->
                val validTags = assistant.tags.filter { tagId ->
                    validTagIds.contains(tagId)
                }
                if (validTags.size != assistant.tags.size) {
                    assistant.copy(tags = validTags)
                } else {
                    assistant
                }
            }

            // 获取清理后的 assistant 中使用的 tag id
            val usedTagIds = cleanedAssistants.flatMap { it.tags }.toSet()

            // 清理未使用的 tags
            val cleanedTags = settings.assistantTags.filter { tag ->
                usedTagIds.contains(tag.id)
            }

            // 检查是否需要更新
            val needUpdateAssistants = cleanedAssistants != settings.assistants
            val needUpdateTags = cleanedTags.size != settings.assistantTags.size

            if (needUpdateAssistants || needUpdateTags) {
                settingsStore.update(
                    settings = settings.copy(
                        assistants = cleanedAssistants,
                        assistantTags = cleanedTags
                    )
                )
            }
        }
    }

    fun update(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            val prunedAssistant = assistant.pruneExtensionIds(settings)
            settings.getAssistantById(prunedAssistant.id)?.let { oldAssistant ->
                checkAvatarDelete(old = oldAssistant, new = prunedAssistant) // 删除旧头像
                checkBackgroundDelete(old = oldAssistant, new = prunedAssistant) // 删除旧背景
            }
            settingsStore.updateAssistantConfig(prunedAssistant)
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        updateMemoryCapabilities { assistant -> assistant.copy(enableMemory = enabled) }
    }

    fun setMemoryTableEnabled(enabled: Boolean) {
        updateMemoryCapabilities { assistant -> assistant.copy(enableMemoryTable = enabled) }
    }

    private fun updateMemoryCapabilities(transform: (Assistant) -> Assistant) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.id == assistantId) transform(assistant) else assistant
                    }
                )
            }
        }
    }

    fun reloadSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            loadSkillsNow()
        }
    }

    private fun loadSkillsNow() {
        _skills.value = skillManager.listSkillsForAssistant(assistantId)
        _assistantPrivateSkills.value = skillManager.listAssistantSkills(assistantId)
    }

    fun saveAssistantSkill(name: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = skillManager.saveAssistantSkill(assistantId, name, content)
            loadSkillsNow()
            withContext(Dispatchers.Main) {
                onResult(result != null)
            }
        }
    }

    fun importAssistantSkillFromFile(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = FileUtils.getFileNameFromUri(appContext, uri).orEmpty()
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        withContext(Dispatchers.Main) { onResult(false, "无法读取文件") }
                        return@launch
                    }

                val importedNames = SkillFileImportReader.read(fileName, bytes).map { bundle ->
                    val saved = skillManager.saveAssistantSkillFileBytesAtomically(
                        assistantId = assistantId,
                        skillName = bundle.name,
                        files = bundle.files,
                    )
                    if (!saved) {
                        error("保存失败：${bundle.name}")
                    }
                    bundle.name
                }.distinct()

                loadSkillsNow()
                withContext(Dispatchers.Main) {
                    onResult(true, importedNames.joinToString())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "未知错误") }
            }
        }
    }

    fun deleteAssistantSkill(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            skillManager.deleteAssistantSkill(assistantId, name)
            loadSkillsNow()
        }
    }

    fun addMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            memoryRepository.addMemory(
                assistantId = assistantId.toString(),
                content = memory.content,
                scope = memory.scope,
            )
        }
    }

    fun updateMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            memoryRepository.updateMemory(
                id = memory.id,
                content = memory.content,
                actorAssistantId = assistantId.toString(),
                scope = memory.scope,
            )
        }
    }

    fun deleteMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(
                id = memory.id,
                actorAssistantId = assistantId.toString(),
            )
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun upsertHook(hook: ConversationHook) {
        mutateHooks { hooks ->
            val existing = hooks.firstOrNull { it.id == hook.id }
            if (existing == null) {
                hooks + hook.copy(configVersion = 1)
            } else {
                hooks.map { current ->
                    if (current.id == hook.id) {
                        hook.copy(configVersion = existing.configVersion + 1)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun setHookEnabled(hookId: Uuid, enabled: Boolean) {
        mutateHooks { hooks ->
            hooks.map { hook ->
                if (hook.id == hookId && hook.enabled != enabled) {
                    hook.copy(enabled = enabled, configVersion = hook.configVersion + 1)
                } else {
                    hook
                }
            }
        }
    }

    fun moveHook(fromIndex: Int, toIndex: Int) {
        mutateHooks { hooks ->
            if (fromIndex !in hooks.indices || toIndex !in hooks.indices || fromIndex == toIndex) {
                hooks
            } else {
                hooks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    }

    fun deleteHook(hookId: Uuid) {
        mutateHooks { hooks -> hooks.filterNot { it.id == hookId } }
    }

    private fun mutateHooks(transform: (List<ConversationHook>) -> List<ConversationHook>) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.id == assistantId) {
                            assistant.copy(hooks = transform(assistant.hooks))
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
    }

    suspend fun getMemoryTableTemplateForEditor(templateId: String): MemoryTableTemplate? =
        memoryTableRepository.getEffectiveTemplate(
            id = templateId,
            assistantId = assistantId.toString(),
        )

    fun upsertMemoryTableTemplate(
        template: MemoryTableTemplate,
        requestedScopeType: MemoryTableScopeType? = null,
        onDone: (Result<MemoryTableTemplate>) -> Unit = {},
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    memoryTableRepository.upsertTemplate(
                        template = template,
                        actorAssistantId = assistantId.toString(),
                        requestedScopeType = requestedScopeType,
                    )
                }
            )
        }
    }

    fun createMemoryTableDocument(
        template: MemoryTableTemplate,
        onDone: (Result<MemoryTableDocument>) -> Unit,
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    persistMemoryTableCreation(
                        template = template,
                        persistTemplate = null,
                        persistDocument = { persistedTemplate ->
                            memoryTableRepository.upsertDocument(
                                MemoryTableDocument(
                                    templateId = persistedTemplate.id,
                                    scopeType = MemoryTableScopeType.ASSISTANT,
                                    scopeId = assistantId.toString(),
                                ),
                                actorAssistantId = assistantId.toString(),
                            )
                        },
                    )
                }
            )
        }
    }

    fun createMemoryTableTemplateAndDocument(
        template: MemoryTableTemplate,
        scopeType: MemoryTableScopeType,
        onDone: (Result<MemoryTableDocument>) -> Unit,
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    persistMemoryTableCreation(
                        template = template.copy(scopeType = scopeType),
                        persistTemplate = { draft ->
                            memoryTableRepository.upsertTemplate(
                                template = draft,
                                actorAssistantId = assistantId.toString(),
                                requestedScopeType = scopeType,
                            )
                        },
                        persistDocument = { persistedTemplate ->
                            memoryTableRepository.upsertDocument(
                                MemoryTableDocument(
                                    templateId = persistedTemplate.id,
                                    scopeType = MemoryTableScopeType.ASSISTANT,
                                    scopeId = assistantId.toString(),
                                ),
                                actorAssistantId = assistantId.toString(),
                            )
                        },
                    )
                }
            )
        }
    }

    fun deleteMemoryTableTemplate(
        template: MemoryTableTemplate,
        onDone: (Result<Boolean>) -> Unit = {},
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    memoryTableRepository.deleteTemplate(template.id, actorAssistantId = assistantId.toString())
                }
            )
        }
    }

    fun copyGlobalMemoryTableTemplate(
        template: MemoryTableTemplate,
        copyName: String,
        onDone: (Result<MemoryTableTemplate>) -> Unit = {},
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    memoryTableRepository.copyGlobalTemplateToAssistant(
                        templateId = template.id,
                        actorAssistantId = assistantId.toString(),
                        copyName = copyName,
                    )
                }
            )
        }
    }

    fun upsertMemoryTableDocument(document: MemoryTableDocument, conversationId: String? = null) {
        viewModelScope.launch {
            memoryTableRepository.upsertDocument(
                document,
                actorAssistantId = assistantId.toString(),
                actorConversationId = conversationId,
            )
        }
    }

    fun deleteMemoryTableDocument(
        document: MemoryTableDocument,
        conversationId: String? = null,
        onDone: (Result<MemoryTableSoftDeleteResult>) -> Unit = {},
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    memoryTableRepository.softDeleteDocument(
                        id = document.id,
                        deletedBy = MEMORY_TABLE_DELETED_BY_USER_UI,
                        assistantId = assistantId.toString(),
                        conversationId = conversationId,
                    )
                }
            )
        }
    }

    fun restoreMemoryTableDocument(
        documentId: String,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    memoryTableRepository.restoreDocument(
                        id = documentId,
                        assistantId = assistantId.toString(),
                    )
                }
            )
        }
    }

    fun purgeMemoryTableDocument(
        documentId: String,
        onDone: (Result<Boolean>) -> Unit = {},
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    memoryTableRepository.purgeDocument(
                        id = documentId,
                        assistantId = assistantId.toString(),
                    )
                }
            )
        }
    }

    suspend fun getMemoryTableRevisionHistory(
        documentId: String,
        conversationId: String? = null,
    ): Result<MemoryTableRevisionHistory> = try {
        val current = memoryTableRepository.getEffectiveDocument(
            id = documentId,
            assistantId = assistantId.toString(),
            conversationId = conversationId,
        ) ?: error("memory table document not found or not authorized: $documentId")
        val snapshots = memoryTableRepository.getDocumentSnapshots(
            documentId = documentId,
            actorAssistantId = assistantId.toString(),
            actorConversationId = conversationId,
        )
        Result.success(MemoryTableRevisionHistory(current, snapshots))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    fun rollbackMemoryTableDocument(
        documentId: String,
        revision: Int,
        conversationId: String? = null,
        onDone: (Result<MemoryTableDocument>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = try {
                Result.success(
                    memoryTableRepository.rollbackDocument(
                        documentId = documentId,
                        revision = revision,
                        actorAssistantId = assistantId.toString(),
                        actorConversationId = conversationId,
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            onDone(result)
        }
    }

    fun checkAvatarDelete(old: Assistant, new: Assistant) {
        if (old.avatar is Avatar.Image && old.avatar != new.avatar) {
            filesManager.deleteChatFiles(listOf(old.avatar.url.toUri()))
        }
    }

    fun checkBackgroundDelete(old: Assistant, new: Assistant) {
        val oldBackground = old.background
        val newBackground = new.background

        if (oldBackground != null && oldBackground != newBackground) {
            try {
                val oldUri = oldBackground.toUri()
                if (oldUri.scheme == "content" || oldUri.scheme == "file") {
                    filesManager.deleteChatFiles(listOf(oldUri))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete background file: $oldBackground", e)
            }
        }
    }
}

internal suspend fun persistMemoryTableCreation(
    template: MemoryTableTemplate,
    persistTemplate: (suspend (MemoryTableTemplate) -> MemoryTableTemplate)?,
    persistDocument: suspend (MemoryTableTemplate) -> MemoryTableDocument,
): MemoryTableDocument {
    val persistedTemplate = persistTemplate?.invoke(template) ?: template
    return persistDocument(persistedTemplate)
}

data class MemoryTableRevisionHistory(
    val currentDocument: MemoryTableDocument,
    val snapshots: List<MemoryTableDocumentSnapshot>,
)
