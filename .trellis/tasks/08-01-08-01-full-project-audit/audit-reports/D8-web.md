# D8 — Web Server Module Security + Correctness Audit

**Scope:** `web/` (Ktor host + static SPA), `app/.../web/**` (API, lifecycle, routes), `web-ui/` (auth client, SSE, file URL — low priority scan)  
**Mode:** Static analysis only (no build/run/modify of production code)  
**Date:** 2026-08-01  

---

## 链路梳理

```
App / SettingWebPage
  → Intent ACTION_START|STOP → WebServerService (FGS specialUse)
    → WebServerManager.start(port, localhostOnly)
      → startWebServer(host=0.0.0.0|127.0.0.1)  [web/Entry.kt]
         ├ install Compression, CORS(anyHost/anyMethod), SSE, DefaultHeaders
         ├ staticResources("/", "static") + SPA fallback
         └ configureWebApi(...)                 [WebApiModule.kt]
              ├ ContentNegotiation(JsonInstant)
              ├ StatusPages
              ├ optional Authentication jwt("auth-jwt")  // snapshot at start
              └ /api
                   ├ POST /auth/token          (public when JWT mode)
                   ├ GET  /ai-icon             (always public)
                   └ [authenticate?] 
                        conversations|folders|events|settings|files|assets

LAN discovery (non-localhost): NsdServiceRegistrar → JmDNS _http._tcp
```

**Capability surface (authenticated only if `webServerJwtEnabled` was true at process start):**

| Area | Endpoints (representative) | Effect |
|------|---------------------------|--------|
| Conversations | list/paged/search/CRUD, send, edit, fork, regenerate, stop, tool-approval, SSE stream | Full chat control + tool approval (workspace/MCP tools via ChatService) |
| Folders | list/create/rename/delete | Org structure |
| Events SSE | `/api/events` | **Full `Settings` JSON**, list invalidation, folders |
| Settings mutators | assistant/model/MCP/injections/search/tools/favorites | Mutate app settings (not full Settings write) |
| Files | upload/delete/get by id/path | Read/write under `context.filesDir` |
| Assets | `/api/assets/{path...}` | Read packaged assets |
| Static | `/` SPA | Unauthenticated UI shell |

**Defaults (PreferencesStore):** `webServerEnabled=false`, `webServerLocalhostOnly=true`, `webServerJwtEnabled=false`, port `8080`. UI warns when LAN + no JWT.

---

## 问题清单

### F8-1 — Full `Settings` dumped over SSE (API keys / secrets)

- **file:line:** `app/src/main/java/me/rerere/rikkahub/web/routes/EventsRoutes.kt:45-47`
- **severity:** CRITICAL
- **description:** `/api/events` encodes the entire `Settings` object with `JsonInstant` (`encodeDefaults=true`). `Settings` includes `providers[].apiKey`, `webDavConfig.password`, `s3Config.accessKeyId/secretAccessKey`, `mcpServers` headers + `oauth.accessToken/refreshToken/clientSecret`, `ttsProviders`/`asrProviders` keys, and `webServerAccessPassword`. Any client that can open the events stream receives long-lived cloud credentials and can burn tokens or exfiltrate backups.
- **evidence:**
```kotlin
val settingsEvents = settingsStore.settingsFlow.map { settings ->
    EventPayload(event = "settings", json = JsonInstant.encodeToString(settings))
}
```
`ProviderSetting.OpenAI.apiKey`, `WebDavConfig.password`, `S3Config.secretAccessKey`, `McpOAuthState.accessToken` are all `@Serializable` fields (not `@Transient`).
- **suggested fix:** Emit a **web-safe DTO** (assistants metadata, model ids/names without keys, search service *names* only, MCP server ids/names without headers/tokens, no WebDAV/S3/TTS/ASR secrets, no access password). Never serialize raw `Settings` to the web channel. Redact on both initial snapshot and updates.

### F8-2 — API fully open when JWT disabled (default) + optional LAN bind

