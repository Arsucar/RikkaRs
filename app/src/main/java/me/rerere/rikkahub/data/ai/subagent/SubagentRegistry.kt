package me.rerere.rikkahub.data.ai.subagent

import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.model.Assistant

object SubagentRegistry {
    val BUILTIN_PROFILES: List<SubagentProfile> = listOf(
        SubagentProfile(
            name = "explore",
            displayName = "Explorer",
            description = "Explore and gather information autonomously. " +
                "Use for research, reading files, searching, and producing a factual summary.",
            systemPrompt = """
                You are an exploration subagent. Investigate the task using your tools, then return a concise factual summary.
                Do not ask the user questions; make reasonable assumptions and proceed.
            """.trimIndent(),
            maxSteps = 48,
            workspaceAccess = WorkspaceAccess.READ_ONLY,
            workspaceApproval = WorkspaceApproval.INHERIT,
            canSpawn = false,
            excludedTools = emptySet(),
        ),
        SubagentProfile(
            name = "coder",
            displayName = "Coder",
            description = "Execute a well-scoped coding or editing task autonomously and report results.",
            systemPrompt = """
                You are a coding subagent. Complete the assigned task using your tools, verify outcomes, and summarize changes.
                Do not ask the user questions; proceed with reasonable defaults.
            """.trimIndent(),
            maxSteps = 64,
            workspaceAccess = WorkspaceAccess.FULL,
            workspaceApproval = WorkspaceApproval.AUTO,
            canSpawn = true,
            excludedTools = emptySet(),
        ),
        SubagentProfile(
            name = "reviewer",
            displayName = "Reviewer",
            description = "Review or critique an artifact and return structured feedback without making changes.",
            systemPrompt = """
                You are a review subagent. Analyze the subject, use read-only tools if needed, and return structured feedback.
                Do not modify anything unless explicitly asked.
            """.trimIndent(),
            maxSteps = 24,
            workspaceAccess = WorkspaceAccess.READ_ONLY,
            workspaceApproval = WorkspaceApproval.INHERIT,
            canSpawn = false,
            excludedTools = setOf(
                "workspace_write_file",
                "workspace_edit_file",
                "workspace_shell",
            ),
        ),
    )

    private val builtinByName: Map<String, SubagentProfile> =
        BUILTIN_PROFILES.associateBy { it.name }

    fun resolveProfile(name: String, assistant: Assistant): SubagentProfile? {
        if (name in assistant.disabledBuiltinSubagents && name !in assistant.subagentProfiles.map { it.name }) {
            return null
        }
        assistant.subagentProfiles.firstOrNull { it.name == name }?.let { return it }
        if (name in assistant.disabledBuiltinSubagents) return null
        return builtinByName[name]
    }

    fun allProfiles(assistant: Assistant): List<SubagentProfile> =
        mergeSubagentProfiles(assistant.subagentProfiles, assistant.disabledBuiltinSubagents)
}