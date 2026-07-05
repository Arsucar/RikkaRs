package me.rerere.rikkahub.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilesStorageTest {
    @Test
    fun privateStorageSupportsGitPackWrites() {
        assertTrue(WorkspaceFilesStorage.PRIVATE.supportsWorkspaceGitPackWrites())
    }

    @Test
    fun externalStorageDoesNotClaimGitPackWriteSupport() {
        assertFalse(WorkspaceFilesStorage.EXTERNAL.supportsWorkspaceGitPackWrites())
    }
}
