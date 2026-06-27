package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import kotlin.uuid.Uuid
/**
 * 创建 subagent 相关工具（移植自 kimi-code 的 collaboration/agent.ts + agent-swarm.ts）。
 *
 * 工具集与具体业务解耦：实际"派生子代理"的逻辑由 [spawn] 回调提供（由 ChatService 实现，
 * 内部调用 [SubagentHost.spawn] 并负责按深度递归构建子代理工具）。本函数只负责把工具
 * schema / 描述 / 参数解析 / 结果序列化做好。
 *
 * 关键设计（让子代理容易被调用）：
 * - 工具描述包含明确的"何时该用"触发信号（参考 kimi-code agent.md / system.md）
 * - systemPrompt 注入强引导语，给出具体场景阈值
 * - 负面引导克制（不说"不要用于简单任务"，而是说"简单任务可直接做"）
 *
 * @param profiles 父代理可用的 subagent 配置档列表（用于枚举 profile_name）
 * @param json     用于序列化结果的 Json
 * @param spawn    派生子代理的回调：(profileName, task, description) -> [SubagentResult]
 * @param askBtw   轻量"顺便问一句"回调（无工具、纯文本，对应 kimi-code startBtw）
 * @param delegateOnly 纯决策模式：主代理无任何执行类工具，所有执行必须委派给子代理。
 *                     会强化提示词，要求主代理拆解任务、并行委派独立子任务、综合结果。
 */
