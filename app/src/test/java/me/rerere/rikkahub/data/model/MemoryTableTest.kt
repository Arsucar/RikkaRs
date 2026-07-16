package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableTest {
    @Test
    fun memoryCapabilitiesKeepNormalMemoryAndMemoryTablesIndependent() {
        val combinations = listOf(
            Triple(false, false, MemoryCapabilities(false, false)),
            Triple(true, false, MemoryCapabilities(true, false)),
            Triple(false, true, MemoryCapabilities(false, true)),
            Triple(true, true, MemoryCapabilities(true, true)),
        )

        combinations.forEach { (normalEnabled, tableEnabled, expected) ->
            assertEquals(
                expected,
                resolveMemoryCapabilities(
                    normalMemoryEnabled = normalEnabled,
                    settingsMemoryTableEnabled = tableEnabled,
                    assistantMemoryTableEnabled = tableEnabled,
                )
            )
        }

        assertEquals(
            MemoryCapabilities(normalMemoryEnabled = false, memoryTableEnabled = false),
            resolveMemoryCapabilities(
                normalMemoryEnabled = false,
                settingsMemoryTableEnabled = false,
                assistantMemoryTableEnabled = true,
            )
        )
    }

    @Test
    fun memoryTableRequiresGlobalAndAssistantGate() {
        assertFalse(shouldEnableMemoryTable(settingsEnabled = false, assistantEnabled = false))
        assertFalse(shouldEnableMemoryTable(settingsEnabled = true, assistantEnabled = false))
        assertFalse(shouldEnableMemoryTable(settingsEnabled = false, assistantEnabled = true))
        assertTrue(shouldEnableMemoryTable(settingsEnabled = true, assistantEnabled = true))
    }

    @Test
    fun templateScopeVisibilityAllowsOnlyGlobalAndCurrentAssistant() {
        assertTrue(
            MemoryTableTemplate(
                scopeType = MemoryTableScopeType.GLOBAL,
                scopeId = MEMORY_TABLE_GLOBAL_SCOPE_ID,
            ).isEffectiveFor("assistant-a")
        )
        assertTrue(
            MemoryTableTemplate(
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
            ).isEffectiveFor("assistant-a")
        )
        assertFalse(
            MemoryTableTemplate(
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-b",
            ).isEffectiveFor("assistant-a")
        )
        assertFalse(
            MemoryTableTemplate(
                scopeType = MemoryTableScopeType.CONVERSATION,
                scopeId = "conversation-a",
            ).isEffectiveFor("assistant-a")
        )
    }

    @Test
    fun bundleV1TemplatesDecodeAsGlobalAndV2PreservesAssistantOwner() {
        val v1 = """
            {
              "version": 1,
              "templates": [
                {
                  "id": "legacy",
                  "name": "Legacy",
                  "description": "",
                  "schemaJson": "{\"tables\":[{\"name\":\"facts\",\"columns\":[{\"name\":\"key\"}]}]}",
                  "createdAt": 1,
                  "updatedAt": 1
                }
              ],
              "documents": []
            }
        """.trimIndent()

        val legacy = decodeMemoryTableBundle(v1).templates.single()

        assertEquals(MemoryTableScopeType.GLOBAL, legacy.scopeType)
        assertEquals(MEMORY_TABLE_GLOBAL_SCOPE_ID, legacy.scopeId)

        val assistantTemplate = MemoryTableTemplate(
            id = "assistant-template",
            name = "Assistant",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "unknown-assistant",
        )
        val roundTrip = decodeMemoryTableBundle(
            encodeMemoryTableBundle(
                templates = listOf(assistantTemplate),
                documents = emptyList(),
            )
        ).templates.single()

        assertEquals(2, MEMORY_TABLE_BUNDLE_VERSION)
        assertEquals(MemoryTableScopeType.ASSISTANT, roundTrip.scopeType)
        assertEquals("unknown-assistant", roundTrip.scopeId)
    }

    @Test
    fun schemaValidationAcceptsInjectPolicyObject() {
        // Default schema ships an injectPolicy object; it must validate cleanly.
        validateMemoryTableSchemaJson(DEFAULT_MEMORY_TABLE_SCHEMA_JSON)
        validateMemoryTableSchemaJson(
            """
            {
              "tables": [
                {
                  "name": "memories",
                  "columns": [ { "name": "key" } ],
                  "injectPolicy": { "enabled": false, "triggerSend": true }
                }
              ]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun schemaValidationRejectsNonObjectInjectPolicy() {
        assertThrows(IllegalArgumentException::class.java) {
            validateMemoryTableSchemaJson(
                """
                {
                  "tables": [
                    {
                      "name": "memories",
                      "columns": [ { "name": "key" } ],
                      "injectPolicy": true
                    }
                  ]
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun schemaValidationRejectsNonBooleanInjectPolicyEnabled() {
        assertThrows(IllegalArgumentException::class.java) {
            validateMemoryTableSchemaJson(
                """
                {
                  "tables": [
                    {
                      "name": "memories",
                      "columns": [ { "name": "key" } ],
                      "injectPolicy": { "enabled": "yes" }
                    }
                  ]
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun readInjectionTogglesDefaultsToEnabledWhenPolicyMissing() {
        val toggles = readMemoryTableInjectionToggles(
            """
            {
              "tables": [
                { "name": "with_policy", "columns": [ { "name": "key" } ], "injectPolicy": { "enabled": false } },
                { "name": "no_policy", "columns": [ { "name": "key" } ] }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, toggles.size)
        assertFalse(toggles.first { it.name == "with_policy" }.injectEnabled)
        assertTrue(toggles.first { it.name == "no_policy" }.injectEnabled)
    }

    @Test
    fun setInjectionEnabledTogglesNamedTableOnly() {
        val schema = """
            {
              "tables": [
                { "name": "a", "columns": [ { "name": "key" } ] },
                { "name": "b", "columns": [ { "name": "key" } ], "injectPolicy": { "enabled": true } }
              ]
            }
        """.trimIndent()

        val disabledA = setMemoryTableInjectionEnabled(schema, "a", enabled = false)
        val toggles = readMemoryTableInjectionToggles(disabledA)
        assertFalse(toggles.first { it.name == "a" }.injectEnabled)
        // Untouched table keeps its original value.
        assertTrue(toggles.first { it.name == "b" }.injectEnabled)
        // Resulting schema is still valid.
        validateMemoryTableSchemaJson(disabledA)
    }

    @Test
    fun setInjectionEnabledReturnsOriginalForUnknownTable() {
        val schema = """
            {
              "tables": [
                { "name": "a", "columns": [ { "name": "key" } ] }
              ]
            }
        """.trimIndent()
        assertEquals(schema, setMemoryTableInjectionEnabled(schema, "missing", enabled = false))
    }
}
