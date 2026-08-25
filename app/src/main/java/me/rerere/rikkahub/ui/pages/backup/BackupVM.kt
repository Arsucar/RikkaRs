package me.rerere.rikkahub.ui.pages.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.sync.importer.ChatboxImporter
import me.rerere.rikkahub.data.sync.importer.CherryStudioProviderImporter
import me.rerere.rikkahub.data.sync.BackupOperation
import me.rerere.rikkahub.data.sync.BackupTaskCoordinator
import me.rerere.rikkahub.data.sync.BackupTaskStage
import me.rerere.rikkahub.data.sync.BackupTaskState
import me.rerere.rikkahub.data.sync.copyToCancellable
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.S3Sync
import me.rerere.rikkahub.utils.UiState
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "BackupVM"

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webDavSync: WebDavSync,
    private val s3Sync: S3Sync,
    private val conversationRepository: ConversationRepository,
    private val context: Context,
    private val taskCoordinator: BackupTaskCoordinator,
) : ViewModel() {
    val taskStates = taskCoordinator.states

    private val _activeWebDavRestoreHref = MutableStateFlow<String?>(null)
    val activeWebDavRestoreHref: StateFlow<String?> = _activeWebDavRestoreHref.asStateFlow()

    private val _activeS3RestoreKey = MutableStateFlow<String?>(null)
    val activeS3RestoreKey: StateFlow<String?> = _activeS3RestoreKey.asStateFlow()

    fun consumeTaskSuccess(operation: BackupOperation): Boolean =
        taskCoordinator.consumeSuccess(operation)

    // #186: consume a terminal state (Success/Failed/Cancelled) once for one-shot UI feedback.
    fun consumeTaskTerminal(operation: BackupOperation): BackupTaskState? =
        taskCoordinator.consumeTerminal(operation)

    fun cancelTask(operation: BackupOperation): Boolean = taskCoordinator.cancel(operation)

    val settings = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Settings.dummy()
    )

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)
    val s3BackupItems = MutableStateFlow<UiState<List<S3BackupItem>>>(UiState.Idle)
    val localBackupItems = MutableStateFlow(WebDavConfig.BackupItem.entries.toList())

    init {
        loadBackupFileItems()
        loadS3BackupFileItems()
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    @Deprecated(
        message = "使用 transform 重载避免读快照-全量写竞态 (#267)",
        replaceWith = ReplaceWith("updateSettings { it.copy(...) }"),
    )
    fun updateSettings(settings: Settings) = updateSettings { settings }

    fun updateLocalBackupItems(items: List<WebDavConfig.BackupItem>) {
        localBackupItems.value = items
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            runCatching {
                webDavBackupItems.emit(UiState.Loading)
                webDavBackupItems.emit(
                    value = UiState.Success(
                        data = webDavSync.listBackupFiles(
                            config = settings.value.webDavConfig
                        ).sortedByDescending { it.lastModified }
                    )
                )
            }.onFailure {
                webDavBackupItems.emit(UiState.Error(it))
            }
        }
    }

    // #186: run test connection on the coordinator scope so it survives page navigation and reports
    // its result through taskStates (WEB_DAV_TEST) instead of the composition-scoped coroutine.
    fun testWebDav(): Boolean = taskCoordinator.start(
        operation = BackupOperation.WEB_DAV_TEST,
        initialStage = BackupTaskStage.TRANSFERRING,
    ) {
        webDavSync.testConnection(settings.value.webDavConfig)
    }

    suspend fun backup() {
        webDavSync.backup(settings.value.webDavConfig)
        recordBackupTime()
    }

    fun startWebDavBackup(): Boolean = taskCoordinator.start(BackupOperation.WEB_DAV_BACKUP) {
        webDavSync.backup(settings.value.webDavConfig) { updateStage(it) }
        recordBackupTime()
        loadBackupFileItems()
    }

    fun startWebDavRestore(item: WebDavBackupItem): Boolean {
        _activeWebDavRestoreHref.value = item.href
        val started = taskCoordinator.start(
            operation = BackupOperation.WEB_DAV_RESTORE,
            initialStage = BackupTaskStage.TRANSFERRING,
        ) {
            try {
                webDavSync.restore(
                    config = settings.value.webDavConfig,
                    item = item,
                    onStage = { updateStage(it) },
                )
            } finally {
                if (_activeWebDavRestoreHref.value == item.href) {
                    _activeWebDavRestoreHref.value = null
                }
            }
        }
        if (!started && _activeWebDavRestoreHref.value == item.href) {
            _activeWebDavRestoreHref.value = null
        }
        return started
    }

    suspend fun restore(item: WebDavBackupItem) {
        webDavSync.restore(config = settings.value.webDavConfig, item = item)
    }

    // #186: delete remote backup on the coordinator scope so an in-flight delete is not cancelled by
    // leaving the page; on success reload the list. Result is surfaced through taskStates (WEB_DAV_DELETE).
    fun deleteWebDavBackupFile(item: WebDavBackupItem): Boolean = taskCoordinator.start(
        operation = BackupOperation.WEB_DAV_DELETE,
        initialStage = BackupTaskStage.TRANSFERRING,
    ) {
        webDavSync.deleteBackupFile(settings.value.webDavConfig, item)
        loadBackupFileItems()
    }

    suspend fun exportToFile(): File {
        val file = webDavSync.prepareBackupFile(
            settings.value.webDavConfig.copy(items = localBackupItems.value)
        )
        recordBackupTime()
        return file
    }

    fun startLocalExport(targetUri: Uri): Boolean = taskCoordinator.start(BackupOperation.LOCAL_EXPORT) {
        val exportFile = webDavSync.prepareBackupFile(
            settings.value.webDavConfig.copy(items = localBackupItems.value)
        )
        try {
            updateStage(BackupTaskStage.WRITING)
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(targetUri)
                    ?: error("Unable to open the selected export destination")
                output.use { outputStream ->
                    FileInputStream(exportFile).use { inputStream ->
                        inputStream.copyToCancellable(outputStream)
                    }
                }
            }
            recordBackupTime()
        } finally {
            exportFile.delete()
        }
    }

    fun startLocalImport(sourceUri: Uri, importType: String): Boolean =
        taskCoordinator.start(
            operation = BackupOperation.LOCAL_IMPORT,
            initialStage = BackupTaskStage.TRANSFERRING,
        ) {
            val extension = if (importType == "chatbox") "json" else "zip"
            val tempFile = File.createTempFile("temp_${importType}_", ".$extension", context.cacheDir)
            try {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(sourceUri)
                        ?: error("Unable to open the selected import file")
                    input.use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyToCancellable(outputStream)
                        }
                    }
                }
                updateStage(BackupTaskStage.RESTORING)
                when (importType) {
                    "local" -> restoreFromLocalFile(tempFile)
                    "chatbox" -> restoreFromChatBox(tempFile)
                    "cherry" -> restoreFromCherryStudio(tempFile)
                    else -> error("Unsupported import type: $importType")
                }
            } finally {
                tempFile.delete()
            }
        }

    suspend fun restoreFromLocalFile(file: File) {
        webDavSync.restoreFromLocalFile(
            file,
            settings.value.webDavConfig.copy(items = localBackupItems.value),
        )
    }

    suspend fun restoreFromChatBox(file: File): ChatboxRestoreResult {
        var importedConversations = 0
        var skippedExistingConversations = 0
        val result = ChatboxImporter.importStreaming(
            file = file,
            assistantId = settings.value.assistantId,
            providers = settings.value.providers,
            onConversation = { conversation ->
                if (conversationRepository.existsConversationById(conversation.id)) {
                    skippedExistingConversations++
                } else {
                    conversationRepository.insertConversation(conversation)
                    importedConversations++
                }
            }
        )

        val targetAssistantId = settings.value.assistantId
        settingsStore.update(
            settings.value.copy(
                providers = result.providers + settings.value.providers,
                assistants = settings.value.assistants.map { assistant ->
                    if (result.hasConversationSystemPrompt && assistant.id == targetAssistantId) {
                        assistant.copy(allowConversationSystemPrompt = true)
                    } else {
                        assistant
                    }
                }
            )
        )

        Log.i(
            TAG,
            "restoreFromChatBox: import ${result.providers.size} providers, " +
                "$importedConversations conversations, skip $skippedExistingConversations existing, " +
                "drop ${result.skippedImageParts} images"
        )
        return ChatboxRestoreResult(
            importedProviders = result.providers.size,
            importedConversations = importedConversations,
            skippedExistingConversations = skippedExistingConversations,
            skippedImageParts = result.skippedImageParts,
            skippedEmptyMessages = result.skippedEmptyMessages,
        )
    }

    suspend fun restoreFromCherryStudio(file: File) {
        val importProviders = CherryStudioProviderImporter.importProviders(file)

        if (importProviders.isEmpty()) {
            throw IllegalArgumentException("No importable providers found in Cherry Studio backup")
        }

        Log.i(TAG, "restoreFromCherryStudio: import ${importProviders.size} providers")

        settingsStore.update(
            settings.value.copy(
                providers = importProviders + settings.value.providers,
            )
        )
    }

    // S3 Backup methods
    fun loadS3BackupFileItems() {
        viewModelScope.launch {
            runCatching {
                s3BackupItems.emit(UiState.Loading)
                s3BackupItems.emit(
                    value = UiState.Success(
                        data = s3Sync.listBackupFiles(
                            config = settings.value.s3Config
                        )
                    )
                )
            }.onFailure {
                s3BackupItems.emit(UiState.Error(it))
            }
        }
    }

    // #186: run test connection on the coordinator scope so it survives page navigation and reports
    // its result through taskStates (S3_TEST) instead of the composition-scoped coroutine.
    fun testS3(): Boolean = taskCoordinator.start(
        operation = BackupOperation.S3_TEST,
        initialStage = BackupTaskStage.TRANSFERRING,
    ) {
        s3Sync.testS3(settings.value.s3Config)
    }

    suspend fun backupToS3() {
        s3Sync.backupToS3(settings.value.s3Config)
        recordBackupTime()
    }

    fun startS3Backup(): Boolean = taskCoordinator.start(BackupOperation.S3_BACKUP) {
        s3Sync.backupToS3(settings.value.s3Config) { updateStage(it) }
        recordBackupTime()
        loadS3BackupFileItems()
    }

    fun startS3Restore(item: S3BackupItem): Boolean {
        _activeS3RestoreKey.value = item.key
        val started = taskCoordinator.start(
            operation = BackupOperation.S3_RESTORE,
            initialStage = BackupTaskStage.TRANSFERRING,
        ) {
            try {
                s3Sync.restoreFromS3(
                    config = settings.value.s3Config,
                    item = item,
                    onStage = { updateStage(it) },
                )
            } finally {
                if (_activeS3RestoreKey.value == item.key) {
                    _activeS3RestoreKey.value = null
                }
            }
        }
        if (!started && _activeS3RestoreKey.value == item.key) {
            _activeS3RestoreKey.value = null
        }
        return started
    }

    suspend fun restoreFromS3(item: S3BackupItem) {
        s3Sync.restoreFromS3(config = settings.value.s3Config, item = item)
    }

    // #186: delete remote S3 backup on the coordinator scope so an in-flight delete is not cancelled by
    // leaving the page; on success reload the list. Result is surfaced through taskStates (S3_DELETE).
    fun deleteS3BackupFile(item: S3BackupItem): Boolean = taskCoordinator.start(
        operation = BackupOperation.S3_DELETE,
        initialStage = BackupTaskStage.TRANSFERRING,
    ) {
        s3Sync.deleteS3BackupFile(settings.value.s3Config, item)
        loadS3BackupFileItems()
    }

    private suspend fun recordBackupTime() {
        settingsStore.update { settings ->
            settings.copy(
                backupReminderConfig = settings.backupReminderConfig.copy(
                    lastBackupTime = System.currentTimeMillis()
                )
            )
        }
    }
}

data class ChatboxRestoreResult(
    val importedProviders: Int,
    val importedConversations: Int,
    val skippedExistingConversations: Int,
    val skippedImageParts: Int,
    val skippedEmptyMessages: Int,
)
