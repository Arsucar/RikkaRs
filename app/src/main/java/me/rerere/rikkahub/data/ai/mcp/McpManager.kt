package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.StringValues
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.oauth.OAuthLoopbackCallbackServer
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ToolConnectionState
import me.rerere.rikkahub.data.model.ToolConnectionStatus
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private fun String.redactMcpSecrets(): String = replace(
    Regex("(?i)(authorization|token|secret|password|cookie)(\\s*[:=]\\s*)([^\\s,;}&]+)"),
    "$1$2[redacted]",
).replace(
    Regex("(?i)([?&](?:token|secret|password|key|sig)=)([^&\\s]+)"),
    "$1[redacted]",
).replace(Regex("https?://[^\\s,;]+", RegexOption.IGNORE_CASE), "[redacted-url]")

/**
 * MCP 子系统的公共入口。
 *
 * 这里仅协调配置、OAuth、连接注册表与 UI 内容转换；单个服务器的连接状态机由
 * [McpSessionRegistry] 管理，OAuth 协议细节由 [McpOAuthCoordinator] 管理。
 *
 * Fork 保留：按助手过滤工具、仅 Connected 服务器可注入、只读 [testConnection] 探测。
 */
class McpManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val filesManager: FilesManager,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .build()

    private val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(SSE)
    }

    private val statusStore = McpStatusStore()
    private val oauthCallbackServer = OAuthLoopbackCallbackServer(
        port = MCP_OAUTH_CALLBACK_PORT,
        callbackPath = MCP_OAUTH_CALLBACK_PATH,
    )
    private val oauthCoordinator = McpOAuthCoordinator(
        settingsStore = settingsStore,
        appScope = appScope,
        oauthClient = OAuthHttpClient(okHttpClient),
        discoveryClient = McpOAuthDiscoveryClient(okHttpClient),
        callbackServer = oauthCallbackServer,
        authorizationLauncher = CustomTabsOAuthAuthorizationLauncher,
        updateStatus = statusStore::update,
    )
    private val sessionRegistry = McpSessionRegistry(
        settingsStore = settingsStore,
        appScope = appScope,
        httpClient = httpClient,
        oauthCoordinator = oauthCoordinator,
        statusStore = statusStore,
    )

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.mcpServers }
                .distinctUntilChanged()
                .collect(sessionRegistry::reconcile)
        }
    }

    val syncingStatus: StateFlow<Map<Uuid, McpStatus>>
        get() = statusStore.status

    fun getClient(config: McpServerConfig): Client? = sessionRegistry.getClient(config.id)

    fun getStatus(config: McpServerConfig): Flow<McpStatus> = sessionRegistry.getStatus(config.id)

    /**
     * Explicit, read-only connectivity probe. It performs initialize/authentication and
     * listTools only; it never invokes a business tool and does not update settings or the
     * long-lived client map.
     */
    suspend fun testConnection(config: McpServerConfig, revision: Long = 0L): ToolConnectionStatus =
        withContext(Dispatchers.IO) {
            val probe = Client(clientInfo = Implementation(name = config.commonOptions.name, version = "1.0"))
            try {
                val enabledNames = config.commonOptions.tools.filter { it.enable }.map { it.name }.toSet()
                val count = withTimeout(30.seconds) {
                    runSafeMcpProbe(
                        connect = { probe.connect(createProbeTransport(config)) },
                        listToolNames = { probe.listTools().tools.map { it.name } },
                        close = { probe.close() },
                        enabledToolNames = enabledNames.takeUnless { config.commonOptions.tools.isEmpty() },
                    )
                }
                ToolConnectionStatus(
                    state = if (count == 0) ToolConnectionState.EMPTY else ToolConnectionState.SUCCESS,
                    toolCount = count,
                    revision = revision,
                    checkedAtEpochMillis = System.currentTimeMillis(),
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val status = if (oauthCoordinator.needsAuthorization(config, e)) {
                    ToolConnectionState.NEEDS_AUTHORIZATION
                } else if (e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
                    e is java.io.IOException || e.message.orEmpty().contains("timeout", true)
                ) {
                    ToolConnectionState.NETWORK_ERROR
                } else {
                    ToolConnectionState.PROTOCOL_ERROR
                }
                ToolConnectionStatus(
                    status,
                    message = e.message?.redactMcpSecrets(),
                    revision = revision,
                    checkedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }

    /**
     * 返回当前助手可用的 MCP 工具。
     *
     * - 按 [assistant] 的 mcpServers 绑定过滤（默认当前全局助手）
     * - 仅注入状态为 [McpStatus.Connected] 的服务器（fork 修复：避免未就绪工具进入生成）
     */
    fun getAllAvailableTools(
        assistant: Assistant = settingsStore.settingsFlow.value.getCurrentAssistant(),
    ): List<Triple<Uuid, String, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        val connected = syncingStatus.value
        return settings.mcpServers
            .filter {
                it.commonOptions.enable &&
                    it.id in assistant.mcpServers &&
                    connected[it.id] == McpStatus.Connected
            }
            .flatMap { server ->
                server.commonOptions.tools
                    .filter { tool -> tool.enable }
                    .map { tool -> Triple(server.id, server.commonOptions.name, tool) }
            }
    }

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> {
        val result = try {
            sessionRegistry.callTool(serverId, toolName, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: McpClientUnavailableException) {
            return listOf(UIMessagePart.Text("Failed to execute MCP tool: ${e.message ?: e.javaClass.name}"))
        }
        return result.content.map { content ->
            when (content) {
                is TextContent -> UIMessagePart.Text(content.text)
                is ImageContent -> convertImageContentToFilePart(content)
                else -> UIMessagePart.Text(JsonInstant.encodeToString(content))
            }
        }
    }

    suspend fun addClient(config: McpServerConfig) = sessionRegistry.addClient(config)

    suspend fun removeClient(config: McpServerConfig) = sessionRegistry.removeClient(config)

    suspend fun syncAll() = sessionRegistry.syncAll()

    fun startAuthorization(config: McpServerConfig, context: Context) {
        oauthCoordinator.startAuthorization(config, context)
    }

    fun cancelAuthorization(config: McpServerConfig) {
        oauthCoordinator.cancelAuthorization(config.id)
    }

    suspend fun clearAuthorization(config: McpServerConfig) {
        val freshConfig = oauthCoordinator.clearAuthorization(config)
        sessionRegistry.addClient(freshConfig)
    }

    private suspend fun convertImageContentToFilePart(image: ImageContent): UIMessagePart.Image {
        val bytes = Base64.decode(image.data)
        val extension = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(image.mimeType) ?: "bin"
        val entity = filesManager.saveUploadFromBytes(
            bytes = bytes,
            displayName = "mcp_image.$extension",
            mimeType = image.mimeType,
        )
        return UIMessagePart.Image(url = filesManager.getFile(entity).toUri().toString())
    }

    /** Probe-only transport; mirrors session registry headers without touching long-lived clients. */
    private fun createProbeTransport(config: McpServerConfig): AbstractTransport = when (config) {
        is McpServerConfig.SseTransportServer -> SseClientTransport(
            urlString = config.url,
            client = httpClient,
            requestBuilder = { appendResolvedHeaders(config) },
        )

        is McpServerConfig.StreamableHTTPServer -> StreamableHttpClientTransport(
            url = config.url,
            client = httpClient,
            requestBuilder = { appendResolvedHeaders(config) },
        )
    }

    private fun HttpRequestBuilder.appendResolvedHeaders(config: McpServerConfig) {
        headers.appendAll(StringValues.build {
            config.probeResolvedHeaders().forEach { (name, value) -> append(name, value) }
        })
    }
}

private fun McpServerConfig.probeResolvedHeaders(): List<Pair<String, String>> {
    val base = commonOptions.headers
    val token = commonOptions.oauth?.takeIf { it.enabled }?.accessToken
    val hasAuthorization = base.any { it.first.equals("Authorization", ignoreCase = true) }
    return if (!token.isNullOrBlank() && !hasAuthorization) {
        base + ("Authorization" to "Bearer $token")
    } else {
        base
    }
}
