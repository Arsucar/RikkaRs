package me.rerere.common.js

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class HttpResponseDto(
    val status: Int,
    val ok: Boolean,
    val statusText: String,
    val body: String,
)

private fun isBlockedHost(host: String): Boolean {
    val normalized = host.trim().lowercase().removePrefix("[").removeSuffix("]")
    if (normalized == "localhost" || normalized == "0.0.0.0" || normalized.endsWith(".localhost")) {
        return true
    }
    return try {
        InetAddress.getAllByName(normalized).any { addr ->
            addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.hostAddress == "169.254.169.254"
        }
    } catch (_: Exception) {
        true
    }
}

private fun blockedFetchResponse(reason: String): String = json.encodeToString(
    HttpResponseDto(
        status = 403,
        ok = false,
        statusText = "Forbidden",
        body = reason,
    )
)

// fetch() returns a Response object synchronously (not a Promise)
// because this QuickJS wrapper doesn't support microtask scheduling.
private const val FETCH_POLYFILL = """
globalThis.fetch = function(url, options) {
    options = options || {};
    var method = (options.method || 'GET').toUpperCase();
    var headers = options.headers ? JSON.stringify(options.headers) : null;
    var body = options.body;
    if (typeof body === 'object' && body !== null) {
        body = JSON.stringify(body);
    } else if (typeof body !== 'string') {
        body = null;
    }

    var raw = __httpRequest(url, method, headers, body);
    var data = JSON.parse(raw);
    return {
        status: data.status,
        ok: data.ok,
        statusText: data.statusText,
        url: url,
        _body: data.body,
        text: function() { return this._body; },
        json: function() { return JSON.parse(this._body); }
    };
};
"""

fun QuickJSContext.injectFetch(httpClient: OkHttpClient) {
    globalObject.setProperty("__httpRequest", JSCallFunction { args ->
        val url = args[0] as? String ?: error("url is required")
        val method = (args[1] as? String ?: "GET").uppercase()
        val headersJson = args[2] as? String
        val body = args[3] as? String

        val httpUrl = url.toHttpUrlOrNull()
            ?: return@JSCallFunction blockedFetchResponse("Blocked: invalid URL")
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            return@JSCallFunction blockedFetchResponse("Blocked: only http/https allowed")
        }
        if (isBlockedHost(httpUrl.host)) {
            return@JSCallFunction blockedFetchResponse("Blocked: private or local network address")
        }

        val requestBuilder = Request.Builder().url(httpUrl)

        val parsedHeaders = if (!headersJson.isNullOrBlank() && headersJson != "null") {
            json.parseToJsonElement(headersJson).jsonObject
        } else null

        parsedHeaders?.entries?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.jsonPrimitive.content)
        }

        val contentType = try {
            parsedHeaders?.get("Content-Type")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }

        val mediaType = (contentType ?: "application/json").toMediaType()
        when (method) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            else -> {
                val reqBody = body?.toRequestBody(mediaType)
                    ?: if (method in setOf("POST", "PUT", "PATCH")) {
                        "".toRequestBody(mediaType)
                    } else {
                        null
                    }
                requestBuilder.method(method, reqBody)
            }
        }

        val safeClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        safeClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body.string()
            val code = response.code
            val message = response.message

            json.encodeToString(
                HttpResponseDto(
                    status = code,
                    ok = code in 200..299,
                    statusText = message,
                    body = responseBody,
                )
            )
        }
    })

    evaluate(FETCH_POLYFILL)
}