- **file:line:** `WebApiModule.kt:169-186`, `WebServerManager.kt:24-25,65`, `PreferencesStore.kt:1109-1111`, `Entry.kt:20,25-30`
- **severity:** CRITICAL
- **description:** When `webServerJwtEnabled` is false (default), all conversation/settings/files/events routes are registered **without** `authenticate`. Combined with `localhostOnly=false` (user can disable; default is true but UI allows LAN) and bind host `0.0.0.0`, any host on the LAN (or other apps on-device via non-loopback if bound all-interfaces) can: read all conversations (by id), send messages (spend API quota), approve tools (workspace/shell/MCP), upload/download files under app `filesDir`, and receive F8-1 secrets. CORS is `anyHost()` + `anyMethod()`, so browser origins are unrestricted. No TLS.
- **evidence:**
```kotlin
} else {
    conversationRoutes(...)
    eventsRoutes(...)  // full Settings
    settingsRoutes(...)
    filesRoutes(...)
}
// host default "0.0.0.0"; CORS anyHost/anyMethod
```
- **suggested fix:** (1) Require JWT whenever `!localhostOnly`; refuse start if password blank. (2) Prefer default JWT-on for any non-loopback bind. (3) Consider binding only Wi‑Fi LAN address instead of `0.0.0.0`. (4) Tighten CORS to same-origin / known hosts. (5) Document cleartext risk; optional TLS or reverse-proxy guidance.

### F8-3 — Cross-conversation / cross-assistant IDOR

- **file:line:** `ConversationRoutes.kt:144-151,154-161,271-286,358-363`; `search` at `125-141` + `ConversationRepository.searchMessages` (global FTS)
- **severity:** CRITICAL (with unauth) / HIGH (with shared JWT)
- **description:** `GET/DELETE /conversations/{id}`, message send/edit/delete, tool-approval, stream, etc. load by UUID with **no check** that the conversation belongs to the current assistant or that the caller is limited to a subset. `GET /conversations/search` calls `conversationRepo.searchMessages(query)` which indexes **all** conversations, not only `settings.assistantId`. Single shared password JWT means any holder is superuser for the whole device dataset—acceptable for single-user LAN only if documented; still an authorization gap for multi-user or leaked token.
- **evidence:**
```kotlin
val conversation = conversationRepo.getConversationById(uuid)
    ?: throw NotFoundException("Conversation not found")
call.respond(conversation.toDto(isGenerating))
// search:
val results = conversationRepo.searchMessages(query) // no assistant filter
```
- **suggested fix:** Scope list/search to assistant (or explicit multi-assistant ACL). For `{id}` routes, verify ownership or return 404. Consider per-session scopes if multi-user ever appears.

### F8-4 — Tool approval over HTTP = remote code/tool execution path

- **file:line:** `ConversationRoutes.kt:358-363` → `ChatService.handleToolApproval` / `sendMessage`
- **severity:** CRITICAL (unauth or stolen token) / HIGH (auth present)
- **description:** Web client can `POST .../messages` (triggers generation with workspace/MCP tools attached by ChatService) and `POST .../tool-approval` to approve pending tools. On a compromised or open server this is remote control of the same tool pipeline as the phone UI (shell/workspace/MCP depending on assistant config).
- **evidence:** Route accepts `ToolApprovalRequest(toolCallId, approved, reason, answer)` with no extra confirmation channel or step-up auth.
- **suggested fix:** Require JWT always for mutating routes; optional “local confirm on device” for high-risk tools; never expose tool-approval without auth; rate-limit; audit log.

### F8-5 — JWT access token accepted via query string

- **file:line:** `WebApiModule.kt:47,104-110,219-221`; `web-ui/app/services/api.ts:31,133-143`; `web-ui/app/lib/files.ts:24,33`
- **severity:** HIGH
- **description:** `extractAccessToken` accepts `?access_token=`. Frontend `appendWebAuthQuery` puts the JWT on file image URLs. Tokens leak via proxy logs, Referer, browser history, screenshots, and shared links. TTL is **30 days** (`WEB_JWT_TTL_MILLIS`).
- **evidence:**
```kotlin
private const val WEB_ACCESS_TOKEN_QUERY_KEY = "access_token"
// authHeader uses queryToken fallback
private const val WEB_JWT_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
```
- **suggested fix:** Prefer short-lived cookie (HttpOnly, SameSite) or Authorization-only; for `<img>` use blob URLs fetched with header; shorten TTL (hours) + refresh; strip token from server access logs.

