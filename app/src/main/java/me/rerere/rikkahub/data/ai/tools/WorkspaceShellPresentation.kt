package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.common.http.jsonObjectOrNull

const val WORKSPACE_SHELL_TOOL_NAME: String = "workspace_shell"

data class WorkspaceShellCommandInfo(
    val command: String? = null,
    val cwd: String? = null,
    val continuationPath: String? = null,
) {
    val hasCommand: Boolean get() = !command.isNullOrBlank()
    val isContinuationRead: Boolean get() = !continuationPath.isNullOrBlank()
}

fun workspaceShellCommandInfo(arguments: JsonElement?, rawInput: String? = null): WorkspaceShellCommandInfo {
    val command = arguments.string("command")
    val cwd = arguments.string("cwd")
    val continuationPath = arguments.string("continuation_path")
        ?: detectToolOutputPath(command)

    if (!command.isNullOrBlank() || !cwd.isNullOrBlank() || !continuationPath.isNullOrBlank()) {
        return WorkspaceShellCommandInfo(
            command = command?.trim()?.takeIf { it.isNotBlank() },
            cwd = cwd?.trim()?.takeIf { it.isNotBlank() },
            continuationPath = continuationPath,
        )
    }

    return rawInput?.let(::workspaceShellCommandInfoFromRawInput) ?: WorkspaceShellCommandInfo()
}

fun workspaceShellCommandInfoFromRawInput(rawInput: String): WorkspaceShellCommandInfo {
    val parsed = runCatching { Json.parseToJsonElement(rawInput) }.getOrNull()
    if (parsed != null) {
        val fromJson = workspaceShellCommandInfo(parsed)
        if (fromJson.hasCommand || !fromJson.cwd.isNullOrBlank() || fromJson.isContinuationRead) {
            return fromJson
        }
    }

    val command = extractJsonStringField(rawInput, "command")?.trim()?.takeIf { it.isNotBlank() }
    val cwd = extractJsonStringField(rawInput, "cwd")?.trim()?.takeIf { it.isNotBlank() }
    return WorkspaceShellCommandInfo(
        command = command,
        cwd = cwd,
        continuationPath = detectToolOutputPath(command),
    )
}

fun workspaceShellTranscriptInput(rawInput: String, maxCommandChars: Int): String {
    val info = workspaceShellCommandInfoFromRawInput(rawInput)
    val command = info.command ?: return truncate(rawInput, maxCommandChars)
    return buildJsonObject {
        put("command", truncate(command, maxCommandChars))
        if (!info.cwd.isNullOrBlank()) put("cwd", info.cwd)
        if (!info.continuationPath.isNullOrBlank()) put("continuation_path", info.continuationPath)
    }.toString()
}

fun WorkspaceShellCommandInfo.commandPreview(maxChars: Int): String? =
    command
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { truncate(it, maxChars) }

private fun JsonElement?.string(name: String): String? =
    runCatching {
        this?.jsonObjectOrNull?.get(name)?.jsonPrimitive?.contentOrNull
    }.getOrNull()

private fun detectToolOutputPath(command: String?): String? =
    command
        ?.let { TOOL_OUTPUTS_PATH_REGEX.find(it)?.value }
        ?.trimEnd('.', ',', ':', ';')

private fun extractJsonStringField(rawInput: String, field: String): String? {
    val fieldStart = Regex(""""${Regex.escape(field)}"\s*:\s*"""")
        .find(rawInput)
        ?.range
        ?.last
        ?.plus(1)
        ?: return null
    val builder = StringBuilder()
    var pendingEscape = false
    for (index in fieldStart until rawInput.length) {
        val char = rawInput[index]
        when {
            pendingEscape -> {
                builder.append('\\')
                builder.append(char)
                pendingEscape = false
            }

            char == '\\' -> pendingEscape = true
            char == '"' -> break
            else -> builder.append(char)
        }
    }
    val escaped = builder.toString().trimEnd('\u2026')
    return runCatching {
        Json.decodeFromString<String>("\"$escaped\"")
    }.getOrNull()
}

private fun truncate(text: String, maxChars: Int): String =
    if (maxChars <= 0 || text.length <= maxChars) text else text.take(maxChars) + "..."

private val TOOL_OUTPUTS_PATH_REGEX = Regex("""/tool_outputs/[^\s`'"|;&)]+""")
