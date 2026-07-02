package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import kotlin.uuid.Uuid

val SUBAGENT_TOOL_NAMES: Set<String> = setOf(
    "spawn_subagent",
    "ask_btw",
    "manage_subagent_profile",
)

fun createSubagentTools(
    json: Json,
    spawn: suspend (profileName: String, task: String, description: String) -> SubagentResult,
    askBtw: suspend (question: String) -> String,
    getProfiles: () -> List<SubagentProfile>,
    includeAskBtw: Boolean = true,
    delegateOnly: Boolean = false,
): List<Tool> {
    val spawnTool = Tool(
        name = "spawn_subagent",
        description = """
            Launch a subagent to handle a task autonomously. The subagent runs its own tool loop with a fresh context and reports back a summary.

            Writing the task prompt:
            - The subagent starts with ZERO context — include the goal, known facts, paths, and specifics.
            - Give the question, not step-by-step instructions when investigating.

            When to USE: research needing many reads/searches, multi-step scoped tasks, parallel independent work.
            When to SKIP: a single known file read, one quick search, or answers you already have.

            **Parallel execution:** Multiple `spawn_subagent` calls in the SAME response run concurrently when possible.

            Available subagent profile names are listed in the system prompt under <available_subagent_profiles>.
        """.trimIndent(),
        systemPrompt = { _, _ ->
            buildString {
                appendLine()
                if (delegateOnly) {
                    appendLine("**Delegation-Only Mode**")
                    appendLine("You have NO execution tools. You MUST decompose the task and delegate via `spawn_subagent` (you may emit multiple in one response). Synthesize subagent results; do not paste raw transcripts.")
                    appendLine()
                }
                appendLine("**Subagents — Delegation Guidance**")
                appendLine("Use `spawn_subagent` to delegate substantial work. Task prompts must be self-contained.")
                appendLine()
                appendLine("<available_subagent_profiles>")
                getProfiles().forEach { p ->
                    appendLine("  <profile>")
                    appendLine("    <name>${p.name}</name>")
                    appendLine("    <description>${p.description}</description>")
                    appendLine("    <workspace_access>${p.workspaceAccess.name}</workspace_access>")
                    appendLine("  </profile>")
                }
                append("</available_subagent_profiles>")
            }
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put(
                        "profile_name",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Subagent profile to spawn (see system prompt for available names)")
                        },
                    )
                    put(
                        "task",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Self-contained task prompt for the subagent")
                        },
                    )
                    put(
                        "description",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Short label for this delegation (optional)")
                        },
                    )
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
            val listSerializer = ListSerializer(SubagentTranscriptStep.serializer())
            val finalMetadata = buildJsonObject {
                put("subagent_transcript", json.encodeToJsonElement(listSerializer, result.transcript))
                put("subagent_profile", JsonPrimitive(result.profileName))
                put("subagent_steps", JsonPrimitive(result.steps))
                put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
                put("subagent_tool_calls", JsonPrimitive(result.toolCallCount))
                put("subagent_transcript_size", JsonPrimitive(result.transcript.size))
                put("subagent_succeeded", JsonPrimitive(result.succeeded))
                put("subagent_streaming", JsonPrimitive(false))
            }
            val slimPayload = buildJsonObject {
                put("profile_name", JsonPrimitive(result.profileName))
                put("summary", JsonPrimitive(result.summary))
                put("succeeded", JsonPrimitive(result.succeeded))
                if (!result.error.isNullOrBlank()) put("error", JsonPrimitive(result.error))
                put("steps", JsonPrimitive(result.steps))
                put("tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
                put("transcript_size", JsonPrimitive(result.transcript.size))
                put("tool_call_count", JsonPrimitive(result.toolCallCount))
                result.usage?.let { put("usage", Json.encodeToJsonElement(TokenUsage.serializer(), it)) }
            }.toString()
            listOf(UIMessagePart.Text(text = slimPayload, metadata = finalMetadata))
        },
    )

    val tools = mutableListOf(spawnTool)
    if (includeAskBtw) {
        tools += Tool(
            name = "ask_btw",
            description = """
                Ask a lightweight side question with no tools. Provide all context in the question.
            """.trimIndent(),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put(
                            "question",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Self-contained question")
                            },
                        )
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
    }
    return tools
}

