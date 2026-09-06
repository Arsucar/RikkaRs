package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.LegacyModeInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.migratedWithEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * #259: entries-only + lorebook injection path.
 * Direct ModeInjection binding / conversation mode injection tests removed.
 */
class PromptInjectionTransformerTest {

    private fun createAssistant(
        presetIds: Set<Uuid> = emptySet(),
        lorebookIds: Set<Uuid> = emptySet(),
    ) = Assistant(
        presetIds = presetIds,
        lorebookIds = lorebookIds,
    )

    private fun createRegexInjection(
        id: Uuid = Uuid.random(),
        name: String = "Test Regex",
        enabled: Boolean = true,
        priority: Int = 0,
        position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        content: String = "Regex injected content",
        injectDepth: Int = 4,
        role: MessageRole = MessageRole.USER,
        keywords: List<String> = listOf("trigger"),
        useRegex: Boolean = false,
        caseSensitive: Boolean = false,
        scanDepth: Int = 5,
        constantActive: Boolean = false,
    ) = PromptInjection.RegexInjection(
        id = id,
        name = name,
        enabled = enabled,
        priority = priority,
        position = position,
        content = content,
        injectDepth = injectDepth,
        role = role,
        keywords = keywords,
        useRegex = useRegex,
        caseSensitive = caseSensitive,
        scanDepth = scanDepth,
        constantActive = constantActive,
    )

    private fun createLorebook(
        id: Uuid = Uuid.random(),
        name: String = "Test Lorebook",
        enabled: Boolean = true,
        entries: List<PromptInjection.RegexInjection> = emptyList(),
    ) = Lorebook(
        id = id,
        name = name,
        enabled = enabled,
        entries = entries,
    )

    private fun getMessageText(message: UIMessage): String {
        return message.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("") { it.text }
    }

    private fun customPreset(
        content: String,
        position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        enabled: Boolean = true,
        order: Int = 0,
        role: MessageRole = MessageRole.USER,
        injectDepth: Int = 4,
        legacyPriority: Int? = null,
        name: String = "",
    ): Preset {
        val entry = PresetEntry.Custom(
            enabled = enabled,
            order = order,
            position = position,
            injectDepth = injectDepth,
            role = role,
            name = name,
            content = content,
            legacyPriority = legacyPriority,
        )
        return Preset(
            entries = listOf(entry),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
    }

    // region Basics

    @Test
    fun `no injections should return original messages`() {
        val messages = listOf(
            UIMessage.system("System"),
            UIMessage.user("Hello"),
        )
        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(),
            lorebooks = emptyList(),
            presets = emptyList(),
        )
        assertEquals(messages, result)
    }

    @Test
    fun `disabled custom entry should not be applied`() {
        val preset = customPreset(content = "Should not appear", enabled = false)
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Hello"))
        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertFalse(getMessageText(result.first()).contains("Should not appear"))
    }

