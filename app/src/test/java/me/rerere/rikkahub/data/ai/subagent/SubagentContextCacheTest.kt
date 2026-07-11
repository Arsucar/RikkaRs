package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.uuid.Uuid

class SubagentContextCacheTest {
    private val scope = SubagentContextScope(
        conversationId = Uuid.random(),
        parentAssistantId = Uuid.random(),
        workspaceId = Uuid.random(),
        workspaceCwd = "/workspace",
        depth = 1,
        profileName = "explore",
        workspaceAccess = WorkspaceAccess.READ_ONLY,
        permissionFingerprint = "read-only-profile-v1",
    )

    @Test
    fun defaultCacheConfigurationMatchesRuntimeContract() {
        assertEquals(60L * 60L * 1000L, DEFAULT_SUBAGENT_CONTEXT_TTL_MILLIS)
        assertEquals(16, DEFAULT_SUBAGENT_CONTEXT_CACHE_SIZE)
    }

    @Test
    fun completedContextCanBeAcquiredWithFullMessages() = runBlocking {
        val cache = SubagentContextCache()
        val messages = listOf(UIMessage.user("task"), UIMessage.assistant("result"))
        val context = cache.createAndAcquire(scope, messages)
        cache.finish(context.contextId, SubagentStatus.COMPLETED, messages)

        val reused = cache.acquireForReuse(context.contextId, scope)

        assertEquals(SubagentStatus.RUNNING, reused.status)
        assertEquals(messages, reused.messages)
    }

    @Test
    fun acquireForReuseAtomicallyAppendsTheNewTask() = runBlocking {
        val cache = SubagentContextCache()
        val context = completed(cache, "first task")

        val reused = cache.acquireForReuse(
            contextId = context.contextId,
            scope = scope,
            messagesToAppend = listOf(UIMessage.user("follow up")),
        )

        assertEquals(listOf("first task", "follow up"), reused.messages.map { it.toText() })
        assertEquals(SubagentStatus.RUNNING, cache.snapshot(context.contextId)?.status)
    }

    @Test
    fun expiredContextReturnsStableError() = runBlocking {
        var now = 1_000L
        val cache = SubagentContextCache(ttlMillis = 100, nowMillis = { now })
        val context = cache.createAndAcquire(scope, listOf(UIMessage.user("task")))
        cache.finish(context.contextId, SubagentStatus.COMPLETED)
        now += 101

        val error = expectContextError {
            cache.acquireForReuse(context.contextId, scope)
        }

        assertEquals(SubagentContextErrorCode.CONTEXT_EXPIRED, error.code)

        now += 100
        assertEquals(
            SubagentContextErrorCode.CONTEXT_NOT_FOUND,
            expectContextError { cache.acquireForReuse(context.contextId, scope) }.code,
        )
    }

    @Test
    fun expiredTombstonesAreBoundedAndKeepStableRecentSemantics() = runBlocking {
        var now = 0L
        val cache = SubagentContextCache(ttlMillis = 1_000, maxEntries = 1, nowMillis = { now })
        val expiredIds = mutableListOf<String>()

        repeat(DEFAULT_SUBAGENT_CONTEXT_CACHE_SIZE + 1) { index ->
            now = 0L
            val context = cache.createAndAcquire(
                scope.copy(depth = index),
                listOf(UIMessage.user("task-$index")),
            )
            cache.finish(context.contextId, SubagentStatus.COMPLETED)
            now = 1_001L
            assertEquals(
                SubagentContextErrorCode.CONTEXT_EXPIRED,
                expectContextError { cache.acquireForReuse(context.contextId, scope.copy(depth = index)) }.code,
            )
            expiredIds += context.contextId
        }

        assertEquals(
            SubagentContextErrorCode.CONTEXT_NOT_FOUND,
            expectContextError { cache.acquireForReuse(expiredIds.first(), scope) }.code,
        )
        assertEquals(
            SubagentContextErrorCode.CONTEXT_EXPIRED,
            expectContextError {
                cache.acquireForReuse(
                    expiredIds.last(),
                    scope.copy(depth = DEFAULT_SUBAGENT_CONTEXT_CACHE_SIZE),
                )
            }.code,
        )
    }