### F8-6 — Password is HMAC secret; weak password ⇒ forgeable JWT

- **file:line:** `WebApiModule.kt:191-209,97-102`
- **severity:** HIGH
- **description:** JWT is signed with `Algorithm.HMAC256(accessPassword)`. User-chosen short passwords are weak HMAC keys. Offline brute-force of captured JWTs is feasible. No min length/entropy enforced in UI beyond non-blank for enabling JWT.
- **evidence:** `createWebJwt(accessPassword)` / `buildWebJwtVerifier(secret)`.
- **suggested fix:** Derive signing key via HKDF/PBKDF2 from password + device salt stored at first enable; enforce min length; rotate key on password change (already invalidates old tokens if secret changes—keep that).

### F8-7 — JWT enablement snapshotted at server start; live toggle ineffective

- **file:line:** `WebApiModule.kt:69,90,170`; `SettingWebPage.kt:336-345` (JWT switch enabled while running)
- **severity:** HIGH
- **description:** `val jwtEnabled = settingsStore.settingsFlow.value.webServerJwtEnabled` is read once when configuring the application. Toggling JWT in settings while the server runs does **not** reinstall Authentication or re-wrap routes. User may believe LAN is protected after enabling JWT without restart—API stays open.
- **evidence:** UI allows changing `webServerJwtEnabled` without forcing stop/start (unlike port/localhost which are disabled while running for port/localhost only—JWT switch is **not** disabled while running).
- **suggested fix:** Disable JWT/password controls while running **or** auto-restart server on auth policy change; document “restart required”.

### F8-8 — `restart()` race / port check TOCTOU

- **file:line:** `WebServerManager.kt:136-150,74-81`
- **severity:** MEDIUM
- **description:** `restart()` calls `stop()` then `start()` without awaiting stop completion (`stop` launches async). Concurrent start may see `server != null` and no-op, or fail port bind. `isPortAvailable` opens `ServerSocket(port)` (all interfaces) then closes—race with another process between check and Ktor bind; also briefly binds all interfaces even for localhost-only mode.
- **evidence:**
```kotlin
fun restart(...) { stop(); start(port, serviceName, localhostOnly) }
private fun isPortAvailable(port: Int) = try { ServerSocket(port).use { true } } catch ...
```
- **suggested fix:** Serialize start/stop with Mutex; await engine stop; bind with SO_REUSEADDR handling inside Ktor and surface bind errors; for localhost-only probe `127.0.0.1` only.

### F8-9 — Path serving under entire `filesDir` (broad read)

- **file:line:** `FilesRoutes.kt:117-173`
- **severity:** MEDIUM (HIGH if unauthenticated)
- **description:** `GET /api/files/path/{path...}` serves any file under `context.filesDir` after `..` and canonical prefix checks. That includes uploads, images, skill trees, and other app-private files—not limited to managed upload IDs. Traversal basic checks exist (`..`, leading `/`, canonical prefix) but scope is still wide. Prefix check lacks trailing-separator hardening used in `FilePathSecurity.resolveContainedFile` (lower residual risk because construction is `File(filesDir, relativePath)`).
- **evidence:** Path join + `respondFile`; no allowlist of `upload/` / `images/`.
- **suggested fix:** Restrict to known folders (`FileFolders.UPLOAD`, images, etc.) or only managed file IDs; reuse `resolveContainedFile`; set `Content-Disposition` / disable HTML execution (`Content-Type` + `X-Content-Type-Options: nosniff`).

### F8-10 — Exception messages / StatusPages leak internals

