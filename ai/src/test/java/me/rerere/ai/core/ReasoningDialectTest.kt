package me.rerere.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for reasoning dialect resolve/map (#207).
 */
class ReasoningDialectTest {

    @Test
    fun `explicit dialect wins over host and model id`() {
        val dialect = resolveDialect(
            explicit = ReasoningDialect.OpenAIClassic,
            host = "api.deepseek.com",
            modelId = "deepseek-reasoner",
        )
        assertEquals(ReasoningDialect.OpenAIClassic, dialect)
    }

    @Test
    fun `auto resolves official deepseek host to DeepSeekMax`() {
        val dialect = resolveDialect(
            explicit = ReasoningDialect.Auto,
            host = "api.deepseek.com",
            modelId = "deepseek-chat",
        )
        assertEquals(ReasoningDialect.DeepSeekMax, dialect)
    }

    @Test
    fun `auto resolves nvidia deepseek-v4 to DeepSeekMax`() {
        val dialect = resolveDialect(
            explicit = ReasoningDialect.Auto,
            host = "integrate.api.nvidia.com",
            modelId = "deepseek-ai/deepseek-v4-pro",
        )
        assertEquals(ReasoningDialect.DeepSeekMax, dialect)
    }

    @Test
    fun `auto uses model-id hint for deepseek reasoner on unknown host`() {
        val dialect = resolveDialect(
            explicit = ReasoningDialect.Auto,
            host = "proxy.example.com",
            modelId = "deepseek-reasoner",
        )
        assertEquals(ReasoningDialect.DeepSeekMax, dialect)
    }

    @Test
    fun `auto falls back to OpenAIExtended for unknown models`() {
        val dialect = resolveDialect(
            explicit = ReasoningDialect.Auto,
            host = "api.openai.com",
            modelId = "o3-mini",
        )
        assertEquals(ReasoningDialect.OpenAIExtended, dialect)
    }

    @Test
    fun `DeepSeekMax maps XHIGH to max`() {
        assertEquals(
            "max",
            mapReasoningEffort(ReasoningDialect.DeepSeekMax, ReasoningLevel.XHIGH),
        )
        assertEquals(
            "high",
            mapReasoningEffort(ReasoningDialect.DeepSeekMax, ReasoningLevel.HIGH),
        )
        assertEquals(
            "none",
            mapReasoningEffort(ReasoningDialect.DeepSeekMax, ReasoningLevel.OFF),
        )
    }

    @Test
    fun `OpenAIExtended keeps xhigh`() {
        assertEquals(
            "xhigh",
            mapReasoningEffort(ReasoningDialect.OpenAIExtended, ReasoningLevel.XHIGH),
        )
    }

    @Test
    fun `OpenAIClassic clamps XHIGH to high`() {
        assertEquals(
            "high",
            mapReasoningEffort(ReasoningDialect.OpenAIClassic, ReasoningLevel.XHIGH),
        )
    }

    @Test
    fun `noneAsLow rewrites OFF`() {
        assertEquals(
            "low",
            mapReasoningEffort(
                ReasoningDialect.OpenAIExtended,
                ReasoningLevel.OFF,
                noneAsLow = true,
            ),
        )
        assertEquals(
            "none",
            mapReasoningEffort(
                ReasoningDialect.OpenAIExtended,
                ReasoningLevel.OFF,
                noneAsLow = false,
            ),
        )
    }

    @Test
    fun `AUTO omits effort token`() {
        assertNull(mapReasoningEffort(ReasoningDialect.DeepSeekMax, ReasoningLevel.AUTO))
        assertNull(mapReasoningEffort(ReasoningDialect.OpenAIExtended, ReasoningLevel.AUTO))
    }

    @Test
    fun `OnOffOnly never emits effort token`() {
        assertNull(mapReasoningEffort(ReasoningDialect.OnOffOnly, ReasoningLevel.XHIGH))
        assertNull(mapReasoningEffort(ReasoningDialect.OnOffOnly, ReasoningLevel.HIGH))
        assertNull(mapReasoningEffort(ReasoningDialect.OnOffOnly, ReasoningLevel.OFF))
    }

    @Test
    fun `nvidia deepseek-v4 coarse table`() {
        assertEquals("max", mapNvidiaDeepSeekV4Effort(ReasoningLevel.XHIGH))
        assertEquals("none", mapNvidiaDeepSeekV4Effort(ReasoningLevel.OFF))
        assertEquals("high", mapNvidiaDeepSeekV4Effort(ReasoningLevel.LOW))
        assertEquals("high", mapNvidiaDeepSeekV4Effort(ReasoningLevel.MEDIUM))
        assertEquals("high", mapNvidiaDeepSeekV4Effort(ReasoningLevel.HIGH))
        assertNull(mapNvidiaDeepSeekV4Effort(ReasoningLevel.AUTO))
    }
}
