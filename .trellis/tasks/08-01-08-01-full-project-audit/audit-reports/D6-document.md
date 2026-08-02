# D6 — Document Parsing Module Audit

**Scope:** `document/` (PDF / DOCX / PPTX / EPUB) + chat integration (`DocumentAsPromptTransformer`, upload/attachment flow)  
**Mode:** static analysis only (no build / Gradle / runtime)  
**Date:** 2026-08-01  
**Package root:** `me.rerere.document`  
**Primary sources:**
- `document/src/main/java/me/rerere/document/{Pdf,Docx,Pptx,Epub}Parser.kt`
- vendored MuPDF JNI: `document/src/main/java/com/artifex/mupdf/fitz/**` + `jniLibs/{arm64-v8a,x86_64}/libmupdf_java.so` (~9MB each)
- `app/.../data/ai/transformers/DocumentAsPromptTransformer.kt`
- upload: `FilesManager.createChatFilesByContents`, `ChatPage` file picker, `ChatUtil.isAllowedFileType`

---

## 1. 链路梳理 (End-to-end chain)

```
[UI] ChatPage filePickerLauncher (OpenMultipleDocuments)
  → resolveChatFileUploadMetadata(fileName, mime)
  → isAllowedFileType(name, mime)          // ChatUtil.kt
  → FilesManager.createChatFilesByContents // copy full stream → filesDir/upload/<uuid>.ext
  → UIMessagePart.Document(url=file://..., fileName, mime)
  → ChatInputState.addFiles

[Send] ChatService.inputTransformers (lazy)
  includes DocumentAsPromptTransformer (after Time/PromptInjection/Placeholder)
  → withContext(Dispatchers.IO)
  → for each message: appendDocumentPromptsInOrder
      → readDocumentContent(mime switch)
          pdf  → PdfParser.parserPdf (MuPDF)
          docx → DocxParser.parse (ZipInputStream + XmlPullParser)
          pptx → PptxParser.parse (ZipFile + XmlPullParser)
          epub → EpubParser.parse (ZipFile + OPF spine + XHTML)
          else → file.readText()
      → append UIMessagePart.Text("<UploadFile name=... path?=...>```content```</UploadFile>")
  → original Document parts remain on the message
  → provider encoders (OpenAI/Claude/Google) only emit Text/Image;
     raw Document parts are ignored at wire format (by design after transform)
```

**Related limits elsewhere (NOT applied to document parse path):**
| Location | Limit |
|---|---|
| `FilesRoutes` web upload | 20 MB |
| `WorkspaceTools` read | 8 MB |
| `TextFileUtil` view | 5 MB |
| `GenerationHandler.maybeTruncateToolOutput` | tool output only |
| Chat `createChatFilesByContents` | **none** |
| Document parsers / `DocumentAsPromptTransformer` | **none** |

**Tests:** `DocumentAsPromptTransformerTest` covers prompt ordering only.  
`document` module tests are placeholder `2+2`. **No parser unit tests.**

---

## 2. Format parser inventory

| Format | Entry | I/O model | Memory | Zip-bomb guard | Native cleanup | Cancellation |
|---|---|---|---|---|---|---|
| PDF | `PdfParser.parserPdf` | path open via MuPDF | all pages → one `StringBuilder` | N/A (PDF) | **no** `destroy()` | no |
| DOCX | `DocxParser.parse` | `ZipInputStream` sequential | full `document.xml` text | **none** | streams via `use` | no |
| PPTX | `PptxParser.parse` | `ZipFile` random access | all slides + notes | **none** | `ZipFile.use` | no |
| EPUB | `EpubParser.parse` | `ZipFile` + spine | all XHTML chapters | **none** | `ZipFile.use` | no |
| Text/other | `file.readText()` | whole file | whole file | N/A | N/A | no |

---

## 3. Findings

### F6-1 — CRITICAL — Unbounded parse / prompt materialization (OOM & API blow-up)

**file:line:**  
- `DocumentAsPromptTransformer.kt:60-76`  
- `PdfParser.kt:7-18`  
- `DocxParser.kt:21-38`  
- `PptxParser.kt:17-56`  
- `EpubParser.kt:16-45`  
- `FilesManager.kt:105-145`

