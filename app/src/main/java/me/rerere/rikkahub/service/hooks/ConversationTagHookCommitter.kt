package me.rerere.rikkahub.service.hooks

import androidx.room.withTransaction
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.ConversationTagDAO
import me.rerere.rikkahub.data.db.dao.HookDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.data.model.ConversationTagException
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

class ConversationTagHookCommitter(
    private val database: AppDatabase,
    private val hookDao: HookDAO,
    private val messageNodeDao: MessageNodeDAO,
    private val tagDao: ConversationTagDAO,
    private val tagRepository: ConversationTagRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun commitManageTags(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction.ManageConversationTags,
        output: ParsedManageConversationTagsHookOutput,
    ) {
        try {
            database.withTransaction {
                val execution = activeExecution(executionId, leaseToken) ?: throw LeaseLost()
                when (validateSource(prepared.conversationId, prepared.sourceNodeId, prepared.sourceMessageId)) {
                    SourceValidation.CONVERSATION_MISSING -> {
                        finish(
                            executionId, leaseToken, execution.runId, HookExecutionStatus.CANCELLED,
                            decision = null, tagId = null, reason = null, reasonTruncated = false,
                            errorCode = HookErrorCode.CONVERSATION_NOT_FOUND,
                        )
                        return@withTransaction
                    }
                    SourceValidation.INACTIVE -> {
                        finish(
                            executionId, leaseToken, execution.runId, HookExecutionStatus.CANCELLED,
                            decision = null, tagId = null, reason = null, reasonTruncated = false,
                            errorCode = HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE,
                        )
                        return@withTransaction
                    }
                    SourceValidation.ACTIVE -> Unit
                }
                for (op in output.operations) {
                    if (tagDao.getTagById(op.tagId.toString()) == null) {
                        finish(
                            executionId, leaseToken, execution.runId, HookExecutionStatus.SKIPPED,
                            decision = HookDecision.APPLY,
                            tagId = op.tagId,
                            reason = output.reason,
                            reasonTruncated = output.reasonTruncated,
                            errorCode = HookErrorCode.TAG_NOT_FOUND,
                        )
                        return@withTransaction
                    }
                }
                var changedCount = 0
                try {
                    for (op in output.operations) {
                        val changed = when (op.kind) {
                            TagManageOpKind.ADD -> tagRepository.addTag(prepared.conversationId, op.tagId)
                            TagManageOpKind.REMOVE -> tagRepository.removeTag(prepared.conversationId, op.tagId)
                        }
                        if (changed) changedCount += 1
                    }
                } catch (error: ConversationTagException) {
                    throw RollbackFailure(mapTransitionError(error.code))
                }
                val primaryTagId = output.operations.firstOrNull()?.tagId
                val summaryJson = manageTagsAuditSummaryJson(output.operations, changedCount)
                val diffJson = manageTagsAuditDiffJson(output.operations)
                finish(
                    executionId = executionId,
                    leaseToken = leaseToken,
                    runId = execution.runId,
                    status = if (changedCount == 0) HookExecutionStatus.SKIPPED else HookExecutionStatus.SUCCESS,
                    decision = HookDecision.APPLY,
                    tagId = primaryTagId,
                    operationCount = changedCount,
                    operationSummaryJson = summaryJson,
                    diffSummaryJson = diffJson,
                    reason = output.reason,
                    reasonTruncated = output.reasonTruncated,
                    errorCode = null,
                )
            }
        } catch (_: LeaseLost) {
        } catch (failure: RollbackFailure) {
            try {
                database.withTransaction {
                    val execution = activeExecution(executionId, leaseToken) ?: throw LeaseLost()
                    finish(
                        executionId = executionId,
                        leaseToken = leaseToken,
                        runId = execution.runId,
                        status = HookExecutionStatus.FAILED,
                        decision = HookDecision.APPLY,
                        tagId = output.operations.firstOrNull()?.tagId,
                        reason = output.reason,
                        reasonTruncated = output.reasonTruncated,
                        errorCode = failure.errorCode,
                    )
                }
            } catch (_: LeaseLost) {
            }
        } catch (error: ConversationTagException) {
            try {
                database.withTransaction {
                    val execution = activeExecution(executionId, leaseToken) ?: throw LeaseLost()
                    finish(
                        executionId = executionId,
                        leaseToken = leaseToken,
                        runId = execution.runId,
                        status = HookExecutionStatus.FAILED,
                        decision = HookDecision.APPLY,
                        tagId = output.operations.firstOrNull()?.tagId,
                        reason = output.reason,
                        reasonTruncated = output.reasonTruncated,
                        errorCode = mapTransitionError(error.code),
                    )
                }
            } catch (_: LeaseLost) {
            }
        }
    }

    suspend fun commitAdd(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction.AddConversationTag,
        output: ParsedAddTagHookOutput,
        tagId: Uuid,
    ) {
        try {
            database.withTransaction {
                val execution = activeExecution(executionId, leaseToken) ?: throw LeaseLost()
                when (validateSource(prepared.conversationId, prepared.sourceNodeId, prepared.sourceMessageId)) {
                    SourceValidation.CONVERSATION_MISSING -> {
                        finish(
                            executionId, leaseToken, execution.runId, HookExecutionStatus.CANCELLED,
                            decision = null, tagId = null, reason = null, reasonTruncated = false,
                            errorCode = HookErrorCode.CONVERSATION_NOT_FOUND,
                        )
                        return@withTransaction
                    }
                    SourceValidation.INACTIVE -> {
                        finish(
                            executionId, leaseToken, execution.runId, HookExecutionStatus.CANCELLED,
                            decision = null, tagId = null, reason = null, reasonTruncated = false,
                            errorCode = HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE,
                        )
                        return@withTransaction
                    }
                    SourceValidation.ACTIVE -> Unit
                }
                if (tagDao.getTagById(tagId.toString()) == null) {
                    finish(
                        executionId, leaseToken, execution.runId, HookExecutionStatus.SKIPPED,
                        decision = HookDecision.APPLY, tagId = tagId, reason = output.reason,
                        reasonTruncated = output.reasonTruncated, errorCode = HookErrorCode.TAG_NOT_FOUND,
                    )
                    return@withTransaction
                }
                val changed = try {
                    tagRepository.addTag(prepared.conversationId, tagId)
                } catch (error: ConversationTagException) {
                    when (error.code) {
                        ConversationTagErrorCode.TAG_NOT_FOUND -> {
                            finish(
                                executionId, leaseToken, execution.runId, HookExecutionStatus.SKIPPED,
                                decision = HookDecision.APPLY, tagId = tagId, reason = output.reason,
                                reasonTruncated = output.reasonTruncated, errorCode = HookErrorCode.TAG_NOT_FOUND,
                            )
                            return@withTransaction
                        }
                        ConversationTagErrorCode.CONVERSATION_NOT_FOUND -> {
                            finish(
                                executionId, leaseToken, execution.runId, HookExecutionStatus.CANCELLED,
                                decision = null, tagId = null, reason = null, reasonTruncated = false,
                                errorCode = HookErrorCode.CONVERSATION_NOT_FOUND,
                            )
                            return@withTransaction
                        }
                        else -> {
                            finish(
                                executionId, leaseToken, execution.runId, HookExecutionStatus.CANCELLED,
                                decision = null, tagId = null, reason = null, reasonTruncated = false,
                                errorCode = HookErrorCode.ACTION_FAILED,
                            )
                            return@withTransaction
                        }
                    }
                }
                finish(
                    executionId = executionId,
                    leaseToken = leaseToken,
                    runId = execution.runId,
                    status = if (changed) HookExecutionStatus.SUCCESS else HookExecutionStatus.SKIPPED,
                    decision = HookDecision.APPLY,
                    tagId = tagId,
                    reason = output.reason,
                    reasonTruncated = output.reasonTruncated,
                    errorCode = null,
                )
            }
        } catch (_: LeaseLost) {
            // Timeout or another terminal owner won the lease. Never overwrite its row.
        }
    }

    suspend fun commitTransition(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction.TransitionConversationTags,
        output: ParsedTransitionConversationTagsHookOutput,
    ) {
        try {
            database.withTransaction {
                val execution = activeExecution(executionId, leaseToken) ?: throw LeaseLost()
                when (validateSource(prepared.conversationId, prepared.sourceNodeId, prepared.sourceMessageId)) {
                    SourceValidation.CONVERSATION_MISSING -> {
                        finishTransitionWithoutMutation(
                            executionId, leaseToken, execution.runId, prepared,
                            HookExecutionStatus.CANCELLED, HookErrorCode.CONVERSATION_NOT_FOUND,
                        )
                        return@withTransaction
                    }
                    SourceValidation.INACTIVE -> {
                        finishTransitionWithoutMutation(
                            executionId, leaseToken, execution.runId, prepared,
                            HookExecutionStatus.CANCELLED, HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE,
                        )
                        return@withTransaction
                    }
                    SourceValidation.ACTIVE -> Unit
                }
                if (prepared.addTagId == prepared.removeTagId) {
                    finishTransitionWithoutMutation(
                        executionId, leaseToken, execution.runId, prepared,
                        HookExecutionStatus.FAILED, HookErrorCode.TAG_TRANSITION_CONFLICT,
                    )
                    return@withTransaction
                }
                if (tagDao.getTagById(prepared.addTagId.toString()) == null ||
                    tagDao.getTagById(prepared.removeTagId.toString()) == null
                ) {
                    finishTransitionWithoutMutation(
                        executionId, leaseToken, execution.runId, prepared,
                        HookExecutionStatus.FAILED, HookErrorCode.TAG_NOT_FOUND,
                    )
                    return@withTransaction
                }
                val removed = tagRepository.removeTag(prepared.conversationId, prepared.removeTagId)
                val added = try {
                    tagRepository.addTag(prepared.conversationId, prepared.addTagId)
                } catch (error: ConversationTagException) {
                    throw RollbackFailure(mapTransitionError(error.code))
                }
                val operationCount = listOf(removed, added).count { it }
                val config = HookActionConfig.TransitionConversationTags(
                    addTagId = prepared.addTagId,
                    removeTagId = prepared.removeTagId,
                )
                finish(
                    executionId = executionId,
                    leaseToken = leaseToken,
                    runId = execution.runId,
                    status = if (operationCount == 0) HookExecutionStatus.SKIPPED else HookExecutionStatus.SUCCESS,
                    decision = HookDecision.APPLY,
                    tagId = prepared.addTagId,
                    operationCount = operationCount,
                    operationSummaryJson = transitionAuditSummaryJson(config, prepared.evidence, added, removed),
                    diffSummaryJson = transitionAuditDiffJson(
                        addTagId = prepared.addTagId,
                        removeTagId = prepared.removeTagId,
                        added = added,
                        removed = removed,
                    ),
                    reason = output.reason,
                    reasonTruncated = output.reasonTruncated,
                    errorCode = null,
                )
            }
        } catch (_: LeaseLost) {
            // Timeout or another terminal owner won the lease. Never overwrite its row.
        } catch (failure: RollbackFailure) {
            finishTransitionFailureAfterRollback(executionId, leaseToken, prepared, failure.errorCode)
        } catch (error: ConversationTagException) {
            finishTransitionFailureAfterRollback(
                executionId,
                leaseToken,
                prepared,
                mapTransitionError(error.code),
            )
        }
    }

    private suspend fun finishTransitionFailureAfterRollback(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction.TransitionConversationTags,
        errorCode: HookErrorCode,
    ) {
        try {
            database.withTransaction {
                val execution = activeExecution(executionId, leaseToken) ?: throw LeaseLost()
                finishTransitionWithoutMutation(
                    executionId,
                    leaseToken,
                    execution.runId,
                    prepared,
                    HookExecutionStatus.FAILED,
                    errorCode,
                )
            }
        } catch (_: LeaseLost) {
            // Preserve the row already terminalized by the timeout owner.
        }
    }

    private suspend fun finishTransitionWithoutMutation(
        executionId: Uuid,
        leaseToken: Long,
        runId: String,
        prepared: PreparedHookAction.TransitionConversationTags,
        status: HookExecutionStatus,
        errorCode: HookErrorCode,
    ) = finish(
        executionId = executionId,
        leaseToken = leaseToken,
        runId = runId,
        status = status,
        decision = null,
        tagId = prepared.addTagId,
        reason = null,
        reasonTruncated = false,
        errorCode = errorCode,
    )

    private suspend fun activeExecution(executionId: Uuid, leaseToken: Long) =
        hookDao.getExecution(executionId.toString())
            ?.takeIf { hookDao.isLeaseActive(executionId.toString(), leaseToken) }

    private suspend fun validateSource(
        conversationId: Uuid,
        sourceNodeId: Uuid,
        sourceMessageId: Uuid,
    ): SourceValidation {
        if (!tagDao.conversationExists(conversationId.toString())) return SourceValidation.CONVERSATION_MISSING
        val node = messageNodeDao.getNode(conversationId.toString(), sourceNodeId.toString())
            ?: return SourceValidation.INACTIVE
        if (node.hidden) return SourceValidation.INACTIVE
        val selectedMessageId = runCatching {
            JsonInstant.decodeFromString<List<UIMessage>>(node.messages)
                .getOrNull(node.selectIndex)
                ?.id
        }.getOrNull()
        return if (selectedMessageId == sourceMessageId) SourceValidation.ACTIVE else SourceValidation.INACTIVE
    }

    private suspend fun finish(
        executionId: Uuid,
        leaseToken: Long,
        runId: String,
        status: HookExecutionStatus,
        decision: HookDecision?,
        tagId: Uuid?,
        operationCount: Int? = null,
        operationSummaryJson: String? = null,
        diffSummaryJson: String? = null,
        reason: String?,
        reasonTruncated: Boolean,
        errorCode: HookErrorCode?,
    ) {
        val endedAt = now()
        val updated = hookDao.finishExecutionRaw(
            executionId = executionId.toString(),
            leaseToken = leaseToken,
            status = status.name,
            decision = decision?.name,
            tagId = tagId?.toString(),
            targetDocumentId = null,
            targetTemplateId = null,
            targetScopeType = null,
            targetScopeId = null,
            baseRevision = null,
            resultRevision = null,
            operationCount = operationCount,
            operationSummaryJson = operationSummaryJson,
            diffSummaryJson = diffSummaryJson,
            retryOfExecutionId = null,
            idempotencyKey = null,
            reason = reason,
            reasonTruncated = reasonTruncated,
            errorCode = errorCode?.name,
            sanitizedError = null,
            endedAt = endedAt,
        )
        if (updated != 1) throw LeaseLost()
        hookDao.recalculateRun(runId, endedAt)
    }

    private fun mapTransitionError(code: ConversationTagErrorCode): HookErrorCode = when (code) {
        ConversationTagErrorCode.TAG_NOT_FOUND -> HookErrorCode.TAG_NOT_FOUND
        ConversationTagErrorCode.CONVERSATION_NOT_FOUND -> HookErrorCode.CONVERSATION_NOT_FOUND
        ConversationTagErrorCode.CONVERSATION_TAG_LIMIT_REACHED,
        ConversationTagErrorCode.TAG_LIMIT_REACHED,
        -> HookErrorCode.TAG_LIMIT_REACHED
        ConversationTagErrorCode.SAME_TAG -> HookErrorCode.TAG_TRANSITION_CONFLICT
        else -> HookErrorCode.ACTION_FAILED
    }

    private fun manageTagsAuditSummaryJson(operations: List<TagManageOperation>, changedCount: Int): String {
        val ops = operations.joinToString(",") { op ->
            """{"op":"${op.kind.name.lowercase()}","tagId":"${op.tagId}"}"""
        }
        return """{"action":"manage_conversation_tags","operationCount":${operations.size},"changedCount":$changedCount,"ops":[$ops]}"""
    }

    private fun manageTagsAuditDiffJson(operations: List<TagManageOperation>): String {
        val ops = operations.joinToString(",") { op ->
            """{"op":"${op.kind.name.lowercase()}","tagId":"${op.tagId}"}"""
        }
        return "[$ops]"
    }

    private enum class SourceValidation {
        ACTIVE,
        CONVERSATION_MISSING,
        INACTIVE,
    }

    private class LeaseLost : RuntimeException()
    private class RollbackFailure(val errorCode: HookErrorCode) : RuntimeException()
}
