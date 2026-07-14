package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

sealed interface AssistantArchiveResult {
    data object Success : AssistantArchiveResult
    data object LastActiveAssistant : AssistantArchiveResult
    data object AssistantNotFound : AssistantArchiveResult
}

internal data class AssistantLifecycleState(
    val assistants: List<Assistant>,
    val selectedAssistantId: Uuid,
)

fun Settings.activeAssistants(): List<Assistant> = assistants.filterNot { it.isArchived }

internal fun transitionAssistantArchive(
    assistants: List<Assistant>,
    selectedAssistantId: Uuid,
    assistantId: Uuid,
    archived: Boolean,
): Pair<AssistantArchiveResult, AssistantLifecycleState?> {
    val target = assistants.firstOrNull { it.id == assistantId }
        ?: return AssistantArchiveResult.AssistantNotFound to null
    if (target.isArchived == archived) {
        return AssistantArchiveResult.Success to AssistantLifecycleState(assistants, selectedAssistantId)
    }
    if (archived && assistants.count { !it.isArchived } <= 1) {
        return AssistantArchiveResult.LastActiveAssistant to null
    }

    val updatedAssistants = assistants.map { assistant ->
        if (assistant.id == assistantId) assistant.copy(isArchived = archived) else assistant
    }
    val updatedSelectedId = if (archived && selectedAssistantId == assistantId) {
        updatedAssistants.first { !it.isArchived }.id
    } else {
        selectedAssistantId
    }
    return AssistantArchiveResult.Success to AssistantLifecycleState(
        assistants = updatedAssistants,
        selectedAssistantId = updatedSelectedId,
    )
}

internal fun normalizeAssistantLifecycle(
    assistants: List<Assistant>,
    selectedAssistantId: Uuid,
): AssistantLifecycleState {
    require(assistants.isNotEmpty())
    val normalizedAssistants = if (assistants.none { !it.isArchived }) {
        assistants.mapIndexed { index, assistant ->
            if (index == 0) assistant.copy(isArchived = false) else assistant
        }
    } else {
        assistants
    }
    val normalizedSelectedId = normalizedAssistants
        .firstOrNull { it.id == selectedAssistantId && !it.isArchived }
        ?.id
        ?: normalizedAssistants.first { !it.isArchived }.id
    return AssistantLifecycleState(normalizedAssistants, normalizedSelectedId)
}

internal fun reorderActiveAssistants(
    assistants: List<Assistant>,
    fromIndex: Int,
    toIndex: Int,
): List<Assistant> {
    val active = assistants.filterNot { it.isArchived }.toMutableList()
    if (fromIndex !in active.indices || toIndex !in active.indices || fromIndex == toIndex) {
        return assistants
    }
    active.add(toIndex, active.removeAt(fromIndex))
    val reordered = active.iterator()
    return assistants.map { assistant ->
        if (assistant.isArchived) assistant else reordered.next()
    }
}