**description:**  
No max file size, max extracted chars, max pages/slides/chapters, or prompt truncation on the document path. Chat upload copies the entire content stream. Parsers accumulate full text into `StringBuilder`s. Transformer embeds the full string into `<UploadFile>` text parts. A multi-hundred-MB PDF/DOCX/EPUB or a highly compressible zip can OOM the process or produce multi-million-token prompts. Tool-output truncation exists elsewhere but is **not** applied here.

**evidence:**
```kotlin
// DocumentAsPromptTransformer.kt
when (document.mime) {
    "application/pdf" -> parsePdfAsText(file)
    // ...
    else -> file.readText()  // entire file
}
// PdfParser.kt
for (i in 0 until pages) {
    result.append(page.asText())  // unbounded
}
// FilesManager — no length check before copyTo
input.copyTo(output)
```

**suggested fix:**  
1) Enforce chat upload max size (align with web 20MB or stricter).  
2) Cap extracted text (e.g. 200–500k chars) with explicit truncation marker + path for workspace re-read.  
3) Cap PDF pages / EPUB chapters processed.  
4) Prefer streaming append with early stop when budget exhausted.

---

### F6-2 — CRITICAL — Zip bomb / decompression bomb (DOCX/PPTX/EPUB)

**file:line:**  
- `DocxParser.kt:23-33`  
- `PptxParser.kt:19-49`  
- `EpubParser.kt:18-42`

**description:**  
OOXML/EPUB are ZIP containers. Parsers use `ZipInputStream` / `ZipFile` with **no**:
- max compressed entry size  
- max uncompressed size / expansion ratio  
- max total uncompressed budget  
- max entry count  

A tiny malicious DOCX with a huge `word/document.xml` (or many huge slides/XHTML) can expand to GBs during XML parse / string build → OOM DoS (user-selected local file; still severe on mobile).

**evidence:**
```kotlin
ZipInputStream(fileInputStream).use { zipStream ->
    // no entry.size / available checks
    if (entry.name == "word/document.xml") {
        return parseDocumentXml(zipStream)  // unbounded pull
    }
}
```

**suggested fix:**  
Before parsing each entry: reject if `entry.size` (when known) > limit; wrap stream with a counting `InputStream` that aborts past N bytes; track total uncompressed; reject expansion ratio > e.g. 100× for known sizes; limit entry count.

---

### F6-3 — HIGH — MuPDF native resource leak (PDF)

**file:line:** `PdfParser.kt:7-18`  
(API: `Document.destroy` / `Page.destroy` / `StructuredText.destroy` in fitz bindings)

**description:**  
`PDFDocument.openDocument`, each `loadPage`, and `toStructuredText` allocate native pointers. `PdfParser` never calls `destroy()`. Cleanup relies solely on finalizers. Under large page counts or repeated attachments, native heap can grow until OOM/crash without timely GC.

**evidence:**
```kotlin
val document = PDFDocument.openDocument(file.absolutePath).asPDF()
for (i in 0 until pages) {
    val page = document.loadPage(i).toStructuredText()
    result.append(page.asText())
    // page / StructuredText never destroy()'d
}
// document never destroy()'d
```

**suggested fix:**  
```kotlin
document.use-style try/finally {
  val page = document.loadPage(i)
  try {
    val st = page.toStructuredText()
    try { ... } finally { st.destroy() }
  } finally { page.destroy() }
} finally { document.destroy(); Context.emptyStore() optional }
```

---

### F6-4 — HIGH — PDF: no password gate, no structured error, native crash surface

**file:line:** `PdfParser.kt:7-18`; outer catch only at `DocumentAsPromptTransformer.kt:66-76`

**description:**  
- `needsPassword()` / `authenticatePassword` never checked → encrypted PDFs fail opaquely or throw.  
- Parser has no local try/catch; failures become generic `"[ERROR, failed to read file: name]"` losing root cause.  
- Malformed PDFs exercise native MuPDF; uncaught native faults can kill the process (beyond Kotlin `runCatching`).  
- No page-count pre-check before full traversal.

**suggested fix:**  
Check `needsPassword()` early → return clear error. Catch exceptions per-page and continue/abort with partial text. Optionally set MuPDF store size via `Context` before open. Validate magic `%PDF` before open.

---

### F6-5 — HIGH — Allowed office types without real parsers (binary as text)

