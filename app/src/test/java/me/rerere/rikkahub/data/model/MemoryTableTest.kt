package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableTest {
    @Test
    fun memoryTableRequiresGlobalAndAssistantGate() {
        assertFalse(shouldEnableMemoryTable(settingsEnabled = false, assistantEnabled = false))
        assertFalse(shouldEnableMemoryTable(settingsEnabled = true, assistantEnabled = false))
        assertFalse(shouldEnableMemoryTable(settingsEnabled = false, assistantEnabled = true))
        assertTrue(shouldEnableMemoryTable(settingsEnabled = true, assistantEnabled = true))
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
