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
 * Priority when resolving: per-model override → official-host rules → [OpenAIExtended].
 * Unknown hosts (custom proxies) stay on [OpenAIExtended] passthrough; users opt into a
 * dialect explicitly via the per-model override.
 */
@Serializable
enum class ReasoningDialect {
    /** Resolve from official host rules; unknown hosts default to [OpenAIExtended]. */
    @SerialName("auto")
    Auto,

    /** `low|medium|high|xhigh` (OpenAI extended / OpenRouter-style). */
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
        // Unknown hosts (custom proxies) keep OpenAI-compat passthrough of level.effort;
        // users opt into DeepSeekMax explicitly via the per-model dialect override (#214).
        else -> ReasoningDialect.OpenAIExtended
    }
}

/**
 * Maps an abstract [ReasoningLevel] to an OpenAI-style `reasoning_effort` / `effort` wire token.
 *
 * @return wire token, or `null` when the field should be omitted ([ReasoningLevel.AUTO],
 *   or [ReasoningDialect.OnOffOnly] which only toggles enable/disable in the host branch).
 *   OFF maps to null (field omitted); OpenAI-compat reasoning_effort vocabularies
 *   (OpenAI: low|medium|high, DeepSeek: low|high|max) do not accept "none" (#228).
 */
fun mapReasoningEffort(
    dialect: ReasoningDialect,
    level: ReasoningLevel,
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
        ReasoningDialect.OpenAIExtended -> if (level == ReasoningLevel.OFF) null else level.effort

        ReasoningDialect.OpenAIClassic -> when (level) {
            ReasoningLevel.OFF -> null
            ReasoningLevel.LOW -> "low"
            ReasoningLevel.MEDIUM -> "medium"
            ReasoningLevel.HIGH,
            ReasoningLevel.XHIGH -> "high"
            ReasoningLevel.AUTO -> null
        }

        ReasoningDialect.DeepSeekMax -> when (level) {
            ReasoningLevel.OFF -> null
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
