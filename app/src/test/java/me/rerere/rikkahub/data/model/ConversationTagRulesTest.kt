package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationTagRulesTest {
    @Test
    fun normalizeNameAppliesNfcWhitespaceCollapseAndStableCase() {
        val normalized = ConversationTagRules.normalizeName("  Cafe\u0301\t  REVIEW  ")

        assertEquals("Café REVIEW", normalized.displayName)
        assertEquals("café review", normalized.normalizedName)
    }

    @Test
    fun nameLimitCountsUnicodeCodePointsInsteadOfUtf16Units() {
        val fortyEmoji = "😀".repeat(ConversationTagRules.MAX_NAME_CODE_POINTS)
        assertEquals(fortyEmoji, ConversationTagRules.normalizeName(fortyEmoji).displayName)

        val error = assertThrows(ConversationTagException::class.java) {
            ConversationTagRules.normalizeName("😀".repeat(ConversationTagRules.MAX_NAME_CODE_POINTS + 1))
        }
        assertEquals(ConversationTagErrorCode.NAME_TOO_LONG, error.code)
    }

    @Test
    fun unknownColorIsRejected() {
        val error = assertThrows(ConversationTagException::class.java) {
            ConversationTagRules.requireValidColor("#ff00ff")
        }

        assertEquals(ConversationTagErrorCode.INVALID_COLOR, error.code)
    }
}
