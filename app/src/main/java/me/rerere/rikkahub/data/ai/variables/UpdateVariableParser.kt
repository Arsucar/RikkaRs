package me.rerere.rikkahub.data.ai.variables

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Parses SillyTavern-style MVU `<UpdateVariable>…</UpdateVariable>` blocks and applies
 * JSON Patch ops (add/replace/remove) on `/varname` string values.
 *
 * Success: returns stripped text + updated map.
 * Failure (malformed / incomplete): returns original text unchanged and same map.
 */
object UpdateVariableParser {
    private val blockRegex = Regex(
        """<UpdateVariable>([\s\S]*?)</UpdateVariable>""",
        RegexOption.IGNORE_CASE,
    )

    private val strictJson = Json {
        isLenient = false
        ignoreUnknownKeys = false
    }

    data class Result(
        val text: String,
        val variables: Map<String, String>,
        val applied: Boolean,
    )

    fun apply(
        text: String,
        current: Map<String, String>,
    ): Result {
        if (text.isEmpty() || !text.contains("UpdateVariable", ignoreCase = true)) {
            return Result(text = text, variables = current, applied = false)
        }
        // Incomplete streaming block: keep original (caller may buffer).
        if (hasOpenUnclosedBlock(text)) {
            return Result(text = text, variables = current, applied = false)
        }

        val matches = blockRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            return Result(text = text, variables = current, applied = false)
        }

        val mutable = current.toMutableMap()
        var anyApplied = false
        var failed = false
        val rangesToStrip = mutableListOf<IntRange>()

        for (match in matches) {
            val body = match.groupValues[1].trim()
            val ops = parseOps(body)
            if (ops == null) {
                failed = true
                break
            }
            val ok = applyOps(mutable, ops)
            if (!ok) {
                failed = true
                break
            }
            anyApplied = true
            rangesToStrip += match.range
        }

        if (failed || !anyApplied) {
            return Result(text = text, variables = current, applied = false)
        }

        val stripped = stripRanges(text, rangesToStrip).trim()
        return Result(
            text = stripped,
            variables = ConversationVariables.sanitize(mutable),
            applied = true,
        )
    }

    private fun hasOpenUnclosedBlock(text: String): Boolean {
        val open = Regex("""<UpdateVariable\b""", RegexOption.IGNORE_CASE)
        val close = Regex("""</UpdateVariable>""", RegexOption.IGNORE_CASE)
        val openCount = open.findAll(text).count()
        val closeCount = close.findAll(text).count()
        return openCount > closeCount
    }

    private val jsonPatchXmlRegex = Regex(
        """<JSONPatch\b[^>]*>([\s\S]*?)</JSONPatch>""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseOps(body: String): List<JsonObject>? {
        parseJsonOps(body)?.let { return it }
        // ST MVU shell: <Analysis>…</Analysis><JSONPatch>[…]</JSONPatch>
        val xmlInner = jsonPatchXmlRegex.find(body)?.groupValues?.get(1)?.trim()
        if (!xmlInner.isNullOrEmpty()) {
            return parseJsonOps(xmlInner)
        }
        return null
    }

    private fun parseJsonOps(payload: String): List<JsonObject>? {
        val element = runCatching { strictJson.parseToJsonElement(payload) }.getOrNull() ?: return null
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> {
                // Allow wrapper objects with Analysis/JSONPatch style keys if present.
                val nested = element["JSONPatch"]
                    ?: element["jsonPatch"]
                    ?: element["ops"]
                    ?: element["patch"]
                when (nested) {
                    is JsonArray -> nested
                    else -> return null
                }
            }
            else -> return null
        }
        return array.mapNotNull { it as? JsonObject }
    }

    private fun applyOps(map: MutableMap<String, String>, ops: List<JsonObject>): Boolean {
        if (ops.isEmpty()) return true
        for (opObj in ops) {
            val op = (opObj["op"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: return false
            val path = (opObj["path"] as? JsonPrimitive)?.contentOrNull ?: return false
            val name = pathToName(path) ?: return false
            when (op) {
                "add", "replace" -> {
                    val valueElement = opObj["value"] ?: return false
                    val value = jsonValueAsString(valueElement) ?: return false
                    ConversationVariables.putVar(map, name, value)
                }
                "remove" -> {
                    ConversationVariables.removeVar(map, name)
                }
                else -> return false
            }
        }
        return true
    }

    private fun pathToName(path: String): String? {
        val trimmed = path.trim()
        if (!trimmed.startsWith("/")) return null
        val name = trimmed.removePrefix("/").trim()
        if (name.isEmpty() || name.contains('/')) return null
        return name
    }

    private fun jsonValueAsString(element: kotlinx.serialization.json.JsonElement): String? = when (element) {
        is JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        is JsonObject, is JsonArray -> element.toString()
    }

    private fun stripRanges(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val sorted = ranges.sortedByDescending { it.first }
        var result = text
        for (range in sorted) {
            result = result.removeRange(range)
        }
        // Collapse leftover double blank lines from stripped blocks.
        return result.replace(Regex("\n{3,}"), "\n\n")
    }
}
