# D12 — Global Security Audit (Static Analysis)

**Scope:** All modules — `app/`, `ai/`, `common/`, `document/`, `search/`, `speech/`, `workspace/`, `web/`  
**Mode:** READ-ONLY static analysis (no Gradle, no code changes outside this report)  
**Date:** 2026-08-01  
**Cross-ref:** Web-server findings overlap D8 (`D8-web.md`); this report covers whole-app secrets, network, injection, intents, data-at-rest, and prompt-injection surfaces.

---

## 链路梳理 (Top Risk Surfaces)

```
1) Secrets at rest
   User API keys / WebDAV / S3 / MCP OAuth / web password
     → Preferences DataStore (plaintext JSON)  [PreferencesStore]
     → Manual backup ZIP settings.json         [BackupArchive]
     → Optional Web SSE /api/events full Settings  [EventsRoutes]  ← D8 F8-1

2) Network egress
   AI providers / search / TTS/ASR / MCP / S3 / WebDAV / skills GitHub
     → OkHttp + RequestLoggingInterceptor (full headers+body when enabled)
     → HttpLoggingInterceptor.Level.HEADERS (logcat Authorization risk)
     → android:usesCleartextTraffic=true (global cleartext allow)

3) Local web server (LAN remote control)
   SettingWebPage → WebServerService → WebServerManager
     → bind 0.0.0.0 | 127.0.0.1, HTTP only, CORS anyHost
     → JWT optional (default OFF) → full chat + tool-approval + files + Settings SSE

4) AI tool execution
   Model tool_calls → ChatService
     → workspace_shell / read|write|edit (PRoot + heuristic ShellPolicy)
     → MCP callTool (remote schemas/results)
     → clipboard_tool, get_logs, skills, search
     → Web tool-approval can drive same pipeline remotely

5) Content → WebView
   Assistant/user markdown → buildMarkdownPreviewHtml (base64) → WebView JS on
     → marked/KaTeX/Mermaid → innerHTML
     → WebViewPage also loads arbitrary url string when non-empty

6) Intents / export
   Exported RouteActivity (SEND/PROCESS_TEXT), ShortcutHandler, McpOAuthCallback
   FileProvider (broad cache/files/external), WorkspaceDocumentsProvider (SAF)
   Backup ZIP / share without encryption
```

**Default posture (mitigating):** web server off; localhost-only default; JWT off but UI warns LAN without JWT; workspace shell needs approval by default (`workspace_shell` default approval true); path containment helpers + tests for zip/files; LogRedaction on `get_logs` tool path; MCP OAuth state binding; no hardcoded production API keys found in source.

---

## 问题清单

### F12-1 — Web SSE dumps full `Settings` (API keys, sync secrets, OAuth tokens)

- **file:line:** `app/src/main/java/me/rerere/rikkahub/web/routes/EventsRoutes.kt:44-47`
- **severity:** CRITICAL
- **description:** `/api/events` serializes the entire `Settings` object. That includes provider `apiKey`s, WebDAV password, S3 keys, MCP headers/OAuth tokens, TTS/ASR keys, and `webServerAccessPassword`. Any client that can open the stream (unauthenticated when JWT off, or with a valid token when on) receives long-lived cloud credentials → quota burn / data exfiltration / backup theft.
- **evidence:**
```kotlin
val settingsEvents = settingsStore.settingsFlow.map { settings ->
    EventPayload(event = "settings", json = JsonInstant.encodeToString(settings))
}
```
- **suggested fix:** Emit a web-safe DTO only (ids/names, no secrets). Never serialize raw `Settings` over the web channel. Cross-ref D8 F8-1.

### F12-2 — Web API open by default when JWT disabled + optional LAN bind

