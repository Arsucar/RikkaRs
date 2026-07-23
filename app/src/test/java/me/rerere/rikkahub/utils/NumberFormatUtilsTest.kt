package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberFormatUtilsTest {

    @Test
    fun `formatNumber under 1000 is plain`() {
        assertEquals("0", 0.formatNumber())
        assertEquals("999", 999.formatNumber())
        assertEquals("-42", (-42).formatNumber())
    }

    @Test
    fun `formatNumber thousands`() {
        assertEquals("1K", 1000.formatNumber())
        assertEquals("1.5K", 1500.formatNumber())
        assertEquals("-2K", (-2000).formatNumber())
    }

    @Test
    fun `formatNumber millions and billions`() {
        assertEquals("1M", 1_000_000.formatNumber())
        assertEquals("2.5M", 2_500_000.formatNumber())
        assertEquals("1B", 1_000_000_000.formatNumber())
        assertEquals("-2.1B", Int.MIN_VALUE.formatNumber())
    }

    @Test
    fun `formatNumber uses a stable decimal separator`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5K", 1500.formatNumber())
            assertEquals("3.14", 3.14159.toFixed(2))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `toFixed rounds to given digits`() {
        assertEquals("1.5", 1.5f.toFixed(1))
        assertEquals("3.14", 3.14159.toFixed(2))
        assertEquals("10.0", 10.0.toFixed(1))
    }
}
