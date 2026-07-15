# Workspace Media Preview Contract

## Scenario: View Workspace files without duplicating editors

### 1. Scope / Trigger

- Trigger: adding or changing Workspace file click/menu behavior, media preview, external open, or LINUX-area actions.
- Applies to the app Workspace Compose page, ViewModel, repository, cache export, and FileProvider handoff.

### 2. Signatures

- `classifyWorkspaceFile(fileName: String): WorkspaceFileKind`
- `workspaceMimeType(fileName: String): String`
- `buildWorkspaceViewIntent(uri: Uri, mimeType: String): Intent`
- `WorkspaceRepository.writeText/importFile/deleteFile(..., area: WorkspaceStorageArea)`

### 3. Contracts

- Text and Markdown continue through `TextFileUtil`, `FullScreenTextEditor`, and `FullScreenMarkdownViewer`; do not add `WorkspaceFileEditorPage` or `readTextForPreview`.
- Images are exported to an app cache file and displayed by the shared `ImagePreviewDialog` after existence/decode validation.
- Other files are exported to cache, exposed as a FileProvider `content://` URI, and opened with `ACTION_VIEW`, MIME type, and `FLAG_GRANT_READ_URI_PERMISSION`.
- Unknown MIME falls back to `application/octet-stream`; raw `file://` URIs are forbidden.
- LINUX permits view/export/share/external-open only. Import, edit, save, and delete are rejected in UI and repository/ViewModel boundaries.

### 4. Validation & Error Matrix

- File missing after export -> localized missing-file feedback.
- Image decode bounds invalid -> localized image-decode feedback; do not open the dialog.
- No matching Activity -> localized no-viewer feedback.
- FileProvider/path/security/intent failure -> localized safe-open failure; no crash.
- Repository write with `WorkspaceStorageArea.LINUX` -> `IllegalArgumentException` before filesystem mutation.

### 5. Good/Base/Bad Cases

- Good: `.png` opens in the shared dialog; `.mp4` opens through a temporary content URI.
- Base: `.txt` and `.md` retain the established read/edit/Markdown flows.
- Bad: hiding Edit in Compose while repository writes to LINUX remain callable.

### 6. Tests Required

- JVM tests for text/Markdown/image/other classification, query/fragment/case normalization, known MIME, and fallback MIME.
- Repository policy test asserting FILES writes are allowed and LINUX writes are rejected.
- Resource checks for default English and real Simplified Chinese feedback strings.
- App unit tests, resources, Kotlin compile, and device install when available.

### 7. Wrong vs Correct

#### Wrong

```kotlin
Intent(Intent.ACTION_VIEW, Uri.fromFile(file))
```

#### Correct

```kotlin
val uri = FileProvider.getUriForFile(context, authority, cacheFile)
context.startActivity(buildWorkspaceViewIntent(uri, mimeType))
```
