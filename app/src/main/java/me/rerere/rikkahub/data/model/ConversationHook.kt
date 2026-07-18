package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ConversationHook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: HookTrigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
    val modelId: Uuid,
    val prompt: String = "",
    val actionConfig: HookActionConfig,
    val configVersion: Long = 1,
)

@Serializable
enum class HookTrigger {
    AFTER_ASSISTANT_RESPONSE_SUCCESS,
}

@Serializable
sealed interface HookActionConfig {
    @Serializable
    @SerialName("add_conversation_tag")
    data class AddConversationTag(
        val allowedTagIds: Set<Uuid> = emptySet(),
    ) : HookActionConfig

    @Serializable
    @SerialName("transition_conversation_tags")
    data class TransitionConversationTags(
        val addTagId: Uuid,
        val removeTagId: Uuid,
        val filter: HookActionFilter = HookActionFilter.GITHUB_ISSUE_COMPLETION,
    ) : HookActionConfig

    @Serializable
    @SerialName("sync_memory_table")
    data class SyncMemoryTable(
        val targetDocumentId: String = "",
        val targetScopeType: MemoryTableScopeType = MemoryTableScopeType.ASSISTANT,
        val recentMessageCount: Int = HookRuntimeRules.DEFAULT_SYNC_MESSAGE_COUNT,
        val includeUserMessages: Boolean = true,
        val includeAssistantMessages: Boolean = true,
        val maxContextChars: Int = HookRuntimeRules.DEFAULT_SYNC_CONTEXT_CHARS,
        val maxOperations: Int = HookRuntimeRules.DEFAULT_SYNC_MAX_OPERATIONS,
        val minimumIntervalSeconds: Int = 0,
        val automatic: Boolean = false,
    ) : HookActionConfig
}

@Serializable
enum class HookActionType {
    ADD_CONVERSATION_TAG,
    TRANSITION_CONVERSATION_TAGS,
    SYNC_MEMORY_TABLE,
}

@Serializable
enum class HookActionFilter {
    GITHUB_ISSUE_COMPLETION,
}

val HookActionConfig.actionType: HookActionType
    get() = when (this) {
        is HookActionConfig.AddConversationTag -> HookActionType.ADD_CONVERSATION_TAG
        is HookActionConfig.TransitionConversationTags -> HookActionType.TRANSITION_CONVERSATION_TAGS
        is HookActionConfig.SyncMemoryTable -> HookActionType.SYNC_MEMORY_TABLE
    }

