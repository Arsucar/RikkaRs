package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.WorkspaceToolDefaultApprovals
import kotlin.uuid.Uuid

@Serializable
enum class WorkspaceAccess {
    NONE,
    READ_ONLY,
    FULL,
}

@Serializable
enum class WorkspaceApproval {
    INHERIT,
    AUTO,
    OVERRIDE,
}

val WorkspaceAccessAllowedTools: Map<WorkspaceAccess, Set<String>> = mapOf(
    WorkspaceAccess.NONE to emptySet(),
    WorkspaceAccess.READ_ONLY to setOf(
        "workspace_read_file",
        "workspace_shell",
    ),
    WorkspaceAccess.FULL to setOf(
        "workspace_read_file",
        "workspace_write_file",
        "workspace_edit_file",
        "workspace_shell",
    ),
)

fun workspaceToolAllowed(access: WorkspaceAccess, toolName: String): Boolean =
    toolName in (WorkspaceAccessAllowedTools[access] ?: emptySet())

fun resolveSubagentWorkspaceApproval(
    profileApproval: WorkspaceApproval,
    workspaceOverrides: Map<String, Boolean>,
    toolName: String,
    profileOverrides: Map<String, Boolean> = emptyMap(),
): Boolean = when (profileApproval) {
    WorkspaceApproval.AUTO -> false
    WorkspaceApproval.OVERRIDE -> profileOverrides[toolName]
        ?: workspaceOverrides[toolName]
        ?: WorkspaceToolDefaultApprovals[toolName]
        ?: false
    WorkspaceApproval.INHERIT -> workspaceOverrides[toolName]
        ?: WorkspaceToolDefaultApprovals[toolName]
        ?: false
}

@Serializable
data class SubagentProfile(
    val name: String,
    val displayName: String = name,
    val description: String = "",
    val systemPrompt: String = "",
    val chatModelId: Uuid? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxSteps: Int = 32,
    val maxToolCalls: Int? = null,
    val workspaceAccess: WorkspaceAccess = WorkspaceAccess.READ_ONLY,
    val workspaceApproval: WorkspaceApproval = WorkspaceApproval.INHERIT,
    val allowedPathPrefixes: List<String> = listOf("/workspace"),
    val canSpawn: Boolean = false,
    val streamOutput: Boolean = false,
    val inheritTools: Boolean = true,
    val excludedTools: Set<String> = emptySet(),
    val localTools: List<LocalToolOption> = emptyList(),
    val extraLocalTools: List<LocalToolOption> = emptyList(),
    val enabledSkills: Set<String> = emptySet(),
    val mcpServerIds: Set<Uuid> = emptySet(),
    val toolApprovalOverrides: Map<String, Boolean> = emptyMap(),
    val enableMemory: Boolean = false,
    val summaryMinLength: Int = 200,
    val summaryContinuationAttempts: Int = 1,
) {
    init {
        require(name.isNotBlank()) { "Subagent profile name must not be blank" }
        require(name.matches(IdentifierRegex)) {
            "Subagent profile name must be lowercase letters/digits/underscore: $name"
        }
    }

    companion object {
        val IdentifierRegex = Regex("^[a-z][a-z0-9_]*$")
    }
}

@Serializable
data class SubagentResult(
    @SerialName("profile_name") val profileName: String,
    @SerialName("summary") val summary: String,
    @SerialName("succeeded") val succeeded: Boolean,
    @SerialName("error") val error: String? = null,
    @SerialName("depth") val depth: Int = 0,
    @SerialName("usage") val usage: TokenUsage? = null,
    @SerialName("steps") val steps: Int = 0,
    /// 子代理本轮调用的工具总数（便于父代理审计"它是否真干了活"，区别于 generation 轮次 steps）。
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    /// 实际工具循环步数（受 maxSteps 控制），每步 = 一次 LLM 调用 + 工具执行。
    @SerialName("tool_loop_steps") val toolLoopSteps: Int = 0,
    @SerialName("truncated") val truncated: Boolean = false,
    @SerialName("transcript") val transcript: List<SubagentTranscriptStep> = emptyList(),
)

@Serializable
sealed interface SubagentTranscriptStep {
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val text: String,
        val createdAt: Long? = null,
    ) : SubagentTranscriptStep

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val toolName: String,
        val input: String,
        val output: String,
        val executed: Boolean = true,
    ) : SubagentTranscriptStep

    @Serializable
    @SerialName("text")
    data class Text(
        val content: String,
    ) : SubagentTranscriptStep
}

/**
 * Merges assistant-local custom profiles over global profiles (custom wins on name collision).
 * [global] is usually [Settings.globalSubagentProfiles]; when empty, builtins are used as fallback
 * via [SubagentRegistry.effectiveGlobalProfiles].
 */
fun mergeSubagentProfiles(
    custom: List<SubagentProfile>,
    global: List<SubagentProfile> = emptyList(),
    disabledGlobal: Set<String> = emptySet(),
): List<SubagentProfile> {
    val byName = LinkedHashMap<String, SubagentProfile>()
    SubagentRegistry.effectiveGlobalProfiles(global)
        .filter { it.name !in disabledGlobal }
        .forEach { byName[it.name] = it }
    custom.forEach { byName[it.name] = it }
    return byName.values.toList()
}

fun upsertSubagentProfile(
    custom: List<SubagentProfile>,
    profile: SubagentProfile,
): List<SubagentProfile> {
    val exists = custom.any { it.name == profile.name }
    return if (exists) {
        custom.map { if (it.name == profile.name) profile else it }
    } else {
        custom + profile
    }
}

fun removeSubagentProfile(
    custom: List<SubagentProfile>,
    name: String,
): List<SubagentProfile> = custom.filterNot { it.name == name }

fun SubagentProfile.toggleSkill(skillName: String, enabled: Boolean): SubagentProfile =
    copy(enabledSkills = if (enabled) enabledSkills + skillName else enabledSkills - skillName)

fun SubagentProfile.withLocalToolOptions(
    options: List<LocalToolOption>,
    extra: Boolean,
): SubagentProfile =
    if (extra) copy(extraLocalTools = options) else copy(localTools = options)

/**
 * Fills fields that are still at [SubagentProfile] defaults from [base] (typically a global/builtin profile).
 * Used when a sparse assistant-local override is stored without copying inherited metadata.
 */
internal fun SubagentProfile.mergeInheritedFrom(base: SubagentProfile): SubagentProfile {
    if (name != base.name) return this
    return copy(
        displayName = displayName.takeIf { it.isNotBlank() && it != name } ?: base.displayName,
        description = description.ifBlank { base.description },
        systemPrompt = systemPrompt.ifBlank { base.systemPrompt },
    )
}