- **file:line:** `WebApiModule.kt:82-86`
- **severity:** MEDIUM
- **description:** Generic `Throwable` handler returns `cause.message` to clients. Stack-origin messages may include paths, provider errors, or partial secrets from lower layers.
- **evidence:**
```kotlin
exception<Throwable> { call, cause ->
    call.respond(HttpStatusCode.InternalServerError,
        ErrorResponse(cause.message ?: "Internal server error", 500))
}
```
- **suggested fix:** Log full exception server-side; client gets opaque id + generic message.

### F8-11 — No rate limit on `POST /api/auth/token`

- **file:line:** `WebApiModule.kt:142-166`
- **severity:** MEDIUM
- **description:** Password guessing is only mitigated by `secureEquals` (good constant-time compare for equal lengths). No lockout, delay, or IP throttle—LAN attacker can hammer the endpoint.
- **suggested fix:** Per-IP/backoff lockout; CAPTCHA not needed on device—simple exponential delay + max attempts.

### F8-12 — Cleartext HTTP only; mDNS advertises service

- **file:line:** `Entry.kt` (no SSL); `NsdServiceRegistrar.kt:61-68`; `SettingWebPage` URLs `http://...`
- **severity:** MEDIUM
- **description:** All traffic is HTTP. On hostile Wi‑Fi, MITM can steal JWT (header or query), conversation content, and settings secrets. mDNS `_http._tcp` advertises name/port to the LAN, increasing discoverability.
- **suggested fix:** Warn strongly; optional self-signed TLS; consider not registering mDNS when JWT off; user education already partially present for LAN risk.

### F8-13 — `ai-icon` and static SPA always unauthenticated

- **file:line:** `WebApiModule.kt:168`; `Entry.kt:34-39`
- **severity:** LOW
- **description:** Expected for static hosting; `/api/ai-icon` is public even when JWT on. Low risk (icons only). SPA shell is public—API must remain the trust boundary (currently fails when JWT off).
- **suggested fix:** Keep public; ensure API never relies on “UI gate” alone (`WebAuthGate` is client-side only).

### F8-14 — Service intent default `localhost_only=false`

- **file:line:** `WebServerService.kt:48-49`
- **severity:** LOW–MEDIUM
- **description:** `getBooleanExtra(EXTRA_LOCALHOST_ONLY, false)` defaults to **false** (all interfaces) if extra omitted. Call sites currently pass extras; sticky/recreate paths with null extras use settings. Residual footgun for future callers.
- **suggested fix:** Default extra to `true` to match settings default.

### F8-15 — Upload loads whole file into memory (20MB cap)

- **file:line:** `FilesRoutes.kt:29,214-232`
- **severity:** LOW–MEDIUM
- **description:** Multipart parts fully buffered to `ByteArray` with 20MB cap—DoS via many concurrent uploads if unauthenticated/open LAN.
- **suggested fix:** Stream to disk; global concurrency limit; require auth.

### F8-16 — CORS `anyHost` + `allowHeader(Authorization)`

- **file:line:** `Entry.kt:25-30`
- **severity:** MEDIUM (with browser attackers on LAN)
- **description:** Any website can make credentialed-header requests from a victim browser to the phone’s IP if the user pastes a token into a malicious page, or if token is in JS-accessible storage (it is—`localStorage`). Combined with F8-1 this is high impact on open LAN.
- **suggested fix:** Restrict origins; avoid storing long-lived JWT in `localStorage` (memory + HttpOnly cookie).

### F8-17 — Password stored plaintext in DataStore

- **file:line:** `PreferencesStore.kt:198,359,677,1110`
- **severity:** MEDIUM (device compromise / backup export cross-domain)
- **description:** `webServerAccessPassword` stored as plain preferences string. Backup/export paths (D10) may ship it. Expected for local app secrets but increases blast radius of F8-1 if settings leave the device.
- **suggested fix:** Hash for verification + separate random JWT signing key; never echo password over SSE (F8-1).

### F8-18 — Canonical path prefix check weaker than shared helper

