package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableBudgetSettingsTest {
    @Test
    fun missingPreferenceUsesDefaultBudget() {
        assertEquals(
            DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
            decodeMemoryTableBudget(null, DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS),
        )
        assertEquals(
            DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
            decodeMemoryTableBudget(null, DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS),
        )
        assertEquals(
            DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
            decodeMemoryTableBudget(null, DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS),
        )
    }

    @Test
    fun preferenceCodecRoundTripsUnlimitedZeroAndPositiveValues() {
        assertNull(
            decodeMemoryTableBudget(
                encodeMemoryTableBudget(null),
                DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
            )
        )
        assertEquals(0, decodeMemoryTableBudget(encodeMemoryTableBudget(0), 20))
        assertEquals(42, decodeMemoryTableBudget(encodeMemoryTableBudget(42), 20))
    }

    @Test
    fun corruptNegativePreferenceFallsBackToDefault() {
        assertEquals(20, decodeMemoryTableBudget(storedValue = -2, defaultValue = 20))
    }

    @Test
    fun oldSettingsJsonWithoutBudgetFieldsUsesDefaults() {
        val decoded = JsonInstant.decodeFromString<Settings>("{}")

        assertEquals(DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS, decoded.memoryTableMaxInjectDocuments)
        assertEquals(DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS, decoded.memoryTableMaxInjectTokens)
        assertEquals(DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS, decoded.memoryTableMaxInjectChars)
    }

    @Test
    fun finiteBudgetsSurviveSettingsJsonRoundTrip() {
        val original = Settings(
            memoryTableMaxInjectDocuments = 0,
            memoryTableMaxInjectTokens = 1_024,
            memoryTableMaxInjectChars = 16_384,
        )

        val decoded = JsonInstant.decodeFromString<Settings>(
            JsonInstant.encodeToString(original)
        )

        assertEquals(0, decoded.memoryTableMaxInjectDocuments)
        assertEquals(1_024, decoded.memoryTableMaxInjectTokens)
        assertEquals(16_384, decoded.memoryTableMaxInjectChars)
    }

    @Test
    fun explicitNullBudgetSurvivesSettingsJsonRoundTrip() {
        val original = Settings(
            memoryTableMaxInjectDocuments = null,
            memoryTableMaxInjectTokens = null,
            memoryTableMaxInjectChars = null,
        )

        val encoded = JsonInstant.encodeToString(original)
        val decoded = JsonInstant.decodeFromString<Settings>(encoded)

        assertTrue(encoded.contains("\"memoryTableMaxInjectDocuments\":null"))
        assertTrue(encoded.contains("\"memoryTableMaxInjectTokens\":null"))
        assertTrue(encoded.contains("\"memoryTableMaxInjectChars\":null"))
        assertNull(decoded.memoryTableMaxInjectDocuments)
        assertNull(decoded.memoryTableMaxInjectTokens)
        assertNull(decoded.memoryTableMaxInjectChars)
    }
}
