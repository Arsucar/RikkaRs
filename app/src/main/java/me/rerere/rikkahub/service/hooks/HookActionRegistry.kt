package me.rerere.rikkahub.service.hooks

import kotlinx.serialization.json.JsonArray
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import kotlin.uuid.Uuid

data class HookFreezeContext(
    val conversation: Conversation,
    val assistantId: Uuid,
    val logicalTurnId: Uuid,
    val sourceNodeId: Uuid,
    val sourceMessageId: Uuid,
    val sourceMessageModelId: Uuid?,
    val executionMode: HookExecutionMode,
    val sourceKey: String = logicalTurnId.toString(),
    val retryOfExecutionId: Uuid? = null,
)

data class HookPreparedAudit(
    val targetDocumentId: String,
    val targetTemplateId: String,
    val targetScopeType: MemoryTableScopeType,
    val targetScopeId: String,
    val baseRevision: Int,
    val idempotencyKey: String,
    val retryOfExecutionId: Uuid?,
)

sealed interface PreparedHookAction {
    val request: FrozenHookModelRequest
    val audit: HookPreparedAudit?

    data class AddConversationTag(
        override val request: FrozenHookModelRequest.AddConversationTag,
        val conversationId: Uuid,
        val sourceNodeId: Uuid,
        val sourceMessageId: Uuid,
        val allowedTagIds: Set<Uuid>,
    ) : PreparedHookAction {
        override val audit: HookPreparedAudit? = null
    }

    data class SyncMemoryTable(
        override val request: FrozenHookModelRequest.SyncMemoryTable,
        override val audit: HookPreparedAudit,
        val hookId: Uuid,
        val hookConfigVersion: Long,
        val hookConfigHash: String,
        val assistantId: Uuid,
        val conversationId: Uuid,
        val logicalTurnId: Uuid,
        val cutoffMessageId: Uuid,
        val sourceKind: String,
        val sourceKey: String,
        val target: MemoryTableDocument,
        val schemaJson: String,
        val config: HookActionConfig.SyncMemoryTable,
    ) : PreparedHookAction
}

sealed interface HookActionPreparation {
    data class Ready(val prepared: PreparedHookAction) : HookActionPreparation
    data class Skipped(
        val errorCode: HookErrorCode,
        val reason: String? = null,
        val audit: HookPreparedAudit? = null,
    ) : HookActionPreparation

    data class Cancelled(val errorCode: HookErrorCode) : HookActionPreparation
}

sealed interface HookActionResult {
    data class Applied(val tagId: Uuid) : HookActionResult
    data class Skipped(val tagId: Uuid?, val errorCode: HookErrorCode? = null) : HookActionResult
    data class Cancelled(val errorCode: HookErrorCode) : HookActionResult
    data object Terminalized : HookActionResult
}

data class MemoryTableHookPreview(
    val hookId: Uuid,
    val hookConfigVersion: Long,
    val hookConfigHash: String,
    val conversationId: Uuid,
    val cutoffMessageId: Uuid,
    val targetDocumentId: String,
    val targetTemplateId: String,
    val targetScopeType: MemoryTableScopeType,
    val targetScopeId: String,
    val baseRevision: Int,
    val decision: HookDecision,
    val operationCount: Int,
    val operationSummaryJson: String,
    val diffSummaryJson: String,
    val reason: String,
    internal val operations: JsonArray,
    internal val sourceKey: String,
    internal val prepared: PreparedHookAction.SyncMemoryTable,
)

interface HookActionHandler {
    val actionType: HookActionType

    suspend fun prepare(
        hook: ConversationHook,
        context: HookFreezeContext,
    ): HookActionPreparation

    fun parse(raw: String, prepared: PreparedHookAction): ParsedHookOutput

    suspend fun preview(
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): MemoryTableHookPreview? = null

    suspend fun execute(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): HookActionResult
}

class HookActionRegistry(handlers: Collection<HookActionHandler>) {
    private val handlers = handlers.associateBy { it.actionType }

    fun requireHandler(actionType: HookActionType): HookActionHandler = handlers[actionType]
        ?: throw HookOutputException(HookErrorCode.ACTION_FAILED)
}

internal fun ParsedHookOutput.requireDecision(expected: HookDecision) {
    if (decision != expected) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
}
