package me.rerere.rikkahub.data.ai.subagent

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import kotlin.uuid.Uuid

/**
 * Subagent 配置档 —— 移植自 kimi-code 的 ResolvedAgentProfile / DEFAULT_AGENT_PROFILES.subagents。
 *
 * 一个 SubagentProfile 描述了一类"子代理"的行为：它拥有独立的系统提示词、
 * 可独立配置的模型 / 温度 / 最大步数，并可选择继承父代理的工具集（子集）。
 * 父代理通过 `spawn_subagent` 工具按 profile 名称委派任务，子代理自主运行
 * 完整的 generation 循环后，把最终摘要作为工具结果返回给父代理。
 *
 * @param name            唯一标识名（小写英文，用于工具参数引用）
 * @param displayName     展示名称
 * @param description     向父代理描述该子代理擅长什么（会注入到 spawn_subagent 工具说明中）
 * @param systemPrompt    子代理系统提示词
 * @param chatModelId     指定模型；为 null 时继承父代理当前模型
 * @param temperature     采样温度；为 null 时继承父代理
 * @param topP            top_p；为 null 时继承父代理
 * @param maxTokens       单轮最大 token；为 null 时继承父代理
 * @param reasoningLevel  推理强度；默认 AUTO
 * @param maxSteps        子代理最大工具调用轮次（防止无限循环）
 * @param inheritTools    是否继承父代理工具集
 * @param excludedTools   继承时要排除的工具名（例如避免递归委派）
 * @param extraLocalTools 额外注入的本地工具选项
 * @param enableMemory    是否启用记忆（子代理通常独立、无记忆）
 * @param summaryMinLength 摘要短于该长度时触发一次"扩写"追问（对应 kimi-code SUMMARY_MIN_LENGTH）
 * @param summaryContinuationAttempts 扩写追问最大次数
 * @param streamOutput    是否流式输出（子代理默认不流式，结果一次性返回）
 */
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
    val inheritTools: Boolean = true,
    val localTools: List<LocalToolOption> = emptyList(),
    val enabledSkills: Set<String> = emptySet(),
    val mcpServerIds: Set<Uuid> = emptySet(),
    val excludedTools: Set<String> = emptySet(),
    val extraLocalTools: List<LocalToolOption> = emptyList(),
    val enableMemory: Boolean = false,
    val summaryMinLength: Int = DEFAULT_SUMMARY_MIN_LENGTH,
    val summaryContinuationAttempts: Int = DEFAULT_SUMMARY_CONTINUATION_ATTEMPTS,
    val streamOutput: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Subagent profile name must not be blank" }
        require(name.matches(IdentifierRegex)) {
            "Subagent profile name must be lowercase letters/digits/underscore: $name"
        }
    }

    companion object {
        val IdentifierRegex = Regex("^[a-z][a-z0-9_]*$")

        /** 摘要过短时触发扩写的最小长度。设为 0 = 永不追问（continuation 浪费一整轮 LLM 调用） */
        const val DEFAULT_SUMMARY_MIN_LENGTH = 0

        /** 扩写追问最大次数。设为 0 = 不追问 */
        const val DEFAULT_SUMMARY_CONTINUATION_ATTEMPTS = 0

        /**
         * 内置默认 subagent 配置档，移植自 kimi-code 的 DEFAULT_AGENT_PROFILES.agent.subagents。
         * 用户可在 Assistant 上自定义覆盖 / 追加。
         */
        val BUILTIN: List<SubagentProfile> = listOf(
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
                maxSteps = 16,
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
                maxSteps = 20,
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
                inheritTools = true,
                excludedTools = setOf(
                    "workspace_write_file", "workspace_edit_file", "workspace_shell",
                ),
                maxSteps = 12,
            ),
        )
    }
}

/**
 * 把用户自定义的 profile 与内置 profile 合并（用户同名覆盖内置）。
 *
 * @param disabledBuiltin 被显式禁用的内置 profile 名集合。出现在其中的内置 profile
 *   不会被合并进来（实现「完全删除内置子代理」——删除内置 profile 时将其名加入此集合，
 *   而非仅从 custom 移除，否则 BUILTIN 会被重新加入）。自定义 profile 不受此参数影响。
 */
fun mergeSubagentProfiles(
    custom: List<SubagentProfile>,
    disabledBuiltin: Set<String> = emptySet(),
): List<SubagentProfile> {
    val byName = LinkedHashMap<String, SubagentProfile>()
    SubagentProfile.BUILTIN
        .filter { it.name !in disabledBuiltin }
        .forEach { byName[it.name] = it }
    custom.forEach { byName[it.name] = it }
    return byName.values.toList()
}

/**
 * 把一个 profile upsert 到自定义列表（按 name 去重替换）。
 * 用于 UI 编辑：编辑内置 profile 时，把修改后的副本写入 subagentProfiles，
 * mergeSubagentProfiles 会让它覆盖同名内置项。
 */
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

/**
 * 按 name 删除一个自定义 profile（不影响内置）。
 */
fun removeSubagentProfile(
    custom: List<SubagentProfile>,
    name: String,
): List<SubagentProfile> = custom.filterNot { it.name == name }

/**
 * Returns a copy of this profile with [skillName] added to or removed from
 * [SubagentProfile.enabledSkills], depending on [enabled].
 *
 * Mirrors [Assistant.toggleSkill] for use in subagent profile editing UI.
 */
fun SubagentProfile.toggleSkill(skillName: String, enabled: Boolean): SubagentProfile =
    copy(enabledSkills = if (enabled) enabledSkills + skillName else enabledSkills - skillName)

/**
 * Subagent 运行结果 —— 对应 kimi-code 的 SubagentCompletion / SubagentResult。
 *
 * @param profileName 触发时引用的配置档名
 * @param summary     子代理最终摘要文本（作为工具结果返回给父代理）
 * @param succeeded   是否成功完成
 * @param error       失败时的错误信息
 * @param depth       该子代理所在的递归深度（0 = 直接子代理）
 * @param usage       子代理本次运行累计的 token 用量（含扩写追问轮次）；null 表示未统计到
 * @param steps       子代理实际执行的 generation 轮次（含扩写追问）
 * @param transcript  子代理完整运行轨迹（思维链 + 工具调用 + 正文），用于 UI 可视化展开。
 *                    传输时序列化进 [SubagentResult]，但 UI 渲染依赖 metadata 中的副本。
 *                    为 null 表示无轨迹（如 depth 超限、profile 未找到等提前返回场景）。
 */
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

/**
 * 子代理运行轨迹中的一个步骤，用于 UI 嵌套展开可视化。
 *
 * 三种类型分别对应子代理消息中的：
 * - [Reasoning] → 子代理的 `<think>` / reasoning part（思维链）
 * - [ToolCall]  → 子代理调用的工具（含入参与完整输出）
 * - [Text]      → 子代理产出的正文文本（通常是末轮摘要）
 *
 * 完整保留（不截断）——用户可展开查看子代理搜索到的原始网页内容等。
 * 嵌套深度超过 1 层时，孙代理的 ToolCall 内部不再展开其 transcript（见 UI 渲染）。
 */
@Serializable
sealed interface SubagentTranscriptStep {
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val text: String,
        val createdAt: Long = 0,
    ) : SubagentTranscriptStep

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val name: String,
        val input: String,
        val output: String,
        val executed: Boolean,
        val childTranscript: List<SubagentTranscriptStep> = emptyList(),
    ) : SubagentTranscriptStep

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
    ) : SubagentTranscriptStep
}
