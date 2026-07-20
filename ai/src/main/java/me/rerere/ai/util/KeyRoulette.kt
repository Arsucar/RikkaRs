package me.rerere.ai.util

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.WeakHashMap

interface KeyRoulette {
    fun next(keys: String, providerId: String = ""): String

    companion object {
        fun default(): KeyRoulette = DefaultKeyRoulette()

        /**
         * LRU 轮询，持久化存储到 cacheDir/lru_key_roulette.json
         * 通过 providerId 区分同类型的多个 provider 实例，在 next() 调用时传入
         */
        fun lru(context: Context): KeyRoulette = lruKeyRoulette(File(context.cacheDir, LRU_CACHE_FILE))
    }
}

private val SPLIT_KEY_REGEX = "[\\s,]+".toRegex() // 空格换行和逗号

private fun splitKey(key: String): List<String> {
    return key
        .split(SPLIT_KEY_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private class DefaultKeyRoulette : KeyRoulette {
    override fun next(keys: String, providerId: String): String {
        val keyList = splitKey(keys)
        return if (keyList.isNotEmpty()) {
            keyList.random()
        } else {
            keys
        }
    }
}

private const val LRU_CACHE_FILE = "lru_key_roulette.json"
private const val EXPIRE_DURATION_MS = 24 * 60 * 60 * 1000L // 1 天

// 全局文件锁，防止多个 provider 实例并发读写同一文件
private object LruFileLock

// 文件结构: Map<providerId, Map<apiKey, lastUsedTimestamp>>
private typealias LruCache = Map<String, Map<String, Long>>
private val lruMemoryCache = WeakHashMap<File, LruCache>()

internal fun lruKeyRoulette(
    cacheFile: File,
    nowMillis: () -> Long = System::currentTimeMillis,
): KeyRoulette = LruKeyRoulette(cacheFile, nowMillis)

private class LruKeyRoulette(
    private val cacheFile: File,
    private val nowMillis: () -> Long,
) : KeyRoulette {

    override fun next(keys: String, providerId: String): String {
        val keyList = splitKey(keys)
        if (keyList.isEmpty()) return keys

        synchronized(LruFileLock) {
            val now = nowMillis()
            val allCache = loadCache().toMutableMap()

            // 取本 provider 的记录，过滤掉已过期条目和不在当前 key 列表中的条目
            val providerCache = (allCache[providerId] ?: emptyMap())
                .filter { (k, lastUsed) -> k in keyList && now - lastUsed < EXPIRE_DURATION_MS }
                .toMutableMap()

            // 优先选从未使用的 key，否则选最久未使用的
            val selected = keyList.firstOrNull { it !in providerCache }
                ?: providerCache.minByOrNull { it.value }!!.key

            providerCache[selected] = now
            allCache[providerId] = providerCache

            // 清理整个 provider 条目均已过期的记录
            allCache.entries.removeIf { (id, cache) ->
                id != providerId && cache.values.all { now - it >= EXPIRE_DURATION_MS }
            }

            saveCache(allCache)
            return selected
        }
    }

    private fun loadCache(): LruCache {
        lruMemoryCache[cacheFile]?.let { return it }

        return try {
            if (!cacheFile.exists()) emptyMap() else Json.decodeFromString<LruCache>(cacheFile.readText())
        } catch (_: Exception) {
            emptyMap<String, Map<String, Long>>()
        }.also { lruMemoryCache[cacheFile] = it }
    }

    private fun saveCache(cache: LruCache) {
        lruMemoryCache[cacheFile] = cache
        try {
            cacheFile.writeText(Json.encodeToString(cache))
        } catch (_: Exception) {
        }
    }
}