fun createSubagentTools(
    profiles: List<SubagentProfile>,
    json: Json,
    spawn: suspend (profileName: String, task: String, description: String) -> SubagentResult,
    askBtw: suspend (question: String) -> String,
    delegateOnly: Boolean = false,
    includeAskBtw: Boolean = true,
): List<Tool> {
    if (profiles.isEmpty()) return emptyList()

    val profileNames = profiles.map { it.name }
    val profileListText = profiles.joinToString("\n") { "  - ${it.name}: ${it.description}" }

    val spawnTool = Tool(
        name = "spawn_subagent",
        description = """
            Launch a subagent to handle a task autonomously. The subagent runs its own tool loop with a fresh context and reports back a summary.

            Writing the task prompt:
            - The subagent starts with ZERO context — it has not seen this conversation. Brief it like a colleague who just walked into the room: state the goal, list what you already know, hand over the specifics.
            - Lookups (read this file, search for X): put the exact path or query in the prompt. The subagent should not have to search for things you already know.
            - Investigations (figure out X, find why Y): give the question, not prescribed steps — fixed steps become dead weight when the premise is wrong.

            When to USE this tool (reach for it proactively):
            - Research or exploration that will clearly need MORE than 2-3 search queries or file reads.
            - Multi-step tasks with a clear, self-contained goal (write a script, review a document, analyze data).
            - When you want to parallelize: spawn multiple subagents for independent sub-tasks instead of doing them sequentially.
            - When the task would bloat your own context with intermediate details you don't need to keep.
            - Code review, critique, or second opinion on a substantial artifact.

            When you can SKIP this tool (do it directly):
            - Reading a file whose path you already know.
            - A single quick search or calculation.
            - Answering from knowledge you already have.

            **Parallel execution:** Multiple `spawn_subagent` calls made in the SAME response run
            concurrently. For independent sub-tasks, batch them into one response rather than
            spawning one at a time — this dramatically cuts total wait time. Only serialize when
            one task depends on another's result.

            Available subagent profiles:
$profileListText

            The subagent's result is only visible to you, not to the user. When the user needs to see what a subagent produced, summarize the relevant parts in your own reply.
        """.trimIndent(),
        systemPrompt = { _, _ ->
            buildString {
                appendLine()
                appendLine("**Subagents — Delegation Guidance**")
                if (delegateOnly) {
                    appendLine("You are operating in **delegation-only mode**: you have NO execution tools of your own (no search, no file access, no shell). Your ONLY way to perform real work is to delegate via `spawn_subagent`. Your role is that of an orchestrator: break the user's request into sub-tasks, delegate each to an appropriate subagent, then synthesize the subagents' results into a coherent answer.")
                    appendLine()
                    appendLine("**How to orchestrate well:**")
                    appendLine("- Decompose the request into independent, self-contained sub-tasks. Each subagent starts with zero context, so every task prompt must contain the full goal, relevant facts, and specifics.")
                    appendLine("- **Parallelize aggressively**: if sub-tasks are independent, issue multiple `spawn_subagent` calls in the SAME response. They will run concurrently — this is far faster than spawning them one at a time and waiting between each.")
                    appendLine("- For dependent sub-tasks (B needs A's result), spawn A first, wait for its summary, then spawn B with A's findings included.")
                    appendLine("- After all subagents return, synthesize their summaries into a single, coherent reply to the user. Don't just paste raw summaries — integrate, deduplicate, and resolve conflicts.")
                    appendLine("- Use `ask_btw` only for quick knowledge-only sanity checks that need no tools.")
                    appendLine("- Use `ask_user` (if available) only when you genuinely lack information needed to decompose the task and cannot reasonably proceed.")
                } else {
                    appendLine("You have the `spawn_subagent` tool to delegate work to specialized subagents. Each subagent runs autonomously with its own tool loop and fresh context, then returns a summary. Prefer delegating to the matching specialized subagent rather than doing substantial work inline — it keeps your own context clean and lets you focus on orchestration.")
                    appendLine()
                    appendLine("**Route work to the right subagent by specialty:**")
                    appendLine("- `explore`: research / investigation that needs more than 2-3 searches or reads; gathering context before you decide. Delegate instead of cluttering your own context with intermediate steps.")
                    appendLine("- `coder`: substantial coding or editing tasks — writing or modifying files, running shell commands, building/verifying a script. Delegate these even though you CAN code directly; the subagent keeps your context focused on the big picture.")
                    appendLine("- `reviewer`: reviews, critiques, second opinions, or structured analysis of an artifact or plan. Delegate for an independent perspective rather than reviewing inline.")
                    appendLine()
                    appendLine("**Parallelize aggressively:** if a task has independent parts, spawn multiple subagents in the SAME response (e.g. two `coder` instances on two independent files, or `explore` + `reviewer` together) — they run concurrently and this is far faster than doing them one by one. Only serialize when one sub-task depends on another's result.")
                    appendLine()
                    appendLine("**When NOT to delegate (do it directly):**")
                    appendLine("- A single quick file read, one search, or a trivial calculation.")
                    appendLine("- Anything you can answer from context you already have.")
                    appendLine("- Do NOT skip delegation merely because you have the tools — for multi-step coding or review work, delegating is the preferred path.")
                }
                appendLine()
                appendLine("**How to delegate well:**")
                appendLine("- The subagent sees NONE of your conversation. Include the goal, relevant context, and specifics (file paths, search terms, known facts) in the task prompt.")
                appendLine("- Give the question, not step-by-step instructions — let the subagent figure out the approach.")
                appendLine()
                appendLine("<available_subagent_profiles>")
                profiles.forEach { p ->
                    appendLine("  <profile>")
                    appendLine("    <name>${p.name}</name>")
                    appendLine("    <description>${p.description}</description>")
                    appendLine("  </profile>")
                }
                append("</available_subagent_profiles>")
            }
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("profile_name", buildJsonObject {
                        put("type", "string")
                        put("description", "The subagent profile to spawn. Pick the one whose specialty best matches the task.")
                        put(
                            "enum",
                            kotlinx.serialization.json.buildJsonArray {
                                profileNames.forEach { add(it) }
                            }
                        )
                    })
                    put("task", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "Full, self-contained task prompt for the subagent. The subagent has NOT seen this conversation — include the goal, known facts, file paths, and any specifics needed."
                        )
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Short 3-5 word description of this delegation for display (optional)")
                    })
                },
                required = listOf("profile_name", "task"),
            )
        },
        execute = { args ->
            val params = args.jsonObject
            val profileName = params["profile_name"]?.jsonPrimitive?.contentOrNull
                ?: error("profile_name is required")
            val task = params["task"]?.jsonPrimitive?.contentOrNull
                ?: error("task is required")
            val description = params["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val result = spawn(profileName, task, description)
            val payload = buildJsonObject {
                put("profile_name", JsonPrimitive(result.profileName))
                put("succeeded", JsonPrimitive(result.succeeded))
                if (!result.error.isNullOrBlank()) {
                    put("error", JsonPrimitive(result.error))
                }
                put("summary", JsonPrimitive(result.summary))
                put("depth", JsonPrimitive(result.depth))
                put("steps", JsonPrimitive(result.steps))
            }
            // transcript 完整存入 metadata（不发给模型，仅 UI 渲染用）。
            // provider 序列化时只取 Text.text，metadata 持久化到对话但不出现在 API 请求中。
            val transcriptMetadata = if (result.transcript.isNotEmpty()) {
                val listSerializer = kotlinx.serialization.builtins.ListSerializer(
                    SubagentTranscriptStep.serializer()
                )
                buildJsonObject {
                    put("subagent_transcript", json.encodeToJsonElement(listSerializer, result.transcript))
                    put("subagent_profile", JsonPrimitive(result.profileName))
                    put("subagent_steps", JsonPrimitive(result.steps))
                    put("subagent_succeeded", JsonPrimitive(result.succeeded))
                }
            } else {
                null
            }
            listOf(
                UIMessagePart.Text(
                    text = payload.toString(),
                    metadata = transcriptMetadata,
                )
            )
        },
    )

    val btwTool = Tool(
        name = "ask_btw",
        description = """
            Ask a lightweight, tool-less side question to a fresh agent instance.
            Use this to get a quick second opinion or sanity check without spinning up a full
            subagent. The side agent has NO tools and answers from its own knowledge only.
            It does not see your conversation history; provide all necessary context in the question.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("question", buildJsonObject {
                        put("type", "string")
                        put("description", "The self-contained side question to ask (include all needed context)")
                    })
                },
                required = listOf("question"),
            )
        },
        execute = { args ->
            val question = args.jsonObject["question"]?.jsonPrimitive?.contentOrNull
                ?: error("question is required")
            val answer = askBtw(question)
            val payload = buildJsonObject {
                put("answer", JsonPrimitive(answer))
            }
            listOf(UIMessagePart.Text(payload.toString()))
        },
    )

    val tools = mutableListOf(spawnTool)
    if (includeAskBtw) tools.add(btwTool)

    return tools
}

/**
 * 把工具参数 JSON 中的字段应用到 [SubagentProfile] 上（仅覆盖出现的字段）。
 * 供 manage_subagent_profile 的 create/update 使用。
 */

/**
 * 创建 manage_subagent_profile 工具：让主代理自主增删改子代理配置。
 *
 * 与 [createSubagentTools] 解耦——此工具在根代理（depth==0）**无条件注册**，
 * 即使当前没有任何子代理 profile（profiles 为空）也注册，这样模型才能从零创建
 * 子代理。否则 profiles 为空时模型调用 manage 会因工具未注册而报 "not found"。
 *
 * @param profiles 当前可用 profile 列表（用于 update 时查找已有 profile 作为 base）
 * @param json     序列化用 Json
 * @param manage   实际处理回调：(action, name, profile) -> 结果文本
 */
fun createManageSubagentTool(
    profiles: List<SubagentProfile>,
    json: Json,
    manage: suspend (action: String, name: String, profile: SubagentProfile?) -> String,
): Tool = Tool(
    name = "manage_subagent_profile",
    description = """
        Manage the subagent profiles available to you (create / update / delete / list).
        Use this to adapt your delegation toolkit to the task: add a specialized subagent,
        tweak an existing one's system prompt or tools, remove one you don't need, or list
        what's currently available.

        Actions:
        - "list": list all available subagent profiles with their name, description, and tool config. No other params needed.
        - "create": add a NEW profile. Requires "name" (lowercase, [a-z][a-z0-9_]*). Provide the fields you want to set; omitted optional fields use defaults.
        - "update": modify an EXISTING profile (built-in or custom). Provide "name" plus only the fields to change. You can update built-in profiles this way (creates a custom override).
        - "delete": remove a profile. Requires "name". Built-in profiles are disabled; custom ones are removed.

        Fields (optional for create/update, ignored for delete/list):
        - display_name: human-readable name shown in UI
        - description: what this subagent is good at (shown to you in spawn_subagent's profile list)
        - system_prompt: the subagent's system prompt
        - model_id: UUID of a chat model (omit to inherit parent's model)
        - inherit_tools: boolean — true = inherit all parent tools; false = use only the tools selected below
        - local_tools: array of local tool names, one of: "javascript_engine", "time_info", "clipboard", "tts", "ask_btw" (only when inherit_tools=false)
        - enabled_skills: array of skill names (only when inherit_tools=false)
        - mcp_server_ids: array of MCP server UUIDs (only when inherit_tools=false)
        - excluded_tools: array of tool names to exclude when inheriting
        - max_steps: max tool-call rounds (1-256)
        - stream_output: boolean, whether the subagent streams its output to UI
        - enable_memory: boolean
        - temperature, top_p, max_tokens: optional numeric overrides

        Changes persist to the assistant configuration and affect all future conversations with this assistant.
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", kotlinx.serialization.json.buildJsonArray {
                        listOf("list", "create", "update", "delete").forEach { add(it) }
                    })
                    put("description", "One of: list, create, update, delete")
                })
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Profile name (lowercase [a-z][a-z0-9_]*). Required for create/update/delete; ignored for list.")
                })
                put("display_name", buildJsonObject { put("type", "string") })
                put("description", buildJsonObject { put("type", "string") })
                put("system_prompt", buildJsonObject { put("type", "string") })
                put("model_id", buildJsonObject { put("type", "string") })
                put("inherit_tools", buildJsonObject { put("type", "boolean") })
                put("local_tools", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject {
                        put("type", "string")
                        put("enum", kotlinx.serialization.json.buildJsonArray {
                            listOf("javascript_engine", "time_info", "clipboard", "tts", "ask_btw").forEach { add(it) }
                        })
                    })
                })
                put("enabled_skills", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("mcp_server_ids", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("excluded_tools", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("max_steps", buildJsonObject { put("type", "integer") })
                put("stream_output", buildJsonObject { put("type", "boolean") })
                put("enable_memory", buildJsonObject { put("type", "boolean") })
                put("temperature", buildJsonObject { put("type", "number") })
                put("top_p", buildJsonObject { put("type", "number") })
                put("max_tokens", buildJsonObject { put("type", "integer") })
            },
            required = listOf("action"),
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull
            ?: error("action is required")
        val name = params["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        when (action) {
            "list" -> {
                val text = manage("list", "", null)
                listOf(UIMessagePart.Text(text))
            }
            "delete" -> {
                if (name.isBlank()) error("name is required for delete")
                val text = manage("delete", name, null)
                listOf(UIMessagePart.Text(text))
            }
            "create", "update" -> {
                if (name.isBlank()) error("name is required for $action")
                if (!name.matches(SubagentProfile.IdentifierRegex)) {
                    error("name must be lowercase [a-z][a-z0-9_]*: $name")
                }
                val base = if (action == "update") {
                    profiles.firstOrNull { it.name == name }
                        ?: error("profile '$name' not found; use create instead")
                } else {
                    SubagentProfile(name = name)
                }
                val updated = base.applyPatch(params, json)
                val text = manage(action, name, updated)
                listOf(UIMessagePart.Text(text))
            }
            else -> error("unknown action: $action")
        }
    },
)

