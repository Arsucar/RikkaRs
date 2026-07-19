package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.ai.mcp.McpStatus
import kotlin.uuid.Uuid

/** User-visible result categories for a tool connection. */
enum class ToolConnectionState {
    IDLE,
    CONNECTING,
    SUCCESS,
    EMPTY,
    NEEDS_AUTHORIZATION,
    NETWORK_ERROR,
    PROTOCOL_ERROR,
    ERROR,
}

data class ToolConnectionStatus(
    val state: ToolConnectionState,
    val toolCount: Int = 0,
    val message: String? = null,
    val revision: Long = 0L,
)

/** Keeps one result per server and rejects results produced for an obsolete config. */
class ToolConnectionStatusStore {
    private val revisions = mutableMapOf<Uuid, Long>()
    private val statuses = mutableMapOf<Uuid, ToolConnectionStatus>()

    @Synchronized
    fun begin(serverId: Uuid, revision: Long) {
        revisions[serverId] = revision
        statuses[serverId] = ToolConnectionStatus(ToolConnectionState.CONNECTING, revision = revision)
    }

    @Synchronized
    fun publish(serverId: Uuid, status: ToolConnectionStatus): Boolean {
        if (revisions[serverId] != status.revision) return false
        statuses[serverId] = status
        return true
    }

    @Synchronized
    fun remove(serverId: Uuid) {
        revisions.remove(serverId)
        statuses.remove(serverId)
    }

    @Synchronized
    fun snapshot(): Map<Uuid, ToolConnectionStatus> = statuses.toMap()
}

fun workspaceConnectionStatus(shellStatus: String): ToolConnectionStatus = when (normalizeWorkspaceShellStatus(shellStatus)) {
    NormalizedWorkspaceShellStatus.READY -> ToolConnectionStatus(ToolConnectionState.SUCCESS)
    NormalizedWorkspaceShellStatus.INSTALLING -> ToolConnectionStatus(ToolConnectionState.CONNECTING)
    NormalizedWorkspaceShellStatus.DISABLED -> ToolConnectionStatus(ToolConnectionState.IDLE)
    NormalizedWorkspaceShellStatus.BROKEN -> ToolConnectionStatus(ToolConnectionState.ERROR)
    NormalizedWorkspaceShellStatus.UNKNOWN -> ToolConnectionStatus(ToolConnectionState.ERROR)
}

fun aggregateToolConnectionStatus(statuses: Collection<ToolConnectionStatus>): ToolConnectionStatus {
    if (statuses.isEmpty()) return ToolConnectionStatus(ToolConnectionState.IDLE)
    val priority = listOf(
        ToolConnectionState.NEEDS_AUTHORIZATION,
        ToolConnectionState.NETWORK_ERROR,
        ToolConnectionState.PROTOCOL_ERROR,
        ToolConnectionState.ERROR,
        ToolConnectionState.CONNECTING,
        ToolConnectionState.SUCCESS,
        ToolConnectionState.EMPTY,
        ToolConnectionState.IDLE,
    )
    val state = priority.first { candidate -> statuses.any { it.state == candidate } }
    return ToolConnectionStatus(state, toolCount = statuses.sumOf { it.toolCount })
}

fun McpStatus.toToolConnectionStatus(toolCount: Int = 0, revision: Long = 0L): ToolConnectionStatus = when (this) {
    McpStatus.Idle -> ToolConnectionStatus(ToolConnectionState.IDLE, revision = revision)
    McpStatus.Connecting, is McpStatus.Reconnecting, McpStatus.Authorizing ->
        ToolConnectionStatus(ToolConnectionState.CONNECTING, revision = revision)
    McpStatus.Connected -> ToolConnectionStatus(
        if (toolCount == 0) ToolConnectionState.EMPTY else ToolConnectionState.SUCCESS,
        toolCount = toolCount,
        revision = revision,
    )
    McpStatus.NeedsAuthorization -> ToolConnectionStatus(ToolConnectionState.NEEDS_AUTHORIZATION, revision = revision)
    is McpStatus.Error -> ToolConnectionStatus(
        state = if (this.message.contains("timeout", true) || this.message.contains("network", true))
            ToolConnectionState.NETWORK_ERROR else ToolConnectionState.PROTOCOL_ERROR,
        message = this.message.redactConnectionSecrets(),
        revision = revision,
    )
}

private fun String.redactConnectionSecrets(): String = replace(Regex("(?i)(authorization|token|secret|password)=?[^ ,;]+"), "$1=[redacted]")
