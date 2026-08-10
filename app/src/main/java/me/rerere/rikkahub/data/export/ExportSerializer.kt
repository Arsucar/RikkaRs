package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
data class ExportData(
    val version: Int = 1,
    val type: String,
    val data: JsonElement
)

interface ExportSerializer<T> {
    val type: String

    fun export(data: T): ExportData
    fun import(context: Context, uri: Uri): Result<T>

    // 获取导出文件名
    fun getExportFileName(data: T): String = "${type}.json"

    // 便捷方法：直接导出为 JSON 字符串
    fun exportToJson(data: T, json: Json = DefaultJson): String {
        return json.encodeToString(ExportData.serializer(), export(data))
    }

    // 读取 URI 内容的便捷方法
    fun readUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Failed to read file")
    }

    fun getUriFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else null
            } else null
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}


object PresetSerializer : ExportSerializer<Preset> {
    override val type = "preset"

    /** ST `prompt_order` 中的全局默认分组 id（100000 为 dummy 组） */
    private const val ST_DEFAULT_CHARACTER_ID = 100001

    override fun getExportFileName(data: Preset): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: Preset): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<Preset> {
        return runCatching {
            val json = readUri(context, uri)
            // 首先尝试解析为自己的格式
            tryImportNative(json)
                // 然后尝试解析为 SillyTavern 提示词预设格式
                ?: tryImportSillyTavernPreset(json, getUriFileName(context, uri)?.removeSuffix(".json"))
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    internal fun tryImportNative(json: String): Preset? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            val preset = ExportSerializer.DefaultJson.decodeFromJsonElement<Preset>(exportData.data)
            // 条目 id 也要重随机，否则同一份预设导入两次会产生重复条目 id
            preset.copy(
                id = Uuid.random(),
                entries = preset.entries.map { entry ->
                    when (entry) {
                        is PresetEntry.Custom -> entry.copy(id = Uuid.random())
                        is PresetEntry.Builtin -> entry.copy(id = Uuid.random())
                    }
                }
            )
        }.getOrNull()
    }

    /**
     * 解析 SillyTavern（酒馆）「提示词预设」JSON（见 issue #188）。
     *
     * Context-free 纯函数，便于 JVM 单测。识别门槛为「含非空 prompts[]」，
     * 不满足或最终没有可导入条目时返回 null，交由调用方抛 Unsupported format。
     */
    internal fun tryImportSillyTavernPreset(json: String, fileName: String?): Preset? {
        return runCatching {
            val stPreset = ExportSerializer.DefaultJson.decodeFromString(
                SillyTavernPreset.serializer(),
                json
            )
            val rawPrompts = stPreset.prompts.orEmpty()
            if (rawPrompts.isEmpty()) return null

            // identifier 重复保留首个；identifier 缺失的条目用下标合成 key（无法被 prompt_order 引用，只走原序回退）
            val promptsByKey = linkedMapOf<String, SillyTavernPrompt>()
            rawPrompts.forEachIndexed { index, prompt ->
                val key = prompt.identifier?.takeIf { it.isNotBlank() } ?: "#$index"
                if (!promptsByKey.containsKey(key)) promptsByKey[key] = prompt
            }

            // 顺序与启用优先由 prompt_order 决定；ST 可能带多个 character_id 分组，
            // 其中 100001 是全局默认组、100000 是 dummy 组，故优先 100001，再回退第一个非空组。
            // 缺失 prompt_order 时回退 prompts[] 原序，全部视为启用。
            val orderGroups = stPreset.promptOrder.orEmpty().filter { !it.order.isNullOrEmpty() }
            val orderList = (orderGroups.firstOrNull { it.characterId == ST_DEFAULT_CHARACTER_ID }
                ?: orderGroups.firstOrNull())
                ?.order
            val orderedKeys: List<Pair<String, Boolean>> = if (orderList != null) {
                val seen = mutableSetOf<String>()
                orderList.mapNotNull { orderEntry ->
                    val key = orderEntry.identifier?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    // 引用了不存在的 identifier 直接跳过
                    if (!promptsByKey.containsKey(key) || !seen.add(key)) return@mapNotNull null
                    key to (orderEntry.enabled ?: true)
                }
            } else {
                promptsByKey.keys.map { it to true }
            }

            var skippedMarkers = 0
            val entries = mutableListOf<PresetEntry>()
            orderedKeys.forEach { (key, orderEnabled) ->
                val prompt = promptsByKey[key] ?: return@forEach
                // marker = true 的系统占位条目（chatHistory / worldInfoBefore / charDescription 等）无实际内容，跳过
                if (prompt.marker == true) {
                    skippedMarkers++
                    return@forEach
                }
                val content = prompt.content.orEmpty()
                if (content.isBlank()) return@forEach
                val stRole = mapSillyTavernPromptRole(prompt.role)
                entries += PresetEntry.Custom(
                    id = Uuid.random(),
                    // prompts[].enabled 与 prompt_order[].order[].enabled 取 AND，任一 false 即禁用
                    enabled = (prompt.enabled ?: true) && orderEnabled,
                    order = entries.size,
                    position = mapSillyTavernPresetPosition(prompt.injectionPosition, stRole),
                    injectDepth = prompt.injectionDepth ?: 4,
                    // ST 的 system 角色只用于决定注入位置，不能落进 PresetEntry.role（见下方归一说明）
                    role = normalizeSillyTavernEntryRole(stRole),
                    // name 空则回退 identifier；identifier 也缺失时留空（不暴露内部合成 key）
                    name = prompt.name?.takeIf { it.isNotBlank() }
                        ?: prompt.identifier?.takeIf { it.isNotBlank() }
                        ?: "",
                    content = content,
                )
            }
            if (entries.isEmpty()) return null

            Preset(
                id = Uuid.random(),
                name = fileName?.takeIf { it.isNotBlank() } ?: LocalDateTime.now().toLocalString(),
                description = buildSillyTavernPresetDescription(stPreset, skippedMarkers),
                entries = entries,
                entriesVersion = PRESET_ENTRIES_VERSION,
            )
        }.getOrNull()
    }

    /**
     * ST 提示词预设的 injection_position 映射。
     *
     * 注意：**不能**复用 [LorebookSerializer] 的世界书 position 映射。世界书 position 是
     * 0=before system / 1=after system / 2,3=top of chat / 4=@depth；而预设的 injection_position
     * 只有 0=relative（按 prompt_order 就地拼接）与 1=absolute（按深度插入），值 1 的语义完全不同。
     *
     * relative 条目在 ST 里位于 chat history 之前按序拼接：system 归入 system 块；
     * user/assistant 若也塞进 system 块会丢角色语义，用 TOP_OF_CHAT 更接近 ST 行为。
     */
    internal fun mapSillyTavernPresetPosition(
        injectionPosition: Int?,
        role: MessageRole,
    ): InjectionPosition = when {
        injectionPosition == 1 -> InjectionPosition.AT_DEPTH
        role == MessageRole.SYSTEM -> InjectionPosition.AFTER_SYSTEM_PROMPT
        else -> InjectionPosition.TOP_OF_CHAT
    }

    /** ST role 字符串 → [MessageRole]，缺失/非法一律按 SYSTEM 处理 */
    internal fun mapSillyTavernPromptRole(role: String?): MessageRole = when (role?.lowercase()) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    }

    /**
     * [PresetEntry.role] 归一：SYSTEM 折成 USER。
     *
     * SYSTEM 只是 ST 侧用来定位置的中间值（见 [mapSillyTavernPresetPosition]），不能落进条目：
     * - `PresetEntry` 契约只承认 USER / ASSISTANT，PresetDetailPage 的 role 选择器也只有这两项，
     *   SYSTEM 会显示未本地化的裸 "SYSTEM"，且用户一旦打开下拉就无法选回。
     * - 注入期 `createMergedInjectionMessages` 本就把 SYSTEM 降级为 user 消息，但它按 role 分组，
     *   同深度混有 SYSTEM/USER 时会被拆成两条消息并打乱既有顺序（ST 的 @Depth 提示词顺序敏感）。
     *
     * 折成 USER 与实际注入结果一致；AFTER_SYSTEM_PROMPT 位置的条目 role 本就被忽略，不受影响。
     */
    internal fun normalizeSillyTavernEntryRole(role: MessageRole): MessageRole =
        if (role == MessageRole.ASSISTANT) MessageRole.ASSISTANT else MessageRole.USER

    /**
     * ST 顶层格式设置在 RikkaHub 无落点（v1 不映射），导入链路也没有承载警告文案的位置，
     * 因此把「未导入的设置名 + 跳过的 marker 条目数」写进 [Preset.description] 让用户可见。
     */
    private fun buildSillyTavernPresetDescription(
        preset: SillyTavernPreset,
        skippedMarkers: Int,
    ): String {
        val unmapped = buildList {
            if (!preset.impersonationPrompt.isNullOrBlank()) add("impersonation_prompt")
            if (!preset.newChatPrompt.isNullOrBlank()) add("new_chat_prompt")
            if (!preset.newGroupChatPrompt.isNullOrBlank()) add("new_group_chat_prompt")
            if (!preset.newExampleChatPrompt.isNullOrBlank()) add("new_example_chat_prompt")
            if (!preset.continueNudgePrompt.isNullOrBlank()) add("continue_nudge_prompt")
            if (!preset.scenarioFormat.isNullOrBlank()) add("scenario_format")
            if (!preset.personalityFormat.isNullOrBlank()) add("personality_format")
            if (!preset.groupNudgePrompt.isNullOrBlank()) add("group_nudge_prompt")
            if (!preset.wiFormat.isNullOrBlank()) add("wi_format")
        }
        return buildList {
            if (unmapped.isNotEmpty()) {
                add("Imported from SillyTavern. Settings not imported: ${unmapped.joinToString(", ")}")
            }
            if (skippedMarkers > 0) {
                add("Skipped $skippedMarkers SillyTavern marker prompt(s).")
            }
        }.joinToString("\n")
    }
}

