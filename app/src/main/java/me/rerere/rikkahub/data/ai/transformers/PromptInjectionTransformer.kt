package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.effectivePosition
import me.rerere.rikkahub.data.model.extractContextForMatching
import me.rerere.rikkahub.data.model.isTriggered
import me.rerere.rikkahub.data.model.inPresetDisplayOrder
import kotlin.uuid.Uuid

/**
 * 提示词注入转换器
 *
 * #259: 仅展开助手绑定预设的 entries + Lorebook；无独立 ModeInjection 直连路径。
 */
object PromptInjectionTransformer : InputMessageTransformer {
    override val previewPolicy: PreviewTransformPolicy = PreviewTransformPolicy.SideEffectFree
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return transformMessages(
            messages = messages,
            assistant = ctx.assistant,
            lorebooks = ctx.settings.lorebooks,
            conversationLorebookIds = ctx.conversationLorebookIds,
            presets = ctx.settings.presets,
        )
    }
}

/**
 * 核心注入逻辑（可测试的纯函数）
 */
internal fun transformMessages(
    messages: List<UIMessage>,
    assistant: Assistant,
    lorebooks: List<Lorebook>,
    conversationLorebookIds: Set<Uuid> = emptySet(),
    presets: List<Preset> = emptyList(),
): List<UIMessage> {
    val injections = collectInjections(
        messages = messages,
        assistant = assistant,
        lorebooks = lorebooks,
        conversationLorebookIds = conversationLorebookIds,
        presets = presets,
    )

    if (injections.isEmpty()) {
        return messages
    }

    val byPosition = injections
        .sortedWith(compareByDescending { it.priority })
        .groupBy { it.position }

    return applyInjections(messages, byPosition)
}

/**
 * 收集需要注入的内容（#259: 仅 entries + lorebook）
 */
internal fun collectInjections(
    messages: List<UIMessage>,
    assistant: Assistant,
    lorebooks: List<Lorebook>,
    conversationLorebookIds: Set<Uuid> = emptySet(),
    presets: List<Preset> = emptyList(),
): List<PromptInjection> {
    val injections = mutableListOf<PromptInjection>()
    // 对话级 lorebook 仍可覆盖（助手开启 conversation system prompt 等场景外，
    // 对话级注入开关已删除；此处保留 conversationLorebookIds 透传兼容 ChatService）。
    val effectiveLorebookIds = conversationLorebookIds.ifEmpty { assistant.lorebookIds }
    val effectivePresetIds = assistant.presetIds
    val activePresets = presets.filter { it.id in effectivePresetIds }

    val injectedIds = mutableSetOf<Uuid>()
    // 展开预设 entries：每个启用条目就地解析为 ResolvedInjection
    activePresets
        .filter { it.hasEntries() }
        .forEach { preset ->
            preset.entries
                .inPresetDisplayOrder()
                .forEachIndexed { displayIndex, entry ->
                    if (!entry.enabled) return@forEachIndexed
                    resolvePresetEntry(
                        entry = entry,
                        fallbackPriority = -displayIndex,
                    )?.let { resolved ->
                        if (injectedIds.add(resolved.deduplicationId)) {
                            injections.add(resolved.injection)
                        }
                    }
                }
        }

    // Lorebook 中被触发的 RegexInjection
    val enabledLorebooks = lorebooks.filter {
        it.enabled && effectiveLorebookIds.contains(it.id)
    }
    if (enabledLorebooks.isNotEmpty()) {
        val nonSystemMessages = messages.filter { it.role != MessageRole.SYSTEM }

        enabledLorebooks.forEach { lorebook ->
            lorebook.entries
                .filter { entry ->
                    val context = extractContextForMatching(nonSystemMessages, entry.scanDepth)
                    entry.isTriggered(context)
                }
                .forEach { injections.add(it) }
        }
    }

    return injections
}

/**
 * 将一个启用的 [PresetEntry] 解析为可注入的 [PromptInjection.ResolvedInjection]。
 *
 * - [PresetEntry.Custom]：直接用内嵌内容。
 * - [PresetEntry.Builtin]：查 [BuiltinPromptRegistry]；未知 key / 非 injectable / 空内容则跳过。
 *
 * order → priority 采用 `-order` 映射；迁移条目的 legacyPriority 优先。
 */
private data class ResolvedPresetEntry(
    val injection: PromptInjection.ResolvedInjection,
    val deduplicationId: Uuid,
)

private fun resolvePresetEntry(
    entry: PresetEntry,
    fallbackPriority: Int,
): ResolvedPresetEntry? {
    val content: String
    val role: MessageRole
    val deduplicationId = entry.id
    val position: InjectionPosition = entry.effectivePosition()
    var priority = fallbackPriority
    when (entry) {
        is PresetEntry.Custom -> {
            content = entry.content
            role = entry.role
            entry.legacyPriority?.let { priority = it }
        }

        is PresetEntry.Builtin -> {
            val def = BuiltinPromptRegistry[entry.builtinKey] ?: return null
            if (!def.injectable) return null
            val override = entry.overrideContent?.takeIf { def.overridable }
            content = override ?: def.defaultContent
            role = entry.role
        }
    }
    if (content.isBlank()) return null
    return ResolvedPresetEntry(
        injection = PromptInjection.ResolvedInjection(
            id = entry.id,
            enabled = true,
            priority = priority,
            position = position,
            content = content,
            injectDepth = entry.injectDepth,
            role = role,
        ),
        deduplicationId = deduplicationId,
    )
}

