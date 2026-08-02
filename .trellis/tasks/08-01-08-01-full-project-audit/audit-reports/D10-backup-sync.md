# D10 Backup / Restore / Sync / Export — Static Audit Report

> Scope: full-app backup ZIP, WebDAV/S3 remote backup, local import/export, third-party importers (Chatbox / Cherry Studio / provider importers), preset/lorebook export serializers, chat markdown/image export  
> Method: READ-ONLY static analysis (no Gradle, no runtime)  
> Date: 2026-08-01  
> Package roots: `app/.../me/rerere/rikkahub/data/sync`, `data/export`, `data/datastore` (WebDavConfig/Settings), `ui/pages/backup`, `ui/pages/chat/Export.kt`

---

## 1. 链路梳理 (Call Chains)

### 1.1 Backup archive create

```
UI (ImportExportTab / WebDavTab / S3Tab)
  → BackupVM.startLocalExport | startWebDavBackup | startS3Backup
    → BackupTaskCoordinator.start (AppScope, 15min timeout, cancellable)
      → BackupArchive.create(BackupArchiveOptions)
          1. refuse if settings.init (dummy)
          2. File.createTempFile(backup_*.zip, cacheDir)
          3. Zip: settings.json = full Settings encode (providers, WebDAV/S3 creds, assistants, …)
          4. optional VACUUM INTO snapshot → validate integrity+FK → zip as rikka_hub.db
          5. optional files: upload/ (top-level only), skills/ (recursive + cycle/escape checks), fonts/ (top-level)
      → WebDAV: stream PUT file.readChannel() | S3: SHA-256 + SigV4 + stream PUT
      → Local: copyToCancellable → SAF CreateDocument URI
      → finally delete temp zip / snapshot
```

**Payload completeness vs DB:** Room DB (v48, all entities) is snapshotted wholesale when `DATABASE` selected — conversations, message nodes, memories, hooks, managed files metadata, workspaces *rows*, favorites, etc. **On-disk workspace trees, assistant_skills, skill_shared, tool_outputs are NOT archived.**

### 1.2 Restore (local / WebDAV / S3)

```
download or SAF → temp cache file
  → BackupRestorer.restore(includeDatabase, includeFiles)
      ZipInputStream walk:
        settings.json → decode SettingsJsonMigrator.migrate → pendingSettings (not applied yet)
        rikka_hub.db|wal|shm → stage under cache workDir (if includeDatabase)
        upload|skills|fonts → immediate overwrite on filesDir (if includeFiles)  ← not atomic
      if includeDatabase && stagedDb:
        validate integrity + FK on staged copy
        settingsStore.update(newSettings)          ← before swap
        swapDatabaseAtomically (.restore-bak rollback)
        on swap fail: try roll back previous settings
      else: apply settings only
      finally delete workDir
  → UI Success → BackupDialog → exitProcess(0)  (Room never reopened in-process)
```

Local import forces `WebDavConfig.BackupItem.entries` (always DB+files). Remote restore honours per-config `items`.

### 1.3 WebDAV / S3 “sync”

Not delta sync: **full zip upload** per backup; **full zip download + restore** per restore. List by name prefix `backup_*.zip` / `rikkahub_backups/backup_*.zip`. Auth: WebDAV Basic; S3 AWS SigV4 (accessKeyId + secretAccessKey). No OAuth. No automatic conflict merge. Cancellation via coordinator job cancel + `copyToCancellable` / coroutine cancel (download loops partially cooperative).

### 1.4 Third-party import

| Source | Entry | Behavior |
|--------|-------|----------|
| Chatbox JSON | `ChatboxImporter.importStreaming` | Streaming sessions; insert new conversations only; prepend providers; may enable conversation system prompt |
| Cherry Studio ZIP | `CherryStudioProviderImporter` | Read `data.json` only → providers; prepend to settings |
| Provider JSON importers | OpenCode / NewAPI / Cherry / Chatbox helpers | Settings/provider merge outside full backup |

