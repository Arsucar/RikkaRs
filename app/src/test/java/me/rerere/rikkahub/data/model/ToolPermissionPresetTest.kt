package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.datastore.decodeToolPermissionPresets

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
    fun applyingPresetReportsUnknownKeysWithoutDiscardingKnownChanges() {
        val preset = ToolPermissionPreset(
            name = "mixed",
            permissions = mapOf("memory:normal" to ToolPermission.DENY, "gone:tool" to ToolPermission.ASK),
        )
        val result = applyToolPermissionPreset(preset, Assistant(), setOf("memory:normal"), confirmRelaxation = true)
        assertEquals(ToolPresetApplyStatus.SKIPPED_UNKNOWN, result.status)
        assertEquals(setOf("gone:tool"), result.skippedKeys)
        assertEquals(mapOf("memory:normal" to ToolPermission.DENY), result.changed)
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
    fun assistantPermissionRoundTripPreservesStablePolicies() {
        val original = Assistant(toolPermissions = mapOf("memory:normal" to ToolPermission.ASK))
        val restored = JsonInstant.decodeFromString<Assistant>(JsonInstant.encodeToString(original))
        assertEquals(original.toolPermissions, restored.toolPermissions)
    }

    @Test
    fun presetRoundTripPreservesVersionAndPolicies() {
        val original = ToolPermissionPreset(name = "roundtrip", permissions = mapOf("memory:normal" to ToolPermission.DENY))
        val restored = JsonInstant.decodeFromString<ToolPermissionPreset>(JsonInstant.encodeToString(original))
        assertEquals(original, restored)
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

    @Test
    fun copyingAssistantPoliciesExcludesResourceBindings() {
        val source = Assistant(toolPermissions = mapOf(
            "builtin:web_search" to ToolPermission.ALLOW,
            "mcp:server:lookup" to ToolPermission.DENY,
            "skill:private" to ToolPermission.ASK,
            "skill:management" to ToolPermission.DENY,
        ))
        val preset = permissionPresetFromAssistant("copy", source, setOf("builtin:web_search", "mcp:server:lookup", "skill:private", "skill:management"))
        assertEquals(
            mapOf("builtin:web_search" to ToolPermission.ALLOW, "skill:management" to ToolPermission.DENY),
            preset.permissions,
        )
    }

    @Test
    fun batchPermissionReportsUnknownKeys() {
        val result = batchToolPermission(
            Assistant(),
            setOf("memory:normal", "gone:tool"),
            ToolPermission.DENY,
            setOf("memory:normal"),
        )
        assertEquals(setOf("gone:tool"), result.skippedKeys)
        assertEquals(mapOf("memory:normal" to ToolPermission.DENY), result.changed)
    }

    @Test
    fun persistedPresetDecoderRejectsMalformedAndUnknownVersions() {
        assertTrue(decodeToolPermissionPresets("not-json").isEmpty())
        val unknown = JsonInstant.encodeToString(listOf(ToolPermissionPreset(name = "future", version = 2)))
        assertTrue(decodeToolPermissionPresets(unknown).isEmpty())
        assertTrue(decodeToolPermissionPresets(" ".repeat(512_001)).isEmpty())
    }
}
