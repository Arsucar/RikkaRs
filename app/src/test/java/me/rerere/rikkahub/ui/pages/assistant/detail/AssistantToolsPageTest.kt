package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToolsPageTest {
    @Test
    fun memoryTableUiPreservesPreferenceWhenGlobalGateIsOff() {
        val state = memoryTableToolUiState(
            memoryTableGloballyEnabled = false,
            assistantMemoryTableEnabled = true,
        )

        assertTrue(state.checked)
        assertFalse(state.active)
        assertFalse(state.controlEnabled)
    }

    @Test
    fun memoryTableUiActivatesStoredPreferenceWhenGlobalGateReturns() {
        val state = memoryTableToolUiState(
            memoryTableGloballyEnabled = true,
            assistantMemoryTableEnabled = true,
        )

        assertTrue(state.checked)
        assertTrue(state.active)
        assertTrue(state.controlEnabled)
    }

    @Test
    fun empowermentStatsCountOnlyEffectiveMemoryTableCapability() {
        val assistant = Assistant(enableMemory = false, enableMemoryTable = true)
        val enabledWithGlobalGate = empowermentToolStats(
            assistant = assistant,
            memoryTableGloballyEnabled = true,
        ).first
        val enabledWithoutGlobalGate = empowermentToolStats(
            assistant = assistant,
            memoryTableGloballyEnabled = false,
        ).first

        assertEquals(1, enabledWithGlobalGate - enabledWithoutGlobalGate)
    }
}
