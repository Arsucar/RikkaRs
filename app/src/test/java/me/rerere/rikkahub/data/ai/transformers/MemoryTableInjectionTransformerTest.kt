package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import org.junit.Assert.assertEquals
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
    fun defaultLimitsPreserveExistingHeaderBehavior() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template")),
            documents = listOf(document("one", MemoryTableScopeType.ASSISTANT)),
        )

        assertTrue(prompt.contains("documents=1/1"))
        assertTrue(prompt.contains("maxTokens=800"))
        assertTrue(prompt.contains("maxChars=3200"))
    }

    @Test
    fun nullDocumentLimitIncludesEveryAuthorizedDocument() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = """{"tables":[]}""")),
            documents = listOf(
                document("one", MemoryTableScopeType.ASSISTANT),
                document("two", MemoryTableScopeType.GLOBAL),
            ),
            maxDocuments = null,
            maxTokens = null,
            maxChars = null,
        )

        assertTrue(prompt.contains("documents=2/2"))
        assertTrue(prompt.contains("one"))
        assertTrue(prompt.contains("two"))
        assertFalse(prompt.contains("omitted by document limit"))
    }

    @Test
    fun nullTokenLimitStillHonorsExplicitCharacterLimit() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = """{"tables":[]}""")),
            documents = listOf(
                document(
                    "large",
                    MemoryTableScopeType.ASSISTANT,
                    payloadJson = """{"value":"${"x".repeat(1_000)}"}""",
                )
            ),
            maxTokens = null,
            maxChars = 300,
        )

        assertTrue(prompt.length <= 300)
        assertTrue(prompt.contains("maxTokens=unlimited"))
        assertTrue(prompt.contains("maxChars=300"))
    }

    @Test
    fun nullCharacterLimitStillHonorsTokenDerivedLimit() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = """{"tables":[]}""")),
            documents = listOf(
                document(
                    "large",
                    MemoryTableScopeType.ASSISTANT,
                    payloadJson = """{"value":"${"x".repeat(1_000)}"}""",
                )
            ),
            maxTokens = 100,
            maxChars = null,
        )

        assertTrue(prompt.length <= 400)
        assertTrue(prompt.contains("maxTokens=100"))
        assertTrue(prompt.contains("maxChars=400"))
    }

    @Test
    fun allNullLimitsDoNotTruncateAndUseUnlimitedHeader() {
        val payload = """{"value":"${"x".repeat(1_000)}"}"""
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = """{"tables":[]}""")),
            documents = listOf(document("large", MemoryTableScopeType.ASSISTANT, payload)),
            maxDocuments = null,
            maxTokens = null,
            maxChars = null,
        )

        assertTrue(prompt.contains("maxTokens=unlimited"))
        assertTrue(prompt.contains("maxChars=unlimited"))
        assertTrue(prompt.contains(payload))
        assertTrue(prompt.trimEnd().endsWith("</memory_tables>"))
    }

    @Test
    fun schemaTokenLimitTakesPrecedenceWhenUserTokenLimitIsNull() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(
                template(
                    "template",
                    schemaJson = """{"tables":[],"maxInjectTokens":100}""",
                )
            ),
            documents = listOf(
                document(
                    "large",
                    MemoryTableScopeType.ASSISTANT,
                    payloadJson = """{"value":"${"x".repeat(1_000)}"}""",
                )
            ),
            maxTokens = null,
            maxChars = null,
        )

        assertTrue(prompt.length <= 400)
        assertTrue(prompt.contains("maxTokens=100"))
        assertTrue(prompt.contains("maxChars=400"))
    }

    @Test
    fun anyExplicitZeroOrNegativeBudgetDisablesInjection() {
        val templates = listOf(template("template", schemaJson = """{"tables":[]}"""))
        val documents = listOf(document("doc", MemoryTableScopeType.ASSISTANT))

        assertTrue(buildMemoryTablePrompt(templates, documents, maxDocuments = 0).isBlank())
        assertTrue(buildMemoryTablePrompt(templates, documents, maxTokens = 0).isBlank())
        assertTrue(buildMemoryTablePrompt(templates, documents, maxChars = 0).isBlank())
        assertTrue(buildMemoryTablePrompt(templates, documents, maxDocuments = -1).isBlank())
        assertTrue(buildMemoryTablePrompt(templates, documents, maxTokens = -1).isBlank())
        assertTrue(buildMemoryTablePrompt(templates, documents, maxChars = -1).isBlank())
    }

    @Test
    fun tokenToCharacterBudgetSaturatesInsteadOfOverflowing() {
        assertEquals((Int.MAX_VALUE / 4) * 4, tokensToCharLimit(Int.MAX_VALUE / 4))
        assertEquals(Int.MAX_VALUE, tokensToCharLimit(Int.MAX_VALUE / 4 + 1))
        assertEquals(Int.MAX_VALUE, tokensToCharLimit(Int.MAX_VALUE))

        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template", schemaJson = """{"tables":[]}""")),
            documents = listOf(document("doc", MemoryTableScopeType.ASSISTANT)),
            maxTokens = Int.MAX_VALUE,
            maxChars = null,
        )

        assertTrue(prompt.isNotBlank())
        assertTrue(prompt.contains("maxTokens=${Int.MAX_VALUE}"))
        assertTrue(prompt.contains("maxChars=${Int.MAX_VALUE}"))
        assertFalse(prompt.contains("maxChars=-"))
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
