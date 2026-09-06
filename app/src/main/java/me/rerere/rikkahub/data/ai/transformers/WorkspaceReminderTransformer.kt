package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.ai.prompts.buildWorkspaceGuidePrompt
import me.rerere.rikkahub.data.ai.prompts.resolveBuiltinOverride
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.resolveWorkspaceToolCapability

/**
 * Workspace 系统提示注入转换器
 *
 * 当助手绑定了一个 shell 已就绪的 workspace 时, 在系统提示词中追加一段引导,
 * 让模型了解 workspace 环境与 workspace_* 工具的使用方式。
 */
class WorkspaceReminderTransformer(
    private val workspace: WorkspaceEntity?,
) : InputMessageTransformer {
    override val previewPolicy: PreviewTransformPolicy = PreviewTransformPolicy.SideEffectFree
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val workspace = workspace ?: return messages
        // 与 ChatService.createWorkspaceToolsIfReady 保持一致: 仅在 shell 就绪时注入
        if (!resolveWorkspaceToolCapability(workspace.id, listOf(workspace)).available) return messages

        // #182: 预设内启用的 workspace_guide 覆盖优先；宏由本处用运行时 workspace 数据替换。
        // 无有效覆盖时回退默认拼装。
        val override = resolveBuiltinOverride(
            ctx.assistant,
            ctx.settings.presets,
            BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE,
        )
        val prompt = if (override != null) {
            BuiltinPromptRegistry.resolveContent(
                def = requireNotNull(BuiltinPromptRegistry[BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE]),
                override = override,
                vars = mapOf(
                    "workspace_name" to workspace.name,
                    "cwd" to ctx.workspaceCwd.orEmpty(),
                ),
            )
        } else {
            buildWorkspaceGuidePrompt(workspace.name, ctx.workspaceCwd)
        }

        // 追加到第一条 system 消息; 若不存在则插入一条
        val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
        return if (systemIndex >= 0) {
            messages.toMutableList().apply {
                this[systemIndex] = this[systemIndex]
                    .appendText("\n\n$prompt")
                    .copy(isSynthetic = true)
            }
        } else {
            listOf(UIMessage.system(prompt).copy(isSynthetic = true)) + messages
        }
    }
}

private fun UIMessage.appendText(extra: String): UIMessage {
    val updatedParts = parts.toMutableList()
    val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex >= 0) {
        val text = updatedParts[firstTextIndex] as UIMessagePart.Text
        updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
    } else {
        updatedParts.add(UIMessagePart.Text(extra))
    }
    return copy(parts = updatedParts)
}
