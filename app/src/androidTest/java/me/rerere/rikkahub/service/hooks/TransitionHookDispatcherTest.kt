package me.rerere.rikkahub.service.hooks

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.HookExecutionEntity
import me.rerere.rikkahub.data.db.entity.HookRunEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.HookExecutionStatus
import me.rerere.rikkahub.data.model.HookRunStatus
import me.rerere.rikkahub.data.repository.HookRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class TransitionHookDispatcherTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun prepareRejectsWithoutProviderAndPersistsNullDecisionAudit() = runBlocking {
        val fixture = seedExecution("filter-reject")
        val model = CountingModelExecutor("unused")
        val handler = FakeTransitionHandler(FakeMode.FILTER_REJECT, fixture.prepared)

        dispatcher(model, handler).dispatch(fixture.runId, listOf(fixture.execution))

        val row = database.hookDao().getExecution(fixture.execution.executionId.toString())
        val summary = JsonInstant.decodeFromString<ConversationTagTransitionAuditSummary>(
            row?.operationSummaryJson.orEmpty()
        )
        assertEquals(0, model.calls)
        assertEquals(HookExecutionStatus.SKIPPED.name, row?.status)
        assertNull(row?.decision)
        assertEquals(HookErrorCode.GITHUB_ISSUE_EVIDENCE_NOT_FOUND.name, row?.errorCode)
        assertEquals(0, row?.operationCount)
        assertEquals("none", summary.evidence)
        assertFalse(row?.operationSummaryJson.orEmpty().contains("Created #148"))
    }

    @Test
    fun providerSkipPreservesAcceptedEvidenceAndWritesNoRelations() = runBlocking {
        val fixture = seedExecution("provider-skip")
        val model = CountingModelExecutor("""{"decision":"skip","reason":"model skip"}""")
        val handler = FakeTransitionHandler(FakeMode.PROVIDER_SKIP, fixture.prepared)

        dispatcher(model, handler).dispatch(fixture.runId, listOf(fixture.execution))

        val row = database.hookDao().getExecution(fixture.execution.executionId.toString())
        val summary = JsonInstant.decodeFromString<ConversationTagTransitionAuditSummary>(
            row?.operationSummaryJson.orEmpty()
        )
        assertEquals(1, model.calls)
        assertEquals(HookDecision.SKIP.name, row?.decision)
        assertNull(row?.errorCode)
        assertEquals("issue_number", summary.evidence)
        assertEquals("[]", row?.diffSummaryJson)
        assertEquals(0, database.conversationTagDao().countTagsForConversation(CONVERSATION_ID.toString()))
    }

    @Test
    fun postProviderConfigChangeSkipsApplyWithParsedDecision() = runBlocking {
        val fixture = seedExecution("config-change")
        val model = CountingModelExecutor("""{"decision":"apply","reason":"apply"}""")
        val handler = FakeTransitionHandler(FakeMode.CONFIG_CHANGED, fixture.prepared)

        dispatcher(model, handler).dispatch(fixture.runId, listOf(fixture.execution))

        val row = database.hookDao().getExecution(fixture.execution.executionId.toString())
        assertEquals(1, model.calls)
        assertEquals(HookExecutionStatus.SKIPPED.name, row?.status)
        assertEquals(HookDecision.APPLY.name, row?.decision)
        assertEquals(HookErrorCode.HOOK_DISABLED.name, row?.errorCode)
        assertEquals(0, row?.operationCount)
        assertEquals(0, database.conversationTagDao().countTagsForConversation(CONVERSATION_ID.toString()))
    }

    private fun dispatcher(model: HookModelExecutor, handler: HookActionHandler) = HookDispatcher(
        hookRepository = HookRepository(database.hookDao()),
        modelExecutor = model,
        actionRegistry = HookActionRegistry(listOf(handler)),
        now = { 50L },
    )

    private suspend fun seedExecution(name: String): Fixture {
        val runId = Uuid.random()
        val executionId = Uuid.random()
        val addTagId = Uuid.random()
        val removeTagId = Uuid.random()
        val config = HookActionConfig.TransitionConversationTags(addTagId, removeTagId)
        val hook = ConversationHook(
            id = HOOK_ID,
            name = name,
            modelId = MODEL_ID,
            prompt = "transition",
            actionConfig = config,
        )
        val evidence = GitHubIssueEvidence(GitHubIssueEvidenceType.ISSUE_NUMBER, 148)
        val prepared = PreparedHookAction.TransitionConversationTags(
            request = FrozenHookModelRequest.TransitionConversationTags(
                modelId = MODEL_ID,
                prompt = "transition",
                messageTextSnapshot = "Created #148",
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
            sourceMessageId = SOURCE_MESSAGE_ID,
            addTagId = addTagId,
            removeTagId = removeTagId,
            evidence = evidence,
        )
        database.conversationDao().insert(
            ConversationEntity(
                id = CONVERSATION_ID.toString(),
                assistantId = ASSISTANT_ID.toString(),
                title = name,
                nodes = "[]",
                createAt = 1,
                updateAt = 1,
                chatSuggestions = "[]",
                isPinned = false,
            )
        )
        database.hookDao().insertRunIgnore(
            HookRunEntity(
                runId = runId.toString(),
                conversationId = CONVERSATION_ID.toString(),
                assistantId = ASSISTANT_ID.toString(),
                logicalTurnId = Uuid.random().toString(),
                nodeId = SOURCE_NODE_ID.toString(),
                messageId = SOURCE_MESSAGE_ID.toString(),
                messageModelId = null,
                invocationKind = "HOOK_TEST",
                trigger = "AFTER_ASSISTANT_RESPONSE_SUCCESS",
                configVersion = 1,
                configHash = "hash",
                startedAt = 10,
                endedAt = null,
                status = HookRunStatus.QUEUED.name,
                failureCount = 0,
            )
        )
        database.hookDao().insertExecutions(
            listOf(
                HookExecutionEntity(
                    executionId = executionId.toString(),
                    runId = runId.toString(),
                    hookId = HOOK_ID.toString(),
                    hookOrder = 0,
                    hookConfigVersion = 1,
                    hookConfigHash = "hash",
                    modelId = MODEL_ID.toString(),
                    actionType = HookActionType.TRANSITION_CONVERSATION_TAGS.name,
                    executionMode = HookExecutionMode.AUTO.name,
                    startedAt = null,
                    endedAt = null,
                    status = HookExecutionStatus.QUEUED.name,
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
                    leaseToken = 0,
                )
            )
        )
        val conversation = Conversation(
            id = CONVERSATION_ID,
            assistantId = ASSISTANT_ID,
            messageNodes = emptyList(),
        )
        val execution = FrozenHookExecution(
            executionId = executionId,
            hook = hook,
            freezeContext = HookFreezeContext(
                conversation = conversation,
                assistantId = ASSISTANT_ID,
                logicalTurnId = Uuid.random(),
                sourceNodeId = SOURCE_NODE_ID,
                sourceMessageId = SOURCE_MESSAGE_ID,
                sourceMessageTextSnapshot = "Created #148",
                sourceMessageModelId = null,
                executionMode = HookExecutionMode.AUTO,
            ),
        )
        return Fixture(runId, execution, prepared)
    }

    private data class Fixture(
        val runId: Uuid,
        val execution: FrozenHookExecution,
        val prepared: PreparedHookAction.TransitionConversationTags,
    )

    private enum class FakeMode {
        FILTER_REJECT,
        PROVIDER_SKIP,
        CONFIG_CHANGED,
    }

    private class CountingModelExecutor(private val output: String) : HookModelExecutor {
        var calls: Int = 0
        override suspend fun execute(request: FrozenHookModelRequest): String {
            calls++
            return output
        }
    }

    private class FakeTransitionHandler(
        private val mode: FakeMode,
        private val transition: PreparedHookAction.TransitionConversationTags,
    ) : HookActionHandler {
        override val actionType: HookActionType = HookActionType.TRANSITION_CONVERSATION_TAGS

        override suspend fun prepare(
            hook: ConversationHook,
            context: HookFreezeContext,
        ): HookActionPreparation = if (mode == FakeMode.FILTER_REJECT) {
            val config = hook.actionConfig as HookActionConfig.TransitionConversationTags
            HookActionPreparation.Skipped(
                errorCode = HookErrorCode.GITHUB_ISSUE_EVIDENCE_NOT_FOUND,
                audit = transitionPreparedAudit(config, evidence = null),
                decision = null,
            )
        } else {
            HookActionPreparation.Ready(transition)
        }

        override fun parse(raw: String, prepared: PreparedHookAction): ParsedHookOutput =
            TransitionConversationTagsHookOutputParser.parse(raw)

        override suspend fun execute(
            executionId: Uuid,
            leaseToken: Long,
            prepared: PreparedHookAction,
            output: ParsedHookOutput,
        ): HookActionResult = when (mode) {
            FakeMode.FILTER_REJECT -> error("execute must not run")
            FakeMode.PROVIDER_SKIP -> HookActionResult.Skipped(null)
            FakeMode.CONFIG_CHANGED -> HookActionResult.Skipped(null, HookErrorCode.HOOK_DISABLED)
        }
    }

    companion object {
        private val CONVERSATION_ID = Uuid.parse("00000000-0000-0000-0000-000000000301")
        private val ASSISTANT_ID = Uuid.parse("00000000-0000-0000-0000-000000000302")
        private val SOURCE_NODE_ID = Uuid.parse("00000000-0000-0000-0000-000000000303")
        private val SOURCE_MESSAGE_ID = Uuid.parse("00000000-0000-0000-0000-000000000304")
        private val HOOK_ID = Uuid.parse("00000000-0000-0000-0000-000000000305")
        private val MODEL_ID = Uuid.parse("00000000-0000-0000-0000-000000000306")
    }
}
