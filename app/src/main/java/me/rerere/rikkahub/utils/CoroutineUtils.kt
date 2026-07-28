package me.rerere.rikkahub.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val TAG = "CoroutineUtils"

private const val MAX_COLLECT_RETRIES = 3L
private const val RETRY_BASE_DELAY_MS = 200L

/**
 * 收集上游 [Flow] 到一个 [MutableStateFlow]，失败时有限次退避重试。
 *
 * 重试耗尽后仍失败时的处理交给 [onRetriesExhausted]（默认仅记录日志、保留当前值继续运行）。
 * 调用方可据此区分「已有有效值 → 优雅降级保留」与「仍是初值 → 升级为崩溃」两种语义（见 #189）。
 * 回调收到失败原因与 StateFlow 的当前值；本函数不再自行吞掉异常语义决策。
 */
fun <T> Flow<T>.toMutableStateFlow(
    scope: CoroutineScope,
    initial: T,
    onRetriesExhausted: (cause: Throwable, current: T) -> Unit = { _, _ -> },
): MutableStateFlow<T> {
    val stateFlow = MutableStateFlow(initial)
    scope.launch {
        runCatching {
            this@toMutableStateFlow
                .retryWhen { cause, attempt ->
                    cause.printStackTrace()
                    Log.e(
                        TAG,
                        "Error while collecting flow (attempt ${attempt + 1}): ${cause.message}",
                        cause
                    )
                    val shouldRetry = attempt < MAX_COLLECT_RETRIES
                    if (shouldRetry) {
                        // 简单线性退避：200ms, 400ms, 600ms
                        delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                    }
                    shouldRetry
                }
                .collect { value ->
                    stateFlow.value = value
                }
        }.onFailure { cause ->
            cause.printStackTrace()
            Log.e(
                TAG,
                "Flow collection failed after retries (current=${stateFlow.value}): ${cause.message}",
                cause
            )
            onRetriesExhausted(cause, stateFlow.value)
        }
    }
    return stateFlow
}
