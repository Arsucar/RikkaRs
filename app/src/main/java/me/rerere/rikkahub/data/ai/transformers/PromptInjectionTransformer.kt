package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.datastore.boundPresetInjectionIds
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
 * 根据 Assistant 关联的 ModeInjection 和 Lorebook 进行提示词注入
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
            modeInjections = ctx.settings.modeInjections,
            lorebooks = ctx.settings.lorebooks,
            conversationModeInjectionIds = ctx.conversationModeInjectionIds,
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
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    presets: List<Preset> = emptyList(),
): List<UIMessage> {
    // 收集所有需要注入的内容
    val injections = collectInjections(
        messages = messages,
        assistant = assistant,
        modeInjections = modeInjections,
        lorebooks = lorebooks,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
        presets = presets,
    )

    if (injections.isEmpty()) {
        return messages
    }

    // 按位置和优先级分组
    val modeInjectionOrder = modeInjections.withIndex().associate { (index, injection) -> injection.id to index }
    val byPosition = injections
        .sortedWith(
            compareByDescending<PromptInjection> { it.priority }
                .thenBy { modeInjectionOrder[it.id] ?: Int.MAX_VALUE }
        )
        .groupBy { it.position }

    // 应用注入
    return applyInjections(messages, byPosition)
}

/**
 * 收集需要注入的内容
 */
internal fun collectInjections(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    presets: List<Preset> = emptyList(),
): List<PromptInjection> {
    val injections = mutableListOf<PromptInjection>()
    val effectiveModeInjectionIds = if (assistant.allowConversationPromptInjection) {
        conversationModeInjectionIds
    } else {
        // #205: 组装期只读去重（替代 #201 的加载期持久化删除）。
        // 跳过与已绑定预设条目（启用条目 + Reference 引用的全局 id，语义与 #201 的
        // boundPresetInjectionIds 一致）重复的直连 id；直连绑定数据保留在 DataStore 不销毁，
        // 关闭预设后直连恢复生效、system 消息不残留旧注入。
        assistant.modeInjectionIds
            .filterNot { it in boundPresetInjectionIds(assistant.presetIds, presets) }
            .toSet()
    }
    val effectiveLorebookIds = if (assistant.allowConversationPromptInjection) {
        conversationLorebookIds
    } else {
        assistant.lorebookIds
    }
    // 助手关联的预设 (见 issue #65 / #182)
    val effectivePresetIds = assistant.presetIds
    val activePresets = presets.filter { it.id in effectivePresetIds }

    // 展开旧模型预设内生效的注入 ID (未迁移到 entries 的预设走此路径, 见 issue #65)
    // 预设内被单独禁用的条目会被排除
    val presetInjectionIds = activePresets
        .filter { !it.hasEntries() }
        .flatMap { it.effectiveInjectionIds() }
        .toSet()
    val allModeInjectionIds = effectiveModeInjectionIds + presetInjectionIds

    // 1. 获取关联的 ModeInjection (含直接绑定与旧模型预设展开的条目)
    // 记录已注入 id，供 step1b 去重（同一 id 只注入一次，避免直连+entries 双注入）。
    val injectedIds = mutableSetOf<Uuid>()
    modeInjections
        .filter { it.enabled && allModeInjectionIds.contains(it.id) }
        .forEach {
            injections.add(it)
            injectedIds.add(it.id)
        }

    // 1b. 展开新模型预设 (entries)：每个启用条目就地解析为 ModeInjection (见 issue #182)
    // priority = -order（或迁移条目的 legacyPriority），使下游 sortedByDescending{priority} 生效。
    // 若解析出的 injection.id 已在 step1 注入过则跳过（按 id 去重，lorebook 不参与）。
    activePresets
        .filter { it.hasEntries() }
        .forEach { preset ->
            preset.entries
                .inPresetDisplayOrder()
                .forEachIndexed { displayIndex, entry ->
                    if (!entry.enabled) return@forEachIndexed
                    resolvePresetEntry(
                        entry = entry,
                        modeInjections = modeInjections,
                        fallbackPriority = -displayIndex,
                    )?.let { resolved ->
                        if (injectedIds.add(resolved.deduplicationId)) {
                            injections.add(resolved.injection)
                        }
                    }
                }
        }

    // 2. 获取关联的 Lorebook 中被触发的 RegexInjection
    val enabledLorebooks = lorebooks.filter {
        it.enabled && effectiveLorebookIds.contains(it.id)
    }
    if (enabledLorebooks.isNotEmpty()) {
        // 提取上下文用于匹配（只取非 SYSTEM 消息）
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
 * 将一个启用的 [PresetEntry] 解析为可注入的 [PromptInjection.ModeInjection]。
 *
 * - [PresetEntry.Custom]：直接用内嵌内容。
 * - [PresetEntry.Builtin]：查 [BuiltinPromptRegistry]，用 override 或默认模板，DYNAMIC 时替换宏；
 *   未知 key 或解析后内容为空则跳过（返回 null，AC6：不注入、不报错）。
 * - [PresetEntry.Reference]：查全局 [modeInjections] 命中 [PresetEntry.Reference.modeInjectionId]；
 *   被删或全局禁用则跳过。
 *
 * order → priority 采用 `-order` 映射：下游 `sortedByDescending { priority }` 即为组内 order 升序拼接。
 * position 对 Builtin 取 [effectivePosition]（overridePosition 优先）。
 */
private data class ResolvedPresetEntry(
    val injection: PromptInjection.ModeInjection,
    val deduplicationId: Uuid,
)

private fun resolvePresetEntry(
    entry: PresetEntry,
    modeInjections: List<PromptInjection.ModeInjection>,
    fallbackPriority: Int,
): ResolvedPresetEntry? {
    val content: String
    val role: MessageRole
    var deduplicationId = entry.id
    val position: InjectionPosition = entry.effectivePosition()
    // 迁移快照条目保留原 priority 参与全局混排（防排序回归）；用户新建条目 legacyPriority=null 时回退 -order。
    var priority = fallbackPriority
    when (entry) {
        is PresetEntry.Custom -> {
            content = entry.content
            role = entry.role
            entry.legacyPriority?.let { priority = it }
        }

        is PresetEntry.Builtin -> {
            val def = BuiltinPromptRegistry[entry.builtinKey] ?: return null
            // config-only：内置模板的真实注入由各自专用 transformer / 特性流程完成。
            // 预设路径对 injectable=false 的条目一律跳过，避免双注入或泄漏未解析字面宏（见 #182）。
            if (!def.injectable) return null
            val override = entry.overrideContent?.takeIf { def.overridable }
            content = override ?: def.defaultContent
            role = entry.role
        }

        is PresetEntry.Reference -> {
            val target = modeInjections.firstOrNull { it.id == entry.modeInjectionId }
                ?.takeIf { it.enabled } ?: return null
            content = target.content
            role = entry.role
            deduplicationId = target.id
        }
    }
    if (content.isBlank()) return null
    return ResolvedPresetEntry(
        injection = PromptInjection.ModeInjection(
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
                parts = listOf(UIMessagePart.Text(newText))
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
            result.add(0, UIMessage.system(combinedContent))
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
            }
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
