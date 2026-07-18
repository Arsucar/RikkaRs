package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import kotlin.uuid.Uuid

class AddConversationTagHookAction(
    private val tagRepository: ConversationTagRepository,
    private val committer: ConversationTagHookCommitter,
) : HookActionHandler {
    override val actionType: HookActionType = HookActionType.ADD_CONVERSATION_TAG

    override suspend fun prepare(
        hook: ConversationHook,
        context: HookFreezeContext,
    ): HookActionPreparation {
        val config = hook.actionConfig as? HookActionConfig.AddConversationTag
            ?: return HookActionPreparation.Cancelled(HookErrorCode.ACTION_FAILED)
        if (context.conversation.currentMessages.none { it.id == context.sourceMessageId }) {
            return HookActionPreparation.Cancelled(HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE)
        }
        val allowedTags = config.allowedTagIds.mapNotNull { tagId ->
            tagRepository.getTag(tagId)?.let { tagId to it.displayName }
        }.toMap()
        return HookActionPreparation.Ready(
            PreparedHookAction.AddConversationTag(
                request = FrozenHookModelRequest.AddConversationTag(
                    modelId = hook.modelId,
                    prompt = hook.prompt,
                    messageTextSnapshot = context.sourceMessageTextSnapshot,
                    allowedTags = allowedTags,
                ),
                conversationId = context.conversation.id,
                sourceNodeId = context.sourceNodeId,
                sourceMessageId = context.sourceMessageId,
                allowedTagIds = config.allowedTagIds,
            )
        )
    }

    override fun parse(raw: String, prepared: PreparedHookAction): ParsedHookOutput =
        HookOutputParser.parse(raw)

    override suspend fun execute(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): HookActionResult {
        val context = prepared as? PreparedHookAction.AddConversationTag
            ?: return HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
        val tagOutput = output as? ParsedAddTagHookOutput
            ?: return HookActionResult.Cancelled(HookErrorCode.SCHEMA_MISMATCH)
        if (tagOutput.decision == HookDecision.SKIP) return HookActionResult.Skipped(null)
        val tagId = tagOutput.tagId ?: return HookActionResult.Skipped(null, HookErrorCode.SCHEMA_MISMATCH)
        if (tagId !in context.allowedTagIds) {
            return HookActionResult.Skipped(tagId, HookErrorCode.TAG_NOT_ALLOWED)
        }
        if (tagRepository.getTag(tagId) == null) {
            return HookActionResult.Skipped(tagId, HookErrorCode.TAG_NOT_FOUND)
        }
        committer.commitAdd(executionId, leaseToken, context, tagOutput, tagId)
        return HookActionResult.Terminalized
    }
}
