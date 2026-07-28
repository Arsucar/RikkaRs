package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry

/**
 * 从 Assistant 关联的启用预设中解析某个内置模板（[builtinKey]）的用户覆盖内容（见 #182）。
 *
 * config-only 的内置模板（reply_draft / suggestion / workspace_guide / memory_table_guide）
 * 不走 [me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer] 的注入路径，
 * 而是由各自专用消费点（buildInputDraftPrompt / generateSuggestion /
 * WorkspaceReminderTransformer / MemoryTableInjectionTransformer）反读预设覆盖后自行使用。
 * 这样「预设里编辑的内容」才能真正生效，而不是空转（#182 审查修复的收尾特性）。
 *
 * 本函数只负责「取出覆盖文本」，是纯函数：
 * - 不做任何宏替换（`{{workspace_name}}` / `{{cwd}}` / `{{memory_tables}}` 等由持有运行时数据的
 *   消费点自行替换）。
 * - 不读写数据文件。
 *
 * 解析规则：
 * - 只看 [Assistant.presetIds] 命中的预设，且预设自身与条目都必须处于启用态。
 * - 只认 [PresetEntry.Builtin] 且 [PresetEntry.Builtin.builtinKey] == [builtinKey] 的条目。
 * - 覆盖内容取 [PresetEntry.Builtin.overrideContent]，且必须 [String.isNotBlank]（空白视为未覆盖）。
 * - 多个预设/条目命中时取「首个」有效覆盖（预设按 [Assistant.presetIds] 关联顺序，
 *   条目按 [PresetEntry.order] 升序），保证行为确定、可测。
 * - 无命中返回 null，调用方回退到各自默认（默认常量或全局设置）。
 *
 * @param assistant 当前对话的助手。
 * @param presets 全局预设列表（通常来自 `settings.presets`）。
 * @param builtinKey 见 [BuiltinPromptRegistry] 的 KEY_* 常量。
 * @return 用户覆盖的模板文本；无有效覆盖时为 null。
 */
fun resolveBuiltinOverride(
    assistant: Assistant,
    presets: List<Preset>,
    builtinKey: String,
): String? {
    if (assistant.presetIds.isEmpty()) return null
    // 按 assistant.presetIds 的关联顺序遍历，保证多预设命中时结果确定。
    return assistant.presetIds
        .asSequence()
        .mapNotNull { id -> presets.firstOrNull { it.id == id } }
        .flatMap { preset ->
            preset.entries
                .asSequence()
                .filterIsInstance<PresetEntry.Builtin>()
                .filter { it.enabled && it.builtinKey == builtinKey }
                .sortedBy { it.order }
        }
        .mapNotNull { it.overrideContent?.takeIf { content -> content.isNotBlank() } }
        .firstOrNull()
}
