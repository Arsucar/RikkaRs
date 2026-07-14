package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationSessionTest {
    @Test
    fun `delayed old job completion cannot clear a newer generation job`() = runBlocking {
        val session = session()
        val oldCompletionEntered = CompletableDeferred<Unit>()
        val releaseOldCompletion = CompletableDeferred<Unit>()
        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                oldCompletionEntered.complete(Unit)
                withContext(NonCancellable) {
                    releaseOldCompletion.await()
                }
            }
        }
        val newJob = Job()

        session.setJob(oldJob)
        session.setJob(newJob)
        oldCompletionEntered.await()

        try {
            assertFalse(oldJob.isCompleted)
            assertSame(newJob, session.getJob())
        } finally {
            releaseOldCompletion.complete(Unit)
            oldJob.join()
        }

        assertSame(newJob, session.getJob())
        newJob.cancel()
    }

    @Test
    fun `state snapshot rejects publication after source changes`() {
        val session = session()
        val snapshot = session.snapshotState()
        session.updateState { it.copy(title = "newer") }

        assertFalse(session.matchesSnapshot(snapshot))
        assertNull(session.compareAndSetState(snapshot, snapshot.conversation.copy(title = "compressed")))
    }

    @Test
    fun `trigger state is committed only after coordinator acquisition`() {
        val session = session()
        val config = AutoCompressionConfig(
            enabled = true,
            thresholdTokens = 100,
            targetTokens = 40,
            keepRecentMessages = 0,
            identity = "assistant",
        )
        val evaluation = session.evaluateAutoCompression(
            AutoCompressionPolicyInput(
                config = config,
                invocationKind = GenerationInvocationKind.NormalSend,
                providerInputAvailable = true,
                promptTokens = 100,
                visibleMessageCount = 1,
                fingerprint = "send",
                busy = false,
            )
        )

        assertEquals(AutoCompressionDecision.Trigger, evaluation.decision)
        assertTrue(session.autoCompressionStateForTest().armed)
        assertTrue(session.commitAutoCompressionTrigger(evaluation))
        assertFalse(session.autoCompressionStateForTest().armed)
    }

    private fun session(): ConversationSession {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode.of(UIMessage.user("hello"))),
        )
        return ConversationSession(
            id = conversation.id,
            initial = conversation,
            scope = CoroutineScope(Dispatchers.Unconfined),
            onIdle = {},
        )
    }
}
