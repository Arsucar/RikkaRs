package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.ui.context.HorizontalGestureExclusionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class ChatPageGestureTest {
    @Test
    fun exclusionStateUsesReferenceCountingAndDoesNotUnderflow() {
        val state = HorizontalGestureExclusionState()
        val pointerId = 1L

        assertFalse(state.isActive)
        state.acquire(pointerId)
        state.acquire(pointerId)
        state.release(pointerId)
        assertTrue(state.isActive)
        assertTrue(state.isExcluded(pointerId))
        state.release(pointerId)
        state.release(pointerId)
        assertFalse(state.isActive)
        assertFalse(state.isExcluded(pointerId))
    }

    @Test
    fun exclusionIsScopedToThePointerThatStartedInsideTheTable() {
        val state = HorizontalGestureExclusionState()

        state.acquire(pointerId = 2L)

        assertTrue(state.isExcluded(pointerId = 2L))
        assertFalse(state.isExcluded(pointerId = 1L))
        assertTrue(
            shouldClaimRightDrawerGesture(
                totalX = -24f,
                totalY = 4f,
                touchSlop = 8f,
                drawersClosed = true,
                gestureExcluded = state.isExcluded(pointerId = 1L),
            )
        )
    }

    @Test
    fun onlyOverflowingContentAcquiresExclusionAndAlwaysReleasesIt() {
        val state = HorizontalGestureExclusionState()

        val nonOverflowLease = state.acquireIfScrollable(pointerId = 1L, maxScrollValue = 0)
        assertFalse(state.isActive)
        nonOverflowLease?.close()

        state.acquireIfScrollable(pointerId = 1L, maxScrollValue = 1).use {
            assertTrue(state.isActive)
        }
        assertFalse(state.isActive)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                val lease = state.acquireIfScrollable(pointerId = 1L, maxScrollValue = 1)
                try {
                    throw CancellationException("cancelled gesture")
                } finally {
                    lease?.close()
                }
            }
        }
        assertFalse(state.isActive)
    }

    @Test
    fun leftwardHorizontalDragClaimsRightDrawerOutsideExcludedContent() {
        assertTrue(
            shouldClaimRightDrawerGesture(
                totalX = -24f,
                totalY = 4f,
                touchSlop = 8f,
                drawersClosed = true,
                gestureExcluded = false,
            )
        )
    }

    @Test
    fun excludedGestureDoesNotClaimRightDrawer() {
        assertFalse(
            shouldClaimRightDrawerGesture(
                totalX = -24f,
                totalY = 4f,
                touchSlop = 8f,
                drawersClosed = true,
                gestureExcluded = true,
            )
        )
    }

    @Test
    fun rightwardVerticalOrOpenDrawerGesturesDoNotClaimRightDrawer() {
        assertFalse(
            shouldClaimRightDrawerGesture(
                totalX = -4f,
                totalY = 1f,
                touchSlop = 8f,
                drawersClosed = true,
                gestureExcluded = false,
            )
        )
        assertFalse(
            shouldClaimRightDrawerGesture(
                totalX = 24f,
                totalY = 4f,
                touchSlop = 8f,
                drawersClosed = true,
                gestureExcluded = false,
            )
        )
        assertFalse(
            shouldClaimRightDrawerGesture(
                totalX = -12f,
                totalY = 24f,
                touchSlop = 8f,
                drawersClosed = true,
                gestureExcluded = false,
            )
        )
        assertFalse(
            shouldClaimRightDrawerGesture(
                totalX = -24f,
                totalY = 4f,
                touchSlop = 8f,
                drawersClosed = false,
                gestureExcluded = false,
            )
        )
    }
}
