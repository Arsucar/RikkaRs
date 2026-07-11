package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.ShellChangedFilesMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.rikkahub.data.ai.subagent.SubagentTranscriptStep
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageEditedFilesTest {
    @Test
    fun extractsTopLevelWriteAndShellPathsWithStableDeduplication() {
        val parts = listOf(
            executedTool("workspace_write_file", """{"path":"/workspace/a.txt"}"""),
            executedTool(
                "workspace_shell",
                """{"command":"generate"}""",
                ShellChangedFilesMetadata(listOf("/workspace/a.txt", "/workspace/b.txt")).toMetadata(),
            ),
        )

        assertEquals(
            listOf("/workspace/a.txt", "/workspace/b.txt"),
            extractEditedFilePaths(parts),
        )
    }

    @Test
    fun extractsSubagentWriteAndShellPathsAndIgnoresUnexecutedSteps() {
        val transcript = listOf(
            SubagentTranscriptStep.ToolCall(
                toolName = "workspace_edit_file",
                input = """{"path":"/workspace/a.txt"}""",
                output = "ok",
            ),
            SubagentTranscriptStep.ToolCall(
                toolName = "workspace_shell",
                input = "{}",
                output = "truncated",
                changedFiles = listOf("/workspace/b.txt"),
            ),
            SubagentTranscriptStep.ToolCall(
                toolName = "workspace_shell",
                input = "{}",
                output = "",
                executed = false,
                changedFiles = listOf("/workspace/pending.txt"),
            ),
        )
        val transcriptJson = JsonInstant.encodeToJsonElement(
            ListSerializer(SubagentTranscriptStep.serializer()),
            transcript,
        )
        val spawnOutputMetadata = buildJsonObject { put("subagent_transcript", transcriptJson) }
        val parts = listOf(
            UIMessagePart.Tool(
                toolCallId = "spawn",
                toolName = "spawn_subagent",
                input = "{}",
                output = listOf(UIMessagePart.Text("done", metadata = spawnOutputMetadata)),
            ),
        )

        assertEquals(
            listOf("/workspace/a.txt", "/workspace/b.txt"),
            extractEditedFilePaths(parts),
        )
    }

    @Test
    fun ignoresPendingTopLevelToolsAndLegacyShellWithoutMetadata() {
        val parts = listOf(
            UIMessagePart.Tool(
                toolCallId = "pending",
                toolName = "workspace_write_file",
                input = """{"path":"/workspace/pending.txt"}""",
            ),
            executedTool("workspace_shell", """{"command":"old"}"""),
        )

        assertEquals(emptyList<String>(), extractEditedFilePaths(parts))
    }

    @Test
    fun filtersInvalidShellPathsBeforeDeduplication() {
        val parts = listOf(
            executedTool(
                "workspace_shell",
                """{"command":"generate"}""",
                ShellChangedFilesMetadata(
                    listOf(
                        "/workspace/b.txt",
                        "/tmp/out.txt",
                        "/workspace/../escape.txt",
                        "/workspace/b.txt",
                    ),
                ).toMetadata(),
            ),
        )

        assertEquals(listOf("/workspace/b.txt"), extractEditedFilePaths(parts))
    }

    private fun executedTool(
        name: String,
        input: String,
        metadata: kotlinx.serialization.json.JsonObject? = null,
    ) = UIMessagePart.Tool(
        toolCallId = name,
        toolName = name,
        input = input,
        output = listOf(UIMessagePart.Text("{}", metadata = metadata)),
    )
}
