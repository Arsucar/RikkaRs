package me.rerere.rikkahub.data.files

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePathSecurityTest {
    @Test
    fun resolveContainedFileRejectsArchiveTraversal() {
        val root = Files.createTempDirectory("backup-path-test").toFile()

        assertEquals(root.resolve("nested/file.bin").canonicalFile, resolveContainedFile(root, "nested/file.bin"))
        assertNull(resolveContainedFile(root, "../outside.bin"))
        assertNull(resolveContainedFile(root, "/absolute.bin"))
        assertNull(resolveContainedFile(root, "nested\\outside.bin"))
    }
}
