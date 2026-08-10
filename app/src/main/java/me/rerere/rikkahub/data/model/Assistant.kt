package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.utils.SimpleCache
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val name: String = "",
    val isArchived: Boolean = false,
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    // 上下文消息条数上限, 超出后阶梯式截断; 0 表示不限制
    val contextMessageLimit: Int = 0,
    // #59 fork: 自动压缩上下文（与 contextMessageLimit 阶梯截断并存）
    val autoCompressEnabled: Boolean = false,
    val autoCompressThresholdTokens: Int = 8000,
    val autoCompressKeepRecentMessages: Int = 32,
    val streamOutput: Boolean = true,
    val enableWebSearch: Boolean = false, // 网络搜索开关(每个助手独立)
    val enableMemory: Boolean = false,
    val useGlobalMemory: Boolean = false, // 使用全局共享记忆而非助手隔离记忆
    val enableMemoryTable: Boolean = false,
    /** [SemanticMemory Plugin] per-assistant semantic memory gate (independent of enableMemory). */
    val enableSemanticMemory: Boolean = false,
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val workspaceId: Uuid? = null,
    val defaultWorkspaceCwd: String? = null, // 助手级默认工作区 CWD，新建会话继承（DataStore JSON，旧数据反序列化为 null）
    val background: String? = null, // 聊天页背景图地址(本地文件 URI 或网络 URL), 为 null 时无背景
    val backgroundOpacity: Float = 1.0f, // 背景图不透明度(0~1)
    val useGradientBackground: Boolean = false, // 开启后聊天页使用动态渐变背景
    val presetIds: Set<Uuid> = emptySet(),             // 关联的预设 ID (见 issue #65)
    val lorebookIds: Set<Uuid> = emptySet(),            // 关联的 Lorebook ID
    val enabledSkills: Set<String> = emptySet(),        // 启用的 skill 名称列表
    val enableTimeReminder: Boolean = false,            // 时间间隔提醒注入
    val allowConversationSystemPrompt: Boolean = false, // 允许对话单独重写 system prompt
    val enableSubagents: Boolean = false,
    val subagentMaxDepth: Int = 2,
    val subagentMaxConcurrent: Int = 3,
    val parallelToolExecution: Boolean = false,
    val subagentDelegateOnly: Boolean = false,
    val subagentProfiles: List<SubagentProfile> = emptyList(),
    val disabledBuiltinSubagents: Set<String> = emptySet(),
    val disabledGlobalSubagents: Set<String> = emptySet(),
    val stepsCountdownThreshold: Int? = null,
    val hooks: List<ConversationHook> = emptyList(),
    /** Per-tool policy keyed by stable ToolCapabilityCatalog ids. Missing entries inherit defaults. */
    val toolPermissions: Map<String, ToolPermission> = emptyMap(),
    /**
     * #217/#216: conversation variable system (macros + MVU). Default off for zero side effects.
     * Legacy bridge for [FEATURE_VARIABLE_SYSTEM]; consumers must use [isVariableSystemEnabled].
     */
    val enableVariableSystem: Boolean = false,
    /** #215: assistant-scoped experimental feature overrides (featureId → enabled). */
    val experimentalFeatureOverrides: Map<String, Boolean> = emptyMap(),
)

/** Stable feature gate for variable_system. Map override wins over legacy [enableVariableSystem]. */
fun Assistant.isVariableSystemEnabled(): Boolean {
    experimentalFeatureOverrides[me.rerere.rikkahub.data.experimental.FEATURE_VARIABLE_SYSTEM]
        ?.let { return it }
    return enableVariableSystem
}

@Serializable
enum class ToolPermission { INHERIT, ALLOW, ASK, DENY }

@Serializable
data class QuickMessage(
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val content: String = "",
)

@Serializable
data class AssistantMemory(
    val id: Int,
    val content: String = "",
    val scope: MemoryScope = MemoryScope.ASSISTANT,
)

@Serializable
enum class MemoryScope {
    ASSISTANT,
    GLOBAL;

    companion object {
        fun fromStorage(value: String?): MemoryScope {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ASSISTANT
        }
    }
}

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}

@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // 正则表达式
    val replaceString: String = "", // 替换字符串
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // 是否仅在视觉上影响
)

// 流式输出时每个chunk都会调用replaceRegexes，正则必须缓存编译结果，
// 否则长回复期间会重复编译上万次；编译失败也缓存，避免反复构造异常
private val regexCache = SimpleCache.builder<String, Result<Regex>>()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build()

private fun compileRegexCached(pattern: String): Regex? {
    regexCache.getIfPresent(pattern)?.let { return it.getOrNull() }
    val result = runCatching { Regex(pattern) }.onFailure { it.printStackTrace() }
    regexCache.put(pattern, result)
    return result.getOrNull()
}

fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            val compiled = compileRegexCached(regex.findRegex) ?: return@fold acc
            try {
                acc.replace(
                    regex = compiled,
                    replacement = regex.replaceString,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 替换字符串可能引用不存在的分组，失败时返回原字符串
                acc
            }
        } else {
            acc
        }
    }
}

/**
 * 注入位置
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system_prompt")
    BEFORE_SYSTEM_PROMPT,   // 系统提示词之前

    @SerialName("after_system_prompt")
    AFTER_SYSTEM_PROMPT,    // 系统提示词之后（最常用）

    @SerialName("top_of_chat")
    TOP_OF_CHAT,            // 对话最开头（第一条用户消息之前）

    @SerialName("bottom_of_chat")
    BOTTOM_OF_CHAT,         // 最新消息之前（当前用户输入之前）

    @SerialName("at_depth")
    AT_DEPTH,               // 在指定深度位置插入（从最新消息往前数）
}

/**
 * 提示词注入
 *
 * - [ResolvedInjection]: 运行时解析结果（预设 Custom/Builtin 展开后的注入载体；不持久化）
 * - [RegexInjection]: 基于正则匹配的注入（Lorebook）
 *
 * #259: 独立 ModeInjection 全局表与直连绑定已删除；预设条目是唯一产品路径。
 */
@Serializable
sealed class PromptInjection {
    abstract val id: Uuid
    abstract val name: String
    abstract val enabled: Boolean
    abstract val priority: Int
    abstract val position: InjectionPosition
    abstract val content: String
    abstract val injectDepth: Int  // 当 position 为 AT_DEPTH 时使用，表示从最新消息往前数的位置
    abstract val role: MessageRole  // 注入角色：USER 或 ASSISTANT

    /**
     * 运行时解析注入（由预设条目展开生成，不写入 DataStore）。
     *
     * 保留 @SerialName("mode") 以便旧备份中的 mode 条目在迁移期可被识别后丢弃/快照。
     */
    @Serializable
    @SerialName("mode")
    data class ResolvedInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
    ) : PromptInjection()

    /**
     * 正则注入 - 基于内容匹配触发（世界书）
     */
    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val keywords: List<String> = emptyList(),  // 触发关键词
        val useRegex: Boolean = false,             // 是否使用正则匹配
        val caseSensitive: Boolean = false,        // 大小写敏感
        val scanDepth: Int = 4,                    // 扫描最近N条消息
        val constantActive: Boolean = false,       // 常驻激活（无需匹配）
    ) : PromptInjection()
}

/**
 * 旧全局 ModeInjection 迁移用类型（#259）。
 * 仅用于从 DataStore `mode_injections` key / 备份 JSON 解码后做 Reference→Custom 快照。
 */
@Serializable
@SerialName("mode")
data class LegacyModeInjection(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    val content: String = "",
    val injectDepth: Int = 4,
    val role: MessageRole = MessageRole.USER,
)

/**
 * Lorebook - 组织管理多个 RegexInjection
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
)

/**
 * Preset - 可编辑提示词条目容器 (见 issue #65 / #182 / #259)
 *
 * 启用预设 = 展开 [entries] 注入。旧字段 [modeInjectionIds]/[disabledEntryIds] 仅迁移期使用。
 */
@Serializable
data class Preset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val modeInjectionIds: Set<Uuid> = emptySet(),   // 旧字段：引用全局 mode_injections（迁移后清空）
    val disabledEntryIds: Set<Uuid> = emptySet(),   // 旧字段：预设内禁用的全局 id（迁移后清空）
    val entries: List<PresetEntry> = emptyList(),   // 可编辑/开关/排序的预设条目 (见 issue #182)
    val entriesVersion: Int = 0,                    // 0=旧 ID 模型；1=entries 模型（空列表也有效）
    /**
     * Reply-draft context assembly options (issue #196).
     * `null` = use [DEFAULT_DRAFT_CONTEXT] at runtime; missing field deserializes as null (compat).
     */
    val draftContext: DraftContextConfig? = null,
) {
    /** 该预设启用时实际生效的旧全局注入 ID 集合（仅当 [entries] 为空时使用） */
    fun effectiveInjectionIds(): Set<Uuid> = modeInjectionIds - disabledEntryIds

    /** 是否已迁移到新 [entries] 模型 */
    fun hasEntries(): Boolean = entriesVersion >= PRESET_ENTRIES_VERSION || entries.isNotEmpty()
}

const val PRESET_ENTRIES_VERSION = 1

