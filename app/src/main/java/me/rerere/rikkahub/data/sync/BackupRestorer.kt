package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.db.validateDatabaseIntegrity
import me.rerere.rikkahub.data.db.validateRestoredDatabaseForeignKeys
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.SkillPaths
import me.rerere.rikkahub.data.files.resolveContainedFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val TAG = "BackupRestorer"
private const val DEFAULT_DB_NAME = "rikka_hub"
// The zip entry names are fixed by the backup format regardless of the on-disk database name.
private const val DB_FILE = "rikka_hub.db"
private const val DB_WAL = "rikka_hub-wal"
private const val DB_SHM = "rikka_hub-shm"
private const val BAK_SUFFIX = ".restore-bak"
private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
private const val MAX_ENTRY_UNCOMPRESSED_BYTES = 200L * 1024 * 1024
private const val MAX_ZIP_ENTRY_COUNT = 50_000
private const val ZIP_COPY_BUFFER_SIZE = 64 * 1024

/**
 * #184: Shared, atomic restore used by both [me.rerere.rikkahub.data.sync.webdav.WebDavSync] and
 * [me.rerere.rikkahub.data.sync.S3Sync], replacing their near-identical `restoreFromBackupFile`.
 *
 * Guarantees:
 * - The restored database is unpacked to a temporary directory and validated (integrity +
 *   foreign keys) there. Only after it passes is the live database replaced via an atomic move
 *   with a `.restore-bak` fallback, so a mid-restore failure leaves the live database untouched
 *   (fixes #184 缺陷 1、2).
 * - Settings are decoded/migrated up front but applied only after the database swap succeeds, so
 *   the persisted state is never "new settings + old database" (fixes #184 缺陷 3).
 * - `includeDatabase` / `includeFiles` gate database and file payloads respectively, honouring the
 *   backup `config.items` switches (fixes #184 缺陷 3 的开关校验).
 *
 * File payloads (upload/skills/fonts) keep the existing per-file overwrite + path-containment
 * semantics; they are not part of the atomic database/settings unit.
 */
