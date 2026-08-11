package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedWriteRootsTest {

    @Test
    fun matchesRootPrefix_requiresBoundarySeparator() {
        assertTrue(matchesRootPrefix("/skills", "/skills"))
        assertTrue(matchesRootPrefix("/skills/x", "/skills"))
        assertTrue(matchesRootPrefix("/skills/x/y", "/skills/x"))

        assertFalse(matchesRootPrefix("/skills-private/x", "/skills"))
        assertFalse(matchesRootPrefix("/skills_private/x", "/skills"))
        assertFalse(matchesRootPrefix("/skills/x2", "/skills/x"))
        assertFalse(matchesRootPrefix("/skill/x", "/skills"))
    }

    @Test
    fun needsPathHardApproval_tableFromDesign() {
        // /workspace/a — free
        assertFalse(needsPathHardApproval("/workspace/a", emptyList()))
        // /tmp/a — free
        assertFalse(needsPathHardApproval("/tmp/a", emptyList()))
        // /skills/x — outside, no trust
        assertTrue(needsPathHardApproval("/skills/x", emptyList()))
        // /skills/x with ["/skills"] — trusted
        assertFalse(needsPathHardApproval("/skills/x", listOf("/skills")))
        // /skills-private/x with ["/skills"] — not trusted (boundary)
        assertTrue(needsPathHardApproval("/skills-private/x", listOf("/skills")))
        // /skills_private/x with ["/skills"] — not trusted
        assertTrue(needsPathHardApproval("/skills_private/x", listOf("/skills")))
        // /skills/x/y with ["/skills/x"] — trusted
        assertFalse(needsPathHardApproval("/skills/x/y", listOf("/skills/x")))
        // /skills/x2 with ["/skills/x"] — not trusted
        assertTrue(needsPathHardApproval("/skills/x2", listOf("/skills/x")))
    }

    @Test
    fun normalizeTrustedWriteRoot_rejectsTraversalAndRelative() {
        assertEquals("/skills/my-skill", normalizeTrustedWriteRoot("/skills/my-skill/"))
        assertEquals("/skills/my-skill", normalizeTrustedWriteRoot("/skills/my-skill"))
        assertNull(normalizeTrustedWriteRoot("../skills"))
        assertNull(normalizeTrustedWriteRoot("/skills/../tmp"))
        assertNull(normalizeTrustedWriteRoot("skills/foo"))
        assertNull(normalizeTrustedWriteRoot(""))
        assertNull(normalizeTrustedWriteRoot("   "))
    }

    @Test
    fun deriveTrustedWriteRoot_skillTwoSegmentsOrParentDir() {
        assertEquals("/skills/my-skill", deriveTrustedWriteRoot("/skills/my-skill/SKILL.md"))
        assertEquals("/skills/my-skill", deriveTrustedWriteRoot("/skills/my-skill"))
        assertEquals(
            "/skills_private/private-skill",
            deriveTrustedWriteRoot("/skills_private/private-skill/notes.md"),
        )
        assertEquals("/custom/dir", deriveTrustedWriteRoot("/custom/dir/file.txt"))
        assertEquals("/only", deriveTrustedWriteRoot("/only"))
        assertNull(deriveTrustedWriteRoot("/skills/../evil"))
        assertNull(deriveTrustedWriteRoot("relative/path"))
    }

    @Test
    fun isTrustableWriteTool_onlyWriteAndEdit() {
        assertTrue(isTrustableWriteTool("workspace_write_file"))
        assertTrue(isTrustableWriteTool("workspace_edit_file"))
        assertFalse(isTrustableWriteTool("workspace_shell"))
        assertFalse(isTrustableWriteTool("skill_tool"))
        assertFalse(isTrustableWriteTool("workspace_read_file"))
    }

    @Test
    fun isOutsideBuiltinWritableRoots_boundarySafe() {
        assertFalse(isOutsideBuiltinWritableRoots("/workspace"))
        assertFalse(isOutsideBuiltinWritableRoots("/workspace/a"))
        assertFalse(isOutsideBuiltinWritableRoots("/tmp/x"))
        assertTrue(isOutsideBuiltinWritableRoots("/workspace-extra/a"))
        assertTrue(isOutsideBuiltinWritableRoots("/skills/x"))
    }

    @Test
    fun normalizeRootfsToolPath_rejectsTraversal() {
        assertEquals("/skills/my-skill/file.md", normalizeRootfsToolPath("/skills/my-skill/file.md"))
        assertEquals("/skills/my-skill", normalizeRootfsToolPath("/skills/./my-skill/"))
        assertEquals("/", normalizeRootfsToolPath("/"))
        assertNull(normalizeRootfsToolPath("/skills/my-skill/../other/evil.md"))
        assertNull(normalizeRootfsToolPath("../skills"))
        assertNull(normalizeRootfsToolPath("relative"))
        assertNull(normalizeRootfsToolPath(""))
        assertNull(normalizeRootfsToolPath("/skills/\u0000evil"))
    }

    @Test
    fun needsPathHardApproval_rejectsDotDotTraversalBypass() {
        // Traversal out of a trusted skill root must still need hard approval.
        assertTrue(
            needsPathHardApproval(
                "/skills/my-skill/../other/evil.md",
                listOf("/skills/my-skill"),
            ),
        )
        assertTrue(
            needsPathHardApproval(
                "/skills/my-skill/../../tmp/evil",
                listOf("/skills/my-skill"),
            ),
        )
        // Valid path under trusted root still free.
        assertFalse(
            needsPathHardApproval(
                "/skills/my-skill/notes.md",
                listOf("/skills/my-skill"),
            ),
        )
    }

    @Test
    fun workspaceWriteEditNeedsApproval_switchOffSkipsPathHardApproval() {
        val off = mapOf("workspace_write_file" to false)
        // Explicit OFF must fully disable approval, including outside builtin roots.
        assertFalse(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/skills/x/SKILL.md",
                off,
                emptyList(),
            ),
        )
        assertFalse(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/etc/hosts",
                off,
                emptyList(),
            ),
        )
        assertFalse(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/workspace/a.txt",
                off,
                emptyList(),
            ),
        )
    }

    @Test
    fun workspaceWriteEditNeedsApproval_defaultStillFreeUnderBuiltinRoots() {
        assertFalse(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/workspace/a.txt",
                emptyMap(),
                emptyList(),
            ),
        )
        assertFalse(
            workspaceWriteEditNeedsApproval(
                "workspace_edit_file",
                "/tmp/scratch.txt",
                emptyMap(),
                emptyList(),
            ),
        )
        // Outside builtin without trust still needs approval when switch is not OFF.
        assertTrue(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/skills/x/SKILL.md",
                emptyMap(),
                emptyList(),
            ),
        )
    }

    @Test
    fun workspaceWriteEditNeedsApproval_switchOnAlwaysApproves() {
        val on = mapOf("workspace_write_file" to true)
        assertTrue(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/workspace/a.txt",
                on,
                emptyList(),
            ),
        )
    }

    @Test
    fun workspaceWriteEditNeedsApproval_trustedRootBypassesWhenSwitchDefault() {
        assertFalse(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/skills/x/SKILL.md",
                emptyMap(),
                listOf("/skills/x"),
            ),
        )
        // Switch ON still forces approval even under trusted root.
        assertTrue(
            workspaceWriteEditNeedsApproval(
                "workspace_write_file",
                "/skills/x/SKILL.md",
                mapOf("workspace_write_file" to true),
                listOf("/skills/x"),
            ),
        )
    }

    @Test
    fun isWorkspaceToolApprovalExplicitlyDisabled_onlyFalseOverride() {
        assertTrue(
            isWorkspaceToolApprovalExplicitlyDisabled(
                "workspace_write_file",
                mapOf("workspace_write_file" to false),
            ),
        )
        assertFalse(
            isWorkspaceToolApprovalExplicitlyDisabled(
                "workspace_write_file",
                mapOf("workspace_write_file" to true),
            ),
        )
        assertFalse(
            isWorkspaceToolApprovalExplicitlyDisabled("workspace_write_file", emptyMap()),
        )
    }
}
