package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.OptimisticWriteCoordinator
import kotlin.uuid.Uuid

private const val TAG = "ChatMessageVM"

/**
 * Message send/edit/regenerate/delete, tool approval, compression, and favorites.
 * Shares conversation id with [ChatVM] via the same NavBackStackEntry + parametersOf(id).
 */
class ChatMessageVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val conversationId: Uuid = Uuid.parse(id)
    private val conversation: StateFlow<Conversation> = chatService.getConversationFlow(conversationId)

    // #295: per-node coordinators so rapid favorite toggles do not lose updates.
    private val favoriteWriteCoordinators = mutableMapOf<Uuid, OptimisticWriteCoordinator>()

    private fun favoriteCoordinatorFor(nodeId: Uuid): OptimisticWriteCoordinator =
        favoriteWriteCoordinators.getOrPut(nodeId) { OptimisticWriteCoordinator() }

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     */
    fun handleMessageSend(
        content: List<UIMessagePart>,
        answer: Boolean = true,
    ) {
        if (content.isEmptyInputMessage()) return

        chatService.sendMessage(conversationId, content, answer)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        viewModelScope.launch {
            chatService.editMessage(conversationId, messageId, parts)
        }
    }

    fun handleCompressContext(additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int): Job {
        return viewModelScope.launch {
            val result = chatService.compressConversation(
                conversationId = conversationId,
                additionalPrompt = additionalPrompt,
                targetTokens = targetTokens,
                keepRecentMessages = keepRecentMessages,
            )
            handleManualCompressionResult(
                result = result,
                targetTokens = targetTokens,
                keepRecentMessages = keepRecentMessages,
                persistPreferences = settingsStore::updateCompressionPreferences,
                onCompressionFailure = {
                    chatService.addError(
                        it,
                        title = context.getString(R.string.error_title_compress_conversation),
                    )
                },
                onPreferencePersistenceFailure = {
                    Log.e(TAG, "Failed to persist compression preferences", it)
                },
            )
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        return chatService.forkConversationAtMessage(conversationId, message.id)
    }

    fun deleteMessage(message: UIMessage) {
        viewModelScope.launch {
            chatService.deleteMessage(conversationId, message)
        }
    }

    fun toggleMessageHidden(messageId: Uuid) = viewModelScope.launch {
        chatService.toggleMessageHidden(conversationId, messageId)
    }

    fun showDeleteBlockedWhileGeneratingError() {
        chatService.addError(
            error = IllegalStateException("请先停止生成再删除消息"),
            conversationId = conversationId,
            title = context.getString(R.string.error_title_operation)
        )
    }

    fun regenerateAtMessage(
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        chatService.regenerateAtMessage(conversationId, message, regenerateAssistantMsg)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        trustedWriteRoot: String? = null,
    ) {
        chatService.handleToolApproval(
            conversationId = conversationId,
            toolCallId = toolCallId,
            approved = approved,
            reason = reason,
            trustedWriteRoot = trustedWriteRoot,
        )
    }

    fun trustWriteRootAndApprove(toolCallId: String, rootPrefix: String) {
        handleToolApproval(
            toolCallId = toolCallId,
            approved = true,
            trustedWriteRoot = rootPrefix,
        )
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        chatService.handleToolApproval(conversationId, toolCallId, approved = true, answer = answer)
    }

    fun stopGeneration() {
        viewModelScope.launch {
            chatService.stopGeneration(conversationId)
        }
    }

    /**
     * #295: optimistic favorite toggle. UI reads [MessageNode.isFavorite] from conversation
     * state, so we flip that flag first (no pre-query), then persist to FavoriteRepository.
     * Per-node coordinators keep rapid double-taps coherent.
     */
    fun toggleMessageFavorite(node: MessageNode) {
        viewModelScope.launch {
            val liveNode = conversation.value.messageNodes.firstOrNull { it.id == node.id } ?: node
            val currentlyFavorited = liveNode.isFavorite
            val targetFavorited = !currentlyFavorited
            val conversationTitle = conversation.value.title
            favoriteCoordinatorFor(node.id).run(
                applyOptimistic = {
                    chatService.updateConversationState(conversationId) { current ->
                        conversationWithNodeFavorite(current, node.id, targetFavorited)
                    }
                    targetFavorited
                },
                persist = { wantFavorite ->
                    if (wantFavorite) {
                        favoriteRepository.addNodeFavorite(
                            NodeFavoriteTarget(
                                conversationId = conversationId,
                                conversationTitle = conversationTitle,
                                nodeId = node.id,
                                node = liveNode.copy(isFavorite = true),
                            )
                        )
                    } else {
                        favoriteRepository.removeNodeFavorite(conversationId, node.id)
                    }
                },
                rollback = {
                    chatService.updateConversationState(conversationId) { current ->
                        conversationWithNodeFavorite(current, node.id, currentlyFavorited)
                    }
                },
                onError = { error ->
                    chatService.addError(
                        error = error,
                        conversationId = conversationId,
                        title = context.getString(R.string.error_title_operation),
                    )
                },
            )
        }
    }
}