/**
 * 应用注入到消息列表
 */
internal fun applyInjections(
    messages: List<UIMessage>,
    byPosition: Map<InjectionPosition, List<PromptInjection>>
): List<UIMessage> {
    val result = messages.toMutableList()

    // 找到系统消息的索引（通常是第一条）
    val systemIndex = result.indexOfFirst { it.role == MessageRole.SYSTEM }

    // 处理 BEFORE_SYSTEM_PROMPT 和 AFTER_SYSTEM_PROMPT
    if (systemIndex >= 0) {
        val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""
        val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""

        if (beforeContent.isNotEmpty() || afterContent.isNotEmpty()) {
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }

            val newText = buildString {
                if (beforeContent.isNotEmpty()) {
                    append(beforeContent)
                    appendLine()
                }
                append(originalText)
                if (afterContent.isNotEmpty()) {
                    appendLine()
                    append(afterContent)
                }
            }

            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText)),
                isSynthetic = true,
            )
        }
    } else {
        // 没有系统消息时，创建一个新的系统消息
        val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""
        val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""

        val combinedContent = buildString {
            if (beforeContent.isNotEmpty()) {
                append(beforeContent)
            }
            if (afterContent.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                append(afterContent)
            }
        }

        if (combinedContent.isNotEmpty()) {
            result.add(0, UIMessage.system(combinedContent).copy(isSynthetic = true))
        }
    }

    // 处理 TOP_OF_CHAT：在第一条用户消息之前插入
    val topInjections = byPosition[InjectionPosition.TOP_OF_CHAT]
    if (!topInjections.isNullOrEmpty()) {
        // 重新计算索引（因为可能插入了系统消息）
        var insertIndex = result.indexOfFirst { it.role == MessageRole.USER }
            .takeIf { it >= 0 } ?: result.size
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(topInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 BOTTOM_OF_CHAT：在最后一条消息之前插入
    val bottomInjections = byPosition[InjectionPosition.BOTTOM_OF_CHAT]
    if (!bottomInjections.isNullOrEmpty()) {
        var insertIndex = (result.size - 1).coerceAtLeast(0)
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(bottomInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 AT_DEPTH：在指定深度位置插入（从最新消息往前数）
    // 按 injectDepth 分组，相同深度的合并，按深度从大到小处理（避免索引变化问题）
    val atDepthInjections = byPosition[InjectionPosition.AT_DEPTH]
    if (!atDepthInjections.isNullOrEmpty()) {
        val byDepth = atDepthInjections.groupBy { it.injectDepth }
        byDepth.keys.sortedDescending().forEach { depth ->
            val injections = byDepth[depth] ?: return@forEach
            // 计算插入位置：result.size - depth，但要确保在有效范围内
            // depth=1 表示在最后一条消息之前，depth=2 表示在倒数第二条之前...
            var insertIndex = (result.size - depth.coerceAtLeast(1)).coerceIn(0, result.size)
            insertIndex = findSafeInsertIndex(result, insertIndex)
            createMergedInjectionMessages(injections).forEach { message ->
                result.add(insertIndex, message)
                insertIndex++
            }
        }
    }

    return result
}

/**
 * 将同一 role 的注入合并成消息列表
 * 按 role 分组后合并内容，返回合并后的消息列表
 */
private fun createMergedInjectionMessages(injections: List<PromptInjection>): List<UIMessage> {
    return injections
        .groupBy { it.role }
        .map { (role, grouped) ->
            val mergedContent = grouped.joinToString("\n") { it.content }
            when (role) {
                MessageRole.ASSISTANT -> UIMessage.assistant(mergedContent)
                else -> UIMessage.user(mergedContent)
            }.copy(
                isSynthetic = true,
            )
        }
}

/**
 * 查找安全的插入位置，避免注入到 USER → ASSISTANT(含Tool) 之间
 *
 * 某些提供商（如 deepseek）要求 USER 之后紧跟带工具的 ASSISTANT，
 * 在两者之间插入消息会导致报错或破坏推理连续性。
 */
internal fun findSafeInsertIndex(messages: List<UIMessage>, targetIndex: Int): Int {
    var index = targetIndex.coerceIn(0, messages.size)

    // 向前查找，直到找到一个安全的位置
    while (index > 0) {
        val prevMessage = messages.getOrNull(index - 1)
        val currentMessage = messages.getOrNull(index)

        // 不能插入到 USER → ASSISTANT(含Tool) 之间
        val isPrevUser = prevMessage?.role == MessageRole.USER
        val isCurrentAssistantWithTools = currentMessage?.role == MessageRole.ASSISTANT
            && currentMessage.getTools().isNotEmpty()

        if (isPrevUser && isCurrentAssistantWithTools) {
            index--
        } else {
            break
        }
    }

    return index
}
