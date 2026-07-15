package me.rerere.rikkahub.service.hooks

import androidx.room.withTransaction
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.data.model.ConversationTagException
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import kotlin.uuid.Uuid

fun interface HookExecutionLeaseGuard {
    suspend fun isActive(executionId: Uuid, leaseToken: Long): Boolean
}

class AddConversationTagHookAction(
    private val conversationRepository: ConversationRepository,
    private val tagRepository: ConversationTagRepository,
    private val leaseGuard: HookExecutionLeaseGuard,
    private val database: AppDatabase,
) : HookActionHandler {
    override suspend fun execute(
        context: HookActionContext,
        output: ParsedHookOutput,
    ): HookActionResult {
        if (output.decision == HookDecision.SKIP) return HookActionResult.Skipped(null)
        val tagId = output.tagId ?: return HookActionResult.Skipped(null, HookErrorCode.SCHEMA_MISMATCH)
        if (!leaseGuard.isActive(context.executionId, context.leaseToken)) {
            return HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
        }
        if (tagId !in context.allowedTagIds) {
            return HookActionResult.Skipped(tagId, HookErrorCode.TAG_NOT_ALLOWED)
        }
        if (tagRepository.getTag(tagId) == null) {
            return HookActionResult.Skipped(tagId, HookErrorCode.TAG_NOT_FOUND)
        }
        val conversation = conversationRepository.getConversationById(context.conversationId)
            ?: return HookActionResult.Cancelled(HookErrorCode.CONVERSATION_NOT_FOUND)
        val sourceNode = conversation.messageNodes.firstOrNull { it.id == context.sourceNodeId }
        val sourceIsActive = sourceNode != null &&
            sourceNode.messages.getOrNull(sourceNode.selectIndex)?.id == context.sourceMessageId &&
            conversation.currentMessages.any { it.id == context.sourceMessageId }
        if (!sourceIsActive) {
            return HookActionResult.Cancelled(HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE)
        }
        return try {
            val changed = database.withTransaction {
                if (!leaseGuard.isActive(context.executionId, context.leaseToken)) return@withTransaction null
                tagRepository.addTag(context.conversationId, tagId)
            } ?: return HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
            if (changed) {
                HookActionResult.Applied(tagId)
            } else {
                HookActionResult.Skipped(tagId)
            }
        } catch (error: ConversationTagException) {
            when (error.code) {
                ConversationTagErrorCode.TAG_NOT_FOUND ->
                    HookActionResult.Skipped(tagId, HookErrorCode.TAG_NOT_FOUND)
                ConversationTagErrorCode.CONVERSATION_NOT_FOUND ->
                    HookActionResult.Cancelled(HookErrorCode.CONVERSATION_NOT_FOUND)
                else -> HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
            }
        }
    }
}
