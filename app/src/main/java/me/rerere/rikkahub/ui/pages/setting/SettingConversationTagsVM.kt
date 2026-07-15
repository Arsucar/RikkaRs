package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.data.model.ConversationTagException
import me.rerere.rikkahub.data.model.ConversationTagReferenceCount
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import kotlin.uuid.Uuid

class SettingConversationTagsVM(
    private val repository: ConversationTagRepository,
) : ViewModel() {
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val tags: StateFlow<List<ConversationTag>> = repository.observeTags()
        .onEach { _loaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val referenceCounts: StateFlow<List<ConversationTagReferenceCount>> = repository.observeReferenceCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createTag(name: String, colorKey: String, onComplete: (ConversationTagUiError?) -> Unit) {
        launchMutation(onComplete) { repository.createTag(name, colorKey) }
    }

    fun renameTag(tagId: Uuid, name: String, onComplete: (ConversationTagUiError?) -> Unit) {
        launchMutation(onComplete) { repository.renameTag(tagId, name) }
    }

    fun recolorTag(tagId: Uuid, colorKey: String, onComplete: (ConversationTagUiError?) -> Unit) {
        launchMutation(onComplete) { repository.recolorTag(tagId, colorKey) }
    }

    fun deleteTag(tagId: Uuid, onComplete: (ConversationTagUiError?) -> Unit) {
        launchMutation(onComplete) { repository.deleteTag(tagId) }
    }

    fun mergeTag(sourceTagId: Uuid, targetTagId: Uuid, onComplete: (ConversationTagUiError?) -> Unit) {
        launchMutation(onComplete) { repository.mergeTag(sourceTagId, targetTagId) }
    }

    private fun launchMutation(
        onComplete: (ConversationTagUiError?) -> Unit,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (error: ConversationTagException) {
                ConversationTagUiError.Domain(error.code)
            } catch (_: Exception) {
                ConversationTagUiError.Unexpected
            }
            onComplete(error)
        }
    }
}

sealed interface ConversationTagUiError {
    data class Domain(val code: ConversationTagErrorCode) : ConversationTagUiError
    data object Unexpected : ConversationTagUiError
}
