package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookActionType
import kotlin.uuid.Uuid

data class HookActionContext(
    val conversationId: Uuid,
    val sourceNodeId: Uuid,
    val sourceMessageId: Uuid,
    val executionId: Uuid,
    val leaseToken: Long,
    val allowedTagIds: Set<Uuid>,
)

sealed interface HookActionResult {
    data class Applied(val tagId: Uuid) : HookActionResult
    data class Skipped(val tagId: Uuid?, val errorCode: HookErrorCode? = null) : HookActionResult
    data class Cancelled(val errorCode: HookErrorCode) : HookActionResult
}

fun interface HookActionHandler {
    suspend fun execute(context: HookActionContext, output: ParsedHookOutput): HookActionResult
}

class HookActionRegistry(handlers: Map<HookActionType, HookActionHandler>) {
    private val handlers = handlers.toMap()

    fun requireHandler(actionType: HookActionType): HookActionHandler = handlers[actionType]
        ?: throw HookOutputException(HookErrorCode.ACTION_FAILED)
}
