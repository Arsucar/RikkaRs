package me.rerere.rikkahub.data.ai.mcp

/** The probe surface deliberately has no callTool operation. */
internal suspend fun runSafeMcpProbe(
    connect: suspend () -> Unit,
    listToolNames: suspend () -> List<String>,
    close: suspend () -> Unit,
    enabledToolNames: Set<String>? = null,
): Int = try {
    connect()
    val discovered = listToolNames()
    if (enabledToolNames == null) discovered.size else discovered.count { it in enabledToolNames }
} finally {
    runCatching { close() }
}
