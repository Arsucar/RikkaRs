# Research: FilesManager — file writing API

- **Query**: Show how files are written and managed.
- **Scope**: internal
- **Date**: 2026-08-16

## File

`app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt` (615 lines)

## Class

```kotlin
class FilesManager(
    private val context: Context,
    private val repository: FilesRepository,
    private val appScope: AppScope,
)
```
Companion constants (L37-43): `MAX_UPLOAD_BYTES = 50L * 1024 * 1024` (50MB), HTTP timeouts 30s.

## Write APIs

### `saveManagedFromUri(folder, uri, displayName?, mimeType?)` (L45-83)
- Resolves name/mime from URI if not provided.
- If `folder == FileFolders.UPLOAD`, calls `ensureUploadSizeAllowed(uri)` (queries size via `OpenableColumns.SIZE` for content://, `File.length()` for file://).
- Creates target file via `createTargetFile(folder, name, mime)` — UUID-prefixed filename inside `context.filesDir/<folder>/`.
- Copies input → output; uses `copyWithSizeLimit` only when in UPLOAD folder (50MB hard cap during streaming copy).
- On exception: deletes target file and rethrows.
- Persists a `ManagedFileEntity` row via `repository.insert(...)` (L77-82).

### `saveManagedFromBytes(folder, bytes, displayName, mimeType)` (L85-102)
- For UPLOAD folder: enforces 50MB cap up-front (`bytes.size > MAX_UPLOAD_BYTES`).
- `target.writeBytes(bytes)`.
- Persists `ManagedFileEntity`.

### `saveManagedText(folder, text, displayName, mimeType="text/plain")` (L104-118)
- `target.writeText(text)`.
- Persists `ManagedFileEntity`.

### `createChatFilesByContents(uris: List<Uri>): List<Uri>` (L133-174)
- Synchronous (NOT suspend) — used by `AssistantImporter.kt` L266.
- For each URI:
  - Resolves source name + mime
  - `ensureUploadSizeAllowed(uri)`
  - Builds UUID filename via `FileUtils.buildUuidFileName(displayName, mimeType)`
  - Creates file under `context.filesDir/upload/`
  - Copies with size limit
  - Calls `trackManagedFile(...)` — **fire-and-forget** via `appScope.launch(Dispatchers.IO)` (L478-506); failures are logged but do NOT fail the calling copy. This means a successful copy + failed DB tracking is possible.
  - Appends `file.toUri()` to result list.
- Failures per-URI are caught and logged; does NOT abort the whole batch.
- Returns list of `file://...` URIs for successfully-copied files.

### `createChatFilesByByteArrays(byteArrays)` (L176-204)
- Same pattern as `createChatFilesByContents` but for in-memory `ByteArray` (assumes PNG).

### `createChatTextFile(text): UIMessagePart.Document` (L265-284)
- Writes to `upload/pasted_text.txt` (UUID-prefixed), returns a `UIMessagePart.Document` with the file:// URI.

### `createImageFileFromBase64(base64Data, filePath): File` (L294-307)
- Strips optional `data:image/...;base64,` prefix.
- Decodes via `kotlin.io.encoding.Base64` (not `android.util.Base64`).
- Creates parent dirs, writes bytes, returns `File`. **Does NOT track via repository.**

## Tracking model — `ManagedFileEntity`

Stored via `FilesRepository` (Room). Fields visible in `createManagedFileEntity` (L458-476) and `syncFolder` (L366-410):
- `folder: String`
- `relativePath: String` — e.g. `"upload/<uuid-name>.png"`
- `displayName: String`
- `mimeType: String`
- `sizeBytes: Long`
- `createdAt: Long`, `updatedAt: Long`

## Folders — `FileFolders` object (L575-582)
```kotlin
object FileFolders {
    const val UPLOAD = "upload"
    const val SKILLS = "skills"
    const val ASSISTANT_SKILLS = "assistant_skills"
    const val SKILL_SHARED = "skill_shared"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
}
```
All files live under `context.filesDir/<folder>/`. No dedicated folder for world info / presets / lorebooks — they are NOT stored as files; they live in `Settings.presets` / `Settings.lorebooks` (DataStore JSON).

## Convenience extensions (L584-615)

- `FilesManager.saveUploadFromUri(uri, displayName?, mimeType?)` — wraps `saveManagedFromUri` with `UPLOAD`.
- `FilesManager.saveUploadFromBytes(bytes, displayName, mimeType)` — wraps `saveManagedFromBytes` with `UPLOAD`.
- `FilesManager.saveUploadText(text, displayName, mimeType)` — wraps `saveManagedText` with `UPLOAD`.

## Lifecycle / sync

- `syncFolder(folder)` (L366-410): reconciles disk vs DB; inserts missing rows, deletes orphans.
- `delete(id, deleteFromDisk=true)` (L412-418)
- `deleteAll(folder)` (L420-445)
- `deleteChatFiles(uris)` (L236-252): deletes files + DB rows in fire-and-forget `appScope.launch`.

## Usage by AssistantImporter (relevant to #302)

`AssistantImporter.kt` L266:
```kotlin
val bg = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
```
This is the ONLY file-write call in the import path — it copies the PNG into `upload/` and stores the resulting `file://` URI as `Assistant.background`.

**For world_info/preset bindings in #302, FilesManager is NOT the right persistence layer.** World books and presets are JSON-structured data living in `Settings.lorebooks` / `Settings.presets` (DataStore); they are not managed as files. The import flow should:
1. Parse embedded bindings from the card JSON.
2. Convert via the existing `LorebookSerializer` / `PresetSerializer` mapping logic (or lifted helpers).
3. Append to `Settings.lorebooks` / `Settings.presets` via `SettingsStore.update { it.copy(lorebooks = ..., presets = ...) }`.
4. Reference the new IDs from `Assistant.lorebookIds` / `Assistant.presetIds`.

The only file write that remains appropriate is the PNG background copy already in place.

## Related files

| File | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/db/entity/ManagedFileEntity.kt` | DB entity (referenced via `FilesRepository`) |
| `app/src/main/java/me/rerere/rikkahub/data/repository/FilesRepository.kt` | Room DAO wrapper |
| `app/src/main/java/me/rerere/rikkahub/utils/FileUtils.kt` | `buildUuidFileName`, `guessMimeType`, `getFileNameFromUri`, `getFileMimeType`, `buildRelativePath`, `getRelativePathInFilesDir` |

## Caveats / Not found

- `createChatFilesByContents` swallows per-URI failures; caller sees a short result list without knowing which inputs failed.
- `trackManagedFile` is fire-and-forget — DB tracking lag is possible immediately after a write.
- No transactional "write file + update Settings" API exists; a multi-step import that writes a file AND adds a lorebook must compose `FilesManager` + `SettingsStore.update` and handle rollback manually.
