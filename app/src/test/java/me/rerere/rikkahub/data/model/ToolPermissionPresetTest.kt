package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.utils.JsonInstant

class ToolPermissionPresetTest {
    @Test
    fun builtInsAreStableAndDoNotContainResourceBindings() {
        val presets = builtInToolPermissionPresets()
        assertEquals(3, presets.size)
        assertEquals(setOf("readonly-research", "approval-workspace", "least-privilege"), presets.map { it.name }.toSet())
        assertTrue(presets.flatMap { it.permissions.keys }.none { it.contains("mcp:") || it.contains("skill:") && it != "skill:management" })
    }

    @Test
    fun unknownCapabilitiesAreSkippedAndDiffIsSourceQualified() {
        val preset = ToolPermissionPreset(
            name = "custom",
            permissions = mapOf("workspace:workspace_read_file" to ToolPermission.ALLOW, "local:lookup" to ToolPermission.DENY),
        )
        val diff = diffToolPermissionPreset(preset, emptyMap(), setOf("workspace:workspace_read_file"))
        assertEquals(setOf("local:lookup"), diff.unknownKeys)
        assertEquals(mapOf("workspace:workspace_read_file" to ToolPermission.ALLOW), diff.changed)
    }

    @Test
    fun relaxationRequiresExplicitConfirmation() {
        val preset = ToolPermissionPreset(name = "relax", permissions = mapOf("memory:normal" to ToolPermission.ALLOW))
        val assistant = Assistant(toolPermissions = mapOf("memory:normal" to ToolPermission.DENY))
        val denied = applyToolPermissionPreset(preset, assistant, setOf("memory:normal"))
        assertEquals(ToolPresetApplyStatus.REJECTED_RELAXATION, denied.status)
        val applied = applyToolPermissionPreset(preset, assistant, setOf("memory:normal"), confirmRelaxation = true)
        assertEquals(ToolPresetApplyStatus.APPLIED, applied.status)
    }

    @Test
    fun legacyAssistantDefaultsToInheritWithoutMigration() {
        val legacy = JsonInstant.decodeFromString<Assistant>("{}")
        assertTrue(legacy.toolPermissions.isEmpty())
        assertEquals(ToolPermission.INHERIT, resolveToolPermission("memory:normal", ToolPermission.INHERIT))
    }

    @Test
    fun askToAllowRequiresExplicitConfirmation() {
        val preset = ToolPermissionPreset(name = "relax", permissions = mapOf("memory:normal" to ToolPermission.ALLOW))
        val assistant = Assistant(toolPermissions = mapOf("memory:normal" to ToolPermission.ASK))
        assertEquals(
            ToolPresetApplyStatus.REJECTED_RELAXATION,
            applyToolPermissionPreset(preset, assistant, setOf("memory:normal")).status,
        )
    }

    @Test
    fun invalidVersionAndOversizedMetadataAreRejected() {
        val assistant = Assistant()
        val known = setOf("memory:normal")
        assertEquals(
            ToolPresetApplyStatus.INVALID,
            applyToolPermissionPreset(ToolPermissionPreset(name = "x", version = 2), assistant, known).status,
        )
        assertEquals(
            ToolPresetApplyStatus.INVALID,
            applyToolPermissionPreset(ToolPermissionPreset(name = "x".repeat(129)), assistant, known).status,
        )
    }
}