**file:line:**  
- `ChatUtil.kt:35-45` (allows `msword`, `ms-excel`, `xlsx`, `ms-powerpoint`)  
- `DocumentAsPromptTransformer.kt:67-72` (only pdf/docx/pptx/epub specialized)

**description:**  
User can attach `.doc` / `.xls` / `.xlsx` / `.ppt`. Transformer falls through to `file.readText()`, which decodes binary as UTF-8 → mojibake, huge invalid strings, or charset errors. Model receives garbage inside `<UploadFile>` as if it were content. xlsx is zip-of-xml but not routed to a spreadsheet parser.

**evidence:**
```kotlin
ALLOWED_MIME_TYPES = setOf(..., "application/msword", "...spreadsheetml.sheet", ...)
// transformer:
else -> file.readText()
```

**suggested fix:**  
Either implement parsers (or reject at picker), or in `readDocumentContent` return explicit unsupported-type error for binary office formats instead of `readText()`.

---

### F6-6 — HIGH — Chat upload path has no size limit (vs web 20MB)

**file:line:** `FilesManager.kt:105-145`; contrast `FilesRoutes.kt:29` (`MAX_UPLOAD_FILE_SIZE_BYTES = 20MB`)

**description:**  
In-app picker copies arbitrary-size files into `filesDir/upload` on (typically) the main-thread activity result path, then later parses them. Web path is capped; chat path is not → inconsistency and DoS.

**suggested fix:**  
Share one `MAX_CHAT_UPLOAD_BYTES`; abort `copyTo` with counting stream; surface toaster error.

---

### F6-7 — HIGH — `createChatFilesByContents` can block UI thread on large files

**file:line:** `FilesManager.kt:105-145`; call site `ChatPage.kt:963`

**description:**  
Method is synchronous (not `suspend`). File picker callback runs on main thread and copies full content. Large documents → jank/ANR before parse even starts.

**suggested fix:**  
Make suspend + `withContext(IO)`; show progress; size-check first.

---

### F6-8 — HIGH — Errors returned as “document content” (parser swallow)

**file:line:**  
- `DocxParser.kt:32,35-36,68`  
- `PptxParser.kt:30,54-55,99-100`  
- `EpubParser.kt:20-24,41,44`  
- `DocumentAsPromptTransformer.kt:74-76`

**description:**  
Parsers catch `Exception` and return English error **strings** as successful parse results. Transformer only wraps true exceptions. Model sees e.g. `Error parsing DOCX file: ...` inside fenced code as if it were the document body. User may not get a UI error.

**suggested fix:**  
Use sealed result `Success(text) | Failure(reason)` or throw; transformer maps failures to `[ERROR, ...]` and optionally toaster; never embed stack messages as body without a clear ERROR prefix consistently.

---

### F6-9 — MEDIUM — No cooperative cancellation mid-parse

**file:line:** `DocumentAsPromptTransformer.kt:21-33`; all parsers’ loops

**description:**  
Transform runs on `Dispatchers.IO` under a coroutine, but page/slide/chapter loops never call `ensureActive()` / check `isActive`. Cancelled generation still burns CPU/native work until finish.

**suggested fix:**  
Pass `CoroutineContext` or check `currentCoroutineContext().ensureActive()` each page/slide/chapter.

---

### F6-10 — MEDIUM — Image-only / scanned PDFs produce empty “text” with no OCR path

**file:line:** `PdfParser.kt:11-16`; OCR is `OcrTransformer` for images, not PDF pages

**description:**  
MuPDF `asText()` on image-only pages yields empty strings. Output still has `---Page N:` headers. No hook to render page → OCR. User believes document was “read.”

**suggested fix:**  
Detect low text density; either warn in prompt (`[scanned PDF: little extractable text]`) or optional page raster + OCR pipeline.

---

### F6-11 — MEDIUM — DOCX content coverage gaps

**file:line:** `DocxParser.kt:27-28,72-115,303-347`

**description:**  
Only `word/document.xml`. Skips headers/footers (`header*.xml`/`footer*.xml`), footnotes, endnotes, comments, text boxes in some drawings, embedded OLE.  
List numbering: `number` hard-coded to `1` (`ListInfo(..., number = 1)`); `numId` only sets `isNumbered = val != null` (almost always true), no `numbering.xml` resolution → wrong list markers.  
Heading detection only `HeadingN` style name suffix digit.

**suggested fix:**  
Parse numbering part for real levels/formats; optionally merge header/footer/footnote text; track list counters per `numId`.

