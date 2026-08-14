package me.rerere.rikkahub.ui.pages.assistant

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.AssistantArchiveResult
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import me.rerere.rikkahub.service.ChatService

class AssistantVM(
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val memoryTableRepository: MemoryTableRepository,
    private val conversationRepo: ConversationRepository,
    private val filesManager: FilesManager,
    private val chatService: ChatService,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings.dummy())

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    @Deprecated(
        message = "使用 transform 重载避免读快照-全量写竞态 (#267)",
        replaceWith = ReplaceWith("updateSettings { it.copy(...) }"),
    )
    fun updateSettings(settings: Settings) = updateSettings { settings }

    fun addAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            settingsStore.update(
                settings.copy(
                    assistants = settings.assistants.plus(assistant.copy(isArchived = false))
                )
            )
        }
    }

    /**
     * 添加助手并同时写入关联的世界书（issue #302）。
     *
     * 一次性原子写入 lorebooks + assistant，避免分步写入产生的中间状态。
     * assistant.lorebookIds 自动关联到新建的 lorebook ids。
     */
    fun addAssistantWithLorebooks(assistant: Assistant, lorebooks: List<Lorebook>) {
        viewModelScope.launch {
            val settings = settings.value
            val lorebookIds = lorebooks.map { it.id }.toSet()
            settingsStore.update(
                settings.copy(
                    assistants = settings.assistants.plus(
                        assistant.copy(
                            isArchived = false,
                            lorebookIds = lorebookIds,
                        )
                    ),
                    lorebooks = settings.lorebooks.plus(lorebooks)
                )
            )
        }
    }

    fun setAssistantArchived(
        assistant: Assistant,
        archived: Boolean,
        onResult: (AssistantArchiveResult) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(settingsStore.setAssistantArchived(assistant.id, archived))
        }
    }

    fun removeAssistant(assistant: Assistant) {
        viewModelScope.launch {
            cleanupAssistantFiles(assistant)

            val settings = settings.value
            val remaining = settings.assistants.filter { it.id != assistant.id }
            val newSelected = if (settings.assistantId == assistant.id) {
                remaining.firstOrNull { !it.isArchived }?.id ?: DEFAULT_ASSISTANT_ID
            } else {
                settings.assistantId
            }
            settingsStore.update(
                settings.copy(
                    assistants = remaining,
                    assistantId = newSelected,
                )
            )
            memoryRepository.deleteMemoriesOfAssistant(assistant.id.toString())
            memoryTableRepository.deleteDataOwnedByAssistant(assistant.id.toString())
            val ids = conversationRepo.getConversationIdsOfAssistant(assistant.id)
            ids.forEach { chatService.stopGeneration(it) }
            conversationRepo.deleteConversationOfAssistant(assistant.id)
        }
    }

    private fun cleanupAssistantFiles(assistant: Assistant) {
        val uris = buildList {
            (assistant.avatar as? Avatar.Image)?.let { add(it.url.toUri()) }
            assistant.background?.let { add(it.toUri()) }
        }

        if (uris.isNotEmpty()) {
            filesManager.deleteChatFiles(uris)
        }
    }

    fun copyAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            val copiedAssistant = assistant.copyAsActiveClone()
            settingsStore.update(
                settings.copy(
                    assistants = settings.assistants.plus(copiedAssistant)
                )
            )
        }
    }

    fun getMemories(assistant: Assistant) =
        memoryRepository.getEffectiveMemoriesFlow(assistant.id.toString())
}

internal fun Assistant.copyAsActiveClone(): Assistant = copy(
    id = kotlin.uuid.Uuid.random(),
    name = "$name (Clone)",
    isArchived = false,
    avatar = if (avatar is Avatar.Image) Avatar.Dummy else avatar,
)
