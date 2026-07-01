package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class WorkspaceCwdUtilsTest {

    // ---- normalizeWorkspaceCwd ----

    @Test
    fun normalize_nullOrBlank_fallsBackToRoot() {
        assertEquals("/workspace", normalizeWorkspaceCwd(null))
        assertEquals("/workspace", normalizeWorkspaceCwd(""))
        assertEquals("/workspace", normalizeWorkspaceCwd("   "))
    }

    @Test
    fun normalize_keepsNormalPath() {
        assertEquals("/workspace", normalizeWorkspaceCwd("/workspace"))
        assertEquals("/workspace/work", normalizeWorkspaceCwd("/workspace/work"))
        assertEquals("/workspace/a/b", normalizeWorkspaceCwd("/workspace/a/b"))
    }

    @Test
    fun normalize_collapsesRedundantSlashesAndBackslashes() {
        assertEquals("/workspace/a/b", normalizeWorkspaceCwd("/workspace//a///b"))
        assertEquals("/workspace/a/b", normalizeWorkspaceCwd("\\workspace\\a\\b"))
        assertEquals("/workspace/a", normalizeWorkspaceCwd("/workspace/a/"))
    }

    @Test
    fun normalize_resolvesDotAndDotDotWithinRoot() {
        assertEquals("/workspace/b", normalizeWorkspaceCwd("/workspace/a/../b"))
        assertEquals("/workspace/a", normalizeWorkspaceCwd("/workspace/./a"))
        assertEquals("/workspace", normalizeWorkspaceCwd("/workspace/a/.."))
    }

    @Test
    fun normalize_dotDotEscapingRootFallsBack() {
        assertEquals("/workspace", normalizeWorkspaceCwd("/workspace/../etc"))
        assertEquals("/workspace", normalizeWorkspaceCwd("/etc/passwd"))
        assertEquals("/workspace", normalizeWorkspaceCwd("/workspace/../.."))
    }

    // ---- resolveEffectiveWorkspaceCwd ----

    private fun conv(cwd: String?) =
        Conversation(assistantId = Uuid.random(), messageNodes = emptyList(), workspaceCwd = cwd)

    @Test
    fun resolve_conversationCwd_takesPriority() {
        val assistant = Assistant(defaultWorkspaceCwd = "/workspace/assistant")
        assertEquals(
            "/workspace/conv",
            resolveEffectiveWorkspaceCwd(conv("/workspace/conv"), assistant),
        )
    }

    @Test
    fun resolve_fallsBackToAssistantDefault() {
        val assistant = Assistant(defaultWorkspaceCwd = "/workspace/assistant")
        assertEquals(
            "/workspace/assistant",
            resolveEffectiveWorkspaceCwd(conv(null), assistant),
        )
    }

    @Test
    fun resolve_fallsBackToRootWhenBothNull() {
        assertEquals(
            "/workspace",
            resolveEffectiveWorkspaceCwd(conv(null), Assistant()),
        )
    }

    @Test
    fun resolve_normalizesResult() {
        val assistant = Assistant(defaultWorkspaceCwd = "/workspace/../etc")
        assertEquals(
            "/workspace",
            resolveEffectiveWorkspaceCwd(conv(null), assistant),
        )
        assertEquals(
            "/workspace/x",
            resolveEffectiveWorkspaceCwd(conv("/workspace//x/"), Assistant()),
        )
    }
}
