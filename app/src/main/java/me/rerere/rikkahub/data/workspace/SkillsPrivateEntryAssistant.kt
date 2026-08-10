package me.rerere.rikkahub.data.workspace

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

/**
 * /skills_private 浏览器入口助手解析结果 (issue #247).
 *
 * - 恰好 1 个绑定 workspace 的助手 → 直接使用
 * - 0 个 → 回退当前助手, [isCurrentAssistantFallback] = true
 * - 多个 → 需要 UI 选择; [selectedAssistant] 为默认展示 (优先当前助手若在绑定集中)
 */
data class SkillsPrivateEntryAssistant(
    val boundAssistants: List<Assistant>,
    val selectedAssistant: Assistant,
    val isCurrentAssistantFallback: Boolean,
    val requiresSelection: Boolean,
)

/**
 * 从 Settings 解析 workspace 的 /skills_private 入口助手.
 * 不缓存绑定快照; 调用方每次 refresh 重新解析.
 */
fun resolveSkillsPrivateEntryAssistant(
    settings: Settings,
    workspaceId: String,
    selectedAssistantId: Uuid? = null,
): SkillsPrivateEntryAssistant {
    val bound = settings.assistants.filter { assistant ->
        !assistant.isArchived && assistant.workspaceId?.toString() == workspaceId
    }
    val current = settings.getCurrentAssistant()
    return when {
        bound.size == 1 -> SkillsPrivateEntryAssistant(
            boundAssistants = bound,
            selectedAssistant = bound.first(),
            isCurrentAssistantFallback = false,
            requiresSelection = false,
        )
        bound.isEmpty() -> SkillsPrivateEntryAssistant(
            boundAssistants = emptyList(),
            selectedAssistant = current,
            isCurrentAssistantFallback = true,
            requiresSelection = false,
        )
        else -> {
            val selected = selectedAssistantId
                ?.let { id -> bound.find { it.id == id } }
                ?: bound.find { it.id == current.id }
                ?: bound.first()
            SkillsPrivateEntryAssistant(
                boundAssistants = bound,
                selectedAssistant = selected,
                isCurrentAssistantFallback = false,
                requiresSelection = true,
            )
        }
    }
}
