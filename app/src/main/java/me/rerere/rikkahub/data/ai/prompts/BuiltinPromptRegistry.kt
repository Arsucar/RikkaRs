package me.rerere.rikkahub.data.ai.prompts

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition

private const val WORKSPACE_NAME_MACRO = "{{workspace_name}}"
private const val WORKSPACE_CWD_MACRO = "{{cwd}}"

internal fun buildWorkspaceGuidePrompt(
    workspaceName: String,
    cwd: String? = null,
): String = buildList {
    add("<workspace>")
    add(
        "You have access to a persistent Linux workspace named \"$workspaceName\", " +
            "running in a sandboxed proot rootfs environment."
    )
    add(
        "- The workspace files area is mounted at `/workspace`. Use it as your working directory; " +
            "files written there persist across turns of this conversation."
    )
    add(
        "- All paths passed to workspace tools must be absolute and inside the Rootfs " +
            "(for example `/workspace/notes.md`)."
    )
    add("- Available tools:")
    add("  - `workspace_read_file`: read file contents.")
    add("  - `workspace_write_file` / `workspace_edit_file`: create files, or make precise edits to existing files.")
    add("  - `workspace_shell`: run shell commands (the files area is mounted at /workspace).")
    add(
        "- Prefer `workspace_shell` for tasks that standard Unix tools handle well, and prefer " +
            "`workspace_edit_file` for targeted edits over rewriting whole files."
    )
    add(
        "- Global skills are mounted for inspection at `/skills/<skill-name>/`; assistant-private skills for this " +
            "assistant are mounted at `/skills_private/<skill-name>/`."
    )
    add(
        "- You may run scripts from `/skills_private` and iterate on private skill files there. Writes outside " +
            "`/workspace` and `/tmp`, including skill files, may require user approval."
    )
    add(
        "- Files the user uploaded are mounted at `/upload`. Treat `/upload` as READ-ONLY: read uploaded files from " +
            "`/upload/<file-name>`, but never modify, overwrite, or delete anything there. If you need to change an " +
            "uploaded file, copy it into `/workspace` first and edit the copy."
    )
    if (!cwd.isNullOrBlank()) {
        add(
            "- Current working directory: `$cwd`. Use this as the default context for file operations and shell " +
                "commands."
        )
    }
    add("</workspace>")
}.joinToString("\n")

/**
 * 内置提示词模板的元信息定义。
 *
 * 用于 #182 的可编辑预设条目（PresetEntry.Builtin）：条目通过稳定 [key] 引用一个内置模板，
 * 可选择覆盖内容/位置或开关启用。注册表本身只描述模板元信息与动态宏解析入口，
 * 不参与实际注入流程。
 */
data class BuiltinPromptDef(
    /** 稳定 key，会持久化进 PresetEntry.Builtin.builtinKey，一经发布不可修改。 */
    val key: String,
    /** 可选：展示名的 string 资源 id；为 null 时 UI 回退到 [key]。 */
    val displayNameRes: Int? = null,
    /** 默认模板内容。动态模板中包含 `{{...}}` 宏占位符。 */
    val defaultContent: String,
    /** 默认注入角色。参考 PromptInjection.role（USER / ASSISTANT）。 */
    val defaultRole: MessageRole = MessageRole.USER,
    /** 默认注入位置。 */
    val defaultPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    /** true 表示模板包含运行时宏（`{{...}}`），需要通过 [BuiltinPromptRegistry.resolveContent] 解析。 */
    val dynamic: Boolean,
    /** 用户是否可以覆盖模板内容。运行时自动拼装的内容（如记忆表实时快照）不建议开放覆盖。 */
    val overridable: Boolean,
    /** 专用消费点支持、可由编辑器插入的变量原文；保留单/双花括号的真实语法。 */
    val supportedVariables: List<String> = emptyList(),
    /**
     * 是否允许由预设条目注入进对话。
     *
     * config-only 语义：默认 false，表示该内置模板仅供 UI 展示/编辑，真实注入由各自专用
     * transformer（WorkspaceReminderTransformer / MemoryTableInjectionTransformer）或特性流程
     * （buildInputDraftPrompt / 建议流程）完成。预设注入路径对 injectable=false 的条目一律跳过，
     * 避免双注入或泄漏未解析的字面宏（见 #182）。
     */
    val injectable: Boolean = false,
)