- **file:line:** `app/.../web/WebApiModule.kt:169-186`; `WebServerManager.kt:24-25,65`; `web/.../Entry.kt:18-30`; `PreferencesStore.kt` defaults `webServerJwtEnabled=false`
- **severity:** CRITICAL
- **description:** With JWT disabled (default), conversation/settings/files/events routes register without `authenticate`. User can bind `0.0.0.0` (LAN). CORS `anyHost()` + `anyMethod()`. Cleartext HTTP. LAN peer can read chats, send messages (spend API quota), approve tools (shell/MCP), upload/download under `filesDir`, and receive F12-1 secrets.
- **evidence:**
```kotlin
} else {
    conversationRoutes(...)
    eventsRoutes(...)
    settingsRoutes(...)
    filesRoutes(...)
}
// startWebServer host default "0.0.0.0"; CORS anyHost/anyMethod
```
- **suggested fix:** Refuse non-loopback bind without JWT+password; default JWT on for LAN; tighten CORS; optional TLS. Cross-ref D8 F8-2/F8-4.

### F12-3 — Secrets stored in plaintext DataStore (not EncryptedSharedPreferences / Keystore)

- **file:line:** `app/.../data/datastore/PreferencesStore.kt:152,198,626,677,1073,1110`
- **severity:** HIGH
- **description:** Provider API keys, `webServerAccessPassword`, WebDAV/S3 configs, MCP OAuth tokens live in Preferences DataStore as plain JSON strings under app private storage. Rooted device, backup tools with elevated access, or malware with backup/read access can extract all keys. No `EncryptedSharedPreferences` / Android Keystore wrapping for secrets.
- **evidence:** `PROVIDERS` / `WEB_SERVER_ACCESS_PASSWORD` / `WEBDAV_CONFIG` / `S3_CONFIG` / `MCP_SERVERS` as `stringPreferencesKey` + `JsonInstant.encodeToString`.
- **suggested fix:** Encrypt secret fields with keys in Android Keystore (or EncryptedFile/DataStore); redact secrets from debug dumps; consider biometric gate for export.

### F12-4 — Manual / cloud backup archives contain unencrypted secrets

- **file:line:** `app/.../data/sync/BackupArchive.kt:61-65`; `WebDavSync.kt` / `S3Sync.kt` upload path
- **severity:** HIGH
- **description:** `BackupArchive.create` writes full `settings` JSON into ZIP (`settings.json`) plus optional DB. WebDAV/S3 restore path uploads that ZIP. No archive encryption/password. Anyone with the ZIP or access to the remote bucket/share gets all API keys and conversation DB content.
- **evidence:**
```kotlin
addVirtualFileToZip(zipOut, name = "settings.json", content = json.encodeToString(settings))
```
- **suggested fix:** Optional AES password for export; exclude secrets by default with explicit “include API keys” toggle; encrypt at rest on remote with user passphrase.

### F12-5 — Global cleartext traffic allowed

- **file:line:** `app/src/main/AndroidManifest.xml:59`
- **severity:** HIGH
- **description:** `android:usesCleartextTraffic="true"` allows HTTP for all app networking (providers, custom base URLs, S3 `http://`, local web server). Enables MITM on hostile networks for API keys in `Authorization` headers and conversation content. No `networkSecurityConfig` domain allowlist found.
- **evidence:**
```xml
android:usesCleartextTraffic="true"
```
- **suggested fix:** Set false by default; use Network Security Config with cleartext only for `localhost` / user-explicit endpoints; warn on non-HTTPS provider base URLs.

### F12-6 — OkHttp `HttpLoggingInterceptor` logs request headers (incl. Authorization)

- **file:line:** `app/.../di/DataSourceModule.kt:446-450`
- **severity:** HIGH
- **description:** Shared OkHttp client always adds `HttpLoggingInterceptor` at `Level.HEADERS`. Authorization / API-key headers can appear in logcat. Combined with debuggable builds or `adb logcat`, secrets leak. Separate `RequestLoggingInterceptor` also stores full headers+body when request logging is enabled (user-facing Log page; AI `get_logs` redacts).
- **evidence:**
```kotlin
.addNetworkInterceptor(RequestLoggingInterceptor())
.addInterceptor(HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.HEADERS
})
```
- **suggested fix:** Disable HEADER logging in release; redact Authorization in a custom logger; never enable BODY in production.

### F12-7 — JWT signed with user password; 30-day TTL; query-string token

