package me.rerere.rikkahub.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceStorageMigratorTest {
    @Test
    fun verifyEqualMatchesFileCountAndSizes() {
        val root = File(System.getProperty("java.io.tmpdir"), "migrator-verify-${System.nanoTime()}")
        val source = File(root, "source")
        val destination = File(root, "dest")
        File(source, "a").apply {
            parentFile?.mkdirs()
            writeText("hello")
        }
        File(source, "nested/b.txt").apply {
            parentFile?.mkdirs()
            writeText("world")
        }
        WorkspaceStorageMigrator.copyTree(source, destination)
        assertTrue(WorkspaceStorageMigrator.verifyEqual(source, destination))
    }

    @Test
    fun verifyEqualFailsWhenSizesDiffer() {
        val root = File(System.getProperty("java.io.tmpdir"), "migrator-mismatch-${System.nanoTime()}")
        val source = File(root, "source")
        val destination = File(root, "dest")
        File(source, "a.txt").apply {
            parentFile?.mkdirs()
            writeText("short")
        }
        File(destination, "a.txt").apply {
            parentFile?.mkdirs()
            writeText("much-longer-content")
        }
        assertFalse(WorkspaceStorageMigrator.verifyEqual(source, destination))
    }
}