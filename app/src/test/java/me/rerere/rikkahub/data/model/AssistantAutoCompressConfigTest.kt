package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAutoCompressConfigTest {
    @Test
    fun oldAssistantJsonUsesAutoCompressDefaults() {
        val decoded = JsonInstant.decodeFromString<Assistant>("{}")

        assertFalse(decoded.autoCompressEnabled)
        assertEquals(8_000, decoded.autoCompressThresholdTokens)
        assertEquals(32, decoded.autoCompressKeepRecentMessages)
    }

    @Test
    fun assistantJsonExportIncludesAutoCompressDefaults() {
        val encoded = JsonInstant.encodeToString(Assistant())

        assertTrue(encoded.contains("\"autoCompressEnabled\":false"))
        assertTrue(encoded.contains("\"autoCompressThresholdTokens\":8000"))
        assertTrue(encoded.contains("\"autoCompressKeepRecentMessages\":32"))
    }

    @Test
    fun autoCompressFieldsSurviveAssistantJsonRoundTrip() {
        val original = Assistant(
            autoCompressEnabled = true,
            autoCompressThresholdTokens = 24_000,
            autoCompressKeepRecentMessages = 12,
        )

        val encoded = JsonInstant.encodeToString(original)
        val decoded = JsonInstant.decodeFromString<Assistant>(encoded)

        assertTrue(decoded.autoCompressEnabled)
        assertEquals(24_000, decoded.autoCompressThresholdTokens)
        assertEquals(12, decoded.autoCompressKeepRecentMessages)
    }
}
