package me.rerere.rikkahub.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableTest {
    @Test
    fun memoryTableRequiresGlobalAndAssistantGate() {
        assertFalse(shouldEnableMemoryTable(settingsEnabled = false, assistantEnabled = false))
        assertFalse(shouldEnableMemoryTable(settingsEnabled = true, assistantEnabled = false))
        assertFalse(shouldEnableMemoryTable(settingsEnabled = false, assistantEnabled = true))
        assertTrue(shouldEnableMemoryTable(settingsEnabled = true, assistantEnabled = true))
    }
}
