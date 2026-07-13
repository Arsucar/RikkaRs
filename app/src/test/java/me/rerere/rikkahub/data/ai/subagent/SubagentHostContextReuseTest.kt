package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.uuid.Uuid

class SubagentHostContextReuseTest {
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
    fun omittedContextIdAutomaticallyContinuesLatestCompletedContext() = runBlocking {
        val cache = SubagentContextCache()
        val previous = cache.createAndAcquire(scope, listOf(UIMessage.user("first task")))
        cache.finish(
            contextId = previous.contextId,
            status = SubagentStatus.COMPLETED,
            messages = previous.messages + UIMessage.assistant("first result"),
        )

        val acquisition = acquireSubagentContext(
            cache = cache,
            scope = scope,
            task = "follow up",
            reuseContextId = null,
        )

        assertEquals(previous.contextId, acquisition.context.contextId)
        assertTrue(acquisition.reusedContext)
        assertEquals(
            listOf("first task", "first result", "follow up"),
            acquisition.context.messages.map { it.toText() },
        )
    }

    @Test
    fun explicitContextIdKeepsInterruptedReuseSemanticsAndTakesPriority() = runBlocking {
        val cache = SubagentContextCache()
        val interrupted = cache.createAndAcquire(scope, listOf(UIMessage.user("interrupted task")))
        cache.finish(interrupted.contextId, SubagentStatus.INTERRUPTED, error = "cancelled")
        val completed = cache.createAndAcquire(scope, listOf(UIMessage.user("completed task")))
        cache.finish(completed.contextId, SubagentStatus.COMPLETED)

        val acquisition = acquireSubagentContext(
            cache = cache,
            scope = scope,
            task = "resume interrupted",
            reuseContextId = interrupted.contextId,
        )

        assertEquals(interrupted.contextId, acquisition.context.contextId)
        assertTrue(acquisition.reusedContext)
        assertEquals(
            listOf("interrupted task", "resume interrupted"),
            acquisition.context.messages.map { it.toText() },
        )
        assertEquals(SubagentStatus.COMPLETED, cache.snapshot(completed.contextId)?.status)
    }

    @Test
    fun explicitContextIdFailureDoesNotFallBackToAutomaticReuseOrCreation() = runBlocking {
        val cache = SubagentContextCache()
        val automaticCandidate = cache.createAndAcquire(scope, listOf(UIMessage.user("existing")))
        cache.finish(automaticCandidate.contextId, SubagentStatus.COMPLETED)

        val error = expectContextError {
            acquireSubagentContext(
                cache = cache,
                scope = scope,
                task = "follow up",
                reuseContextId = "missing-context",
            )
        }

        assertEquals(SubagentContextErrorCode.CONTEXT_NOT_FOUND, error.code)
        assertEquals(1, cache.size())
        assertEquals(SubagentStatus.COMPLETED, cache.snapshot(automaticCandidate.contextId)?.status)
    }

    @Test
    fun omittedContextIdCreatesNewContextWhenNoCompletedCandidateExists() = runBlocking {
        val cache = SubagentContextCache()
        cache.createAndAcquire(scope, listOf(UIMessage.user("still running")))

        val acquisition = acquireSubagentContext(
            cache = cache,
            scope = scope,
            task = "independent task",
            reuseContextId = null,
        )

        assertFalse(acquisition.reusedContext)
        assertEquals("independent task", acquisition.context.messages.single().toText())
        assertEquals(2, cache.size())
    }

    @Test
    fun concurrentAutomaticAcquisitionReusesCandidateOnceAndSafelyCreatesAnother() = runBlocking {
        val cache = SubagentContextCache()
        val previous = cache.createAndAcquire(scope, listOf(UIMessage.user("previous")))
        cache.finish(previous.contextId, SubagentStatus.COMPLETED)

        val acquisitions = coroutineScope {
            List(2) { index ->
                async {
                    acquireSubagentContext(
                        cache = cache,
                        scope = scope,
                        task = "task-$index",
                        reuseContextId = null,
                    )
                }
            }.awaitAll()
        }

        assertEquals(2, acquisitions.map { it.context.contextId }.toSet().size)
        assertEquals(1, acquisitions.count { it.context.contextId == previous.contextId })
        assertEquals(1, acquisitions.count { it.reusedContext })
        assertEquals(1, acquisitions.count { !it.reusedContext })
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