class BackupRestorer(
    private val context: Context,
    private val json: Json,
    private val settingsStore: SettingsStore,
    // Injectable so instrumentation tests can target an isolated database instead of the live one.
    private val databaseName: String = DEFAULT_DB_NAME,
) {
    suspend fun restore(
        backupFile: File,
        includeDatabase: Boolean,
        includeFiles: Boolean,
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "restore: start (db=$includeDatabase, files=$includeFiles) from ${backupFile.absolutePath}")

        val databaseDir = context.getDatabasePath(databaseName).parentFile
            ?: throw IllegalStateException("Cannot resolve database directory")
        val workDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}_${System.nanoTime()}")
        if (workDir.exists()) workDir.deleteRecursively()
        if (!workDir.mkdirs()) throw IllegalStateException("Unable to create restore work directory")

        var pendingSettings: Settings? = null
        var tempDb: File? = null
        var tempWal: File? = null
        var tempShm: File? = null
        var entryCount = 0
        var totalUncompressedBytes = 0L

        try {
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    val zipEntry = entry ?: continue
                    currentCoroutineContext().ensureActive()
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                        throw IOException("Backup zip exceeds max entry count ($MAX_ZIP_ENTRY_COUNT)")
                    }
                    validateZipEntryName(zipEntry.name)
                    val declaredSize = zipEntry.size
                    if (declaredSize >= 0) {
                        if (declaredSize > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                            throw IOException(
                                "Backup zip entry exceeds max size (${zipEntry.name}: $declaredSize bytes)"
                            )
                        }
                        if (totalUncompressedBytes + declaredSize > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw IOException(
                                "Backup zip exceeds max total uncompressed size ($MAX_TOTAL_UNCOMPRESSED_BYTES bytes)"
                            )
                        }
                    }
                    Log.i(TAG, "restore: processing entry ${zipEntry.name}")

                    when (zipEntry.name) {
                        "settings.json" -> {
                            // Decode + migrate now, but do NOT apply until the database swap succeeds.
                            val rawBytes = zipIn.readBounded(MAX_ENTRY_UNCOMPRESSED_BYTES)
                            totalUncompressedBytes += rawBytes.size.toLong()
                            if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw IOException(
                                    "Backup zip exceeds max total uncompressed size ($MAX_TOTAL_UNCOMPRESSED_BYTES bytes)"
                                )
                            }
                            val raw = rawBytes.toString(Charsets.UTF_8)
                            pendingSettings = try {
                                json.decodeFromString<Settings>(SettingsJsonMigrator.migrate(raw))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                throw IllegalStateException("Failed to parse settings: ${e.message}", e)
                            }
                        }

                        DB_FILE, DB_WAL, DB_SHM -> {
                            if (includeDatabase) {
                                val target = File(workDir, zipEntry.name)
                                val written = FileOutputStream(target).use {
                                    zipIn.copyToCancellableLimited(it, MAX_ENTRY_UNCOMPRESSED_BYTES)
                                }
                                totalUncompressedBytes += written
                                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    throw IOException(
                                        "Backup zip exceeds max total uncompressed size ($MAX_TOTAL_UNCOMPRESSED_BYTES bytes)"
                                    )
                                }
                                when (zipEntry.name) {
                                    DB_FILE -> tempDb = target
                                    DB_WAL -> tempWal = target
                                    DB_SHM -> tempShm = target
                                }
                                Log.i(TAG, "restore: staged ${zipEntry.name} (${target.length()} bytes)")
                            }
                        }

                        else -> if (includeFiles) {
                            totalUncompressedBytes += restoreFileEntry(zipIn, zipEntry.name)
                            if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw IOException(
                                    "Backup zip exceeds max total uncompressed size ($MAX_TOTAL_UNCOMPRESSED_BYTES bytes)"
                                )
                            }
                        }
                    }

                    zipIn.closeEntry()
                }
            }

            // Validate the restored database on the temporary copy BEFORE touching the live one.
            val stagedDb = tempDb
            val newSettings = pendingSettings
            if (includeDatabase && stagedDb != null) {
                validateDatabaseIntegrity(stagedDb, context)
                validateRestoredDatabaseForeignKeys(stagedDb, context)
                Log.i(TAG, "restore: staged database passed integrity + foreign-key checks")

                // #190: make the database swap the FINAL commit point so a failure never leaves the
                // persisted state as "new settings + old database". Apply settings first (DataStore
                // edit is transactional, so update() throwing leaves the DB untouched → still fully
                // old). Snapshot the previous settings so a swap failure can roll them back too,
                // mirroring the .restore-bak rollback the swap already performs on the database.
                val previousSettings = settingsStore.settingsFlow.value.takeIf { !it.init }
                newSettings?.let {
                    settingsStore.update(it)
                    Log.i(TAG, "restore: settings applied (pre-swap)")
                }
                try {
                    swapDatabaseAtomically(databaseDir, stagedDb, tempWal, tempShm)
                    Log.i(TAG, "restore: database swapped in atomically")
                } catch (error: Throwable) {
                    // The swap already restored the live database from its .restore-bak snapshot;
                    // roll settings back too so the persisted state stays old-settings + old-database.
                    if (newSettings != null && previousSettings != null) {
                        runCatching { settingsStore.update(previousSettings) }
                            .onFailure { Log.e(TAG, "restore: failed to roll back settings after swap failure", it) }
                    }
                    throw error
                }
            } else {
                // No database payload: settings stand on their own, there is no swap to coordinate with.
                newSettings?.let {
                    settingsStore.update(it)
                    Log.i(TAG, "restore: settings applied (no database payload)")
                }
            }

            Log.i(TAG, "restore: completed successfully")
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Backs up the live database triple, moves the validated staged files into place, and rolls the
     * originals back if any move fails. The `.db` file is moved first because it is the file the app
     * reopens on restart; stale wal/shm not present in the backup are removed so they cannot be
     * replayed onto the restored database.
     */
    private fun swapDatabaseAtomically(
        databaseDir: File,
        stagedDb: File,
        stagedWal: File?,
        stagedShm: File?,
    ) {
        // The on-disk names come from the Room database name (e.g. `rikka_hub`, `rikka_hub-wal`),
        // NOT the zip entry names (`rikka_hub.db`). getDatabasePath(databaseName) has no `.db` suffix,
        // so the live db file the app reopens on restart must be `databaseName`, not `DB_FILE`.
        val liveDb = File(databaseDir, databaseName)
        val liveWal = File(databaseDir, "$databaseName-wal")
        val liveShm = File(databaseDir, "$databaseName-shm")
        val bakDb = File(databaseDir, "$databaseName$BAK_SUFFIX")
        val bakWal = File(databaseDir, "$databaseName-wal$BAK_SUFFIX")
        val bakShm = File(databaseDir, "$databaseName-shm$BAK_SUFFIX")

        // Snapshot current live files so we can roll back on failure.
        backupLiveFile(liveDb, bakDb)
        backupLiveFile(liveWal, bakWal)
        backupLiveFile(liveShm, bakShm)

        try {
            moveInto(stagedDb, liveDb)
            if (stagedWal != null) moveInto(stagedWal, liveWal) else liveWal.delete()
            if (stagedShm != null) moveInto(stagedShm, liveShm) else liveShm.delete()
        } catch (error: Throwable) {
            Log.e(TAG, "restore: database swap failed, rolling back", error)
            rollbackLiveFile(bakDb, liveDb)
            rollbackLiveFile(bakWal, liveWal)
            rollbackLiveFile(bakShm, liveShm)
            throw error
        }

        // Swap succeeded: discard the snapshots.
        bakDb.delete()
        bakWal.delete()
        bakShm.delete()
    }

    /**
     * Move [src] onto [dest]. Prefers an atomic rename; if the source and destination live on
     * different mount points (cacheDir vs. the database dir) renameTo returns false, so a
     * copy+delete completes the move. Either way [dest] ends up holding the staged content.
     */
    private fun moveInto(src: File, dest: File) {
        dest.delete()
        if (src.renameTo(dest)) return
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        src.delete()
    }

    private fun backupLiveFile(live: File, bak: File) {
        bak.delete()
        if (live.exists() && !live.renameTo(bak)) {
            FileInputStream(live).use { input ->
                FileOutputStream(bak).use { output -> input.copyTo(output) }
            }
        }
    }

    /** Restore [live] from its snapshot [bak] on rollback, or delete [live] if there was no original. */
    private fun rollbackLiveFile(bak: File, live: File) {
        live.delete()
        if (bak.exists() && !bak.renameTo(live)) {
            FileInputStream(bak).use { input ->
                FileOutputStream(live).use { output -> input.copyTo(output) }
            }
            bak.delete()
        }
    }

    private suspend fun restoreFileEntry(zipIn: ZipInputStream, entryName: String): Long {
        return when {
            entryName.startsWith("${FileFolders.UPLOAD}/") -> {
                val fileName = entryName.substringAfter("${FileFolders.UPLOAD}/")
                if (fileName.isEmpty() || fileName.endsWith('/')) return 0L
                val uploadFolder = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() }
                val targetFile = resolveContainedFile(uploadFolder, fileName)
                    ?: throw IllegalArgumentException("Invalid backup file path: $entryName")
                targetFile.parentFile?.mkdirs()
                val written = FileOutputStream(targetFile).use {
                    zipIn.copyToCancellableLimited(it, MAX_ENTRY_UNCOMPRESSED_BYTES)
                }
                Log.i(TAG, "restore: restored $entryName (${targetFile.length()} bytes)")
                written
            }

            entryName.startsWith("${FileFolders.SKILLS}/") -> restoreSkillEntry(zipIn, entryName)

            entryName.startsWith("${FileFolders.FONTS}/") -> {
                val fileName = entryName.substringAfter("${FileFolders.FONTS}/")
                if (fileName.isEmpty() || fileName.contains('/')) return 0L
                val fontsFolder = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
                val targetFile = File(fontsFolder, fileName)
                val written = FileOutputStream(targetFile).use {
                    zipIn.copyToCancellableLimited(it, MAX_ENTRY_UNCOMPRESSED_BYTES)
                }
                Log.i(TAG, "restore: restored $entryName (${targetFile.length()} bytes)")
                written
            }

            else -> {
                Log.i(TAG, "restore: skipping entry $entryName")
                0L
            }
        }
    }

    private suspend fun restoreSkillEntry(zipIn: ZipInputStream, entryName: String): Long {
        val relativePath = entryName.substringAfter("${FileFolders.SKILLS}/")
        val skillName = relativePath.substringBefore('/', missingDelimiterValue = "")
        val skillRelativePath = relativePath.substringAfter('/', missingDelimiterValue = "")
        if (skillName.isBlank() || skillRelativePath.isBlank()) {
            Log.w(TAG, "restore: invalid skill entry $entryName")
            return 0L
        }

        val skillsRoot = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() }
        val skillDir = SkillPaths.resolveSkillDir(skillsRoot, skillName)
            ?: throw IllegalArgumentException("Invalid skill directory: $entryName")
        val targetFile = SkillPaths.resolveSkillFile(skillDir, skillRelativePath)
            ?: throw IllegalArgumentException("Invalid skill file path: $entryName")

        skillDir.mkdirs()
        targetFile.parentFile?.mkdirs()
        val written = FileOutputStream(targetFile).use {
            zipIn.copyToCancellableLimited(it, MAX_ENTRY_UNCOMPRESSED_BYTES)
        }
        Log.i(TAG, "restore: restored $entryName (${targetFile.length()} bytes)")
        return written
    }

    private fun validateZipEntryName(name: String) {
        val normalized = name.replace('\\', '/')
        if (normalized.isBlank()) {
            throw IOException("Backup zip entry name is blank")
        }
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            throw IOException("Backup zip entry has absolute path: $name")
        }
        if (normalized.length >= 2 && normalized[1] == ':') {
            throw IOException("Backup zip entry has absolute path: $name")
        }
        if (normalized.split('/').any { it == ".." }) {
            throw IOException("Backup zip entry path traversal rejected: $name")
        }
    }

    private suspend fun InputStream.copyToCancellableLimited(output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(ZIP_COPY_BUFFER_SIZE)
        var bytesCopied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val bytesRead = read(buffer)
            if (bytesRead < 0) break
            bytesCopied += bytesRead
            if (bytesCopied > maxBytes) {
                throw IOException("Backup zip entry exceeds max size ($maxBytes bytes)")
            }
            output.write(buffer, 0, bytesRead)
        }
        return bytesCopied
    }

    private fun InputStream.readBounded(maxBytes: Long): ByteArray {
        val buffer = ByteArray(ZIP_COPY_BUFFER_SIZE)
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val bytesRead = read(buffer)
            if (bytesRead < 0) break
            total += bytesRead
            if (total > maxBytes) {
                throw IOException("Backup zip entry exceeds max size ($maxBytes bytes)")
            }
            out.write(buffer, 0, bytesRead)
        }
        return out.toByteArray()
    }
}
