package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTransformerPreviewTest {
    @Test
    fun previewCacheHitUsesOnlyInjectedReadLookup() {
        var lookupCalls = 0
        val result = OcrTransformer.performCachedOcr(UIMessagePart.Image("file:/image.png")) { url ->
            lookupCalls++
            assertEquals("file:/image.png", url)
            "cached OCR"
        }

        assertEquals("cached OCR", result)
        assertEquals(1, lookupCalls)
    }

    @Test
    fun previewCacheMissFailsClosedWithoutFallbackWork() {
        var lookupCalls = 0

        val error = assertThrows(PreviewSideEffectRequiredException::class.java) {
            OcrTransformer.performCachedOcr(UIMessagePart.Image("file:/missing.png")) {
                lookupCalls++
                null
            }
        }

        assertEquals(1, lookupCalls)
        assertTrue(error.message.orEmpty().contains("OCR cache is missing"))
    }
}
