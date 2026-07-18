package me.rerere.rikkahub.service.hooks

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.ConversationTagCrossRef
import me.rerere.rikkahub.data.db.entity.ConversationTagEntity
import me.rerere.rikkahub.data.db.entity.HookExecutionEntity
import me.rerere.rikkahub.data.db.entity.HookRunEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRunStatus
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ConversationTagHookCommitterTest {
    private lateinit var database: AppDatabase
    private lateinit var tagRepository: ConversationTagRepository
    private lateinit var sourceMessage: UIMessage

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        tagRepository = ConversationTagRepository(database.conversationTagDao())
        sourceMessage = UIMessage.assistant("Created GitHub Issue #148")
        database.conversationDao().insert(conversation())
        database.messageNodeDao().insert(
            MessageNodeEntity(
                id = SOURCE_NODE_ID.toString(),
                conversationId = CONVERSATION_ID.toString(),
                nodeIndex = 0,
                messages = JsonInstant.encodeToString(listOf(sourceMessage)),
                selectIndex = 0,
                hidden = false,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transitionSwapsTagsAtTwentyTagLimitAndWritesAtomicAudit() = runBlocking {
        val removeTagId = seedTag("in-progress", related = true)
        repeat(19) { seedTag("other-$it", related = true) }
        val addTagId = seedTag("completed", related = false)
        val executionId = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-swap")

        committer().commitTransition(
            executionId,
            LEASE_TOKEN,
            transitionPrepared(addTagId, removeTagId),
            transitionOutput(),
        )

        val execution = database.hookDao().getExecution(executionId.toString())
        assertEquals(20, database.conversationTagDao().countTagsForConversation(CONVERSATION_ID.toString()))
        assertFalse(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), removeTagId.toString()))
        assertTrue(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), addTagId.toString()))
        assertEquals(HookExecutionStatus.SUCCESS.name, execution?.status)
        assertEquals(HookDecision.APPLY.name, execution?.decision)
        assertEquals(2, execution?.operationCount)
        val summary = JsonInstant.decodeFromString<ConversationTagTransitionAuditSummary>(
            execution?.operationSummaryJson.orEmpty()
        )
        val operations = JsonInstant.decodeFromString<List<ConversationTagTransitionAuditOperation>>(
            execution?.diffSummaryJson.orEmpty()
        )
        assertEquals(true, summary.added)
        assertEquals(true, summary.removed)
        assertEquals(listOf("remove", "add"), operations.map { it.type })
        assertEquals(listOf(true, true), operations.map { it.changed })
    }

    @Test
    fun alreadyCompletedAndAlreadyRemovedIsIdempotentSkip() = runBlocking {
        val addTagId = seedTag("completed", related = true)
        val removeTagId = seedTag("in-progress", related = false)
        val executionId = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-noop")

        committer().commitTransition(
            executionId,
            LEASE_TOKEN,
            transitionPrepared(addTagId, removeTagId),
            transitionOutput(),
        )

        val execution = database.hookDao().getExecution(executionId.toString())
        assertEquals(HookExecutionStatus.SKIPPED.name, execution?.status)
        assertEquals(HookDecision.APPLY.name, execution?.decision)
        assertEquals(0, execution?.operationCount)
        assertEquals(HookRunStatus.SKIPPED.name, database.hookDao().getRun("run-noop")?.status)
    }

    @Test
    fun fullConversationWithoutRemoveRelationFailsWithoutChangingOtherTags() = runBlocking {
        repeat(20) { seedTag("full-$it", related = true) }
        val removeTagId = seedTag("in-progress", related = false)
        val addTagId = seedTag("completed", related = false)
        val executionId = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-limit")

        committer().commitTransition(
            executionId,
            LEASE_TOKEN,
            transitionPrepared(addTagId, removeTagId),
            transitionOutput(),
        )

        val execution = database.hookDao().getExecution(executionId.toString())
        assertEquals(20, database.conversationTagDao().countTagsForConversation(CONVERSATION_ID.toString()))
        assertFalse(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), addTagId.toString()))
        assertEquals(HookExecutionStatus.FAILED.name, execution?.status)
        assertEquals(HookErrorCode.TAG_LIMIT_REACHED.name, execution?.errorCode)
        assertEquals(null, execution?.decision)
    }

    @Test
    fun terminalizationFailureRollsBackBothRelationshipChanges() = runBlocking {
        val removeTagId = seedTag("in-progress", related = true)
        val addTagId = seedTag("completed", related = false)
        val executionId = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-terminal-fail")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                committer(now = { error("injected terminal failure") }).commitTransition(
                    executionId,
                    LEASE_TOKEN,
                    transitionPrepared(addTagId, removeTagId),
                    transitionOutput(),
                )
            }
        }

        assertTrue(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), removeTagId.toString()))
        assertFalse(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), addTagId.toString()))
        assertEquals(
            HookExecutionStatus.RUNNING.name,
            database.hookDao().getExecution(executionId.toString())?.status,
        )
    }

    @Test
    fun transitionRecordsEachOneChangeState() = runBlocking {
        val addAlreadyPresent = seedTag("completed-present", related = true)
        val removePresent = seedTag("in-progress-present", related = true)
        val firstExecution = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-one-remove")
        committer().commitTransition(
            firstExecution,
            LEASE_TOKEN,
            transitionPrepared(addAlreadyPresent, removePresent),
            transitionOutput(),
        )

        val addAbsent = seedTag("completed-absent", related = false)
        val removeAlreadyAbsent = seedTag("in-progress-absent", related = false)
        val secondExecution = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-one-add")
        committer().commitTransition(
            secondExecution,
            LEASE_TOKEN,
            transitionPrepared(addAbsent, removeAlreadyAbsent),
            transitionOutput(),
        )

        assertEquals(1, database.hookDao().getExecution(firstExecution.toString())?.operationCount)
        assertEquals(1, database.hookDao().getExecution(secondExecution.toString())?.operationCount)
    }

    @Test
    fun missingTagAndInactiveSourceProduceStableTerminalRowsWithoutWrites() = runBlocking {
        val removeTagId = seedTag("in-progress", related = true)
        val missingAddTagId = Uuid.random()
        val missingExecution = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-missing")
        committer().commitTransition(
            missingExecution,
            LEASE_TOKEN,
            transitionPrepared(missingAddTagId, removeTagId),
            transitionOutput(),
        )
        assertTrue(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), removeTagId.toString()))
        assertEquals(
            HookErrorCode.TAG_NOT_FOUND.name,
            database.hookDao().getExecution(missingExecution.toString())?.errorCode,
        )

        val addTagId = seedTag("completed", related = false)
        val node = database.messageNodeDao().getNode(CONVERSATION_ID.toString(), SOURCE_NODE_ID.toString())!!
        database.messageNodeDao().update(node.copy(hidden = true))
        val inactiveExecution = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-inactive")
        committer().commitTransition(
            inactiveExecution,
            LEASE_TOKEN,
            transitionPrepared(addTagId, removeTagId),
            transitionOutput(),
        )
        val inactive = database.hookDao().getExecution(inactiveExecution.toString())
        assertEquals(HookExecutionStatus.CANCELLED.name, inactive?.status)
        assertEquals(HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE.name, inactive?.errorCode)
        assertFalse(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), addTagId.toString()))
    }

    @Test
    fun lostLeasePreservesTimeoutOwnerAndMakesNoTagChanges() = runBlocking {
        val removeTagId = seedTag("in-progress", related = true)
        val addTagId = seedTag("completed", related = false)
        val executionId = seedRunningExecution(HookActionType.TRANSITION_CONVERSATION_TAGS, "run-timeout")
        assertTrue(
            database.hookDao().invalidateLeaseAndFailTimeout(
                executionId = executionId.toString(),
                leaseToken = LEASE_TOKEN,
                errorCode = HookErrorCode.HOOK_TIMEOUT.name,
                sanitizedError = null,
                endedAt = 40,
            )
        )

        committer().commitTransition(
            executionId,
            LEASE_TOKEN,
            transitionPrepared(addTagId, removeTagId),
            transitionOutput(),
        )

        val execution = database.hookDao().getExecution(executionId.toString())
        assertEquals(HookExecutionStatus.FAILED.name, execution?.status)
        assertEquals(HookErrorCode.HOOK_TIMEOUT.name, execution?.errorCode)
        assertTrue(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), removeTagId.toString()))
        assertFalse(database.conversationTagDao().relationExists(CONVERSATION_ID.toString(), addTagId.toString()))
    }

    @Test
    fun legacyAddTagKeepsSuccessAndDuplicateExecutionRows() = runBlocking {
        val tagId = seedTag("completed", related = false)
        val firstExecution = seedRunningExecution(HookActionType.ADD_CONVERSATION_TAG, "run-add-success")
        val prepared = addPrepared(tagId)
        val output = ParsedAddTagHookOutput(HookDecision.APPLY, tagId, "done", false)

        committer().commitAdd(firstExecution, LEASE_TOKEN, prepared, output, tagId)

        val first = database.hookDao().getExecution(firstExecution.toString())
        assertEquals(HookExecutionStatus.SUCCESS.name, first?.status)
        assertEquals(HookDecision.APPLY.name, first?.decision)
        assertEquals(tagId.toString(), first?.tagId)
        assertEquals("done", first?.reason)
        assertEquals(null, first?.operationCount)

        val duplicateExecution = seedRunningExecution(HookActionType.ADD_CONVERSATION_TAG, "run-add-noop")
        committer().commitAdd(duplicateExecution, LEASE_TOKEN, prepared, output, tagId)
        val duplicate = database.hookDao().getExecution(duplicateExecution.toString())
        assertEquals(HookExecutionStatus.SKIPPED.name, duplicate?.status)
        assertEquals(HookDecision.APPLY.name, duplicate?.decision)
        assertEquals(tagId.toString(), duplicate?.tagId)
        assertEquals(null, duplicate?.operationCount)
    }

    private suspend fun seedTag(name: String, related: Boolean): Uuid {
        val id = Uuid.random()
        database.conversationTagDao().insertTagIgnore(
            ConversationTagEntity(
                id = id.toString(),
                normalizedName = name,
                displayName = name,
                colorKey = "blue",
                createdAt = 1,
                updatedAt = 1,
            )
        )
        if (related) {
            database.conversationTagDao().insertRelationIgnore(
                ConversationTagCrossRef(CONVERSATION_ID.toString(), id.toString())
            )
        }
        return id
    }

    private suspend fun seedRunningExecution(actionType: HookActionType, runId: String): Uuid {
        val executionId = Uuid.random()
        database.hookDao().insertRunIgnore(
            HookRunEntity(
                runId = runId,
                conversationId = CONVERSATION_ID.toString(),
                assistantId = ASSISTANT_ID.toString(),
                logicalTurnId = "turn-$runId",
                nodeId = SOURCE_NODE_ID.toString(),
                messageId = sourceMessage.id.toString(),
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
                    actionType = actionType.name,
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
        return executionId
    }

    private fun transitionPrepared(
        addTagId: Uuid,
        removeTagId: Uuid,
    ): PreparedHookAction.TransitionConversationTags {
        val evidence = GitHubIssueEvidence(GitHubIssueEvidenceType.ISSUE_NUMBER, 148)
        val config = HookActionConfig.TransitionConversationTags(addTagId, removeTagId)
        return PreparedHookAction.TransitionConversationTags(
            request = FrozenHookModelRequest.TransitionConversationTags(
                modelId = MODEL_ID,
                prompt = "transition",
                messageTextSnapshot = sourceMessage.toText(),
                evidence = evidence,
                addTagId = addTagId,
                addTagName = "completed",
                removeTagId = removeTagId,
                removeTagName = "in-progress",
            ),
            audit = transitionPreparedAudit(config, evidence),
            hookId = HOOK_ID,
            hookConfigVersion = 1,
            hookConfigHash = "hash",
            assistantId = ASSISTANT_ID,
            conversationId = CONVERSATION_ID,
            sourceNodeId = SOURCE_NODE_ID,
            sourceMessageId = sourceMessage.id,
            addTagId = addTagId,
            removeTagId = removeTagId,
            evidence = evidence,
        )
    }

    private fun addPrepared(tagId: Uuid) = PreparedHookAction.AddConversationTag(
        request = FrozenHookModelRequest.AddConversationTag(
            modelId = MODEL_ID,
            prompt = "add",
            messageTextSnapshot = sourceMessage.toText(),
            allowedTags = mapOf(tagId to "completed"),
        ),
        conversationId = CONVERSATION_ID,
        sourceNodeId = SOURCE_NODE_ID,
        sourceMessageId = sourceMessage.id,
        allowedTagIds = setOf(tagId),
    )

    private fun transitionOutput() = ParsedTransitionConversationTagsHookOutput(
        decision = HookDecision.APPLY,
        reason = "verified",
        reasonTruncated = false,
    )

    private fun committer(now: () -> Long = { 50L }) = ConversationTagHookCommitter(
        database = database,
        hookDao = database.hookDao(),
        messageNodeDao = database.messageNodeDao(),
        tagDao = database.conversationTagDao(),
        tagRepository = tagRepository,
        now = now,
    )

    private fun conversation() = ConversationEntity(
        id = CONVERSATION_ID.toString(),
        assistantId = ASSISTANT_ID.toString(),
        title = "conversation",
        nodes = "[]",
        createAt = 1,
        updateAt = 1,
        chatSuggestions = "[]",
        isPinned = false,
    )

    companion object {
        private val CONVERSATION_ID = Uuid.parse("00000000-0000-0000-0000-000000000201")
        private val ASSISTANT_ID = Uuid.parse("00000000-0000-0000-0000-000000000202")
        private val SOURCE_NODE_ID = Uuid.parse("00000000-0000-0000-0000-000000000203")
        private val HOOK_ID = Uuid.parse("00000000-0000-0000-0000-000000000204")
        private val MODEL_ID = Uuid.parse("00000000-0000-0000-0000-000000000205")
        private const val LEASE_TOKEN = 99L
    }
}
