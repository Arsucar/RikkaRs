package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.HookDAO
import me.rerere.rikkahub.data.db.dao.HookRunCreationResult
import me.rerere.rikkahub.data.db.entity.GenerationLogicalTurnEntity
import me.rerere.rikkahub.data.db.entity.GenerationLogicalTurnPendingToolEntity
import me.rerere.rikkahub.data.db.entity.GenerationLogicalTurnWithPendingTools
import me.rerere.rikkahub.data.db.entity.HookExecutionEntity
import me.rerere.rikkahub.data.db.entity.HookRunEntity
import me.rerere.rikkahub.data.db.entity.HookRunWithExecutions
import me.rerere.rikkahub.data.model.GenerationLogicalTurnRecord
import me.rerere.rikkahub.data.model.GenerationLogicalTurnStatus
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookDispatchPersistenceResult
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionMetadata
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.HookExecutionRecord
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRunHistory
import me.rerere.rikkahub.data.model.HookRunRecord
import me.rerere.rikkahub.data.model.HookRunStatus
import me.rerere.rikkahub.data.model.HookRuntimeRules
import me.rerere.rikkahub.data.model.HookTrigger
import me.rerere.rikkahub.data.model.sanitizeHookError
import me.rerere.rikkahub.data.model.truncateHookReason
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