fun ConversationHook.configurationHash(): String {
    val actionMaterial = when (val action = actionConfig) {
        is HookActionConfig.AddConversationTag -> action.allowedTagIds
            .map { it.toString() }
            .sorted()
            .joinToString(",")
        is HookActionConfig.TransitionConversationTags -> listOf(
            action.addTagId.toString(),
            action.removeTagId.toString(),
            action.filter.name,
        ).joinToString("|") { value -> "${value.length}:$value" }
        is HookActionConfig.SyncMemoryTable -> listOf(
            action.targetDocumentId,
            action.targetScopeType.name,
            action.recentMessageCount.toString(),
            action.includeUserMessages.toString(),
            action.includeAssistantMessages.toString(),
            action.maxContextChars.toString(),
            action.maxOperations.toString(),
            action.minimumIntervalSeconds.toString(),
            action.automatic.toString(),
        ).joinToString("|") { value -> "${value.length}:$value" }
    }
    val material = listOf(
        id.toString(),
        configVersion.toString(),
        name,
        enabled.toString(),
        trigger.name,
        modelId.toString(),
        prompt,
        actionConfig.actionType.name,
        actionMaterial,
    ).joinToString(separator = "|") { value -> "${value.length}:$value" }
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

enum class GenerationLogicalTurnStatus {
    ACTIVE,
    WAITING_FOR_TOOL,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

enum class HookRunStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    SKIPPED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

enum class HookExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    SKIPPED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

enum class HookExecutionMode {
    AUTO,
    MANUAL,
}

fun aggregateHookRunStatus(statuses: List<HookExecutionStatus>): HookRunStatus {
    require(statuses.isNotEmpty())
    if (statuses.all { it == HookExecutionStatus.QUEUED }) return HookRunStatus.QUEUED
    if (statuses.any { it == HookExecutionStatus.QUEUED || it == HookExecutionStatus.RUNNING }) {
        return HookRunStatus.RUNNING
    }
    return when {
        statuses.any { it == HookExecutionStatus.FAILED } -> HookRunStatus.FAILED
        statuses.any { it == HookExecutionStatus.INTERRUPTED } -> HookRunStatus.INTERRUPTED
        statuses.any { it == HookExecutionStatus.CANCELLED } -> HookRunStatus.CANCELLED
        statuses.any { it == HookExecutionStatus.SUCCESS } -> HookRunStatus.SUCCESS
        else -> HookRunStatus.SKIPPED
    }
}

enum class HookDecision {
    APPLY,
    SKIP,
}

enum class HookErrorCode {
    MODEL_NOT_FOUND,
    PROVIDER_NOT_FOUND,
    MODEL_REQUEST_FAILED,
    HOOK_TIMEOUT,
    INVALID_JSON,
    SCHEMA_MISMATCH,
    TAG_NOT_ALLOWED,
    TAG_NOT_FOUND,
    TAG_TRANSITION_CONFLICT,
    TAG_LIMIT_REACHED,
    GITHUB_ISSUE_EVIDENCE_NOT_FOUND,
    CONVERSATION_NOT_FOUND,
    SOURCE_MESSAGE_NOT_ACTIVE,
    HOOK_DISABLED,
    MEMORY_TABLE_DISABLED,
    MEMORY_TABLE_AUTO_SYNC_DISABLED,
    MEMORY_TABLE_AUTOMATIC_DISABLED,
    MEMORY_TABLE_FREQUENCY_LIMIT,
    MEMORY_TABLE_TARGET_NOT_FOUND,
    MEMORY_TABLE_TARGET_DELETED,
    MEMORY_TABLE_TARGET_CHANGED,
    MEMORY_TABLE_SCOPE_FORBIDDEN,
    MEMORY_TABLE_REVISION_CONFLICT,
    MEMORY_TABLE_INVALID_OPERATIONS,
    IDEMPOTENT_REPLAY,
    RETRY_NOT_ALLOWED,
    ACTION_FAILED,
}

data class HookRunRecord(
    val runId: Uuid,
    val conversationId: Uuid,
    val assistantId: Uuid,
    val logicalTurnId: Uuid,
    val nodeId: Uuid?,
    val messageId: Uuid?,
    val messageModelId: Uuid?,
    val invocationKind: String,
    val trigger: HookTrigger,
    val configVersion: Long,
    val configHash: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: HookRunStatus,
    val failureCount: Int,
)

data class HookExecutionRecord(
    val executionId: Uuid,
    val runId: Uuid,
    val hookId: Uuid,
    val hookOrder: Int,
    val hookConfigVersion: Long,
    val hookConfigHash: String,
    val modelId: Uuid,
    val actionType: HookActionType,
    val executionMode: HookExecutionMode,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val status: HookExecutionStatus,
    val decision: HookDecision?,
    val tagId: Uuid?,
    val targetDocumentId: String?,
    val targetTemplateId: String?,
    val targetScopeType: MemoryTableScopeType?,
    val targetScopeId: String?,
    val baseRevision: Int?,
    val resultRevision: Int?,
    val operationCount: Int?,
    val operationSummaryJson: String?,
    val diffSummaryJson: String?,
    val retryOfExecutionId: Uuid?,
    val idempotencyKey: String?,
    val reason: String?,
    val reasonTruncated: Boolean,
    val errorCode: HookErrorCode?,
    val sanitizedError: String?,
    val duration: Duration?,
    val leaseToken: Long,
)

data class HookRunHistory(
    val run: HookRunRecord,
    val executions: List<HookExecutionRecord>,
)

data class GenerationLogicalTurnRecord(
    val logicalTurnId: Uuid,
    val conversationId: Uuid,
    val assistantId: Uuid,
    val sourceNodeId: Uuid?,
    val sourceMessageId: Uuid?,
    val invocationKind: String,
    val pendingToolCallIds: Set<String>,
    val status: GenerationLogicalTurnStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
)

data class HookExecutionMetadata(
    val executionId: Uuid = Uuid.random(),
    val hookId: Uuid,
    val hookOrder: Int,
    val hookConfigVersion: Long,
    val hookConfigHash: String,
    val modelId: Uuid,
    val actionType: HookActionType,
    val executionMode: HookExecutionMode = HookExecutionMode.AUTO,
    val retryOfExecutionId: Uuid? = null,
)

sealed interface HookDispatchPersistenceResult {
    data class Created(
        val runId: Uuid,
        val executionIds: List<Uuid>,
    ) : HookDispatchPersistenceResult

    data class Duplicate(val runId: Uuid) : HookDispatchPersistenceResult
    data object NoHooks : HookDispatchPersistenceResult
    data object TurnNotActive : HookDispatchPersistenceResult
}

object HookRuntimeRules {
    const val MAX_REASON_CODE_POINTS = 500
    const val MAX_SANITIZED_ERROR_CODE_POINTS = 500
    const val MAX_RUNS_PER_CONVERSATION = 100
    const val RETENTION_DAYS = 30L
    const val EXECUTION_TIMEOUT_SECONDS = 30L
    const val DEFAULT_SYNC_MESSAGE_COUNT = 8
    const val MIN_SYNC_MESSAGE_COUNT = 1
    const val MAX_SYNC_MESSAGE_COUNT = 40
    const val DEFAULT_SYNC_CONTEXT_CHARS = 12_000
    const val MIN_SYNC_CONTEXT_CHARS = 256
    const val MAX_SYNC_CONTEXT_CHARS = 64_000
    const val DEFAULT_SYNC_MAX_OPERATIONS = 12
    const val MIN_SYNC_MAX_OPERATIONS = 1
    const val MAX_SYNC_MAX_OPERATIONS = 50
    const val MAX_SYNC_RESPONSE_CHARS = 64_000
    const val MAX_TAG_TRANSITION_RESPONSE_CHARS = 64_000
    const val MAX_SYNC_AUDIT_JSON_CHARS = 8_000
}

data class TruncatedHookText(
    val value: String,
    val truncated: Boolean,
)

fun truncateHookReason(reason: String): TruncatedHookText = truncateHookText(
    value = reason.trim(),
    maxCodePoints = HookRuntimeRules.MAX_REASON_CODE_POINTS,
)

fun sanitizeHookError(error: String?): String? = error
    ?.replace(Regex("(?i)\\bBearer\\s+\\S+"), "Bearer ***")
    ?.replace(
        Regex("(?i)\\b(api[-_ ]?key|authorization|token|secret|password)\\s*[:=]\\s*\\S+"),
        "${'$'}1=***",
    )
    ?.replace(Regex("\\b(?:sk-|ghp_|github_pat_|AIza)[A-Za-z0-9_-]+"), "***")
    ?.replace(Regex("(https?://[^\\s?]+)\\?[^\\s]+"), "${'$'}1?***")
    ?.replace(Regex("[\\r\\n\\t]+"), " ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { truncateHookText(it, HookRuntimeRules.MAX_SANITIZED_ERROR_CODE_POINTS).value }

private fun truncateHookText(value: String, maxCodePoints: Int): TruncatedHookText {
    val codePointCount = Character.codePointCount(value, 0, value.length)
    if (codePointCount <= maxCodePoints) return TruncatedHookText(value, false)
    val endIndex = Character.offsetByCodePoints(value, 0, maxCodePoints)
    return TruncatedHookText(value.substring(0, endIndex), true)
}
