package me.rerere.rikkahub.data.model

import me.rerere.ai.core.Tool
import org.junit.Assert.*
import org.junit.Test

class ToolPermissionPolicyTest {
    private fun tool() = Tool("x", "x", execute = { emptyList() })

    @Test fun denyRemovesTool() {
        assertTrue(applyAssistantToolPermissions(listOf(tool()), mapOf("local:x" to ToolPermission.DENY)).isEmpty())
    }

    @Test fun askForcesApproval() {
        val resolved = applyAssistantToolPermissions(listOf(tool()), mapOf("local:x" to ToolPermission.ASK))
        assertTrue(resolved.single().needsApproval(kotlinx.serialization.json.JsonNull))
    }

    @Test fun missingPolicyInheritsToolBehavior() {
        val resolved = applyAssistantToolPermissions(listOf(tool()), emptyMap())
        assertFalse(resolved.single().needsApproval(kotlinx.serialization.json.JsonNull))
    }

    @Test fun catalogProjectionAndOrphansUseStableIds() {
        val capability = ToolCapability(
            id = "memory:normal",
            source = ToolCapabilitySource.MEMORY,
            runtimeName = "memory_tool",
            configured = true,
            available = true,
            effective = true,
            reasonCode = ToolCapabilityReason.AVAILABLE,
        )
        val projected = ToolCapabilitySnapshot(listOf(capability)).applyPermissions(
            mapOf("memory:normal" to ToolPermission.DENY),
        )
        assertFalse(projected.capabilities.single().effective)
        assertEquals(
            setOf("removed:tool"),
            orphanToolPermissionIds(mapOf("removed:tool" to ToolPermission.ASK), setOf("memory:normal")),
        )
    }
}
