# D4 Audit — Tools / MCP / Subagent / Search

**Scope:** Tool registry & dispatch, workspace sandbox safety, MCP client lifecycle, subagent spawn/result, search providers & chat integration  
**Mode:** Static analysis only (no Gradle / no runtime)  
**Date:** 2026-08-01  
**Package roots:**
- Tools: `app/.../data/ai/tools`, `ai/.../core/Tool.kt`
- MCP: `app/.../data/ai/mcp`
- Subagent: `app/.../data/ai/subagent`
- Search: `search/src/main/java/me/rerere/search`
- Workspace sandbox: `workspace/src/main/java/me/rerere/workspace`
- Assembly: `app/.../service/ChatService.kt`, `app/.../data/ai/GenerationHandler.kt`

---

## 1. 链路梳理 (Call Chain)

### 1.1 Tool assembly → provider → execute → result

```
ChatService.prepareGeneration
  └─ finalizeGenerationTools(buildGenerationTools(...), assistant.toolPermissions)
       ├─ createSearchTools(settings)          // if enableWebSearch
       ├─ LocalTools.getTools(assistant.localTools)
       ├─ createConversationTools / memory / memory_table
       ├─ createWorkspaceToolsIfReady(...)     // Proot rootfs ready only
       ├─ createSkillTools / buildSkillManagementTools
       ├─ MCP: mcpManager.getAllAvailableTools → Tool("mcp__${server}__${name}")
       └─ buildSubagentToolsForChat → createSubagentTools + manage_subagent_profile
  └─ GenerationHandler.generateText / prepareFirstProviderInput
       ├─ buildToolsForStep (+ memory tools if enableMemory)
       ├─ prepareProviderInput: tool.systemPrompt() concatenated into system message
       ├─ provider stream → UIMessagePart.Tool (pending)
       ├─ needsApproval → ToolApprovalState.Pending (HITL break)
       └─ executeSingleTool → toolDef.execute(args) → maybeTruncateToolOutput → next step
```

**Schema:** `me.rerere.ai.core.Tool` (`name`, `description`, `parameters: () -> InputSchema?`, `systemPrompt`, `needsApproval`, `execute`).  
**Permission gate:** `ToolPermissionPolicy.applyToolPermission` / `finalizeGenerationTools` (DENY drops tool; ASK forces approval).  
**MCP stable ids:** `mcp:$serverId:$toolName` for permission keys; runtime names `mcp__ServerName__tool`.

### 1.2 MCP

```
Settings.mcpServers flow → McpSessionRegistry.reconcile
  ├─ connect (SSE / Streamable HTTP) + OAuth ensureFreshToken
  ├─ listTools → mergeTools into settings (schema/description)
  └─ status: Idle|Connecting|Connected|Reconnecting|NeedsAuthorization|Error

Generation inject:
  getAllAvailableTools(assistant)
    filter: enable && id in assistant.mcpServers && status == Connected && tool.enable
  callTool(serverId, name, args) → TextContent / ImageContent→FilesManager

Probe-only: McpManager.testConnection → runSafeMcpProbe (connect + listTools only; no callTool)
```

### 1.3 Subagent

```
spawn_subagent.execute
  → SubagentHost.spawn
       ├─ resolve profile (assistant + global)
       ├─ depth / maxDepth gate
       ├─ acquireSubagentContext (create | reuse_context_id | latest completed same scope)
       ├─ buildChildAssistant + toolsForSubagentProfile
       │     ├─ createSubagentWorkspaceTools (access + path prefixes + approval)
       │     ├─ buildSubagentTools (inherit parent tools / exclude / canSpawn nest)
       │     └─ sandboxToolsForSubagent (identity; approvals preserved)
       ├─ GenerationHandler loop (tool budget, summary continuation)
       └─ SubagentResult → slim JSON payload + rich metadata (transcript UI-only)

Cancel: SubagentSessionRegistry.requestCancel(conversationId)
```

### 1.4 Search

```
createSearchTools(settings)
  search_web / scrape_web (if service.scrapingParameters != null)
    → SearchService.getService(selected options)
    → search/scrape(params, searchCommonOptions, serviceOptions)
    → Result encoded as Text tool output (+ short citation ids)

Providers (SearchService companion when):
  BingLocal, RikkaHub, Zhipu, Tavily, Exa, SearXNG, LinkUp, Brave, Metaso,
  Ollama, Perplexity, Firecrawl, Jina, Bocha, Grok, Tinyfish, Serper, CustomJs
```

