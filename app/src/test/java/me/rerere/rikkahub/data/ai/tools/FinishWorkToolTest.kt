package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class FinishWorkToolTest {
    @Test
    fun name_isFinishWork() {
        assertEquals(FINISH_WORK_TOOL_NAME, createFinishWorkTool().name)
    }

    @Test
    fun execute_returnsTaskCompletedText() = runBlocking {
        val parts = createFinishWorkTool().execute(JsonObject(emptyMap()))
        assertEquals(1, parts.size)
        val text = parts.single() as UIMessagePart.Text
        assertEquals("Task completed.", text.text)
    }

    @Test
    fun needsApproval_isAlwaysFalse() {
        val tool = createFinishWorkTool()
        assertFalse(tool.needsApproval(JsonObject(emptyMap())))
    }

    @Test
    fun parameters_isNull() {
        val tool = createFinishWorkTool()
        assertSame(null, tool.parameters())
    }
}