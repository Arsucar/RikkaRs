package me.rerere.rikkahub.ui.pages.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository

enum class ArchiveSearchMode {
    TITLE,
    MESSAGE,
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveVM(
    private val conversationRepo: ConversationRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val searchMode = MutableStateFlow(ArchiveSearchMode.TITLE)

    val archiveSearchMode: StateFlow<ArchiveSearchMode> = searchMode

    val archivedConversations: StateFlow<List<Conversation>> =
        combine(searchQuery, searchMode) { query, mode -> query to mode }
            .flatMapLatest { (query, mode) ->
                when (mode) {
                    ArchiveSearchMode.TITLE -> {
                        if (query.isBlank()) {
                            conversationRepo.getArchivedConversations()
                        } else {
                            conversationRepo.searchArchivedConversations(query)
                        }
                    }

                    ArchiveSearchMode.MESSAGE -> {
                        if (query.isBlank()) {
                            conversationRepo.getArchivedConversations()
                        } else {
                            flow { emit(emptyList()) }
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messageSearchResults: StateFlow<List<MessageSearchResult>> =
        combine(searchQuery, searchMode) { query, mode -> query to mode }
            .flatMapLatest { (query, mode) ->
                if (mode != ArchiveSearchMode.MESSAGE || query.isBlank()) {
                    flow { emit(emptyList()) }
                } else {
                    flow {
                        emit(
                            conversationRepo.searchArchivedMessages(
                                query,
                                MessageSearchSort.RELEVANCE,
                            ),
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSearchMode(mode: ArchiveSearchMode) {
        searchMode.value = mode
    }

    fun unarchive(c: Conversation) {
        viewModelScope.launch {
            conversationRepo.unarchiveConversation(c.id)
        }
    }

    fun deletePermanently(c: Conversation) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(c)
        }
    }

    fun unarchiveAll() {
        viewModelScope.launch {
            conversationRepo.unarchiveAll()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            conversationRepo.deleteAllArchived()
        }
    }
}