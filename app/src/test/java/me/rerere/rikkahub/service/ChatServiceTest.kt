package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.model.ToolCapability
import me.rerere.rikkahub.data.model.ToolCapabilityReason
import me.rerere.rikkahub.data.model.ToolCapabilitySnapshot
import me.rerere.rikkahub.data.model.ToolCapabilitySource
import me.rerere.rikkahub.data.model.ToolPermission
import me.rerere.rikkahub.data.model.finalizeGenerationTools
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatServiceTest {
    @Test
    fun `background generation params include model custom request configuration`() {
        val headers = listOf(CustomHeader(name = "X-Gateway-Token", value = "test-token"))
        val bodies = listOf(CustomBody(key = "gateway_mode", value = JsonPrimitive("strict")))
        val model = Model(
            modelId = "custom-chat-model",
            customHeaders = headers,
            customBodies = bodies,
        )

        val params = backgroundTextGenerationParams(model)

        assertEquals(model, params.model)
        assertEquals(ReasoningLevel.OFF, params.reasoningLevel)
        assertEquals(headers, params.customHeaders)
        assertEquals(bodies, params.customBody)
    }

    @Test
    fun capabilitySnapshotAndGenerationFinalizationShareRuntimeNames() {
        fun capability(id: String, name: String, effective: Boolean) = ToolCapability(
            id = id,
            source = ToolCapabilitySource.BUILTIN,
            runtimeName = name,
            configured = true,
            available = true,
            effective = effective,
            reasonCode = if (effective) ToolCapabilityReason.AVAILABLE else ToolCapabilityReason.DISABLED,
        )
        val snapshot = ToolCapabilitySnapshot(listOf(
            capability("builtin:web_search", "search_web", true),
            capability("memory:normal", "memory_tool", false),
        ))
        val tools = finalizeGenerationTools(
            listOf(
                Tool("search_web", "", execute = { emptyList() }),
                Tool("memory_tool", "", execute = { emptyList() }),
            ),
            mapOf("memory:normal" to ToolPermission.DENY),
        )
        assertTrue(snapshot.matchesRuntimeNames(tools.map { it.name }))
    }
}
