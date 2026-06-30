package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

const val FINISH_WORK_TOOL_NAME = "finish_work"

fun createFinishWorkTool(): Tool = Tool(
    name = FINISH_WORK_TOOL_NAME,
    description = """
        Signal that your assigned work is complete and stop further tool use.
        Call this only after you have finished the task and written a concise summary
        in your assistant message (before or alongside this call).
        No parameters.
    """.trimIndent().replace("\n", " "),
    parameters = { null },
    needsApproval = { false },
    systemPrompt = { _, _ ->
        """
        When you have fully completed the task, call the `finish_work` tool to end your
        tool loop. Your final assistant text (summary, findings, or change report) should
        already be present in the same turn; do not rely on the tool output as the summary.
        """.trimIndent()
    },
    execute = {
        listOf(UIMessagePart.Text("Task completed."))
    },
)