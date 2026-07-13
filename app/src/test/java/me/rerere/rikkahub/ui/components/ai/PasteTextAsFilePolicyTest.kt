package me.rerere.rikkahub.ui.components.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class PasteTextAsFilePolicyTest {
    @Test
    fun `conversion follows enabled editing and threshold matrix`() {
        val threshold = 1_000
        val cases = listOf(
            Case(
                description = "editing long text remains regular text",
                enabled = true,
                isEditing = true,
                textLength = threshold + 1,
                expected = false,
            ),
            Case(
                description = "non-editing long text converts to a file",
                enabled = true,
                isEditing = false,
                textLength = threshold + 1,
                expected = true,
            ),
            Case(
                description = "text equal to the threshold remains regular text",
                enabled = true,
                isEditing = false,
                textLength = threshold,
                expected = false,
            ),
            Case(
                description = "text below the threshold remains regular text",
                enabled = true,
                isEditing = false,
                textLength = threshold - 1,
                expected = false,
            ),
            Case(
                description = "disabled conversion leaves long text unchanged",
                enabled = false,
                isEditing = false,
                textLength = threshold + 1,
                expected = false,
            ),
        )

        cases.forEach { case ->
            assertEquals(
                case.description,
                case.expected,
                shouldConvertPastedTextToFile(
                    enabled = case.enabled,
                    isEditing = case.isEditing,
                    textLength = case.textLength,
                    threshold = threshold,
                ),
            )
        }
    }

    private data class Case(
        val description: String,
        val enabled: Boolean,
        val isEditing: Boolean,
        val textLength: Int,
        val expected: Boolean,
    )
}
