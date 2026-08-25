package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageBackgroundOption
import me.rerere.ai.ui.ImageOutputFormatOption
import me.rerere.ai.ui.ImageModerationOption
import me.rerere.ai.ui.ImageQualityOption
import me.rerere.ai.ui.ImageSizeOption
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.clash.ClashProxyConfig
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV4Migration
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.LegacyModeInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.migratedWithEntries
import me.rerere.rikkahub.data.model.snapshotReferenceAsCustom
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.ToolPermissionPreset
import me.rerere.rikkahub.data.model.isValidForPersistence
import me.rerere.rikkahub.data.model.WorkspaceFilesStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"
private const val MEMORY_TABLE_BUDGET_UNLIMITED_SENTINEL = -1
const val RECENT_CHAT_MODELS_LIMIT = 8
const val IMAGE_GALLERY_MIN_COLUMNS = 1
const val IMAGE_GALLERY_MAX_COLUMNS = 6
private const val TOOL_PERMISSION_PRESET_JSON_MAX_CHARS = 512_000
private const val TOOL_PERMISSION_PRESET_MAX_COUNT = 128

/** Allowed tool-step intervals for mid-generation checkpoint cache (#220). */
val CHECKPOINT_STEP_INTERVAL_OPTIONS = listOf(4, 8, 16, 32)
const val DEFAULT_CHECKPOINT_STEP_INTERVAL = 8

fun coerceCheckpointStepInterval(value: Int): Int {
    return CHECKPOINT_STEP_INTERVAL_OPTIONS.minByOrNull { kotlin.math.abs(it - value) }
        ?: DEFAULT_CHECKPOINT_STEP_INTERVAL
}

/** Pure trigger math for #220 checkpoints: fire when steps advanced since last checkpoint >= N. */
fun shouldWriteCheckpoint(
    enableCheckpointCache: Boolean,
    stepIndex: Int,
    lastCheckpointStep: Int,
    interval: Int,
): Boolean {
    if (!enableCheckpointCache) return false
    val n = coerceCheckpointStepInterval(interval)
    return (stepIndex - lastCheckpointStep) >= n
}

internal fun decodeToolPermissionPresets(json: String?): List<ToolPermissionPreset> {
    if (json == null || json.length > TOOL_PERMISSION_PRESET_JSON_MAX_CHARS) return emptyList()
    return runCatching { JsonInstant.decodeFromString<List<ToolPermissionPreset>>(json) }
        .getOrDefault(emptyList())
        .take(TOOL_PERMISSION_PRESET_MAX_COUNT)
        .filter { it.isValidForPersistence() }
}
const val DEFAULT_COMPRESS_TARGET_TOKENS = 2000
const val DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES = 32