object LorebookSerializer : ExportSerializer<Lorebook> {
    override val type = "lorebook"

    override fun getExportFileName(data: Lorebook): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: Lorebook): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<Lorebook> {
        return runCatching {
            val json = readUri(context, uri)
            // 首先尝试解析为自己的格式
            tryImportNative(json)
            // 然后尝试解析为 SillyTavern 格式
                ?: tryImportSillyTavern(json, getUriFileName(context, uri)?.removeSuffix(".json"))
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    private fun tryImportNative(json: String): Lorebook? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<Lorebook>(exportData.data)
                .copy(
                    id = Uuid.random(),
                    entries = ExportSerializer.DefaultJson
                        .decodeFromJsonElement<Lorebook>(exportData.data)
                        .entries.map { it.copy(id = Uuid.random()) }
                )
        }.getOrNull()
    }

    private fun tryImportSillyTavern(json: String, fileName: String?): Lorebook? {
        return runCatching {
            val stLorebook = ExportSerializer.DefaultJson.decodeFromString(
                SillyTavernLorebook.serializer(),
                json
            )
            Lorebook(
                id = Uuid.random(),
                name = fileName ?: LocalDateTime.now().toLocalString(),
                description = "",
                enabled = true,
                entries = stLorebook.entries.values.map { entry ->
                    PromptInjection.RegexInjection(
                        id = Uuid.random(),
                        name = entry.comment.orEmpty().ifEmpty { entry.key.firstOrNull().orEmpty() },
                        enabled = !entry.disable,
                        priority = entry.order,
                        position = mapSillyTavernPosition(entry.position),
                        injectDepth = entry.depth,
                        content = entry.content,
                        keywords = entry.key,
                        useRegex = false, // SillyTavern 格式不支持 useRegex
                        caseSensitive = entry.caseSensitive ?: false,
                        scanDepth = entry.scanDepth ?: 4,
                        constantActive = entry.constant,
                    )
                }
            )
        }.getOrNull()
    }

    private fun mapSillyTavernPosition(position: Int): InjectionPosition {
        return when (position) {
            0 -> InjectionPosition.BEFORE_SYSTEM_PROMPT
            1 -> InjectionPosition.AFTER_SYSTEM_PROMPT
            2 -> InjectionPosition.TOP_OF_CHAT
            3 -> InjectionPosition.TOP_OF_CHAT // After Examples -> 聊天历史开头
            4 -> InjectionPosition.AT_DEPTH    // @Depth 模式
            else -> InjectionPosition.AFTER_SYSTEM_PROMPT
        }
    }
}

