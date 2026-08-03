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

    @Test
    fun `ST comment prefix does not block setvar getvar`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(
            "{{// comment}}{{setvar::foo::bar}}value={{getvar::foo}}",
            vars,
        )
        assertEquals("value=bar", out)
        assertEquals("bar", vars["foo"])
        assertTrue("//" !in out)
        assertTrue("comment" !in out)
    }

    @Test
    fun `multiline ST comment is deleted and following macros expand`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(
            "{{//\npreset comment\n}}\n{{setvar::foo::bar}}value={{getvar::foo}}",
            vars,
        )
        assertEquals("\nvalue=bar", out)
        assertEquals("bar", vars["foo"])
    }

    @Test
    fun `many sequential setvars all apply beyond former depth budget`() {
        val count = 100
        val sb = StringBuilder()
        repeat(count) { i ->
            sb.append("{{setvar::k$i::v$i}}")
        }
        sb.append("last={{getvar::k99}}")
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(sb.toString(), vars)
        assertEquals("last=v99", out)
        assertEquals(count, vars.size)
        assertEquals("v0", vars["k0"])
        assertEquals("v50", vars["k50"])
        assertEquals("v99", vars["k99"])
    }

    @Test
    fun `trim does not block following getvar`() {
        val vars = mutableMapOf("x" to "ok")
        val out = ConversationVariables.expandMacros("{{trim}}{{getvar::x}}{{trim}}", vars)
        assertEquals("ok", out)
    }

    @Test
    fun `newline expands to line break`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros("a{{newline}}b", vars)
        assertEquals("a\nb", out)
    }

    @Test
    fun `unknown macros are left intact without blocking later macros`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(
            "{{char}}{{setvar::n::1}}={{getvar::n}}",
            vars,
        )
        assertEquals("{{char}}=1", out)
        assertEquals("1", vars["n"])
    }

    @Test
    fun `nested unknown macro does not block outer setvar`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(
            "{{setvar::name::Hello {{char}}!}}{{getvar::name}}",
            vars,
        )
        assertEquals("Hello {{char}}!", out)
        assertEquals("Hello {{char}}!", vars["name"])
    }

    @Test
    fun `malformed setvar without value separator is left intact`() {
        val vars = mutableMapOf<String, String>()
        val out = ConversationVariables.expandMacros(
            "{{setvar::onlyname}}{{setvar::ok::1}}={{getvar::ok}}",
            vars,
        )
        assertEquals("{{setvar::onlyname}}=1", out)
        assertEquals("1", vars["ok"])
        assertTrue("onlyname" !in vars)
    }
}
