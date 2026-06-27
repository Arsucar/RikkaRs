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
    val workspaceAccess: WorkspaceAccess = WorkspaceAccess.READ_ONLY,
    val workspaceApproval: WorkspaceApproval = WorkspaceApproval.INHERIT,
    val allowedPathPrefixes: List<String> = listOf("/workspace"),
    val canSpawn: Boolean = false,
    val streamOutput: Boolean = false,
    val inheritTools: Boolean = true,
    val excludedTools: Set<String> = emptySet(),
    val localTools: List<LocalToolOption> = emptyList(),
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

fun mergeSubagentProfiles(
    custom: List<SubagentProfile>,
    global: List<SubagentProfile> = emptyList(),
    disabledGlobal: Set<String> = emptySet(),
): List<SubagentProfile> {
    val byName = LinkedHashMap<String, SubagentProfile>()
    global
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