    @Test
    fun snapshotAndProgressRefreshSlidingTtl() = runBlocking {
        var now = 1_000L
        val cache = SubagentContextCache(ttlMillis = 100, nowMillis = { now })
        val context = cache.createAndAcquire(scope, listOf(UIMessage.user("task")))
        cache.finish(context.contextId, SubagentStatus.COMPLETED)

        now = 1_050L
        assertNotNull(cache.snapshot(context.contextId))
        now = 1_120L
        val reused = cache.acquireForReuse(context.contextId, scope)
        cache.updateProgress(reused.contextId, reused.messages + UIMessage.assistant("working"))
        cache.finish(reused.contextId, SubagentStatus.COMPLETED)
        now = 1_190L

        assertNotNull(cache.acquireForReuse(context.contextId, scope))
    }

    @Test
    fun accessOrderLruEvictsLeastRecentlyUsedTerminalContext() = runBlocking {
        val cache = SubagentContextCache(maxEntries = 2)
        val first = completed(cache, "first")
        val second = completed(cache, "second")
        cache.acquireForReuse(first.contextId, scope)
        cache.finish(first.contextId, SubagentStatus.COMPLETED)

        completed(cache, "third")

        assertEquals(
            SubagentContextErrorCode.CONTEXT_NOT_FOUND,
            expectContextError { cache.acquireForReuse(second.contextId, scope) }.code,
        )
        assertNotNull(cache.acquireForReuse(first.contextId, scope))
    }

    @Test
    fun snapshotAndProgressBothRefreshLruOrder() = runBlocking {
        val cache = SubagentContextCache(maxEntries = 2)
        val first = completed(cache, "first")
        val second = completed(cache, "second")
        cache.snapshot(first.contextId)
        cache.updateProgress(first.contextId, listOf(UIMessage.user("first updated")))

        completed(cache, "third")

        assertNotNull(cache.snapshot(first.contextId))
        assertNull(cache.snapshot(second.contextId))
    }

    @Test
    fun runningContextsAreNeverEvicted() = runBlocking {
        val cache = SubagentContextCache(maxEntries = 1)
        val first = cache.createAndAcquire(scope, listOf(UIMessage.user("first")))
        val second = cache.createAndAcquire(scope.copy(depth = 2), listOf(UIMessage.user("second")))

        assertEquals(2, cache.size())
        cache.finish(second.contextId, SubagentStatus.COMPLETED)

        assertEquals(1, cache.size())
        assertEquals(SubagentStatus.RUNNING, cache.snapshot(first.contextId)?.status)
    }

    @Test
    fun allRunningOverflowConvergesAfterTerminalTransition() = runBlocking {
        val cache = SubagentContextCache(maxEntries = 1)
        val first = cache.createAndAcquire(scope, listOf(UIMessage.user("first")))
        val second = cache.createAndAcquire(scope.copy(depth = 2), listOf(UIMessage.user("second")))

        assertEquals(2, cache.size())
        cache.finish(first.contextId, SubagentStatus.COMPLETED)

        assertEquals(1, cache.size())
        assertNull(cache.snapshot(first.contextId))
        assertEquals(SubagentStatus.RUNNING, cache.snapshot(second.contextId)?.status)
    }

