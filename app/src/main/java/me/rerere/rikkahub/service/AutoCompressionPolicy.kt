package me.rerere.rikkahub.service

import kotlin.math.max
import kotlin.math.min

/** Explicitly classifies why the first generation step is being invoked. */
enum class GenerationInvocationKind {
    NormalSend,
    Regenerate,
    ToolContinuation,
    Preview,
}

fun GenerationInvocationKind.supportsAutoCompression(providerInputAvailable: Boolean): Boolean =
    this == GenerationInvocationKind.NormalSend && providerInputAvailable

enum class AutoCompressionDecision {
    Disabled,
    BelowThreshold,
    InvalidConfig,
    UnsupportedFlow,
    NotCompressible,
    Busy,
    Duplicate,
    Disarmed,
    CoolingDown,
    Trigger,
}

data class AutoCompressionConfig(
    val enabled: Boolean,
    val thresholdTokens: Int,
    val targetTokens: Int,
    val keepRecentMessages: Int,
    val identity: String,
) {
    internal val signature: String
        get() = "$identity|$enabled|$thresholdTokens|$targetTokens|$keepRecentMessages"
}

data class AutoCompressionRuntimeState(
    val armed: Boolean = true,
    val lastAttemptFingerprint: String? = null,
    val failedVisibleMessageCount: Int? = null,
    val configSignature: String? = null,
)

data class AutoCompressionPolicyInput(
    val config: AutoCompressionConfig,
    val invocationKind: GenerationInvocationKind,
    val providerInputAvailable: Boolean,
    val promptTokens: Int,
    val visibleMessageCount: Int,
    val fingerprint: String,
    val busy: Boolean,
)

data class AutoCompressionEvaluation(
    val decision: AutoCompressionDecision,
    val previousState: AutoCompressionRuntimeState,
    val nextState: AutoCompressionRuntimeState,
    val lowWatermarkTokens: Int? = null,
)

fun evaluateAutoCompression(
    input: AutoCompressionPolicyInput,
    currentState: AutoCompressionRuntimeState,
): AutoCompressionEvaluation {
    val config = input.config
    val configuredState = if (currentState.configSignature == config.signature) {
        currentState
    } else {
        AutoCompressionRuntimeState(configSignature = config.signature)
    }

    fun result(
        decision: AutoCompressionDecision,
        nextState: AutoCompressionRuntimeState = configuredState,
        lowWatermark: Int? = null,
    ) = AutoCompressionEvaluation(
        decision = decision,
        previousState = currentState,
        nextState = nextState,
        lowWatermarkTokens = lowWatermark,
    )

    if (!config.enabled) {
        return result(
            decision = AutoCompressionDecision.Disabled,
            nextState = AutoCompressionRuntimeState(configSignature = config.signature),
        )
    }

    val lowWatermark = calculateAutoCompressionLowWatermark(
        thresholdTokens = config.thresholdTokens,
        targetTokens = config.targetTokens,
    ) ?: return result(AutoCompressionDecision.InvalidConfig)

    if (
        config.keepRecentMessages < 0 ||
        input.promptTokens < 0 ||
        input.visibleMessageCount < 0 ||
        input.fingerprint.isBlank()
    ) {
        return result(AutoCompressionDecision.InvalidConfig, lowWatermark = lowWatermark)
    }

    if (!input.invocationKind.supportsAutoCompression(input.providerInputAvailable)) {
        return result(AutoCompressionDecision.UnsupportedFlow, lowWatermark = lowWatermark)
    }

    var state = configuredState
    if (input.promptTokens <= lowWatermark) {
        state = state.copy(
            armed = true,
            lastAttemptFingerprint = null,
            failedVisibleMessageCount = null,
        )
    }

    if (input.promptTokens < config.thresholdTokens) {
        return result(
            decision = AutoCompressionDecision.BelowThreshold,
            nextState = state,
            lowWatermark = lowWatermark,
        )
    }

    if (input.visibleMessageCount <= config.keepRecentMessages) {
        return result(
            decision = AutoCompressionDecision.NotCompressible,
            nextState = state,
            lowWatermark = lowWatermark,
        )
    }

    if (input.busy) {
        return result(
            decision = AutoCompressionDecision.Busy,
            nextState = state,
            lowWatermark = lowWatermark,
        )
    }

    state.failedVisibleMessageCount?.let { failedCount ->
        val retryGrowth = max(4, config.keepRecentMessages / 4)
        val retryAt = failedCount.toLong() + retryGrowth.toLong()
        if (input.visibleMessageCount.toLong() < retryAt) {
            return result(
                decision = AutoCompressionDecision.CoolingDown,
                nextState = state,
                lowWatermark = lowWatermark,
            )
        }
        // Failure growth is an explicit retry escape hatch, even if the estimate never fell to L.
        state = state.copy(
            armed = true,
            failedVisibleMessageCount = null,
        )
    }

    if (state.lastAttemptFingerprint == input.fingerprint) {
        return result(
            decision = AutoCompressionDecision.Duplicate,
            nextState = state,
            lowWatermark = lowWatermark,
        )
    }

    if (!state.armed) {
        return result(
            decision = AutoCompressionDecision.Disarmed,
            nextState = state,
            lowWatermark = lowWatermark,
        )
    }

    return result(
        decision = AutoCompressionDecision.Trigger,
        nextState = state.copy(
            armed = false,
            lastAttemptFingerprint = input.fingerprint,
            failedVisibleMessageCount = null,
        ),
        lowWatermark = lowWatermark,
    )
}

fun calculateAutoCompressionLowWatermark(
    thresholdTokens: Int,
    targetTokens: Int,
): Int? {
    if (thresholdTokens <= 0 || targetTokens < 0) return null
    val high = thresholdTokens.toLong()
    val target = targetTokens.toLong()
    val threeQuarters = high * 3L / 4L
    val low = min(high - 1L, max(target, threeQuarters))
    return low.takeIf { it >= 0L && it < high }?.toInt()
}

internal fun AutoCompressionRuntimeState.withFailureAt(
    visibleMessageCount: Int,
): AutoCompressionRuntimeState = copy(
    armed = false,
    failedVisibleMessageCount = visibleMessageCount.coerceAtLeast(0),
)