- **file:line:** `app/.../web/WebApiModule.kt:46-47,191-221`
- **severity:** HIGH
- **description:** HMAC256 secret is the access password (weak passwords ⇒ forgeable JWTs). TTL 30 days. Token accepted via `?access_token=` (logs, Referer, history). Password change invalidates if secret changes—good—but no min entropy.
- **evidence:** `WEB_JWT_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000`; `createWebJwt(accessPassword)`; `extractAccessToken(..., queryToken)`.
- **suggested fix:** Derive signing key (HKDF); short TTL + refresh; Authorization header only (blob URLs for media); min password length. Cross-ref D8 F8-5/F8-6.

### F12-8 — Workspace shell: heuristic policy is not a security boundary

- **file:line:** `workspace/.../WorkspaceShellPolicy.kt:3-8,47-95`; `WorkspaceManager.kt:205-248`; `app/.../tools/WorkspaceTools.kt:30-35`
- **severity:** HIGH (when shell enabled / auto-approved or remote-approved)
- **description:** File documents heuristic-only blocking (`rm -rf /`, sensitive path substrings, `../` only when combined with sensitive paths). Real isolation depends on PRoot/cwd. Policy can be bypassed with encoding, alternate paths, or commands not covered by regexes. Default `workspace_shell` needsApproval=true, but web tool-approval or user auto-approve removes the gate. Model-driven RCE within sandbox + possible host impact depending on bind mounts.
- **evidence:**
```kotlin
 * **Heuristic interception only — not a security boundary.**
fun evaluateShellCommand(command: String): ShellCommandVerdict { ... }
"workspace_shell" to true  // default needsApproval
```
- **suggested fix:** Keep mandatory approval for shell on web; stronger sandbox; deny-by-default command allowlists for untrusted profiles; never auto-approve shell for remote clients.

### F12-9 — WebView: JS enabled; model/user content → HTML; optional arbitrary URL

- **file:line:** `app/.../ui/components/webview/WebView.kt:90-128,156-164`; `MarkdownWeb.kt:24-30`; `assets/html/mark.html` (~260 `innerHTML`); `WebViewPage.kt:50-75`
- **severity:** HIGH (XSS / local content abuse) / MEDIUM (URL load)
- **description:** WebView always enables JavaScript and DOM storage. Markdown preview base64-decodes content and sets `innerHTML` after marked/KaTeX/Mermaid—XSS depends on renderer sanitization; malicious assistant/user content can attempt script injection. `WebViewPage(url)` loads any URL when `url` non-empty (navigation type; currently internal contentId path preferred). No `shouldOverrideUrlLoading` allowlist; no mixed-content lock-down; `addJavascriptInterface` supported by API (empty by default).
- **evidence:**
```kotlin
settings.javaScriptEnabled = true
// mark.html: document.getElementById('content').innerHTML = html;
if (url.isNotEmpty()) rememberWebViewState(url = url, ...)
```
- **suggested fix:** Sanitize HTML (DOMPurify); disable JS where possible; block navigation off allowlist; `MIXED_CONTENT_NEVER_ALLOW`; avoid JavascriptInterface; treat model HTML as untrusted.

### F12-10 — Prompt / tool injection surfaces (MCP schemas, skills, workspace files, subagents)

- **file:line:** `app/.../data/ai/mcp/McpSessionRegistry.kt:314+` (listTools → settings); `data/ai/tools/SkillsTools.kt:29-40`; `SlashSkillPrompt.kt:7-27`; `WorkspaceTools` read_file results into context
- **severity:** HIGH (trust-boundary design) / MEDIUM (practical abuse)
- **description:** Remote MCP servers supply tool names/descriptions/schemas that become model-callable tools. Skill SKILL.md bodies and user-activated skill text are injected as system/instruction content (“override default assistant behavior”). Workspace file contents and shell stdout return into the model context. Subagent prompts carry transferred context. A malicious MCP server, skill package (GitHub install), or file can steer tools (exfil via shell/HTTP, overwrite skills, abuse clipboard).
- **evidence:** Skills system prompt lists skill bodies; MCP `listTools` persists schemas; `use_skill` reads arbitrary skill-dir files (path-checked).
- **suggested fix:** Treat MCP/skills as untrusted; user confirm new MCP tools; sandbox skill install; strip instruction-like content from tool results where feasible; separate trust tiers for subagents.

