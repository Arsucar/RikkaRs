package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.normalize
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import kotlin.uuid.Uuid

class ManageConversationTagsHookAction(
    private val tagRepository: ConversationTagRepository,
    private val committer: ConversationTagHookCommitter,
) : HookActionHandler {
    override val actionType: HookActionType = HookActionType.MANAGE_CONVERSATION_TAGS

    override suspend fun prepare(
        hook: ConversationHook,
        context: HookFreezeContext,
    ): HookActionPreparation {
        val config = hook.actionConfig.normalize() as? HookActionConfig.ManageConversationTags
            ?: return HookActionPreparation.Cancelled(HookErrorCode.ACTION_FAILED)
        if (context.conversation.currentMessages.none { it.id == context.sourceMessageId }) {
            return HookActionPreparation.Cancelled(HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE)
        }
        val allowedTags = config.allowedTagIds.mapNotNull { tagId ->
            tagRepository.getTag(tagId)?.let { tagId to it.displayName }
        }.toMap()
        return HookActionPreparation.Ready(
            PreparedHookAction.ManageConversationTags(
                request = FrozenHookModelRequest.ManageConversationTags(
                    modelId = hook.modelId,
                    prompt = hook.prompt,
                    messageTextSnapshot = context.sourceMessageTextSnapshot,
                    allowedTags = allowedTags,
                ),
                conversationId = context.conversation.id,
                sourceNodeId = context.sourceNodeId,
                sourceMessageId = context.sourceMessageId,
                allowedTagIds = config.allowedTagIds,
            ),
        )
    }

    override fun parse(raw: String, prepared: PreparedHookAction): ParsedHookOutput =
        ManageConversationTagsHookOutputParser.parse(raw)

    override suspend fun execute(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): HookActionResult {
        val context = prepared as? PreparedHookAction.ManageConversationTags
            ?: return HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
        val manageOutput = output as? ParsedManageConversationTagsHookOutput
            ?: return HookActionResult.Cancelled(HookErrorCode.SCHEMA_MISMATCH)
        if (manageOutput.decision == HookDecision.SKIP) {
            return HookActionResult.Skipped(null)
        }
        if (manageOutput.operations.isEmpty()) {
            return HookActionResult.Skipped(null, HookErrorCode.SCHEMA_MISMATCH)
        }
        for (op in manageOutput.operations) {
            if (op.tagId !in context.allowedTagIds) {
                return HookActionResult.Skipped(op.tagId, HookErrorCode.TAG_NOT_ALLOWED)
            }
            if (tagRepository.getTag(op.tagId) == null) {
                return HookActionResult.Skipped(op.tagId, HookErrorCode.TAG_NOT_FOUND)
            }
        }
        val seen = mutableSetOf<Pair<TagManageOpKind, Uuid>>()
        for (op in manageOutput.operations) {
            val key = op.kind to op.tagId
            if (!seen.add(key)) {
                return HookActionResult.Skipped(op.tagId, HookErrorCode.SCHEMA_MISMATCH)
            }
        }
        committer.commitManageTags(executionId, leaseToken, context, manageOutput)
        return HookActionResult.Terminalized
    }
}