HTTP: shared `SearchService.httpClient` (30s read timeout, redirects on). Keys via settings / `KeyRoulette` multi-key rotation. No hardcoded production API keys found in search module.

### 1.5 Workspace shell path

```
workspace_shell → WorkspaceRepository.executeCommand
  → WorkspaceManager.executeCommand
       ├─ resolve cwd under filesDir (canonical path check)
       ├─ evaluateShellCommand (heuristic reject only)
       └─ ProotShellRunner (production DI) → bash -c via proot rootfs
```

File tools: absolute rootfs paths; known mounts (`/skills`, `/skills_private`); write outside `/workspace|/tmp` forces approval.

---

## 2. Issues

### F4-1 — Custom JS search `fetch` has no SSRF / scheme guard

| Field | Value |
|-------|--------|
| **Severity** | **CRITICAL** |
| **File:line** | `common/src/main/java/me/rerere/common/js/QuickJSFetch.kt:53-91` (+ `search/.../CustomJsSearchService.kt:89-99`) |
| **Description** | User-authored Custom JS search/scrape scripts get a synchronous `fetch` polyfill that calls OkHttp with **any** URL (private RFC1918, link-local, cloud metadata, `http://127.0.0.1`, etc.). No allowlist, no block of non-http(s), no size limit on response body beyond default client. Compromised or careless scripts become a device-local SSRF / data-exfil channel while the model only sees search results. |
| **Evidence** | |
```kotlin
fun QuickJSContext.injectFetch(httpClient: OkHttpClient) {
    globalObject.setProperty("__httpRequest", JSCallFunction { args ->
        val url = args[0] as? String ?: error("url is required")
        // ...
        val requestBuilder = Request.Builder().url(url)
        val response = httpClient.newCall(requestBuilder.build()).execute()
        val responseBody = response.body.string()
```
| **Suggested fix** | Restrict schemes to `https` (optional `http` for LAN only behind explicit toggle); block private/link-local/metadata IP ranges; cap response body size; optional host allowlist; run scripts with shorter timeouts; document “Custom JS can reach the network” in UI. |

---

### F4-2 — `eval_javascript` never destroys QuickJS context (native leak)

| Field | Value |
|-------|--------|
| **Severity** | **HIGH** |
| **File:line** | `app/.../data/ai/tools/local/JavascriptTool.kt:37-72` |
| **Description** | Each tool call creates `QuickJSContext.create()` and never calls `destroy()`. Custom JS search correctly uses `try/finally { destroy() }`. Repeated `eval_javascript` in long agent loops can exhaust native memory and crash the process. |
| **Evidence** | |
```kotlin
execute = {
    val context = QuickJSContext.create()
    context.setConsole(...)
    val result = context.evaluate(code)
    // no context.destroy()
    listOf(UIMessagePart.Text(payload.toString()))
}
```
| **Suggested fix** | Mirror CustomJs: `try { ... } finally { context.destroy() }`; optional wall-clock / instruction budget. |

---

### F4-3 — Tool failures ship full Java stack traces to the model

| Field | Value |
|-------|--------|
| **Severity** | **HIGH** |
| **File:line** | `app/.../data/ai/GenerationHandler.kt:682-704` |
| **Description** | On tool exception (except cancellation), error payload includes class name, message, **and `stackTraceToString()`**. Stacks expose package layout, internal paths, and sometimes intermediate exception messages (URLs, partial tokens). Also wastes context tokens. |
| **Evidence** | |
```kotlin
put("error", JsonPrimitive(buildString {
    append("[${it.javaClass.name}] ${it.message}")
    append("\n${it.stackTraceToString()}")
}))
```
| **Suggested fix** | Return short sanitized message + stable error code; log full stack only to Logcat / redacted crash pipeline. |

---

### F4-4 — Shell policy is heuristic only; arbitrary command still runs in Proot

