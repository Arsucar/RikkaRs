package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry

/**
 * Reply-draft context assembly options (issue #196).
 *
 * Stored on [Preset.draftContext]. `null` on the preset means “use code defaults”
 * ([DEFAULT_DRAFT_CONTEXT] / [DraftContextConfig] primary constructor defaults).
 */
@Serializable
data class DraftContextConfig(
    val messageCount: Int = 8,
    /** Per-message char cap; `0` = no truncation. */
    val maxCharsPerMessage: Int = 0,
    val includeMedia: Boolean = false,
    val includeTools: Boolean = true,
    val includeReasoning: Boolean = false,
    /** When true, the latest selected message skips [maxCharsPerMessage] truncation. */
    val keepLatestMessageIntact: Boolean = true,
)

/** Code defaults used when [Preset.draftContext] is null. */
val DEFAULT_DRAFT_CONTEXT = DraftContextConfig()

private const val TOOL_SUMMARY_MAX = 300
private const val REASONING_SUMMARY_MAX = 100

/**
 * Draft-only message projection for reply-draft prompts.
 *
 * Differs from [UIMessage.summaryAsText]:
 * - truncates with [String.takeLast] (keep tail), not head
 * - optional non-text placeholders for media / tools / reasoning
 * - [isLatest] + [DraftContextConfig.keepLatestMessageIntact] can skip truncation
 *
 * Does **not** change title / suggestion / compress paths that still use [UIMessage.summaryAsText].
 */
fun UIMessage.toDraftContextText(
    config: DraftContextConfig,
    isLatest: Boolean,
): String {
    val body = parts.mapNotNull { part -> part.toDraftContextFragment(config) }
        .filter { it.isNotEmpty() }
        .joinToString(separator = "\n")
    val text = "[${role.name}]: $body"
    val shouldTruncate = config.maxCharsPerMessage > 0 &&
        !(isLatest && config.keepLatestMessageIntact)
    if (!shouldTruncate || text.length <= config.maxCharsPerMessage) {
        return text
    }
    // Keep the tail (reply target / conclusion often sits at the end).
    return text.takeLast(config.maxCharsPerMessage)
}

/**
 * Preset that owns the effective reply-draft config for an assistant.
 *
 * Must stay aligned with [me.rerere.rikkahub.data.ai.prompts.resolveBuiltinOverride]
 * for [BuiltinPromptRegistry.KEY_REPLY_DRAFT]:
 * 1. Walk linked presets in [Assistant.presetIds] order.
 * 2. Prefer the first preset that has an **enabled** Builtin(reply_draft) with
 *    non-blank [PresetEntry.Builtin.overrideContent] (same source as the template).
 * 3. Else the first linked preset that has any enabled Builtin(reply_draft)
 *    (default template + that preset's draftContext).
 * 4. Else null → caller uses [DEFAULT_DRAFT_CONTEXT].
 *
 * Presets without enabled reply_draft are ignored so multi-preset assistants
 * cannot pair template from one preset with truncation rules from another (#196).
 */
fun resolveReplyDraftSourcePreset(
    assistant: Assistant?,
    presets: List<Preset>,
): Preset? {
    if (assistant == null || assistant.presetIds.isEmpty()) return null
    val linked = assistant.presetIds
        .asSequence()
        .mapNotNull { id -> presets.firstOrNull { it.id == id } }
        .toList()

    fun Preset.enabledReplyDraftEntries(): Sequence<PresetEntry.Builtin> =
        entries.asSequence()
            .filterIsInstance<PresetEntry.Builtin>()
            .filter { it.enabled && it.builtinKey == BuiltinPromptRegistry.KEY_REPLY_DRAFT }
            .sortedBy { it.order }

    // Same selection as resolveBuiltinOverride: first non-blank override wins.
    linked.firstOrNull { preset ->
        preset.enabledReplyDraftEntries()
            .any { it.overrideContent?.isNotBlank() == true }
    }?.let { return it }

    // No override → still bind context to the first enabled reply_draft preset.
    return linked.firstOrNull { it.enabledReplyDraftEntries().any() }
}

/**
 * Resolve draft context for an assistant from the **same** preset that supplies
 * the effective reply_draft entry (see [resolveReplyDraftSourcePreset]).
 *
 * If that preset has null [Preset.draftContext], or no reply_draft source exists,
 * returns [DEFAULT_DRAFT_CONTEXT] (does not fall through to other presets' config).
 */
fun resolveDraftContextConfig(
    assistant: Assistant?,
    presets: List<Preset>,
): DraftContextConfig {
    return resolveReplyDraftSourcePreset(assistant, presets)?.draftContext
        ?: DEFAULT_DRAFT_CONTEXT
}

/**
 * Build the `{content}` block for reply-draft from recent messages.
 */
fun List<UIMessage>.toDraftContextContent(config: DraftContextConfig): String {
    if (config.messageCount <= 0) return ""
    val selected = takeLast(config.messageCount)
    if (selected.isEmpty()) return ""
    val lastIndex = selected.lastIndex
    return selected.mapIndexed { index, message ->
        message.toDraftContextText(config, isLatest = index == lastIndex)
    }.joinToString("\n\n")
}

private fun UIMessagePart.toDraftContextFragment(config: DraftContextConfig): String? = when (this) {
    is UIMessagePart.Text -> text
    is UIMessagePart.Image -> if (config.includeMedia) "[图片]" else null
    is UIMessagePart.Video -> if (config.includeMedia) "[图片]" else null
    is UIMessagePart.Audio -> if (config.includeMedia) "[图片]" else null
    is UIMessagePart.Document -> if (config.includeMedia) {
        "[文件: ${fileName.ifBlank { "file" }}]"
    } else {
        null
    }
    is UIMessagePart.Tool -> if (config.includeTools) formatToolPlaceholder(toolName, output) else null
    is UIMessagePart.ToolCall -> if (config.includeTools) "[工具: $toolName]" else null
    is UIMessagePart.ToolResult -> if (config.includeTools) {
        val summary = content.toString().take(TOOL_SUMMARY_MAX)
        if (summary.isBlank()) "[工具: $toolName]" else "[工具: $toolName → $summary]"
    } else {
        null
    }
    is UIMessagePart.Reasoning -> if (config.includeReasoning) {
        val summary = reasoning.take(REASONING_SUMMARY_MAX)
        if (summary.isBlank()) null else "[推理: $summary]"
    } else {
        null
    }
    is UIMessagePart.SlashSkill -> null
    UIMessagePart.Search -> null
}

private fun formatToolPlaceholder(toolName: String, output: List<UIMessagePart>): String {
    val summary = output.joinToString(separator = " ") { part ->
        when (part) {
            is UIMessagePart.Text -> part.text
            else -> ""
        }
    }.trim().take(TOOL_SUMMARY_MAX)
    return if (summary.isBlank()) {
        "[工具: $toolName]"
    } else {
        "[工具: $toolName → $summary]"
    }
}