@Serializable
private data class SillyTavernLorebook(
    val entries: Map<String, SillyTavernEntry> = emptyMap(),
)

@Serializable
private data class SillyTavernEntry(
    val key: List<String> = emptyList(),
    val content: String = "",
    val comment: String? = null,
    val constant: Boolean = false,
    val position: Int = 0,
    val order: Int = 100,
    val disable: Boolean = false,
    val depth: Int = 4,
    val scanDepth: Int? = null,
    val caseSensitive: Boolean? = null,
)

/**
 * SillyTavern「提示词预设」（Chat Completion Preset）顶层结构。
 *
 * 只声明本次需要的字段：`prompts` 提供条目内容，`promptOrder` 提供顺序与启用状态，
 * 其余字段是 ST 侧的格式化模板/开关，RikkaHub 无落点，仅用于在 description 里提示用户（见 R4）。
 * 依赖 [ExportSerializer.DefaultJson] 的 `ignoreUnknownKeys`，未声明的字段直接忽略。
 */
@Serializable
internal data class SillyTavernPreset(
    // 字段一律可空：ST 导出的 JSON 里这些键可能缺失，也可能是显式 null，
    // 而 DefaultJson 未开启 coerceInputValues，显式 null 落到非空类型会直接抛异常
    val prompts: List<SillyTavernPrompt>? = null,
    @SerialName("prompt_order")
    val promptOrder: List<SillyTavernPromptOrder>? = null,
    // 以下为 v1 不映射的 ST 顶层设置，仅用于生成 description 提示
    @SerialName("impersonation_prompt")
    val impersonationPrompt: String? = null,
    @SerialName("new_chat_prompt")
    val newChatPrompt: String? = null,
    @SerialName("new_group_chat_prompt")
    val newGroupChatPrompt: String? = null,
    @SerialName("new_example_chat_prompt")
    val newExampleChatPrompt: String? = null,
    @SerialName("continue_nudge_prompt")
    val continueNudgePrompt: String? = null,
    @SerialName("scenario_format")
    val scenarioFormat: String? = null,
    @SerialName("personality_format")
    val personalityFormat: String? = null,
    @SerialName("group_nudge_prompt")
    val groupNudgePrompt: String? = null,
    @SerialName("wi_format")
    val wiFormat: String? = null,
)

