package me.rerere.rikkahub.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFileTypeTest {
    @Test
    fun allowsSupportedDocumentMimeTypes() {
        assertTrue(isAllowedFileType("report.bin", "application/pdf"))
        assertTrue(isAllowedFileType("book.bin", "application/epub+zip"))
        assertTrue(
            isAllowedFileType(
                "document.bin",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
        )
    }

    @Test
    fun allowsAnyTextMimeAndKnownExtensionsCaseInsensitively() {
        assertTrue(isAllowedFileType("notes.unknown", "text/x-custom"))
        assertTrue(isAllowedFileType("README.MD", "application/octet-stream"))
        assertTrue(isAllowedFileType("build.KTS", "application/octet-stream"))
        assertTrue(isAllowedFileType("schema.graphql", "application/octet-stream"))
    }

    @Test
    fun rejectsMediaExecutablesArchivesAndUnknownBinary() {
        assertFalse(isAllowedFileType("animation.gif", "image/gif"))
        assertFalse(isAllowedFileType("movie.mp4", "video/mp4"))
        assertFalse(isAllowedFileType("application.apk", "application/vnd.android.package-archive"))
        assertFalse(isAllowedFileType("archive.zip", "application/zip"))
        assertFalse(isAllowedFileType("payload.bin", "application/octet-stream"))
        assertFalse(isAllowedFileType("payload", "application/octet-stream"))
    }
}
