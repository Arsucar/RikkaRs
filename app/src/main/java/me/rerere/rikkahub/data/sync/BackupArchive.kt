package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.DatabaseForeignKeyIntegrityException
import me.rerere.rikkahub.data.db.openRequeryDatabase
import me.rerere.rikkahub.data.db.validateDatabaseForeignKeys
import me.rerere.rikkahub.data.db.validateDatabaseIntegrity
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "BackupArchive"
private const val COPY_BUFFER_SIZE = 64 * 1024

data class BackupArchiveOptions(
    val includeDatabase: Boolean,
    val includeFiles: Boolean,
)

class BackupArchive(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: Context,
    private val database: AppDatabase,
) {
    suspend fun create(options: BackupArchiveOptions): File = withContext(Dispatchers.IO) {
        // #184 defect 4: never back up the placeholder `Settings.dummy()` (init=true) that the
        // settings flow holds before the real preferences finish loading. Persisting it would ship a
        // blank configuration inside the archive.
        val settings = settingsStore.settingsFlow.value
        check(!settings.init) { "Cannot back up before settings are loaded" }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File.createTempFile("backup_${timestamp}_", ".zip", context.cacheDir)
        var databaseSnapshot: File? = null

        try {
            ZipOutputStream(
                BufferedOutputStream(FileOutputStream(backupFile), COPY_BUFFER_SIZE)
            ).use { zipOut ->
                zipOut.setLevel(Deflater.BEST_SPEED)
                addVirtualFileToZip(
                    zipOut = zipOut,
                    name = "settings.json",
                    content = json.encodeToString(settings),
                )

                if (options.includeDatabase) {
                    val snapshot = createDatabaseSnapshot(database, context)
                    databaseSnapshot = snapshot
                    addFileToZip(zipOut, snapshot, "rikka_hub.db")
                }

                if (options.includeFiles) {
                    addFolderRecursive(zipOut, FileFolders.UPLOAD)
                    addSkills(zipOut)
                    addTopLevelFiles(zipOut, FileFolders.FONTS)
                }
            }

            Log.i(TAG, "Created ${backupFile.name} (${backupFile.length().fileSizeToString()})")
            backupFile
        } catch (error: Throwable) {
            backupFile.delete()
            throw error
        } finally {
            databaseSnapshot?.delete()
        }
    }

    private suspend fun addTopLevelFiles(zipOut: ZipOutputStream, folderName: String) {
        val folder = File(context.filesDir, folderName)
        if (!folder.exists()) return
        if (!folder.isDirectory) throw IOException("Backup path is not a directory: ${folder.absolutePath}")

        val files = folder.listFiles()
            ?: throw IOException("Unable to list backup directory: ${folder.absolutePath}")
        files.filter { it.isFile }
            .sortedBy { it.name }
            .forEach { file -> addFileToZip(zipOut, file, "$folderName/${file.name}") }
    }

    private suspend fun addFolderRecursive(zipOut: ZipOutputStream, folderName: String) {
        val root = File(context.filesDir, folderName)
        if (!root.exists()) return
        if (!root.isDirectory) throw IOException("Backup path is not a directory: ${root.absolutePath}")

        addDirectoryToZip(
            zipOut = zipOut,
            rootDir = root.canonicalFile,
            currentDir = root.canonicalFile,
            entryPrefix = "$folderName/",
            visitedDirectories = mutableSetOf(),
        )
    }

    private suspend fun addSkills(zipOut: ZipOutputStream) {
        addFolderRecursive(zipOut, FileFolders.SKILLS)
    }

    private suspend fun addDirectoryToZip(
        zipOut: ZipOutputStream,
        rootDir: File,
        currentDir: File,
        entryPrefix: String,
        visitedDirectories: MutableSet<String>,
    ) {
        currentCoroutineContext().ensureActive()
        val canonicalDirectory = currentDir.canonicalFile
        ensureContained(rootDir, canonicalDirectory)
        if (!visitedDirectories.add(canonicalDirectory.path)) {
            throw IOException("Directory cycle detected: ${canonicalDirectory.absolutePath}")
        }

        val files = canonicalDirectory.listFiles()
            ?: throw IOException("Unable to list backup directory: ${canonicalDirectory.absolutePath}")
        files.sortedBy { it.name }.forEach { file ->
            val canonicalFile = file.canonicalFile
            ensureContained(rootDir, canonicalFile)
            when {
                canonicalFile.isDirectory -> addDirectoryToZip(
                    zipOut = zipOut,
                    rootDir = rootDir,
                    currentDir = canonicalFile,
                    entryPrefix = entryPrefix,
                    visitedDirectories = visitedDirectories,
                )

                canonicalFile.isFile -> {
                    val relativePath = canonicalFile.relativeTo(rootDir).invariantSeparatorsPath
                    addFileToZip(zipOut, canonicalFile, "$entryPrefix$relativePath")
                }
            }
        }
    }

    private fun ensureContained(rootDir: File, file: File) {
        val rootPath = rootDir.path.trimEnd(File.separatorChar)
        if (file.path != rootPath && !file.path.startsWith("$rootPath${File.separator}")) {
            throw IOException("Backup path escapes its root: ${file.absolutePath}")
        }
    }

    private suspend fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        currentCoroutineContext().ensureActive()
        zipOut.putNextEntry(ZipEntry(entryName))
        try {
            FileInputStream(file).use { input -> input.copyToCancellable(zipOut) }
        } finally {
            zipOut.closeEntry()
        }
        Log.d(TAG, "Added $entryName (${file.length()} bytes)")
    }

    private suspend fun addVirtualFileToZip(zipOut: ZipOutputStream, name: String, content: String) {
        zipOut.putNextEntry(ZipEntry(name))
        try {
            content.byteInputStream().use { input -> input.copyToCancellable(zipOut) }
        } finally {
            zipOut.closeEntry()
        }
    }
}

internal suspend fun createDatabaseSnapshot(database: AppDatabase, context: Context): File =
    withContext(Dispatchers.IO) {
        val snapshot = File.createTempFile("rikka_hub_snapshot_", ".db", context.cacheDir)
        check(snapshot.delete()) { "Unable to prepare database snapshot path" }

        try {
            currentCoroutineContext().ensureActive()
            database.openHelper.writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf(snapshot.absolutePath),
            )
            currentCoroutineContext().ensureActive()
            validateDatabaseSnapshot(context, snapshot)
            snapshot
        } catch (error: Throwable) {
            snapshot.delete()
            throw error
        }
    }

private fun validateDatabaseSnapshot(context: Context, snapshot: File) {
    val database = openRequeryDatabase(context, snapshot)
    try {
        validateDatabaseIntegrity(database)
        val violations = validateDatabaseForeignKeys(database)
        if (violations.isNotEmpty()) throw DatabaseForeignKeyIntegrityException(violations)
    } finally {
        database.close()
    }
}

suspend fun InputStream.copyToCancellable(
    output: OutputStream,
    bufferSize: Int = COPY_BUFFER_SIZE,
): Long {
    require(bufferSize > 0) { "bufferSize must be positive" }
    val buffer = ByteArray(bufferSize)
    var bytesCopied = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val bytesRead = read(buffer)
        if (bytesRead < 0) break
        output.write(buffer, 0, bytesRead)
        bytesCopied += bytesRead
    }
    return bytesCopied
}