/**
 * 懒迁移：把旧 [Preset.modeInjectionIds] / [Preset.disabledEntryIds] 展开为 [PresetEntry.Custom] 快照，
 * 并将任何遗留 Reference 形态条目（由迁移 JSON 预解析）转为内容等效 Custom。
 *
 * - 幂等：已无旧字段且无 Reference 的预设原样返回。
 * - 快照语义：内容从全局 [modeInjections] 复制，脱钩全局后续修改。
 * - `enabled = (id !in disabledEntryIds) && injection.enabled`（继承全局 enabled）。
 * - priority DESC 映射为稳定 order（0..n）；同时把原 priority 存入 legacyPriority。
 * - 命中不到全局条目的 ID / 无效 Reference **丢弃**（#259 固定策略）并可由调用方 log。
 */
fun Preset.migratedWithEntries(
    modeInjections: List<LegacyModeInjection>,
): Preset {
    // 先处理旧 ID 容器 → entries
    val base = when {
        entriesVersion >= PRESET_ENTRIES_VERSION || entries.isNotEmpty() -> {
            if (modeInjectionIds.isEmpty() && disabledEntryIds.isEmpty() &&
                entriesVersion >= PRESET_ENTRIES_VERSION
            ) {
                this
            } else {
                copy(
                    modeInjectionIds = emptySet(),
                    disabledEntryIds = emptySet(),
                    entriesVersion = PRESET_ENTRIES_VERSION,
                )
            }
        }
        else -> {
            val migrated = modeInjections
                .filter { it.id in modeInjectionIds }
                .sortedByDescending { it.priority }
                .mapIndexed { index, injection ->
                    PresetEntry.Custom(
                        id = injection.id,
                        enabled = (injection.id !in disabledEntryIds) && injection.enabled,
                        order = index,
                        position = injection.position,
                        injectDepth = injection.injectDepth,
                        role = injection.role,
                        name = injection.name,
                        content = injection.content,
                        legacyPriority = injection.priority,
                    )
                }
            copy(
                modeInjectionIds = emptySet(),
                disabledEntryIds = emptySet(),
                entries = migrated,
                entriesVersion = PRESET_ENTRIES_VERSION,
            )
        }
    }
    // entries 现仅 Builtin|Custom。确保 version / 旧字段清洁。
    return if (base.entriesVersion >= PRESET_ENTRIES_VERSION &&
        base.modeInjectionIds.isEmpty() &&
        base.disabledEntryIds.isEmpty()
    ) {
        base
    } else {
        base.copy(
            modeInjectionIds = emptySet(),
            disabledEntryIds = emptySet(),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
    }
}

/**
 * #259: 将旧 Reference 条目（解码为中间结构）快照为 Custom。
 * 目标缺失则丢弃该 entry。
 */
fun snapshotReferenceAsCustom(
    entryId: Uuid,
    enabled: Boolean,
    order: Int,
    @Suppress("UNUSED_PARAMETER") position: InjectionPosition,
    @Suppress("UNUSED_PARAMETER") injectDepth: Int,
    @Suppress("UNUSED_PARAMETER") role: MessageRole,
    modeInjectionId: Uuid,
    modeInjections: List<LegacyModeInjection>,
): PresetEntry.Custom? {
    val target = modeInjections.firstOrNull { it.id == modeInjectionId } ?: return null
    // Prefer target's content fields for content-equivalent snapshot (#259 AC2).
    return PresetEntry.Custom(
        id = entryId,
        enabled = enabled,
        order = order,
        position = target.position,
        injectDepth = target.injectDepth,
        role = target.role,
        name = target.name,
        content = target.content,
        legacyPriority = target.priority,
    )
}

/**
 * 检查 RegexInjection 是否被触发
 *
 * @param context 要扫描的上下文文本
 * @return 是否触发
 */
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false

    return keywords.any { keyword ->
        if (useRegex) {
            try {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(keyword, options).containsMatchIn(context)
            } catch (e: Exception) {
                false
            }
        } else {
            if (caseSensitive) {
                context.contains(keyword)
            } else {
                context.contains(keyword, ignoreCase = true)
            }
        }
    }
}

/**
 * 从消息列表中提取用于匹配的上下文文本
 *
 * @param messages 消息列表
 * @param scanDepth 扫描深度（最近N条消息）
 * @return 拼接的文本内容
 */
fun extractContextForMatching(
    messages: List<UIMessage>,
    scanDepth: Int
): String {
    return messages
        .takeLast(scanDepth)
        .joinToString("\n") { it.toText() }
}

/**
 * 获取所有被触发的注入，按优先级排序
 *
 * @param injections 所有注入规则
 * @param context 上下文文本
 * @return 被触发的注入列表，按优先级降序排列
 */
fun getTriggeredInjections(
    injections: List<PromptInjection.RegexInjection>,
    context: String
): List<PromptInjection.RegexInjection> {
    return injections
        .filter { it.isTriggered(context) }
        .sortedByDescending { it.priority }
}
