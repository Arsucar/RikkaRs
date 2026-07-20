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
        val existingTagIds = manageOutput.operations
            .map { it.tagId }
            .toSet()
            .filter { tagId -> tagRepository.getTag(tagId) != null }
            .toSet()
        val rejection = validateManageTagOperations(
            operations = manageOutput.operations,
            allowedTagIds = context.allowedTagIds,
            existingTagIds = existingTagIds,
        )
        if (rejection != null) {
            return HookActionResult.Skipped(rejection.tagId, rejection.errorCode)
        }
        committer.commitManageTags(executionId, leaseToken, context, manageOutput)
        return HookActionResult.Terminalized
    }
}

/**
 * Fail-closed pre-write checks for multi-op tag management (C1).
 * Any illegal op rejects the entire batch with zero tag writes.
 */
internal fun validateManageTagOperations(
    operations: List<TagManageOperation>,
    allowedTagIds: Set<Uuid>,
    existingTagIds: Set<Uuid>,
): ManageTagOperationRejection? {
    if (operations.isEmpty()) {
        return ManageTagOperationRejection(null, HookErrorCode.SCHEMA_MISMATCH)
    }
    val seen = mutableSetOf<Pair<TagManageOpKind, Uuid>>()
    for (op in operations) {
        if (op.tagId !in allowedTagIds) {
            return ManageTagOperationRejection(op.tagId, HookErrorCode.TAG_NOT_ALLOWED)
        }
        if (op.tagId !in existingTagIds) {
            return ManageTagOperationRejection(op.tagId, HookErrorCode.TAG_NOT_FOUND)
        }
        if (!seen.add(op.kind to op.tagId)) {
            return ManageTagOperationRejection(op.tagId, HookErrorCode.SCHEMA_MISMATCH)
        }
    }
    return null
}

internal data class ManageTagOperationRejection(
    val tagId: Uuid?,
    val errorCode: HookErrorCode,
)
