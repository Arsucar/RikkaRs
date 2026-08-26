# Research: ImageUtils.getTavernCharacterMeta — PNG tEXt → Base64

- **Query**: Show how PNG tEXt chunk is read and Base64 decoded.
- **Scope**: internal
- **Date**: 2026-08-16

## File

`app/src/main/java/me/rerere/rikkahub/utils/ImageUtils.kt` L301-316

## Full method

```kotlin
/**
 * 获取酒馆角色卡中的角色元数据（如果存在）
 *
 * @param context Android上下文
 * @param uri 图片URI
 * @return Result<String> 包含角色元数据的Result对象
 */
fun getTavernCharacterMeta(context: Context, uri: Uri): Result<String> = runCatching {
    val metadata = context.contentResolver.openInputStream(uri)?.use { ImageMetadataReader.readMetadata(it) }
    if (metadata == null) error("Metadata is null, please check if the image is a character card")
    if (!metadata.containsDirectoryOfType(PngDirectory::class.java)) error("No PNG directory found, please check if the image is a character card")

    val pngDirectory = metadata.getDirectoriesOfType(PngDirectory::class.java)
        .firstOrNull { directory ->
            directory.pngChunkType == PngChunkType.tEXt
                && directory.getString(PngDirectory.TAG_TEXTUAL_DATA).startsWith("[chara:")
        } ?: error("No tEXt chunk found, please check if the image is a character card")

    val value = pngDirectory.getString(PngDirectory.TAG_TEXTUAL_DATA)

    val regex = Regex("""\[chara:\s*(.+?)]""")
    return Result.success(regex.find(value)?.groupValues?.get(1) ?: error("No character data found"))
}
```

## How it works

1. Opens an input stream from `context.contentResolver` for the PNG URI.
2. Uses **metadata-extractor** library (`com.drew.imaging.ImageMetadataReader`) to parse PNG chunk metadata — NOT manual PNG chunk walking. Dependencies visible in imports (L12-14):
   ```kotlin
   import com.drew.imaging.ImageMetadataReader
   import com.drew.imaging.png.PngChunkType
   import com.drew.metadata.png.PngDirectory
   ```
3. Iterates all `PngDirectory` entries; selects the first one whose `pngChunkType == PngChunkType.tEXt` AND whose `TAG_TEXTUAL_DATA` value starts with `"[chara:"`. This is the SillyTavern v2/v3 character card embedding convention: a tEXt chunk with key `chara` whose value is the Base64-encoded JSON.
4. Extracts the Base64 payload via regex `\[chara:\s*(.+?)]` (group 1).
5. Returns `Result<String>` containing the Base64 string (NOT yet decoded).

## Base64 decode step

Decoding happens in the **caller**, `AssistantImporter.kt` L264-265:
```kotlin
result.map { base64Data ->
    val json = String(Base64.decode(base64Data, Base64.DEFAULT))
    val bg = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
    json to bg
}.getOrElse { throw it }
```
Uses `android.util.Base64` (imported L5), URL-safe not specified so default `Base64.DEFAULT` flag is used.

## Caveats

- The regex `\[chara:\s*(.+?)]` is non-greedy and matches up to the first `]`. If the tEXt value format ever changes (e.g. to `[chara_v3:...]` or different bracketing), this breaks silently.
- The library serializes the tEXt chunk as a string like `chara: <base64>` per metadata-extractor's `PngDirectory.TAG_TEXTUAL_DATA` formatting, so the `[chara:...]` wrapper is likely a metadata-extractor rendering of the key/value pair. Verify against actual sample cards when extending.
- Only the `chara` key is matched; `ccv3` key (alternate v3 embedding) is NOT matched — if a v3 card embeds under a different key, this parser won't find it.
- No fallback to manual PNG chunk reading if metadata-extractor fails to recognize the chunk.

## Related files

| File | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` L263 | Caller; does the actual Base64.decode |
| `app/build.gradle.kts` | (look here for `metadata-extractor` dependency version if extending) |

## Not found

- No alternate tavern meta readers (no `ccv3` / v3-specific extractor).
- No tests covering `getTavernCharacterMeta`.
