package me.rerere.rikkahub.di

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.http.AcceptLanguageBuilder
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import me.rerere.rikkahub.data.ai.RequestLoggingInterceptor
import me.rerere.rikkahub.data.ai.clash.ClashApiClient
import me.rerere.rikkahub.data.ai.clash.ClashRetryTracer
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.GenerationLoop
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.api.RikkaHubAPI
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.fts.SimpleDictManager
import me.rerere.rikkahub.data.db.migrations.Migration_6_7
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.db.migrations.Migration_24_25
import me.rerere.rikkahub.data.db.migrations.Migration_25_26
import me.rerere.rikkahub.data.db.migrations.Migration_26_27
import me.rerere.rikkahub.data.db.migrations.Migration_27_28
import me.rerere.rikkahub.data.db.migrations.Migration_28_29
import me.rerere.rikkahub.data.db.migrations.Migration_29_30
import me.rerere.rikkahub.data.db.migrations.Migration_30_31
import me.rerere.rikkahub.data.db.migrations.Migration_31_32
import me.rerere.rikkahub.data.db.migrations.Migration_32_33
import me.rerere.rikkahub.data.db.migrations.Migration_33_34
import me.rerere.rikkahub.data.db.migrations.Migration_34_35
import me.rerere.rikkahub.data.db.migrations.Migration_35_36
import me.rerere.rikkahub.data.db.migrations.Migration_36_37
import me.rerere.rikkahub.data.db.migrations.Migration_37_38
import me.rerere.rikkahub.data.db.migrations.Migration_38_39
import me.rerere.rikkahub.data.db.migrations.Migration_42_43
import me.rerere.rikkahub.data.db.migrations.Migration_43_44
import me.rerere.rikkahub.data.db.migrations.Migration_44_45
import me.rerere.rikkahub.data.db.migrations.Migration_45_46
import me.rerere.rikkahub.data.db.migrations.Migration_46_47
import me.rerere.rikkahub.data.db.migrations.Migration_47_48
import me.rerere.rikkahub.data.db.migrations.Migration_48_49
import me.rerere.rikkahub.data.db.migrations.Migration_49_50
import me.rerere.rikkahub.data.db.migrations.Migration_50_51
import me.rerere.rikkahub.data.db.migrations.Migration_51_52
import me.rerere.rikkahub.data.db.migrations.Migration_52_53
import me.rerere.rikkahub.data.ai.ApiCallRecorder
import me.rerere.rikkahub.data.ai.transformers.SemanticMemoryTransformer
import me.rerere.rikkahub.data.memory.semantic.EmbeddingService
import me.rerere.rikkahub.data.memory.semantic.MemorySummarizer
import me.rerere.rikkahub.data.memory.semantic.RecallService
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryManager
import me.rerere.rikkahub.data.memory.semantic.SemanticMemoryRepository
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.network.SettingsProxySelector
import me.rerere.rikkahub.data.network.SettingsProxyAuthenticator
import me.rerere.rikkahub.data.network.SettingsSocks5Authenticator
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.data.sync.BackupArchive
import me.rerere.rikkahub.data.sync.BackupRestorer
import me.rerere.search.SearchService
import me.rerere.rikkahub.data.sync.S3Sync
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

