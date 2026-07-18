package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.GenerationLogicalTurnEntity
import me.rerere.rikkahub.data.db.entity.GenerationLogicalTurnPendingToolEntity
import me.rerere.rikkahub.data.db.entity.GenerationLogicalTurnWithPendingTools
import me.rerere.rikkahub.data.db.entity.HookExecutionEntity
import me.rerere.rikkahub.data.db.entity.HookActionCursorEntity
import me.rerere.rikkahub.data.db.entity.HookRunEntity
import me.rerere.rikkahub.data.db.entity.HookRunWithExecutions
import me.rerere.rikkahub.data.model.GenerationLogicalTurnStatus
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRunStatus
import me.rerere.rikkahub.data.model.aggregateHookRunStatus

@Dao
interface HookDAO {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLogicalTurn(turn: GenerationLogicalTurnEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingTools(tools: List<GenerationLogicalTurnPendingToolEntity>)

    @Transaction
    suspend fun createLogicalTurn(
        turn: GenerationLogicalTurnEntity,
        pendingTools: List<GenerationLogicalTurnPendingToolEntity>,
    ) {
        insertLogicalTurn(turn)
        if (pendingTools.isNotEmpty()) insertPendingTools(pendingTools)
    }

    @Query("DELETE FROM generation_logical_turn_pending_tools WHERE logical_turn_id = :logicalTurnId")
    suspend fun deletePendingTools(logicalTurnId: String): Int

    @Transaction
    @Query("SELECT * FROM generation_logical_turns WHERE logical_turn_id = :logicalTurnId")
    suspend fun getLogicalTurn(logicalTurnId: String): GenerationLogicalTurnWithPendingTools?

    @Transaction
    @Query(
        """
        SELECT * FROM generation_logical_turns
        WHERE conversation_id = :conversationId AND status IN ('ACTIVE', 'WAITING_FOR_TOOL')
        ORDER BY created_at DESC LIMIT 1
        """
    )
    suspend fun findActiveTurnByConversation(conversationId: String): GenerationLogicalTurnWithPendingTools?

    @Transaction
    @Query(
        """
        SELECT turns.* FROM generation_logical_turns AS turns
        INNER JOIN generation_logical_turn_pending_tools AS tools
            ON tools.logical_turn_id = turns.logical_turn_id
        WHERE tools.tool_call_id = :toolCallId AND turns.status IN ('ACTIVE', 'WAITING_FOR_TOOL')
        LIMIT 1
        """
    )
    suspend fun findActiveTurnByPendingToolCall(toolCallId: String): GenerationLogicalTurnWithPendingTools?

    @Query(
        """
        UPDATE generation_logical_turns SET
            status = :status,
            updated_at = :updatedAt,
            completed_at = :completedAt
        WHERE logical_turn_id = :logicalTurnId AND status IN ('ACTIVE', 'WAITING_FOR_TOOL')
        """
    )
    suspend fun updateLogicalTurnStatus(
        logicalTurnId: String,
        status: String,
        updatedAt: Long,
        completedAt: Long?,
    ): Int

    @Transaction
    suspend fun closeLogicalTurn(
        logicalTurnId: String,
        status: GenerationLogicalTurnStatus,
        completedAt: Long,
    ): Boolean {
        require(status !in setOf(GenerationLogicalTurnStatus.ACTIVE, GenerationLogicalTurnStatus.WAITING_FOR_TOOL))
        val updated = updateLogicalTurnStatus(logicalTurnId, status.name, completedAt, completedAt)
        if (updated == 1) deletePendingTools(logicalTurnId)
        return updated == 1
    }

    @Transaction
    suspend fun updatePendingTools(
        logicalTurnId: String,
        pendingToolCallIds: Set<String>,
        updatedAt: Long,
    ): Boolean {
        val turn = getLogicalTurn(logicalTurnId)?.turn ?: return false
        if (turn.status !in ACTIVE_LOGICAL_TURN_STATUSES) return false
        deletePendingTools(logicalTurnId)
        if (pendingToolCallIds.isNotEmpty()) {
            insertPendingTools(
                pendingToolCallIds.sorted().map { toolCallId ->
                    GenerationLogicalTurnPendingToolEntity(logicalTurnId, toolCallId)
                }
            )
        }
        return updateLogicalTurnStatus(
            logicalTurnId = logicalTurnId,
            status = if (pendingToolCallIds.isEmpty()) {
                GenerationLogicalTurnStatus.ACTIVE.name
            } else {
                GenerationLogicalTurnStatus.WAITING_FOR_TOOL.name
            },
            updatedAt = updatedAt,
            completedAt = null,
        ) == 1
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRunIgnore(run: HookRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExecutions(executions: List<HookExecutionEntity>)

    @Query("SELECT * FROM hook_runs WHERE logical_turn_id = :logicalTurnId AND trigger = :trigger LIMIT 1")
    suspend fun getRunByLogicalTurn(logicalTurnId: String, trigger: String): HookRunEntity?

    @Transaction
    suspend fun finalizeAndCreateRunExactlyOnce(
        logicalTurnId: String,
        trigger: String,
        run: HookRunEntity?,
        executions: List<HookExecutionEntity>,
        completedAt: Long,
    ): HookRunCreationResult {
        val existing = getRunByLogicalTurn(logicalTurnId, trigger)
        if (existing != null) {
            updateLogicalTurnStatus(
                logicalTurnId,
                GenerationLogicalTurnStatus.COMPLETED.name,
                completedAt,
                completedAt,
            )
            deletePendingTools(logicalTurnId)
            return HookRunCreationResult.Duplicate(existing.runId)
        }
        val turn = getLogicalTurn(logicalTurnId)?.turn ?: return HookRunCreationResult.TurnNotActive
        if (turn.status !in ACTIVE_LOGICAL_TURN_STATUSES) return HookRunCreationResult.TurnNotActive
        if (run == null || executions.isEmpty()) {
            updateLogicalTurnStatus(
                logicalTurnId,
                GenerationLogicalTurnStatus.COMPLETED.name,
                completedAt,
                completedAt,
            )
            deletePendingTools(logicalTurnId)
            return HookRunCreationResult.NoHooks
        }
        if (insertRunIgnore(run) == -1L) {
            val raced = getRunByLogicalTurn(logicalTurnId, trigger)
                ?: return HookRunCreationResult.TurnNotActive
            updateLogicalTurnStatus(
                logicalTurnId,
                GenerationLogicalTurnStatus.COMPLETED.name,
                completedAt,
                completedAt,
            )
            deletePendingTools(logicalTurnId)
            return HookRunCreationResult.Duplicate(raced.runId)
        }
        insertExecutions(executions)
        updateLogicalTurnStatus(
            logicalTurnId,
            GenerationLogicalTurnStatus.COMPLETED.name,
            completedAt,
            completedAt,
        )
        deletePendingTools(logicalTurnId)
        return HookRunCreationResult.Created(run.runId, executions.map { it.executionId })
    }

    @Query("SELECT * FROM hook_executions WHERE run_id = :runId AND status = 'QUEUED' ORDER BY hook_order ASC")
    suspend fun getQueuedExecutions(runId: String): List<HookExecutionEntity>

    @Query("SELECT * FROM hook_executions WHERE execution_id = :executionId")
    suspend fun getExecution(executionId: String): HookExecutionEntity?

    @Query("SELECT * FROM hook_executions WHERE idempotency_key = :idempotencyKey LIMIT 1")
    suspend fun getExecutionByIdempotencyKey(idempotencyKey: String): HookExecutionEntity?

    @Query("SELECT * FROM hook_runs WHERE run_id = :runId")
    suspend fun getRun(runId: String): HookRunEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM hook_runs AS runs
            INNER JOIN ConversationEntity AS conversations ON conversations.id = runs.conversation_id
            WHERE runs.run_id = :runId
        )
        """
    )
    suspend fun runAndConversationExist(runId: String): Boolean

    @Query(
        """
        UPDATE hook_executions SET
            status = 'RUNNING',
            started_at = :startedAt,
            ended_at = NULL,
            lease_token = :leaseToken
        WHERE execution_id = :executionId AND status = 'QUEUED'
        """
    )
    suspend fun claimQueuedRaw(executionId: String, leaseToken: Long, startedAt: Long): Int

    @Query("UPDATE hook_runs SET status = 'RUNNING' WHERE run_id = :runId AND status = 'QUEUED'")
    suspend fun markRunRunning(runId: String): Int

    @Transaction
    suspend fun claimQueued(executionId: String, leaseToken: Long, startedAt: Long): Boolean {
        val execution = getExecution(executionId) ?: return false
        if (claimQueuedRaw(executionId, leaseToken, startedAt) != 1) return false
        markRunRunning(execution.runId)
        return true
    }

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM hook_executions
            WHERE execution_id = :executionId AND status = 'RUNNING' AND lease_token = :leaseToken
        )
        """
    )
    suspend fun isLeaseActive(executionId: String, leaseToken: Long): Boolean

    @Query(
        """
        UPDATE hook_executions SET
            target_document_id = :targetDocumentId,
            target_template_id = :targetTemplateId,
            target_scope_type = :targetScopeType,
            target_scope_id = :targetScopeId,
            base_revision = :baseRevision,
            retry_of_execution_id = COALESCE(:retryOfExecutionId, retry_of_execution_id),
            idempotency_key = :idempotencyKey
        WHERE execution_id = :executionId AND status = 'RUNNING' AND lease_token = :leaseToken
        """
    )
    suspend fun setExecutionPreparedAudit(
        executionId: String,
        leaseToken: Long,
        targetDocumentId: String,
        targetTemplateId: String,
        targetScopeType: String,
        targetScopeId: String,
        baseRevision: Int,
        retryOfExecutionId: String?,
        idempotencyKey: String,
    ): Int

    @Query(
        """
        UPDATE hook_executions SET
            status = :status,
            decision = :decision,
            tag_id = :tagId,
            target_document_id = COALESCE(:targetDocumentId, target_document_id),
            target_template_id = COALESCE(:targetTemplateId, target_template_id),
            target_scope_type = COALESCE(:targetScopeType, target_scope_type),
            target_scope_id = COALESCE(:targetScopeId, target_scope_id),
            base_revision = COALESCE(:baseRevision, base_revision),
            result_revision = :resultRevision,
            operation_count = :operationCount,
            operation_summary_json = :operationSummaryJson,
            diff_summary_json = :diffSummaryJson,
            retry_of_execution_id = COALESCE(:retryOfExecutionId, retry_of_execution_id),
            idempotency_key = COALESCE(:idempotencyKey, idempotency_key),
            reason = :reason,
            reason_truncated = :reasonTruncated,
            error_code = :errorCode,
            sanitized_error = :sanitizedError,
            ended_at = :endedAt,
            duration_ms = CASE WHEN started_at IS NULL THEN NULL ELSE MAX(0, :endedAt - started_at) END
        WHERE execution_id = :executionId AND status = 'RUNNING' AND lease_token = :leaseToken
        """
    )
    suspend fun finishExecutionRaw(
        executionId: String,
        leaseToken: Long,
        status: String,
        decision: String?,
        tagId: String?,
        targetDocumentId: String?,
        targetTemplateId: String?,
        targetScopeType: String?,
        targetScopeId: String?,
        baseRevision: Int?,
        resultRevision: Int?,
        operationCount: Int?,
        operationSummaryJson: String?,
        diffSummaryJson: String?,
        retryOfExecutionId: String?,
        idempotencyKey: String?,
        reason: String?,
        reasonTruncated: Boolean,
        errorCode: String?,
        sanitizedError: String?,
        endedAt: Long,
    ): Int

    @Query("SELECT status FROM hook_executions WHERE run_id = :runId")
    suspend fun getExecutionStatuses(runId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCursorIgnore(cursor: HookActionCursorEntity): Long

    @Query("SELECT * FROM hook_action_cursors WHERE idempotency_key = :idempotencyKey")
    suspend fun getCursor(idempotencyKey: String): HookActionCursorEntity?

    @Query(
        """
        SELECT * FROM hook_action_cursors
        WHERE hook_id = :hookId
          AND hook_config_version = :hookConfigVersion
          AND target_document_id = :targetDocumentId
          AND source_kind = :sourceKind
          AND source_key = :sourceKey
        LIMIT 1
        """
    )
    suspend fun getCursorForSource(
        hookId: String,
        hookConfigVersion: Long,
        targetDocumentId: String,
        sourceKind: String,
        sourceKey: String,
    ): HookActionCursorEntity?

    @Query("SELECT * FROM hook_action_cursors WHERE execution_id = :executionId LIMIT 1")
    suspend fun getCursorForExecution(executionId: String): HookActionCursorEntity?

    @Query(
        """
        SELECT MAX(committed_at) FROM hook_action_cursors
        WHERE hook_id = :hookId AND target_document_id = :targetDocumentId
        """
    )
    suspend fun getLatestCursorCommittedAt(hookId: String, targetDocumentId: String): Long?

    @Query("SELECT COUNT(*) FROM hook_executions WHERE run_id = :runId AND status = 'FAILED'")
    suspend fun countFailedExecutions(runId: String): Int

    @Query(
        """
        UPDATE hook_runs SET
            status = :status,
            failure_count = :failureCount,
            ended_at = :endedAt
        WHERE run_id = :runId
        """
    )
    suspend fun updateRunAggregate(
        runId: String,
        status: String,
        failureCount: Int,
        endedAt: Long?,
    ): Int

    @Transaction
    suspend fun finishExecution(
        executionId: String,
        leaseToken: Long,
        status: HookExecutionStatus,
        decision: String?,
        tagId: String?,
        targetDocumentId: String? = null,
        targetTemplateId: String? = null,
        targetScopeType: String? = null,
        targetScopeId: String? = null,
        baseRevision: Int? = null,
        resultRevision: Int? = null,
        operationCount: Int? = null,
        operationSummaryJson: String? = null,
        diffSummaryJson: String? = null,
        retryOfExecutionId: String? = null,
        idempotencyKey: String? = null,
        reason: String?,
        reasonTruncated: Boolean,
        errorCode: String?,
        sanitizedError: String?,
        endedAt: Long,
    ): Boolean {
        require(status in TERMINAL_EXECUTION_STATUSES)
        val execution = getExecution(executionId) ?: return false
        val updated = finishExecutionRaw(
            executionId,
            leaseToken,
            status.name,
            decision,
            tagId,
            targetDocumentId,
            targetTemplateId,
            targetScopeType,
            targetScopeId,
            baseRevision,
            resultRevision,
            operationCount,
            operationSummaryJson,
            diffSummaryJson,
            retryOfExecutionId,
            idempotencyKey,
            reason,
            reasonTruncated,
            errorCode,
            sanitizedError,
            endedAt,
        )
        if (updated == 1) recalculateRun(execution.runId, endedAt)
        return updated == 1
    }

    @Query(
        """
        UPDATE hook_executions SET lease_token = lease_token + 1
        WHERE execution_id = :executionId AND status = 'RUNNING' AND lease_token = :leaseToken
        """
    )
    suspend fun invalidateLease(executionId: String, leaseToken: Long): Int

    @Query("SELECT lease_token FROM hook_executions WHERE execution_id = :executionId")
    suspend fun getLeaseToken(executionId: String): Long?

    @Transaction
    suspend fun invalidateLeaseAndFailTimeout(
        executionId: String,
        leaseToken: Long,
        errorCode: String,
        sanitizedError: String?,
        endedAt: Long,
    ): Boolean {
        if (invalidateLease(executionId, leaseToken) != 1) return false
        val timeoutToken = getLeaseToken(executionId) ?: return false
        return finishExecution(
            executionId = executionId,
            leaseToken = timeoutToken,
            status = HookExecutionStatus.FAILED,
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
            retryOfExecutionId = null,
            idempotencyKey = null,
            reason = null,
            reasonTruncated = false,
            errorCode = errorCode,
            sanitizedError = sanitizedError,
            endedAt = endedAt,
        )
    }

    @Transaction
    suspend fun recalculateRun(runId: String, now: Long) {
        val statuses = getExecutionStatuses(runId).map(HookExecutionStatus::valueOf)
        if (statuses.isEmpty()) return
        val aggregate = aggregateHookRunStatus(statuses)
        val endedAt = now.takeIf { aggregate !in ACTIVE_RUN_STATUSES }
        updateRunAggregate(runId, aggregate.name, countFailedExecutions(runId), endedAt)
    }

    @Transaction
    @Query("SELECT * FROM hook_runs WHERE conversation_id = :conversationId ORDER BY started_at DESC, run_id DESC")
    fun observeHistory(conversationId: String): Flow<List<HookRunWithExecutions>>

    @Query("SELECT run_id FROM hook_runs WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun getActiveRunIds(): List<String>

    @Query(
        """
        UPDATE hook_executions SET
            status = 'INTERRUPTED',
            ended_at = :interruptedAt,
            duration_ms = CASE WHEN started_at IS NULL THEN NULL ELSE MAX(0, :interruptedAt - started_at) END,
            lease_token = lease_token + 1
        WHERE status IN ('QUEUED', 'RUNNING')
        """
    )
    suspend fun interruptActiveExecutions(interruptedAt: Long): Int

    @Query(
        """
        UPDATE generation_logical_turns SET
            status = 'INTERRUPTED',
            updated_at = :interruptedAt,
            completed_at = :interruptedAt
        WHERE status IN ('ACTIVE', 'WAITING_FOR_TOOL')
        """
    )
    suspend fun interruptActiveLogicalTurns(interruptedAt: Long): Int

    @Transaction
    suspend fun interruptRunningOnStartup(interruptedAt: Long): Int {
        val runIds = getActiveRunIds()
        val executionCount = interruptActiveExecutions(interruptedAt)
        runIds.forEach { recalculateRun(it, interruptedAt) }
        interruptActiveLogicalTurns(interruptedAt)
        return executionCount
    }

    @Query("DELETE FROM hook_runs WHERE conversation_id = :conversationId AND started_at < :cutoff")
    suspend fun deleteRunsOlderThan(conversationId: String, cutoff: Long): Int

    @Query(
        """
        DELETE FROM hook_runs WHERE run_id IN (
            SELECT run_id FROM hook_runs
            WHERE conversation_id = :conversationId
            ORDER BY started_at DESC, run_id DESC
            LIMIT -1 OFFSET :maxRuns
        )
        """
    )
    suspend fun trimRunsToLimit(conversationId: String, maxRuns: Int): Int

    @Query(
        """
        DELETE FROM generation_logical_turns
        WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND updated_at < :cutoff
        """
    )
    suspend fun deleteTerminalLogicalTurnsOlderThan(cutoff: Long): Int

    @Transaction
    suspend fun cleanupHistory(conversationId: String, cutoff: Long, maxRuns: Int): Int {
        val expired = deleteRunsOlderThan(conversationId, cutoff)
        val overflow = trimRunsToLimit(conversationId, maxRuns)
        deleteTerminalLogicalTurnsOlderThan(cutoff)
        return expired + overflow
    }

    companion object {
        private val ACTIVE_LOGICAL_TURN_STATUSES = setOf(
            GenerationLogicalTurnStatus.ACTIVE.name,
            GenerationLogicalTurnStatus.WAITING_FOR_TOOL.name,
        )
        private val TERMINAL_EXECUTION_STATUSES = setOf(
            HookExecutionStatus.SUCCESS,
            HookExecutionStatus.SKIPPED,
            HookExecutionStatus.FAILED,
            HookExecutionStatus.CANCELLED,
            HookExecutionStatus.INTERRUPTED,
        )
        private val ACTIVE_RUN_STATUSES = setOf(HookRunStatus.QUEUED, HookRunStatus.RUNNING)
    }
}

sealed interface HookRunCreationResult {
    data class Created(val runId: String, val executionIds: List<String>) : HookRunCreationResult
    data class Duplicate(val runId: String) : HookRunCreationResult
    data object NoHooks : HookRunCreationResult
    data object TurnNotActive : HookRunCreationResult
}
