package me.rerere.rikkahub.data.ai.clash

import kotlinx.serialization.Serializable

@Serializable
data class ClashProxyConfig(
    val apiBaseUrl: String = "http://127.0.0.1:9090",
    val groupName: String = "GLOBAL",
    val maxRetries: Int = 2,
    val switchDelayMs: Long = 500,
) {
    fun validate(): String? {
        if (maxRetries !in 1..5) return "maxRetries must be in 1..5"
        if (switchDelayMs !in 100..2000) return "switchDelayMs must be in 100..2000"
        return null
    }
}