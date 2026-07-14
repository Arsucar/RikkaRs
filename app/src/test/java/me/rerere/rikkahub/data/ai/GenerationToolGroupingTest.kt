package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationToolGroupingTest {
    @Test
    fun consecutiveSpawnCallsShareAGroupWhileOrdinaryToolsStayOrderedSingletons() {
        val tools = listOf(
            tool("read_file", "1"),
            tool("spawn_subagent", "2"),
            tool("spawn_subagent", "3"),
            tool("write_file", "4"),
            tool("spawn_subagent", "5"),
        )

        assertEquals(
            listOf(
                listOf("read_file"),
                listOf("spawn_subagent", "spawn_subagent"),
                listOf("write_file"),
                listOf("spawn_subagent"),
            ),
            groupToolsForSequentialExecution(tools).map { group -> group.map { it.toolName } },
        )
    }

    private fun tool(name: String, id: String) = UIMessagePart.Tool(
        toolCallId = id,
        toolName = name,
        input = "{}",
    )
}