### F12-11 — Request logs store raw secrets; clipboard write logs plaintext

- **file:line:** `app/.../data/ai/RequestLoggingInterceptor.kt:18-59`; `common/.../LogRedaction.kt` (redact only on tool path); `utils/ContextUtil.kt:66-73`
- **severity:** MEDIUM–HIGH
- **description:** When request logging is on, headers/bodies (with API keys) are stored for the Log UI without redaction at write time. `get_logs` redacts for the model—good—but on-device Log page and backups of log buffers may retain secrets. `writeClipboardText` logs full text at Info (`Log.i(..., text)`), leaking passwords/keys if user copies them via app features.
- **evidence:**
```kotlin
requestHeaders = requestHeaders, requestBody = requestBody  // unredacted store
Log.i(TAG, "writeClipboardText: $text")
```
- **suggested fix:** Redact at ingestion; never log clipboard contents; clear logs on backup export or exclude them.

### F12-12 — Android Auto Backup / device transfer includes user files; `allowBackup=true`

- **file:line:** `AndroidManifest.xml:50-54`; `res/xml/backup_rules.xml`; `data_extraction_rules.xml`
- **severity:** MEDIUM
- **description:** `allowBackup=true`. Rules include `file/upload/` and `external/workspaces/` for cloud backup and device transfer. Workspaces may contain sensitive project data; uploads may contain documents. DataStore secrets appear intentionally not included by narrow includes (positive), but workspace/file leakage to cloud backup still sensitive. Confirm OEM behavior for unlisted domains.
- **evidence:** `<include domain="file" path="upload/" />`, workspaces external path.
- **suggested fix:** Review whether workspaces should be cloud-backed; document; consider `allowBackup=false` for high-sensitivity forks or encrypt workspace at rest.

### F12-13 — FileProvider paths are broad (entire cache / files / external-files)

- **file:line:** `app/src/main/res/xml/file_paths.xml:1-11`
- **severity:** MEDIUM
- **description:** `cache-path`, `files-path`, `external-files-path` all use `path="."`, so any granted URI under those trees is shareable. A confused-deputy or overly broad `grantUriPermission` could expose more than intended (e.g. cache contents beyond a single export file).
- **evidence:**
```xml
<cache-path name="cache" path="." />
<files-path name="upload" path="." />
```
- **suggested fix:** Narrow to specific subdirs (`export/`, `share/`); use unique one-off files; FLAG_GRANT_READ_URI_PERMISSION only.

### F12-14 — Exported activities / deep links (OAuth, shortcuts, share)

- **file:line:** `AndroidManifest.xml:67-126`; `McpOAuthCallbackActivity.kt:19-34`; `ShortcutHandlerActivity.kt`
- **severity:** MEDIUM (OAuth) / LOW (share)
- **description:** `RouteActivity` exported with SEND/PROCESS_TEXT (expected). `McpOAuthCallbackActivity` exported + BROWSABLE `rikkahub://mcp-oauth-callback` — coordinator filters callback by OAuth `state` (good). Residual risk: other apps can fire the deep link; without matching state it should no-op, but ensure no side effects on malformed intents. `ShortcutHandlerActivity` exported for `rikkahub://shortcut` always launches camera permission flow (nuisance / unexpected UX). `UCropActivity` lacks explicit `exported` (library).
- **evidence:** OAuth `first { it.state == state }`; shortcut opens camera on any VIEW to host.
- **suggested fix:** Verify package/signature where possible; ignore non-matching intents early; set exported explicitly on third-party activities.

### F12-15 — JWT enablement snapshotted at server start

