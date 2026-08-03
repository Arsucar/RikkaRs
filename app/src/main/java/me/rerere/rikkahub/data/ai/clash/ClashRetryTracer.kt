package me.rerere.rikkahub.data.ai.clash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayDeque

/**
 * 内存中的 Clash 429 重试追踪器。
 *
 * 线程安全说明：
 * - OkHttp 网络线程并发写入（record/clear），UI 主线程读取（traces / snapshot）；
 * - 内部用 [Mutex] 保护 [deque] 的读写，保证数组操作原子、不被并发撕裂；
 * - 通过 [MutableStateFlow] 对外暴露不可变快照集合，保证 UI 读到的是一致的数据，
 *   不会在遍历过程中看到被并发修改的列表。
 *
 * @param limit 最多保留的重试记录条数，超过时丢弃最旧的记录（默认 20）
 */
class ClashRetryTracer(private val limit: Int = 20) {

    private val mutex = Mutex()

    private val deque = ArrayDeque<ClashRetryTrace>()

    private val _traces = MutableStateFlow<List<ClashRetryTrace>>(emptyList())

    /** 当前全部重试记录（按时间先后排序，最新在后）。 */
    val traces: StateFlow<List<ClashRetryTrace>> = _traces.asStateFlow()

    /**
     * 记录一条新 trace，超出 [limit] 上限时丢弃最旧记录。
     */
    suspend fun record(trace: ClashRetryTrace) {
        mutex.withLock {
            deque.addLast(trace)
            while (deque.size > limit) deque.removeFirst()
            _traces.value = deque.toList()
        }
    }

    /**
     * 清空全部记录。
     */
    suspend fun clear() {
        mutex.withLock {
            deque.clear()
            _traces.value = emptyList()
        }
    }

    /**
     * 返回当前记录快照（非挂起，供复制文本等场景直接读取）。
     */
    fun snapshot(): List<ClashRetryTrace> = traces.value
}