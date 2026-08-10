package me.rerere.rikkahub.data.workspace

import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsPrivateEntryAssistantTest {
    private val workspaceId = Uuid.random()
    private val otherWorkspaceId = Uuid.random()

    @Test
    fun exactlyOneBoundAssistantIsUsedDirectly() {
        val bound = Assistant(id = Uuid.random(), name = "Bound", workspaceId = workspaceId)
        val current = Assistant(id = Uuid.random(), name = "current")
        val settings = Settings(
            assistants = listOf(bound, current),
            assistantId = current.id,
        )

        val result = resolveSkillsPrivateEntryAssistant(settings, workspaceId.toString())

        assertEquals(listOf(bound), result.boundAssistants)
        assertEquals(bound.id, result.selectedAssistant.id)
        assertFalse(result.isCurrentAssistantFallback)
        assertFalse(result.requiresSelection)
    }

    @Test
    fun zeroBoundAssistantsFallBackToCurrentAssistant() {
        val current = Assistant(id = Uuid.random(), name = "current")
        val other = Assistant(
            id = Uuid.random(),
            name = "other",
            workspaceId = otherWorkspaceId,
        )
        val settings = Settings(
            assistants = listOf(current, other),
            assistantId = current.id,
        )

        val result = resolveSkillsPrivateEntryAssistant(settings, workspaceId.toString())

        assertTrue(result.boundAssistants.isEmpty())
        assertEquals(current.id, result.selectedAssistant.id)
        assertTrue(result.isCurrentAssistantFallback)
        assertFalse(result.requiresSelection)
    }

    @Test
    fun manyBoundAssistantsRequireSelectionAndPreferCurrentWhenBound() {
        val current = Assistant(id = Uuid.random(), name = "current", workspaceId = workspaceId)
        val second = Assistant(id = Uuid.random(), name = "second", workspaceId = workspaceId)
        val settings = Settings(
            assistants = listOf(second, current),
            assistantId = current.id,
        )

        val result = resolveSkillsPrivateEntryAssistant(settings, workspaceId.toString())

        assertEquals(2, result.boundAssistants.size)
        assertTrue(result.requiresSelection)
        assertFalse(result.isCurrentAssistantFallback)
        assertEquals(current.id, result.selectedAssistant.id)
    }

    @Test
    fun manyBoundAssistantsUseExplicitSelectionOverride() {
        val first = Assistant(id = Uuid.random(), name = "first", workspaceId = workspaceId)
        val second = Assistant(id = Uuid.random(), name = "second", workspaceId = workspaceId)
        val current = Assistant(id = Uuid.random(), name = "current")
        val settings = Settings(
            assistants = listOf(first, second, current),
            assistantId = current.id,
        )

        val result = resolveSkillsPrivateEntryAssistant(
            settings = settings,
            workspaceId = workspaceId.toString(),
            selectedAssistantId = second.id,
        )

        assertTrue(result.requiresSelection)
        assertEquals(second.id, result.selectedAssistant.id)
    }

    @Test
    fun manyBoundWithoutCurrentInSetDefaultsToFirstBound() {
        val first = Assistant(id = Uuid.random(), name = "first", workspaceId = workspaceId)
        val second = Assistant(id = Uuid.random(), name = "second", workspaceId = workspaceId)
        val current = Assistant(id = Uuid.random(), name = "current")
        val settings = Settings(
            assistants = listOf(first, second, current),
            assistantId = current.id,
        )

        val result = resolveSkillsPrivateEntryAssistant(settings, workspaceId.toString())

        assertTrue(result.requiresSelection)
        assertEquals(first.id, result.selectedAssistant.id)
    }

    @Test
    fun archivedBoundAssistantsAreIgnored() {
        val archived = Assistant(
            id = Uuid.random(),
            name = "archived",
            workspaceId = workspaceId,
            isArchived = true,
        )
        val current = Assistant(id = Uuid.random(), name = "current")
        val settings = Settings(
            assistants = listOf(archived, current),
            assistantId = current.id,
        )

        val result = resolveSkillsPrivateEntryAssistant(settings, workspaceId.toString())

        assertTrue(result.boundAssistants.isEmpty())
        assertTrue(result.isCurrentAssistantFallback)
        assertEquals(current.id, result.selectedAssistant.id)
    }
}