### 1.5 Feature export (not full backup)

```
ExportSerializer (preset / lorebook / mode_injection)
  → ExportData{version,type,data} JSON
  → ExportHooks rememberExporter: CreateDocument / share via FileProvider

ChatExportSheet
  → Markdown (in-memory StringBuilder + base64 images) / Image (Compose bitmap)
```

---

## 2. Findings

### F10-1 — CRITICAL — Backup ZIP is unencrypted and contains all secrets

- **file:line:** `BackupArchive.kt:61-65`, `PreferencesStore.kt:1309-1317`, `S3Config.kt:7-10`
- **description:** `settings.json` is a full `Settings` serialization. That object includes provider `apiKey`s, `webDavConfig.password`, `s3Config.secretAccessKey`, TTS/ASR keys, search keys, etc. Archive is a plain ZIP uploaded to user WebDAV/S3 or exported via SAF with **no password, no AEAD, no secret redaction**.
- **evidence:**
```kotlin
addVirtualFileToZip(
    zipOut = zipOut,
    name = "settings.json",
    content = json.encodeToString(settings),
)
// WebDavConfig.password, S3Config.secretAccessKey live inside Settings
```
- **suggested fix:** Optional backup password (AES-GCM of zip or of settings envelope); default redaction of secrets with explicit “include credentials” toggle; never upload secrets to remote without encryption at rest.

### F10-2 — CRITICAL — Credentials stored in plaintext Preferences DataStore

- **file:line:** `PreferencesStore.kt:99-109`, `314-319`, `652-653`
- **description:** WebDAV password, S3 secret, and all provider API keys are persisted as plain JSON strings under `preferencesDataStore(name = "settings")` with no `EncryptedSharedPreferences` / Tink. Backup multiplies the blast radius (F10-1) but local device compromise already exposes them.
- **evidence:**
```kotlin
preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)
```
- **suggested fix:** Encrypt secret fields at rest (Android Keystore-backed); keep non-secret config in DataStore; scrub logs/UI copy.

### F10-3 — CRITICAL — File restore is non-atomic and runs before DB commit

- **file:line:** `BackupRestorer.kt:76-114`, `239-285`, comments `47-48`
- **description:** While walking the ZIP, `includeFiles` entries are written **immediately** to `filesDir` (`upload/`, `skills/`, `fonts/`). DB is only staged/validated/swapped **after** the entire walk. If validation fails, cancel mid-walk, or process dies after partial file writes: **live files are permanently partially overwritten with no rollback**, while DB/settings may remain old or roll back. Comment explicitly accepts this: “File payloads … are not part of the atomic database/settings unit.”
- **evidence:**
```kotlin
else -> if (includeFiles) restoreFileEntry(zipIn, zipEntry.name)
// ... later ...
if (includeDatabase && stagedDb != null) {
    validateDatabaseIntegrity(stagedDb, context)
    // settings + swap
}
```
- **suggested fix:** Stage all files under a restore work tree; only after DB+settings commit, atomically promote (or two-phase: rename roots). On failure delete staging only. Or restore files only after successful DB swap.

### F10-4 — CRITICAL — No zip-bomb / expansion limits on untrusted archives

- **file:line:** `BackupRestorer.kt:76-109`, `86`; `CherryStudioProviderImporter.kt:20-23`
- **description:** Restore trusts user-selected or remote ZIPs. No caps on entry count, per-entry uncompressed size, total uncompressed size, or compression ratio. `settings.json` uses `zipIn.readBytes()`. Malicious or accidental huge entries can fill storage or OOM. Skills/upload streams to disk without a max size either.
- **evidence:**
```kotlin
val raw = zipIn.readBytes().toString(Charsets.UTF_8)
FileOutputStream(target).use { zipIn.copyToCancellable(it) } // unbounded
```
- **suggested fix:** Track total written bytes; refuse > N entries / > M GiB; reject entries with absurd `size` vs compressed size; stream-limit settings JSON (e.g. 32–64 MiB).