val dataSourceModule = module {
    single {
        SettingsStore(context = get(), scope = get())
    }

    single {
        val context: Context = get()
        Room.databaseBuilder(context, AppDatabase::class.java, "rikka_hub")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                Migration_6_7,
                Migration_11_12,
                Migration_13_14,
                Migration_14_15,
                Migration_15_16,
                Migration_24_25,
                Migration_25_26,
                Migration_26_27,
                Migration_27_28,
                Migration_28_29,
                Migration_29_30,
                Migration_30_31,
                Migration_31_32,
                Migration_32_33,
                Migration_33_34,
                Migration_34_35,
                Migration_35_36,
                Migration_36_37,
                Migration_37_38,
                Migration_38_39,
                Migration_42_43,
                Migration_43_44,
                Migration_44_45,
                Migration_45_46,
                Migration_46_47,
                Migration_47_48,
                Migration_48_49,
                Migration_49_50,
                Migration_50_51,
                Migration_51_52,
                Migration_52_53,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    val dictDir = SimpleDictManager.extractDict(context)
                    val cursor = db.query("SELECT jieba_dict(?)", arrayOf(dictDir.absolutePath))
                    cursor.use {
                        if (it.moveToFirst()) {
                            val result = it.getString(0)
                            val success = result?.trimEnd('/') == dictDir.absolutePath.trimEnd('/')
                            if (!success) {
                                android.util.Log.e(
                                    "DataSourceModule",
                                    "jieba_dict failed: $result, path=${dictDir.absolutePath}"
                                )
                            }
                        }
                    }
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                            text,
                            node_id UNINDEXED,
                            message_id UNINDEXED,
                            conversation_id UNINDEXED,
                            title UNINDEXED,
                            update_at UNINDEXED,
                            tokenize = 'simple'
                        )
                        """.trimIndent()
                    )
                    val interruptedAt = System.currentTimeMillis()
                    db.execSQL(
                        """
                        UPDATE hook_executions SET
                            status = 'INTERRUPTED',
                            ended_at = ?,
                            duration_ms = CASE
                                WHEN started_at IS NULL THEN NULL
                                ELSE MAX(0, ? - started_at)
                            END,
                            lease_token = lease_token + 1
                        WHERE status IN ('QUEUED', 'RUNNING')
                        """.trimIndent(),
                        arrayOf(interruptedAt, interruptedAt),
                    )
                    db.execSQL(
                        """
                        UPDATE generation_logical_turns SET
                            status = 'INTERRUPTED',
                            updated_at = ?,
                            completed_at = ?
                        WHERE status IN ('ACTIVE', 'WAITING_FOR_TOOL')
                        """.trimIndent(),
                        arrayOf(interruptedAt, interruptedAt),
                    )
                    db.execSQL(
                        """
                        UPDATE hook_runs SET
                            status = CASE
                                WHEN EXISTS (
                                    SELECT 1 FROM hook_executions e
                                    WHERE e.run_id = hook_runs.run_id AND e.status = 'FAILED'
                                ) THEN 'FAILED'
                                WHEN EXISTS (
                                    SELECT 1 FROM hook_executions e
                                    WHERE e.run_id = hook_runs.run_id AND e.status = 'INTERRUPTED'
                                ) THEN 'INTERRUPTED'
                                WHEN EXISTS (
                                    SELECT 1 FROM hook_executions e
                                    WHERE e.run_id = hook_runs.run_id AND e.status = 'CANCELLED'
                                ) THEN 'CANCELLED'
                                WHEN EXISTS (
                                    SELECT 1 FROM hook_executions e
                                    WHERE e.run_id = hook_runs.run_id AND e.status = 'SUCCESS'
                                ) THEN 'SUCCESS'
                                ELSE 'SKIPPED'
                            END,
                            failure_count = (
                                SELECT COUNT(*) FROM hook_executions e
                                WHERE e.run_id = hook_runs.run_id AND e.status = 'FAILED'
                            ),
                            ended_at = ?
                        WHERE status IN ('QUEUED', 'RUNNING')
                        """.trimIndent(),
                        arrayOf(interruptedAt),
                    )
                }
            })
            .openHelperFactory(
                RequerySQLiteOpenHelperFactory(
                    listOf(
                RequerySQLiteOpenHelperFactory.ConfigurationOptions { options ->
                    options.customExtensions.add(
                        SQLiteCustomExtension(
                            context.applicationInfo.nativeLibraryDir + "/libsimple",
                            null
                        )
                    )
                    options
                }
            )))
            .build()
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().conversationTagDao()
    }

    single {
        get<AppDatabase>().hookDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().memoryTableDao()
    }

    single {
        get<AppDatabase>().memoryTableSnapshotDao()
    }

    single {
        get<AppDatabase>().subagentContextDao()
    }

    single<me.rerere.rikkahub.data.ai.subagent.SubagentContextStore> {
        me.rerere.rikkahub.data.ai.subagent.RoomSubagentContextStore(get())
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().messageNodeDao()
    }

    single {
        get<AppDatabase>().messageStatsDao()
    }

    single {
        get<AppDatabase>().apiCallRecordDao()
    }

    single {
        ApiCallRecorder(dao = get())
    }

    // [SemanticMemory Plugin] DI
    single {
        get<AppDatabase>().episodicMemoryDao()
    }
    single {
        SemanticMemoryRepository(dao = get())
    }
    single {
        EmbeddingService(providerManager = get())
    }
    single {
        RecallService(repository = get(), embeddingService = get())
    }
    single {
        SemanticMemoryTransformer(recallService = get())
    }
    single {
        MemorySummarizer(
            providerManager = get(),
            embeddingService = get(),
            repository = get(),
            recallService = get(),
            json = get(),
        )
    }
    single {
        SemanticMemoryManager(
            summarizer = get(),
            repository = get(),
            embeddingService = get(),
            recallService = get(),
            oldMemoryRepository = get(),
            settingsStore = get(),
            json = get(),
        )
    }

    single {
        get<AppDatabase>().managedFileDao()
    }

    single {
        get<AppDatabase>().favoriteDao()
    }

    single {
        get<AppDatabase>().workspaceDao()
    }

    single {
        get<AppDatabase>().folderDao()
    }

    single {
        MessageFtsManager(get())
    }

    single { McpManager(settingsStore = get(), appScope = get(), filesManager = get()) }

    single {
        GenerationLoop(
            context = get(),
            providerManager = get(),
            json = get(),
            apiCallRecorder = get(),
            memoryRepo = get()
        )
    }

    single {
        val memoryTableRepository = get<me.rerere.rikkahub.data.repository.MemoryTableRepository>()
        val conversationRepository = get<me.rerere.rikkahub.data.repository.ConversationRepository>()
        me.rerere.rikkahub.data.ai.subagent.SubagentHost(
            generationHandler = get(),
            contextCache = me.rerere.rikkahub.data.ai.subagent.SubagentContextCache(store = get()),
            memoryTableInjectionLoader = { parentAssistant, conversationId, selectedDocumentIds, settings ->
                val parentEnabled = settings.enableMemoryTable && parentAssistant.enableMemoryTable
                if (!parentEnabled || selectedDocumentIds.isEmpty()) {
                    me.rerere.rikkahub.data.ai.subagent.SubagentMemoryTableInjectLoad()
                } else {
                    val assistantId = parentAssistant.id.toString()
                    val isolation = if (conversationId != null) {
                        conversationRepository.getConversationById(conversationId)?.memoryTableIsolation == true
                    } else {
                        false
                    }
                    val templates = memoryTableRepository.getEffectiveTemplates(assistantId)
                    val documents = memoryTableRepository.getEffectiveDocuments(
                        assistantId = assistantId,
                        conversationId = conversationId?.toString(),
                    )
                    val labels = me.rerere.rikkahub.data.ai.subagent.buildSubagentMemoryTableLabels(
                        selectedDocumentIds = selectedDocumentIds,
                        templates = templates,
                        documents = documents,
                    )
                    val (resolvedTemplates, resolvedDocuments) =
                        me.rerere.rikkahub.data.ai.subagent.resolveSubagentMemoryTableInjection(
                            selectedDocumentIds = selectedDocumentIds,
                            templates = templates,
                            documents = documents,
                            parentMemoryTableEnabled = true,
                            memoryTableIsolation = isolation,
                        )
                    val transformers = if (resolvedTemplates.isEmpty() || resolvedDocuments.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(
                            me.rerere.rikkahub.data.ai.transformers.MemoryTableInjectionTransformer(
                                templates = resolvedTemplates,
                                documents = resolvedDocuments,
                                maxDocuments = settings.memoryTableMaxInjectDocuments,
                                maxTokens = settings.memoryTableMaxInjectTokens,
                                maxChars = settings.memoryTableMaxInjectChars,
                            ),
                        )
                    }
                    me.rerere.rikkahub.data.ai.subagent.SubagentMemoryTableInjectLoad(
                        transformers = transformers,
                        labels = labels,
                    )
                }
            },
        )
    }

    single {
        TranslationHandler(providerManager = get())
    }

    single<OkHttpClient> {
        val settingsStore: SettingsStore = get()
        val acceptLang = AcceptLanguageBuilder.fromAndroid(get())
            .build()
        java.net.Authenticator.setDefault(SettingsSocks5Authenticator(settingsStore))
        val initialNetworkSetting = settingsStore.settingsFlow.value.networkSetting
        val appliedProxySetting = AtomicReference(
            Triple(
                initialNetworkSetting.proxyUrl,
                initialNetworkSetting.proxyUsername,
                initialNetworkSetting.proxyPassword,
            )
        )
        lateinit var client: OkHttpClient
        client = OkHttpClient.Builder()
            .proxySelector(SettingsProxySelector(settingsStore))
            .proxyAuthenticator(SettingsProxyAuthenticator(settingsStore))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val networkSetting = settingsStore.settingsFlow.value.networkSetting
                val currentProxySetting = Triple(
                    networkSetting.proxyUrl,
                    networkSetting.proxyUsername,
                    networkSetting.proxyPassword,
                )
                if (appliedProxySetting.getAndSet(currentProxySetting) != currentProxySetting) {
                    client.connectionPool.evictAll()
                }

                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)

                if (originalRequest.header(HttpHeaders.UserAgent) == null) {
                    val userAgent = settingsStore.settingsFlow.value.networkSetting.userAgent
                        .trim()
                        .ifEmpty { "RikkaHub-Android/${BuildConfig.VERSION_NAME}" }
                    requestBuilder.addHeader(HttpHeaders.UserAgent, userAgent)
                }

                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val contentTypeHeader = request.header("Content-Type")
                if (
                    contentTypeHeader != null &&
                    contentTypeHeader.contains(";") &&
                    contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)
                ) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Content-Type", contentTypeHeader.substringBefore(";").trim())
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .addInterceptor(
                AIRequestInterceptor(
                    settingsStore = get(),
                    clashApiClient = get(),
                    clashRetryTracer = get(),
                )
            )
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                redactHeader("Authorization")
                redactHeader("Proxy-Authorization")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
                redactHeader("x-api-key")
                redactHeader("api-key")
                redactHeader("x-goog-api-key")
                redactHeader("anthropic-api-key")
            })
            .build()
        client.also { SearchService.init(it, get()) }
    }

    single {
        SponsorAPI.create(get())
    }

    single {
        ProviderManager(client = get(), context = get())
    }

    single { BackupManager(context = get(), database = get(), settingsStore = get(), json = get()) }

    single {
        BackupArchive(
            settingsStore = get(),
            json = get(),
            context = get(),
            database = get(),
        )
    }

    single {
        BackupRestorer(
            context = get(),
            json = get(),
            settingsStore = get(),
        )
    }

    single {
        WebDavSync(
            backupManager = get(),
            context = get(),
            httpClient = get(),
            backupArchive = get(),
            backupRestorer = get(),
        )
    }

    single<ClashApiClient> {
        ClashApiClient(json = get())
    }

    single<ClashRetryTracer> {
        ClashRetryTracer()
    }

    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.MINUTES)
                    writeTimeout(120, TimeUnit.SECONDS)
                    followSslRedirects(true)
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    single {
        S3Sync(
            backupManager = get(),
            context = get(),
            httpClient = get(),
            backupArchive = get(),
            backupRestorer = get(),
        )
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .addConverterFactory(get<Json>().asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    single<RikkaHubAPI> {
        get<Retrofit>().create(RikkaHubAPI::class.java)
    }
}
