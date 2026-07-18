package me.rerere.rikkahub.service.hooks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRuntimeRules
import me.rerere.rikkahub.data.model.actionType
import me.rerere.rikkahub.data.model.sanitizeHookError
import me.rerere.rikkahub.data.repository.HookRepository
import kotlin.uuid.Uuid

data class FrozenHookExecution(
    val executionId: Uuid,
    val hook: ConversationHook,
    val freezeContext: HookFreezeContext,
    val preparedOverride: PreparedHookAction? = null,
    val outputOverride: ParsedHookOutput? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HookDispatcher(
    private val hookRepository: HookRepository,
    private val modelExecutor: HookModelExecutor,
    private val actionRegistry: HookActionRegistry,
    private val timeoutMillis: Long = HookRuntimeRules.EXECUTION_TIMEOUT_SECONDS * 1_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun dispatch(runId: Uuid, executions: List<FrozenHookExecution>) {
        executions.forEach { execution ->
            runCatching { executeOne(execution) }
            // A failed hook is terminalized inside executeOne; the next hook must still run.
        }
    }

    suspend fun preview(
        hook: ConversationHook,
        freezeContext: HookFreezeContext,
    ): MemoryTableHookPreview {
        val handler = actionRegistry.requireHandler(hook.actionConfig.actionType)
        val prepared = when (val preparation = handler.prepare(hook, freezeContext)) {
            is HookActionPreparation.Ready -> preparation.prepared
            is HookActionPreparation.Skipped -> throw HookOutputException(preparation.errorCode)
            is HookActionPreparation.Cancelled -> throw HookOutputException(preparation.errorCode)
        }
        val raw = try {
            modelExecutor.execute(prepared.request)
        } catch (error: HookOutputException) {
            throw error
        } catch (_: Throwable) {
            throw HookOutputException(HookErrorCode.MODEL_REQUEST_FAILED)
        }
        val output = try {
            handler.parse(raw, prepared)
        } catch (error: HookOutputException) {
            throw error
        } catch (_: Throwable) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        return handler.preview(prepared, output)
            ?: throw HookOutputException(HookErrorCode.ACTION_FAILED)
    }

    private suspend fun executeOne(frozen: FrozenHookExecution) = coroutineScope {
        val leaseToken = now().coerceAtLeast(1)
        if (!hookRepository.claimQueued(frozen.executionId, leaseToken)) return@coroutineScope

        val worker = async {
            var fallbackCode = HookErrorCode.ACTION_FAILED
            try {
                val handler = actionRegistry.requireHandler(frozen.hook.actionConfig.actionType)
                val prepared = frozen.preparedOverride ?: when (
                    val preparation = handler.prepare(frozen.hook, frozen.freezeContext)
                ) {
                    is HookActionPreparation.Ready -> preparation.prepared
                    is HookActionPreparation.Skipped -> {
                        preparation.audit?.let { audit -> setPreparedAudit(frozen.executionId, leaseToken, audit) }
                        hookRepository.completeSkipped(
                            executionId = frozen.executionId,
                            leaseToken = leaseToken,
                            reason = preparation.reason,
                            errorCode = preparation.errorCode,
                        )
                        return@async
                    }
                    is HookActionPreparation.Cancelled -> {
                        hookRepository.completeCancelled(
                            frozen.executionId,
                            leaseToken,
                            preparation.errorCode,
                        )
                        return@async
                    }
                }
                prepared.audit?.let { audit ->
                    if (!setPreparedAudit(frozen.executionId, leaseToken, audit)) return@async
                }
                if (!hookRepository.isLeaseActive(frozen.executionId, leaseToken)) return@async
                val parsed = frozen.outputOverride ?: run {
                    fallbackCode = HookErrorCode.MODEL_REQUEST_FAILED
                    val raw = modelExecutor.execute(prepared.request)
                    if (!hookRepository.isLeaseActive(frozen.executionId, leaseToken)) return@async
                    fallbackCode = HookErrorCode.SCHEMA_MISMATCH
                    handler.parse(raw, prepared)
                }
                if (!hookRepository.isLeaseActive(frozen.executionId, leaseToken)) return@async
                fallbackCode = HookErrorCode.ACTION_FAILED
                val result = handler.execute(
                    executionId = frozen.executionId,
                    leaseToken = leaseToken,
                    prepared = prepared,
                    output = parsed,
                )
                finishFromAction(frozen.executionId, leaseToken, parsed, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val code = (error as? HookOutputException)?.code ?: fallbackCode
                hookRepository.completeFailed(
                    executionId = frozen.executionId,
                    leaseToken = leaseToken,
                    errorCode = code,
                    sanitizedError = sanitizeHookError(error.message),
                )
            }
        }

        select<Unit> {
            worker.onAwait { }
            onTimeout(timeoutMillis) {
                hookRepository.invalidateLeaseAndFailTimeout(
                    executionId = frozen.executionId,
                    leaseToken = leaseToken,
                    sanitizedError = null,
                )
                worker.cancelAndJoin()
            }
        }
    }

    private suspend fun setPreparedAudit(
        executionId: Uuid,
        leaseToken: Long,
        audit: HookPreparedAudit,
    ): Boolean = hookRepository.setExecutionPreparedAudit(
        executionId = executionId,
        leaseToken = leaseToken,
        targetDocumentId = audit.targetDocumentId,
        targetTemplateId = audit.targetTemplateId,
        targetScopeType = audit.targetScopeType.name,
        targetScopeId = audit.targetScopeId,
        baseRevision = audit.baseRevision,
        retryOfExecutionId = audit.retryOfExecutionId,
        idempotencyKey = audit.idempotencyKey,
    )

    private suspend fun finishFromAction(
        executionId: Uuid,
        leaseToken: Long,
        parsed: ParsedHookOutput,
        result: HookActionResult,
    ) {
        when (result) {
            is HookActionResult.Applied -> hookRepository.completeSuccess(
                executionId = executionId,
                leaseToken = leaseToken,
                decision = parsed.decision,
                tagId = result.tagId,
                reason = parsed.reason,
                reasonTruncated = parsed.reasonTruncated,
            )
            is HookActionResult.Skipped -> hookRepository.completeSkipped(
                executionId = executionId,
                leaseToken = leaseToken,
                decision = parsed.decision,
                tagId = result.tagId,
                reason = parsed.reason,
                errorCode = result.errorCode,
                reasonTruncated = parsed.reasonTruncated,
            )
            is HookActionResult.Cancelled -> hookRepository.completeCancelled(
                executionId,
                leaseToken,
                result.errorCode,
            )
            HookActionResult.Terminalized -> Unit
        }
    }
}
