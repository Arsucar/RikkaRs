package me.rerere.rikkahub.ui.pages.assistant.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTableBudgetInputTest {
    @Test
    fun onlyEmptyInputMeansUnlimited() {
        val parsed = parseMemoryTableBudgetInput("")

        assertTrue(parsed.isValid)
        assertNull(parsed.value)
    }

    @Test
    fun whitespaceOnlyInputIsInvalid() {
        val parsed = parseMemoryTableBudgetInput("   ")

        assertFalse(parsed.isValid)
        assertNull(parsed.value)
    }

    @Test
    fun zeroAndPositiveDecimalInputsAreValid() {
        assertEquals(0, parseMemoryTableBudgetInput("0").value)
        assertEquals(12_000, parseMemoryTableBudgetInput("12000").value)
        assertTrue(parseMemoryTableBudgetInput("0007").isValid)
    }

    @Test
    fun negativeNonNumericAndOverflowInputsAreInvalid() {
        listOf("-1", "+1", "1.5", "12a", "2147483648").forEach { input ->
            val parsed = parseMemoryTableBudgetInput(input)
            assertFalse(input, parsed.isValid)
            assertNull(parsed.value)
        }
    }

    @Test
    fun formatterUsesLocalizedUnlimitedLabelOnlyForNull() {
        assertEquals("Unlimited", formatMemoryTableBudgetValue(null, "Unlimited"))
        assertEquals("0", formatMemoryTableBudgetValue(0, "Unlimited"))
        assertEquals("800", formatMemoryTableBudgetValue(800, "Unlimited"))
    }
}
