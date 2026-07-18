package me.rerere.rikkahub.service.hooks

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.HookExecutionEntity
import me.rerere.rikkahub.data.db.entity.HookRunEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRunStatus
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableOperationException
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MemoryTableHookSyncCommitterTest {
    private lateinit var database: AppDatabase
    private val json = Json { ignoreUnknownKeys = false }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun commitPersistsPayloadSnapshotCursorExecutionAndRunAtomically() = runBlocking {
        seedMemoryTable()
        val executionId = Uuid.random()
        seedRunningExecution(executionId, "run-success")
        val prepared = prepared(idempotencyKey = "cursor-success", sourceKey = "turn-success")

        committer(now = 50).commit(executionId, LEASE_TOKEN, prepared, updateOutput(baseRevision = 1))

        val document = database.memoryTableDao().getDocument(DOCUMENT_ID)
        val snapshots = database.memoryTableSnapshotDao().getSnapshotsForDocument(DOCUMENT_ID)
        val cursor = database.hookDao().getCursor("cursor-success")
        val execution = database.hookDao().getExecution(executionId.toString())
        val run = database.hookDao().getRun("run-success")
        assertEquals(2, document?.revision)
        assertEquals("""{"facts":[{"id":"a","value":"new"}]}""", document?.payloadJson)
        assertEquals(1, snapshots.size)
        assertEquals(1, snapshots.single().revision)
        assertEquals(OLD_PAYLOAD, snapshots.single().payloadJson)
        assertEquals(2, cursor?.resultRevision)
        assertEquals(HookExecutionStatus.SUCCESS.name, execution?.status)
        assertEquals(1, execution?.baseRevision)
        assertEquals(2, execution?.resultRevision)
        assertEquals(1, execution?.operationCount)
        assertNotNull(execution?.diffSummaryJson)
        assertEquals(HookRunStatus.SUCCESS.name, run?.status)
        assertEquals(0, run?.failureCount)
    }

    @Test
    fun compositeCursorReplayIsSkippedWithoutAnotherWrite() = runBlocking {
        seedMemoryTable()
        val firstExecution = Uuid.random()
        seedRunningExecution(firstExecution, "run-first")
        val firstPrepared = prepared(idempotencyKey = "cursor-first", sourceKey = "same-source")
        committer(now = 50).commit(firstExecution, LEASE_TOKEN, firstPrepared, updateOutput(baseRevision = 1))

        val replayExecution = Uuid.random()
        seedRunningExecution(replayExecution, "run-replay")
        val currentTarget = firstPrepared.target.copy(payloadJson = NEW_PAYLOAD, revision = 2, updatedAt = 50)
        val replayPrepared = firstPrepared.copy(
            audit = firstPrepared.audit.copy(baseRevision = 2, idempotencyKey = "different-idempotency-key"),
            logicalTurnId = Uuid.random(),
            cutoffMessageId = Uuid.random(),
            target = currentTarget,
        )
        committer(now = 60).commit(
            replayExecution,
            LEASE_TOKEN,
            replayPrepared,
            updateOutput(baseRevision = 2),
        )

        val document = database.memoryTableDao().getDocument(DOCUMENT_ID)
        val execution = database.hookDao().getExecution(replayExecution.toString())
        assertEquals(2, document?.revision)
        assertEquals(NEW_PAYLOAD, document?.payloadJson)
        assertEquals(1, database.memoryTableSnapshotDao().countSnapshots(DOCUMENT_ID))
        assertEquals(HookExecutionStatus.SKIPPED.name, execution?.status)
        assertEquals(HookErrorCode.IDEMPOTENT_REPLAY.name, execution?.errorCode)
        assertEquals(2, execution?.resultRevision)
    }

    @Test
    fun schemaChangeAfterPreparationRejectsCommitWithoutWrites() = runBlocking {
        seedMemoryTable()
        val executionId = Uuid.random()
        seedRunningExecution(executionId, "run-schema-change")
        val prepared = prepared(idempotencyKey = "cursor-schema", sourceKey = "schema-change")
        database.memoryTableDao().upsertTemplate(
            MemoryTableTemplateEntity(
                id = TEMPLATE_ID,
                name = "Facts",
                description = "changed",
                schemaJson = CHANGED_SCHEMA,
                scopeType = MemoryTableScopeType.ASSISTANT.name,
                scopeId = ASSISTANT_ID,
                createdAt = 1,
                updatedAt = 20,
            )
        )

        val error = assertThrows(HookOutputException::class.java) {
            runBlocking { committer(now = 50).commit(executionId, LEASE_TOKEN, prepared, updateOutput(1)) }
        }

        assertEquals(HookErrorCode.MEMORY_TABLE_TARGET_CHANGED, error.code)
        assertUncommitted(executionId, "cursor-schema")
    }

    @Test
    fun invalidLaterOperationRollsBackWholeApplication() = runBlocking {
        seedMemoryTable()
        val executionId = Uuid.random()
        seedRunningExecution(executionId, "run-invalid-operation")
        val output = updateOutput(
            baseRevision = 1,
            operations = """
                [
                  {"type":"update","table":"facts","row":{"id":"a","value":"would-change"}},
                  {"type":"delete","table":"facts","row_key_value":"missing"}
                ]
            """.trimIndent(),
        )

        assertThrows(MemoryTableOperationException::class.java) {
            runBlocking {
                committer(now = 50).commit(
                    executionId,
                    LEASE_TOKEN,
                    prepared(idempotencyKey = "cursor-invalid-operation", sourceKey = "invalid-operation"),
                    output,
                )
            }
        }

        assertUncommitted(executionId, "cursor-invalid-operation")
    }

    @Test
    fun competingExpectedRevisionAllowsOnlyFirstCommit() = runBlocking {
        seedMemoryTable()
        val firstExecution = Uuid.random()
        val secondExecution = Uuid.random()
        seedRunningExecution(firstExecution, "run-cas-first")
        seedRunningExecution(secondExecution, "run-cas-second")
        committer(now = 50).commit(
            firstExecution,
            LEASE_TOKEN,
            prepared(idempotencyKey = "cursor-cas-first", sourceKey = "cas-first"),
            updateOutput(1),
        )

        val error = assertThrows(HookOutputException::class.java) {
            runBlocking {
                committer(now = 60).commit(
                    secondExecution,
                    LEASE_TOKEN,
                    prepared(idempotencyKey = "cursor-cas-second", sourceKey = "cas-second"),
                    updateOutput(1),
                )
            }
        }

        assertEquals(HookErrorCode.MEMORY_TABLE_REVISION_CONFLICT, error.code)
        assertEquals(2, database.memoryTableDao().getDocument(DOCUMENT_ID)?.revision)
        assertEquals(NEW_PAYLOAD, database.memoryTableDao().getDocument(DOCUMENT_ID)?.payloadJson)
        assertEquals(1, database.memoryTableSnapshotDao().countSnapshots(DOCUMENT_ID))
        assertEquals(null, database.hookDao().getCursor("cursor-cas-second"))
        assertEquals(
            HookExecutionStatus.RUNNING.name,
            database.hookDao().getExecution(secondExecution.toString())?.status,
        )
    }

    @Test
    fun invalidLeaseCannotWriteAnyMemoryTableState() = runBlocking {
        seedMemoryTable()
        val executionId = Uuid.random()
        seedRunningExecution(executionId, "run-invalid-lease")

        val error = assertThrows(HookOutputException::class.java) {
            runBlocking {
                committer(now = 50).commit(
                    executionId,
                    LEASE_TOKEN + 1,
                    prepared(idempotencyKey = "cursor-invalid", sourceKey = "invalid"),
                    updateOutput(baseRevision = 1),
                )
            }
        }

        assertEquals(HookErrorCode.ACTION_FAILED, error.code)
        assertEquals(1, database.memoryTableDao().getDocument(DOCUMENT_ID)?.revision)
        assertEquals(0, database.memoryTableSnapshotDao().countSnapshots(DOCUMENT_ID))
        assertEquals(null, database.hookDao().getCursor("cursor-invalid"))
    }

    private suspend fun assertUncommitted(executionId: Uuid, cursorKey: String) {
        assertEquals(1, database.memoryTableDao().getDocument(DOCUMENT_ID)?.revision)
        assertEquals(OLD_PAYLOAD, database.memoryTableDao().getDocument(DOCUMENT_ID)?.payloadJson)
        assertEquals(0, database.memoryTableSnapshotDao().countSnapshots(DOCUMENT_ID))
        assertEquals(null, database.hookDao().getCursor(cursorKey))
        assertEquals(HookExecutionStatus.RUNNING.name, database.hookDao().getExecution(executionId.toString())?.status)
    }

    private suspend fun seedMemoryTable() {
        database.memoryTableDao().upsertTemplate(
            MemoryTableTemplateEntity(
                id = TEMPLATE_ID,
                name = "Facts",
                description = "",
                schemaJson = SCHEMA,
                scopeType = MemoryTableScopeType.ASSISTANT.name,
                scopeId = ASSISTANT_ID,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.memoryTableDao().upsertDocument(
            MemoryTableDocumentEntity(
                id = DOCUMENT_ID,
                templateId = TEMPLATE_ID,
                scopeType = MemoryTableScopeType.ASSISTANT.name,
                scopeId = ASSISTANT_ID,
                payloadJson = OLD_PAYLOAD,
                revision = 1,
                createdAt = 1,
                updatedAt = 1,
                sourceDocumentId = null,
                followSource = false,
                deletedAt = null,
                deletedBy = null,
            )
        )
    }

    private suspend fun seedRunningExecution(executionId: Uuid, runId: String) {
        database.conversationDao().insert(
            ConversationEntity(
                id = runId,
                assistantId = ASSISTANT_ID,
                title = runId,
                nodes = "[]",
                createAt = 1,
                updateAt = 1,
                chatSuggestions = "[]",
                isPinned = false,
            )
        )
        database.hookDao().insertRunIgnore(
            HookRunEntity(
                runId = runId,
                conversationId = runId,
                assistantId = ASSISTANT_ID,
                logicalTurnId = "turn-$runId",
                nodeId = null,
                messageId = null,
                messageModelId = null,
                invocationKind = "HOOK_TEST",
                trigger = "AFTER_ASSISTANT_RESPONSE_SUCCESS",
                configVersion = 1,
                configHash = "hash",
                startedAt = 10,
                endedAt = null,
                status = HookRunStatus.RUNNING.name,
                failureCount = 0,
            )
        )
        database.hookDao().insertExecutions(
            listOf(
                HookExecutionEntity(
                    executionId = executionId.toString(),
                    runId = runId,
                    hookId = HOOK_ID.toString(),
                    hookOrder = 0,
                    hookConfigVersion = 1,
                    hookConfigHash = "hash",
                    modelId = MODEL_ID.toString(),
                    actionType = HookActionType.SYNC_MEMORY_TABLE.name,
                    executionMode = HookExecutionMode.AUTO.name,
                    startedAt = 10,
                    endedAt = null,
                    status = HookExecutionStatus.RUNNING.name,
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
                    errorCode = null,
                    sanitizedError = null,
                    durationMs = null,
                    leaseToken = LEASE_TOKEN,
                )
            )
        )
    }

    private fun prepared(idempotencyKey: String, sourceKey: String): PreparedHookAction.SyncMemoryTable {
        val target = MemoryTableDocument(
            id = DOCUMENT_ID,
            templateId = TEMPLATE_ID,
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = ASSISTANT_ID,
            payloadJson = OLD_PAYLOAD,
            revision = 1,
            createdAt = 1,
            updatedAt = 1,
        )
        val config = HookActionConfig.SyncMemoryTable(
            targetDocumentId = DOCUMENT_ID,
            targetScopeType = MemoryTableScopeType.ASSISTANT,
            maxOperations = 3,
            automatic = true,
        )
        return PreparedHookAction.SyncMemoryTable(
            request = FrozenHookModelRequest.SyncMemoryTable(
                modelId = MODEL_ID,
                prompt = "sync",
                messages = emptyList(),
                targetDocumentId = DOCUMENT_ID,
                baseRevision = 1,
                schemaJson = SCHEMA,
                payloadJson = OLD_PAYLOAD,
                maxOperations = 3,
            ),
            audit = HookPreparedAudit(
                targetDocumentId = DOCUMENT_ID,
                targetTemplateId = TEMPLATE_ID,
                targetScopeType = MemoryTableScopeType.ASSISTANT,
                targetScopeId = ASSISTANT_ID,
                baseRevision = 1,
                idempotencyKey = idempotencyKey,
                retryOfExecutionId = null,
            ),
            hookId = HOOK_ID,
            hookConfigVersion = 1,
            hookConfigHash = "hash",
            assistantId = Uuid.parse(ASSISTANT_ID),
            conversationId = Uuid.random(),
            logicalTurnId = Uuid.random(),
            cutoffMessageId = Uuid.random(),
            sourceKind = HookExecutionMode.AUTO.name,
            sourceKey = sourceKey,
            target = target,
            schemaJson = SCHEMA,
            config = config,
        )
    }

    private fun updateOutput(
        baseRevision: Int,
        operations: String = """[{"type":"update","table":"facts","row":{"id":"a","value":"new"}}]""",
    ) = ParsedMemoryTableSyncHookOutput(
        decision = HookDecision.APPLY,
        baseRevision = baseRevision,
        operations = json.parseToJsonElement(operations) as JsonArray,
        reason = "update",
        reasonTruncated = false,
    )

    private fun committer(now: Long) = MemoryTableHookSyncCommitter(
        database = database,
        memoryTableDao = database.memoryTableDao(),
        snapshotDao = database.memoryTableSnapshotDao(),
        hookDao = database.hookDao(),
        json = json,
        now = { now },
    )

    companion object {
        private const val ASSISTANT_ID = "00000000-0000-0000-0000-000000000101"
        private val HOOK_ID = Uuid.parse("00000000-0000-0000-0000-000000000102")
        private val MODEL_ID = Uuid.parse("00000000-0000-0000-0000-000000000103")
        private const val TEMPLATE_ID = "template"
        private const val DOCUMENT_ID = "document"
        private const val LEASE_TOKEN = 99L
        private const val OLD_PAYLOAD = """{"facts":[{"id":"a","value":"old"}]}"""
        private const val NEW_PAYLOAD = """{"facts":[{"id":"a","value":"new"}]}"""
        private val SCHEMA = """
            {
              "tables": [{
                "name": "facts",
                "columns": [
                  {"name":"id","type":"string","primaryKey":true},
                  {"name":"value","type":"string"}
                ],
                "updatePolicy": {"enabled":true}
              }]
            }
        """.trimIndent()
        private val CHANGED_SCHEMA = """
            {
              "tables": [{
                "name": "facts",
                "columns": [
                  {"name":"id","type":"string","primaryKey":true},
                  {"name":"value","type":"string"},
                  {"name":"source","type":"string"}
                ],
                "updatePolicy": {"enabled":true}
              }]
            }
        """.trimIndent()
    }
}