class HookRepository(
    private val dao: HookDAO,
) {
    suspend fun createLogicalTurn(
        conversationId: Uuid,
        assistantId: Uuid,
        sourceNodeId: Uuid?,
        sourceMessageId: Uuid?,
        invocationKind: String,
        pendingToolCallIds: Set<String> = emptySet(),
    ): Uuid {
        val logicalTurnId = Uuid.random()
        val now = System.currentTimeMillis()
        dao.createLogicalTurn(
            turn = GenerationLogicalTurnEntity(
                logicalTurnId = logicalTurnId.toString(),
                conversationId = conversationId.toString(),
                assistantId = assistantId.toString(),
                sourceNodeId = sourceNodeId?.toString(),
                sourceMessageId = sourceMessageId?.toString(),
                invocationKind = invocationKind,
                status = if (pendingToolCallIds.isEmpty()) {
                    GenerationLogicalTurnStatus.ACTIVE.name
                } else {
                    GenerationLogicalTurnStatus.WAITING_FOR_TOOL.name
                },
                createdAt = now,
                updatedAt = now,
                completedAt = null,
            ),
            pendingTools = pendingToolCallIds.sorted().map { toolCallId ->
                GenerationLogicalTurnPendingToolEntity(logicalTurnId.toString(), toolCallId)
            },
        )
        return logicalTurnId
    }

    suspend fun getLogicalTurn(logicalTurnId: Uuid): GenerationLogicalTurnRecord? =
        dao.getLogicalTurn(logicalTurnId.toString())?.toModel()

    suspend fun findActiveTurnByConversation(conversationId: Uuid): GenerationLogicalTurnRecord? =
        dao.findActiveTurnByConversation(conversationId.toString())?.toModel()

    suspend fun findActiveTurnByPendingToolCall(toolCallId: String): GenerationLogicalTurnRecord? =
        dao.findActiveTurnByPendingToolCall(toolCallId)?.toModel()

    suspend fun updatePendingTools(logicalTurnId: Uuid, pendingToolCallIds: Set<String>): Boolean =
        dao.updatePendingTools(logicalTurnId.toString(), pendingToolCallIds, System.currentTimeMillis())

    suspend fun markTurnFailed(logicalTurnId: Uuid): Boolean = dao.closeLogicalTurn(
        logicalTurnId.toString(),
        GenerationLogicalTurnStatus.FAILED,
        System.currentTimeMillis(),
    )

    suspend fun markTurnCancelled(logicalTurnId: Uuid): Boolean = dao.closeLogicalTurn(
        logicalTurnId.toString(),
        GenerationLogicalTurnStatus.CANCELLED,
        System.currentTimeMillis(),
    )

    suspend fun finalizeAndCreateRunExactlyOnce(
        logicalTurnId: Uuid,
        trigger: HookTrigger,
        nodeId: Uuid?,
        messageId: Uuid?,
        messageModelId: Uuid?,
        hooks: List<HookExecutionMetadata>,
    ): HookDispatchPersistenceResult {
        val turn = dao.getLogicalTurn(logicalTurnId.toString())?.turn
            ?: return HookDispatchPersistenceResult.TurnNotActive
        val now = System.currentTimeMillis()
        val runId = Uuid.random()
        val orderedHooks = hooks.sortedBy { it.hookOrder }
        val run = orderedHooks.takeIf { it.isNotEmpty() }?.let {
            HookRunEntity(
                runId = runId.toString(),
                conversationId = turn.conversationId,
                assistantId = turn.assistantId,
                logicalTurnId = turn.logicalTurnId,
                nodeId = nodeId?.toString(),
                messageId = messageId?.toString(),
                messageModelId = messageModelId?.toString(),
                invocationKind = turn.invocationKind,
                trigger = trigger.name,
                configVersion = orderedHooks.maxOf { hook -> hook.hookConfigVersion },
                configHash = aggregateConfigHash(orderedHooks),
                startedAt = now,
                endedAt = null,
                status = HookRunStatus.QUEUED.name,
                failureCount = 0,
            )
        }
        val executions = orderedHooks.map { hook ->
            HookExecutionEntity(
                executionId = hook.executionId.toString(),
                runId = runId.toString(),
                hookId = hook.hookId.toString(),
                hookOrder = hook.hookOrder,
                hookConfigVersion = hook.hookConfigVersion,
                hookConfigHash = hook.hookConfigHash,
                modelId = hook.modelId.toString(),
                actionType = hook.actionType.name,
                executionMode = hook.executionMode.name,
                startedAt = null,
                endedAt = null,
                status = HookExecutionStatus.QUEUED.name,
                decision = null,
                tagId = null,
                targetDocumentId = null,
                targetTemplateId = null,
                targetScopeType = null,
                targetScopeId = null,
                baseRevision = null,
                resultRevision = null,
                operationCount = null,
                operationSummaryJson = null,
                diffSummaryJson = null,
                retryOfExecutionId = hook.retryOfExecutionId?.toString(),
                idempotencyKey = null,
                reason = null,
                reasonTruncated = false,
                errorCode = null,
                sanitizedError = null,
                durationMs = null,
                leaseToken = 0,
            )
        }
        return when (
            val result = dao.finalizeAndCreateRunExactlyOnce(
                logicalTurnId = logicalTurnId.toString(),
                trigger = trigger.name,
                run = run,
                executions = executions,
                completedAt = now,
            )
        ) {
            is HookRunCreationResult.Created -> HookDispatchPersistenceResult.Created(
                runId = Uuid.parse(result.runId),
                executionIds = result.executionIds.map(Uuid::parse),
            )
            is HookRunCreationResult.Duplicate ->
                HookDispatchPersistenceResult.Duplicate(Uuid.parse(result.runId))
            HookRunCreationResult.NoHooks -> HookDispatchPersistenceResult.NoHooks
            HookRunCreationResult.TurnNotActive -> HookDispatchPersistenceResult.TurnNotActive
        }
    }

    suspend fun getQueuedExecutions(runId: Uuid): List<HookExecutionRecord> =
        dao.getQueuedExecutions(runId.toString()).map { it.toModel() }

    suspend fun claimQueued(executionId: Uuid, leaseToken: Long, now: Instant = Instant.now()): Boolean =
        dao.claimQueued(executionId.toString(), leaseToken, now.toEpochMilli())

    suspend fun isLeaseActive(executionId: Uuid, leaseToken: Long): Boolean =
        dao.isLeaseActive(executionId.toString(), leaseToken)

    suspend fun setExecutionPreparedAudit(
        executionId: Uuid,
        leaseToken: Long,
        tagId: Uuid? = null,
        targetDocumentId: String? = null,
        targetTemplateId: String? = null,
        targetScopeType: String? = null,
        targetScopeId: String? = null,
        baseRevision: Int? = null,
        operationCount: Int? = null,
        operationSummaryJson: String? = null,
        diffSummaryJson: String? = null,
        retryOfExecutionId: Uuid? = null,
        idempotencyKey: String? = null,
    ): Boolean = dao.setExecutionPreparedAudit(
        executionId = executionId.toString(),
        leaseToken = leaseToken,
        tagId = tagId?.toString(),
        targetDocumentId = targetDocumentId,
        targetTemplateId = targetTemplateId,
        targetScopeType = targetScopeType,
        targetScopeId = targetScopeId,
        baseRevision = baseRevision,
        operationCount = operationCount,
        operationSummaryJson = operationSummaryJson,
        diffSummaryJson = diffSummaryJson,
        retryOfExecutionId = retryOfExecutionId?.toString(),
        idempotencyKey = idempotencyKey,
    ) == 1

    suspend fun getExecution(executionId: Uuid): HookExecutionRecord? =
        dao.getExecution(executionId.toString())?.toModel()

    suspend fun getRun(runId: Uuid): HookRunRecord? = dao.getRun(runId.toString())?.toModel()

    suspend fun hasCommittedCursor(executionId: Uuid): Boolean =
        dao.getCursorForExecution(executionId.toString()) != null

    suspend fun completeSuccess(
        executionId: Uuid,
        leaseToken: Long,
        decision: HookDecision,
        tagId: Uuid?,
        reason: String,
        reasonTruncated: Boolean = false,
    ): Boolean = finishExecution(
        executionId,
        leaseToken,
        HookExecutionStatus.SUCCESS,
        decision,
        tagId,
        reason,
        null,
        null,
        reasonTruncated,
    )

    suspend fun completeSkipped(
        executionId: Uuid,
        leaseToken: Long,
        decision: HookDecision? = HookDecision.SKIP,
        tagId: Uuid? = null,
        reason: String? = null,
        errorCode: HookErrorCode? = null,
        sanitizedError: String? = null,
        reasonTruncated: Boolean = false,
    ): Boolean = finishExecution(
        executionId,
        leaseToken,
        HookExecutionStatus.SKIPPED,
        decision,
        tagId,
        reason,
        errorCode,
        sanitizedError,
        reasonTruncated,
    )

    suspend fun completeFailed(
        executionId: Uuid,
        leaseToken: Long,
        errorCode: HookErrorCode,
        sanitizedError: String? = null,
    ): Boolean = finishExecution(
        executionId,
        leaseToken,
        HookExecutionStatus.FAILED,
        null,
        null,
        null,
        errorCode,
        sanitizedError,
        false,
    )

    suspend fun completeCancelled(
        executionId: Uuid,
        leaseToken: Long,
        errorCode: HookErrorCode,
        sanitizedError: String? = null,
    ): Boolean = finishExecution(
        executionId,
        leaseToken,
        HookExecutionStatus.CANCELLED,
        null,
        null,
        null,
        errorCode,
        sanitizedError,
        false,
    )

    suspend fun invalidateLeaseAndFailTimeout(
        executionId: Uuid,
        leaseToken: Long,
        sanitizedError: String? = null,
    ): Boolean = dao.invalidateLeaseAndFailTimeout(
        executionId = executionId.toString(),
        leaseToken = leaseToken,
        errorCode = HookErrorCode.HOOK_TIMEOUT.name,
        sanitizedError = sanitizeHookError(sanitizedError),
        endedAt = System.currentTimeMillis(),
    )

    suspend fun runAndConversationExist(runId: Uuid): Boolean =
        dao.runAndConversationExist(runId.toString())

    fun observeHistory(conversationId: Uuid): Flow<List<HookRunHistory>> =
        dao.observeHistory(conversationId.toString()).map { history ->
            history.map { it.toModel() }
        }

    suspend fun interruptRunningOnStartup(now: Instant = Instant.now()): Int =
        dao.interruptRunningOnStartup(now.toEpochMilli())

    suspend fun cleanupHistory(conversationId: Uuid, now: Instant = Instant.now()): Int {
        val cutoff = now.minus(HookRuntimeRules.RETENTION_DAYS, ChronoUnit.DAYS).toEpochMilli()
        return dao.cleanupHistory(
            conversationId.toString(),
            cutoff,
            HookRuntimeRules.MAX_RUNS_PER_CONVERSATION,
        )
    }

    private suspend fun finishExecution(
        executionId: Uuid,
        leaseToken: Long,
        status: HookExecutionStatus,
        decision: HookDecision?,
        tagId: Uuid?,
        reason: String?,
        errorCode: HookErrorCode?,
        sanitizedError: String?,
        reasonWasTruncated: Boolean,
    ): Boolean {
        val truncatedReason = reason?.let(::truncateHookReason)
        return dao.finishExecution(
            executionId = executionId.toString(),
            leaseToken = leaseToken,
            status = status,
            decision = decision?.name,
            tagId = tagId?.toString(),
            targetDocumentId = null,
            targetTemplateId = null,
            targetScopeType = null,
            targetScopeId = null,
            baseRevision = null,
            resultRevision = null,
            operationCount = null,
            operationSummaryJson = null,
            diffSummaryJson = null,
            retryOfExecutionId = null,
            idempotencyKey = null,
            reason = truncatedReason?.value,
            reasonTruncated = reasonWasTruncated || truncatedReason?.truncated == true,
            errorCode = errorCode?.name,
            sanitizedError = sanitizeHookError(sanitizedError),
            endedAt = System.currentTimeMillis(),
        )
    }
}

