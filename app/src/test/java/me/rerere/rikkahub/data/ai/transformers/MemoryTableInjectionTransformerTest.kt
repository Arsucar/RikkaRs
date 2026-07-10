package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableInjectionTransformerTest {
    @Test
    fun buildPromptIncludesAllSupportedScopes() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template")),
            documents = listOf(
                document("global", MemoryTableScopeType.GLOBAL),
                document("assistant", MemoryTableScopeType.ASSISTANT),
                document("conversation", MemoryTableScopeType.CONVERSATION),
            ),
        )

        assertTrue(prompt.contains("scope=global"))
        assertTrue(prompt.contains("scope=assistant"))
        assertTrue(prompt.contains("scope=conversation"))
    }

    @Test
    fun buildPromptAppliesDocumentLimit() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template")),
            documents = listOf(
                document("one", MemoryTableScopeType.ASSISTANT),
                document("two", MemoryTableScopeType.ASSISTANT),
            ),
            maxDocuments = 1,
        )

        assertTrue(prompt.contains("documents=1/2"))
        assertTrue(prompt.contains("one"))
        assertFalse(prompt.contains("two"))
        assertTrue(prompt.contains("omitted by document limit"))
    }

    @Test
    fun buildPromptAppliesSchemaTokenAndCharLimits() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(
                template(
                    id = "template",
                    schemaJson = """{"tables":[],"maxInjectTokens":20}""",
                )
            ),
            documents = listOf(
                document("large", MemoryTableScopeType.ASSISTANT, payloadJson = """{"value":"${"x".repeat(400)}"}"""),
            ),
            maxTokens = 100,
            maxChars = 1_000,
        )

        assertTrue(prompt.length <= 80)
    }

    @Test
    fun buildPromptExcludesPayloadForTablesWithInjectionDisabled() {
        val schemaJson = """
            {
              "tables": [
                { "name": "memories", "columns": [{ "name": "key" }], "injectPolicy": { "enabled": true } },
                { "name": "secrets", "columns": [{ "name": "key" }], "injectPolicy": { "enabled": false } }
              ],
              "maxInjectTokens": 800
            }
        """.trimIndent()
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = schemaJson)),
            documents = listOf(
                document(
                    "doc",
                    MemoryTableScopeType.ASSISTANT,
                    payloadJson = """{"memories":[{"key":"visible-row"}],"secrets":[{"key":"hidden-row"}]}""",
                ),
            ),
        )

        assertTrue(prompt.contains("visible-row"))
        assertFalse(prompt.contains("hidden-row"))
    }

    @Test
    fun buildPromptKeepsOnlyRecentRelevantRowsForTriggerSendTables() {
        val schemaJson = """
            {
              "tables": [
                { "name": "topics", "columns": [{ "name": "key" }], "injectPolicy": { "triggerSend": true } }
              ],
              "maxInjectTokens": 800
            }
        """.trimIndent()
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = schemaJson)),
            documents = listOf(
                document(
                    "doc",
                    MemoryTableScopeType.ASSISTANT,
                    payloadJson = """{"topics":[{"key":"kubernetes"},{"key":"gardening"}]}""",
                ),
            ),
            recentConversationText = "How do I scale a kubernetes deployment?",
        )

        assertTrue(prompt.contains("kubernetes"))
        assertFalse(prompt.contains("gardening"))
    }

    @Test
    fun buildPromptKeepsAllRowsForTriggerSendTableWhenNoRecentText() {
        val schemaJson = """
            {
              "tables": [
                { "name": "topics", "columns": [{ "name": "key" }], "injectPolicy": { "triggerSend": true } }
              ],
              "maxInjectTokens": 800
            }
        """.trimIndent()
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = schemaJson)),
            documents = listOf(
                document(
                    "doc",
                    MemoryTableScopeType.ASSISTANT,
                    payloadJson = """{"topics":[{"key":"kubernetes"},{"key":"gardening"}]}""",
                ),
            ),
            recentConversationText = "",
        )

        assertTrue(prompt.contains("kubernetes"))
        assertTrue(prompt.contains("gardening"))
    }

    @Test
    fun disabledModeHasNoInjectionContentWhenNoDocumentsAreLoaded() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template")),
            documents = emptyList(),
        )

        assertTrue(prompt.isBlank())
    }

    @Test
    fun mergeSubstitutesMacroWhenPresent() {
        val merged = mergeMemoryTableIntoSystemText(
            originalText = "Intro.\n$MEMORY_TABLE_MACRO\nOutro.",
            content = "<memory_tables>DATA</memory_tables>",
        )

        assertTrue(merged.contains("<memory_tables>DATA</memory_tables>"))
        assertFalse(merged.contains(MEMORY_TABLE_MACRO))
        assertTrue(merged.indexOf("DATA") in merged.indexOf("Intro.")..merged.indexOf("Outro."))
    }

    @Test
    fun mergeAppendsWhenNoMacroPresent() {
        val merged = mergeMemoryTableIntoSystemText(
            originalText = "System prompt.",
            content = "<memory_tables>DATA</memory_tables>",
        )

        assertTrue(merged.startsWith("System prompt."))
        assertTrue(merged.trimEnd().endsWith("</memory_tables>"))
    }

    private fun template(
        id: String,
        schemaJson: String = """{"tables":[],"maxInjectTokens":800}""",
    ) = MemoryTableTemplate(
        id = id,
        name = "Template $id",
        schemaJson = schemaJson,
    )

    private fun document(
        id: String,
        scopeType: MemoryTableScopeType,
        payloadJson: String = """{"id":"$id"}""",
    ) = MemoryTableDocument(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = id,
        payloadJson = payloadJson,
    )
}