---

### F6-12 — MEDIUM — PPTX speaker-notes index mismatch

**file:line:** `PptxParser.kt:34-46`

**description:**  
Slides sorted by filename number, but notes looked up as `notesSlide${index+1}.xml`, not the actual slide number from `slideN.xml`. Sparse numbering (e.g. only `slide5.xml`) looks for `notesSlide1.xml` → notes lost.

**evidence:**
```kotlin
.forEachIndexed { index, entry ->
    val slideNumber = index + 1
    val notesEntry = zipFile.getEntry("ppt/notesSlides/notesSlide${slideNumber}.xml")
}
```

**suggested fix:**  
Parse `N` from `slideN.xml` and use the same `N` for notes (and relationships if needed).

---

### F6-13 — MEDIUM — EPUB path from OPF `href` (zip-slip style)

**file:line:** `EpubParser.kt:32-33`

**description:**  
`itemPath = "$opfDir/${item.href}"` with no normalization. Malicious OPF `href` like `../../../other` may resolve unexpected zip entries depending on `ZipFile.getEntry` behavior. Lower risk than filesystem zip-slip (stays inside zip) but can still read unintended package entries.

**suggested fix:**  
Normalize path (reject `..`, absolute paths); resolve relative to opf dir with a safe join; verify entry name is under package root.

---

### F6-14 — MEDIUM — EPUB/DOCX/PPTX XXE / DTD surface uneven

**file:line:**  
- `EpubParser.kt:106` sets `FEATURE_PROCESS_DOCDECL = false` (good)  
- `DocxParser` / `PptxParser` / OPF/container parsers do **not** set equivalent features

**description:**  
Android XmlPullParser is generally safer than full DOM, but DOCDECL/entity expansion policy should be explicit on all parsers for defense in depth (especially untrusted user files).

**suggested fix:**  
Centralize `newSecurePullParser()` disabling DOCDECL/external entities wherever supported.

---

### F6-15 — MEDIUM — Forced UTF-8 may mis-decode some EPUB XHTML

**file:line:** `EpubParser.kt:107`; similarly DOCX/PPTX `setInput(..., "UTF-8")`

**description:**  
OOXML is UTF-8 by spec (OK). EPUB XHTML may declare other encodings; forcing UTF-8 can garble CJK/legacy books.

**suggested fix:**  
Honor XML encoding declaration / HTML meta when present; fallback UTF-8.

---

### F6-16 — MEDIUM — MuPDF ABI coverage: only arm64-v8a + x86_64

**file:line:** `document/src/main/jniLibs/**` (no `armeabi-v7a` / `x86`)

**description:**  
`System.loadLibrary("mupdf_java")` on 32-bit-only devices → `UnsatisfiedLinkError` → PDF path fails (caught as generic ERROR if luckily in Kotlin; may surface earlier). minSdk 26 still includes 32-bit devices.

**suggested fix:**  
Ship v7a build, or gate PDF feature on 64-bit / `Build.SUPPORTED_ABIS`, with clear UX.

---

### F6-17 — MEDIUM — Context preview undercounts Document cost

**file:line:** `ContextPreview.kt:198`

**description:**  
`visibleCharacterCount` for Document uses only `url + fileName + mime` lengths, **not** expanded `<UploadFile>` text after transform. Preview/token estimates systematically undercount document-heavy messages.

**suggested fix:**  
Count post-transform text or estimate via same read path (with cap).

---

### F6-18 — LOW — API naming / dead surface

**file:line:** `PdfParser.kt:7` `parserPdf` typo; `PptxParser` unused imports (`ZipEntry`); empty example tests

**description:**  
Style/maintainability; no functional crash by itself. Zero real parser tests → regressions invisible.

**suggested fix:**  
Rename to `parsePdf`; add golden-file unit tests per format (small fixtures + zip-bomb negative tests).

---

### F6-19 — LOW — DOCX/PPTX markdown formatting edge cases

**file:line:** `DocxParser.kt:136-144`; `EpubParser` strong/em nesting

**description:**  
Per-run `**`/`*` wrapping can break mid-word or nest poorly; table cells lose paragraph breaks (space-joined). Acceptable for MVP extraction quality, not security.

---

### F6-20 — INFO/LOW — Original Document parts retained after transform

