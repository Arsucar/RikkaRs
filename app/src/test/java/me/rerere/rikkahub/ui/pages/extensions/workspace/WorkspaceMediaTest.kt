package me.rerere.rikkahub.ui.pages.extensions.workspace

import me.rerere.rikkahub.data.repository.requireWritableArea
import me.rerere.workspace.WorkspaceStorageArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceMediaTest {
    @Test
    fun `classifies text markdown image and other files`() {
        assertEquals(WorkspaceFileKind.TEXT, classifyWorkspaceFile("notes.txt"))
        assertEquals(WorkspaceFileKind.TEXT, classifyWorkspaceFile("README.MD"))
        assertEquals(WorkspaceFileKind.IMAGE, classifyWorkspaceFile("photo.JPEG?download=1#preview"))
        assertEquals(WorkspaceFileKind.OTHER, classifyWorkspaceFile("movie.mp4"))
    }

    @Test
    fun `infers known and fallback mime types`() {
        assertEquals("video/mp4", workspaceMimeType("MOVIE.MP4?download=1"))
        assertEquals("application/octet-stream", workspaceMimeType("archive.unknown_extension"))
    }

    @Test
    fun `linux area rejects write actions`() {
        requireWritableArea(WorkspaceStorageArea.FILES)
        assertThrows(IllegalArgumentException::class.java) {
            requireWritableArea(WorkspaceStorageArea.LINUX)
        }
    }
}