### F10-5 — HIGH — Backup file set incomplete vs product surface

- **file:line:** `BackupArchive.kt:73-77`, `FileFolders` in `FilesManager.kt:489-496`
- **description:** With `FILES` enabled, only `upload/` (non-recursive), `skills/` (recursive), `fonts/` (non-recursive) are packed. **Missing:** `assistant_skills`, `skill_shared`, `tool_outputs`, and all **workspace** trees (`WorkspaceManager` files/linux under workspace base). DB may reference workspace roots / managed files whose blobs are absent after restore → broken attachments, empty workspaces, missing private skills.
- **evidence:**
```kotlin
addTopLevelFiles(zipOut, FileFolders.UPLOAD)
addSkills(zipOut)
addTopLevelFiles(zipOut, FileFolders.FONTS)
// no ASSISTANT_SKILLS / SKILL_SHARED / TOOL_OUTPUTS / workspace
```
- **suggested fix:** Explicit backup inventory in UI; recursive upload; include assistant/shared skills and optional workspace payload; document intentional exclusions.

### F10-6 — HIGH — `upload/` backup is top-level only

- **file:line:** `BackupArchive.kt:90-99`
- **description:** `addTopLevelFiles` filters `it.isFile` only — nested directories under `upload/` are silently skipped. If product ever nests uploads, backups are incomplete without error.
- **suggested fix:** Reuse `addDirectoryToZip` for upload (and fonts if nested), or fail loud if subdirs exist.

### F10-7 — HIGH — Live Room connection not closed before file swap; relies solely on process kill

- **file:line:** `BackupRestorer.kt:166-202`; `BackupDialog.kt:12-26`; `DataSourceModule.kt:83`
- **description:** Swap renames/copies over `getDatabasePath("rikka_hub")` while the process still holds a Koin singleton `AppDatabase` / open SQLite. Correctness depends on UI forcing `exitProcess(0)` after success. No `RoomDatabase.close()`, no invalidation. If restart dialog is bypassed in a future UI change, or a non-UI caller restores, **in-memory DAOs write to a replaced/rolled-back file** → corruption or silent data loss.
- **evidence:**
```kotlin
// swap moves stagedDb → liveDb while app Room still open
Button(onClick = { exitProcess(0) })
onDismissRequest = {} // cannot dismiss without restart
```
- **suggested fix:** Close DB (and stop writers) before swap; or restore only after cold start from a “pending restore” flag; keep forced restart as belt-and-suspenders.

### F10-8 — HIGH — Settings applied before DB swap; rollback can leave new-settings + old-DB

