package me.rerere.rikkahub.data.model

/** Read-only destination for repairing a capability. */
enum class ToolDiagnosticTarget { NONE, WORKSPACES, SKILLS, MEMORY, MEMORY_TABLE, MCP, ASSISTANT_TOOLS }

/** Stable, UI-independent explanation of one capability decision. */
data class ToolDiagnosticStep(
    val code: ToolCapabilityReason,
    val passed: Boolean,
)

data class ToolCapabilityDiagnostic(
    val capability: ToolCapability,
    val primaryReason: ToolCapabilityReason,
    val reasonChain: List<ToolDiagnosticStep>,
    val repairTarget: ToolDiagnosticTarget,
)

data class ToolDiagnosticsSnapshot(
    val diagnostics: List<ToolCapabilityDiagnostic>,
    val sourceFailures: Map<ToolCapabilitySource, String> = emptyMap(),
) {
    /** Deliberately excludes runtime names, ids, URLs, headers, and configuration payloads. */
    fun copySummary(): String = buildString {
        append("tools=").append(diagnostics.size)
        append(";effective=").append(diagnostics.count { it.capability.effective })
        diagnostics.groupingBy { it.capability.source }.eachCount().toSortedMap().forEach { (source, count) ->
            append(';').append(source.name.lowercase()).append('=').append(count)
        }
        if (sourceFailures.isNotEmpty()) append(";source_failures=").append(sourceFailures.size)
    }
}

private fun ToolCapability.repairTarget(): ToolDiagnosticTarget = when (source) {
    ToolCapabilitySource.WORKSPACE -> ToolDiagnosticTarget.WORKSPACES
    ToolCapabilitySource.SKILL -> ToolDiagnosticTarget.SKILLS
    ToolCapabilitySource.MEMORY -> ToolDiagnosticTarget.MEMORY
    ToolCapabilitySource.MEMORY_TABLE -> ToolDiagnosticTarget.MEMORY_TABLE
    ToolCapabilitySource.MCP -> ToolDiagnosticTarget.MCP
    else -> ToolDiagnosticTarget.ASSISTANT_TOOLS
}

/** Builds a deterministic chain; no I/O or raw configuration is consulted. */
fun ToolCapability.diagnostic(): ToolCapabilityDiagnostic {
    val chain = listOf(
        ToolDiagnosticStep(reasonCode, reasonCode == ToolCapabilityReason.AVAILABLE),
        ToolDiagnosticStep(ToolCapabilityReason.DISABLED, configured),
        ToolDiagnosticStep(ToolCapabilityReason.UNAVAILABLE, available),
        ToolDiagnosticStep(ToolCapabilityReason.AVAILABLE, effective),
    )
    return ToolCapabilityDiagnostic(this, reasonCode, chain, repairTarget())
}

fun ToolCapabilitySnapshot.diagnostics(): ToolDiagnosticsSnapshot =
    ToolDiagnosticsSnapshot(capabilities.map { it.diagnostic() })

/** Collects independent sources. A broken source is represented, while other sources remain visible. */
fun collectToolDiagnostics(
    sources: Map<ToolCapabilitySource, () -> ToolCapabilitySnapshot>,
): ToolDiagnosticsSnapshot {
    val diagnostics = mutableListOf<ToolCapabilityDiagnostic>()
    val failures = linkedMapOf<ToolCapabilitySource, String>()
    sources.toSortedMap(compareBy { it.ordinal }).forEach { (source, provider) ->
        try {
            diagnostics += provider().capabilities
                .filter { it.source == source }
                .map { it.diagnostic() }
        } catch (error: Exception) {
            failures[source] = error::class.simpleName ?: "failure"
        }
    }
    return ToolDiagnosticsSnapshot(diagnostics, failures)
}
