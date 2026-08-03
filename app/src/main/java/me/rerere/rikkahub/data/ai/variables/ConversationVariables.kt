package me.rerere.rikkahub.data.ai.variables

/**
 * Pure helpers for conversation-level ST-compatible variables (#217/#216).
 * Limits and nested macro expansion live here so transformers and UI share one contract.
 */
object ConversationVariables {
    const val MAX_VARIABLE_COUNT = 200
    const val MAX_VALUE_LENGTH = 32_000
    /** Nested [expandMacros] recursion depth (e.g. setvar value containing macros). */
    const val MAX_MACRO_DEPTH = 20
    /**
     * Full-text expansion budget: how many macros may be replaced in one top-level pass.
     * Separate from [MAX_MACRO_DEPTH] so large presets (50–200+ setvar/getvar) all expand (#226).
     */
    const val MAX_MACRO_EXPANSIONS = 2000

    /**
     * Temporary stand-in for unknown/passthrough macros so outer macros can still expand.
     * Must not contain `{{` / `}}` or look like ST macro syntax.
     */
    private const val PASS_OPEN = "\uE000VARPASS:"
    private const val PASS_CLOSE = "\uE001"

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
     * - `{{//...}}` ST comments → deleted
     * - `{{newline}}` → "\n"; `{{trim}}` → "" (MVP: remove macro so it does not block later ones)
     * - Unknown macros left as-is without burning the expansion budget (#226)
     */
    fun expandMacros(
        text: String,
        variables: MutableMap<String, String>,
        depth: Int = 0,
    ): String {
        if (text.isEmpty() || depth > MAX_MACRO_DEPTH) return text
        if (!text.contains("{{")) return text

        // Top-level owns passthrough tokens so nested setvar values can hold unknown
        // macros (e.g. {{char}}) without blocking the outer macro (#226 follow-up).
        val passthrough = mutableListOf<String>()
        var result = expandMacrosInternal(text, variables, depth, passthrough)
        if (passthrough.isNotEmpty()) {
            result = restorePassthrough(result, passthrough)
            // Values written while tokens were active must also be restored.
            for (key in variables.keys.toList()) {
                val value = variables[key] ?: continue
                val restored = restorePassthrough(value, passthrough)
                if (restored != value) {
                    variables[key] = restored
                }
            }
        }
        return result
    }

    private fun expandMacrosInternal(
        text: String,
        variables: MutableMap<String, String>,
        depth: Int,
        passthrough: MutableList<String>,
    ): String {
        if (text.isEmpty() || depth > MAX_MACRO_DEPTH) return text
        if (!text.contains("{{")) return text

        var result = text
        var expansions = 0
        while (expansions < MAX_MACRO_EXPANSIONS) {
            val innermost = findInnermostMacro(result) ?: break
            val (range, body) = innermost
            val replacement = evaluateMacroBody(body, variables, depth, passthrough)
            // Unchanged unknown/passthrough: park as a non-macro token so outer macros expand.
            if (replacement == "{{$body}}") {
                val id = passthrough.size
                passthrough.add(replacement)
                result = result.replaceRange(range, "$PASS_OPEN$id$PASS_CLOSE")
                // Do not count toward expansion budget (many placeholders are common).
                continue
            }
            result = result.replaceRange(range, replacement)
            expansions++
        }
        return result
    }

    private fun restorePassthrough(text: String, passthrough: List<String>): String {
        if (passthrough.isEmpty() || !text.contains(PASS_OPEN)) return text
        var out = text
        // Highest ids first so id 10 is not clobbered by id 1 prefix.
        for (i in passthrough.indices.reversed()) {
            out = out.replace("$PASS_OPEN$i$PASS_CLOSE", passthrough[i])
        }
        return out
    }

    /**
     * Find the leftmost non-nested `{{...}}` pair (body contains no `{{`).
     * Nested outers are skipped until their inner leaves are resolved (inner-first);
     * sibling macros then expand left-to-right so setvar runs before a later getvar.
     */
    private fun findInnermostMacro(text: String): Pair<IntRange, String>? {
        var from = 0
        while (from < text.length - 1) {
            val start = text.indexOf("{{", from)
            if (start < 0) break
            val close = text.indexOf("}}", startIndex = start + 2)
            if (close < 0) break
            val body = text.substring(start + 2, close)
            if (!body.contains("{{")) {
                return (start..close + 1) to body
            }
            from = start + 2
        }
        return null
    }

    private fun evaluateMacroBody(
        body: String,
        variables: MutableMap<String, String>,
        depth: Int,
        passthrough: MutableList<String>,
    ): String {
        val trimmed = body.trim()
        val lower = trimmed.lowercase()
        return when {
            // ST comment: {{// ...}} — delete so it never blocks later macros (#226)
            trimmed.startsWith("//") -> ""
            // Common ST whitespace helpers (MVP)
            lower == "newline" -> "\n"
            lower == "trim" -> ""
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
                val resolved = expandMacrosInternal(value, variables, depth + 1, passthrough)
                putVar(variables, name, resolved)
                ""
            }
            lower.startsWith("addvar::") -> {
                val rest = trimmed.substringAfter("::")
                val sep = rest.indexOf("::")
                if (sep < 0) return "{{$body}}"
                val name = rest.substring(0, sep)
                val value = rest.substring(sep + 2)
                val resolved = expandMacrosInternal(value, variables, depth + 1, passthrough)
                addVar(variables, name, resolved)
                ""
            }
            // Unknown macro: leave as-is for PlaceholderTransformer etc. (#226 skips re-scan)
            else -> "{{$body}}"
        }
    }
}
