package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * #205: 「与已绑定 preset entries 重复的 assistant.modeInjectionIds 直连」去重从
 * 加载期持久化删除（#201）改为组装期只读过滤。
 *
 * 本文件覆盖：
 * - [boundPresetInjectionIds] 语义（启用条目 / Reference 引用 id / 旧字段 / 未绑定预设），
 *   供组装期过滤与 ExtensionSelector UI 共用；
 * - 加载期（[Settings.withPrunedAssistantExtensionIds]）只过滤无效引用，
 *   双绑直连数据保留在 DataStore 不销毁（#201 会静默删除 sharedId）；
 * - 组装输出不含重复注入、关闭预设后不残留，见 PromptInjectionTransformerTest。
 */
class ModeInjectionDirectBindingDedupeTest {

    // ---- boundPresetInjectionIds 语义（#201/#205） ----

    @Test
    fun `bound ids include enabled custom entry id`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertEquals(
            setOf(sharedId),
            boundPresetInjectionIds(setOf(preset.id), listOf(preset)),
        )
    }

    @Test
    fun `bound ids exclude disabled custom entry id`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap", enabled = false)),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertTrue(boundPresetInjectionIds(setOf(preset.id), listOf(preset)).isEmpty())
    }

    @Test
    fun `bound ids include reference entry id and referenced global id`() {
        val entryId = Uuid.random()
        val globalId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Reference(id = entryId, modeInjectionId = globalId)),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertEquals(
            setOf(entryId, globalId),
            boundPresetInjectionIds(setOf(preset.id), listOf(preset)),
        )
    }

    @Test
    fun `bound ids exclude disabled reference entry`() {
        val entryId = Uuid.random()
        val globalId = Uuid.random()
        val preset = Preset(
            entries = listOf(
                PresetEntry.Reference(id = entryId, modeInjectionId = globalId, enabled = false),
            ),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertTrue(boundPresetInjectionIds(setOf(preset.id), listOf(preset)).isEmpty())
    }

    @Test
    fun `bound ids include legacy effective injection ids minus disabled`() {
        val activeId = Uuid.random()
        val disabledId = Uuid.random()
        val legacyPreset = Preset(
            modeInjectionIds = setOf(activeId, disabledId),
            disabledEntryIds = setOf(disabledId),
        )

        assertEquals(
            setOf(activeId),
            boundPresetInjectionIds(setOf(legacyPreset.id), listOf(legacyPreset)),
        )
    }

    @Test
    fun `bound ids ignore preset not bound by assistant`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertTrue(boundPresetInjectionIds(emptySet(), listOf(preset)).isEmpty())
    }

    @Test
    fun `bound ids ignore preset missing from presets list`() {
        val presetId = Uuid.random()

        assertTrue(boundPresetInjectionIds(setOf(presetId), emptyList()).isEmpty())
    }

    // ---- 加载期不再持久化删除直连绑定（#205） ----

    @Test
    fun `load-time pruning keeps dual-bound direct ids in DataStore`() {
        val sharedId = Uuid.random()
        val independentId = Uuid.random()
        val injection = PromptInjection.ModeInjection(id = sharedId, content = "shared")
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId, independentId),
            presetIds = setOf(preset.id),
        )
        val settings = Settings(init = true).copy(
            modeInjections = listOf(injection),
            presets = listOf(preset),
            assistants = listOf(assistant),
        )

        val pruned = settings.withPrunedAssistantExtensionIds()

        // #205: 直连绑定即使与预设条目重复也不再被静默删除（#201 会删 sharedId），仅过滤无效引用。
        assertEquals(
            setOf(sharedId, independentId),
            pruned.assistants.first().modeInjectionIds,
        )
    }

    @Test
    fun `load-time pruning still drops invalid mode injection ids`() {
        val validId = Uuid.random()
        val orphanId = Uuid.random()
        val assistant = Assistant(modeInjectionIds = setOf(validId, orphanId))
        val settings = Settings(init = true).copy(
            modeInjections = listOf(PromptInjection.ModeInjection(id = validId, content = "v")),
            assistants = listOf(assistant),
        )

        val pruned = settings.withPrunedAssistantExtensionIds()

        assertEquals(setOf(validId), pruned.assistants.first().modeInjectionIds)
    }
}