- **file:line:** `FilesRoutes.kt:130-133` vs `FilePathSecurity.kt:6-15`
- **severity:** LOW
- **description:** Uses `startsWith(filesDir.canonicalPath)` without forcing trailing separator. `resolveContainedFile` is safer. Practical exploit via `File(filesDir, rel)` is limited; still inconsistent.
- **suggested fix:** Call `resolveContainedFile(filesDir, relativePath)`.

### F8-19 — SSE conversation stream holds conversation reference without auth re-check

- **file:line:** `ConversationRoutes.kt:367-451`
- **severity:** LOW (if JWT on) / HIGH (if JWT off)
- **description:** Long-lived SSE after connect; auth is connection-time only (normal). Without JWT, anyone can subscribe to live generation including tool args/results.
- **suggested fix:** Covered by mandatory auth (F8-2).

---

## 亮点 / 可复用

1. **Localhost-only default + LAN risk dialogs** (`webServerLocalhostOnly=true`, SettingWebPage risk UI) — good product security defaults.
2. **Optional JWT with dynamic password verifier** — password change invalidates old HMAC tokens without restart (when JWT mode was on at start).
3. **`secureEquals` via `MessageDigest.isEqual`** on password check.
4. **File path basic traversal guards** (`..`, absolute path, canonical check) and upload size cap + display-name sanitization.
5. **Foreground service + notification** with stop action; service `exported=false`.
6. **SSE multiplex design** (`/api/events` + per-conversation stream) and node-diff optimization (`ConversationDiff`) — solid architecture if payload is redacted.
7. **Injection ID validation** against known mode/lorebook/MCP ids before settings mutation.
8. **Web UI auth gate + 401 → re-login** pattern (`web-auth-gate.tsx`, api hooks).

---

## 遗漏与风险

| Gap | Note |
|-----|------|
| No automated security tests for web auth / redaction | Unit tests cover ConversationDiff; no tests that Settings SSE omits secrets |
| TLS / certificate pinning N/A | Cleartext by design today |
| Multi-user ACL | Single shared password model only |
| Workspace path not directly exposed as HTTP FS API | Tools still reachable via chat + approval (F8-4) |
| Static resource MIME/cache | Ktor defaults; SPA `singlePageApplication` — low risk |
| JWT algorithm confusion | HMAC-only fixed — OK |
| Auth header missing password when JWT on | Routes closed with random verifier secret — OK |
| `WebAuthGate` is UX only | Must not be treated as server auth |
| Cross-audit | **D12 security** should re-confirm key storage; **D10 backup** export of password; **D5 workspace** tool risk via web approval |
| Runtime not executed | Bind behavior on OEM FGS denial observed in code paths only |

---

## Severity summary

| Sev | IDs |
|-----|-----|
| CRITICAL | F8-1, F8-2, F8-3 (unauth), F8-4 (unauth) |
| HIGH | F8-3 (shared token), F8-4 (auth), F8-5, F8-6, F8-7 |
| MEDIUM | F8-8, F8-9, F8-10, F8-11, F8-12, F8-15, F8-16, F8-17 |
| LOW | F8-13, F8-14, F8-18, F8-19 |

**Top remediation order:** (1) Redact Settings SSE DTO, (2) force auth off-loopback + restart on policy change, (3) scope conversation access, (4) remove query tokens / shorten JWT, (5) separate signing key from password.

---

## API surface checklist (for integrators)

```
POST /api/auth/token
GET  /api/ai-icon?name=
GET  /api/conversations | /paged | /search | /{id}
DELETE /api/conversations/{id}
POST /api/conversations/{id}/pin|title|injections|move|folder|messages|fork|regenerate|stop|tool-approval|regenerate-title
POST /api/conversations/{id}/messages/{messageId}/edit
DELETE /api/conversations/{id}/messages/{messageId}
POST /api/conversations/{id}/nodes/{nodeId}/select
SSE  /api/conversations/{id}/stream
GET|POST|DELETE /api/folders...
SSE  /api/events          ← settings | conversation_list_invalidate | folders
POST /api/settings/...
POST /api/files/upload | DELETE /api/files/{id} | GET /api/files/id|path/...
GET  /api/assets/{path...}
GET  /* static SPA
```
