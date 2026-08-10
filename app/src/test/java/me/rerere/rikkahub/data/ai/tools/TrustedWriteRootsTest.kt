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
}