    @Test
    fun concurrentAcquireAllowsExactlyOneLease() = runBlocking {
        val cache = SubagentContextCache()
        val context = completed(cache, "task")

        val results = coroutineScope {
            List(2) {
                async {
                    runCatching { cache.acquireForReuse(context.contextId, scope) }
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it.isSuccess })
        val failure = results.single { it.isFailure }.exceptionOrNull() as SubagentContextException
        assertEquals(SubagentContextErrorCode.CONTEXT_IN_USE, failure.code)
    }

    @Test
    fun eachMismatchedOwnerScopeFieldIsNamedAndRejected() = runBlocking {
        val cache = SubagentContextCache()
        val context = completed(cache, "task")

        val mismatches = listOf(
            "conversationId" to scope.copy(conversationId = Uuid.random()),
            "parentAssistantId" to scope.copy(parentAssistantId = Uuid.random()),
            "workspaceId" to scope.copy(workspaceId = Uuid.random()),
            "workspaceCwd" to scope.copy(workspaceCwd = "/other"),
            "depth" to scope.copy(depth = 2),
            "profileName" to scope.copy(profileName = "review"),
            "workspaceAccess" to scope.copy(workspaceAccess = WorkspaceAccess.FULL),
            "permissionFingerprint" to scope.copy(permissionFingerprint = "expanded-permissions"),
        )

        mismatches.forEach { (fieldName, mismatchedScope) ->
            val error = expectContextError {
                cache.acquireForReuse(context.contextId, mismatchedScope)
            }
            assertEquals(SubagentContextErrorCode.CONTEXT_SCOPE_MISMATCH, error.code)
            assertTrue(error.message.orEmpty().contains("actual(cached)"))
            assertTrue(error.message.orEmpty().contains("expected(requested)"))
            assertTrue(error.message.orEmpty().contains(fieldName))
            assertEquals(SubagentStatus.COMPLETED, cache.snapshot(context.contextId)?.status)
        }
    }

    @Test
    fun multipleScopeMismatchesAreReportedInStableFieldOrder() = runBlocking {
        val cache = SubagentContextCache()
        val context = completed(cache, "task")
        val mismatchedScope = scope.copy(
            conversationId = Uuid.random(),
            workspaceCwd = "/other",
            permissionFingerprint = "expanded-permissions",
        )

        val error = expectContextError {
            cache.acquireForReuse(context.contextId, mismatchedScope)
        }

        assertEquals(SubagentContextErrorCode.CONTEXT_SCOPE_MISMATCH, error.code)
        assertEquals(
            "Subagent context scope mismatch: actual(cached) differs from expected(requested) for fields: " +
                "conversationId, workspaceCwd, permissionFingerprint",
            error.message,
        )
    }

    @Test
    fun permissionFingerprintMismatchDoesNotExposeSensitiveValues() = runBlocking {
        val cache = SubagentContextCache()
        val context = completed(cache, "task")
        val requestedFingerprint = "secret-expanded-permissions"

        val error = expectContextError {
            cache.acquireForReuse(
                context.contextId,
                scope.copy(permissionFingerprint = requestedFingerprint),
            )
        }

        assertEquals(SubagentContextErrorCode.CONTEXT_SCOPE_MISMATCH, error.code)
        assertTrue(error.message.orEmpty().contains("permissionFingerprint"))
        assertFalse(error.message.orEmpty().contains(scope.permissionFingerprint))
        assertFalse(error.message.orEmpty().contains(requestedFingerprint))
    }

    @Test
    fun progressKeepsCompleteToolBearingHistory() = runBlocking {
        val cache = SubagentContextCache()
        val context = cache.createAndAcquire(scope, listOf(UIMessage.user("task")))
        val messages = listOf(
            UIMessage.user("task"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    me.rerere.ai.ui.UIMessagePart.Tool(
                        toolCallId = "call",
                        toolName = "workspace_read_file",
                        input = "{\"path\":\"/a\"}",
                        output = listOf(me.rerere.ai.ui.UIMessagePart.Text("body")),
                    ),
                ),
            ),
        )

        cache.updateProgress(context.contextId, messages)
        cache.finish(context.contextId, SubagentStatus.INTERRUPTED)
        val reused = cache.acquireForReuse(
            contextId = context.contextId,
            scope = scope,
            messagesToAppend = listOf(UIMessage.user("continue without replaying tools")),
        )

        assertEquals(messages, reused.messages.dropLast(1))
        assertEquals("continue without replaying tools", reused.messages.last().toText())
        assertTrue(
            reused.messages[1].parts.filterIsInstance<me.rerere.ai.ui.UIMessagePart.Tool>().single().isExecuted,
        )
    }