| Field | Value |
|-------|--------|
| **Severity** | **HIGH** (defense-in-depth; real isolation is Proot + FS layout) |
| **File:line** | `workspace/.../WorkspaceShellPolicy.kt:4-8,47-95`; `ProotShellRunner.kt:84-93`; `WorkspaceManager.kt:221-232` |
| **Description** | Documented as non-boundary. Blocks only a few patterns (`rm -rf /`, fork bombs, `mkfs`, sensitive absolute path substrings). Easy bypasses: `rm -rf /workspace/*`, encoded paths, `curl` to exfil, writing under bind mounts (`/skills`, `/upload`, `/tool_outputs`). Command is still `eval "$2"` of model-supplied text. Subagent `allowedPathPrefixes` only validates `path`/`cwd` keys, **not** paths inside the command string. |
| **Evidence** | |
```kotlin
/** Heuristic interception only — not a security boundary. */
fun evaluateShellCommand(command: String): ShellCommandVerdict { ... }
// Proot:
"cd -- \"\$1\" && eval \"\$2\"", ..., context.command
```
| **Suggested fix** | Keep Proot as primary isolation; tighten bind mounts (read-only skills where possible); optional command allowlist mode; extend path-prefix checks to shell by parsing common path args or forcing cwd-only execution; never mount host secrets into rootfs. |

---

### F4-5 — Tool output truncation only when `workspace_shell` is present

| Field | Value |
|-------|--------|
| **Severity** | **HIGH** |
| **File:line** | `GenerationHandler.kt:709-740` (`maybeTruncateToolOutput`); `SearchTools.kt:65-77,112-113` |
| **Description** | Truncation to 32KB (spill to `/tool_outputs`) runs **only if** any tool named `workspace_shell` is in the current tool list. Large `search_web` / `scrape_web` / MCP / memory_table results are returned in full → context overflow, rate limits, or provider 400s. Scraped pages especially unbounded. |
| **Evidence** | |
```kotlin
if (totalChars <= MAX_TOOL_OUTPUT_CHARS || !hasShellAccess) return output
```
| **Suggested fix** | Always truncate by char budget for all tools; per-tool caps (search items text, scrape content); keep shell spill path when shell available. |

---

### F4-6 — MCP newly discovered tools auto-enable

| Field | Value |
|-------|--------|
| **Severity** | **HIGH** |
| **File:line** | `app/.../data/ai/mcp/McpSessionRegistry.kt:499-511` |
| **Description** | `mergeTools` preserves enable/approval for known tools but **new** server tools are created with `enable = true` (and default `needsApproval = false`). A malicious or updated MCP server can introduce high-impact tools that become injectable on next Connected generation without explicit user opt-in. |
| **Evidence** | |
```kotlin
?: McpTool(
    name = serverTool.name,
    description = serverTool.description,
    enable = true,
    inputSchema = serverTool.inputSchema.toSchema(),
)
```
| **Suggested fix** | Default new tools to `enable = false` (or require user confirmation); surface “N new tools” UI; optionally default `needsApproval = true` for new tools. |

---

### F4-7 — Subagent `inheritTools = false` incomplete (TODO)

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `SubagentPermissionBuilder.kt:142-144` |
| **Description** | When profile does not inherit parent tools, builder returns **only workspace tools** (plus optional spawn / finish_work). Profile fields `localTools`, `enabledSkills`, `mcpServerIds` are ignored despite UI/config. Users configuring “isolated” subagents get weaker capability than documented; or conversely cannot intentionally grant MCP/skills without inherit. |
| **Evidence** | |
```kotlin
} else {
    // TODO: expand with profile.localTools, enabledSkills, mcpServerIds when inheritTools is false
    workspaceTools
}
```
| **Suggested fix** | Implement non-inherit assembly: local tools from profile, skills/MCP from profile ids, still apply parent DENY and path prefixes. |

---

### F4-8 — Bing local search ignores `resultSize`

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `search/.../BingSearchService.kt:64-80` |
| **Description** | Parses all `li.b_algo` results; never `.take(commonOptions.resultSize)`. User setting “result size” is ignored for default Bing provider → larger tool payloads than configured. |
| **Evidence** | |
```kotlin
val results = doc.select("li.b_algo").map { ... }
require(results.isNotEmpty()) { ... }
SearchResult(items = results)
```
| **Suggested fix** | `.take(commonOptions.resultSize.coerceAtLeast(1))` after map. |

---

