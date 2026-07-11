package me.rerere.rikkahub.ui.pages.chat

import kotlin.math.abs

internal fun shouldClaimRightDrawerGesture(
    totalX: Float,
    totalY: Float,
    touchSlop: Float,
    drawersClosed: Boolean,
    gestureExcluded: Boolean,
): Boolean =
    (abs(totalX) > touchSlop || abs(totalY) > touchSlop) &&
        abs(totalX) > abs(totalY) &&
        totalX < 0f &&
        drawersClosed &&
        !gestureExcluded
