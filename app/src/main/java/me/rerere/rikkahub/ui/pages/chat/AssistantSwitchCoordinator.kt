package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

internal data class AssistantSwitchRequest(
    val generation: Long,
    val assistantId: Uuid,
)

/**
 * Serializes assistant selection persistence, destination resolution, and navigation.
 *
 * Requests are invalidated as soon as a newer selection is registered. The mutex keeps
 * persistence writes ordered, while the generation checks prevent an older query result
 * from navigating after the user has selected another assistant.
 */
internal class AssistantSwitchCoordinator(
    private val currentAssistantId: () -> Uuid,
    private val persistAssistant: suspend (Uuid) -> Unit,
    private val getLatestActiveConversationId: suspend (Uuid) -> Uuid?,
    private val newConversationId: () -> Uuid = { Uuid.random() },
    private val onError: (Throwable) -> Unit,
) {
    private val requestGeneration = AtomicLong(0L)
    private val switchMutex = Mutex()
    private var settledAssistantId: Uuid? = null

    fun requestSwitch(assistantId: Uuid): AssistantSwitchRequest {
        return AssistantSwitchRequest(
            generation = requestGeneration.incrementAndGet(),
            assistantId = assistantId,
        )
    }

    suspend fun executeSwitch(
        request: AssistantSwitchRequest,
        navigate: (Uuid) -> Unit,
    ) {
        switchMutex.withLock {
            if (!isLatest(request)) return

            try {
                val currentId = settledAssistantId ?: currentAssistantId().also {
                    settledAssistantId = it
                }
                if (request.assistantId == currentId) {
                    // A stale request may already have persisted a different assistant before
                    // this latest selection invalidated it. Restore the settled selection, but
                    // keep same-assistant selection free of conversation lookup/navigation.
                    if (currentAssistantId() != request.assistantId) {
                        persistAssistant(request.assistantId)
                    }
                    return
                }

                persistAssistant(request.assistantId)
                if (!isLatest(request)) return

                val destination = getLatestActiveConversationId(request.assistantId)
                    ?: newConversationId()
                if (!isLatest(request)) return

                navigate(destination)
                settledAssistantId = request.assistantId
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isLatest(request)) {
                    onError(error)
                }
            }
        }
    }

    private fun isLatest(request: AssistantSwitchRequest): Boolean {
        return request.generation == requestGeneration.get()
    }
}
