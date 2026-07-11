package me.rerere.common.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruCacheTest {
    @Test
    fun peekDoesNotRemoveExpiredBackingEntryOrPopulateMemory() {
        val store = RecordingStore(
            mutableMapOf("expired" to CacheEntry(value = "old", expiresAt = 0L)),
        )
        val cache = LruCache(
            capacity = 2,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = false,
        )

        assertNull(cache.peek("expired"))
        assertEquals(0, store.removeCalls)
        assertEquals(0, store.saveCalls)
        assertEquals(0, cache.size())
    }

    private class RecordingStore(
        private val entries: MutableMap<String, CacheEntry<String>>,
    ) : CacheStore<String, String> {
        var removeCalls = 0
        var saveCalls = 0

        override fun loadEntry(key: String): CacheEntry<String>? = entries[key]

        override fun saveEntry(key: String, entry: CacheEntry<String>) {
            saveCalls++
            entries[key] = entry
        }

        override fun remove(key: String) {
            removeCalls++
            entries.remove(key)
        }

        override fun clear() = entries.clear()

        override fun loadAllEntries(): Map<String, CacheEntry<String>> = entries.toMap()

        override fun keys(): Set<String> = entries.keys
    }
}
