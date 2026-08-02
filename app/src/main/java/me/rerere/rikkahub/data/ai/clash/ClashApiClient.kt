package me.rerere.rikkahub.data.ai.clash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ClashNodeInfo(val name: String, val isSelectable: Boolean)

/**
 * Minimal client for Clash External Controller RESTful API (FlClash / Clash Verge).
 * GET /proxies -> list strategy groups & nodes; PUT /proxies/{group} -> switch node.
 * 3s timeout per request; failures throw so callers can skip and let original 429 pass.
 */
class ClashApiClient(
    private val json: Json,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private companion object {
        val mediaType = "application/json".toMediaType()
        val nonSelectableNodes = setOf("DIRECT", "REJECT", "REJECT-DROP", "COMPATIBLE", "PASS")
    }

    @Serializable
    private data class SwitchNodeBody(val name: String)

    /** Returns selectable node names of the given strategy group (excluding DIRECT/REJECT/...). */
    suspend fun getSelectableNodes(apiBaseUrl: String, groupName: String): List<String> =
        withContext(Dispatchers.IO) {
            val group = fetchGroup(apiBaseUrl, groupName)
            val all = group["all"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            all.filterNot { it in nonSelectableNodes }
        }

    /** Returns the currently selected node of the strategy group, or null when unknown. */
    suspend fun getCurrentNode(apiBaseUrl: String, groupName: String): String? =
        withContext(Dispatchers.IO) {
            fetchGroup(apiBaseUrl, groupName)["now"]?.jsonPrimitive?.contentOrNull
        }

    /** Switches the active node of the strategy group. Returns true when Clash accepted (200). */
    suspend fun switchNode(apiBaseUrl: String, groupName: String, nodeName: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = apiBaseUrl.trimEnd('/').toHttpUrl()
                .newBuilder()
                .addPathSegment("proxies")
                .addPathSegment(groupName)
                .build()
            val body = json.encodeToString(SwitchNodeBody.serializer(), SwitchNodeBody(nodeName))
            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody(mediaType))
                .build()
            val response = execute(request)
            try {
                val ok = response.isSuccessful
                if (!ok) {
                    val text = response.body?.string().orEmpty()
                    throw IllegalStateException("Clash PUT /proxies/$groupName -> ${response.code}: $text")
                }
                true
            } finally {
                response.close()
            }
        }

    private suspend fun fetchGroup(apiBaseUrl: String, groupName: String): kotlinx.serialization.json.JsonObject {
        val request = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/proxies")
            .get()
            .build()
        val response = execute(request)
        try {
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Clash GET /proxies -> ${response.code}: $text")
            val root = json.parseToJsonElement(text).jsonObject
            val proxies = root["proxies"]?.jsonObject
                ?: throw IllegalStateException("Clash /proxies missing 'proxies'")
            return proxies[groupName]?.jsonObject
                ?: throw IllegalStateException("Clash group '$groupName' not found")
        } finally {
            response.close()
        }
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = okHttpClient.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { call.cancel() } }
    }
}