private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration(),
            PreferenceStoreV4Migration()
        )
    }
)

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    private val appScope = scope

    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val NETWORK_SETTING = stringPreferencesKey("network_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val REQUEST_LOGGING_ENABLED = booleanPreferencesKey("request_logging_enabled")
        // Legacy global web-search key (migration input only; removed by PreferenceStoreV4Migration)
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        // 模型选择
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val RECENT_CHAT_MODELS = stringPreferencesKey("recent_chat_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val FAST_MODEL = stringPreferencesKey("fast_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val ENABLE_SUGGESTION = booleanPreferencesKey("enable_suggestion")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")
        val COMPRESS_TARGET_TOKENS = intPreferencesKey("compress_target_tokens")
        val COMPRESS_KEEP_RECENT_MESSAGES = intPreferencesKey("compress_keep_recent_messages")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")
        val PROVIDER_TAG_ORDER = stringPreferencesKey("provider_tag_order")
        val HIDDEN_PROVIDER_TAGS = stringPreferencesKey("hidden_provider_tags")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")
        val ENABLE_MEMORY_TABLE = booleanPreferencesKey("enable_memory_table")
        val MEMORY_TABLE_MAX_INJECT_DOCUMENTS = intPreferencesKey("memory_table_max_inject_documents")
        val MEMORY_TABLE_MAX_INJECT_TOKENS = intPreferencesKey("memory_table_max_inject_tokens")
        val MEMORY_TABLE_MAX_INJECT_CHARS = intPreferencesKey("memory_table_max_inject_chars")
        val MEMORY_TABLE_AUTO_SYNC_ENABLED = booleanPreferencesKey("memory_table_auto_sync_enabled")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")
        val GLOBAL_SUBAGENT_PROFILES = stringPreferencesKey("global_subagent_profiles")
        val SUBAGENT_BUILTIN_MIGRATED = booleanPreferencesKey("subagent_builtin_migrated")

        // #182: 预设懒迁移为 entries 快照后的一次性持久化标记
        val PRESET_ENTRIES_MIGRATED = booleanPreferencesKey("preset_entries_migrated")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")
        val DEFAULT_TTS_PLAYBACK_SPEED = floatPreferencesKey("default_tts_playback_speed")

        // ASR
        val ASR_PROVIDERS = stringPreferencesKey("asr_providers")
        val SELECTED_ASR_PROVIDER = stringPreferencesKey("selected_asr_provider")

        // Web Server
        val WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
        val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
        val WEB_SERVER_JWT_ENABLED = booleanPreferencesKey("web_server_jwt_enabled")
        val WEB_SERVER_ACCESS_PASSWORD = stringPreferencesKey("web_server_access_password")
        val WEB_SERVER_LOCALHOST_ONLY = booleanPreferencesKey("web_server_localhost_only")

        // 提示词注入
        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
        val PRESETS = stringPreferencesKey("presets")
        val TOOL_PERMISSION_PRESETS = stringPreferencesKey("tool_permission_presets")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")
        val IMAGE_QUICK_MESSAGES = stringPreferencesKey("image_quick_messages")
        val IMAGE_GENERATION_SETTINGS = stringPreferencesKey("image_generation_settings")
        val IMAGE_GALLERY_SETTINGS = stringPreferencesKey("image_gallery_settings")
        val IMAGE_FAVORITE_COLLECTIONS = stringPreferencesKey("image_favorite_collections")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")

        // 赞助提醒
        val SPONSOR_ALERT_DISMISSED_AT = intPreferencesKey("sponsor_alert_dismissed_at")
        val SPONSOR_ALERT_DISABLED = booleanPreferencesKey("sponsor_alert_disabled")

        val WORKSPACE_FILES_STORAGE = stringPreferencesKey("workspace_files_storage")

        // [SemanticMemory Plugin]
        val SEMANTIC_MEMORY_CONFIG = stringPreferencesKey("semantic_memory_config")

        // Clash proxy rotation for 429 retry (#209)
        val CLASH_PROXY_CONFIG = stringPreferencesKey("clash_proxy_config")

        // #219: experimental FGS keep-alive during chat generation (legacy bridge for #215)
        val ENABLE_KEEP_ALIVE_NOTIFICATION = booleanPreferencesKey("enable_keep_alive_notification")

        // #220: experimental mid-generation conversation checkpoint cache (legacy bridge for #215)
        val ENABLE_CHECKPOINT_CACHE = booleanPreferencesKey("enable_checkpoint_cache")
        val CHECKPOINT_STEP_INTERVAL = intPreferencesKey("checkpoint_step_interval")

        // #215: global experimental feature map (featureId → enabled)
        val EXPERIMENTAL_FEATURES = stringPreferencesKey("experimental_features")
    }

    private val dataStore = context.settingsStore

    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val compressionPreferences = preferences.compressionPreferences()
            Settings(
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                recentChatModels = preferences[RECENT_CHAT_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                fastModelId = preferences[FAST_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) },
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                enableSuggestion = preferences[ENABLE_SUGGESTION] != false,
                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) },
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = preferences[COMPRESS_MODEL]?.let { Uuid.parse(it) } ?: DEFAULT_AUTO_MODEL_ID,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                compressTargetTokens = compressionPreferences.targetTokens,
                compressKeepRecentMessages = compressionPreferences.keepRecentMessages,
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                enableMemoryTable = preferences[ENABLE_MEMORY_TABLE] == true,
                memoryTableMaxInjectDocuments = decodeMemoryTableBudget(
                    storedValue = preferences[MEMORY_TABLE_MAX_INJECT_DOCUMENTS],
                    defaultValue = DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
                ),
                memoryTableMaxInjectTokens = decodeMemoryTableBudget(
                    storedValue = preferences[MEMORY_TABLE_MAX_INJECT_TOKENS],
                    defaultValue = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
                ),
                memoryTableMaxInjectChars = decodeMemoryTableBudget(
                    storedValue = preferences[MEMORY_TABLE_MAX_INJECT_CHARS],
                    defaultValue = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
                ),
                memoryTableAutoSyncEnabled = preferences.readMemoryTableAutoSyncEnabled(),
                providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
                providerTagOrder = preferences[PROVIDER_TAG_ORDER]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                hiddenProviderTags = preferences[HIDDEN_PROVIDER_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = preferences[CUSTOM_THEMES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                requestLoggingEnabled = preferences[REQUEST_LOGGING_ENABLED] == true,
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
                networkSetting = JsonInstant.decodeFromString(preferences[NETWORK_SETTING] ?: "{}"),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: listOf(SearchServiceOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                globalSubagentProfiles = preferences[GLOBAL_SUBAGENT_PROFILES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                subagentBuiltinMigrated = preferences[SUBAGENT_BUILTIN_MIGRATED] == true,
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: WebDavConfig(),
                s3Config = preferences[S3_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: S3Config(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                defaultTTSPlaybackSpeed = preferences[DEFAULT_TTS_PLAYBACK_SPEED]?.coerceIn(0.5f, 2.0f) ?: 1.0f,
                asrProviders = preferences[ASR_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let { Uuid.parse(it) },
                // #259: mode_injections no longer loaded into Settings; migration reads raw key.
                // Flag pending migration when residual mode_injections key still exists.
                presetEntriesMigrationPending = preferences[MODE_INJECTIONS] != null,
                presetsStoreExists = preferences[PRESETS] != null,
                    presets = preferences[PRESETS]?.let { rawPresets ->
                    // Prefer JSON-level Reference→Custom so sealed decode does not fail.
                    val rawMode = preferences[MODE_INJECTIONS]?.let { raw ->
                        runCatching {
                            JsonInstant.decodeFromString<List<LegacyModeInjection>>(raw)
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                    // HIGH-3: never treat non-empty corrupt presets as [] for emission only;
                    // emptyList is temporary until migratePresetEntriesIfNeeded succeeds on raw keys.
                    // migratePresetsJsonWithReferences throws on unparseable root (does not return []).
                    runCatching {
                        val referencedIds = mutableSetOf<Uuid>()
                        absorbOrphanModeInjections(
                            migratePresetsJsonWithReferences(rawPresets, rawMode, referencedIds),
                            rawMode,
                            extraCoveredIds = referencedIds,
                        )
                    }.recoverCatching {
                        JsonInstant.decodeFromString<List<Preset>>(rawPresets)
                    }.onFailure { err ->
                        Log.e(
                            "SettingsStore",
                            "Failed to decode presets (raw length=${rawPresets.length}); " +
                                "emitting empty until migration retry (raw keys preserved)",
                            err,
                        )
                    }.getOrDefault(emptyList())
                } ?: emptyList(),
                toolPermissionPresets = decodeToolPermissionPresets(preferences[TOOL_PERMISSION_PRESETS]),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                quickMessages = preferences[QUICK_MESSAGES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                imageQuickMessages = preferences[IMAGE_QUICK_MESSAGES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                imageGenerationSettings = preferences[IMAGE_GENERATION_SETTINGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: ImageGenerationSettings(),
                imageGallerySettings = preferences[IMAGE_GALLERY_SETTINGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: ImageGallerySettings(),
                imageFavoriteCollections = preferences[IMAGE_FAVORITE_COLLECTIONS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] ?: true,
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: BackupReminderConfig(),
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                sponsorAlertDismissedAt = preferences[SPONSOR_ALERT_DISMISSED_AT] ?: 0,
                sponsorAlertDisabled = preferences[SPONSOR_ALERT_DISABLED] == true,
                workspaceFilesStorage = preferences[WORKSPACE_FILES_STORAGE]
                    ?.let { runCatching { WorkspaceFilesStorage.valueOf(it) }.getOrNull() }
                    ?: WorkspaceFilesStorage.PRIVATE,
                // [SemanticMemory Plugin]
                semanticMemoryConfig = preferences[SEMANTIC_MEMORY_CONFIG]?.let {
                    runCatching {
                        JsonInstant.decodeFromString<me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig>(it)
                    }.getOrNull()
                } ?: me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig(),
                clashConfig = preferences[CLASH_PROXY_CONFIG]?.let {
                    runCatching { JsonInstant.decodeFromString<ClashProxyConfig>(it) }.getOrNull()
                } ?: ClashProxyConfig(),
                enableKeepAliveNotification = preferences[ENABLE_KEEP_ALIVE_NOTIFICATION] == true,
                enableCheckpointCache = preferences[ENABLE_CHECKPOINT_CACHE] == true,
                checkpointStepInterval = coerceCheckpointStepInterval(
                    preferences[CHECKPOINT_STEP_INTERVAL] ?: DEFAULT_CHECKPOINT_STEP_INTERVAL,
                ),
                experimentalFeatures = decodeExperimentalFeatures(preferences[EXPERIMENTAL_FEATURES]),
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    providers.add(defaultProvider.copyProvider())
                }
            }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
                if (assistants.none { it.id == defaultAssistant.id }) {
                    assistants.add(defaultAssistant.copy())
                }
            }
            val assistantLifecycle = normalizeAssistantLifecycle(
                assistants = assistants,
                selectedAssistantId = it.assistantId,
            )
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            it.copy(
                providers = providers,
                assistants = assistantLifecycle.assistants,
                assistantId = assistantLifecycle.selectedAssistantId,
                ttsProviders = ttsProviders,
            )
        }
        .map { settings ->
            // 去重并清理无效引用（#259: 无 modeInjections）
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val presets = settings.presets.distinctBy { it.id }
            val validPresetIds = presets.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            // #182/#259: 迁移标记由 schedulePresetEntriesMigrationPersist 持久化路径完成
            // （含 Reference→Custom 与 mode_injections 清除）。此处仅清洗旧字段。
            val cleanedPresets = presets.map { preset ->
                if (preset.modeInjectionIds.isEmpty() &&
                    preset.disabledEntryIds.isEmpty() &&
                    preset.entriesVersion >= PRESET_ENTRIES_VERSION
                ) {
                    preset
                } else {
                    preset.copy(
                        modeInjectionIds = emptySet(),
                        disabledEntryIds = emptySet(),
                        entriesVersion = maxOf(preset.entriesVersion, PRESET_ENTRIES_VERSION),
                    )
                }
            }
            val anyPresetNeedsPersist = cleanedPresets != presets ||
                // 仍有 mode_injections key 时也要触发迁移持久化
                settings.presetEntriesMigrationPending
            settings.copy(
                presetEntriesMigrationPending = anyPresetNeedsPersist || settings.presetEntriesMigrationPending,
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet(),
                        presetIds = assistant.presetIds.filter { id ->
                            id in validPresetIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                asrProviders = asrProviders,
                selectedASRProviderId = settings.selectedASRProviderId
                    ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
                    ?: asrProviders.firstOrNull()?.id,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                recentChatModels = settings.recentChatModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                }.distinct().take(RECENT_CHAT_MODELS_LIMIT),
                providerTagOrder = (settings.providerTagOrder +
                    settings.providers.flatMap { it.tags })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct(),
                hiddenProviderTags = settings.hiddenProviderTags.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct(),
                presets = cleanedPresets,
                lorebooks = settings.lorebooks.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
                imageQuickMessages = settings.imageQuickMessages.distinctBy { it.id },
                imageGenerationSettings = settings.imageGenerationSettings.normalized(),
                imageGallerySettings = settings.imageGallerySettings.copy(
                    columns = settings.imageGallerySettings.columns.coerceIn(
                        IMAGE_GALLERY_MIN_COLUMNS,
                        IMAGE_GALLERY_MAX_COLUMNS
                    )
                ),
            )
        }
        .map { settings -> migrateSubagentBuiltinsIfNeeded(settings) }
        .onEach { settings ->
            if (!settings.init && settings.subagentBuiltinMigrated) {
                scheduleSubagentBuiltinMigrationPersist(settings)
            }
            // #182: 迁移状态随当前 emission 携带，避免共享易失标志被后续 emission 覆盖。
            if (!settings.init && settings.presetEntriesMigrationPending) {
                schedulePresetEntriesMigrationPersist()
            }
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }

    private val subagentMigrationPersistScheduled = AtomicBoolean(false)

    private val presetEntriesMigrationPersistScheduled = AtomicBoolean(false)

    /** Serializes full and partial settings writes so concurrent RMW cannot interleave mid-update. */
    private val updateMutex = Mutex()

    private fun schedulePresetEntriesMigrationPersist() {
        appScope.launch {
            if (!presetEntriesMigrationPersistScheduled.compareAndSet(false, true)) {
                return@launch
            }
            try {
                updateMutex.withLock {
                    dataStore.edit { preferences ->
                        preferences.migratePresetEntriesIfNeeded()
                    }
                }
            } finally {
                presetEntriesMigrationPersistScheduled.set(false)
            }
        }
    }

    private fun scheduleSubagentBuiltinMigrationPersist(settings: Settings) {
        appScope.launch {
            if (dataStore.data.first()[SUBAGENT_BUILTIN_MIGRATED] == true) {
                return@launch
            }
            if (!subagentMigrationPersistScheduled.compareAndSet(false, true)) {
                return@launch
            }
            try {
                update(settings)
            } finally {
                subagentMigrationPersistScheduled.set(false)
            }
        }
    }

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .toMutableStateFlow(scope, Settings.dummy()) { cause, current ->
            // #189: 区分冷启动首帧失败与运行期失败。
            // - current 仍是 dummy（init=true）：从未拿到有效配置，UI 只会显示空白且 update() 被
            //   init 护栏静默丢弃。此时把失败逃逸出 AppScope（其 CoroutineExceptionHandler 只 log），
            //   post 到主线程 looper 交给已安装的 UncaughtExceptionHandler（CrashHandler）标记崩溃并
            //   重启进入 SafeModeActivity，兑现 #183「崩溃兜底交 SafeMode」的验收契约。
            // - current 已是有效值：仅上游后续失败，保留当前值优雅降级（不终止进程）。
            if (current.init) {
                Handler(Looper.getMainLooper()).post {
                    throw IllegalStateException(
                        "Failed to load settings on cold start; entering safe mode",
                        cause,
                    )
                }
            }
        }

    suspend fun update(settings: Settings) {
        updateMutex.withLock {
            updateUnlocked(settings)
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        updateMutex.withLock {
            updateUnlocked(fn(settingsFlow.value))
        }
    }

    private suspend fun updateUnlocked(settings: Settings) {
        if (settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        dataStore.edit { preferences ->
            preferences.writeFullSettings(settings)
        }
        // Assign only after a successful edit so a failed write cannot leave memory ahead of disk.
        settingsFlow.value = settings
    }

    private suspend fun syncSettingsFlowFromStore() {
        settingsFlow.value = settingsFlowRaw.first()
    }

    suspend fun updateCompressionPreferences(targetTokens: Int, keepRecentMessages: Int) {
        updateMutex.withLock {
            dataStore.edit { preferences ->
                preferences.writeCompressionPreferences(
                    targetTokens = targetTokens,
                    keepRecentMessages = keepRecentMessages,
                )
            }
            syncSettingsFlowFromStore()
        }
    }

    suspend fun updateAssistant(assistantId: Uuid): Boolean {
        return updateMutex.withLock {
            val fallbackAssistants = settingsFlow.value.assistants
            var selected = false
            dataStore.edit { preferences ->
                selected = preferences.selectActiveAssistant(
                    assistantId = assistantId,
                    fallbackAssistants = fallbackAssistants,
                )
            }
            if (selected) {
                syncSettingsFlowFromStore()
            }
            selected
        }
    }

    suspend fun setAssistantArchived(
        assistantId: Uuid,
        archived: Boolean,
    ): AssistantArchiveResult {
        return updateMutex.withLock {
            val fallbackSettings = settingsFlow.value
            var result: AssistantArchiveResult = AssistantArchiveResult.AssistantNotFound
            dataStore.edit { preferences ->
                result = preferences.writeAssistantArchiveState(
                    assistantId = assistantId,
                    archived = archived,
                    fallbackAssistants = fallbackSettings.assistants,
                    fallbackSelectedAssistantId = fallbackSettings.assistantId,
                )
            }
            if (result is AssistantArchiveResult.Success) {
                syncSettingsFlowFromStore()
            }
            result
        }
    }

    suspend fun updateAssistantConfig(assistant: Assistant) {
        updateMutex.withLock {
            val fallbackAssistants = settingsFlow.value.assistants
            dataStore.edit { preferences ->
                preferences.writeAssistantConfig(
                    assistant = assistant,
                    fallbackAssistants = fallbackAssistants,
                )
            }
            syncSettingsFlowFromStore()
        }
    }

    /** Applies a preset mutation to the latest persisted value without rewriting unrelated settings. */
    suspend fun updatePreset(
        presetId: Uuid,
        transform: (Preset) -> Preset,
    ): Boolean {
        return updateMutex.withLock {
            val fallbackSettings = settingsFlow.value
            var updated = false
            dataStore.edit { preferences ->
                updated = preferences.writePresetUpdate(
                    presetId = presetId,
                    fallbackPresets = fallbackSettings.presets,
                    transform = transform,
                )
            }
            if (updated) {
                syncSettingsFlowFromStore()
            }
            updated
        }
    }

    /**
     * Atomically applies a mutation to the latest persisted semantic memory config
     * without rewriting unrelated settings (#202). Mirrors [updatePreset].
     */
    suspend fun updateSemanticMemoryConfig(
        transform: (me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig) -> me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig,
    ) {
        updateMutex.withLock {
            val fallbackSettings = settingsFlow.value
            dataStore.edit { preferences ->
                val current = preferences[SEMANTIC_MEMORY_CONFIG]?.let {
                    runCatching {
                        JsonInstant.decodeFromString<me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig>(it)
                    }.getOrNull()
                } ?: fallbackSettings.semanticMemoryConfig
                preferences[SEMANTIC_MEMORY_CONFIG] = JsonInstant.encodeToString(transform(current))
            }
            syncSettingsFlowFromStore()
        }
    }

    /** Atomically applies a mutation to the latest persisted Clash proxy config (#209). */
    suspend fun updateClashProxyConfig(
        transform: (ClashProxyConfig) -> ClashProxyConfig,
    ) {
        updateMutex.withLock {
            val fallbackSettings = settingsFlow.value
            dataStore.edit { preferences ->
                val current = preferences[CLASH_PROXY_CONFIG]?.let {
                    runCatching { JsonInstant.decodeFromString<ClashProxyConfig>(it) }.getOrNull()
                } ?: fallbackSettings.clashConfig
                preferences[CLASH_PROXY_CONFIG] = JsonInstant.encodeToString(transform(current))
            }
            syncSettingsFlowFromStore()
        }
    }

    /** Partial write for experimental generation keep-alive switch (#219 / #215 dual-write). */
    suspend fun updateEnableKeepAliveNotification(enabled: Boolean) {
        updateExperimentalFeature(
            id = me.rerere.rikkahub.data.experimental.FEATURE_CHAT_KEEPALIVE,
            enabled = enabled,
        )
    }

    /** Partial write for experimental mid-generation checkpoint cache (#220 / #215 dual-write). */
    suspend fun updateCheckpointCache(
        enabled: Boolean? = null,
        stepInterval: Int? = null,
    ) {
        updateMutex.withLock {
            dataStore.edit { preferences ->
                if (enabled != null) {
                    preferences.writeExperimentalFeature(
                        id = me.rerere.rikkahub.data.experimental.FEATURE_CHECKPOINT_CACHE,
                        enabled = enabled,
                        fallbackMap = settingsFlow.value.experimentalFeatures,
                    )
                }
                if (stepInterval != null) {
                    preferences[CHECKPOINT_STEP_INTERVAL] = coerceCheckpointStepInterval(stepInterval)
                }
            }
            syncSettingsFlowFromStore()
        }
    }

    /**
     * #215: partial write for a global experimental feature.
     * Dual-writes legacy booleans for chat_keepalive / checkpoint_cache.
     */
    suspend fun updateExperimentalFeature(id: String, enabled: Boolean) {
        updateMutex.withLock {
            dataStore.edit { preferences ->
                preferences.writeExperimentalFeature(
                    id = id,
                    enabled = enabled,
                    fallbackMap = settingsFlow.value.experimentalFeatures,
                )
            }
            syncSettingsFlowFromStore()
        }
    }

    /**
     * #215: partial ASSISTANTS write for an assistant-scoped experimental feature override.
     * Dual-writes [Assistant.enableVariableSystem] for variable_system.
     */
    suspend fun updateAssistantExperimentalFeature(
        assistantId: Uuid,
        id: String,
        enabled: Boolean,
    ): Boolean {
        return updateMutex.withLock {
            val fallbackAssistants = settingsFlow.value.assistants
            var updated = false
            dataStore.edit { preferences ->
                updated = preferences.writeAssistantExperimentalFeature(
                    assistantId = assistantId,
                    featureId = id,
                    enabled = enabled,
                    fallbackAssistants = fallbackAssistants,
                )
            }
            if (updated) {
                syncSettingsFlowFromStore()
            }
            updated
        }
    }

    /**
     * #215: apply the same assistant-scoped experimental override to every assistant.
     */
    suspend fun updateAllAssistantsExperimentalFeature(id: String, enabled: Boolean) {
        updateMutex.withLock {
            val fallbackAssistants = settingsFlow.value.assistants
            dataStore.edit { preferences ->
                val assistants = preferences[ASSISTANTS]?.let {
                    runCatching { JsonInstant.decodeFromString<List<Assistant>>(it) }.getOrNull()
                } ?: fallbackAssistants
                val next = assistants.map { assistant ->
                    assistant.withExperimentalFeature(id, enabled)
                }
                preferences[ASSISTANTS] = JsonInstant.encodeToString(next)
            }
            syncSettingsFlowFromStore()
        }
    }

    suspend fun updateAssistantWorkspaceBinding(
        assistantId: Uuid,
        workspaceId: Uuid?,
    ): AssistantWorkspaceBindingUpdateResult {
        return updateMutex.withLock {
            val fallbackAssistants = settingsFlow.value.assistants
            var result = AssistantWorkspaceBindingUpdateResult.NOT_FOUND
            dataStore.edit { preferences ->
                result = preferences.writeAssistantWorkspaceBinding(
                    assistantId = assistantId,
                    workspaceId = workspaceId,
                    fallbackAssistants = fallbackAssistants,
                )
            }
            if (result == AssistantWorkspaceBindingUpdateResult.UPDATED) {
                syncSettingsFlowFromStore()
            }
            result
        }
    }

    /**
     * Toggles a preset binding on one assistant with a partial ASSISTANTS write (#218).
     *
     * Enabling uses exclusive selection (`setOf(presetId)`), matching the chat extension selector.
     * Optimistically updates [settingsFlow] under [updateMutex] before the disk edit so Switch UI
     * flips immediately; rolls back via [syncSettingsFlowFromStore] on failure.
     */
    suspend fun toggleAssistantPreset(
        assistantId: Uuid,
        presetId: Uuid,
        enabled: Boolean,
    ): Boolean {
        return updateMutex.withLock {
            val fallbackSettings = settingsFlow.value
            if (fallbackSettings.init) {
                Log.w(TAG, "Cannot toggleAssistantPreset on dummy settings")
                return@withLock false
            }
            val current = fallbackSettings.assistants.firstOrNull { it.id == assistantId }
            if (current == null) {
                Log.w(TAG, "toggleAssistantPreset: assistant $assistantId not found")
                return@withLock false
            }
            val optimisticIds = if (enabled) setOf(presetId) else current.presetIds - presetId
            settingsFlow.value = fallbackSettings.copy(
                assistants = fallbackSettings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(presetIds = optimisticIds)
                    } else {
                        assistant
                    }
                },
            )
            try {
                var updated = false
                dataStore.edit { preferences ->
                    updated = preferences.writeAssistantPresetToggle(
                        assistantId = assistantId,
                        presetId = presetId,
                        enabled = enabled,
                        fallbackAssistants = fallbackSettings.assistants,
                    )
                }
                if (!updated) {
                    syncSettingsFlowFromStore()
                    return@withLock false
                }
                syncSettingsFlowFromStore()
                true
            } catch (error: CancellationException) {
                // Do not treat structured cancel as IO failure; still drop optimistic if needed.
                runCatching { syncSettingsFlowFromStore() }
                    .onFailure { settingsFlow.value = fallbackSettings }
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "toggleAssistantPreset failed", error)
                // Prefer re-reading disk; if that fails too, restore pre-optimistic snapshot (AC5).
                runCatching { syncSettingsFlowFromStore() }
                    .onFailure { syncError ->
                        Log.w(TAG, "toggleAssistantPreset rollback sync failed", syncError)
                        settingsFlow.value = fallbackSettings
                    }
                false
            }
        }
    }

    suspend fun updateAssistantWebSearch(assistantId: Uuid, enabled: Boolean) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(enableWebSearch = enabled)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

/**
 * 在单次 DataStore edit 中从最新持久化值迁移 preset entries。
 *
 * #259:
 * 1. 解码 `mode_injections`（LegacyModeInjection）
 * 2. 旧 modeInjectionIds → Custom 快照
 * 3. Reference 条目 → Custom 内容快照（目标缺失则丢弃）
 * 4. 写回 PRESETS，删除 MODE_INJECTIONS key
 *
 * 只写 PRESETS / 迁移标记 / 删除 mode_injections，避免用 flow 中的旧 Settings 快照覆盖并发用户设置。
 */
internal fun MutablePreferences.migratePresetEntriesIfNeeded(): Boolean {
    val rawModeInjections = this[SettingsStore.MODE_INJECTIONS]
    val storedModeInjections = rawModeInjections?.let {
        runCatching { JsonInstant.decodeFromString<List<LegacyModeInjection>>(it) }.getOrDefault(emptyList())
    }.orEmpty()
    val rawPresets = this[SettingsStore.PRESETS]
    val referencedInjectionIds = mutableSetOf<Uuid>()
    val storedPresets = when {
        rawPresets != null -> migratePresetsJsonWithReferences(
            rawPresets,
            storedModeInjections,
            referencedInjectionIds,
        )
        storedModeInjections.isNotEmpty() -> listOf(
            Preset(
                id = DEFAULT_PRESET_ID,
                name = "Default Preset",
                description = "Contains existing quick injections.",
                modeInjectionIds = storedModeInjections.mapTo(mutableSetOf()) { it.id },
            ).migratedWithEntries(storedModeInjections)
        )
        else -> {
            // 无 mode_injections 也无 presets：仅标记已迁移
            if (this[SettingsStore.PRESET_ENTRIES_MIGRATED] == true) return false
            this[SettingsStore.PRESET_ENTRIES_MIGRATED] = true
            return false
        }
    }
    val migratedPresets = absorbOrphanModeInjections(
        storedPresets.map { it.migratedWithEntries(storedModeInjections) },
        storedModeInjections,
        extraCoveredIds = referencedInjectionIds,
    )
    val originalPresets = rawPresets?.let {
        runCatching { JsonInstant.decodeFromString<List<Preset>>(it) }.getOrNull()
    }
    val changed = migratedPresets != originalPresets || rawModeInjections != null
    if (changed || originalPresets == null) {
        this[SettingsStore.PRESETS] = JsonInstant.encodeToString(migratedPresets)
    }
    // #259: 清除全局 mode_injections key（迁移完成后不再保留）
    this.remove(SettingsStore.MODE_INJECTIONS)
    this[SettingsStore.PRESET_ENTRIES_MIGRATED] = true
    return changed || rawModeInjections != null
}

/**
 * 解码 presets JSON，把仍带 `@SerialName("reference")` 的条目快照为 Custom。
 * 目标 modeInjection 缺失时丢弃该 entry（#259 固定策略）。
 *
 * Parse failure throws so callers do not treat corrupt JSON as an empty preset list
 * and wipe DataStore (HIGH-3).
 */
internal fun migratePresetsJsonWithReferences(
    rawPresetsJson: String,
    modeInjections: List<LegacyModeInjection>,
    referencedInjectionIds: MutableSet<Uuid>? = null,
): List<Preset> {
    // 先尝试正常解码（无 Reference 类后，reference discriminator 会失败）
    runCatching { JsonInstant.decodeFromString<List<Preset>>(rawPresetsJson) }
        .getOrNull()
        ?.let { return it.map { p -> p.migratedWithEntries(modeInjections) } }

    // 失败：用 JsonElement 手工展开 reference 条目
    val root = runCatching {
        JsonInstant.parseToJsonElement(rawPresetsJson).jsonArray
    }.getOrNull()
        ?: error("Failed to parse presets JSON for Reference migration")

    return root.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val entriesElement = obj["entries"] as? JsonArray
        val migratedEntries = entriesElement?.mapNotNull { entryEl ->
            val entryObj = entryEl as? JsonObject ?: return@mapNotNull null
            val type = (entryObj["type"] as? JsonPrimitive)?.contentOrNull
                ?: (entryObj["type"] as? JsonPrimitive)?.content
            when (type) {
                "reference" -> {
                    val modeInjectionId = (entryObj["modeInjectionId"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        ?: return@mapNotNull null
                    val snapped = snapshotReferenceAsCustom(
                        entryId = (entryObj["id"] as? JsonPrimitive)?.contentOrNull
                            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            ?: Uuid.random(),
                        enabled = (entryObj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                        order = (entryObj["order"] as? JsonPrimitive)?.intOrNull ?: 0,
                        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                        injectDepth = (entryObj["injectDepth"] as? JsonPrimitive)?.intOrNull ?: 4,
                        role = MessageRole.USER,
                        modeInjectionId = modeInjectionId,
                        modeInjections = modeInjections,
                    )
                    if (snapped != null) {
                        referencedInjectionIds?.add(modeInjectionId)
                    }
                    snapped
                }
                "custom", "builtin", null -> {
                    // 让正常解码路径处理：临时单条目 encode 再 decode
                    runCatching {
                        JsonInstant.decodeFromString<PresetEntry>(JsonInstant.encodeToString(entryEl))
                    }.getOrNull()
                }
                else -> null
            }
        }.orEmpty()

        // 用去掉 entries 后的对象解码 Preset 外壳，再塞回迁移后的 entries
        val withoutEntries = obj.toMutableMap().apply {
            remove("entries")
            // 清除旧绑定字段，由 migratedWithEntries 处理
        }
        val shellJson = JsonInstant.encodeToString(JsonObject(withoutEntries))
        val shell = runCatching {
            JsonInstant.decodeFromString<Preset>(shellJson)
        }.getOrNull() ?: return@mapNotNull null

        val withEntries = if (migratedEntries.isNotEmpty() || entriesElement != null) {
            shell.copy(
                entries = migratedEntries,
                entriesVersion = PRESET_ENTRIES_VERSION,
                modeInjectionIds = emptySet(),
                disabledEntryIds = emptySet(),
            )
        } else {
            shell
        }
        withEntries.migratedWithEntries(modeInjections)
    }
}

/**
 * #259 HIGH-2: modeInjections not already snapshotted into any preset (by entry id
 * matching injection id, or via modeInjectionIds before migration) are absorbed into
 * Default Preset so assistant-only / orphan content is not lost when the global key is removed.
 */
internal fun absorbOrphanModeInjections(
    presets: List<Preset>,
    modeInjections: List<LegacyModeInjection>,
    extraCoveredIds: Set<Uuid> = emptySet(),
): List<Preset> {
    if (modeInjections.isEmpty()) return presets

    val coveredIds = buildSet {
        addAll(extraCoveredIds)
        for (preset in presets) {
            addAll(preset.modeInjectionIds)
            for (entry in preset.entries) {
                add(entry.id)
            }
        }
    }
    val orphans = modeInjections.filter { it.id !in coveredIds }
    if (orphans.isEmpty()) return presets

    val orphanEntries = orphans
        .sortedByDescending { it.priority }
        .mapIndexed { index, injection ->
            PresetEntry.Custom(
                id = injection.id,
                enabled = injection.enabled,
                order = index,
                position = injection.position,
                injectDepth = injection.injectDepth,
                role = injection.role,
                name = injection.name,
                content = injection.content,
                legacyPriority = injection.priority,
            )
        }

    val defaultIndex = presets.indexOfFirst { it.id == DEFAULT_PRESET_ID }
    if (defaultIndex >= 0) {
        val existing = presets[defaultIndex]
        val existingIds = existing.entries.mapTo(mutableSetOf()) { it.id }
        val toAppend = orphanEntries.filter { it.id !in existingIds }
        if (toAppend.isEmpty()) return presets
        val baseOrder = (existing.entries.maxOfOrNull { it.order } ?: -1) + 1
        val mergedEntries = existing.entries + toAppend.mapIndexed { i, e ->
            e.copy(order = baseOrder + i)
        }
        return presets.toMutableList().apply {
            set(
                defaultIndex,
                existing.copy(
                    entries = mergedEntries,
                    entriesVersion = PRESET_ENTRIES_VERSION,
                    modeInjectionIds = emptySet(),
                    disabledEntryIds = emptySet(),
                ),
            )
        }
    }

    val defaultPreset = Preset(
        id = DEFAULT_PRESET_ID,
        name = "Default Preset",
        description = "Contains existing quick injections.",
        entries = orphanEntries,
        entriesVersion = PRESET_ENTRIES_VERSION,
    )
    return listOf(defaultPreset) + presets
}

/** Full settings rewrite used by [SettingsStore.update]; kept private to the store call path. */
private fun MutablePreferences.writeFullSettings(settings: Settings) {
    this[SettingsStore.DYNAMIC_COLOR] = settings.dynamicColor
    this[SettingsStore.THEME_ID] = settings.themeId
    this[SettingsStore.CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
    this[SettingsStore.DEVELOPER_MODE] = settings.developerMode
    this[SettingsStore.REQUEST_LOGGING_ENABLED] = settings.requestLoggingEnabled
    this[SettingsStore.DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)
    this[SettingsStore.NETWORK_SETTING] = JsonInstant.encodeToString(settings.networkSetting)

    this[SettingsStore.FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
    this[SettingsStore.RECENT_CHAT_MODELS] = JsonInstant.encodeToString(settings.recentChatModels)
    this[SettingsStore.SELECT_MODEL] = settings.chatModelId.toString()
    this[SettingsStore.FAST_MODEL] = settings.fastModelId.toString()
    settings.titleModelId?.let {
        this[SettingsStore.TITLE_MODEL] = it.toString()
    } ?: this.remove(SettingsStore.TITLE_MODEL)
    this[SettingsStore.TRANSLATE_MODEL] = settings.translateModeId.toString()
    this[SettingsStore.ENABLE_SUGGESTION] = settings.enableSuggestion
    settings.suggestionModelId?.let {
        this[SettingsStore.SUGGESTION_MODEL] = it.toString()
    } ?: this.remove(SettingsStore.SUGGESTION_MODEL)
    this[SettingsStore.IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
    this[SettingsStore.TITLE_PROMPT] = settings.titlePrompt
    this[SettingsStore.TRANSLATION_PROMPT] = settings.translatePrompt
    this[SettingsStore.TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
    this[SettingsStore.SUGGESTION_PROMPT] = settings.suggestionPrompt
    this[SettingsStore.OCR_MODEL] = settings.ocrModelId.toString()
    this[SettingsStore.OCR_PROMPT] = settings.ocrPrompt
    this[SettingsStore.COMPRESS_MODEL] = settings.compressModelId.toString()
    this[SettingsStore.COMPRESS_PROMPT] = settings.compressPrompt
    writeCompressionPreferences(
        targetTokens = settings.compressTargetTokens,
        keepRecentMessages = settings.compressKeepRecentMessages,
    )

    this[SettingsStore.PROVIDERS] = JsonInstant.encodeToString(settings.providers)
    this[SettingsStore.PROVIDER_TAG_ORDER] = JsonInstant.encodeToString(settings.providerTagOrder)
    this[SettingsStore.HIDDEN_PROVIDER_TAGS] = JsonInstant.encodeToString(settings.hiddenProviderTags)

    this[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
    this[SettingsStore.SELECT_ASSISTANT] = settings.assistantId.toString()
    this[SettingsStore.ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)
    this[SettingsStore.ENABLE_MEMORY_TABLE] = settings.enableMemoryTable
    this[SettingsStore.MEMORY_TABLE_MAX_INJECT_DOCUMENTS] = encodeMemoryTableBudget(
        settings.memoryTableMaxInjectDocuments
    )
    this[SettingsStore.MEMORY_TABLE_MAX_INJECT_TOKENS] = encodeMemoryTableBudget(
        settings.memoryTableMaxInjectTokens
    )
    this[SettingsStore.MEMORY_TABLE_MAX_INJECT_CHARS] = encodeMemoryTableBudget(
        settings.memoryTableMaxInjectChars
    )
    writeMemoryTableAutoSyncEnabled(settings.memoryTableAutoSyncEnabled)

    this[SettingsStore.SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
    this[SettingsStore.SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
    this[SettingsStore.SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, settings.searchServices.size - 1)

    this[SettingsStore.MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
    this[SettingsStore.GLOBAL_SUBAGENT_PROFILES] = JsonInstant.encodeToString(settings.globalSubagentProfiles)
    this[SettingsStore.SUBAGENT_BUILTIN_MIGRATED] = settings.subagentBuiltinMigrated
    this[SettingsStore.WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
    this[SettingsStore.S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
    this[SettingsStore.TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
    settings.selectedTTSProviderId?.let {
        this[SettingsStore.SELECTED_TTS_PROVIDER] = it.toString()
    } ?: this.remove(SettingsStore.SELECTED_TTS_PROVIDER)
    this[SettingsStore.DEFAULT_TTS_PLAYBACK_SPEED] = settings.defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f)
    this[SettingsStore.ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
    settings.selectedASRProviderId?.let {
        this[SettingsStore.SELECTED_ASR_PROVIDER] = it.toString()
    } ?: this.remove(SettingsStore.SELECTED_ASR_PROVIDER)
    // #259 / HIGH-3: never wipe non-empty PRESETS or residual MODE_INJECTIONS with empty
    // settings.presets while migration is still pending (load decode may have failed transiently).
    val existingRawPresets = this[SettingsStore.PRESETS]
    val migrationPending = this[SettingsStore.MODE_INJECTIONS] != null
    val wouldWipeNonEmptyPresets = settings.presets.isEmpty() &&
        !existingRawPresets.isNullOrBlank() &&
        existingRawPresets.trim() != "[]"
    if (wouldWipeNonEmptyPresets && migrationPending) {
        Log.w(
            "SettingsStore",
            "writeFullSettings: skip overwriting PRESETS/MODE_INJECTIONS with empty while migration pending",
        )
    } else {
        this.remove(SettingsStore.MODE_INJECTIONS)
        this[SettingsStore.PRESETS] = JsonInstant.encodeToString(settings.presets)
    }
    this[SettingsStore.TOOL_PERMISSION_PRESETS] = JsonInstant.encodeToString(
        settings.toolPermissionPresets.take(TOOL_PERMISSION_PRESET_MAX_COUNT).filter { it.isValidForPersistence() }
    )
    this[SettingsStore.LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
    this[SettingsStore.QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
    this[SettingsStore.IMAGE_QUICK_MESSAGES] = JsonInstant.encodeToString(settings.imageQuickMessages)
    this[SettingsStore.IMAGE_GENERATION_SETTINGS] = JsonInstant.encodeToString(settings.imageGenerationSettings)
    this[SettingsStore.IMAGE_GALLERY_SETTINGS] = JsonInstant.encodeToString(settings.imageGallerySettings)
    this[SettingsStore.IMAGE_FAVORITE_COLLECTIONS] = JsonInstant.encodeToString(settings.imageFavoriteCollections)
    this[SettingsStore.WEB_SERVER_ENABLED] = settings.webServerEnabled
    this[SettingsStore.WEB_SERVER_PORT] = settings.webServerPort
    this[SettingsStore.WEB_SERVER_JWT_ENABLED] = settings.webServerJwtEnabled
    this[SettingsStore.WEB_SERVER_ACCESS_PASSWORD] = settings.webServerAccessPassword
    this[SettingsStore.WEB_SERVER_LOCALHOST_ONLY] = settings.webServerLocalhostOnly
    this[SettingsStore.BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
    this[SettingsStore.LAUNCH_COUNT] = settings.launchCount
    this[SettingsStore.WORKSPACE_FILES_STORAGE] = settings.workspaceFilesStorage.name
    this[SettingsStore.SPONSOR_ALERT_DISMISSED_AT] = settings.sponsorAlertDismissedAt
    this[SettingsStore.SPONSOR_ALERT_DISABLED] = settings.sponsorAlertDisabled
    // [SemanticMemory Plugin]
    this[SettingsStore.SEMANTIC_MEMORY_CONFIG] = JsonInstant.encodeToString(settings.semanticMemoryConfig)
    this[SettingsStore.CLASH_PROXY_CONFIG] = JsonInstant.encodeToString(settings.clashConfig)
    this[SettingsStore.ENABLE_KEEP_ALIVE_NOTIFICATION] = settings.enableKeepAliveNotification
    this[SettingsStore.ENABLE_CHECKPOINT_CACHE] = settings.enableCheckpointCache
    this[SettingsStore.CHECKPOINT_STEP_INTERVAL] = coerceCheckpointStepInterval(settings.checkpointStepInterval)
    this[SettingsStore.EXPERIMENTAL_FEATURES] = JsonInstant.encodeToString(settings.experimentalFeatures)
}

/** Reads the latest persisted preset list and changes only [presetId]. */
internal fun MutablePreferences.writePresetUpdate(
    presetId: Uuid,
    fallbackPresets: List<Preset>,
    transform: (Preset) -> Preset,
): Boolean {
    val presets = this[SettingsStore.PRESETS]?.let {
        JsonInstant.decodeFromString<List<Preset>>(it)
    } ?: fallbackPresets
    val index = presets.indexOfFirst { it.id == presetId }
    if (index < 0) return false

    val modeInjections = this[SettingsStore.MODE_INJECTIONS]?.let {
        runCatching { JsonInstant.decodeFromString<List<LegacyModeInjection>>(it) }.getOrDefault(emptyList())
    }.orEmpty()
    val current = presets[index].migratedWithEntries(modeInjections)
    val transformed = transform(current).copy(id = current.id)
    this[SettingsStore.PRESETS] = JsonInstant.encodeToString(
        presets.toMutableList().apply { set(index, transformed) }
    )
    return true
}

internal fun MutablePreferences.writeAssistantConfig(
    assistant: Assistant,
    fallbackAssistants: List<Assistant>,
) {
    val assistants = this[SettingsStore.ASSISTANTS]?.let {
        JsonInstant.decodeFromString<List<Assistant>>(it)
    } ?: fallbackAssistants
    if (assistants.none { it.id == assistant.id }) {
        return
    }
    this[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(
        assistants.map { current ->
            if (current.id == assistant.id) assistant else current
        }
    )
}

enum class AssistantWorkspaceBindingUpdateResult {
    UPDATED,
    NOT_FOUND,
}

internal fun MutablePreferences.writeAssistantWorkspaceBinding(
    assistantId: Uuid,
    workspaceId: Uuid?,
    fallbackAssistants: List<Assistant>,
): AssistantWorkspaceBindingUpdateResult {
    val assistants = this[SettingsStore.ASSISTANTS]?.let {
        JsonInstant.decodeFromString<List<Assistant>>(it)
    } ?: fallbackAssistants
    if (assistants.none { it.id == assistantId }) {
        return AssistantWorkspaceBindingUpdateResult.NOT_FOUND
    }
    this[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(
        assistants.map { current ->
            if (current.id == assistantId) current.copy(workspaceId = workspaceId) else current
        }
    )
    return AssistantWorkspaceBindingUpdateResult.UPDATED
}

/**
 * Partial ASSISTANTS write for preset Switch toggles (#218).
 * Enabling is exclusive (`setOf(presetId)`); disabling removes only [presetId].
 * Mutates only the target assistant's [Assistant.presetIds]; other keys stay untouched.
 */
internal fun MutablePreferences.writeAssistantPresetToggle(
    assistantId: Uuid,
    presetId: Uuid,
    enabled: Boolean,
    fallbackAssistants: List<Assistant>,
): Boolean {
    val assistants = this[SettingsStore.ASSISTANTS]?.let {
        JsonInstant.decodeFromString<List<Assistant>>(it)
    } ?: fallbackAssistants
    val index = assistants.indexOfFirst { it.id == assistantId }
    if (index < 0) return false
    val current = assistants[index]
    val newIds = if (enabled) setOf(presetId) else current.presetIds - presetId
    if (newIds == current.presetIds) return true
    this[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(
        assistants.toMutableList().apply {
            set(index, current.copy(presetIds = newIds))
        },
    )
    return true
}

internal fun MutablePreferences.writeAssistantArchiveState(
    assistantId: Uuid,
    archived: Boolean,
    fallbackAssistants: List<Assistant>,
    fallbackSelectedAssistantId: Uuid,
): AssistantArchiveResult {
    val assistants = storedAssistantsWithFallback(fallbackAssistants)
    val selectedAssistantId = this[SettingsStore.SELECT_ASSISTANT]
        ?.let { storedId -> runCatching { Uuid.parse(storedId) }.getOrNull() }
        ?: fallbackSelectedAssistantId
    val (result, transition) = transitionAssistantArchive(
        assistants = assistants,
        selectedAssistantId = selectedAssistantId,
        assistantId = assistantId,
        archived = archived,
    )
    if (transition != null) {
        this[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(transition.assistants)
        this[SettingsStore.SELECT_ASSISTANT] = transition.selectedAssistantId.toString()
    }
    return result
}

internal fun MutablePreferences.selectActiveAssistant(
    assistantId: Uuid,
    fallbackAssistants: List<Assistant>,
): Boolean {
    val target = storedAssistantsWithFallback(fallbackAssistants)
        .firstOrNull { it.id == assistantId && !it.isArchived }
        ?: return false
    this[SettingsStore.SELECT_ASSISTANT] = target.id.toString()
    return true
}

private fun Preferences.storedAssistantsWithFallback(fallbackAssistants: List<Assistant>): List<Assistant> {
    val stored = this[SettingsStore.ASSISTANTS]?.let {
        JsonInstant.decodeFromString<List<Assistant>>(it)
    }.orEmpty()
    if (stored.isEmpty()) return fallbackAssistants
    val storedIds = stored.mapTo(mutableSetOf()) { it.id }
    return stored + fallbackAssistants.filterNot { it.id in storedIds }
}

internal data class CompressionPreferences(
    val targetTokens: Int,
    val keepRecentMessages: Int,
)

internal fun Preferences.compressionPreferences(): CompressionPreferences = CompressionPreferences(
    targetTokens = this[SettingsStore.COMPRESS_TARGET_TOKENS] ?: DEFAULT_COMPRESS_TARGET_TOKENS,
    keepRecentMessages = this[SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES]
        ?: DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES,
)

internal fun Preferences.readMemoryTableAutoSyncEnabled(): Boolean =
    this[SettingsStore.MEMORY_TABLE_AUTO_SYNC_ENABLED] == true

internal fun MutablePreferences.writeMemoryTableAutoSyncEnabled(enabled: Boolean) {
    this[SettingsStore.MEMORY_TABLE_AUTO_SYNC_ENABLED] = enabled
}

internal fun MutablePreferences.writeCompressionPreferences(
    targetTokens: Int,
    keepRecentMessages: Int,
) {
    this[SettingsStore.COMPRESS_TARGET_TOKENS] = targetTokens
    this[SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES] = keepRecentMessages
}

internal fun decodeExperimentalFeatures(json: String?): Map<String, Boolean> {
    if (json.isNullOrBlank()) return emptyMap()
    return runCatching {
        JsonInstant.decodeFromString<Map<String, Boolean>>(json)
    }.getOrDefault(emptyMap())
}

internal fun MutablePreferences.writeExperimentalFeature(
    id: String,
    enabled: Boolean,
    fallbackMap: Map<String, Boolean>,
) {
    val current = this[SettingsStore.EXPERIMENTAL_FEATURES]
        ?.let { decodeExperimentalFeatures(it) }
        ?: fallbackMap
    val next = current + (id to enabled)
    this[SettingsStore.EXPERIMENTAL_FEATURES] = JsonInstant.encodeToString(next)
    when (id) {
        me.rerere.rikkahub.data.experimental.FEATURE_CHAT_KEEPALIVE -> {
            this[SettingsStore.ENABLE_KEEP_ALIVE_NOTIFICATION] = enabled
        }
        me.rerere.rikkahub.data.experimental.FEATURE_CHECKPOINT_CACHE -> {
            this[SettingsStore.ENABLE_CHECKPOINT_CACHE] = enabled
        }
    }
}

internal fun Assistant.withExperimentalFeature(featureId: String, enabled: Boolean): Assistant {
    val overrides = experimentalFeatureOverrides + (featureId to enabled)
    return if (featureId == me.rerere.rikkahub.data.experimental.FEATURE_VARIABLE_SYSTEM) {
        copy(
            experimentalFeatureOverrides = overrides,
            enableVariableSystem = enabled,
        )
    } else {
        copy(experimentalFeatureOverrides = overrides)
    }
}

internal fun MutablePreferences.writeAssistantExperimentalFeature(
    assistantId: Uuid,
    featureId: String,
    enabled: Boolean,
    fallbackAssistants: List<Assistant>,
): Boolean {
    val assistants = this[SettingsStore.ASSISTANTS]?.let {
        runCatching { JsonInstant.decodeFromString<List<Assistant>>(it) }.getOrNull()
    } ?: fallbackAssistants
    val index = assistants.indexOfFirst { it.id == assistantId }
    if (index < 0) return false
    val next = assistants.toMutableList().apply {
        set(index, this[index].withExperimentalFeature(featureId, enabled))
    }
    this[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(next)
    return true
}

internal fun decodeMemoryTableBudget(storedValue: Int?, defaultValue: Int): Int? = when {
    storedValue == null -> defaultValue
    storedValue == MEMORY_TABLE_BUDGET_UNLIMITED_SENTINEL -> null
    storedValue >= 0 -> storedValue
    else -> defaultValue
}

internal fun encodeMemoryTableBudget(value: Int?): Int =
    value ?: MEMORY_TABLE_BUDGET_UNLIMITED_SENTINEL

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    @Transient
    val presetEntriesMigrationPending: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val requestLoggingEnabled: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val recentChatModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid? = null,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    val enableSuggestion: Boolean = true,
    val suggestionModelId: Uuid? = null,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = Uuid.random(),
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val compressTargetTokens: Int = DEFAULT_COMPRESS_TARGET_TOKENS,
    val compressKeepRecentMessages: Int = DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val providerTagOrder: List<String> = emptyList(),
    val hiddenProviderTags: List<String> = emptyList(),
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val enableMemoryTable: Boolean = false,
    val memoryTableMaxInjectDocuments: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS,
    val memoryTableMaxInjectTokens: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS,
    val memoryTableMaxInjectChars: Int? = DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS,
    val memoryTableAutoSyncEnabled: Boolean = false,
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val globalSubagentProfiles: List<SubagentProfile> = emptyList(),
    val subagentBuiltinMigrated: Boolean = false,
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    @Transient
    val presetsStoreExists: Boolean = false,
    val presets: List<Preset> = emptyList(),
    val toolPermissionPresets: List<ToolPermissionPreset> = emptyList(),
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val imageQuickMessages: List<QuickMessage> = emptyList(),
    val imageGenerationSettings: ImageGenerationSettings = ImageGenerationSettings(),
    val imageGallerySettings: ImageGallerySettings = ImageGallerySettings(),
    val imageFavoriteCollections: List<ImageFavoriteCollection> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = true,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    val sponsorAlertDismissedAt: Int = 0,
    val sponsorAlertDisabled: Boolean = false,
    val workspaceFilesStorage: WorkspaceFilesStorage = WorkspaceFilesStorage.PRIVATE,
    // [SemanticMemory Plugin]
    val semanticMemoryConfig: me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig =
        me.rerere.rikkahub.data.memory.semantic.SemanticMemoryConfig(),
    val clashConfig: ClashProxyConfig = ClashProxyConfig(),
    /** Experimental: show an ongoing FGS notification while chat generation is active (#219 legacy). */
    val enableKeepAliveNotification: Boolean = false,
    /** Experimental: persist conversation checkpoints every N tool steps during generation (#220 legacy). */
    val enableCheckpointCache: Boolean = false,
    /** Tool-step interval for #220 checkpoints; coerced to 4|8|16|32. */
    val checkpointStepInterval: Int = DEFAULT_CHECKPOINT_STEP_INTERVAL,
    /** #215: global experimental feature map (featureId → enabled). */
    val experimentalFeatures: Map<String, Boolean> = emptyMap(),
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

fun Settings.withRecentChatModel(modelId: Uuid): Settings = copy(
    recentChatModels = (listOf(modelId) + recentChatModels.filter { it != modelId })
        .take(RECENT_CHAT_MODELS_LIMIT)
)

fun Settings.effectiveProviderTags(): List<String> {
    val usedTags = providers.flatMap { it.tags }.mapNotNull { it.normalizedProviderTagOrNull() }
    val hiddenTags = hiddenProviderTags.mapNotNull { it.normalizedProviderTagOrNull() }.toSet()
    return (providerTagOrder + usedTags)
        .mapNotNull { it.normalizedProviderTagOrNull() }
        .filter { tag -> tag in usedTags || tag !in hiddenTags }
        .distinct()
}

fun Settings.renameProviderTag(oldTag: String, newTag: String): Settings {
    val old = oldTag.normalizedProviderTagOrNull() ?: return this
    val new = newTag.normalizedProviderTagOrNull() ?: return this
    if (old == new) return this
    val renamedProviders = providers.map { provider ->
        provider.copyProvider(
            tags = provider.tags.map { tag ->
                if (tag.normalizedProviderTagOrNull() == old) new else tag
            }.mapNotNull { it.normalizedProviderTagOrNull() }.distinct()
        )
    }
    val currentTags = copy(providers = renamedProviders).effectiveProviderTags()
    return copy(
        providers = renamedProviders,
        providerTagOrder = currentTags.map { if (it == old) new else it }.distinct(),
        hiddenProviderTags = (hiddenProviderTags + old).filter { it != new }.distinct(),
    )
}

fun Settings.deleteProviderTag(tag: String): Settings {
    val normalized = tag.normalizedProviderTagOrNull() ?: return this
    return copy(
        providers = providers.map { provider ->
            provider.copyProvider(
                tags = provider.tags.filter { it.normalizedProviderTagOrNull() != normalized }
            )
        },
        providerTagOrder = providerTagOrder.filter { it.normalizedProviderTagOrNull() != normalized },
        hiddenProviderTags = (hiddenProviderTags + normalized).distinct(),
    )
}

fun Settings.reorderProviderTags(tags: List<String>): Settings = copy(
    providerTagOrder = tags.mapNotNull { it.normalizedProviderTagOrNull() }.distinct()
)

private fun String.normalizedProviderTagOrNull(): String? =
    trim().takeIf { it.isNotBlank() }

internal fun migrateSubagentBuiltinsIfNeeded(settings: Settings): Settings {
    if (settings.init || settings.subagentBuiltinMigrated) {
        return settings
    }
    val existingNames = settings.globalSubagentProfiles.map { it.name }.toSet()
    val toAdd = SubagentRegistry.BUILTIN_PROFILES.filter { it.name !in existingNames }
    val globalSubagentProfiles = settings.globalSubagentProfiles + toAdd
    val globalProfileNames = globalSubagentProfiles.map { it.name }.toSet()
    val assistants = settings.assistants.map { assistant ->
        if (assistant.disabledBuiltinSubagents.isEmpty()) {
            assistant
        } else {
            val migratedDisabled = assistant.disabledBuiltinSubagents.filter { it in globalProfileNames }.toSet()
            assistant.copy(
                disabledGlobalSubagents = assistant.disabledGlobalSubagents + migratedDisabled,
            )
        }
    }
    return settings.copy(
        globalSubagentProfiles = globalSubagentProfiles,
        subagentBuiltinMigrated = true,
        assistants = assistants,
    )
}

@Serializable
data class ImageGenerationSettings(
    val size: ImageSizeOption = ImageSizeOption.AUTO,
    val customSize: String = "2048x1152",
    val quality: ImageQualityOption = ImageQualityOption.AUTO,
    val outputFormat: ImageOutputFormatOption = ImageOutputFormatOption.PNG,
    val outputCompression: Int = 100,
    val background: ImageBackgroundOption = ImageBackgroundOption.AUTO,
    val moderation: ImageModerationOption = ImageModerationOption.AUTO,
    val imageStreaming: Boolean = false,
    /** 同时进行中的 API 请求数上限（已完成/失败的任务卡片不占名额） */
    val maxConcurrentJobs: Int = 4,
)

@Suppress("DEPRECATION")
private fun ImageGenerationSettings.normalized(): ImageGenerationSettings {
    val normalizedOutputFormat = when (outputFormat) {
        ImageOutputFormatOption.URL,
        ImageOutputFormatOption.B64_JSON -> ImageOutputFormatOption.PNG
        else -> outputFormat
    }
    return copy(
        outputFormat = normalizedOutputFormat,
        outputCompression = outputCompression.coerceIn(0, 100),
        maxConcurrentJobs = maxConcurrentJobs.coerceIn(1, 8),
    )
}

@Serializable
data class ImageGallerySettings(
    val displayMode: ImageGalleryDisplayMode = ImageGalleryDisplayMode.GRID,
    val columns: Int = 2,
    val spaceColumns: Int = 2,
)

@Serializable
data class ImageFavoriteCollection(
    val id: String,
    val name: String,
)

@Serializable
enum class ImageGalleryDisplayMode {
    @SerialName("grid")
    GRID,

    @SerialName("grouped")
    GROUPED,
}

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class UiTypographyFamily {
    @SerialName("system")
    SYSTEM,

    @SerialName("brand")
    BRAND,
}

@Serializable
enum class UiTypographyWeightBias {
    @SerialName("light")
    LIGHT,

    @SerialName("default")
    DEFAULT,

    @SerialName("medium")
    MEDIUM,

    @SerialName("bold")
    BOLD,
}

fun Float.coerceUiTypographyScale(): Float = coerceIn(0.85f, 1.25f)

fun Float.coerceUiTypographyLineHeightScale(): Float = coerceIn(0.9f, 1.3f)

fun Float.coerceUiTypographyLetterSpacingScale(): Float = coerceIn(0f, 1.5f)

@Serializable
enum class UploadInjectMode {
    @SerialName("path_only")
    PATH_ONLY,
    @SerialName("full_body")
    FULL_BODY,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableBlurEffect: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val documentUploadInjectMode: UploadInjectMode = UploadInjectMode.PATH_ONLY,
    val uiTypographyFamily: UiTypographyFamily = UiTypographyFamily.SYSTEM,
    val uiTypographyWeightBias: UiTypographyWeightBias = UiTypographyWeightBias.DEFAULT,
    val uiTypographyScale: Float = 1.0f,
    val uiTypographyLineHeightScale: Float = 1.0f,
    val uiTypographyLetterSpacingScale: Float = 1.0f,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid?, fallback: Uuid? = null): Model? {
    if (uuid == null && fallback == null) return null
    return uuid?.let { this.providers.findModelById(it) }
        ?: fallback?.let { this.providers.findModelById(it) }
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.resolveChatModelId(
    conversation: Conversation? = null,
    assistant: Assistant = resolveAssistant(conversation),
): Uuid = conversation?.chatModelId ?: assistant.chatModelId ?: chatModelId

fun Settings.resolveAssistant(conversation: Conversation? = null): Assistant {
    return conversation?.let { getAssistantById(it.assistantId) } ?: getCurrentAssistant()
}

fun Settings.getCurrentChatModel(conversation: Conversation? = null): Model? {
    return findModelById(resolveChatModelId(conversation))
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId && !it.isArchived }
        ?: this.assistants.first { !it.isArchived }
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedASRProvider(): ASRProviderSetting? {
    return selectedASRProviderId?.let { id ->
        asrProviders.find { it.id == id }
    } ?: asrProviders.firstOrNull()
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = ""
    ),
    Assistant(
        id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
        name = "Template Assistant",
        systemPrompt = """
            You are a helpful assistant, called {{char}}, based on model {{model_name}}.

            ## Info
            - Date: {{cur_date}}
            - Locale: {{locale}}
            - Timezone: {{timezone}}
            - Device Info: {{device_info}}
            - System Version: {{system_version}}
            - User Nickname: {{user}}

            ## Hint
            - If the user does not specify a language, reply in the user's primary language.
            - Remember to use Markdown syntax for formatting, and use latex for mathematical expressions.
        """.trimIndent()
    ),
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.OpenAI(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "AiHubMix",
        baseUrl = "https://aihubmix.com/v1",
        model = "gpt-4o-mini-tts",
        voice = "alloy",
    )
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

internal val DEFAULT_PRESET_ID = Uuid.parse("a9433f62-d8e9-4a38-8fb8-9c3f63f7f1b0")
