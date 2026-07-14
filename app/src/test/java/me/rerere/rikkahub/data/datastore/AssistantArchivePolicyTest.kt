package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantArchivePolicyTest {
    private val a = Assistant(id = Uuid.random(), name = "A")
    private val b = Assistant(id = Uuid.random(), name = "B")

    @Test
    fun archivingSelectedAssistantFallsBackAndRestoreDoesNotSwitch() {
        val (archiveResult, archived) = transitionAssistantArchive(listOf(a, b), a.id, a.id, true)
        assertEquals(AssistantArchiveResult.Success, archiveResult)
        assertEquals(b.id, archived?.selectedAssistantId)
        assertTrue(archived!!.assistants.first { it.id == a.id }.isArchived)

        val (_, restored) = transitionAssistantArchive(archived.assistants, b.id, a.id, false)
        assertEquals(b.id, restored?.selectedAssistantId)
        assertFalse(restored!!.assistants.first { it.id == a.id }.isArchived)
    }

    @Test
    fun lastActiveAssistantCannotBeArchived() {
        val (result, transition) = transitionAssistantArchive(listOf(a), a.id, a.id, true)
        assertEquals(AssistantArchiveResult.LastActiveAssistant, result)
        assertNull(transition)
    }

    @Test
    fun activeReorderPreservesArchivedSlots() {
        val archived = Assistant(id = Uuid.random(), name = "X", isArchived = true)
        assertEquals(listOf(b, archived, a), reorderActiveAssistants(listOf(a, archived, b), 0, 1))
    }
}
