package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.staticCompositionLocalOf

internal class HorizontalGestureExclusionState {
    private val activePointers = mutableMapOf<Long, Int>()

    val isActive: Boolean
        get() = activePointers.isNotEmpty()

    fun isExcluded(pointerId: Long): Boolean = activePointers.containsKey(pointerId)

    fun acquire(pointerId: Long) {
        activePointers[pointerId] = activePointers.getOrDefault(pointerId, 0) + 1
    }

    fun acquireIfScrollable(pointerId: Long, maxScrollValue: Int): AutoCloseable? {
        if (maxScrollValue <= 0) return null
        acquire(pointerId)
        return HorizontalGestureExclusionLease(this, pointerId)
    }

    fun release(pointerId: Long) {
        val remaining = (activePointers[pointerId] ?: return) - 1
        if (remaining > 0) {
            activePointers[pointerId] = remaining
        } else {
            activePointers.remove(pointerId)
        }
    }
}

internal val LocalHorizontalGestureExclusionState =
    staticCompositionLocalOf<HorizontalGestureExclusionState?> { null }

private class HorizontalGestureExclusionLease(
    private val state: HorizontalGestureExclusionState,
    private val pointerId: Long,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            state.release(pointerId)
        }
    }
}