### F4-9 — `searchCommonOptions.resultSize` unbounded in UI

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `SettingSearchPage.kt:399-404`; `SearchService.kt:95-97` |
| **Description** | Number input has no min/max clamp. Extreme values (0, negative if allowed by widget, or thousands) propagate to provider APIs (`max_results`, `count`, etc.) → cost, timeouts, or huge model inputs. Ollama alone coerces `5..10`. |
| **Suggested fix** | Clamp e.g. `1..20` (or provider-specific) in Settings update path and in `SearchCommonOptions` factory. |

---

### F4-10 — Sensitive local tools lack default approval

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `ClipboardTool.kt:17-69`; `JavascriptTool.kt:16-73`; `LogsTool.kt:56-98`; `ScreenTimeTool.kt:28-79` |
| **Description** | Only `ask_user`, `calendar_create`, skill management, and workspace_shell (default) set `needsApproval`. Clipboard **write**, JS eval, log dump (incl. redacted HTTP request metadata), and screen-time can run automatically if enabled on assistant. Prompt-injection in conversation can exfil clipboard or usage stats to the remote model. |
| **Suggested fix** | Default ASK for write/clipboard/logs/screen_time/eval_javascript; or force ASK under “strict” permission preset. |

---

### F4-11 — Workspace path checks reject `..` but shell can still operate broadly

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `WorkspaceTools.kt:362-370,496-516`; `SubagentPermissionBuilder.kt:48-78` |
| **Description** | File tools normalize absolute paths and reject `..` segments; writes outside `/workspace`/`/tmp` need approval. Shell tool only path-gates `cwd` for subagents. Model can `cat /skills/...` or rewrite bind-mounted host dirs without path-prefix violation. |
| **Suggested fix** | Document mount trust model; make skills mounts read-only in proot where possible; optional shell sandbox profile. |

---

### F4-12 — Search scrape has no client-side URL policy

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `SearchTools.kt:102-113`; e.g. `TavilySearchService.kt:129-146` |
| **Description** | `scrape_web` passes model-chosen URL straight to provider. Relies on third-party scrape APIs for SSRF safety. Local Custom JS scrape is worse (F4-1). No scheme/host filter before call. |
| **Suggested fix** | Require `http/https`; optional blocklist; size cap on returned content before encoding to tool result. |

---

### F4-13 — Provider error paths `println` raw bodies

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | e.g. `ExaSearchService.kt:103-105,121`; `ZhipuSearchService.kt:79-81,95`; `SearXNGService.kt:97,115` |
| **Description** | Failed decode / non-2xx paths print full response body to stdout. May include query text or provider diagnostics in logcat dumps shared for support. Not secret keys in body typically, but PII/query leakage. |
| **Suggested fix** | Use redacted `Log` with truncated body; never `println` production paths. |

---

### F4-14 — Parallel non-subagent tools unbounded concurrency

| Field | Value |
|-------|--------|
| **Severity** | **MEDIUM** |
| **File:line** | `GenerationHandler.kt:319-338` |
| **Description** | When `parallelToolExecution` is true, non-`spawn_subagent` tools run fully parallel (`async` without semaphore). Many concurrent MCP/search/shell calls can thrash network, Proot, or device memory. Subagents alone are limited by `subagentMaxConcurrent`. |
| **Suggested fix** | Global tool concurrency semaphore (e.g. 3–5) for all parallel tools. |

---

### F4-15 — MCP call timeout fixed 120s; cancel path partial

| Field | Value |
|-------|--------|
| **Severity** | **LOW** |
| **File:line** | `McpSessionRegistry.kt:152-158`; `McpManager.kt:190-196` |
| **Description** | `RequestOptions(timeout = 120.seconds)`. CancellationException rethrown (good). Non-cancel failures rethrow except unavailable client → text error. Long hangs still burn generation step budget. Image MCP content fully base64-decoded into FilesManager without size guard in `convertImageContentToFilePart`. |
| **Suggested fix** | Configurable timeout; max image bytes before save; map more failures to structured tool errors without stack. |

---

### F4-16 — Subagent path prefix matching is prefix-string only

| Field | Value |
|-------|--------|
| **Severity** | **LOW** |
| **File:line** | `SubagentPermissionBuilder.kt:34-45` |
| **Description** | Prefix match after light normalize; no symlink resolution on the candidate before compare (actual FS access still goes through rootfs/mounts). Misconfigured prefixes like `/work` would incorrectly allow `/workspace` only if `/work` is prefix—`/workspace` does not match `/work` (OK). Empty prefix list → all paths fail closed when candidate present; shell without cwd may skip validation (`candidate == null` → execute). |
| **Suggested fix** | For shell, require cwd always; treat empty allowed list as deny-all for path tools. |

