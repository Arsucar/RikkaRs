package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_CHARS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_DOCUMENTS
import me.rerere.rikkahub.data.model.DEFAULT_MEMORY_TABLE_MAX_INJECT_TOKENS
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.WorkspaceFilesStorage
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
const val DEFAULT_COMPRESS_TARGET_TOKENS = 2000
const val DEFAULT_COMPRESS_KEEP_RECENT_MESSAGES = 32

private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration()
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
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val REQUEST_LOGGING_ENABLED = booleanPreferencesKey("request_logging_enabled")
        // 模型选择
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
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

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")

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

        val WORKSPACE_FILES_STORAGE = stringPreferencesKey("workspace_files_storage")
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
                enableWebSearch = preferences[ENABLE_WEB_SEARCH] == true,
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
                memoryTableAutoSyncEnabled = false,
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
                asrProviders = preferences[ASR_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let { Uuid.parse(it) },
                modeInjections = preferences[MODE_INJECTIONS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                presetsStoreExists = preferences[PRESETS] != null,
                presets = preferences[PRESETS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
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
                workspaceFilesStorage = preferences[WORKSPACE_FILES_STORAGE]
                    ?.let { runCatching { WorkspaceFilesStorage.valueOf(it) }.getOrNull() }
                    ?: WorkspaceFilesStorage.PRIVATE,
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
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val modeInjections = settings.modeInjections.distinctBy { it.id }
            val validModeInjectionIds = modeInjections.map { it.id }.toSet()
            val presets = settings.presets.withDefaultPreset(
                modeInjections = modeInjections,
                shouldCreateDefault = !settings.presetsStoreExists,
            )
            val validPresetIds = presets.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            settings.copy(
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
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        // 过滤掉不存在的模式注入 ID
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        // 过滤掉不存在的 Lorebook ID
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        // 过滤掉不存在的快捷消息 ID
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet(),
                        // 过滤掉不存在的预设 ID
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
                modeInjections = modeInjections,
                presets = presets.map { preset ->
                    preset.copy(
                        modeInjectionIds = preset.modeInjectionIds.filter { it in validModeInjectionIds }.toSet(),
                        disabledEntryIds = preset.disabledEntryIds.filter { it in validModeInjectionIds }.toSet(),
                    )
                }.distinctBy { it.id },
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
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }

    private val subagentMigrationPersistScheduled = AtomicBoolean(false)

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
        .toMutableStateFlow(scope, Settings.dummy())

    suspend fun update(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        settingsFlow.value = settings
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = settings.dynamicColor
            preferences[THEME_ID] = settings.themeId
            preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
            preferences[DEVELOPER_MODE] = settings.developerMode
            preferences[REQUEST_LOGGING_ENABLED] = settings.requestLoggingEnabled
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)

            preferences[ENABLE_WEB_SEARCH] = settings.enableWebSearch
            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
            preferences[RECENT_CHAT_MODELS] = JsonInstant.encodeToString(settings.recentChatModels)
            preferences[SELECT_MODEL] = settings.chatModelId.toString()
            preferences[FAST_MODEL] = settings.fastModelId.toString()
            settings.titleModelId?.let {
                preferences[TITLE_MODEL] = it.toString()
            } ?: preferences.remove(TITLE_MODEL)
            preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
            preferences[ENABLE_SUGGESTION] = settings.enableSuggestion
            settings.suggestionModelId?.let {
                preferences[SUGGESTION_MODEL] = it.toString()
            } ?: preferences.remove(SUGGESTION_MODEL)
            preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = settings.titlePrompt
            preferences[TRANSLATION_PROMPT] = settings.translatePrompt
            preferences[TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
            preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
            preferences[OCR_MODEL] = settings.ocrModelId.toString()
            preferences[OCR_PROMPT] = settings.ocrPrompt
            preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
            preferences[COMPRESS_PROMPT] = settings.compressPrompt
            preferences.writeCompressionPreferences(
                targetTokens = settings.compressTargetTokens,
                keepRecentMessages = settings.compressKeepRecentMessages,
            )

            preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)
            preferences[PROVIDER_TAG_ORDER] = JsonInstant.encodeToString(settings.providerTagOrder)
            preferences[HIDDEN_PROVIDER_TAGS] = JsonInstant.encodeToString(settings.hiddenProviderTags)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
            preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)
            preferences[ENABLE_MEMORY_TABLE] = settings.enableMemoryTable
            preferences[MEMORY_TABLE_MAX_INJECT_DOCUMENTS] = encodeMemoryTableBudget(
                settings.memoryTableMaxInjectDocuments
            )
            preferences[MEMORY_TABLE_MAX_INJECT_TOKENS] = encodeMemoryTableBudget(
                settings.memoryTableMaxInjectTokens
            )
            preferences[MEMORY_TABLE_MAX_INJECT_CHARS] = encodeMemoryTableBudget(
                settings.memoryTableMaxInjectChars
            )
            preferences[MEMORY_TABLE_AUTO_SYNC_ENABLED] = false

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
            preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, settings.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
            preferences[GLOBAL_SUBAGENT_PROFILES] = JsonInstant.encodeToString(settings.globalSubagentProfiles)
            preferences[SUBAGENT_BUILTIN_MIGRATED] = settings.subagentBuiltinMigrated
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
            settings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
            preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
            settings.selectedASRProviderId?.let {
                preferences[SELECTED_ASR_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_ASR_PROVIDER)
            preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
            preferences[PRESETS] = JsonInstant.encodeToString(settings.presets)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
            preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
            preferences[IMAGE_QUICK_MESSAGES] = JsonInstant.encodeToString(settings.imageQuickMessages)
            preferences[IMAGE_GENERATION_SETTINGS] = JsonInstant.encodeToString(settings.imageGenerationSettings)
            preferences[IMAGE_GALLERY_SETTINGS] = JsonInstant.encodeToString(settings.imageGallerySettings)
            preferences[IMAGE_FAVORITE_COLLECTIONS] = JsonInstant.encodeToString(settings.imageFavoriteCollections)
            preferences[WEB_SERVER_ENABLED] = settings.webServerEnabled
            preferences[WEB_SERVER_PORT] = settings.webServerPort
            preferences[WEB_SERVER_JWT_ENABLED] = settings.webServerJwtEnabled
            preferences[WEB_SERVER_ACCESS_PASSWORD] = settings.webServerAccessPassword
            preferences[WEB_SERVER_LOCALHOST_ONLY] = settings.webServerLocalhostOnly
            preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
            preferences[LAUNCH_COUNT] = settings.launchCount
            preferences[WORKSPACE_FILES_STORAGE] = settings.workspaceFilesStorage.name
            preferences[SPONSOR_ALERT_DISMISSED_AT] = settings.sponsorAlertDismissedAt
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    suspend fun updateCompressionPreferences(targetTokens: Int, keepRecentMessages: Int) {
        dataStore.edit { preferences ->
            preferences.writeCompressionPreferences(
                targetTokens = targetTokens,
                keepRecentMessages = keepRecentMessages,
            )
        }
    }

    suspend fun updateAssistant(assistantId: Uuid): Boolean {
        val fallbackAssistants = settingsFlow.value.assistants
        var selected = false
        dataStore.edit { preferences ->
            selected = preferences.selectActiveAssistant(
                assistantId = assistantId,
                fallbackAssistants = fallbackAssistants,
            )
        }
        return selected
    }

    suspend fun setAssistantArchived(
        assistantId: Uuid,
        archived: Boolean,
    ): AssistantArchiveResult {
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
        return result
    }

    suspend fun updateAssistantConfig(assistant: Assistant) {
        val fallbackAssistants = settingsFlow.value.assistants
        dataStore.edit { preferences ->
            preferences.writeAssistantConfig(
                assistant = assistant,
                fallbackAssistants = fallbackAssistants,
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
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
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

internal fun MutablePreferences.writeCompressionPreferences(
    targetTokens: Int,
    keepRecentMessages: Int,
) {
    this[SettingsStore.COMPRESS_TARGET_TOKENS] = targetTokens
    this[SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES] = keepRecentMessages
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
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val requestLoggingEnabled: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
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
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    @Transient
    val presetsStoreExists: Boolean = false,
    val presets: List<Preset> = emptyList(),
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
    val workspaceFilesStorage: WorkspaceFilesStorage = WorkspaceFilesStorage.PRIVATE,
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
    val showUpdates: Boolean = true,
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
            - Time: {{cur_datetime}}
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

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)

private val DEFAULT_PRESET_ID = Uuid.parse("a9433f62-d8e9-4a38-8fb8-9c3f63f7f1b0")

private fun List<Preset>.withDefaultPreset(
    modeInjections: List<PromptInjection.ModeInjection>,
    shouldCreateDefault: Boolean,
): List<Preset> {
    if (!shouldCreateDefault || modeInjections.isEmpty() || any { it.id == DEFAULT_PRESET_ID }) return this
    return listOf(
        Preset(
            id = DEFAULT_PRESET_ID,
            name = "Default Preset",
            description = "Contains existing quick injections.",
            modeInjectionIds = modeInjections.map { it.id }.toSet(),
        )
    ) + this
}
