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
    fun buildPromptAppliesRowLimit() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template")),
            documents = listOf(
                document("one", MemoryTableScopeType.ASSISTANT),
                document("two", MemoryTableScopeType.ASSISTANT),
            ),
            maxRows = 1,
        )

        assertTrue(prompt.contains("one"))
        assertFalse(prompt.contains("two"))
        assertTrue(prompt.contains("omitted by row limit"))
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
    fun disabledModeHasNoInjectionContentWhenNoDocumentsAreLoaded() {
        val prompt = buildMemoryTablePrompt(
            templates = listOf(template("template")),
            documents = emptyList(),
        )

        assertTrue(prompt.isBlank())
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
