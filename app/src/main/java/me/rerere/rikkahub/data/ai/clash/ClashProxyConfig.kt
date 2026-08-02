package me.rerere.rikkahub.data.ai.clash

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
        val baseUrlError = validateApiBaseUrl(apiBaseUrl)
        if (baseUrlError != null) return baseUrlError
        return null
    }

    /**
     * 仅允许 loopback / 私网网段，避免 enable429IpRotation 触发对任意主机的 PUT（SSRF 类）。
     * 历史默认值 http://127.0.0.1:9090 与常见的 10.x 局域网部署均通过校验。
     * 主机必须是 IP 字面量（不触发 DNS，任意域名直接拒绝）或 "localhost"。
     */
    private fun validateApiBaseUrl(url: String): String? {
        val httpUrl = url.trim().toHttpUrlOrNull()
            ?: return "apiBaseUrl must be a valid http(s) URL"
        if (httpUrl.scheme !in setOf("http", "https")) {
            return "apiBaseUrl must use http or https scheme"
        }
        val host = httpUrl.host
        if (host.equals("localhost", ignoreCase = true)) return null
        val ip = host.toLiteralInetAddressOrNull()
            ?: return "apiBaseUrl host must be a loopback/private IP literal"
        return when {
            ip.isLoopbackAddress() -> null
            ip.isSiteLocalAddress() -> null
            else -> "apiBaseUrl only allows loopback/private addresses (127.0.0.1, ::1, 10.x, 172.16-31.x, 192.168.x)"
        }
    }
}

/**
 * 仅在字符串形如 IPv4 / IPv6 字面量时返回对应的 [java.net.InetAddress]，否则返回 null。
 * 不触发任何 DNS 查找：先做正则形态校验，通过后才交给 [java.net.InetAddress.getByName]，
 * 此时该参数必然是字面 IP（getByName 对字面量形式不会发起 DNS）。
 */
private fun String.toLiteralInetAddressOrNull(): java.net.InetAddress? {
    if (this.isEmpty()) return null
    val isIpv4Literal = IPV4_REGEX.matches(this)
    val isIpv6Literal = this.contains(':') && IPV6_LIKE_REGEX.matches(this)
    if (!isIpv4Literal && !isIpv6Literal) return null
    return try {
        java.net.InetAddress.getByName(this)
    } catch (_: java.net.UnknownHostException) {
        null
    } catch (_: Exception) {
        null
    }
}

private val IPV4_REGEX =
    Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

// 仅做形态过滤（含冒号 + 字符集宽松匹配），精确解析交给 InetAddress。
private val IPV6_LIKE_REGEX =
    Regex("""^[0-9a-fA-F:]+$""")