    @Test
    fun cancelledCoroutinePersistsInterruptedContextWithoutSwallowingCancellation() = runBlocking {
        val cache = SubagentContextCache()
        val context = cache.createAndAcquire(scope, listOf(UIMessage.user("task")))
        val job = launch {
            try {
                awaitCancellation()
            } finally {
                persistSubagentFailure(
                    cache = cache,
                    contextId = context.contextId,
                    status = SubagentStatus.INTERRUPTED,
                    fallbackMessages = listOf(UIMessage.user("fallback")),
                    fallbackUsage = null,
                    error = "cancelled",
                )
                persistSubagentFailure(
                    cache = cache,
                    contextId = context.contextId,
                    status = SubagentStatus.INTERRUPTED,
                    fallbackMessages = emptyList(),
                    fallbackUsage = null,
                    error = "cancelled again",
                )
            }
        }
        yield()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        val saved = cache.snapshot(context.contextId)!!
        assertEquals(SubagentStatus.INTERRUPTED, saved.status)
        assertEquals("task", saved.messages.single().toText())
        assertEquals("cancelled again", saved.lastError)
    }

    @Test
    fun cachedMessagesAreDeepSnapshotsOfMutableInputsAndOutputs() = runBlocking {
        val cache = SubagentContextCache()
        val output = mutableListOf<UIMessagePart>(
            UIMessagePart.Text(
                text = "body",
                metadata = buildJsonObject { put("phase", JsonPrimitive("saved")) },
            ),
        )
        val tool = UIMessagePart.Tool(
            toolCallId = "call",
            toolName = "workspace_read_file",
            input = "{\"path\":\"/a\"}",
            output = output,
            metadata = buildJsonObject { put("owner", JsonPrimitive("saved")) },
        )
        val parts = mutableListOf<UIMessagePart>(tool)
        val messages = mutableListOf(UIMessage(role = MessageRole.ASSISTANT, parts = parts))
        val context = cache.createAndAcquire(scope, messages)

        parts.clear()
        messages += UIMessage.assistant("later")
        tool.metadata = buildJsonObject { put("owner", JsonPrimitive("mutated")) }
        (output.single() as UIMessagePart.Text).metadata =
            buildJsonObject { put("phase", JsonPrimitive("mutated")) }
        output += UIMessagePart.Text("extra")

        val saved = cache.snapshot(context.contextId)!!
        val savedTool = saved.messages.single().parts.single() as UIMessagePart.Tool
        assertEquals("saved", savedTool.metadata?.get("owner")?.toString()?.trim('"'))
        assertEquals(1, savedTool.output.size)
        val savedOutput = savedTool.output.single() as UIMessagePart.Text
        assertEquals("body", savedOutput.text)
        assertEquals("saved", savedOutput.metadata?.get("phase")?.toString()?.trim('"'))
        assertFalse(saved.messages.any { it.toText() == "later" })
    }

    private suspend fun completed(cache: SubagentContextCache, task: String): SubagentContext {
        val context = cache.createAndAcquire(scope, listOf(UIMessage.user(task)))
        cache.finish(context.contextId, SubagentStatus.COMPLETED)
        return context
    }

    private suspend fun expectContextError(block: suspend () -> Unit): SubagentContextException {
        try {
            block()
            fail("Expected SubagentContextException")
        } catch (error: SubagentContextException) {
            return error
        }
        error("unreachable")
    }
}
