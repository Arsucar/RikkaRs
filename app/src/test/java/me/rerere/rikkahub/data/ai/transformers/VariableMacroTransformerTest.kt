package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.ai.variables.ConversationVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableMacroTransformerTest {

    @Test
    fun `getvar undefined becomes empty string`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros("hello {{getvar::missing}} world", vars)
        assertEquals("hello  world", out)
    }

    @Test
    fun `setvar then getvar`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(
            "{{setvar::foo::bar}}value={{getvar::foo}}",
            vars,
        )
        assertEquals("value=bar", out)
        assertEquals("bar", vars["foo"])
    }

    @Test
    fun `addvar appends`() {
        val vars = mutableMapOf("n" to "a")
        val out = ConversationVariables.expandMacros("{{addvar::n::b}}{{getvar::n}}", vars)
        assertEquals("ab", out)
        assertEquals("ab", vars["n"])
    }

    @Test
    fun `setvar self removes from text`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros("x{{setvar::k::v}}y", vars)
        assertEquals("xy", out)
        assertEquals("v", vars["k"])
    }

    @Test
    fun `nested macros resolve inner first`() {
        val vars = mutableMapOf("inner" to "42")
        val out = ConversationVariables.expandMacros(
            "{{setvar::outer::{{getvar::inner}}}}{{getvar::outer}}",
            vars,
        )
        assertEquals("42", out)
        assertEquals("42", vars["outer"])
    }

    @Test
    fun `getglobalvar returns empty in mvp`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros("g={{getglobalvar::x}}", vars)
        assertEquals("g=", out)
    }

    @Test
    fun `no macros leaves text unchanged`() {
        val vars = mutableMapOf("a" to "1")
        val text = "plain text without macros"
        assertEquals(text, ConversationVariables.expandMacros(text, vars))
        assertEquals(mapOf("a" to "1"), vars)
    }

    @Test
    fun `value length is truncated`() {
        val long = "x".repeat(ConversationVariables.MAX_VALUE_LENGTH + 50)
        val vars = mutableMapOf<String, String>()
        ConversationVariables.expandMacros("{{setvar::big::$long}}", vars)
        assertEquals(ConversationVariables.MAX_VALUE_LENGTH, vars["big"]!!.length)
    }

    @Test
    fun `variable count cap rejects extra keys`() {
        val vars = (1..ConversationVariables.MAX_VARIABLE_COUNT).associate { "k$it" to "v" }.toMutableMap()
        ConversationVariables.expandMacros("{{setvar::overflow::x}}", vars)
        assertTrue("overflow" !in vars)
        assertEquals(ConversationVariables.MAX_VARIABLE_COUNT, vars.size)
    }
}