private fun GenerationLogicalTurnWithPendingTools.toModel(): GenerationLogicalTurnRecord =
    GenerationLogicalTurnRecord(
        logicalTurnId = Uuid.parse(turn.logicalTurnId),
        conversationId = Uuid.parse(turn.conversationId),
        assistantId = Uuid.parse(turn.assistantId),
        sourceNodeId = turn.sourceNodeId?.let(Uuid::parse),
        sourceMessageId = turn.sourceMessageId?.let(Uuid::parse),
        invocationKind = turn.invocationKind,
        pendingToolCallIds = pendingTools.mapTo(linkedSetOf()) { it.toolCallId },
        status = GenerationLogicalTurnStatus.valueOf(turn.status),
        createdAt = Instant.ofEpochMilli(turn.createdAt),
        updatedAt = Instant.ofEpochMilli(turn.updatedAt),
        completedAt = turn.completedAt?.let(Instant::ofEpochMilli),
    )

private fun HookRunWithExecutions.toModel(): HookRunHistory = HookRunHistory(
    run = run.toModel(),
    executions = executions.sortedBy { it.hookOrder }.map { it.toModel() },
)

private fun HookRunEntity.toModel(): HookRunRecord = HookRunRecord(
    runId = Uuid.parse(runId),
    conversationId = Uuid.parse(conversationId),
    assistantId = Uuid.parse(assistantId),
    logicalTurnId = Uuid.parse(logicalTurnId),
    nodeId = nodeId?.let(Uuid::parse),
    messageId = messageId?.let(Uuid::parse),
    messageModelId = messageModelId?.let(Uuid::parse),
    invocationKind = invocationKind,
    trigger = HookTrigger.valueOf(trigger),
    configVersion = configVersion,
    configHash = configHash,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let(Instant::ofEpochMilli),
    status = HookRunStatus.valueOf(status),
    failureCount = failureCount,
)

