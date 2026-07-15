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
    val request: FrozenHookModelRequest,
    val actionContext: HookActionContext,
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

    private suspend fun executeOne(frozen: FrozenHookExecution) = coroutineScope {
        val leaseToken = now().coerceAtLeast(1)
        val executionId = frozen.executionId.toString()
        if (!hookRepository.claimQueued(frozen.executionId, leaseToken)) return@coroutineScope

        val worker = async {
            try {
                val raw = modelExecutor.execute(frozen.request)
                if (!hookRepository.isLeaseActive(frozen.executionId, leaseToken)) return@async
                val parsed = HookOutputParser.parse(raw)
                if (!hookRepository.isLeaseActive(frozen.executionId, leaseToken)) return@async
                val handler = actionRegistry.requireHandler(frozen.hook.actionConfig.actionType)
                val result = handler.execute(
                    frozen.actionContext.copy(leaseToken = leaseToken),
                    parsed,
                )
                finishFromAction(executionId, leaseToken, parsed, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val code = (error as? HookOutputException)?.code ?: HookErrorCode.MODEL_REQUEST_FAILED
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

    private suspend fun finishFromAction(
        executionId: String,
        leaseToken: Long,
        parsed: ParsedHookOutput,
        result: HookActionResult,
    ) {
        val status: HookExecutionStatus
        val errorCode: HookErrorCode?
        val tagId: Uuid?
        when (result) {
            is HookActionResult.Applied -> {
                status = HookExecutionStatus.SUCCESS
                errorCode = null
                tagId = result.tagId
            }
            is HookActionResult.Skipped -> {
                status = HookExecutionStatus.SKIPPED
                errorCode = result.errorCode
                tagId = result.tagId
            }
            is HookActionResult.Cancelled -> {
                status = HookExecutionStatus.CANCELLED
                errorCode = result.errorCode
                tagId = parsed.tagId
            }
        }
        when (status) {
            HookExecutionStatus.SUCCESS -> hookRepository.completeSuccess(
                executionId = Uuid.parse(executionId),
                leaseToken = leaseToken,
                decision = parsed.decision,
                tagId = tagId,
                reason = parsed.reason,
                reasonTruncated = parsed.reasonTruncated,
            )
            HookExecutionStatus.SKIPPED -> hookRepository.completeSkipped(
                executionId = Uuid.parse(executionId),
                leaseToken = leaseToken,
                decision = parsed.decision,
                tagId = tagId,
                reason = parsed.reason,
                errorCode = errorCode,
                reasonTruncated = parsed.reasonTruncated,
            )
            HookExecutionStatus.CANCELLED -> hookRepository.completeCancelled(
                Uuid.parse(executionId), leaseToken, errorCode ?: HookErrorCode.ACTION_FAILED,
            )
            else -> Unit
        }
    }
}