/**
 * 内置提示词模板统一注册表。
 *
 * 收敛此前分散在 [me.rerere.rikkahub.data.ai.prompts] / transformers 中的内置模板元信息，
 * 供预设条目引用/覆盖/开关。
 *
 * 说明：
 * - 静态模板（回复草稿 / 建议回复）在本注册表视角下 [BuiltinPromptDef.dynamic] = false，
 *   它们虽含 `{locale}` / `{content}` 等单花括号占位符，但那由 [buildInputDraftPrompt] /
 *   [me.rerere.rikkahub.utils.applyPlaceholders] 在各自特性流程里解析，不属于本注册表的 `{{...}}` 宏。
 * - 动态模板在真实注入流程里由运行时数据拼装。记忆表内容依赖 templates/documents，注册表保留精简引导；
 *   Workspace 默认由 [buildWorkspaceGuidePrompt] 同时服务编辑器与运行时，避免两份安全契约漂移。
 */
object BuiltinPromptRegistry {
    // ---- 稳定 key 常量（持久化用，不可修改）----
    const val KEY_REPLY_DRAFT = "reply_draft"
    const val KEY_SUGGESTION = "suggestion"
    const val KEY_MEMORY_TABLE_GUIDE = "memory_table_guide"
    const val KEY_WORKSPACE_GUIDE = "workspace_guide"

    private const val VAR_LOCALE = "{locale}"
    private const val VAR_CONTENT = "{content}"
    private const val VAR_USER_INSTRUCTION = "{user_instruction}"
    // 记忆表手动注入宏，与 MemoryTableInjectionTransformer.MEMORY_TABLE_MACRO 保持一致。
    private const val VAR_MEMORY_TABLES = "{{memory_tables}}"
    private const val VAR_WORKSPACE_NAME = WORKSPACE_NAME_MACRO
    private const val VAR_CWD = WORKSPACE_CWD_MACRO

    /**
     * 记忆表向导默认模板（精简版）。
     *
     * 来源：真实注入内容由 [me.rerere.rikkahub.data.ai.transformers.MemoryTableInjectionTransformer]
     * 的 buildMemoryTablePrompt(...) 在运行时按 templates/documents 拼装（含 `<memory_tables>` 块）。
     * 该函数依赖运行时数据，无法作为顶层常量引用，故此处提供带 {{memory_tables}} 宏的精简默认，
     * 由 resolveContent 在注入时把宏替换为实时渲染的记忆表块。
     */
    private val DEFAULT_MEMORY_TABLE_GUIDE = """
        You have access to structured memory table documents for this conversation.
        Use them as authoritative context; do not invent table names outside the defined schema.

        $VAR_MEMORY_TABLES
    """.trimIndent()

    /** 完整 Workspace 默认；宏由专用 transformer 在运行时解析。 */
    private val DEFAULT_WORKSPACE_GUIDE = buildWorkspaceGuidePrompt(VAR_WORKSPACE_NAME, VAR_CWD)

