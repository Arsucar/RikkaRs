package me.rerere.ai.util

import java.util.Base64
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FileEncoderDataUrlTest {
    @Test
    fun `png data url preserves mime and bytes with or without prefix`() {
        assertDataUrlRoundTrip(
            mimeType = "image/png",
            bytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d,
            ),
        )
    }

    @Test
    fun `jpeg data url preserves mime and bytes with or without prefix`() {
        assertDataUrlRoundTrip(
            mimeType = "image/jpeg",
            bytes = byteArrayOf(
                0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(),
                0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
            ),
        )
    }

    private fun assertDataUrlRoundTrip(mimeType: String, bytes: ByteArray) {
        val rawBase64 = Base64.getEncoder().encodeToString(bytes)
        val dataUrl = "data:$mimeType;base64,$rawBase64"
        val image = UIMessagePart.Image(dataUrl)

        val prefixed = image.encodeBase64(withPrefix = true).getOrThrow()
        val unprefixed = image.encodeBase64(withPrefix = false).getOrThrow()

        assertEquals(mimeType, prefixed.mimeType)
        assertEquals(dataUrl, prefixed.base64)
        assertEquals(mimeType, unprefixed.mimeType)
        assertEquals(rawBase64, unprefixed.base64)
        assertArrayEquals(bytes, Base64.getDecoder().decode(unprefixed.base64))
    }
}
