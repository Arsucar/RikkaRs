package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantSwitchCoordinatorTest {
    @Test
    fun `selecting current assistant is a no-op`() = runBlocking {
        val currentAssistantId = Uuid.random()
        var persistCalls = 0
        var queryCalls = 0
        var idCreations = 0
        val navigations = mutableListOf<Uuid>()
        val errors = mutableListOf<Throwable>()
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { currentAssistantId },
            persistAssistant = { persistCalls++ },
            getLatestActiveConversationId = { queryCalls++; null },
            newConversationId = { idCreations++; Uuid.random() },
            onError = errors::add,
        )

        coordinator.executeSwitch(
            request = coordinator.requestSwitch(currentAssistantId),
            navigate = navigations::add,
        )

        assertEquals(0, persistCalls)
        assertEquals(0, queryCalls)
        assertEquals(0, idCreations)
        assertTrue(navigations.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `switch persists before querying and navigates once to latest history`() = runBlocking {
        val currentAssistantId = Uuid.random()
        val targetAssistantId = Uuid.random()
        val latestConversationId = Uuid.random()
        val events = mutableListOf<String>()
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { currentAssistantId },
            persistAssistant = {
                assertEquals(targetAssistantId, it)
                events += "persist"
            },
            getLatestActiveConversationId = {
                assertEquals(targetAssistantId, it)
                events += "query"
                latestConversationId
            },
            newConversationId = { error("history must be reused") },
            onError = { throw AssertionError("unexpected error", it) },
        )

        coordinator.executeSwitch(
            request = coordinator.requestSwitch(targetAssistantId),
            navigate = {
                assertEquals(latestConversationId, it)
                events += "navigate"
            },
        )

        assertEquals(listOf("persist", "query", "navigate"), events)
    }

    @Test
    fun `switch without history creates one destination after persistence`() = runBlocking {
        val currentAssistantId = Uuid.random()
        val targetAssistantId = Uuid.random()
        val newConversationId = Uuid.random()
        val events = mutableListOf<String>()
        var idCreations = 0
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { currentAssistantId },
            persistAssistant = { events += "persist" },
            getLatestActiveConversationId = {
                events += "query"
                null
            },
            newConversationId = {
                idCreations++
                events += "create"
                newConversationId
            },
            onError = { throw AssertionError("unexpected error", it) },
        )

        coordinator.executeSwitch(
            request = coordinator.requestSwitch(targetAssistantId),
            navigate = {
                assertEquals(newConversationId, it)
                events += "navigate"
            },
        )

        assertEquals(1, idCreations)
        assertEquals(listOf("persist", "query", "create", "navigate"), events)
    }

    @Test
    fun `persistence failure reports error and does not query or navigate`() = runBlocking {
        val currentAssistantId = Uuid.random()
        val targetAssistantId = Uuid.random()
        val failure = IllegalStateException("save failed")
        var queryCalls = 0
        val navigations = mutableListOf<Uuid>()
        val errors = mutableListOf<Throwable>()
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { currentAssistantId },
            persistAssistant = { throw failure },
            getLatestActiveConversationId = { queryCalls++; Uuid.random() },
            onError = errors::add,
        )

        coordinator.executeSwitch(
            request = coordinator.requestSwitch(targetAssistantId),
            navigate = navigations::add,
        )

        assertEquals(listOf(failure), errors)
        assertEquals(0, queryCalls)
        assertTrue(navigations.isEmpty())
    }

    @Test
    fun `query failure reports error and does not create or navigate`() = runBlocking {
        val currentAssistantId = Uuid.random()
        val targetAssistantId = Uuid.random()
        val failure = IllegalStateException("query failed")
        var idCreations = 0
        val navigations = mutableListOf<Uuid>()
        val errors = mutableListOf<Throwable>()
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { currentAssistantId },
            persistAssistant = {},
            getLatestActiveConversationId = { throw failure },
            newConversationId = { idCreations++; Uuid.random() },
            onError = errors::add,
        )

        coordinator.executeSwitch(
            request = coordinator.requestSwitch(targetAssistantId),
            navigate = navigations::add,
        )

        assertEquals(listOf(failure), errors)
        assertEquals(0, idCreations)
        assertTrue(navigations.isEmpty())
    }

    @Test
    fun `rapid selections only navigate the latest assistant`() = runBlocking {
        val originalAssistantId = Uuid.random()
        val middleAssistantId = Uuid.random()
        val finalAssistantId = Uuid.random()
        val middlePersistEntered = CompletableDeferred<Unit>()
        val releaseMiddlePersist = CompletableDeferred<Unit>()
        val finalConversationId = Uuid.random()
        val persistedAssistants = mutableListOf<Uuid>()
        val queriedAssistants = mutableListOf<Uuid>()
        val navigations = mutableListOf<Uuid>()
        var selectedAssistantId = originalAssistantId
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { selectedAssistantId },
            persistAssistant = { assistantId ->
                persistedAssistants += assistantId
                if (assistantId == middleAssistantId) {
                    middlePersistEntered.complete(Unit)
                    releaseMiddlePersist.await()
                }
                selectedAssistantId = assistantId
            },
            getLatestActiveConversationId = { assistantId ->
                queriedAssistants += assistantId
                if (assistantId == finalAssistantId) finalConversationId else Uuid.random()
            },
            onError = { throw AssertionError("unexpected error", it) },
        )

        val middleRequest = coordinator.requestSwitch(middleAssistantId)
        val middleJob = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.executeSwitch(middleRequest, navigations::add)
        }
        middlePersistEntered.await()

        val finalRequest = coordinator.requestSwitch(finalAssistantId)
        val finalJob = async {
            coordinator.executeSwitch(finalRequest, navigations::add)
        }
        releaseMiddlePersist.complete(Unit)
        awaitAll(middleJob, finalJob)

        assertEquals(listOf(middleAssistantId, finalAssistantId), persistedAssistants)
        assertEquals(listOf(finalAssistantId), queriedAssistants)
        assertEquals(listOf(finalConversationId), navigations)
        assertEquals(finalAssistantId, selectedAssistantId)
    }

    @Test
    fun `selecting settled assistant invalidates pending switch without navigation`() = runBlocking {
        val originalAssistantId = Uuid.random()
        val pendingAssistantId = Uuid.random()
        val pendingPersistEntered = CompletableDeferred<Unit>()
        val releasePendingPersist = CompletableDeferred<Unit>()
        val persistedAssistants = mutableListOf<Uuid>()
        val queriedAssistants = mutableListOf<Uuid>()
        val navigations = mutableListOf<Uuid>()
        var selectedAssistantId = originalAssistantId
        val coordinator = AssistantSwitchCoordinator(
            currentAssistantId = { selectedAssistantId },
            persistAssistant = { assistantId ->
                persistedAssistants += assistantId
                if (assistantId == pendingAssistantId) {
                    pendingPersistEntered.complete(Unit)
                    releasePendingPersist.await()
                }
                selectedAssistantId = assistantId
            },
            getLatestActiveConversationId = { assistantId ->
                queriedAssistants += assistantId
                Uuid.random()
            },
            onError = { throw AssertionError("unexpected error", it) },
        )

        val pendingRequest = coordinator.requestSwitch(pendingAssistantId)
        val pendingJob = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.executeSwitch(pendingRequest, navigations::add)
        }
        pendingPersistEntered.await()

        val restoreRequest = coordinator.requestSwitch(originalAssistantId)
        val restoreJob = async {
            coordinator.executeSwitch(restoreRequest, navigations::add)
        }
        releasePendingPersist.complete(Unit)
        awaitAll(pendingJob, restoreJob)

        assertEquals(listOf(pendingAssistantId, originalAssistantId), persistedAssistants)
        assertEquals(originalAssistantId, selectedAssistantId)
        assertTrue(queriedAssistants.isEmpty())
        assertTrue(navigations.isEmpty())
    }
}
