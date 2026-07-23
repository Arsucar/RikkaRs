package me.rerere.rikkahub.utils

import java.util.Locale

fun Number.toFixed(digits: Int = 0) = String.format(Locale.ROOT, "%.${digits}f", this)

fun Float.toFixed(digits: Int = 0) = String.format(Locale.ROOT, "%.${digits}f", this)

fun Double.toFixed(digits: Int = 0) = String.format(Locale.ROOT, "%.${digits}f", this)

fun Int.formatNumber(): String {
    // Convert before abs so Int.MIN_VALUE does not overflow back to a negative value.
    val absValue = kotlin.math.abs(toLong())
    val sign = if (this < 0) "-" else ""

    return when {
        absValue < 1000L -> this.toString()
        absValue < 1000000L -> {
            val value = absValue / 1000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}K"
            } else {
                "$sign${value.toFixed(1)}K"
            }
        }

        absValue < 1000000000L -> {
            val value = absValue / 1000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}M"
            } else {
                "$sign${value.toFixed(1)}M"
            }
        }

        else -> {
            val value = absValue / 1000000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}B"
            } else {
                "$sign${value.toFixed(1)}B"
            }
        }
    }
}
