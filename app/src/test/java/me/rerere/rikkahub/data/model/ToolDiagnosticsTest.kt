package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDiagnosticsTest {
    @Test
    fun `diagnostic exposes primary reason chain and repair target`() {
        val capability = ToolCapability("workspace:workspace_read_file", ToolCapabilitySource.WORKSPACE,
            "workspace_read_file", configured = true, available = false, effective = false,
            reasonCode = ToolCapabilityReason.MISSING)
        val diagnostic = capability.diagnostic()
        assertEquals(ToolCapabilityReason.MISSING, diagnostic.primaryReason)
        assertEquals(ToolDiagnosticTarget.WORKSPACES, diagnostic.repairTarget)
        assertFalse(diagnostic.reasonChain.first().passed)
        assertTrue(diagnostic.reasonChain[1].passed)
        assertFalse(diagnostic.reasonChain[2].passed)
    }

    @Test
    fun `copy summary is allowlisted`() {
        val snapshot = ToolDiagnosticsSnapshot(listOf(
            ToolCapability(
                id = "mcp:private-url:token",
                source = ToolCapabilitySource.MCP,
                runtimeName = "mcp__private__tool",
                configured = true,
                available = false,
                effective = false,
                reasonCode = ToolCapabilityReason.CONNECTION_ERROR,
            ).diagnostic(),
        ))
        val summary = snapshot.copySummary()
        assertTrue(summary.contains("tools=1"))
        assertFalse(summary.contains("private-url"))
        assertFalse(summary.contains("token"))
    }

    @Test
    fun `source failures do not hide healthy sources`() {
        val healthy = ToolCapability(
            id = "builtin:web_search",
            source = ToolCapabilitySource.BUILTIN,
            runtimeName = "search_web",
            configured = true,
            available = true,
            effective = true,
            reasonCode = ToolCapabilityReason.AVAILABLE,
        )
        val result = collectToolDiagnostics(mapOf(
            ToolCapabilitySource.BUILTIN to { ToolCapabilitySnapshot(listOf(healthy)) },
            ToolCapabilitySource.MCP to { error("connection") },
        ))
        assertEquals(1, result.diagnostics.size)
        assertEquals("IllegalStateException", result.sourceFailures[ToolCapabilitySource.MCP])
    }
}
