package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.ai.transformers.applyMemoryTableGuideOverride
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 覆盖 #182 config-only 内置模板「编辑回流」的核心纯函数：
 * - [resolveBuiltinOverride]：从 assistant 关联的启用预设取覆盖文本。
 * - [buildInputDraftPrompt] 的 template 参数：预设覆盖模板真正生效。
 * - [applyMemoryTableGuideOverride]：引导文案与运行时数据块合并。
 */
class BuiltinPromptOverridesTest {

    private fun builtinEntry(
        key: String,
        override: String?,
        enabled: Boolean = true,
        order: Int = 0,
    ) = PresetEntry.Builtin(
        id = Uuid.random(),
        enabled = enabled,
        order = order,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        builtinKey = key,
        overrideContent = override,
    )

    private fun presetWith(vararg entries: PresetEntry): Preset =
        Preset(id = Uuid.random(), entries = entries.toList())

    private fun assistantWith(vararg presetIds: Uuid): Assistant =
        Assistant(presetIds = presetIds.toSet())

    // ---- resolveBuiltinOverride ----

    @Test
    fun `returns null when assistant has no presets`() {
        val preset = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "X"))
        val result = resolveBuiltinOverride(
            assistant = Assistant(),
            presets = listOf(preset),
            builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION,
        )
        assertNull(result)
    }

    @Test
    fun `returns override for matching enabled builtin entry`() {
        val preset = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "custom suggestion"))
        val assistant = assistantWith(preset.id)
        val result = resolveBuiltinOverride(assistant, listOf(preset), BuiltinPromptRegistry.KEY_SUGGESTION)
        assertEquals("custom suggestion", result)
    }

    @Test
    fun `returns null when builtin key does not match`() {
        val preset = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "custom"))
        val assistant = assistantWith(preset.id)
        val result = resolveBuiltinOverride(assistant, listOf(preset), BuiltinPromptRegistry.KEY_REPLY_DRAFT)
        assertNull(result)
    }

    @Test
    fun `skips disabled entry`() {
        val preset = presetWith(
            builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "disabled override", enabled = false),
        )
        val assistant = assistantWith(preset.id)
        val result = resolveBuiltinOverride(assistant, listOf(preset), BuiltinPromptRegistry.KEY_SUGGESTION)
        assertNull(result)
    }

    @Test
    fun `skips blank override`() {
        val preset = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "   "))
        val assistant = assistantWith(preset.id)
        val result = resolveBuiltinOverride(assistant, listOf(preset), BuiltinPromptRegistry.KEY_SUGGESTION)
        assertNull(result)
    }

    @Test
    fun `skips null override`() {
        val preset = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, null))
        val assistant = assistantWith(preset.id)
        val result = resolveBuiltinOverride(assistant, listOf(preset), BuiltinPromptRegistry.KEY_SUGGESTION)
        assertNull(result)
    }

    @Test
    fun `ignores preset not linked to assistant`() {
        val linked = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "linked"))
        val unlinked = presetWith(builtinEntry(BuiltinPromptRegistry.KEY_REPLY_DRAFT, "unlinked"))
        val assistant = assistantWith(linked.id)
        val result = resolveBuiltinOverride(
            assistant,
            listOf(linked, unlinked),
            BuiltinPromptRegistry.KEY_REPLY_DRAFT,
        )
        assertNull(result)
    }

    @Test
    fun `picks lowest order entry when multiple match in a preset`() {
        val preset = presetWith(
            builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "second", order = 5),
            builtinEntry(BuiltinPromptRegistry.KEY_SUGGESTION, "first", order = 1),
        )
        val assistant = assistantWith(preset.id)
        val result = resolveBuiltinOverride(assistant, listOf(preset), BuiltinPromptRegistry.KEY_SUGGESTION)
        assertEquals("first", result)
    }

    // ---- buildInputDraftPrompt template 参数 ----

    @Test
    fun `input draft uses default template when none supplied`() {
        val prompt = buildInputDraftPrompt(locale = "en", content = "hello")
        assertTrue(prompt.contains("reply draft"))
        assertTrue(prompt.contains("hello"))
    }

    @Test
    fun `input draft honors override template with placeholders`() {
        val prompt = buildInputDraftPrompt(
            locale = "zh",
            content = "对话内容",
            template = "自定义模板 locale={locale}\n{content}",
        )
        assertTrue(prompt.contains("自定义模板 locale=zh"))
        assertTrue(prompt.contains("对话内容"))
    }

    // ---- applyMemoryTableGuideOverride ----

    @Test
    fun `memory guide macro replaced by data block`() {
        val result = applyMemoryTableGuideOverride(
            guideOverride = "引导\n{{memory_tables}}\n结尾",
            dataBlock = "<memory_tables>DATA</memory_tables>",
        )
        assertTrue(result.contains("<memory_tables>DATA</memory_tables>"))
        assertTrue(!result.contains("{{memory_tables}}"))
        assertTrue(result.startsWith("引导"))
        assertTrue(result.endsWith("结尾"))
    }

    @Test
    fun `memory guide without macro appends data block`() {
        val result = applyMemoryTableGuideOverride(
            guideOverride = "只有引导文案",
            dataBlock = "<memory_tables>DATA</memory_tables>",
        )
        assertTrue(result.startsWith("只有引导文案"))
        assertTrue(result.contains("<memory_tables>DATA</memory_tables>"))
    }
}
