package me.rerere.rikkahub.data.ai.clash

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashRetryTracerTest {

    private fun trace(seq: Int) = ClashRetryTrace(
        timestamp = seq.toLong(),
        requestHost = "host-$seq",
        responseCode = 429,
    )

    @Test
    fun recordsKeepsNewestWithinLimit() = runBlocking {
        val tracer = ClashRetryTracer()

        for (i in 1..25) {
            tracer.record(trace(i))
        }

        val snapshot = tracer.snapshot()
        assertEquals(20, snapshot.size)
        // 25 条只需保留最新 20，即序号 6..25（第 6 条为最早）
        assertEquals(6L, snapshot.first().timestamp)
        assertEquals("host-6", snapshot.first().requestHost)
        // 最新（第 25 条）在末端
        assertEquals(25L, snapshot.last().timestamp)
        assertEquals("host-25", snapshot.last().requestHost)
        // 全程升序，无乱序
        assertTrue(snapshot.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun clearEmptiesBuffer() = runBlocking {
        val tracer = ClashRetryTracer()

        for (i in 1..5) {
            tracer.record(trace(i))
        }
        assertEquals(5, tracer.traces.value.size)

        tracer.clear()

        assertTrue(tracer.traces.value.isEmpty())
        assertTrue(tracer.snapshot().isEmpty())
    }

    @Test
    fun concurrentRecordsRoundRobinPreservesAllWithinLimit() = runBlocking {
        val tracer = ClashRetryTracer()
        val totalCoroutines = 50
        val perCoroutine = 10

        coroutineScope {
            val writers = List(totalCoroutines) { c ->
                async {
                    repeat(perCoroutine) { k ->
                        // 每协程写入一个不重叠的唯一区间，避免 seq++ 在多线程下的竞态
                        tracer.record(trace(c * perCoroutine + k))
                    }
                }
            }
            writers.joinAll()
        }

        val snapshot = tracer.snapshot()
        assertEquals(20, snapshot.size)
        // 500 条并发录入只保留最新 20 条：去重后数量正确、无撕裂
        assertEquals(20, snapshot.map { it.timestamp }.toSet().size)
        // 全程升序，无撕裂/乱序
        assertTrue(snapshot.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
        // 保留的都是并发写入过的数据（在 0..499 区间内）
        assertTrue(
            snapshot.all {
                it.timestamp in 0L until (totalCoroutines * perCoroutine).toLong() &&
                    it.requestHost == "host-${it.timestamp}"
            }
        )
    }

    @Test
    fun recordUpdatesStateFlow() = runBlocking {
        val tracer = ClashRetryTracer()

        tracer.record(trace(1))
        tracer.record(trace(2))

        val value = tracer.traces.value
        assertEquals(2, value.size)
        // 最新在末端
        assertEquals(2L, value.last().timestamp)
        assertEquals("host-2", value.last().requestHost)
        assertEquals(1L, value.first().timestamp)
    }
}