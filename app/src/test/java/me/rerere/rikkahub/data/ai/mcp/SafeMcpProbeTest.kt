package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeMcpProbeTest {
    @Test
    fun probeOnlyConnectsListsAndCloses() = runBlocking {
        val calls = mutableListOf<String>()
        val count = runSafeMcpProbe(
            connect = { calls += "connect" },
            listToolNames = { calls += "list"; listOf("enabled", "disabled") },
            close = { calls += "close" },
            enabledToolNames = setOf("enabled"),
        )
        assertEquals(1, count)
        assertEquals(listOf("connect", "list", "close"), calls)
    }

    @Test
    fun probeClosesAfterFailure() = runBlocking {
        val calls = mutableListOf<String>()
        runCatching {
            runSafeMcpProbe(
                connect = { calls += "connect" },
                listToolNames = { error("protocol") },
                close = { calls += "close" },
            )
        }
        assertEquals(listOf("connect", "close"), calls)
    }
}
