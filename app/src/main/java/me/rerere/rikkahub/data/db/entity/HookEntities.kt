package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "generation_logical_turns",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "status"]),
        Index(value = ["updated_at"]),
    ],
)
data class GenerationLogicalTurnEntity(
    @PrimaryKey
    @ColumnInfo("logical_turn_id")
    val logicalTurnId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("source_node_id")
    val sourceNodeId: String?,
    @ColumnInfo("source_message_id")
    val sourceMessageId: String?,
    @ColumnInfo("invocation_kind")
    val invocationKind: String,
    val status: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
    @ColumnInfo("completed_at")
    val completedAt: Long?,
)

@Entity(
    tableName = "generation_logical_turn_pending_tools",
    primaryKeys = ["logical_turn_id", "tool_call_id"],
    foreignKeys = [
        ForeignKey(
            entity = GenerationLogicalTurnEntity::class,
            parentColumns = ["logical_turn_id"],
            childColumns = ["logical_turn_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tool_call_id"], unique = true)],
)
data class GenerationLogicalTurnPendingToolEntity(
    @ColumnInfo("logical_turn_id")
    val logicalTurnId: String,
    @ColumnInfo("tool_call_id")
    val toolCallId: String,
)

data class GenerationLogicalTurnWithPendingTools(
    @Embedded
    val turn: GenerationLogicalTurnEntity,
    @Relation(
        parentColumn = "logical_turn_id",
        entityColumn = "logical_turn_id",
    )
    val pendingTools: List<GenerationLogicalTurnPendingToolEntity>,
)

@Entity(
    tableName = "hook_runs",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["logical_turn_id", "trigger"], unique = true),
        Index(value = ["conversation_id", "started_at"]),
        Index(value = ["status"]),
    ],
)
data class HookRunEntity(
    @PrimaryKey
    @ColumnInfo("run_id")
    val runId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("logical_turn_id")
    val logicalTurnId: String,
    @ColumnInfo("node_id")
    val nodeId: String?,
    @ColumnInfo("message_id")
    val messageId: String?,
    @ColumnInfo("message_model_id")
    val messageModelId: String?,
    @ColumnInfo("invocation_kind")
    val invocationKind: String,
    val trigger: String,
    @ColumnInfo("config_version")
    val configVersion: Long,
    @ColumnInfo("config_hash")
    val configHash: String,
    @ColumnInfo("started_at")
    val startedAt: Long,
    @ColumnInfo("ended_at")
    val endedAt: Long?,
    val status: String,
    @ColumnInfo("failure_count")
    val failureCount: Int,
)

@Entity(
    tableName = "hook_executions",
    foreignKeys = [
        ForeignKey(
            entity = HookRunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["run_id", "hook_id", "hook_config_version"], unique = true),
        Index(value = ["run_id", "hook_order"]),
        Index(value = ["status"]),
        Index(value = ["target_document_id", "ended_at"]),
        Index(value = ["retry_of_execution_id"]),
    ],
)
data class HookExecutionEntity(
    @PrimaryKey
    @ColumnInfo("execution_id")
    val executionId: String,
    @ColumnInfo("run_id")
    val runId: String,
    @ColumnInfo("hook_id")
    val hookId: String,
    @ColumnInfo("hook_order")
    val hookOrder: Int,
    @ColumnInfo("hook_config_version")
    val hookConfigVersion: Long,
    @ColumnInfo("hook_config_hash")
    val hookConfigHash: String,
    @ColumnInfo("model_id")
    val modelId: String,
    @ColumnInfo("action_type")
    val actionType: String,
    @ColumnInfo("execution_mode", defaultValue = "'AUTO'")
    val executionMode: String,
    @ColumnInfo("started_at")
    val startedAt: Long?,
    @ColumnInfo("ended_at")
    val endedAt: Long?,
    val status: String,
    val decision: String?,
    @ColumnInfo("tag_id")
    val tagId: String?,
    @ColumnInfo("target_document_id")
    val targetDocumentId: String?,
    @ColumnInfo("target_template_id")
    val targetTemplateId: String?,
    @ColumnInfo("target_scope_type")
    val targetScopeType: String?,
    @ColumnInfo("target_scope_id")
    val targetScopeId: String?,
    @ColumnInfo("base_revision")
    val baseRevision: Int?,
    @ColumnInfo("result_revision")
    val resultRevision: Int?,
    @ColumnInfo("operation_count")
    val operationCount: Int?,
    @ColumnInfo("operation_summary_json")
    val operationSummaryJson: String?,
    @ColumnInfo("diff_summary_json")
    val diffSummaryJson: String?,
    @ColumnInfo("retry_of_execution_id")
    val retryOfExecutionId: String?,
    @ColumnInfo("idempotency_key")
    val idempotencyKey: String?,
    val reason: String?,
    @ColumnInfo("reason_truncated", defaultValue = "0")
    val reasonTruncated: Boolean,
    @ColumnInfo("error_code")
    val errorCode: String?,
    @ColumnInfo("sanitized_error")
    val sanitizedError: String?,
    @ColumnInfo("duration_ms")
    val durationMs: Long?,
    @ColumnInfo("lease_token")
    val leaseToken: Long,
)

@Entity(
    tableName = "hook_action_cursors",
    indices = [
        Index(
            value = ["hook_id", "hook_config_version", "target_document_id", "source_kind", "source_key"],
            unique = true,
        ),
        Index(value = ["target_document_id", "committed_at"]),
        Index(value = ["execution_id"], unique = true),
    ],
)
data class HookActionCursorEntity(
    @PrimaryKey
    @ColumnInfo("idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo("action_type")
    val actionType: String,
    @ColumnInfo("hook_id")
    val hookId: String,
    @ColumnInfo("hook_config_version")
    val hookConfigVersion: Long,
    @ColumnInfo("target_document_id")
    val targetDocumentId: String,
    @ColumnInfo("source_kind")
    val sourceKind: String,
    @ColumnInfo("source_key")
    val sourceKey: String,
    @ColumnInfo("logical_turn_id")
    val logicalTurnId: String?,
    @ColumnInfo("cutoff_message_id")
    val cutoffMessageId: String,
    @ColumnInfo("execution_id")
    val executionId: String,
    @ColumnInfo("result_revision")
    val resultRevision: Int,
    @ColumnInfo("committed_at")
    val committedAt: Long,
)

data class HookRunWithExecutions(
    @Embedded
    val run: HookRunEntity,
    @Relation(
        parentColumn = "run_id",
        entityColumn = "run_id",
    )
    val executions: List<HookExecutionEntity>,
)