- **file:line:** `BackupRestorer.kt:124-145`
- **description:** `#190` applies settings **before** swap so a settings write failure leaves old DB. If swap fails and `previousSettings` is null (`settingsFlow` still `init`/dummy), rollback is skipped → **new settings + rolled-back old DB**. Even with rollback, DataStore update is best-effort (`runCatching`).
- **evidence:**
```kotlin
val previousSettings = settingsStore.settingsFlow.value.takeIf { !it.init }
newSettings?.let { settingsStore.update(it) }
try { swapDatabaseAtomically(...) } catch (error: Throwable) {
    if (newSettings != null && previousSettings != null) {
        runCatching { settingsStore.update(previousSettings) }
    }
    throw error
}
```
- **suggested fix:** Keep settings pending until swap succeeds (original #184 design), or require non-init previous snapshot and hard-fail restore if rollback impossible; single transactional “commit point”.

### F10-9 — HIGH — `moveInto` after `dest.delete()` is not crash-safe on cross-filesystem copy

- **file:line:** `BackupRestorer.kt:210-216`, `219-236`
- **description:** When `renameTo` fails (cache vs databases different mounts), code `dest.delete()` then copy. Mid-copy crash leaves **missing or partial live DB** until rollback path runs — rollback only runs if the exception is caught in `swapDatabaseAtomically`. Process kill during copy can leave DB missing despite `.restore-bak` still present (app may open empty/new DB on next start depending on Room behavior).
- **suggested fix:** Copy to `liveDb.tmp` then atomic rename over live; only then delete bak. Prefer same-filesystem staging under databases dir.

### F10-10 — HIGH — Cherry Studio import logs provider objects (API keys)

- **file:line:** `BackupVM.kt:273`
- **description:** `Log.i(..., "providers: $importProviders")` stringifies provider settings including `apiKey`. Logcat / bug reports leak secrets.
- **evidence:**
```kotlin
Log.i(TAG, "restoreFromCherryStudio: import ${importProviders.size} providers: $importProviders")
```
- **suggested fix:** Log counts/names only; never log apiKey/password/secret.

### F10-11 — HIGH — Path traversal mostly mitigated, but fonts path is weaker than upload/skills

- **file:line:** `BackupRestorer.kt:253-259` vs `245-246`, `FilePathSecurity.kt:6-15`, `SkillPaths.kt:69-78`
- **description:** Upload uses `resolveContainedFile`; skills use `SkillPaths`. Fonts only reject empty or `contains('/')` — no `resolveContainedFile`, no `..` segment ban, no `\` ban. On Android, a name like `..%00` edge cases are limited, but consistency gap remains; absolute-looking names without `/` are less relevant on Unix. Still weaker than sibling handlers.
- **suggested fix:** Always `resolveContainedFile(fontsFolder, fileName)` and reject multi-segment names.

### F10-12 — MEDIUM — No backup format / schema version envelope

- **file:line:** `BackupArchive.kt:61-70`; `SettingsJsonMigrator.kt:20-80`
- **description:** ZIP has no `manifest.json` / format version. Only settings JSON is best-effort migrated; DB relies on Room open+migrations after restart. Future layout changes (entry renames, multi-db) have no negotiation. Migrator on failure **returns original JSON** and continues — may fail decode or silently mis-parse.
- **suggested fix:** Add `backup_format_version`; fail closed on unknown major; align migrator failure with hard error when `DATABASE` restore requested.

### F10-13 — MEDIUM — Chatbox import is non-transactional partial commit

- **file:line:** `BackupVM.kt:220-249`
- **description:** Each conversation `insertConversation` commits immediately; settings (providers) updated only at end. Cancel/timeout/failure mid-stream leaves **partial conversations** and possibly no provider merge (or partial if settings already updated in other paths). No undo.
- **suggested fix:** Import to staging tables or single transaction; or record import batch id for cleanup on failure.

### F10-14 — MEDIUM — Chatbox provider merge can duplicate keys

- **file:line:** `BackupVM.kt:238-240`; `CherryStudioProviderImporter.kt:41` (has `distinctBy`)
- **description:** Chatbox path does `result.providers + settings.value.providers` without dedup. Re-import duplicates provider entries (API keys appear multiple times in UI).
- **suggested fix:** Same `distinctBy` / merge-by-endpoint strategy as Cherry.

### F10-15 — MEDIUM — S3 list truncates at 1000 keys without continuation

- **file:line:** `S3Sync.kt:54-59`; `S3Client.kt:248-285`
- **description:** `listObjects(maxKeys = 1000)` ignores `isTruncated` / `nextContinuationToken`. Users with >1000 backups cannot see/delete older objects in-app.
- **suggested fix:** Paginate until complete or until UI page size with “load more”.

### F10-16 — MEDIUM — Download loops lack cooperative cancellation / progress

- **file:line:** `WebDavClient.kt:168-176`; `S3Client.kt:180-183`
- **description:** `downloadToFile` / `downloadObjectToFile` stream without `ensureActive()` or size checks. Cancel may wait until buffer fills; huge remote objects fill disk.
- **suggested fix:** `ensureActive` per chunk; optional max bytes from Content-Length; delete partial target on cancel.

### F10-17 — MEDIUM — WebDAV/S3 HttpClient follows redirects with auth headers

- **file:line:** `DataSourceModule.kt:490-499`; `WebDavClient.kt:60-62`
- **description:** Shared Ktor client `followRedirects(true)` + `basicAuth` / SigV4 on each request. Depending on engine, redirects can re-attach credentials to a different host (credential leak) or break SigV4.
- **suggested fix:** Dedicated backup client with `followRedirects(false)` or strip auth on cross-origin redirect.

### F10-18 — MEDIUM — Dead full-body GET APIs invite OOM if reused

- **file:line:** `WebDavClient.kt:112-130`; `S3Client.kt:104-128`
- **description:** `get` / `getObject` load entire response into `ByteArray`. Restore path correctly uses download-to-file, but these APIs remain public footguns.
- **suggested fix:** Remove or mark internal deprecated; force streaming-only API surface.

### F10-19 — MEDIUM — No remote retention / overwrite conflict policy

- **file:line:** `WebDavSync.kt:41-55`; `S3Sync.kt:37-51`
- **description:** Each backup uploads a new timestamped object; never prunes. No detection if two devices backup concurrently (only multiple files). Disk on NAS/S3 grows unbounded from app’s perspective.
- **suggested fix:** Configurable keep-last-N; optional replace-in-place with versioning.

### F10-20 — MEDIUM — Feature export loads whole document into memory; no cancel

- **file:line:** `ExportSerializer.kt:40-49`; `ExportHooks.kt:32-70`; `chat/Export.kt:240-364`
- **description:** `readUri` / `exportToJson` / markdown export build full strings (images base64-inlined). Large lorebooks/presets/chats risk OOM; share path re-encodes via `value` getter; write errors in `writeToUri` are silent (no UI failure).
- **suggested fix:** Stream write; size guard; surface IO errors; cancelable jobs for large chat export.

### F10-21 — MEDIUM — Local export item selection coupled to WebDAV config

- **file:line:** `BackupVM.kt:158-166`, `213-217`
- **description:** Local export uses `settings.webDavConfig` items; local import forces all items. Users who unchecked WebDAV “files” silently export DB-only locally. Surprising UX / data-loss risk when they think “full backup”.
- **suggested fix:** Independent local backup options; default full for local export.

### F10-22 — MEDIUM — Coordinator timeout aborts mid-restore without file rollback

- **file:line:** `BackupTaskCoordinator.kt:74-84`, `145-147` (15 min)
- **description:** `withTimeout` cancels restore. Combined with F10-3, timeout after partial file extract leaves mixed files; DB may be untouched if cancel before swap — inconsistent app state without clear “dirty restore” flag.
- **suggested fix:** Stage-then-commit; on cancel mark incomplete and refuse run until cleanup.

### F10-23 — LOW — `get()` ByteArray put path still present for WebDAV/S3

- **file:line:** `WebDavClient.kt:51-78`; `S3Client.kt:33-65`
- **description:** Non-streaming `put(ByteArray)` retained; backup uses file streaming (good). Dead code risk for future callers.
- **suggested fix:** Restrict to tests or delete.

### F10-24 — LOW — No encryption password UX documented in backup UI path

- **file:line:** (absence across `BackupArchive` / tabs)
- **description:** Product has no backup password field; security expectation may not match “cloud backup” mental model.
- **suggested fix:** UI warning that backups contain API keys in plaintext until F10-1 fixed.

### F10-25 — LOW — Propfind/list XML parsers are lenient

- **file:line:** `WebDavClient.kt:326-398`; `S3Client.kt:315-423`
- **description:** Hand-rolled XmlPullParser; namespace stripping via `substringAfter(":")`. Odd server XML may mis-list or hide files; not a direct security issue for trusted servers.
- **suggested fix:** Harden tests against real Nextcloud/minio fixtures; consider library.

---

## 3. 亮点 / 可复用 (Strengths)

| Area | Notes |
|------|--------|
| Shared `BackupRestorer` | #184/#190 fixed dual WebDAV/S3 restore duplication; staged DB integrity+FK before touch live |
| `VACUUM INTO` snapshot | Consistent DB copy without shipping live WAL/SHM (tests assert no wal sidecars in zip) |
| Streaming upload/download | File channel PUT + download-to-file avoids full-zip heap for primary paths |
| Path containment | `resolveContainedFile`, `SkillPaths`, backup-side `ensureContained` + directory cycle detection |
| `BackupTaskCoordinator` | Survives navigation (#186); timeout; cancel; terminal consume for toasts |
| Settings migrator for JSON backups | Bridges DataStore key migrations to monolithic `settings.json` |
| Chatbox streaming import | `JsonReader` session walk avoids loading entire export |
| Instrumentation tests | `BackupRestorerTest`, `BackupArchiveZipTest`, snapshot/integration tests around atomic DB path |
| S3 SigV4 streaming | Payload hash from file stream + content-length without buffering whole object for sign |

---

## 4. 遗漏与风险 (Gaps / Residual Risk)

1. **Secret sprawl:** Device DataStore + unencrypted backup + remote object storage + log line (F10-10) — treat as one threat model.
2. **Workspace & private skills not in backup** — users may believe “full backup” restores coding environments; it does not.
3. **No true multi-device sync** — last restore wins; concurrent edits on two phones are not merged (by design today, but undocumented as “backup not sync”).
4. **HTTP WebDAV** allowed if user enters `http://` — MITM of Basic auth + full backup body.
5. **Managed file DB rows vs missing blobs** after incomplete file backup → UI broken links.
6. **Room version 48** restore from very old app builds depends on migration completeness after restart; no pre-check of `user_version` vs supported range in restorer (only integrity/FK).
7. **Cancel during Chatbox import** leaves durable partial data (F10-13).
8. **ProcessPhoenix/`exitProcess`** is abrupt — no flush of other in-flight writers (generation, hooks) before kill after restore success race.

---

## 5. Severity summary

| ID | Sev | One-liner |
|----|-----|-----------|
| F10-1 | CRITICAL | Unencrypted backup ZIP contains all API keys & remote secrets |
| F10-2 | CRITICAL | Secrets in plaintext DataStore |
| F10-3 | CRITICAL | File restore non-atomic, before DB commit |
| F10-4 | CRITICAL | No zip-bomb / size limits on import |
| F10-5 | HIGH | Missing workspace / assistant_skills / tool_outputs in archive |
| F10-6 | HIGH | upload/ non-recursive |
| F10-7 | HIGH | Room still open across file swap; depends on exitProcess |
| F10-8 | HIGH | Settings-before-swap rollback hole when previous was init |
| F10-9 | HIGH | Cross-FS swap copy after delete not crash-safe |
| F10-10 | HIGH | Cherry import logs providers (api keys) |
| F10-11 | HIGH | Fonts restore weaker path checks |
| F10-12–F10-22 | MEDIUM | Format version, partial imports, S3 pagination, redirects, export memory, etc. |
| F10-23–F10-25 | LOW | Dead APIs, UX warnings, XML parsers |

---

## 6. Suggested fix priority

1. **P0 security:** Encrypt backups + at-rest secrets; stop logging secrets; warn on plaintext remote backup.  
2. **P0 integrity:** Stage files with DB; close DB before swap; crash-safe promote; zip limits.  
3. **P1 completeness:** Expand file inventory (workspace optional, skills complete, recursive upload).  
4. **P1 UX/ops:** Independent local options; S3 pagination; retention; cancel-safe imports.  

---

*End of D10 report. Static analysis only; no runtime verification of WebDAV/S3 servers.*
