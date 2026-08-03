package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.ai.variables.UpdateVariableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVariableOutputTransformerTest {

    @Test
    fun `successful patch strips block and updates map`() {
        val text = """Hello
<UpdateVariable>
[{"op":"add","path":"/score","value":"10"}]
</UpdateVariable>
Done"""
        val result = UpdateVariableParser.apply(text, emptyMap())
        assertTrue(result.applied)
        assertEquals("10", result.variables["score"])
        assertFalse(result.text.contains("UpdateVariable", ignoreCase = true))
        assertTrue(result.text.contains("Hello"))
        assertTrue(result.text.contains("Done"))
    }

    @Test
    fun `replace and remove ops`() {
        val current = mapOf("a" to "1", "b" to "2")
        val text = """
            <UpdateVariable>
            [
              {"op":"replace","path":"/a","value":"9"},
              {"op":"remove","path":"/b"}
            ]
            </UpdateVariable>
        """.trimIndent()
        val result = UpdateVariableParser.apply(text, current)
        assertTrue(result.applied)
        assertEquals("9", result.variables["a"])
        assertFalse(result.variables.containsKey("b"))
    }

    @Test
    fun `malformed json keeps original text`() {
        val text = "keep me <UpdateVariable>{not-json}</UpdateVariable> tail"
        val result = UpdateVariableParser.apply(text, mapOf("x" to "1"))
        assertFalse(result.applied)
        assertEquals(text, result.text)
        assertEquals(mapOf("x" to "1"), result.variables)
    }

    @Test
    fun `incomplete open block keeps original`() {
        val text = "prefix <UpdateVariable>[{\"op\":\"add\""
        val result = UpdateVariableParser.apply(text, emptyMap())
        assertFalse(result.applied)
        assertEquals(text, result.text)
    }

    @Test
    fun `wrapper object with JSONPatch key`() {
        val text = """
            <UpdateVariable>
            {"JSONPatch":[{"op":"add","path":"/name","value":"Alice"}]}
            </UpdateVariable>
        """.trimIndent()
        val result = UpdateVariableParser.apply(text, emptyMap())
        assertTrue(result.applied)
        assertEquals("Alice", result.variables["name"])
    }

    @Test
    fun `st xml shell with Analysis and JSONPatch applies and strips`() {
        val text = """
            Before
            <UpdateVariable>
              <Analysis>score should rise after combat</Analysis>
              <JSONPatch>
              [{"op":"replace","path":"/score","value":"42"}]
              </JSONPatch>
            </UpdateVariable>
            After
        """.trimIndent()
        val result = UpdateVariableParser.apply(text, mapOf("score" to "1"))
        assertTrue(result.applied)
        assertEquals("42", result.variables["score"])
        assertFalse(result.text.contains("UpdateVariable", ignoreCase = true))
        assertFalse(result.text.contains("Analysis", ignoreCase = true))
        assertFalse(result.text.contains("JSONPatch", ignoreCase = true))
        assertTrue(result.text.contains("Before"))
        assertTrue(result.text.contains("After"))
    }

    @Test
    fun `malformed JSONPatch inside xml shell keeps original`() {
        val text = """
            keep
            <UpdateVariable>
              <Analysis>ignored</Analysis>
              <JSONPatch>{not-json}</JSONPatch>
            </UpdateVariable>
            tail
        """.trimIndent()
        val current = mapOf("x" to "1")
        val result = UpdateVariableParser.apply(text, current)
        assertFalse(result.applied)
        assertEquals(text, result.text)
        assertEquals(current, result.variables)
    }

    @Test
    fun `no block is no-op`() {
        val text = "just text"
        val result = UpdateVariableParser.apply(text, mapOf("k" to "v"))
        assertFalse(result.applied)
        assertEquals(text, result.text)
        assertEquals(mapOf("k" to "v"), result.variables)
    }
}
