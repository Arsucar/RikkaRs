package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

/**
 * 预设条目 (见 issue #182)
 *
 * 将「预设 = ModeInjection ID 容器」升级为「可编辑、可开关、可排序的提示词条目」。
 * 三个变体：
 * - [Custom]: 自定义内容条目，内容内嵌于预设，与全局 ModeInjection 脱钩（快照语义）。
 * - [Builtin]: 引用系统内置提示词（回复草稿/建议回复/记忆表向导/工作区向导等），
 *   可覆盖模板内容与位置；当前注册项均为 config-only，不进入通用对话注入路径。
 * - [Reference]: 链接全局 [PromptInjection.ModeInjection]，随全局条目同步。
 *
 * 公共字段：
 * - [order]: 组内排序键（同 position 组内按此升序拼接），取代 priority 作为预设内排序。
 * - [position]: 注入位置，沿用 [InjectionPosition] 5 值。
 * - [injectDepth]: 当 position 为 AT_DEPTH 时使用。
 * - [role]: 注入角色（USER / ASSISTANT），system 类位置拼接时忽略。
 */
@Serializable
sealed class PresetEntry {
    abstract val id: Uuid
    abstract val enabled: Boolean
    abstract val order: Int
    abstract val position: InjectionPosition
    abstract val injectDepth: Int
    abstract val role: MessageRole

    /**
     * 自定义条目 - 内容内嵌，脱钩全局
     *
     * @param legacyPriority 迁移快照专用：旧 [PromptInjection.ModeInjection.priority] 原值。
     *   非 null 时（由 [migratedWithEntries] 写入）注入解析用它参与全局混排排序，防止迁移条目与
     *   直连注入/lorebook 混排时排序翻转；用户新建条目为 null，回退到 `-order`（见 #182）。
     */
    @Serializable
    @SerialName("custom")
    data class Custom(
        override val id: Uuid = Uuid.random(),
        override val enabled: Boolean = true,
        override val order: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val name: String = "",
        val content: String = "",
        val legacyPriority: Int? = null,
    ) : PresetEntry()

    /**
     * 内置提示词条目 - 引用 BuiltinPromptRegistry 中注册的模板
     *
     * @param builtinKey 内置模板 key（见 BuiltinPromptRegistry）
     * @param overrideContent 非 null 时覆盖默认模板内容
     * @param overridePosition 非 null 时覆盖注入位置（否则使用当前 [position]）
     */
    @Serializable
    @SerialName("builtin")
    data class Builtin(
        override val id: Uuid = Uuid.random(),
        override val enabled: Boolean = true,
        override val order: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val builtinKey: String = "",
        val overrideContent: String? = null,
        val overridePosition: InjectionPosition? = null,
    ) : PresetEntry()

    /**
     * 全局引用条目 - 链接全局 ModeInjection，随全局条目同步
     *
     * @param modeInjectionId 引用的全局 [PromptInjection.ModeInjection] ID；被删则跳过（UI 标无效）
     */
    @Serializable
    @SerialName("reference")
    data class Reference(
        override val id: Uuid = Uuid.random(),
        override val enabled: Boolean = true,
        override val order: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val modeInjectionId: Uuid = Uuid.random(),
    ) : PresetEntry()
}

/**
 * 该条目在指定 [InjectionPosition] 下的有效位置。
 * Builtin 的 [PresetEntry.Builtin.overridePosition] 优先。
 */
fun PresetEntry.effectivePosition(): InjectionPosition = when (this) {
    is PresetEntry.Builtin -> overridePosition ?: position
    else -> position
}

/** 与 PresetDetailPage 的 Builtin / Custom / Reference 分区保持一致的稳定展示顺序。 */
fun Iterable<PresetEntry>.inPresetDisplayOrder(): List<PresetEntry> = sortedWith(
    compareBy<PresetEntry>({ entry ->
        when (entry) {
            is PresetEntry.Builtin -> 0
            is PresetEntry.Custom -> 1
            is PresetEntry.Reference -> 2
        }
    }, PresetEntry::order)
)