private fun HookExecutionEntity.toModel(): HookExecutionRecord = HookExecutionRecord(
    executionId = Uuid.parse(executionId),
    runId = Uuid.parse(runId),
    hookId = Uuid.parse(hookId),
    hookOrder = hookOrder,
    hookConfigVersion = hookConfigVersion,
    hookConfigHash = hookConfigHash,
    modelId = Uuid.parse(modelId),
    actionType = HookActionType.valueOf(actionType),
    executionMode = HookExecutionMode.valueOf(executionMode),
    startedAt = startedAt?.let(Instant::ofEpochMilli),
    endedAt = endedAt?.let(Instant::ofEpochMilli),
    status = HookExecutionStatus.valueOf(status),
    decision = decision?.let(HookDecision::valueOf),
    tagId = tagId?.let(Uuid::parse),
    targetDocumentId = targetDocumentId,
    targetTemplateId = targetTemplateId,
    targetScopeType = targetScopeType?.let(me.rerere.rikkahub.data.model.MemoryTableScopeType::valueOf),
    targetScopeId = targetScopeId,
    baseRevision = baseRevision,
    resultRevision = resultRevision,
    operationCount = operationCount,
    operationSummaryJson = operationSummaryJson,
    diffSummaryJson = diffSummaryJson,
    retryOfExecutionId = retryOfExecutionId?.let(Uuid::parse),
    idempotencyKey = idempotencyKey,
    reason = reason,
    reasonTruncated = reasonTruncated,
    errorCode = errorCode?.let(HookErrorCode::valueOf),
    sanitizedError = sanitizedError,
    duration = durationMs?.let(Duration::ofMillis),
    leaseToken = leaseToken,
)

private fun aggregateConfigHash(hooks: List<HookExecutionMetadata>): String {
    val material = hooks.joinToString("|") { hook ->
        "${hook.hookOrder}:${hook.hookId}:${hook.hookConfigVersion}:${hook.hookConfigHash}"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
