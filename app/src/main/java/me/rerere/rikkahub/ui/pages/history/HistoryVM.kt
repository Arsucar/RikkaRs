package me.rerere.rikkahub.ui.pages.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

class HistoryVM(
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
) : ViewModel() {
    val assistant = settingsStore.settingsFlow
        .map { it.getCurrentAssistant() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: Flow<PagingData<Conversation>> = assistant
        .flatMapLatest { current ->
            val id = current?.id ?: return@flatMapLatest flowOf(PagingData.empty())
            conversationRepo.getConversationsOfAssistantPaging(id)
        }
        .cachedIn(viewModelScope)

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatService.stopGeneration(conversation.id)
            conversationRepo.deleteConversation(conversation)
        }
    }

    fun deleteAllConversations() {
        val assistant = assistant.value ?: return
        viewModelScope.launch {
            val ids = conversationRepo.getConversationIdsOfAssistant(assistant.id)
            ids.forEach { chatService.stopGeneration(it) }
            conversationRepo.deleteConversationOfAssistant(assistant.id)
        }
    }

    fun togglePinStatus(conversationId: Uuid) {
        viewModelScope.launch {
            chatService.togglePinStatus(conversationId)
        }
    }

    fun getPinnedConversations(): Flow<List<Conversation>> =
        conversationRepo.getPinnedConversations()

    fun restoreConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.insertConversation(conversation)
        }
    }

    suspend fun getFullConversation(conversationId: Uuid): Conversation? {
        return conversationRepo.getConversationById(conversationId)
    }
}