- **file:line:** `WebApiModule.kt:69,90,170`
- **severity:** HIGH (operational)
- **description:** `jwtEnabled` read once at configure time. Enabling JWT in UI without restart leaves API open. Cross-ref D8 F8-7.
- **suggested fix:** Restart server on auth policy change or disable toggle while running.

### F12-16 — S3/WebDAV allow HTTP endpoints; credentials in settings

- **file:line:** `data/sync/s3/S3Config.kt:18-33`; WebDAV client basicAuth
- **severity:** MEDIUM
- **description:** S3 client builds `http://` when endpoint not `https://`. Basic auth / SigV4 over cleartext exposes secrets. Combined with F12-5.
- **suggested fix:** Default require HTTPS; warn/block cleartext sync.

### F12-17 — Skills install downloads from GitHub `download_url` (SSRF-ish / supply chain)

- **file:line:** `ui/pages/extensions/skills/SkillsVM.kt:96-131,316`
- **severity:** MEDIUM
- **description:** Skill install follows GitHub Contents API then `URL(url).openConnection()` for each `download_url`. Relies on GitHub returning trusted URLs; if API is MITM’d (cleartext) or compromised, arbitrary content written into skill dirs later injected into prompts (F12-10). No content signing.
- **suggested fix:** Pin host allowlist (`raw.githubusercontent.com`, `api.github.com`); HTTPS only; size limits; user review before enable.

### F12-18 — Image save / fetch arbitrary `http(s)` URLs

- **file:line:** `data/files/FilesManager.kt:304-319`
- **severity:** MEDIUM (SSRF to local network)
- **description:** `saveMessageImage` for `http` prefixes opens `URL(image).openConnection()` with no host allowlist—can hit link-local/cloud metadata if model/user supplies URL (and user triggers save).
- **suggested fix:** Block private IP ranges; HTTPS-only option; size/timeout limits.

### F12-19 — Web exception messages returned to clients

- **file:line:** `WebApiModule.kt:82-86`
- **severity:** MEDIUM
- **description:** Generic handler returns `cause.message` to HTTP clients—may leak paths or internal errors. Cross-ref D8 F8-10.
- **suggested fix:** Opaque errors to client; full log server-side.

### F12-20 — No rate limit on web password auth

- **file:line:** `WebApiModule.kt:142-166`
- **severity:** MEDIUM
- **description:** `secureEquals` is constant-time for equal-length compares (good) but no lockout/backoff on `POST /api/auth/token`. Cross-ref D8 F8-11.
- **suggested fix:** Exponential backoff / attempt cap per interface.

### F12-21 — Room / SQLite not encrypted (conversation content at rest)

- **file:line:** `di/DataSourceModule.kt` Room setup; conversation entities
- **severity:** MEDIUM
- **description:** Chat history, tool results, possibly pasted secrets live in unencrypted SQLite. Physical access / backup of DB file exposes content. Manual backup includes DB when selected.
- **suggested fix:** Optional SQLCipher; device encryption reliance documented; exclude DB from unencrypted cloud backup by default.

### F12-22 — Shell / path traversal defenses present but incomplete for absolute escapes

- **file:line:** `WorkspaceShellPolicy.kt:78-93`; `WorkspaceFileSystem` / `FilePathSecurity.kt` (good patterns)
- **severity:** LOW–MEDIUM
- **description:** `../` alone is not rejected unless sensitive path substrings match—relies on filesystem resolve confinement. File APIs use canonical path checks (`resolveContainedFile`, rootfs `safeResolve`)—good, tested. Residual: shell can still read within sandbox freely once approved.
- **suggested fix:** Document trust model; optional read-only mode for high-risk assistants.

### F12-23 — `PACKAGE_USAGE_STATS` / calendar permissions

- **file:line:** `AndroidManifest.xml:18-22`
- **severity:** LOW (privacy)
- **description:** Sensitive permissions increase privacy blast radius if app or tools are abused. Ensure tools requiring them are opt-in and disclosed.
- **suggested fix:** Runtime rationale; disable tools when permission absent.

### F12-24 — GITHUB_API_TOKEN in BuildConfig (optional)