---

### F4-17 — No hardcoded search API keys in source (positive)

| Field | Value |
|-------|--------|
| **Severity** | **n/a (positive finding)** |
| **Description** | Search options store keys in user settings (`apiKey` defaults `""`). Auth headers use runtime settings / KeyRoulette. MCP OAuth masks tokens in `toString`. Logs tool redacts Authorization. |

---

## 3. 亮点 / 可复用 (Strengths)

1. **Unified `Tool` model** with lazy schema, systemPrompt hooks, approval, and execute — clean provider-agnostic surface (`ai/.../Tool.kt`).
2. **Permission catalog** (`ToolCapabilityCatalog` + `finalizeGenerationTools`) with stable capability ids and DENY/ASK/ALLOW — reusable for UI and generation.
3. **MCP session registry**: per-server mutex, connect-before-publish client, backoff reconnect, settings as source of truth for stale jobs, OAuth refresh lock, probe without `callTool`.
4. **Connected-only MCP injection** + alphanumeric server name gate for runtime tool naming (`isValidMcpServerRuntimeName`).
5. **Workspace FS** `resolvePath` canonical escape check; write size limits; shell stdout/stderr hard cap 128KB; interrupt kills process.
6. **Production uses ProotShellRunner** (not HostShellRunner default) via `RepositoryModule`.
7. **Subagent**: depth limit, context reuse with permission fingerprint, tool budget + summary continuation, cancel registry, slim tool payload vs full transcript in metadata, shell JSON-safe transcript truncation.
8. **SearchService** pluggable interface + 18 providers; shared client/json/key roulette; scrape opt-in by schema nullability.
9. **HITL** for `ask_user` / calendar create / skill install / workspace shell default approval.
10. **Secret hygiene patterns** (MCP redact, Logs redaction, OAuth masked toString) are good templates for tool errors (currently not applied — F4-3).

---

## 4. 遗漏与风险 (Gaps & Residual Risk)

| Area | Gap |
|------|-----|
| **SSRF** | Custom JS fetch is the clearest host-network SSRF. Scrape_web depends on third parties. No shared URL safety util. |
| **Isolation trust** | Shell + bind mounts (`/skills`, `/upload`, `/tool_outputs`) expand blast radius; policy is explicit non-boundary. |
| **Context blow-up** | Truncation gated on shell presence; scrape/MCP/search can still dump huge text into chat. |
| **MCP supply chain** | Auto-enable new tools + remote server = silent capability expansion. |
| **Subagent config fidelity** | `inheritTools=false` incomplete; users may believe MCP/skills isolation works. |
| **Provider consistency** | Bing ignores resultSize; resultSize UI unbounded; error handling mix of `await()` vs `.execute()` (Exa/Zhipu) — cancelability uneven. |
| **Local privacy tools** | Clipboard/logs/screen time → remote LLM without default ASK. |
| **Native resources** | QuickJS leak on eval_javascript. |
| **Tests exist** | Workspace shell policy, path resolve, MCP probe, subagent permission tests — good; no SSRF tests for injectFetch. |
| **Secrets in repo** | No production hardcoded search keys found in this pass. |

---

## 5. Severity summary

| Sev | Count | IDs |
|-----|------|-----|
| CRITICAL | 1 | F4-1 |
| HIGH | 5 | F4-2, F4-3, F4-4, F4-5, F4-6 |
| MEDIUM | 8 | F4-7 … F4-14 |
| LOW | 2 | F4-15, F4-16 |
| Positive | 1 | F4-17 |

---

## 6. Recommended fix order

1. **F4-1** URL allow/deny for `injectFetch` (+ document Custom JS risk).  
2. **F4-2** destroy QuickJS in JavascriptTool.  
3. **F4-3** sanitize tool errors (no stack to model).  
4. **F4-5** always-on tool output budget.  
5. **F4-6** new MCP tools disabled by default.  
6. **F4-8 / F4-9** resultSize clamp + Bing take().  
7. **F4-7** complete non-inherit subagent tool assembly.  
8. **F4-10** default ASK on sensitive local tools.  
9. Defense-in-depth on shell mounts / concurrency (F4-4, F4-11, F4-14).

---

*End of D4 report.*