**file:line:** `DocumentAsPromptTransformer.kt:85-98`; provider `else -> {}` for non Text/Image

**description:**  
By design: prompts appended; raw Document not serialized to most providers. Risk only if a future encoder starts sending Document without stripping — currently OK. Duplicate storage in conversation JSON is metadata-only (url), not full text — good.

---

## 4. 亮点 / 可复用 (Strengths)

1. **Clear module boundary** — pure JVM-ish parsers in `:document`, app only depends via transformer; easy to test in isolation once fixtures exist.  
2. **IO dispatcher** — `DocumentAsPromptTransformer` correctly avoids main-thread parse (`Dispatchers.IO`).  
3. **Streaming-friendly DOCX entry** — `ZipInputStream` + XmlPullParser avoids loading whole ZIP into heap (entry content still unbounded — see F6-2).  
4. **Markdown-oriented extraction** — headings, lists, tables → markdown is a good LLM-facing format.  
5. **EPUB spine-ordered reading** — container.xml → OPF manifest/spine is correct EPUB structural approach; DOCDECL disabled on XHTML.  
6. **Workspace path hint** — `path="/upload/..."` lets tools re-read originals without re-embedding (good pattern if combined with truncation).  
7. **Preview policy** — `PreviewTransformPolicy.SideEffectFree` correctly marks transform as preview-safe (no network).  
8. **MuPDF consumer ProGuard keep** — `consumer-rules.pro` keeps `com.artifex.mupdf.**`.  
9. **Prompt append helper testable** — `appendDocumentPromptsInOrder` is `internal` pure and unit-tested for order.

---

## 5. 遗漏与风险 (Gaps & residual risk)

| Gap | Risk |
|---|---|
| No max size / max extract / zip-bomb controls | Mobile OOM DoS from local or shared files |
| No parser unit/integration tests | Silent extraction regressions |
| Legacy Office + xlsx allowed but unparsed | User trust / garbage context |
| PDF native leak + ABI holes | Long-session instability; 32-bit crash |
| No cancel checkpoints | Wasted work after user stop |
| Scanned PDFs | False “empty doc” understanding |
| Password PDFs | Opaque failure |
| Headers/footers/notes/numbering incomplete | Wrong/missing legal or academic content |
| Prompt injection via document body | Extracted text is attacker-controlled content inside user message fences — model-facing; not OS RCE but prompt-injection vector (inherent to feature; mitigate with clear delimiters already present, size caps, optional sanitization) |
| Re-parse every send | Same attachment re-extracted each generation (CPU); no cache by content hash |

---

## 6. Severity tally

| Severity | Count | IDs |
|---|---|---|
| CRITICAL | 2 | F6-1, F6-2 |
| HIGH | 6 | F6-3, F6-4, F6-5, F6-6, F6-7, F6-8 |
| MEDIUM | 9 | F6-9 … F6-17 |
| LOW | 3 | F6-18, F6-19, F6-20 |

---

## 7. Recommended fix priority

1. **P0:** Upload size cap + extract char/page caps + zip entry budget (F6-1, F6-2, F6-6)  
2. **P0:** MuPDF `destroy()` lifecycle (F6-3)  
3. **P1:** Unsupported mime handling (F6-5); structured errors (F6-8); off-main upload (F6-7)  
4. **P1:** Password PDF + per-page resilience (F6-4); cancel points (F6-9)  
5. **P2:** Notes index, numbering, EPUB path normalize, ABI, preview counts, tests  

---

## 8. File map (quick)

```
document/
  build.gradle.kts
  consumer-rules.pro          # keep mupdf
  src/main/java/me/rerere/document/
    PdfParser.kt              # MuPDF text extract
    DocxParser.kt             # ZIP + document.xml → markdown
    PptxParser.kt             # ZIP + slides/notes → markdown
    EpubParser.kt             # ZIP + OPF spine + XHTML → markdown
  src/main/java/com/artifex/mupdf/fitz/**  # vendored bindings
  src/main/jniLibs/{arm64-v8a,x86_64}/libmupdf_java.so

app/.../DocumentAsPromptTransformer.kt   # integration
app/.../FilesManager.kt                  # upload copy
app/.../ChatUtil.kt                      # allowlist
app/.../ChatPage.kt                      # picker
app/.../ChatService.kt:368               # registers transformer
```

---

*End of D6 report.*