fun createManageSubagentTool(
    json: Json,
    depth: Int,
    resolveProfile: (name: String) -> SubagentProfile?,
    manage: suspend (action: String, name: String, profile: SubagentProfile?) -> String,
): Tool? {
    if (depth != 0) return null
    return Tool(
        name = "manage_subagent_profile",
        description = """
            Manage subagent profiles (list, create, update, delete). Only available to the main agent.
            Changes persist on the assistant configuration.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put(
                        "action",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                buildJsonArray {
                                    listOf("list", "create", "update", "delete").forEach { add(it) }
                                },
                            )
                        },
                    )
                    put("name", buildJsonObject { put("type", "string") })
                    put("display_name", buildJsonObject { put("type", "string") })
                    put("description", buildJsonObject { put("type", "string") })
                    put("system_prompt", buildJsonObject { put("type", "string") })
                    put("model_id", buildJsonObject { put("type", "string") })
                    put("inherit_tools", buildJsonObject { put("type", "boolean") })
                    put("local_tools", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
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
                "list" -> listOf(UIMessagePart.Text(manage("list", "", null)))
                "delete" -> {
                    if (name.isBlank()) error("name is required for delete")
                    listOf(UIMessagePart.Text(manage("delete", name, null)))
                }
                "create", "update" -> {
                    if (name.isBlank()) error("name is required for $action")
                    if (!name.matches(SubagentProfile.IdentifierRegex)) {
                        error("name must be lowercase [a-z][a-z0-9_]*: $name")
                    }
                    val base = if (action == "update") {
                        resolveProfile(name)
                            ?: error("profile '$name' not found; use create instead")
                    } else {
                        SubagentProfile(name = name)
                    }
                    val updated = base.applyPatch(params)
                    listOf(UIMessagePart.Text(manage(action, name, updated)))
                }
                else -> error("unknown action: $action")
            }
        },
    )
}

private fun SubagentProfile.applyPatch(params: JsonObject): SubagentProfile {
    fun str(key: String): String? = params[key]?.jsonPrimitive?.contentOrNull
    fun bool(key: String): Boolean? = params[key]?.jsonPrimitive?.booleanOrNull
    fun int(key: String): Int? = params[key]?.jsonPrimitive?.intOrNull
    fun flt(key: String): Float? = params[key]?.jsonPrimitive?.floatOrNull
    fun strList(key: String): List<String> = (params[key] as? JsonArray)?.mapNotNull {
        runCatching { it.jsonPrimitive.content }.getOrNull()
    } ?: emptyList()

    fun optStrSet(key: String): Set<String>? =
        if (key in params) strList(key).toSet() else null
    fun optUuidSet(key: String): Set<Uuid>? =
        if (key in params) {
            strList(key).mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet()
        } else {
            null
        }
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
        maxToolCalls = if ("max_tool_calls" in params) int("max_tool_calls") else maxToolCalls,
        inheritTools = bool("inherit_tools") ?: inheritTools,
        streamOutput = bool("stream_output") ?: streamOutput,
        enableMemory = bool("enable_memory") ?: enableMemory,
        excludedTools = optStrSet("excluded_tools") ?: excludedTools,
        enabledSkills = optStrSet("enabled_skills") ?: enabledSkills,
        mcpServerIds = optUuidSet("mcp_server_ids") ?: mcpServerIds,
        localTools = optLocalTools("local_tools") ?: localTools,
    )
}

private fun String.toLocalToolOption(): LocalToolOption? = when (this) {
    "javascript_engine" -> LocalToolOption.JavascriptEngine
    "time_info" -> LocalToolOption.TimeInfo
    "clipboard" -> LocalToolOption.Clipboard
    "tts" -> LocalToolOption.Tts
    "ask_user" -> LocalToolOption.AskUser
    "screen_time" -> LocalToolOption.ScreenTime
    "logs" -> LocalToolOption.Logs
    else -> null
}