    val all: Map<String, BuiltinPromptDef> = listOf(
        // 回复草稿：来源 Suggestion.kt#DEFAULT_INPUT_DRAFT_PROMPT（#177 引入 user_instruction）。
        // 单花括号占位符（{locale}/{content}/{user_instruction}）由 buildInputDraftPrompt 解析，
        // 非本注册表的 {{...}} 宏，故 dynamic=false（resolveContent 原样返回）。
        BuiltinPromptDef(
            key = KEY_REPLY_DRAFT,
            defaultContent = DEFAULT_INPUT_DRAFT_PROMPT,
            defaultRole = MessageRole.USER,
            defaultPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
            dynamic = false,
            overridable = true,
            supportedVariables = listOf(VAR_LOCALE, VAR_CONTENT, VAR_USER_INSTRUCTION),
            injectable = false, // config-only：真实注入由 buildInputDraftPrompt 完成，预设路径跳过
        ),
        // 建议回复：来源 Suggestion.kt#DEFAULT_SUGGESTION_PROMPT。
        // 同上，{locale}/{content} 由特性流程解析，非本注册表宏，dynamic=false。
        BuiltinPromptDef(
            key = KEY_SUGGESTION,
            defaultContent = DEFAULT_SUGGESTION_PROMPT,
            defaultRole = MessageRole.USER,
            defaultPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
            dynamic = false,
            overridable = true,
            supportedVariables = listOf(VAR_LOCALE, VAR_CONTENT),
            injectable = false, // config-only：真实注入由建议流程完成，预设路径跳过
        ),
        // 记忆表向导：动态，运行时把 {{memory_tables}} 替换为实时渲染的记忆表块。
        // 真实内容拼装在 MemoryTableInjectionTransformer；引导文案可被用户覆盖，故 overridable=true，
        // 其中的 {{memory_tables}} 宏在注入时由运行时数据块替换（用户只编辑引导文案，数据块运行时填充）。
        // role/position 拿不准（真实流程是并入 system 消息）：默认 USER + AFTER_SYSTEM_PROMPT。
        BuiltinPromptDef(
            key = KEY_MEMORY_TABLE_GUIDE,
            defaultContent = DEFAULT_MEMORY_TABLE_GUIDE,
            defaultRole = MessageRole.USER,
            defaultPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
            dynamic = true,
            overridable = true,
            supportedVariables = listOf(VAR_MEMORY_TABLES),
            injectable = false, // config-only：真实注入由 MemoryTableInjectionTransformer 完成，预设路径跳过
        ),
        // 工作区向导：动态，运行时替换 {{workspace_name}} / {{cwd}}。
        // 真实内容拼装在 WorkspaceReminderTransformer；允许用户覆盖模板文案，故 overridable=true。
        // role/position 拿不准（真实流程是并入 system 消息）：默认 USER + AFTER_SYSTEM_PROMPT。
        BuiltinPromptDef(
            key = KEY_WORKSPACE_GUIDE,
            defaultContent = DEFAULT_WORKSPACE_GUIDE,
            defaultRole = MessageRole.USER,
            defaultPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
            dynamic = true,
            overridable = true,
            supportedVariables = listOf(VAR_WORKSPACE_NAME, VAR_CWD),
            injectable = false, // config-only：真实注入由 WorkspaceReminderTransformer 完成，预设路径跳过
        ),
    ).associateBy { it.key }

    operator fun get(key: String): BuiltinPromptDef? = all[key]

    /**
     * 解析动态宏。
     *
     * - 静态模板（[BuiltinPromptDef.dynamic] = false）忽略 [vars]，原样返回 override 或默认内容。
     * - 动态模板：单次扫描原始模板，把 `{{key}}` / `{{ key }}` 占位符替换为 [vars] 中的值。
     *   插入值保持字面内容，不会作为模板再次解析。
     * - 未在 [vars] 中提供的宏保持原样（不主动清空），已提供但值为空串的宏替换为空串。
     * - 绝不抛异常。
     *
     * @param def 目标模板定义。
     * @param override 用户覆盖内容；非 null 时优先于 [BuiltinPromptDef.defaultContent]。
     * @param vars 运行时变量表，如 mapOf("workspace_name" to "...", "cwd" to "...", "memory_tables" to "...")。
     */
    fun resolveContent(def: BuiltinPromptDef, override: String?, vars: Map<String, String>): String {
        val template = override ?: def.defaultContent
        if (!def.dynamic || vars.isEmpty()) return template
        val macroPattern = Regex("""\{\{\s*([^{}]+?)\s*}}""")
        return macroPattern.replace(template) { match ->
            vars[match.groupValues[1]] ?: match.value
        }
    }
}
