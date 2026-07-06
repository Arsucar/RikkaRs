package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceShellPresentationTest {
    @Test
    fun commandInfoDetectsContinuationRead() {
        val info = workspaceShellCommandInfo(
            arguments = Json.parseToJsonElement(
                """{"command":"cat /tool_outputs/call_123.txt","cwd":"/workspace"}"""
            )
        )

        assertEquals("cat /tool_outputs/call_123.txt", info.command)
        assertEquals("/workspace", info.cwd)
        assertEquals("/tool_outputs/call_123.txt", info.continuationPath)
        assertTrue(info.isContinuationRead)
    }

    @Test
    fun transcriptInputKeepsShellCommandAsValidJson() {
        val compact = workspaceShellTranscriptInput(
            rawInput = """{"command":"grep \"needle\" /tool_outputs/call_123.txt","cwd":"/workspace","unused":"x"}""",
            maxCommandChars = 200,
        )

        val info = workspaceShellCommandInfo(Json.parseToJsonElement(compact))
        assertEquals("""grep "needle" /tool_outputs/call_123.txt""", info.command)
        assertEquals("/workspace", info.cwd)
        assertEquals("/tool_outputs/call_123.txt", info.continuationPath)
    }

    @Test
    fun rawPartialInputCanStillRecoverCommand() {
        val info = workspaceShellCommandInfoFromRawInput("""{"command":"rg \"TODO\" app/src""")

        assertEquals("""rg "TODO" app/src""", info.command)
    }
}