/**
 * ST 预设条目。
 *
 * - `marker = true` 为系统占位（chatHistory / worldInfoBefore / charDescription 等），无实际内容，导入时跳过。
 * - `systemPrompt` / `forbidOverrides` 保留字段以便未来使用，当前不参与映射。
 */
@Serializable
internal data class SillyTavernPrompt(
    val identifier: String? = null,
    val name: String? = null,
    val role: String? = null,
    val content: String? = null,
    val enabled: Boolean? = null,
    val marker: Boolean? = null,
    @SerialName("system_prompt")
    val systemPrompt: Boolean? = null,
    @SerialName("injection_position")
    val injectionPosition: Int? = null,
    @SerialName("injection_depth")
    val injectionDepth: Int? = null,
    @SerialName("forbid_overrides")
    val forbidOverrides: Boolean? = null,
)

/**
 * ST 的 `prompt_order` 按角色分组，`character_id = 100001` 是全局默认组、100000 是 dummy 组。
 * 导入时优先取 100001 组，再回退第一个非空组（见 [PresetSerializer.tryImportSillyTavernPreset]）。
 */
@Serializable
internal data class SillyTavernPromptOrder(
    @SerialName("character_id")
    val characterId: Int? = null,
    val order: List<SillyTavernPromptOrderEntry>? = null,
)

@Serializable
internal data class SillyTavernPromptOrderEntry(
    val identifier: String? = null,
    val enabled: Boolean? = null,
)
