package me.rerere.ai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class KeyRouletteTest {
    @Test
    fun `lru cache rotates keys across instances without rereading the file`() {
        val directory = Files.createTempDirectory("key-roulette-test").toFile()
        try {
            val cacheFile = directory.resolve("lru.json")
            var now = 0L
            val firstInstance = lruKeyRoulette(cacheFile) { ++now }

            assertEquals("first", firstInstance.next("first, second", "provider"))
            assertTrue(cacheFile.exists())

            cacheFile.writeText("invalid json")
            val secondInstance = lruKeyRoulette(cacheFile) { ++now }

            assertEquals("second", secondInstance.next("first, second", "provider"))
            assertTrue(cacheFile.readText().startsWith("{"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `lru cache isolates providers`() {
        val directory = Files.createTempDirectory("key-roulette-provider-test").toFile()
        try {
            var now = 0L
            val roulette = lruKeyRoulette(directory.resolve("lru.json")) { ++now }

            assertEquals("first", roulette.next("first second", "provider-a"))
            assertEquals("first", roulette.next("first second", "provider-b"))
            assertEquals("second", roulette.next("first second", "provider-a"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `lru cache keeps fair rotation under concurrent access`() {
        val directory = Files.createTempDirectory("key-roulette-concurrency-test").toFile()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val now = AtomicLong()
            val roulette = lruKeyRoulette(directory.resolve("lru.json")) { now.incrementAndGet() }
            val selections = executor.invokeAll(
                List(100) {
                    Callable { roulette.next("first second", "provider") }
                },
            ).map { it.get() }

            assertEquals(50, selections.count { it == "first" })
            assertEquals(50, selections.count { it == "second" })
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }
}
