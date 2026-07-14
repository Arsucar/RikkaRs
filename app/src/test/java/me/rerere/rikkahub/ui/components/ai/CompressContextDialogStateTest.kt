package me.rerere.rikkahub.ui.components.ai

import me.rerere.rikkahub.data.datastore.DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES
import me.rerere.rikkahub.data.datastore.DEFAULT_COMPRESS_TARGET_TOKENS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressContextDialogStateTest {
    @Test
    fun presetValuesInitializeAsSelectedPresets() {
        val tokens = initialCompressionNumberSelectorState(1_000, COMPRESS_TARGET_TOKEN_OPTIONS)
        val keepRecent = initialCompressionNumberSelectorState(16, COMPRESS_KEEP_RECENT_OPTIONS)

        assertEquals(1_000, tokens.selectedOption)
        assertEquals("", tokens.customText)
        assertFalse(tokens.isCustom)
        assertEquals(16, keepRecent.selectedOption)
        assertEquals("", keepRecent.customText)
        assertFalse(keepRecent.isCustom)
    }

    @Test
    fun nonPresetValuesInitializeAsExactCustomText() {
        val tokens = initialCompressionNumberSelectorState(3_333, COMPRESS_TARGET_TOKEN_OPTIONS)
        val keepRecent = initialCompressionNumberSelectorState(21, COMPRESS_KEEP_RECENT_OPTIONS)

        assertEquals(3_333, tokens.selectedOption)
        assertEquals("3333", tokens.customText)
        assertTrue(tokens.isCustom)
        assertEquals(21, keepRecent.selectedOption)
        assertEquals("21", keepRecent.customText)
        assertTrue(keepRecent.isCustom)
    }

    @Test
    fun resolverPreservesCustomParsingAndFallbackBehavior() {
        assertEquals(
            3_333,
            resolveCompressionNumber(
                isCustom = true,
                customText = "3333",
                selectedOption = 2_000,
                fallbackValue = DEFAULT_COMPRESS_TARGET_TOKENS,
            ),
        )
        assertEquals(
            DEFAULT_COMPRESS_TARGET_TOKENS,
            resolveCompressionNumber(
                isCustom = true,
                customText = "",
                selectedOption = 4_000,
                fallbackValue = DEFAULT_COMPRESS_TARGET_TOKENS,
            ),
        )
        assertEquals(
            DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES,
            resolveCompressionNumber(
                isCustom = true,
                customText = "not-a-number",
                selectedOption = 64,
                fallbackValue = DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES,
            ),
        )
        assertEquals(
            1_000,
            resolveCompressionNumber(
                isCustom = false,
                customText = "3333",
                selectedOption = 1_000,
                fallbackValue = DEFAULT_COMPRESS_TARGET_TOKENS,
            ),
        )
    }
}
