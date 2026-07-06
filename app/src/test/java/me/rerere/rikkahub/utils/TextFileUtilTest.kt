package me.rerere.rikkahub.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileUtilTest {
    @Test
    fun textLikeFileNameIncludesCommonDotfiles() {
        assertTrue(isTextLikeFileName(".gitignore"))
        assertTrue(isTextLikeFileName(".editorconfig"))
        assertTrue(isTextLikeFileName(".dockerignore"))
        assertTrue(isTextLikeFileName(".env.example"))
    }

    @Test
    fun textLikeFileNameIncludesCommonExtensionlessTextFiles() {
        assertTrue(isTextLikeFileName("README"))
        assertTrue(isTextLikeFileName("LICENSE"))
        assertTrue(isTextLikeFileName("Dockerfile"))
        assertTrue(isTextLikeFileName("Makefile"))
    }

    @Test
    fun textLikeFileNameDoesNotTreatUnknownExtensionlessNamesAsText() {
        assertFalse(isTextLikeFileName("app"))
        assertFalse(isTextLikeFileName("archive"))
    }

    @Test
    fun markdownFileNameRecognizesMarkdownConventions() {
        assertTrue(isMarkdownFileName("README.md"))
        assertTrue(isMarkdownFileName("guide.markdown"))
        assertTrue(isMarkdownFileName("README"))
        assertTrue(isTextLikeFileName("guide.markdown"))
        assertFalse(isMarkdownFileName(".gitignore"))
    }

    @Test
    fun markdownLikeFileRecognizesMarkdownMime() {
        assertTrue(isMarkdownLikeFile("attachment", "text/markdown"))
        assertTrue(isMarkdownLikeFile("attachment", "application/markdown; charset=utf-8"))
        assertFalse(isMarkdownLikeFile("attachment.txt", "text/plain"))
    }
}
