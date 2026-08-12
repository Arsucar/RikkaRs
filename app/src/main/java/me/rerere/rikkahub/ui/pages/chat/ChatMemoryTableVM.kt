package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.repository.MEMORY_TABLE_DELETED_BY_USER_UI
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import me.rerere.rikkahub.data.repository.MemoryTableSoftDeleteResult
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.runOptimisticWrite
import kotlin.uuid.Uuid

/**
 * Conversation-scoped memory table documents / isolation / variables for the chat right drawer.
 * Shares conversation id with [ChatVM] via the same NavBackStackEntry + parametersOf(id).
 * Does not own conversation lifecycle references; [ChatVM] still add/remove refs.
 */
class ChatMemoryTableVM(
    id: String,
    private val context: Application,
    private val chatService: ChatService,
    private val memoryTableRepository: MemoryTableRepository,
) : ViewModel() {
    private val conversationId: Uuid = Uuid.parse(id)
    private val conversation = chatService.getConversationFlow(conversationId)

    // #89: 对话可见的记忆表模板（用于新建对话级文档时选择模板）
    @OptIn(ExperimentalCoroutinesApi::class)
    val memoryTableTemplates: StateFlow<List<MemoryTableTemplate>> = conversation
        .flatMapLatest { conv ->
            memoryTableRepository.getEffectiveTemplatesFlow(conv.assistantId.toString())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // #89: 当前对话生效的记忆表文档（CONVERSATION + 继承的 ASSISTANT/GLOBAL），
    // 随会话切换助手时自动跟随，供右侧抽屉查看与管理。
    @OptIn(ExperimentalCoroutinesApi::class)
    val memoryTableDocuments: StateFlow<List<MemoryTableDocument>> = conversation
        .flatMapLatest { conv ->
            memoryTableRepository.getEffectiveDocumentsFlow(
                assistantId = conv.assistantId.toString(),
                conversationId = conv.id.toString(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // #89/#295: 切换对话级记忆表隔离开关。先无条件更新内存状态，保证开关立即响应
    // （空的新对话拨动也生效）；再尝试落库——非空对话直接持久化，空的新对话
    // 由首条消息发送时的 saveConversation 一并写入，避免重启后丢失。
    // 落库失败时回滚内存态并通过 ChatService.errors 提示（#295）。
    fun setMemoryTableIsolation(enabled: Boolean) {
        val previousEnabled = conversation.value.memoryTableIsolation
        viewModelScope.launch {
            runOptimisticWrite(
                applyOptimistic = {
                    val updated = conversationWithMemoryTableIsolation(conversation.value, enabled)
                    chatService.updateConversationState(conversationId) { updated }
                    updated
                },
                persist = { updated ->
                    chatService.saveConversation(conversationId, updated)
                },
                rollback = {
                    chatService.updateConversationState(conversationId) { current ->
                        conversationWithMemoryTableIsolation(current, previousEnabled)
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

    fun upsertConversationVariable(name: String, value: String) {
        chatService.updateConversationVariables(conversationId) { current ->
            me.rerere.rikkahub.data.ai.variables.ConversationVariables.applyTransform(current) { map ->
                me.rerere.rikkahub.data.ai.variables.ConversationVariables.putVar(map, name, value)
            }
        }
    }

    fun deleteConversationVariable(name: String) {
        chatService.updateConversationVariables(conversationId) { current ->
            current - name.trim()
        }
    }

    // #89: 保存（新建/更新）一个对话级记忆表文档。scopeType 强制为 CONVERSATION，
    // scopeId 绑定到当前对话，从而让 conversation scope 真正可写、可查看。
    fun upsertConversationMemoryTableDocument(
        document: MemoryTableDocument,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                memoryTableRepository.upsertDocument(
                    document.copy(
                        scopeType = MemoryTableScopeType.CONVERSATION,
                        scopeId = conversationId.toString(),
                    ),
                    actorAssistantId = conversation.value.assistantId.toString(),
                    actorConversationId = conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

    // #89: 将助手级/全局的记忆表文档同步（复制）到当前对话级。
    // 若当前对话已存在同模板的对话级文档则覆盖其内容，否则新建，避免重复。
    fun syncMemoryTableDocumentToConversation(
        source: MemoryTableDocument,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val conversationScopeId = conversationId.toString()
                val existing = memoryTableRepository
                    .getDocumentsForScope(MemoryTableScopeType.CONVERSATION, conversationScopeId)
                    .firstOrNull { it.templateId == source.templateId }
                val target = (existing ?: MemoryTableDocument(
                    templateId = source.templateId,
                    scopeType = MemoryTableScopeType.CONVERSATION,
                    scopeId = conversationScopeId,
                )).copy(
                    templateId = source.templateId,
                    scopeType = MemoryTableScopeType.CONVERSATION,
                    scopeId = conversationScopeId,
                    payloadJson = source.payloadJson,
                    // #89: 记录来源助手级文档，默认跟随其更新；用户可在抽屉里断开独立编辑。
                    sourceDocumentId = source.id,
                    followSource = true,
                )
                memoryTableRepository.upsertDocument(
                    target,
                    actorAssistantId = conversation.value.assistantId.toString(),
                    actorConversationId = conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

    // #89: 设置对话级记忆文档是否跟随来源助手级文档。follow=false 即"断开独立编辑"，
    // 后续源文档更新不再覆盖该对话级文档。
    fun setMemoryTableDocumentFollow(
        documentId: String,
        follow: Boolean,
        onDone: (Result<MemoryTableDocument>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val doc = memoryTableRepository.getEffectiveDocument(
                    id = documentId,
                    assistantId = conversation.value.assistantId.toString(),
                    conversationId = conversationId.toString(),
                )
                    ?: error("Memory table document not found: $documentId")
                memoryTableRepository.upsertDocument(
                    doc.copy(followSource = follow),
                    actorAssistantId = conversation.value.assistantId.toString(),
                    actorConversationId = conversationId.toString(),
                )
            }
            onDone(result)
        }
    }

    // #89: 删除一个记忆表文档（抽屉内针对对话级文档的清理）。
    fun deleteMemoryTableDocument(
        documentId: String,
        onDone: (Result<MemoryTableSoftDeleteResult>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                memoryTableRepository.softDeleteDocument(
                    id = documentId,
                    deletedBy = MEMORY_TABLE_DELETED_BY_USER_UI,
                    assistantId = conversation.value.assistantId.toString(),
                    conversationId = conversationId.toString(),
                )
            }
            onDone(result)
        }
    }
}
