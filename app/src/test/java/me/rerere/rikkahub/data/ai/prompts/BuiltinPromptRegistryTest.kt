package me.rerere.rikkahub.data.ai.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinPromptRegistryTest {

    @Test
    fun registryContainsAtLeastTheAc4MinimumKeys() {
        val keys = BuiltinPromptRegistry.all.keys
        assertTrue(keys.contains(BuiltinPromptRegistry.KEY_REPLY_DRAFT))
        assertTrue(keys.contains(BuiltinPromptRegistry.KEY_SUGGESTION))
        assertTrue(keys.contains(BuiltinPromptRegistry.KEY_MEMORY_TABLE_GUIDE))
        assertTrue(keys.contains(BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE))
        assertTrue(BuiltinPromptRegistry.all.size >= 4)
    }

    @Test
    fun getReturnsNonNullDefForEachKnownKey() {
        listOf(
            BuiltinPromptRegistry.KEY_REPLY_DRAFT,
            BuiltinPromptRegistry.KEY_SUGGESTION,
            BuiltinPromptRegistry.KEY_MEMORY_TABLE_GUIDE,
            BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE,
        ).forEach { key ->
            val def = BuiltinPromptRegistry[key]
            assertNotNull("expected def for $key", def)
            assertEquals(key, def!!.key)
            assertTrue("expected non-blank default content for $key", def.defaultContent.isNotBlank())
        }
    }

    @Test
    fun getReturnsNullForUnknownKey() {
        assertNull(BuiltinPromptRegistry["nope"])
    }

    @Test
    fun allBuiltinDefsAreConfigOnlyAndNotInjectable() {
        // #182 config-only 契约锁定：4 个内置模板的真实注入由各自专用 transformer / 特性流程完成，
        // 预设注入路径对 injectable=false 一律跳过。任何一个被误设为 true 都会导致双注入或字面宏泄漏。
        listOf(
            BuiltinPromptRegistry.KEY_REPLY_DRAFT,
            BuiltinPromptRegistry.KEY_SUGGESTION,
            BuiltinPromptRegistry.KEY_MEMORY_TABLE_GUIDE,
            BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE,
        ).forEach { key ->
            val def = BuiltinPromptRegistry[key]!!
            assertFalse("builtin def $key must be config-only (injectable=false)", def.injectable)
        }
        // 全量兜底：注册表中没有任何可注入的内置模板。
        assertTrue(BuiltinPromptRegistry.all.values.none { it.injectable })
    }

    @Test
    fun replyDraftAndSuggestionAreStaticAndSourcedFromExistingConstants() {
        val replyDraft = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_REPLY_DRAFT]!!
        val suggestion = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_SUGGESTION]!!
        assertFalse(replyDraft.dynamic)
        assertFalse(suggestion.dynamic)
        // Static defaults are sourced from the existing Suggestion.kt constants.
        assertEquals(DEFAULT_INPUT_DRAFT_PROMPT, replyDraft.defaultContent)
        assertEquals(DEFAULT_SUGGESTION_PROMPT, suggestion.defaultContent)
    }

    @Test
    fun allBuiltinTemplatesDeclareExactEditorVariables() {
        val replyDraft = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_REPLY_DRAFT]!!
        val suggestion = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_SUGGESTION]!!
        val memory = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_MEMORY_TABLE_GUIDE]!!
        val workspace = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]!!

        assertEquals(listOf("{locale}", "{content}", "{user_instruction}"), replyDraft.supportedVariables)
        assertEquals(listOf("{locale}", "{content}"), suggestion.supportedVariables)
        assertEquals(listOf("{{memory_tables}}"), memory.supportedVariables)
        assertEquals(listOf("{{workspace_name}}", "{{cwd}}"), workspace.supportedVariables)
        assertFalse(replyDraft.dynamic)
        assertFalse(suggestion.dynamic)
        assertTrue(memory.dynamic)
        assertTrue(workspace.dynamic)
    }

    @Test
    fun resolveContentReturnsStaticTemplateUnchangedIgnoringVars() {
        val def = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_SUGGESTION]!!
        val resolved = BuiltinPromptRegistry.resolveContent(
            def = def,
            override = null,
            vars = mapOf("locale" to "English", "content" to "irrelevant"),
        )
        // Static template ignores vars and is returned verbatim.
        assertEquals(def.defaultContent, resolved)
    }

    @Test
    fun resolveContentReplacesDynamicMacros() {
        val def = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]!!
        val resolved = BuiltinPromptRegistry.resolveContent(
            def = def,
            override = null,
            vars = mapOf("workspace_name" to "my-space", "cwd" to "/workspace/proj"),
        )
        assertTrue(resolved.contains("my-space"))
        assertTrue(resolved.contains("/workspace/proj"))
        assertFalse(resolved.contains("{{workspace_name}}"))
        assertFalse(resolved.contains("{{cwd}}"))
        assertEquals(buildWorkspaceGuidePrompt("my-space", "/workspace/proj"), resolved)
        assertTrue(resolved.contains("`/upload` as READ-ONLY"))
        assertTrue(resolved.contains("`/skills_private/<skill-name>/`"))
        assertTrue(resolved.contains("`workspace_edit_file` for targeted edits"))
    }

    @Test
    fun resolveContentReplacesSpacedMacroForm() {
        val def = BuiltinPromptDef(
            key = "spaced",
            defaultContent = "hello {{ name }} world",
            dynamic = true,
            overridable = true,
            supportedVariables = listOf("{{name}}"),
        )
        val resolved = BuiltinPromptRegistry.resolveContent(def, override = null, vars = mapOf("name" to "Kiro"))
        assertEquals("hello Kiro world", resolved)
    }

    @Test
    fun resolveContentSubstitutesEmptyStringForProvidedBlankVarAndDoesNotThrow() {
        val def = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]!!
        // Provide an empty value for cwd and omit workspace_name entirely: must not throw.
        val resolved = BuiltinPromptRegistry.resolveContent(
            def = def,
            override = null,
            vars = mapOf("cwd" to ""),
        )
        // Provided-but-blank macro is replaced with empty string.
        assertFalse(resolved.contains("{{cwd}}"))
    }

    @Test
    fun resolveContentPrefersOverrideWhenPresent() {
        val def = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]!!
        val resolved = BuiltinPromptRegistry.resolveContent(
            def = def,
            override = "custom {{workspace_name}} body",
            vars = mapOf("workspace_name" to "override-space"),
        )
        assertEquals("custom override-space body", resolved)
    }

    @Test
    fun resolveContentDoesNotReparseMacrosInsideRuntimeValues() {
        val def = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]!!

        val resolved = BuiltinPromptRegistry.resolveContent(
            def = def,
            override = "name={{workspace_name}}; cwd={{cwd}}",
            vars = mapOf(
                "workspace_name" to "repo-{{cwd}}",
                "cwd" to "/workspace/project",
            ),
        )

        assertEquals("name=repo-{{cwd}}; cwd=/workspace/project", resolved)
    }

    @Test
    fun resolveContentDoesNotThrowOnUnknownVars() {
        val def = BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]!!
        // Unknown var keys are simply ignored; no exception.
        val resolved = BuiltinPromptRegistry.resolveContent(
            def = def,
            override = null,
            vars = mapOf("totally_unknown" to "x", "workspace_name" to "ws", "cwd" to "/w"),
        )
        assertTrue(resolved.contains("ws"))
    }
}
