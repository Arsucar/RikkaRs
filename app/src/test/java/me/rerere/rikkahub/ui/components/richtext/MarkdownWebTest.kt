package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownWebTest {
    @Test
    fun `template loader caches each asset key`() {
        val assetKey = Any()
        var loads = 0

        val first = loadCachedMarkdownTemplate(assetKey) {
            loads++
            "template"
        }
        val second = loadCachedMarkdownTemplate(assetKey) {
            loads++
            "unexpected"
        }

        assertEquals("template", first)
        assertEquals("template", second)
        assertEquals(1, loads)
    }

    @Test
    fun `template loader isolates different asset keys`() {
        var loads = 0

        val first = loadCachedMarkdownTemplate(Any()) {
            loads++
            "first"
        }
        val second = loadCachedMarkdownTemplate(Any()) {
            loads++
            "second"
        }

        assertEquals("first", first)
        assertEquals("second", second)
        assertEquals(2, loads)
    }
}
