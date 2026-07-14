package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.ShellChangedFilesMetadata
import me.rerere.ai.ui.metadataAs
import me.rerere.workspace.MAX_WORKSPACE_CHANGED_FILES
import me.rerere.workspace.WorkspaceCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolsTest {
    @Test
    fun imagePathDetectionMatchesFormatsSupportedByFileEncoder() {
        listOf("png", "jpg", "jpeg", "gif", "webp").forEach { extension ->
            assertTrue("/workspace/image.$extension".isImagePath())
            assertTrue("/workspace/image.${extension.uppercase()}".isImagePath())
        }
        listOf("bmp", "svg", "txt", "").forEach { extension ->
            assertFalse("/workspace/image.$extension".isImagePath())
        }
    }

    @Test
    fun shellResultKeepsChangedFilesOnlyInBoundedTypedMetadata() {
        val validPaths = (MAX_WORKSPACE_CHANGED_FILES downTo 0).map { index ->
            "/workspace/file-${index.toString().padStart(3, '0')}.txt"
        }
        val part = workspaceShellResultPart(
            WorkspaceCommandResult(
                exitCode = 0,
                stdout = "out",
                stderr = "warning",
                truncated = true,
                changedFiles = validPaths + listOf("/tmp/out.txt", "/workspace/../escape.txt"),
            ),
        )

        val visibleOutput = Json.parseToJsonElement(part.text).jsonObject
        assertEquals("0", visibleOutput.getValue("exitCode").jsonPrimitive.content)
        assertEquals("out", visibleOutput.getValue("stdout").jsonPrimitive.content)
        assertEquals("warning", visibleOutput.getValue("stderr").jsonPrimitive.content)
        assertFalse(visibleOutput.containsKey("changedFiles"))

        val changedFiles = part.metadataAs<ShellChangedFilesMetadata>()?.changedFiles.orEmpty()
        assertEquals(MAX_WORKSPACE_CHANGED_FILES, changedFiles.size)
        assertEquals("/workspace/file-000.txt", changedFiles.first())
        assertEquals("/workspace/file-099.txt", changedFiles.last())
    }
}
