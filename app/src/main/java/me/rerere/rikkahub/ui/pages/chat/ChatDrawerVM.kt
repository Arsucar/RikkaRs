package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.data.model.ConversationTagException
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationFilter
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

class ChatDrawerVM(
    private val context: Application,
    private val settingsStore: SettingsStore,
    conversationRepo: ConversationRepository,
    private val conversationTagRepo: ConversationTagRepository,
    private val folderRepo: FolderRepository,
    private val chatService: ChatService,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    // 当前选中的文件夹筛选，null 表示「未归类」视图
    private val _selectedFolderId = MutableStateFlow<Uuid?>(null)
    val selectedFolderId: StateFlow<Uuid?> = _selectedFolderId.asStateFlow()

    private val selectedTagIdStrings = savedStateHandle.getStateFlow(SELECTED_TAG_IDS, emptyList<String>())
    val selectedTagIds: StateFlow<Set<Uuid>> = selectedTagIdStrings
        .map { ids -> ids.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _tagsLoaded = MutableStateFlow(false)
    val tagsLoaded: StateFlow<Boolean> = _tagsLoaded.asStateFlow()
    val tags: StateFlow<List<ConversationTag>> = conversationTagRepo.observeTags()
        .onEach { _tagsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tagsByConversation: StateFlow<Map<Uuid, List<ConversationTag>>> = combine(
        tags,
        conversationTagRepo.observeRelations(),
    ) { vocabulary, relations ->
        val tagsById = vocabulary.associateBy { it.id }
        relations.groupBy { it.conversationId }.mapValues { (_, conversationRelations) ->
            conversationRelations.mapNotNull { tagsById[it.tagId] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _tagOperationError = MutableStateFlow<ConversationTagErrorCode?>(null)
    val tagOperationError: StateFlow<ConversationTagErrorCode?> = _tagOperationError.asStateFlow()

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, _selectedFolderId, selectedTagIds) { assistantId, folderId, tagIds ->
            ConversationFilter(
                assistantId = assistantId,
                folderId = folderId,
                unfiledOnly = folderId == null,
                archived = false,
                tagIds = tagIds,
            )
        }
            .flatMapLatest(conversationRepo::getConversationsPaging)
            .map { pagingData ->
                pagingData
                    .map { ConversationListItem.Item(it) }
                    .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
                        when {
                            before == null && after is ConversationListItem.Item -> {
                                if (after.conversation.isPinned) {
                                    ConversationListItem.PinnedHeader
                                } else {
                                    val afterDate = after.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    ConversationListItem.DateHeader(
                                        date = afterDate,
                                        label = getDateLabel(afterDate)
                                    )
                                }
                            }

                            before is ConversationListItem.Item && after is ConversationListItem.Item -> {
                                if (before.conversation.isPinned && !after.conversation.isPinned) {
                                    val afterDate = after.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    ConversationListItem.DateHeader(
                                        date = afterDate,
                                        label = getDateLabel(afterDate)
                                    )
                                } else if (!after.conversation.isPinned) {
                                    val beforeDate = before.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    val afterDate = after.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()

                                    if (beforeDate != afterDate) {
                                        ConversationListItem.DateHeader(
                                            date = afterDate,
                                            label = getDateLabel(afterDate)
                                        )
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            else -> null
                        }
                    }
            }
            .cachedIn(viewModelScope)

    val archivedCount = conversationRepo.getArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scrollIndex: Int get() = savedStateHandle["scrollIndex"] ?: 0
    val scrollOffset: Int get() = savedStateHandle["scrollOffset"] ?: 0

    init {
        // 助手切换时重置文件夹筛选，回到「聊天」视图，
        // 避免继续显示上一个助手文件夹内的会话（文件夹是助手内分组）
        viewModelScope.launch {
            assistantIdFlow.collect {
                _selectedFolderId.value = null
            }
        }
        viewModelScope.launch {
            tags.collect { vocabulary ->
                val validIds = vocabulary.mapTo(mutableSetOf()) { it.id }
                val cleanedIds = selectedTagIds.value.filterTo(linkedSetOf()) { it in validIds }
                if (cleanedIds != selectedTagIds.value) {
                    saveSelectedTagIds(cleanedIds)
                }
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        savedStateHandle["scrollIndex"] = index
        savedStateHandle["scrollOffset"] = offset
    }

    fun selectFolder(folderId: Uuid?) {
        _selectedFolderId.value = folderId
    }

    fun toggleTagFilter(tagId: Uuid) {
        val updated = selectedTagIds.value.toMutableSet().apply {
            if (!add(tagId)) remove(tagId)
        }
        saveSelectedTagIds(updated)
    }

    fun clearTagFilters() {
        saveSelectedTagIds(emptySet())
    }

    fun setConversationTag(conversationId: Uuid, tagId: Uuid, selected: Boolean) {
        viewModelScope.launch {
            _tagOperationError.value = null
            try {
                if (selected) {
                    conversationTagRepo.addTag(conversationId, tagId)
                } else {
                    conversationTagRepo.removeTag(conversationId, tagId)
                }
            } catch (error: ConversationTagException) {
                _tagOperationError.value = error.code
            }
        }
    }

    fun clearTagOperationError() {
        _tagOperationError.value = null
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assistantId = assistantIdFlow.first()
            folderRepo.createFolder(assistantId, trimmed)
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepo.renameFolder(folderId, trimmed)
        }
    }

    /**
     * 删除文件夹。若文件夹内有正在生成回复的会话，拒绝删除并返回 false（UI 层据此提示用户）。
     */
    fun deleteFolder(folderId: Uuid): Boolean {
        if (chatService.hasGeneratingConversationInFolder(folderId)) {
            return false
        }
        viewModelScope.launch {
            // 经 ChatService 删除：会同步清空活跃 session 内存态的 folderId，避免整对象保存写回已删文件夹
            chatService.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
        return true
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            // 经 ChatService 移动：活跃会话会先同步内存态，避免后续整对象保存覆盖 folder_id
            chatService.moveConversationToFolder(conversationId, folderId)
        }
    }

    private fun getDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> context.getString(R.string.chat_page_today)
            yesterday -> context.getString(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        }
    }

    private fun saveSelectedTagIds(ids: Set<Uuid>) {
        savedStateHandle[SELECTED_TAG_IDS] = ids.map { it.toString() }
    }

    private companion object {
        const val SELECTED_TAG_IDS = "selectedConversationTagIds"
    }
}
