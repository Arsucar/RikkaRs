package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPreferencesSettingsTest {
    @Test
    fun missingPreferencesUseCompressionDefaults() {
        val preferences = mutablePreferencesOf().compressionPreferences()

        assertEquals(DEFAULT_COMPRESS_TARGET_TOKENS, preferences.targetTokens)
        assertEquals(DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES, preferences.keepRecentMessages)
    }

    @Test
    fun eachMissingPreferenceFallsBackIndependently() {
        val targetOnly = mutablePreferencesOf(
            SettingsStore.COMPRESS_TARGET_TOKENS to 3_333,
        ).compressionPreferences()
        val keepRecentOnly = mutablePreferencesOf(
            SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES to 21,
        ).compressionPreferences()

        assertEquals(3_333, targetOnly.targetTokens)
        assertEquals(DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES, targetOnly.keepRecentMessages)
        assertEquals(DEFAULT_COMPRESS_TARGET_TOKENS, keepRecentOnly.targetTokens)
        assertEquals(21, keepRecentOnly.keepRecentMessages)
    }

    @Test
    fun compressionPreferenceWriterUpdatesBothValues() {
        val preferences = mutablePreferencesOf()

        preferences.writeCompressionPreferences(
            targetTokens = 3_333,
            keepRecentMessages = 21,
        )

        assertEquals(
            CompressionPreferences(targetTokens = 3_333, keepRecentMessages = 21),
            preferences.compressionPreferences(),
        )
    }

    @Test
    fun oldSettingsJsonWithoutCompressionFieldsUsesDefaults() {
        val decoded = JsonInstant.decodeFromString<Settings>("{}")

        assertEquals(DEFAULT_COMPRESS_TARGET_TOKENS, decoded.compressTargetTokens)
        assertEquals(DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES, decoded.compressKeepRecentMessages)
    }

    @Test
    fun settingsJsonExportIncludesCompressionDefaults() {
        val encoded = JsonInstant.encodeToString(Settings())

        assertTrue(encoded.contains("\"compressTargetTokens\":2000"))
        assertTrue(encoded.contains("\"compressKeepRecentMessages\":32"))
    }

    @Test
    fun compressionFieldsSurviveSettingsJsonRoundTrip() {
        val original = Settings(
            compressTargetTokens = 3_333,
            compressKeepRecentMessages = 21,
        )

        val encoded = JsonInstant.encodeToString(original)
        val decoded = JsonInstant.decodeFromString<Settings>(encoded)

        assertTrue(encoded.contains("\"compressTargetTokens\":3333"))
        assertTrue(encoded.contains("\"compressKeepRecentMessages\":21"))
        assertEquals(3_333, decoded.compressTargetTokens)
        assertEquals(21, decoded.compressKeepRecentMessages)
    }
}
