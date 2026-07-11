package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFileUploadMetadataTest {
    @Test
    fun zipFileKeepsDetectedMimeType() {
        val metadata = resolveChatFileUploadMetadata(
            fileName = "archive.zip",
            mimeType = "application/zip",
        )

        assertEquals("archive.zip", metadata.fileName)
        assertEquals("application/zip", metadata.mimeType)
    }

    @Test
    fun apkFileKeepsDetectedMimeType() {
        val metadata = resolveChatFileUploadMetadata(
            fileName = "app.apk",
            mimeType = "application/vnd.android.package-archive",
        )

        assertEquals("app.apk", metadata.fileName)
        assertEquals("application/vnd.android.package-archive", metadata.mimeType)
    }

    @Test
    fun unknownMimeTypeFallsBackToBinary() {
        val metadata = resolveChatFileUploadMetadata(
            fileName = "payload.unknown",
            mimeType = null,
        )

        assertEquals("payload.unknown", metadata.fileName)
        assertEquals("application/octet-stream", metadata.mimeType)
    }

    @Test
    fun extensionlessFileWithBlankMimeTypeFallsBackToBinary() {
        val metadata = resolveChatFileUploadMetadata(
            fileName = "payload",
            mimeType = "",
        )

        assertEquals("payload", metadata.fileName)
        assertEquals("application/octet-stream", metadata.mimeType)
    }
}