private fun SubagentProfile.applyPatch(
    params: JsonObject,
    json: Json,
): SubagentProfile {
    fun str(key: String): String? = params[key]?.jsonPrimitive?.contentOrNull
    fun bool(key: String): Boolean? = params[key]?.jsonPrimitive?.booleanOrNull
    fun int(key: String): Int? = params[key]?.jsonPrimitive?.intOrNull
    fun flt(key: String): Float? = params[key]?.jsonPrimitive?.floatOrNull
    fun strList(key: String): List<String> = (params[key] as? JsonArray)?.mapNotNull {
        runCatching { it.jsonPrimitive.content }.getOrNull()
    } ?: emptyList()

    // 集合类字段：仅当参数中出现该 key 时才覆盖（哪怕为空数组也覆盖为空），
    // 未出现则保留原值。这样 update 既能清空集合也能部分修改。
    fun optStrSet(key: String): Set<String>? =
        if (key in params) strList(key).toSet() else null
    fun optUuidSet(key: String): Set<Uuid>? =
        if (key in params) strList(key).mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet()
        else null
    fun optLocalTools(key: String): List<LocalToolOption>? =
        if (key in params) strList(key).mapNotNull { it.toLocalToolOption() } else null

    return copy(
        displayName = str("display_name") ?: displayName,
        description = str("description") ?: description,
        systemPrompt = str("system_prompt") ?: systemPrompt,
        chatModelId = str("model_id")?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: chatModelId,
        temperature = flt("temperature") ?: temperature,
        topP = flt("top_p") ?: topP,
        maxTokens = int("max_tokens") ?: maxTokens,
        maxSteps = int("max_steps") ?: maxSteps,
        inheritTools = bool("inherit_tools") ?: inheritTools,
        streamOutput = bool("stream_output") ?: streamOutput,
        enableMemory = bool("enable_memory") ?: enableMemory,
        excludedTools = optStrSet("excluded_tools") ?: excludedTools,
        enabledSkills = optStrSet("enabled_skills") ?: enabledSkills,
        mcpServerIds = optUuidSet("mcp_server_ids") ?: mcpServerIds,
        localTools = optLocalTools("local_tools") ?: localTools,
    )
}

/** 工具参数字符串 -> [LocalToolOption]，匹配 LocalToolOption 的 @SerialName。 */
private fun String.toLocalToolOption(): LocalToolOption? = when (this) {
    "javascript_engine" -> LocalToolOption.JavascriptEngine
    "time_info" -> LocalToolOption.TimeInfo
    "clipboard" -> LocalToolOption.Clipboard
    "tts" -> LocalToolOption.Tts
    "ask_user" -> LocalToolOption.AskUser
    "ask_btw" -> LocalToolOption.AskBtw
    "fetch" -> LocalToolOption.Fetch
    "logs" -> LocalToolOption.Logs
    else -> null
}

/** subagent 工具名集合，便于在构建子代理工具时排除 / 判断递归。 */
val SUBAGENT_TOOL_NAMES: Set<String> = setOf("spawn_subagent", "ask_btw", "manage_subagent_profile")