- **file:line:** `app/build.gradle.kts` (~81-111)
- **severity:** LOW
- **description:** Optional `github.api.token` baked into BuildConfig for update checks. If set in CI/local properties, token ships inside APK (extractable). Empty by default—OK.
- **suggested fix:** Prefer unauthenticated API + cache; never commit tokens; use short-lived CI-only tokens not in release APKs.

### F12-25 — Hardcoded production secrets scan

- **file:line:** n/a (repo-wide pattern scan)
- **severity:** (none found as CRITICAL)
- **description:** No live `sk-…`, `AIza…`, `ghp_…`, or PEM private keys in production sources. Test fixtures use fake secrets. Provider defaults use `apiKey = ""`. Positive finding.
- **suggested fix:** Keep secret scanning in CI; block commit of `local.properties` / keystores (already in guidelines).

---

## 亮点 / 可复用

| Practice | Location | Why it helps |
|----------|----------|--------------|
| Log redaction for AI tool | `common/.../LogRedaction.kt`, `LogsTool.kt` | Model cannot easily exfil raw Auth headers via `get_logs` |
| Path containment | `FilePathSecurity.resolveContainedFile`, rootfs `safeResolve`, Web files `..` + canonical check | Stops classic zip/path traversal |
| Backup restore path checks | `BackupRestorer` + skill path resolve | Zip slip mitigated for uploads/skills |
| MCP OAuth state binding | `McpOAuthCoordinator.awaitCallback... first { it.state == state }` | Mitigates bare deep-link code injection |
| MCP OAuth `toString` masking | `McpOAuthState.toString` | Avoids accidental token logging via config print |
| Constant-time password compare | `WebApiModule.secureEquals` | Basic auth brute-force timing hygiene |
| Web localhost default + UI LAN warning | `PreferencesStore` / `SettingWebPage` | Reduces accidental open LAN |
| Workspace shell default needsApproval | `WorkspaceTools` | Human gate before shell |
| WebView content cache ID = sha256 only | `WebViewContentCache` | Blocks `../` contentId traversal (tested) |
| Markdown preview via base64 placeholder | `MarkdownWeb.kt` | Avoids raw string breakout of template (XSS still depends on marked) |
| No hardcoded prod API keys | repo scan | Clean secret hygiene in source |

---

## 遗漏与风险

1. **Dynamic / runtime only:** Certificate pinning, WebView XSS with real marked versions, PRoot escape, OEM backup quirks—not fully verifiable statically.
2. **web-ui client:** Auth gate is client-side; security must be server-side (currently weak when JWT off)—D8 covers more UI detail.
3. **Native libs (proot):** Memory safety / sandbox escape out of scope for Kotlin static pass.
4. **Third-party SDK:** Search/TTS/ASR provider implementations may log differently—spot-check per provider was limited.
5. **Intent redirection / PendingIntent:** WebServer notification uses `FLAG_IMMUTABLE` (good); other PendingIntents not exhaustively audited.
6. **SQL injection:** Room `@Query` dominant; raw `execSQL` in migrations uses fixed strings—low SQLi risk from user input; FTS search parameterization assumed via Room (verify any dynamic raw query builders if added later).
7. **Overlap with D8:** F12-1/2/7/15/19/20 duplicate D8 CRITICAL/HIGH items intentionally for global security rollup.

---

## Severity summary

| Severity | Count (approx) | Themes |
|----------|----------------|--------|
| CRITICAL | 2 | Web Settings SSE secrets; open LAN API |
| HIGH | 9 | Plaintext secrets, backup, cleartext, header logs, JWT design, shell trust, WebView, prompt injection, JWT snapshot |
| MEDIUM | 10 | Clipboard logs, backup rules, FileProvider, intents, HTTP sync, skills/SSRF, DB plaintext, auth rate limit, errors |
| LOW | 4 | Permissions, BuildConfig token, residual shell, public static assets |

**Priority fix order:** (1) Never stream raw Settings / require auth for LAN (2) Redact/encrypt secrets at rest & in backups (3) Kill release header logging + cleartext default (4) Harden shell/WebView/MCP trust boundaries.
