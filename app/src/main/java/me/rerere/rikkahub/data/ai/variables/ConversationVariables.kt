package me.rerere.rikkahub.data.ai.variables

/**
 * Pure helpers for conversation-level ST-compatible variables (#217/#216).
 * Limits and nested macro expansion live here so transformers and UI share one contract.
 */
object ConversationVariables {
    const val MAX_VARIABLE_COUNT = 200
    const val MAX_VALUE_LENGTH = 32_000
    const val MAX_MACRO_DEPTH = 20

    fun truncateValue(value: String): String =
        if (value.length <= MAX_VALUE_LENGTH) value else value.take(MAX_VALUE_LENGTH)

    /**
     * Apply a transform under count/length limits.
     * New keys beyond [MAX_VARIABLE_COUNT] are dropped; values are truncated.
     */
    fun applyTransform(
        current: Map<String, String>,
        transform: (MutableMap<String, String>) -> Unit,
    ): Map<String, String> {
        val mutable = current.toMutableMap()
        transform(mutable)
        return sanitize(mutable)
    }

    fun sanitize(map: Map<String, String>): Map<String, String> {
        if (map.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>(map.size.coerceAtMost(MAX_VARIABLE_COUNT))
        for ((key, value) in map) {
            val name = key.trim()
            if (name.isEmpty()) continue
            if (result.size >= MAX_VARIABLE_COUNT && name !in result) continue
            result[name] = truncateValue(value)
        }
        return result
    }

    fun putVar(map: MutableMap<String, String>, name: String, value: String) {
        val key = name.trim()
        if (key.isEmpty()) return
        if (key !in map && map.size >= MAX_VARIABLE_COUNT) return
        map[key] = truncateValue(value)
    }

    fun addVar(map: MutableMap<String, String>, name: String, value: String) {
        val key = name.trim()
        if (key.isEmpty()) return
        val existing = map[key].orEmpty()
        if (key !in map && map.size >= MAX_VARIABLE_COUNT) return
        map[key] = truncateValue(existing + value)
    }

    fun removeVar(map: MutableMap<String, String>, name: String) {
        map.remove(name.trim())
    }

    /**
     * Expand ST-style variable macros inner-first (recursive, depth-limited).
     * - getvar → value or ""
     * - getglobalvar → "" (MVP; full global store is out of scope)
     * - setvar/addvar → mutate [variables] and remove the macro from text
     */
    fun expandMacros(
        text: String,
        variables: MutableMap<String, String>,
        depth: Int = 0,
    ): String {
        if (text.isEmpty() || depth > MAX_MACRO_DEPTH) return text
        if (!text.contains("{{")) return text

        var result = text
        var guard = 0
        while (guard < MAX_MACRO_DEPTH) {
            val innermost = findInnermostMacro(result) ?: break
            val (range, body) = innermost
            val replacement = evaluateMacroBody(body, variables, depth)
            result = result.replaceRange(range, replacement)
            guard++
        }
        return result
    }

    /**
     * Find the leftmost non-nested `{{...}}` pair (body contains no `{{`).
     * Nested outers are skipped until their inner leaves are resolved (inner-first);
     * sibling macros then expand left-to-right so setvar runs before a later getvar.
     */
    private fun findInnermostMacro(text: String): Pair<IntRange, String>? {
        var searchFrom = 0
        while (searchFrom < text.length - 1) {
            val start = text.indexOf("{{", searchFrom)
            if (start < 0) break
            val close = text.indexOf("}}", startIndex = start + 2)
            if (close < 0) break
            val body = text.substring(start + 2, close)
            if (!body.contains("{{")) {
                return (start..close + 1) to body
            }
            searchFrom = start + 2
        }
        return null
    }

    private fun evaluateMacroBody(
        body: String,
        variables: MutableMap<String, String>,
        depth: Int,
    ): String {
        val trimmed = body.trim()
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("getvar::") -> {
                val name = trimmed.substringAfter("::").trim()
                variables[name].orEmpty()
            }
            lower.startsWith("getglobalvar::") -> ""
            lower.startsWith("setvar::") -> {
                val rest = trimmed.substringAfter("::")
                val sep = rest.indexOf("::")
                if (sep < 0) return "{{$body}}"
                val name = rest.substring(0, sep)
                val value = rest.substring(sep + 2)
                // Value may still contain macros if outer scan missed; expand recursively.
                val resolved = expandMacros(value, variables, depth + 1)
                putVar(variables, name, resolved)
                ""
            }
            lower.startsWith("addvar::") -> {
                val rest = trimmed.substringAfter("::")
                val sep = rest.indexOf("::")
                if (sep < 0) return "{{$body}}"
                val name = rest.substring(0, sep)
                val value = rest.substring(sep + 2)
                val resolved = expandMacros(value, variables, depth + 1)
                addVar(variables, name, resolved)
                ""
            }
            else -> "{{$body}}" // unknown macro: leave as-is for PlaceholderTransformer etc.
        }
    }
}
