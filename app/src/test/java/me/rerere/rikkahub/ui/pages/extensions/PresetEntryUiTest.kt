package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.inPresetDisplayOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PresetEntryUiTest {
    @Test
    fun `moving migrated custom entries clears legacy priority and preserves other groups`() {
        val first = PresetEntry.Custom(order = 0, content = "first", legacyPriority = 10)
        val builtin = PresetEntry.Builtin(order = 0, builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION)
        val second = PresetEntry.Custom(order = 1, content = "second", legacyPriority = 5)
        val entries = listOf(first, builtin, second)

        val moved = movePresetEntryInGroup(entries, second.id, -1)

        assertEquals(builtin, moved[1])
        val customs = moved.inPresetDisplayOrder().filterIsInstance<PresetEntry.Custom>()
        assertEquals(listOf("second", "first"), customs.map { it.content })
        assertTrue(customs.map { it.order } == listOf(0, 1))
        assertTrue(customs.all { it.legacyPriority == null })
    }

    @Test
    fun `empty entries model count does not fall back to legacy ids`() {
        val preset = Preset(
            modeInjectionIds = setOf(Uuid.random()),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertEquals(0, preset.displayEntryCount())
        assertTrue(preset.displayEntryNames().isEmpty())
    }

    @Test
    fun `entry names follow detail page type sections excluding config-only builtins`() {
        val preset = Preset(
            entries = listOf(
                PresetEntry.Custom(order = 0, name = "Custom"),
                PresetEntry.Builtin(order = 0, builtinKey = "suggestion"),
            ),
        )

        assertEquals(
            listOf("Custom"),
            preset.displayEntryNames(),
        )
        assertEquals(1, preset.displayEntryCount())
        assertTrue(preset.entries.single { it is PresetEntry.Builtin }.isConfigOnlyBuiltin())
        assertFalse(preset.entries.single { it is PresetEntry.Builtin }.countsTowardDisplayEntries())
    }

    @Test
    fun `disabled custom is excluded from display count`() {
        val preset = Preset(
            entries = listOf(
                PresetEntry.Custom(order = 0, name = "On", enabled = true),
                PresetEntry.Custom(order = 1, name = "Off", enabled = false),
                PresetEntry.Builtin(
                    order = 0,
                    builtinKey = BuiltinPromptRegistry.KEY_MEMORY_TABLE_GUIDE,
                    enabled = true,
                ),
            ),
        )

        assertEquals(1, preset.displayEntryCount())
        assertEquals(listOf("On"), preset.displayEntryNames())
    }

    @Test
    fun `out of range move is a no-op`() {
        val entry = PresetEntry.Custom(content = "only", legacyPriority = 2)

        val unchanged = movePresetEntryInGroup(listOf(entry), entry.id, -1)

        assertEquals(listOf(entry), unchanged)
        assertEquals(2, (unchanged.single() as PresetEntry.Custom).legacyPriority)
    }

    @Test
    fun `accessible move down uses the same group ordering contract`() {
        val first = PresetEntry.Builtin(order = 0, builtinKey = BuiltinPromptRegistry.KEY_REPLY_DRAFT)
        val custom = PresetEntry.Custom(order = 0, content = "other group")
        val second = PresetEntry.Builtin(order = 1, builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION)

        val moved = movePresetEntryInGroup(listOf(first, custom, second), first.id, 1)

        assertEquals(custom, moved[1])
        assertEquals(
            listOf(BuiltinPromptRegistry.KEY_SUGGESTION, BuiltinPromptRegistry.KEY_REPLY_DRAFT),
            moved.filterIsInstance<PresetEntry.Builtin>().sortedBy { it.order }.map { it.builtinKey },
        )
        assertEquals(moved, movePresetEntryInGroup(moved, first.id, 1))
    }

    @Test
    fun `switching builtin key clears overrides and applies new defaults`() {
        val entry = PresetEntry.Builtin(
            builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION,
            overrideContent = "old override",
            overridePosition = InjectionPosition.AT_DEPTH,
            position = InjectionPosition.BOTTOM_OF_CHAT,
            role = MessageRole.ASSISTANT,
        )
        val newDefinition = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_REPLY_DRAFT]

        val switched = entry.switchBuiltinKey(BuiltinPromptRegistry.KEY_REPLY_DRAFT, newDefinition)

        assertEquals(BuiltinPromptRegistry.KEY_REPLY_DRAFT, switched.builtinKey)
        assertNull(switched.overrideContent)
        assertNull(switched.overridePosition)
        assertEquals(newDefinition!!.defaultPosition, switched.position)
        assertEquals(newDefinition.defaultRole, switched.role)
    }

    @Test
    fun `static builtin with dedicated placeholders still exposes editor variables`() {
        val replyDraft = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_REPLY_DRAFT]!!

        assertFalse(replyDraft.dynamic)
        assertTrue(replyDraft.hasEditorVariables())
        assertEquals(
            listOf("{locale}", "{content}", "{user_instruction}"),
            replyDraft.supportedVariables,
        )
    }

    @Test
    fun `available builtin keys exclude already used keys in registry order`() {
        val used = PresetEntry.Builtin(builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION)
        val disabledUsed = PresetEntry.Builtin(
            builtinKey = BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE,
            enabled = false,
        )
        val entries = listOf(used, disabledUsed, PresetEntry.Custom())

        val available = availableBuiltinKeys(entries)

        val expected = BuiltinPromptRegistry.all.keys.filter {
            it != BuiltinPromptRegistry.KEY_SUGGESTION && it != BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE
        }
        assertEquals(expected, available)
        assertFalse(available.contains(BuiltinPromptRegistry.KEY_SUGGESTION))
        assertFalse(available.contains(BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE))
    }

    @Test
    fun `available builtin keys empty when all used`() {
        val entries = BuiltinPromptRegistry.all.keys.map { PresetEntry.Builtin(builtinKey = it) }

        assertTrue(availableBuiltinKeys(entries).isEmpty())
    }

    @Test
    fun `editing builtin keys keeps current key and excludes keys used by siblings`() {
        val current = PresetEntry.Builtin(builtinKey = BuiltinPromptRegistry.KEY_REPLY_DRAFT)
        val sibling = PresetEntry.Builtin(builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION)

        val available = availableBuiltinKeys(listOf(current, sibling), current.id)

        assertTrue(available.contains(BuiltinPromptRegistry.KEY_REPLY_DRAFT))
        assertFalse(available.contains(BuiltinPromptRegistry.KEY_SUGGESTION))
    }

    @Test
    fun `reorder by target moves custom within group and clears legacy priority`() {
        val first = PresetEntry.Custom(order = 0, content = "first", legacyPriority = 10)
        val second = PresetEntry.Custom(order = 1, content = "second", legacyPriority = 5)
        val third = PresetEntry.Custom(order = 2, content = "third", legacyPriority = 7)
        val entries = listOf(first, second, third)

        val reordered = reorderPresetEntryByTarget(entries, third.id, first.id)

        val customs = reordered.filterIsInstance<PresetEntry.Custom>().sortedBy { it.order }
        assertEquals(listOf("third", "first", "second"), customs.map { it.content })
        assertEquals(listOf(0, 1, 2), customs.map { it.order })
        assertTrue(customs.all { it.legacyPriority == null })
    }

    @Test
    fun `reorder by target across different groups is a no-op`() {
        val custom = PresetEntry.Custom(order = 0, content = "custom")
        val builtin = PresetEntry.Builtin(order = 0, builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION)
        val entries = listOf(custom, builtin)

        val unchanged = reorderPresetEntryByTarget(entries, custom.id, builtin.id)

        assertEquals(entries, unchanged)
    }

    @Test
    fun `reorder by target preserves other groups and renumbers order contiguously`() {
        val builtinA = PresetEntry.Builtin(order = 0, builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION)
        val builtinB = PresetEntry.Builtin(order = 1, builtinKey = BuiltinPromptRegistry.KEY_REPLY_DRAFT)
        val custom = PresetEntry.Custom(order = 0, content = "custom", legacyPriority = 3)
        val entries = listOf(builtinA, custom, builtinB)

        val reordered = reorderPresetEntryByTarget(entries, builtinB.id, builtinA.id)

        assertEquals(custom, reordered[1])
        val builtins = reordered.filterIsInstance<PresetEntry.Builtin>().sortedBy { it.order }
        assertEquals(
            listOf(BuiltinPromptRegistry.KEY_REPLY_DRAFT, BuiltinPromptRegistry.KEY_SUGGESTION),
            builtins.map { it.builtinKey },
        )
        assertEquals(listOf(0, 1), builtins.map { it.order })
    }

    @Test
    fun `reorder by target with unknown or identical ids is a no-op`() {
        val custom = PresetEntry.Custom(order = 0, content = "custom")
        val entries = listOf(custom)

        assertEquals(entries, reorderPresetEntryByTarget(entries, custom.id, custom.id))
        assertEquals(entries, reorderPresetEntryByTarget(entries, Uuid.random(), custom.id))
    }
}
