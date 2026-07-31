package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.AssistantWorkspaceBindingUpdateResult
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.pruneExtensionIds
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.ToolPermission
import me.rerere.rikkahub.data.model.ToolPermissionPreset
import me.rerere.rikkahub.data.model.ToolPresetTargetResult
import me.rerere.rikkahub.data.model.applyToolPermissionPreset
import me.rerere.rikkahub.data.model.batchToolPermission
import me.rerere.rikkahub.data.model.isValidForPersistence
import me.rerere.rikkahub.data.model.permissionPresetFromAssistant
import me.rerere.rikkahub.data.model.ToolConnectionStatus
import me.rerere.rikkahub.data.model.ToolConnectionStatusStore
import me.rerere.rikkahub.data.model.normalizeActionConfig
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
    private val mcpManager: McpManager,
    conversationTagRepository: ConversationTagRepository,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()
    // syncingStatus is already a StateFlow; do not call asStateFlow() on it.
    val mcpStatuses: StateFlow<Map<Uuid, McpStatus>> = mcpManager.syncingStatus
    private val connectionStatusStore = ToolConnectionStatusStore()
    private val connectionJobs = mutableMapOf<Uuid, Job>()
    private val connectionFingerprints = mutableMapOf<Uuid, Long>()
    private val _toolConnectionStatuses = MutableStateFlow<Map<Uuid, ToolConnectionStatus>>(emptyMap())
    val toolConnectionStatuses: StateFlow<Map<Uuid, ToolConnectionStatus>> = _toolConnectionStatuses.asStateFlow()

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

    init {
        viewModelScope.launch {
            mcpServerConfigs.collect { configs ->
                val ids = configs.map { it.id }.toSet()
                configs.forEach { config ->
                    val fingerprint = config.hashCode().toLong()
                    if (connectionFingerprints[config.id] != null && connectionFingerprints[config.id] != fingerprint) {
                        connectionJobs.remove(config.id)?.cancel()
                        connectionStatusStore.remove(config.id)
                    }
                    connectionFingerprints[config.id] = fingerprint
                }
                connectionJobs.keys.filterNot { it in ids }.forEach { id ->
                    connectionJobs.remove(id)?.cancel()
                    connectionStatusStore.remove(id)
                    connectionFingerprints.remove(id)
                }
                _toolConnectionStatuses.value = connectionStatusStore.snapshot()
            }
        }
    }

    fun testMcpConnection(serverId: Uuid) {
        val config = mcpServerConfigs.value.firstOrNull { it.id == serverId } ?: return
        if (connectionJobs[serverId]?.isActive == true) return
        val revision = config.hashCode().toLong()
        connectionFingerprints[serverId] = revision
        connectionStatusStore.begin(serverId, revision)
        _toolConnectionStatuses.value = connectionStatusStore.snapshot()
        connectionJobs[serverId] = viewModelScope.launch {
            val result = runCatching { mcpManager.testConnection(config, revision) }
                .getOrElse { error ->
                    ToolConnectionStatus(
                        state = me.rerere.rikkahub.data.model.ToolConnectionState.ERROR,
                        message = error::class.simpleName,
                        revision = revision,
                    )
                }
            connectionStatusStore.publish(serverId, result)
            _toolConnectionStatuses.value = connectionStatusStore.snapshot()
            connectionJobs.remove(serverId)
        }
    }

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

    private val conversationTagsReloadRequest = MutableStateFlow(0)

    val conversationTagsUiState = conversationTagsReloadRequest
        .flatMapLatest {
            conversationTagRepository.observeTags()
                .map<List<ConversationTag>, ConversationTagsUiState>(ConversationTagsUiState::Success)
                .catch { emit(ConversationTagsUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConversationTagsUiState.Loading)

    fun reloadConversationTags() {
        conversationTagsReloadRequest.value++
    }

    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository
        .listFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val workspaceBindingSaveMutex = Mutex()
    private var workspaceBindingRequestId = 0L
    private val workspaceBindingSaveEventChannel = Channel<WorkspaceBindingSaveEvent>(Channel.BUFFERED)
    val workspaceBindingSaveEvents = workspaceBindingSaveEventChannel.receiveAsFlow()

    private val toolPermissionSaveMutex = Mutex()
    private var toolPermissionRequestId = 0L
    private val toolPermissionSaveEventChannel = Channel<ToolPermissionSaveEvent>(Channel.BUFFERED)
    val toolPermissionSaveEvents = toolPermissionSaveEventChannel.receiveAsFlow()

    fun saveWorkspaceBinding(workspaceId: Uuid?) {
        val requestId = ++workspaceBindingRequestId
        viewModelScope.launch {
            val result = workspaceBindingSaveMutex.withLock {
                if (requestId != workspaceBindingRequestId) return@withLock null
                persistWorkspaceBinding(assistantId, workspaceId) { targetAssistantId, targetWorkspaceId ->
                    settingsStore.updateAssistantWorkspaceBinding(targetAssistantId, targetWorkspaceId)
                }
            }
            if (result == null) return@launch
            if (result == WorkspaceBindingSaveEvent.Failure) {
                Log.e(TAG, "Failed to save workspace binding")
            }
            workspaceBindingSaveEventChannel.send(result)
        }
    }

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

    fun saveToolPermission(capabilityId: String, permission: ToolPermission) {
        val requestId = ++toolPermissionRequestId
        viewModelScope.launch {
            val event = toolPermissionSaveMutex.withLock {
                if (requestId != toolPermissionRequestId) return@withLock null
                persistToolPermission(assistantId, capabilityId, permission) { targetId, id, value ->
                    val current = settingsStore.settingsFlow.value.assistants.firstOrNull { it.id == targetId }
                        ?: error("Assistant not found")
                    val updated = if (value == ToolPermission.INHERIT) {
                        current.toolPermissions - id
                    } else {
                        current.toolPermissions + (id to value)
                    }
                    settingsStore.updateAssistantConfig(current.copy(toolPermissions = updated))
                }
            } ?: return@launch
            toolPermissionSaveEventChannel.send(event)
        }
    }

    fun clearOrphanToolPermissions(capabilityIds: Set<String>) {
        if (capabilityIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val current = settingsStore.settingsFlow.value.assistants.first { it.id == assistantId }
                settingsStore.updateAssistantConfig(
                    current.copy(toolPermissions = current.toolPermissions - capabilityIds),
                )
            }.fold(
                onSuccess = { toolPermissionSaveEventChannel.send(ToolPermissionSaveEvent.Success) },
                onFailure = { toolPermissionSaveEventChannel.send(ToolPermissionSaveEvent.Failure) },
            )
        }
    }

    fun saveToolPermissionPreset(preset: ToolPermissionPreset) {
        if (!preset.isValidForPersistence()) return
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(toolPermissionPresets = (settings.toolPermissionPresets.filterNot { it.id == preset.id } + preset))
            }
        }
    }

    fun deleteToolPermissionPreset(presetId: Uuid) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(toolPermissionPresets = settings.toolPermissionPresets.filterNot { it.id == presetId })
            }
        }
    }

    suspend fun applyToolPermissionPresetToAssistants(
        preset: ToolPermissionPreset,
        assistantIds: Set<Uuid>,
        knownCapabilityIds: Set<String>,
        confirmRelaxation: Boolean = false,
    ): List<ToolPresetTargetResult> {
        val settings = settingsStore.settingsFlow.value
        val results = assistantIds.associateWith { targetId ->
            settings.assistants.firstOrNull { it.id == targetId }?.let { target ->
                applyToolPermissionPreset(preset, target, knownCapabilityIds, confirmRelaxation)
            } ?: ToolPresetTargetResult(targetId, me.rerere.rikkahub.data.model.ToolPresetApplyStatus.TARGET_NOT_FOUND)
        }
        settings.copy(
            assistants = settings.assistants.map { target ->
                val result = results[target.id]
                if (result != null && result.status in setOf(
                        me.rerere.rikkahub.data.model.ToolPresetApplyStatus.APPLIED,
                        me.rerere.rikkahub.data.model.ToolPresetApplyStatus.SKIPPED_UNKNOWN,
                    )) {
                    target.copy(toolPermissions = target.toolPermissions + result.changed)
                } else target
            }
        ).let { settingsStore.update(it) }
        return assistantIds.mapNotNull { results[it] }
    }

    suspend fun copyToolPermissionsToAssistants(
        sourceAssistantId: Uuid,
        targetAssistantIds: Set<Uuid>,
        knownCapabilityIds: Set<String>,
        confirmRelaxation: Boolean = false,
    ): List<ToolPresetTargetResult> {
        val settings = settingsStore.settingsFlow.value
        val source = settings.assistants.firstOrNull { it.id == sourceAssistantId }
            ?: return targetAssistantIds.map { ToolPresetTargetResult(it, me.rerere.rikkahub.data.model.ToolPresetApplyStatus.TARGET_NOT_FOUND) }
        val preset = permissionPresetFromAssistant("copy-$sourceAssistantId", source, knownCapabilityIds)
        return applyToolPermissionPresetToAssistants(preset, targetAssistantIds, knownCapabilityIds, confirmRelaxation)
    }

    suspend fun batchSetToolPermissions(
        capabilityIds: Set<String>,
        permission: ToolPermission,
        knownCapabilityIds: Set<String>,
    ): ToolPresetTargetResult? {
        val current = settingsStore.settingsFlow.value.assistants.firstOrNull { it.id == assistantId } ?: return null
        val result = batchToolPermission(current, capabilityIds, permission, knownCapabilityIds)
        settingsStore.updateAssistantConfig(
            current.copy(
                toolPermissions = if (permission == ToolPermission.INHERIT) {
                    current.toolPermissions - result.changed.keys
                } else {
                    current.toolPermissions + result.changed
                },
            )
        )
        return result
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
        _skills.value = skillManager.listSkillsForAssistant(assistantId, createIfMissing = false)
        _assistantPrivateSkills.value = skillManager.listAssistantSkills(assistantId, createIfMissing = false)
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
            val normalized = hook.normalizeActionConfig()
            val existing = hooks.firstOrNull { it.id == normalized.id }
            if (existing == null) {
                hooks + normalized.copy(configVersion = 1)
            } else {
                hooks.map { current ->
                    if (current.id == normalized.id) {
                        normalized.copy(configVersion = existing.configVersion + 1)
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
                            // Persist only normalized action configs (legacy Add/Transition → Manage).
                            assistant.copy(
                                hooks = transform(assistant.hooks).map { it.normalizeActionConfig() },
                            )
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

sealed interface ConversationTagsUiState {
    data object Loading : ConversationTagsUiState
    data class Success(val tags: List<ConversationTag>) : ConversationTagsUiState
    data object Error : ConversationTagsUiState
}

sealed interface WorkspaceBindingSaveEvent {
    data class Success(val workspaceId: Uuid?) : WorkspaceBindingSaveEvent
    data object Failure : WorkspaceBindingSaveEvent
}

sealed interface ToolPermissionSaveEvent {
    data object Success : ToolPermissionSaveEvent
    data object Failure : ToolPermissionSaveEvent
}

internal suspend fun persistToolPermission(
    assistantId: Uuid,
    capabilityId: String,
    permission: ToolPermission,
    persist: suspend (Uuid, String, ToolPermission) -> Unit,
): ToolPermissionSaveEvent = try {
    persist(assistantId, capabilityId, permission)
    ToolPermissionSaveEvent.Success
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    ToolPermissionSaveEvent.Failure
}

internal suspend fun persistWorkspaceBinding(
    assistantId: Uuid,
    workspaceId: Uuid?,
    persist: suspend (Uuid, Uuid?) -> AssistantWorkspaceBindingUpdateResult,
): WorkspaceBindingSaveEvent = try {
    when (persist(assistantId, workspaceId)) {
        AssistantWorkspaceBindingUpdateResult.UPDATED -> WorkspaceBindingSaveEvent.Success(workspaceId)
        AssistantWorkspaceBindingUpdateResult.NOT_FOUND -> WorkspaceBindingSaveEvent.Failure
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    WorkspaceBindingSaveEvent.Failure
}
