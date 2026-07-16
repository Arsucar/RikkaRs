package me.rerere.rikkahub.service

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.model.resolveMemoryCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationMemoryPreparationTest {
    @Test
    fun normalMemoryOffKeepsMemoryTablePreparationEnabled() = runBlocking {
        val plan = resolveMemoryCapabilities(
            normalMemoryEnabled = false,
            settingsMemoryTableEnabled = true,
            assistantMemoryTableEnabled = true,
        ).toGenerationMemoryPlan()
        var reads = 0

        val memories = plan.readOrdinaryMemories {
            reads++
            listOf("ordinary")
        }

        assertTrue(memories.isEmpty())
        assertEquals(0, reads)
        assertTrue(plan.memoryTableTransformerEnabled)
        assertTrue(plan.memoryTableToolsEnabled)
    }
}
