package me.rerere.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReasoningLevel(
    val budgetTokens: Int,
    val effort: String
) {
    @SerialName("off")
    OFF(0, "none"),
    @SerialName("auto")
    AUTO(-1, "auto"),
    @SerialName("low")
    LOW(1_000, "low"),
    @SerialName("medium")
    MEDIUM(2_000, "medium"),
    @SerialName("high")
    HIGH(8_000, "high"),
    @SerialName("xhigh")
    XHIGH(16_000, "xhigh");

    val isEnabled: Boolean
        get() = this != OFF

    companion object {
        fun fromBudgetTokens(budgetTokens: Int?): ReasoningLevel {
            return entries.minByOrNull { kotlin.math.abs(it.budgetTokens - (budgetTokens ?: AUTO.budgetTokens)) } ?: AUTO
        }
    }
}

/**
 * Wire vocabulary for reasoning effort tokens.
 *
 * [ReasoningLevel] is the user-facing abstract ladder (off…xhigh).
 * [ReasoningDialect] decides how that ladder is encoded for a given model/provider
 * (e.g. DeepSeek uses `max` instead of `xhigh`).
 *
 * Priority when resolving: per-model override → host rules → model-id hints → [OpenAIExtended].
 */
@Serializable
enum class ReasoningDialect {
    /** Follow host / model-id recognition. */
    @SerialName("auto")
    Auto,

    /** `none|low|medium|high|xhigh` (OpenAI extended / OpenRouter-style). */
    @SerialName("openai_extended")
    OpenAIExtended,

    /** No `xhigh`; top rung clamps to `high`. */
    @SerialName("openai_classic")
    OpenAIClassic,

    /** DeepSeek-style vocabulary; top rung is `max` (official + many proxies). */
    @SerialName("deepseek_max")
    DeepSeekMax,

    /** Provider only supports enable/disable; effort string is not sent. */
    @SerialName("on_off_only")
    OnOffOnly,
}

/**
 * Resolves the effective dialect used when mapping [ReasoningLevel] to wire tokens.
 *
 * @param explicit value from [me.rerere.ai.provider.Model.reasoningDialect] (default [ReasoningDialect.Auto])
 * @param host provider baseUrl host (e.g. `api.deepseek.com`)
 * @param modelId raw model id string
 */
fun resolveDialect(
    explicit: ReasoningDialect,
    host: String,
    modelId: String,
): ReasoningDialect {
    if (explicit != ReasoningDialect.Auto) return explicit

    val id = modelId.lowercase()
    return when {
        host == "api.deepseek.com" -> ReasoningDialect.DeepSeekMax
        host == "integrate.api.nvidia.com" && "deepseek-v4" in id -> ReasoningDialect.DeepSeekMax
        // Weak hint for proxies whose host is not official DeepSeek.
        looksLikeDeepSeekReasoningModel(id) -> ReasoningDialect.DeepSeekMax
        else -> ReasoningDialect.OpenAIExtended
    }
}

/**
 * Maps an abstract [ReasoningLevel] to an OpenAI-style `reasoning_effort` / `effort` wire token.
 *
 * @return wire token, or `null` when the field should be omitted ([ReasoningLevel.AUTO],
 *   or [ReasoningDialect.OnOffOnly] which only toggles enable/disable in the host branch).
 * @param noneAsLow some Chat Completions defaults historically map OFF/`none` → `"low"`
 *   because those models cannot fully disable reasoning via effort.
 */
fun mapReasoningEffort(
    dialect: ReasoningDialect,
    level: ReasoningLevel,
    noneAsLow: Boolean = false,
): String? {
    if (level == ReasoningLevel.AUTO) return null
    // Auto should already be resolved by resolveDialect; treat residual Auto as extended.
    val effective = if (dialect == ReasoningDialect.Auto) {
        ReasoningDialect.OpenAIExtended
    } else {
        dialect
    }

    return when (effective) {
        ReasoningDialect.Auto,
        ReasoningDialect.OpenAIExtended -> when {
            level == ReasoningLevel.OFF && noneAsLow -> "low"
            else -> level.effort
        }

        ReasoningDialect.OpenAIClassic -> when (level) {
            ReasoningLevel.OFF -> if (noneAsLow) "low" else "none"
            ReasoningLevel.LOW -> "low"
            ReasoningLevel.MEDIUM -> "medium"
            ReasoningLevel.HIGH,
            ReasoningLevel.XHIGH -> "high"
            ReasoningLevel.AUTO -> null
        }

        ReasoningDialect.DeepSeekMax -> when (level) {
            ReasoningLevel.OFF -> if (noneAsLow) "low" else "none"
            ReasoningLevel.LOW -> "low"
            ReasoningLevel.MEDIUM -> "medium"
            ReasoningLevel.HIGH -> "high"
            ReasoningLevel.XHIGH -> "max"
            ReasoningLevel.AUTO -> null
        }

        ReasoningDialect.OnOffOnly -> null
    }
}

/**
 * NVIDIA DeepSeek-V4 Chat Completions historically collapse non-off/non-xhigh levels to `high`.
 * Keep that coarse table so existing nvidia behavior does not regress.
 */
fun mapNvidiaDeepSeekV4Effort(level: ReasoningLevel): String? {
    if (level == ReasoningLevel.AUTO) return null
    return when (level) {
        ReasoningLevel.XHIGH -> "max"
        ReasoningLevel.OFF -> "none"
        else -> "high"
    }
}

private fun looksLikeDeepSeekReasoningModel(modelIdLower: String): Boolean {
    if (!modelIdLower.contains("deepseek")) return false
    return modelIdLower.contains("reasoner") ||
        modelIdLower.contains("r1") ||
        modelIdLower.contains("v3.1") ||
        modelIdLower.contains("v3_1") ||
        modelIdLower.contains("v3-1") ||
        modelIdLower.contains("v3.2") ||
        modelIdLower.contains("v3_2") ||
        modelIdLower.contains("v3-2") ||
        modelIdLower.contains("v4")
}
