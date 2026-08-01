package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadInjectModeSettingsTest {
    @Test
    fun oldSettingsJsonWithoutUploadInjectModeDefaultsToPathOnly() {
        val decoded = JsonInstant.decodeFromString<Settings>("{}")

        assertEquals(
            UploadInjectMode.PATH_ONLY,
            decoded.displaySetting.documentUploadInjectMode,
        )
    }

    @Test
    fun settingsJsonExportIncludesPathOnlyDefault() {
        val encoded = JsonInstant.encodeToString(Settings())

        assertTrue(encoded.contains("\"documentUploadInjectMode\":\"path_only\""))
    }

    @Test
    fun uploadInjectModeSurvivesSettingsJsonRoundTrip() {
        val original = Settings(
            displaySetting = DisplaySetting(
                documentUploadInjectMode = UploadInjectMode.FULL_BODY,
            ),
        )

        val encoded = JsonInstant.encodeToString(original)
        val decoded = JsonInstant.decodeFromString<Settings>(encoded)

        assertTrue(encoded.contains("\"documentUploadInjectMode\":\"full_body\""))
        assertEquals(
            UploadInjectMode.FULL_BODY,
            decoded.displaySetting.documentUploadInjectMode,
        )
    }
}
