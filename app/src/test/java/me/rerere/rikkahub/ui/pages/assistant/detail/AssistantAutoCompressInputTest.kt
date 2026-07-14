package me.rerere.rikkahub.ui.pages.assistant.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAutoCompressInputTest {
    @Test
    fun thresholdRequiresAsciiDigitsAndValueGreaterThanGlobalTarget() {
        listOf("", " ", "-1", "+1", "1.5", "12a", "８００１", "8000").forEach { input ->
            val parsed = parseAutoCompressThresholdInput(input, compressTargetTokens = 8_000)

            assertFalse(input, parsed.isValid)
            assertNull(input, parsed.value)
        }

        assertEquals(8_001, parseAutoCompressThresholdInput("8001", 8_000).value)
        assertEquals(8_001, parseAutoCompressThresholdInput("0008001", 8_000).value)
    }

    @Test
    fun thresholdRejectsZeroAndIntOverflowWithoutWrapping() {
        listOf("0", "2147483648", "99999999999999999999").forEach { input ->
            val parsed = parseAutoCompressThresholdInput(input, compressTargetTokens = 2_000)

            assertFalse(input, parsed.isValid)
            assertNull(input, parsed.value)
        }
        assertTrue(parseAutoCompressThresholdInput(Int.MAX_VALUE.toString(), Int.MAX_VALUE - 1).isValid)
        assertFalse(parseAutoCompressThresholdInput(Int.MAX_VALUE.toString(), Int.MAX_VALUE).isValid)
    }

    @Test
    fun keepRecentAcceptsZeroAndPositiveAsciiIntegers() {
        assertEquals(0, parseAutoCompressKeepRecentInput("0").value)
        assertEquals(32, parseAutoCompressKeepRecentInput("32").value)
        assertEquals(7, parseAutoCompressKeepRecentInput("0007").value)
    }

    @Test
    fun keepRecentRejectsInvalidAndOverflowDrafts() {
        listOf("", " ", "-1", "+1", "1.5", "12a", "３２", "2147483648").forEach { input ->
            val parsed = parseAutoCompressKeepRecentInput(input)

            assertFalse(input, parsed.isValid)
            assertNull(input, parsed.value)
        }
    }
}
