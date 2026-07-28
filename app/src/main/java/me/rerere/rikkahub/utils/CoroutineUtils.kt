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

fun <T> Flow<T>.toMutableStateFlow(
    scope: CoroutineScope,
    initial: T
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
        }.onFailure {
            // 重试耗尽后仍失败：保留 StateFlow 当前/初始默认值，不终止进程。
            // 崩溃兜底交给既有 SafeModeActivity 常规流程处理。
            it.printStackTrace()
            Log.e(
                TAG,
                "Flow collection failed after retries, keeping current StateFlow value: ${it.message}",
                it
            )
        }
    }
    return stateFlow
}
