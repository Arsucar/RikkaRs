package me.rerere.rikkahub.data.ai.subagent

import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.model.Assistant

object SubagentRegistry {
    val BUILTIN_PROFILES: List<SubagentProfile> = listOf(
        SubagentProfile(
            name = "explore",
            displayName = "Explorer",
            description = "Explore and gather information autonomously. " +
                "Use for research, reading files, searching, and producing a factual summary. " +
                "Best when the parent needs to collect context before deciding.",
            systemPrompt = """
                You are an exploration subagent. Your job is to autonomously investigate the task
                using the tools available to you, then return a concise but complete factual summary.
                Do not ask the user questions — make reasonable assumptions and proceed.
                Always end with a structured summary of your findings; do not leave the work unfinished.
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
            description = "Execute a well-scoped coding / editing task autonomously and report results. " +
                "Use for writing or modifying files, running shell commands, and verifying outcomes.",
            systemPrompt = """
                You are a coding subagent. Complete the assigned task autonomously using your tools.
                Make changes, verify them (e.g. by running commands), and report what you did and
                whether it succeeded. Return a concise summary of changes and verification results.
                Do not ask the user questions — proceed with reasonable defaults.
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
            description = "Review / critique an artifact or plan and return structured feedback. " +
                "Read-only oriented; does not make changes.",
            systemPrompt = """
                You are a review subagent. Analyze the subject described in the task, optionally use
                read-only tools to inspect it, and return structured feedback: strengths, issues,
                and concrete suggestions. Do not modify anything unless explicitly asked.
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

    /**
     * Effective global list for UI and runtime: persisted [global] entries win on name collision;
     * any builtin name missing from [global] is filled from [BUILTIN_PROFILES] (covers empty
     * DataStore, pre-migration, and profiles removed via [me.rerere.rikkahub.ui.pages.setting.SettingVM.deleteGlobalSubagent]).
     */
    internal fun effectiveGlobalProfiles(global: List<SubagentProfile>): List<SubagentProfile> {
        val byName = LinkedHashMap<String, SubagentProfile>()
        BUILTIN_PROFILES.forEach { byName[it.name] = it }
        global.forEach { byName[it.name] = it }
        return byName.values.toList()
    }

    fun resolveProfile(
        name: String,
        assistant: Assistant,
        globalProfiles: List<SubagentProfile> = emptyList(),
    ): SubagentProfile? {
        assistant.subagentProfiles.firstOrNull { it.name == name }?.let { local ->
            effectiveGlobalProfiles(globalProfiles)
                .firstOrNull { it.name == name }
                ?.let { global -> return local.mergeInheritedFrom(global) }
            return local
        }
        if (name in assistant.disabledGlobalSubagents) {
            return null
        }
        return effectiveGlobalProfiles(globalProfiles).firstOrNull { it.name == name }
    }

    fun allProfiles(
        assistant: Assistant,
        globalProfiles: List<SubagentProfile> = emptyList(),
    ): List<SubagentProfile> =
        mergeSubagentProfiles(
            custom = assistant.subagentProfiles,
            global = effectiveGlobalProfiles(globalProfiles),
            disabledGlobal = assistant.disabledGlobalSubagents,
        )
}