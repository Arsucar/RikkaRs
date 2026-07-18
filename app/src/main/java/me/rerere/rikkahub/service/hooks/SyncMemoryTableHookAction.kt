package me.rerere.rikkahub.service.hooks

import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.HookDAO
import me.rerere.rikkahub.data.db.dao.MemoryTableDAO
import me.rerere.rikkahub.data.db.dao.MemoryTableSnapshotDAO
import me.rerere.rikkahub.data.db.entity.HookActionCursorEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableSnapshotEntity
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRuntimeRules
import me.rerere.rikkahub.data.model.MemoryTableOperationException
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.applyValidatedMemoryTableOperations
import me.rerere.rikkahub.data.model.configurationHash
import me.rerere.rikkahub.data.repository.MemoryTableRepository
import java.security.MessageDigest
import kotlin.uuid.Uuid

class SyncMemoryTableHookAction(
    private val settingsStore: SettingsStore,
    private val memoryTableRepository: MemoryTableRepository,
    private val hookDao: HookDAO,
    private val committer: MemoryTableHookSyncCommitter,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) : HookActionHandler {
    override val actionType: HookActionType = HookActionType.SYNC_MEMORY_TABLE

    override suspend fun prepare(
        hook: ConversationHook,
        context: HookFreezeContext,
    ): HookActionPreparation {
        val config = hook.actionConfig as? HookActionConfig.SyncMemoryTable
            ?: return HookActionPreparation.Cancelled(HookErrorCode.ACTION_FAILED)
        val settings = settingsStore.settingsFlow.value
        val currentAssistant = settings.assistants.firstOrNull { it.id == context.assistantId }
            ?: return HookActionPreparation.Skipped(HookErrorCode.HOOK_DISABLED)
        val currentHook = currentAssistant.hooks.firstOrNull { it.id == hook.id }
        if (currentHook == null || !currentHook.enabled || currentHook.configVersion != hook.configVersion ||
            currentHook.configurationHash() != hook.configurationHash()
        ) {
            return HookActionPreparation.Skipped(HookErrorCode.HOOK_DISABLED)
        }
        if (!settings.enableMemoryTable || !currentAssistant.enableMemoryTable) {
            return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_DISABLED)
        }
        if (context.executionMode == HookExecutionMode.AUTO) {
            if (!settings.memoryTableAutoSyncEnabled) {
                return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_AUTO_SYNC_DISABLED)
            }
            if (!config.automatic) {
                return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_AUTOMATIC_DISABLED)
            }
        }
        validateSyncConfig(config)?.let { return HookActionPreparation.Skipped(it) }
        val conversationId = context.conversation.id.toString()
        val actorConversationId = conversationId.takeIf {
            config.targetScopeType == MemoryTableScopeType.CONVERSATION
        }
        val target = memoryTableRepository.getEffectiveDocument(
            id = config.targetDocumentId,
            assistantId = context.assistantId.toString(),
            conversationId = actorConversationId,
        ) ?: run {
            val includingDeleted = memoryTableRepository.getDocumentIncludingDeletedForActor(
                id = config.targetDocumentId,
                assistantId = context.assistantId.toString(),
                conversationId = actorConversationId,
            )
            return HookActionPreparation.Skipped(
                if (includingDeleted?.deletedAt != null) {
                    HookErrorCode.MEMORY_TABLE_TARGET_DELETED
                } else {
                    HookErrorCode.MEMORY_TABLE_TARGET_NOT_FOUND
                }
            )
        }
        if (target.scopeType != config.targetScopeType || target.scopeType == MemoryTableScopeType.GLOBAL) {
            return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_SCOPE_FORBIDDEN)
        }
        if (target.scopeType == MemoryTableScopeType.ASSISTANT && target.scopeId != context.assistantId.toString()) {
            return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_SCOPE_FORBIDDEN)
        }
        if (target.scopeType == MemoryTableScopeType.CONVERSATION && target.scopeId != conversationId) {
            return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_SCOPE_FORBIDDEN)
        }
        val template = memoryTableRepository.getEffectiveTemplates(context.assistantId.toString())
            .firstOrNull { it.id == target.templateId }
            ?: return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_TARGET_CHANGED)
        if (config.minimumIntervalSeconds > 0 && context.executionMode == HookExecutionMode.AUTO) {
            val lastCommitted = hookDao.getLatestCursorCommittedAt(hook.id.toString(), target.id)
            if (lastCommitted != null && now() - lastCommitted < config.minimumIntervalSeconds * 1_000L) {
                return HookActionPreparation.Skipped(HookErrorCode.MEMORY_TABLE_FREQUENCY_LIMIT)
            }
        }
        val messages = freezeMemoryTableHookMessages(
            conversation = context.conversation,
            cutoffMessageId = context.sourceMessageId,
            config = config,
        )
        if (messages.isEmpty()) return HookActionPreparation.Skipped(HookErrorCode.SCHEMA_MISMATCH)
        val idempotencyKey = memoryTableHookIdempotencyKey(
            hookId = hook.id,
            configVersion = hook.configVersion,
            targetDocumentId = target.id,
            sourceKind = context.executionMode.name,
            sourceKey = context.sourceKey,
            cutoffMessageId = context.sourceMessageId,
        )
        val audit = HookPreparedAudit(
            targetDocumentId = target.id,
            targetTemplateId = target.templateId,
            targetScopeType = target.scopeType,
            targetScopeId = target.scopeId,
            baseRevision = target.revision,
            idempotencyKey = idempotencyKey,
            retryOfExecutionId = context.retryOfExecutionId,
        )
        return HookActionPreparation.Ready(
            PreparedHookAction.SyncMemoryTable(
                request = FrozenHookModelRequest.SyncMemoryTable(
                    modelId = hook.modelId,
                    prompt = hook.prompt,
                    messages = messages,
                    targetDocumentId = target.id,
                    baseRevision = target.revision,
                    schemaJson = template.schemaJson,
                    payloadJson = target.payloadJson,
                    maxOperations = config.maxOperations,
                ),
                audit = audit,
                hookId = hook.id,
                hookConfigVersion = hook.configVersion,
                hookConfigHash = hook.configurationHash(),
                assistantId = context.assistantId,
                conversationId = context.conversation.id,
                logicalTurnId = context.logicalTurnId,
                cutoffMessageId = context.sourceMessageId,
                sourceKind = context.executionMode.name,
                sourceKey = context.sourceKey,
                target = target,
                schemaJson = template.schemaJson,
                config = config,
            )
        )
    }

    override fun parse(raw: String, prepared: PreparedHookAction): ParsedHookOutput {
        val sync = prepared as? PreparedHookAction.SyncMemoryTable
            ?: throw HookOutputException(HookErrorCode.ACTION_FAILED)
        return MemoryTableSyncHookOutputParser.parse(raw, sync.config.maxOperations)
    }

    override suspend fun preview(
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): MemoryTableHookPreview? {
        val sync = prepared as? PreparedHookAction.SyncMemoryTable ?: return null
        val parsed = output as? ParsedMemoryTableSyncHookOutput ?: return null
        if (parsed.baseRevision != sync.target.revision) {
            throw HookOutputException(HookErrorCode.MEMORY_TABLE_REVISION_CONFLICT)
        }
        val application = try {
            if (parsed.decision == HookDecision.APPLY) {
                applyValidatedMemoryTableOperations(
                    json = json,
                    schemaJson = sync.schemaJson,
                    payloadJson = sync.target.payloadJson,
                    operations = parsed.operations,
                    maxOperations = sync.config.maxOperations,
                )
            } else {
                emptyApplication(sync.target.payloadJson)
            }
        } catch (_: MemoryTableOperationException) {
            throw HookOutputException(HookErrorCode.MEMORY_TABLE_INVALID_OPERATIONS)
        }
        return MemoryTableHookPreview(
            hookId = sync.hookId,
            hookConfigVersion = sync.hookConfigVersion,
            hookConfigHash = sync.hookConfigHash,
            conversationId = sync.conversationId,
            cutoffMessageId = sync.cutoffMessageId,
            targetDocumentId = sync.target.id,
            targetTemplateId = sync.target.templateId,
            targetScopeType = sync.target.scopeType,
            targetScopeId = sync.target.scopeId,
            baseRevision = sync.target.revision,
            decision = parsed.decision,
            operationCount = application.operationCount,
            operationSummaryJson = application.operationSummaryJson,
            diffSummaryJson = application.diffSummaryJson,
            reason = parsed.reason,
            operations = parsed.operations,
            sourceKey = sync.sourceKey,
            prepared = sync,
        )
    }

    override suspend fun execute(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): HookActionResult {
        val sync = prepared as? PreparedHookAction.SyncMemoryTable
            ?: return HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
        val parsed = output as? ParsedMemoryTableSyncHookOutput
            ?: return HookActionResult.Cancelled(HookErrorCode.SCHEMA_MISMATCH)
        revalidateCurrentGates(sync)?.let { return HookActionResult.Skipped(null, it) }
        if (parsed.baseRevision != sync.target.revision) {
            throw HookOutputException(HookErrorCode.MEMORY_TABLE_REVISION_CONFLICT)
        }
        try {
            committer.commit(
                executionId = executionId,
                leaseToken = leaseToken,
                prepared = sync,
                output = parsed,
            )
        } catch (error: MemoryTableOperationException) {
            throw HookOutputException(HookErrorCode.MEMORY_TABLE_INVALID_OPERATIONS)
        }
        return HookActionResult.Terminalized
    }

    private fun revalidateCurrentGates(sync: PreparedHookAction.SyncMemoryTable): HookErrorCode? {
        val settings = settingsStore.settingsFlow.value
        val currentAssistant = settings.assistants.firstOrNull { it.id == sync.assistantId }
            ?: return HookErrorCode.HOOK_DISABLED
        val currentHook = currentAssistant.hooks.firstOrNull { it.id == sync.hookId }
            ?: return HookErrorCode.HOOK_DISABLED
        if (!currentHook.enabled || currentHook.configVersion != sync.hookConfigVersion ||
            currentHook.configurationHash() != sync.hookConfigHash
        ) {
            return HookErrorCode.HOOK_DISABLED
        }
        if (!settings.enableMemoryTable || !currentAssistant.enableMemoryTable) {
            return HookErrorCode.MEMORY_TABLE_DISABLED
        }
        if (sync.sourceKind == HookExecutionMode.AUTO.name) {
            if (!settings.memoryTableAutoSyncEnabled) return HookErrorCode.MEMORY_TABLE_AUTO_SYNC_DISABLED
            if (!sync.config.automatic) return HookErrorCode.MEMORY_TABLE_AUTOMATIC_DISABLED
        }
        return null
    }

    private fun validateSyncConfig(config: HookActionConfig.SyncMemoryTable): HookErrorCode? = when {
        config.targetDocumentId.isBlank() -> HookErrorCode.MEMORY_TABLE_TARGET_NOT_FOUND
        config.targetScopeType == MemoryTableScopeType.GLOBAL -> HookErrorCode.MEMORY_TABLE_SCOPE_FORBIDDEN
        !config.includeUserMessages && !config.includeAssistantMessages -> HookErrorCode.SCHEMA_MISMATCH
        config.recentMessageCount !in
            HookRuntimeRules.MIN_SYNC_MESSAGE_COUNT..HookRuntimeRules.MAX_SYNC_MESSAGE_COUNT ->
            HookErrorCode.SCHEMA_MISMATCH
        config.maxContextChars !in HookRuntimeRules.MIN_SYNC_CONTEXT_CHARS..HookRuntimeRules.MAX_SYNC_CONTEXT_CHARS ->
            HookErrorCode.SCHEMA_MISMATCH
        config.maxOperations !in HookRuntimeRules.MIN_SYNC_MAX_OPERATIONS..HookRuntimeRules.MAX_SYNC_MAX_OPERATIONS ->
            HookErrorCode.SCHEMA_MISMATCH
        config.minimumIntervalSeconds < 0 -> HookErrorCode.SCHEMA_MISMATCH
        else -> null
    }
}

