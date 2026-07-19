package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

val SUBAGENT_TOOL_NAMES: Set<String> = setOf(
    "spawn_subagent",
    "ask_btw",
    "manage_subagent_profile",
)

fun createSubagentTools(
    json: Json,
    spawn: suspend (
        profileName: String,
        task: String,
        description: String,
        reuseContextId: String?,
    ) -> SubagentResult,
    askBtw: suspend (question: String) -> String,
    getProfiles: () -> List<SubagentProfile>,
    includeAskBtw: Boolean = true,
    delegateOnly: Boolean = false,
    parallelExecutionEnabled: Boolean = false,
): List<Tool> {
    val spawnTool = Tool(
        name = "spawn_subagent",
        description = buildString {
            appendLine(
                """
                Launch a subagent to handle a task autonomously.
                Pass `reuse_context_id` to explicitly continue a previous subagent.
                When omitted, the host may automatically reuse the most recent completed context
                with the same full scope.

                Writing the task prompt:
                - Include the goal, known facts, paths, and specifics; a new context starts with ZERO prior history.
                - When a context is reused, the task is appended to its complete existing history.
                - Give the question, not step-by-step instructions when investigating.

                When to USE: research needing many reads/searches, multi-step scoped tasks, parallel independent work.
                When to SKIP: a single known file read, one quick search, or answers you already have.
                """.trimIndent(),
            )

            if (parallelExecutionEnabled) {
                appendLine()
                appendLine("**Parallel execution:** Multiple `spawn_subagent` calls in the SAME response run concurrently when possible.")
            }

            appendLine()
            append("Available subagent profile names are listed in the system prompt under <available_subagent_profiles>.")
        },
        systemPrompt = { _, _ ->
            buildString {
                appendLine()
                if (delegateOnly) {
                    appendLine("**Delegation-Only Mode**")
                    if (parallelExecutionEnabled) {
                        appendLine("You have no write or shell execution tools. You MUST decompose the task and delegate execution via `spawn_subagent` (you may emit multiple in one response). Synthesize subagent results; do not paste raw transcripts.")
                    } else {
                        appendLine("You have no write or shell execution tools. You MUST decompose the task and delegate execution via `spawn_subagent`. Synthesize subagent results; do not paste raw transcripts.")
                    }
                    appendLine("Use only the read-only or context tools that are actually listed as available for this turn.")
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
                    put(
                        "reuse_context_id",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Context id returned by an earlier spawn_subagent call. Optional; when omitted, " +
                                    "the most recent completed context with the same full scope may be " +
                                    "reused automatically.",
                            )
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
            val reuseContextId = params["reuse_context_id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val profileSnapshot = getProfiles().firstOrNull { it.name == profileName }
            val result = spawn(profileName, task, description, reuseContextId)
            val listSerializer = ListSerializer(SubagentTranscriptStep.serializer())
            val finalMetadata = buildJsonObject {
                put("subagent_transcript", json.encodeToJsonElement(listSerializer, result.transcript))
                put("subagent_profile", JsonPrimitive(result.profileName))
                put("subagent_task", JsonPrimitive(task))
                if (description.isNotBlank()) put("subagent_description", JsonPrimitive(description))
                profileSnapshot?.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                    put("subagent_system_prompt", JsonPrimitive(it))
                }
                profileSnapshot?.workspaceAccess?.let {
                    put("subagent_workspace_access", JsonPrimitive(it.name))
                }
                put("subagent_steps", JsonPrimitive(result.toolLoopSteps))
                put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
                put("subagent_tool_calls", JsonPrimitive(result.toolCallCount))
                put("subagent_max_tool_calls", JsonPrimitive(result.maxToolCalls ?: 32))
                put("subagent_truncated", JsonPrimitive(result.truncated))
                put("subagent_transcript_size", JsonPrimitive(result.transcript.size))
                put("subagent_succeeded", JsonPrimitive(result.succeeded))
                put("subagent_streaming", JsonPrimitive(false))
                result.contextId?.let { put("subagent_context_id", JsonPrimitive(it)) }
                result.contextStatus?.let { put("subagent_context_status", JsonPrimitive(it.name)) }
                put("subagent_context_completeness", JsonPrimitive(result.contextCompleteness.name))
                result.truncationReason?.let { put("subagent_truncation_reason", JsonPrimitive(it)) }
                result.startedAtEpochMillis?.let { put("subagent_started_at", JsonPrimitive(it)) }
                result.endedAtEpochMillis?.let { put("subagent_ended_at", JsonPrimitive(it)) }
            }
            val slimPayload = buildJsonObject {
                put("profile_name", JsonPrimitive(result.profileName))
                put("summary", JsonPrimitive(result.summary))
                put("succeeded", JsonPrimitive(result.succeeded))
                if (!result.error.isNullOrBlank()) put("error", JsonPrimitive(result.error))
                result.usage?.let { put("usage", json.encodeToJsonElement(TokenUsage.serializer(), it)) }
                put("max_tool_calls", JsonPrimitive(result.maxToolCalls ?: 32))
                put("truncated", JsonPrimitive(result.truncated))
                result.contextId?.let { put("context_id", JsonPrimitive(it)) }
                result.contextStatus?.let { put("context_status", JsonPrimitive(it.name)) }
                put("context_completeness", JsonPrimitive(result.contextCompleteness.name))
                result.truncationReason?.let { put("truncation_reason", JsonPrimitive(it)) }
                result.startedAtEpochMillis?.let { put("started_at", JsonPrimitive(it)) }
                result.endedAtEpochMillis?.let { put("ended_at", JsonPrimitive(it)) }
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
                    put("description", buildJsonObject { put("type", "string") })
                    put("system_prompt", buildJsonObject { put("type", "string") })
                    put("model_id", buildJsonObject { put("type", "string") })
                    put("max_tool_calls", buildJsonObject { put("type", "integer") })
                    put("disable_tool_budget_stop", buildJsonObject { put("type", "boolean") })
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
                        error("name must start with a letter and contain only letters/digits/underscore: $name")
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

    return copy(
        description = str("description") ?: description,
        systemPrompt = str("system_prompt") ?: systemPrompt,
        chatModelId = str("model_id")?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: chatModelId,
        maxToolCalls = if ("max_tool_calls" in params) {
            int("max_tool_calls")?.coerceIn(1, 256) ?: maxToolCalls
        } else {
            maxToolCalls
        },
        disableToolBudgetStop = bool("disable_tool_budget_stop") ?: disableToolBudgetStop,
    )
}