    @Test
    fun `unbound preset should not inject`() {
        val preset = customPreset(content = "Unbound")
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Hello"))
        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = emptySet()),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertFalse(getMessageText(result.first()).contains("Unbound"))
    }

    // endregion

    // region Positions via Custom entries

    @Test
    fun `custom AFTER_SYSTEM_PROMPT appends to system message`() {
        val preset = customPreset("After content", InjectionPosition.AFTER_SYSTEM_PROMPT)
        val result = transformMessages(
            messages = listOf(UIMessage.system("System prompt"), UIMessage.user("Hello")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val systemText = getMessageText(result.first())
        assertTrue(systemText.contains("System prompt"))
        assertTrue(systemText.contains("After content"))
        assertTrue(systemText.indexOf("System prompt") < systemText.indexOf("After content"))
        // 融合上游 isSynthetic 断言：合并进已有 system 消息的注入同样标记为 synthetic
        assertTrue(result.first().isSynthetic)
    }

    @Test
    fun `custom BEFORE_SYSTEM_PROMPT prepends to system message`() {
        val preset = customPreset("Before content", InjectionPosition.BEFORE_SYSTEM_PROMPT)
        val result = transformMessages(
            messages = listOf(UIMessage.system("System prompt"), UIMessage.user("Hello")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val systemText = getMessageText(result.first())
        assertTrue(systemText.indexOf("Before content") < systemText.indexOf("System prompt"))
    }

    @Test
    fun `injection without system message creates system message`() {
        val preset = customPreset("Created system", InjectionPosition.AFTER_SYSTEM_PROMPT)
        val result = transformMessages(
            messages = listOf(UIMessage.user("Hello")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertEquals(MessageRole.SYSTEM, result.first().role)
        assertTrue(result.first().isSynthetic)
        assertTrue(getMessageText(result.first()).contains("Created system"))
    }

    @Test
    fun `custom TOP_OF_CHAT inserts before first user message`() {
        val preset = customPreset("Top inject", InjectionPosition.TOP_OF_CHAT)
        val result = transformMessages(
            messages = listOf(
                UIMessage.system("System"),
                UIMessage.user("First user"),
                UIMessage.assistant("Reply"),
            ),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val userIndex = result.indexOfFirst { it.role == MessageRole.USER && getMessageText(it) == "First user" }
        assertTrue(userIndex > 0)
        // 融合上游 isSynthetic 断言：注入的聊天消息标记为 synthetic
        assertTrue(result[userIndex - 1].isSynthetic)
        assertTrue(getMessageText(result[userIndex - 1]).contains("Top inject"))
    }

    @Test
    fun `custom BOTTOM_OF_CHAT inserts before last message`() {
        val preset = customPreset("Bottom inject", InjectionPosition.BOTTOM_OF_CHAT)
        val result = transformMessages(
            messages = listOf(
                UIMessage.system("System"),
                UIMessage.user("User"),
                UIMessage.assistant("Last"),
            ),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertTrue(result[result.lastIndex - 1].isSynthetic)
        assertTrue(getMessageText(result[result.lastIndex - 1]).contains("Bottom inject"))
    }

    @Test
    fun `custom AT_DEPTH inserts at specified depth`() {
        val preset = customPreset(
            content = "Depth inject",
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 2,
        )
        val result = transformMessages(
            messages = listOf(
                UIMessage.system("System"),
                UIMessage.user("U1"),
                UIMessage.assistant("A1"),
                UIMessage.user("U2"),
            ),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertTrue(result.any { getMessageText(it).contains("Depth inject") })
    }

    @Test
    fun `entries ordered by priority descending via legacyPriority`() {
        val high = PresetEntry.Custom(
            order = 0,
            content = "HIGH",
            legacyPriority = 10,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        )
        val low = PresetEntry.Custom(
            order = 1,
            content = "LOW",
            legacyPriority = 1,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        )
        val preset = Preset(
            entries = listOf(low, high),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("U")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val text = getMessageText(result.first())
        assertTrue(text.indexOf("HIGH") < text.indexOf("LOW"))
    }

    // endregion

    // region Lorebook

    @Test
    fun `lorebook with keyword match should trigger injection`() {
        val entry = createRegexInjection(content = "Lore hit", keywords = listOf("magic"))
        val lorebook = createLorebook(entries = listOf(entry))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("cast magic spell")),
            assistant = createAssistant(lorebookIds = setOf(lorebook.id)),
            lorebooks = listOf(lorebook),
        )
        assertTrue(getMessageText(result.first()).contains("Lore hit"))
    }

    @Test
    fun `lorebook without keyword match should not trigger`() {
        val entry = createRegexInjection(content = "Lore miss", keywords = listOf("dragon"))
        val lorebook = createLorebook(entries = listOf(entry))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("hello world")),
            assistant = createAssistant(lorebookIds = setOf(lorebook.id)),
            lorebooks = listOf(lorebook),
        )
        assertFalse(getMessageText(result.first()).contains("Lore miss"))
    }

    @Test
    fun `lorebook with constantActive always triggers`() {
        val entry = createRegexInjection(
            content = "Always on",
            keywords = emptyList(),
            constantActive = true,
        )
        val lorebook = createLorebook(entries = listOf(entry))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("anything")),
            assistant = createAssistant(lorebookIds = setOf(lorebook.id)),
            lorebooks = listOf(lorebook),
        )
        assertTrue(getMessageText(result.first()).contains("Always on"))
    }

    @Test
    fun `disabled lorebook should not trigger`() {
        val entry = createRegexInjection(content = "Disabled book", constantActive = true)
        val lorebook = createLorebook(enabled = false, entries = listOf(entry))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("hi")),
            assistant = createAssistant(lorebookIds = setOf(lorebook.id)),
            lorebooks = listOf(lorebook),
        )
        assertFalse(getMessageText(result.first()).contains("Disabled book"))
    }

    @Test
    fun `conversation lorebook ids override assistant when provided`() {
        val entry = createRegexInjection(content = "Conv lore", constantActive = true)
        val lorebook = createLorebook(entries = listOf(entry))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("hi")),
            assistant = createAssistant(lorebookIds = emptySet()),
            lorebooks = listOf(lorebook),
            conversationLorebookIds = setOf(lorebook.id),
        )
        assertTrue(getMessageText(result.first()).contains("Conv lore"))
    }

    @Test
    fun `combined custom entry and lorebook both apply`() {
        val preset = customPreset("Preset body")
        val entry = createRegexInjection(content = "Lore body", constantActive = true)
        val lorebook = createLorebook(entries = listOf(entry))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("hi")),
            assistant = createAssistant(
                presetIds = setOf(preset.id),
                lorebookIds = setOf(lorebook.id),
            ),
            lorebooks = listOf(lorebook),
            presets = listOf(preset),
        )
        val text = getMessageText(result.first())
        assertTrue(text.contains("Preset body"))
        assertTrue(text.contains("Lore body"))
    }

    // endregion

    // region Preset entries (#182 / #259)

    @Test
    fun `preset custom entries inject in ascending order regardless of list order`() {
        val preset = Preset(
            entries = listOf(
                PresetEntry.Custom(order = 1, content = "second"),
                PresetEntry.Custom(order = 0, content = "first"),
            ),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("U")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val text = getMessageText(result.first())
        // Without legacyPriority, fallback priority = -displayIndex; display order is by order field.
        assertTrue(text.contains("first"))
        assertTrue(text.contains("second"))
    }

    @Test
    fun `different custom entries with equal content remain independent`() {
        val marker = "equal custom marker"
        val preset = Preset(
            entries = listOf(
                PresetEntry.Custom(order = 0, content = marker),
                PresetEntry.Custom(order = 1, content = marker),
            ),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("U")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val occurrences = getMessageText(result.first()).split(marker).size - 1
        assertEquals(2, occurrences)
    }

    @Test
    fun `preset builtin entry is config-only and must not be injected`() {
        val preset = Preset(
            entries = listOf(
                PresetEntry.Builtin(builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION),
            ),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("U")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertEquals(listOf(UIMessage.system("S"), UIMessage.user("U")).map { getMessageText(it) },
            result.map { getMessageText(it) })
    }

    @Test
    fun `preset builtin with unknown key is skipped without error`() {
        val preset = Preset(
            entries = listOf(PresetEntry.Builtin(builtinKey = "unknown_key_xyz")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val messages = listOf(UIMessage.system("S"), UIMessage.user("U"))
        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertEquals(messages.map { getMessageText(it) }, result.map { getMessageText(it) })
    }

    // endregion

    // region Migration

    @Test
    fun `migratedWithEntries snapshots old modeInjectionIds as custom entries`() {
        val injection = LegacyModeInjection(
            id = Uuid.random(),
            name = "Legacy",
            content = "legacy body",
            priority = 5,
        )
        val preset = Preset(modeInjectionIds = setOf(injection.id))
        val migrated = preset.migratedWithEntries(listOf(injection))
        assertTrue(migrated.hasEntries())
        val custom = migrated.entries.single() as PresetEntry.Custom
        assertEquals("legacy body", custom.content)
        assertEquals(5, custom.legacyPriority)
        assertTrue(migrated.modeInjectionIds.isEmpty())
    }

    @Test
    fun `migratedWithEntries is idempotent`() {
        val injection = LegacyModeInjection(id = Uuid.random(), content = "x")
        val preset = Preset(modeInjectionIds = setOf(injection.id))
        val once = preset.migratedWithEntries(listOf(injection))
        val twice = once.migratedWithEntries(listOf(injection))
        assertEquals(once, twice)
    }

    @Test
    fun `migratedWithEntries marks empty preset as entries model`() {
        val preset = Preset(entriesVersion = 0)
        val migrated = preset.migratedWithEntries(emptyList())
        assertEquals(PRESET_ENTRIES_VERSION, migrated.entriesVersion)
        assertTrue(migrated.hasEntries())
    }

    @Test
    fun `migrated preset injects equivalently via custom snapshot`() {
        val injection = LegacyModeInjection(
            id = Uuid.random(),
            content = "migrated inject",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        )
        val preset = Preset(modeInjectionIds = setOf(injection.id))
            .migratedWithEntries(listOf(injection))
        val result = transformMessages(
            messages = listOf(UIMessage.system("S"), UIMessage.user("U")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        assertTrue(getMessageText(result.first()).contains("migrated inject"))
    }

    // endregion

    // region Safe insert / apply helpers

    @Test
    fun `collectInjections returns empty for no matching conditions`() {
        val collected = collectInjections(
            messages = listOf(UIMessage.user("hi")),
            assistant = createAssistant(),
            lorebooks = emptyList(),
        )
        assertTrue(collected.isEmpty())
    }

    @Test
    fun `applyInjections with empty map returns original messages`() {
        val messages = listOf(UIMessage.system("S"), UIMessage.user("U"))
        assertEquals(messages, applyInjections(messages, emptyMap()))
    }

    @Test
    fun `findSafeInsertIndex allows insert before assistant without tools`() {
        val messages = listOf(
            UIMessage.user("U"),
            UIMessage.assistant("A"),
        )
        assertEquals(1, findSafeInsertIndex(messages, 1))
    }

    // endregion
}
