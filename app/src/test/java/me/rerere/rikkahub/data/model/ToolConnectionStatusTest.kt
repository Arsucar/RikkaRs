package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ToolConnectionStatusTest {
    @Test
    fun connectedStatusDistinguishesEmptyAndDiscoveredTools() {
        assertEquals(ToolConnectionState.EMPTY, McpStatus.Connected.toToolConnectionStatus().state)
        val discovered = McpStatus.Connected.toToolConnectionStatus(toolCount = 3)
        assertEquals(ToolConnectionState.SUCCESS, discovered.state)
        assertEquals(3, discovered.toolCount)
    }

    @Test
    fun errorStatusRedactsSecretsAndClassifiesNetworkFailures() {
        val status = McpStatus.Error("network timeout token=secret-value")
            .toToolConnectionStatus(revision = 4)

        assertEquals(ToolConnectionState.NETWORK_ERROR, status.state)
        assertEquals(4, status.revision)
        assertFalse(status.message.orEmpty().contains("secret-value"))
        assertTrue(status.message.orEmpty().contains("[redacted]"))
    }

    @Test
    fun statusStoreRejectsObsoleteRevisionAndRemovesLifecycleState() {
        val serverId = Uuid.random()
        val store = ToolConnectionStatusStore()
        store.begin(serverId, revision = 2)

        assertFalse(
            store.publish(
                serverId,
                ToolConnectionStatus(ToolConnectionState.SUCCESS, toolCount = 1, revision = 1),
            ),
        )
        assertTrue(
            store.publish(
                serverId,
                ToolConnectionStatus(ToolConnectionState.EMPTY, revision = 2),
            ),
        )
        assertEquals(ToolConnectionState.EMPTY, store.snapshot().getValue(serverId).state)

        store.remove(serverId)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun workspaceAndAggregateStatusUseStablePriority() {
        assertEquals(
            ToolConnectionState.SUCCESS,
            workspaceConnectionStatus(WorkspaceShellStatus.READY.name).state,
        )
        val aggregate = aggregateToolConnectionStatus(
            listOf(
                ToolConnectionStatus(ToolConnectionState.SUCCESS, toolCount = 2),
                ToolConnectionStatus(ToolConnectionState.NEEDS_AUTHORIZATION),
            ),
        )
        assertEquals(ToolConnectionState.NEEDS_AUTHORIZATION, aggregate.state)
        assertEquals(2, aggregate.toolCount)
    }
}