class MemoryTableHookSyncCommitter(
    private val database: AppDatabase,
    private val memoryTableDao: MemoryTableDAO,
    private val snapshotDao: MemoryTableSnapshotDAO,
    private val hookDao: HookDAO,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun commit(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction.SyncMemoryTable,
        output: ParsedMemoryTableSyncHookOutput,
    ) {
        try {
            database.withTransaction {
                if (!hookDao.isLeaseActive(executionId.toString(), leaseToken)) {
                    throw HookOutputException(HookErrorCode.ACTION_FAILED)
                }
                val existingCursor = findCommittedCursor(prepared, executionId)
                if (existingCursor != null) {
                    finishSyncExecution(
                        executionId = executionId,
                        leaseToken = leaseToken,
                        prepared = prepared,
                        output = output,
                        status = HookExecutionStatus.SKIPPED,
                        errorCode = HookErrorCode.IDEMPOTENT_REPLAY,
                        resultRevision = existingCursor.resultRevision,
                        application = emptyApplication(prepared.target.payloadJson),
                    )
                    return@withTransaction
                }
                val current = memoryTableDao.getDocumentIncludingDeleted(prepared.target.id)
                    ?: throw HookOutputException(HookErrorCode.MEMORY_TABLE_TARGET_NOT_FOUND)
                if (current.deletedAt != null) {
                    throw HookOutputException(HookErrorCode.MEMORY_TABLE_TARGET_DELETED)
                }
                if (current.templateId != prepared.target.templateId ||
                    current.scopeType != prepared.target.scopeType.name ||
                    current.scopeId != prepared.target.scopeId
                ) {
                    throw HookOutputException(HookErrorCode.MEMORY_TABLE_TARGET_CHANGED)
                }
                if (current.scopeType == MemoryTableScopeType.GLOBAL.name ||
                    (current.scopeType == MemoryTableScopeType.ASSISTANT.name &&
                        current.scopeId != prepared.assistantId.toString()) ||
                    (current.scopeType == MemoryTableScopeType.CONVERSATION.name &&
                        current.scopeId != prepared.conversationId.toString())
                ) {
                    throw HookOutputException(HookErrorCode.MEMORY_TABLE_SCOPE_FORBIDDEN)
                }
                if (current.revision != output.baseRevision) {
                    throw HookOutputException(HookErrorCode.MEMORY_TABLE_REVISION_CONFLICT)
                }
                val template = memoryTableDao.getTemplate(current.templateId)
                    ?: throw HookOutputException(HookErrorCode.MEMORY_TABLE_TARGET_CHANGED)
                if (template.schemaJson != prepared.schemaJson) {
                    throw HookOutputException(HookErrorCode.MEMORY_TABLE_TARGET_CHANGED)
                }
                val application = if (output.decision == HookDecision.APPLY) {
                    applyValidatedMemoryTableOperations(
                        json = json,
                        schemaJson = template.schemaJson,
                        payloadJson = current.payloadJson,
                        operations = output.operations,
                        maxOperations = prepared.config.maxOperations,
                    )
                } else {
                    emptyApplication(current.payloadJson)
                }
                if (output.decision == HookDecision.SKIP) {
                    finishSyncExecution(
                        executionId = executionId,
                        leaseToken = leaseToken,
                        prepared = prepared,
                        output = output,
                        status = HookExecutionStatus.SKIPPED,
                        errorCode = null,
                        resultRevision = current.revision,
                        application = application,
                    )
                    return@withTransaction
                }
                val committedAt = now()
                snapshotDao.upsertSnapshot(
                    MemoryTableSnapshotEntity(
                        id = "memory-table-snapshot:${current.id}:${current.revision}",
                        documentId = current.id,
                        revision = current.revision,
                        payloadJson = current.payloadJson,
                        createdAt = committedAt,
                    )
                )
                val updated = memoryTableDao.updateDocumentPayloadCas(
                    id = current.id,
                    expectedRevision = current.revision,
                    expectedTemplateId = current.templateId,
                    expectedScopeType = current.scopeType,
                    expectedScopeId = current.scopeId,
                    assistantId = prepared.assistantId.toString(),
                    conversationId = prepared.conversationId.toString(),
                    payloadJson = application.payloadJson,
                    updatedAt = committedAt,
                )
                if (updated != 1) {
                    throw HookOutputException(HookErrorCode.MEMORY_TABLE_REVISION_CONFLICT)
                }
                snapshotDao.pruneSnapshots(current.id, 20)
                val resultRevision = current.revision + 1
                if (hookDao.insertCursorIgnore(
                        HookActionCursorEntity(
                            idempotencyKey = prepared.audit.idempotencyKey,
                            actionType = HookActionType.SYNC_MEMORY_TABLE.name,
                            hookId = prepared.hookId.toString(),
                            hookConfigVersion = prepared.hookConfigVersion,
                            targetDocumentId = current.id,
                            sourceKind = prepared.sourceKind,
                            sourceKey = prepared.sourceKey,
                            logicalTurnId = prepared.logicalTurnId.toString(),
                            cutoffMessageId = prepared.cutoffMessageId.toString(),
                            executionId = executionId.toString(),
                            resultRevision = resultRevision,
                            committedAt = committedAt,
                        )
                    ) == -1L
                ) {
                    throw HookOutputException(HookErrorCode.IDEMPOTENT_REPLAY)
                }
                finishSyncExecution(
                    executionId = executionId,
                    leaseToken = leaseToken,
                    prepared = prepared,
                    output = output,
                    status = HookExecutionStatus.SUCCESS,
                    errorCode = null,
                    resultRevision = resultRevision,
                    application = application,
                )
            }
        } catch (error: HookOutputException) {
            if (error.code != HookErrorCode.IDEMPOTENT_REPLAY) throw error
            database.withTransaction {
                val existingCursor = findCommittedCursor(prepared, executionId) ?: throw error
                finishSyncExecution(
                    executionId = executionId,
                    leaseToken = leaseToken,
                    prepared = prepared,
                    output = output,
                    status = HookExecutionStatus.SKIPPED,
                    errorCode = HookErrorCode.IDEMPOTENT_REPLAY,
                    resultRevision = existingCursor.resultRevision,
                    application = emptyApplication(prepared.target.payloadJson),
                )
            }
        }
    }

    private suspend fun findCommittedCursor(
        prepared: PreparedHookAction.SyncMemoryTable,
        executionId: Uuid,
    ): HookActionCursorEntity? = hookDao.getCursor(prepared.audit.idempotencyKey)
        ?: hookDao.getCursorForSource(
            hookId = prepared.hookId.toString(),
            hookConfigVersion = prepared.hookConfigVersion,
            targetDocumentId = prepared.target.id,
            sourceKind = prepared.sourceKind,
            sourceKey = prepared.sourceKey,
        )
        ?: hookDao.getCursorForExecution(executionId.toString())

    private suspend fun finishSyncExecution(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction.SyncMemoryTable,
        output: ParsedMemoryTableSyncHookOutput,
        status: HookExecutionStatus,
        errorCode: HookErrorCode?,
        resultRevision: Int,
        application: me.rerere.rikkahub.data.model.MemoryTableOperationApplication,
    ) {
        val endedAt = now()
        val updated = hookDao.finishExecutionRaw(
            executionId = executionId.toString(),
            leaseToken = leaseToken,
            status = status.name,
            decision = output.decision.name,
            tagId = null,
            targetDocumentId = prepared.target.id,
            targetTemplateId = prepared.target.templateId,
            targetScopeType = prepared.target.scopeType.name,
            targetScopeId = prepared.target.scopeId,
            baseRevision = output.baseRevision,
            resultRevision = resultRevision,
            operationCount = application.operationCount,
            operationSummaryJson = boundedAudit(application.operationSummaryJson),
            diffSummaryJson = boundedAudit(application.diffSummaryJson),
            retryOfExecutionId = prepared.audit.retryOfExecutionId?.toString(),
            idempotencyKey = prepared.audit.idempotencyKey,
            reason = output.reason,
            reasonTruncated = output.reasonTruncated,
            errorCode = errorCode?.name,
            sanitizedError = null,
            endedAt = endedAt,
        )
        if (updated != 1) throw HookOutputException(HookErrorCode.ACTION_FAILED)
        val execution = hookDao.getExecution(executionId.toString())
            ?: throw HookOutputException(HookErrorCode.ACTION_FAILED)
        hookDao.recalculateRun(execution.runId, endedAt)
    }

    private fun boundedAudit(value: String): String =
        value.take(HookRuntimeRules.MAX_SYNC_AUDIT_JSON_CHARS)
}

private fun emptyApplication(payloadJson: String) = me.rerere.rikkahub.data.model.MemoryTableOperationApplication(
    payloadJson = payloadJson,
    operationCount = 0,
    operationSummaryJson = Json.encodeToString(JsonObject.serializer(), JsonObject(emptyMap())),
    diffSummaryJson = Json.encodeToString(JsonArray.serializer(), JsonArray(emptyList())),
)

private fun memoryTableHookIdempotencyKey(
    hookId: Uuid,
    configVersion: Long,
    targetDocumentId: String,
    sourceKind: String,
    sourceKey: String,
    cutoffMessageId: Uuid,
): String {
    val material = listOf(
        hookId.toString(),
        configVersion.toString(),
        targetDocumentId,
        sourceKind,
        sourceKey,
        cutoffMessageId.toString(),
    ).joinToString("|") { value -> "${value.length}:$value" }
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
