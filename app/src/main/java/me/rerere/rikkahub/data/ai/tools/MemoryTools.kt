package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryScope
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate

fun buildMemoryTools(
    json: Json,
    defaultScope: MemoryScope = MemoryScope.ASSISTANT,
    onList: suspend () -> List<AssistantMemory>,
    onCreation: suspend (String, MemoryScope) -> AssistantMemory,
    onUpdate: suspend (Int, String, MemoryScope?) -> AssistantMemory,
    onDelete: suspend (Int) -> Unit
): List<Tool> = listOf(
    Tool(
        name = "memory_tool",
        description = """
            The memory tool stores long-term information across conversations.
            Use `action` to control the operation: `list` (inspect effective records), `create` (add), `edit` (update), `delete` (remove).
            - Need to inspect existing records: `list`
            - No relevant record: `create` + `content`
            - Existing relevant record: `edit` + `id` + `content`
            - Outdated/irrelevant record: `delete` + `id`
            `scope` is optional: `assistant` stores memory only for the current assistant; `global` shares it across assistants.
            Default scope for new records is `${defaultScope.toolValue}`.
            Memories will automatically appear in the <memories> tag in later conversations.
            Do not store sensitive information (e.g., ethnicity, religion, sexual orientation, political views, sex life, criminal records).
            You may store: preferred name, preferences, plans, work-related notes, chat style preferences, first chat time, etc.
            Do not show memory content directly in the conversation unless the user explicitly asks.
            Today is ${LocalDate.now().toLocalString(true)}.
            Similar memories should be merged; prefer updating existing records.

            Examples:
            {"action":"list"}
            {"action":"create","content":"User prefers brief replies and is more active on weekends."}
            {"action":"create","scope":"global","content":"User prefers Chinese replies across assistants."}
            {"action":"edit","id":12,"content":"User’s preferred name updated to “A-Xing”, prefers Chinese replies."}
            {"action":"edit","id":12,"scope":"assistant","content":"This preference is only relevant to this assistant."}
            {"action":"delete","id":7}
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                add("list")
                                add("create")
                                add("edit")
                                add("delete")
                            }
                        )
                        put("description", "Operation to perform: list, create, edit, or delete")
                    })
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The id of the memory record (required for edit/delete)")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The content of the memory record (required for create/edit)")
                    })
                    put("scope", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                add("assistant")
                                add("global")
                            }
                        )
                        put("description", "Optional memory scope: assistant or global")
                    })
                },
                required = listOf("action")
            )
        },
        execute = {
            val params = it.jsonObject
            val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val payload = when (action) {
                "list" -> {
                    json.encodeToJsonElement(ListSerializer(AssistantMemory.serializer()), onList())
                }

                "create" -> {
                    val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                    val scope = params["scope"].toMemoryScopeOrNull() ?: defaultScope
                    json.encodeToJsonElement(AssistantMemory.serializer(), onCreation(content, scope))
                }

                "edit" -> {
                    val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                    val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                    val scope = params["scope"].toMemoryScopeOrNull()
                    json.encodeToJsonElement(AssistantMemory.serializer(), onUpdate(id, content, scope))
                }

                "delete" -> {
                    val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                    onDelete(id)
                    buildJsonObject {
                        put("success", true)
                        put("id", id)
                    }
                }

                else -> error("unknown action: $action, must be one of [list, create, edit, delete]")
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    )
)

private val MemoryScope.toolValue: String
    get() = when (this) {
        MemoryScope.ASSISTANT -> "assistant"
        MemoryScope.GLOBAL -> "global"
    }

private fun kotlinx.serialization.json.JsonElement?.toMemoryScopeOrNull(): MemoryScope? {
    return when (this?.jsonPrimitive?.contentOrNull?.lowercase()) {
        "assistant", "local" -> MemoryScope.ASSISTANT
        "global" -> MemoryScope.GLOBAL
        null -> null
        else -> error("scope must be one of [assistant, global]")
    }
}
