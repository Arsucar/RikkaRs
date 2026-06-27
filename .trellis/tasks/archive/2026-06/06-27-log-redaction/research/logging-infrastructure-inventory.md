# Research: logging infrastructure (log redaction task)

- **Query**: Find logging-related files, LogRedaction, get_logs, LocalToolOption, UI pages, SubagentTools; LogEntry / RequestLog / TextLog definitions
- **Scope**: internal (local codebase)
- **Date**: 2026-06-27

## Findings

### Files Found

| File Path | Description |
|---|---|
| `common/src/main/java/me/rerere/common/android/Logging.kt` | In-memory ring buffer (max 100), `LogEntry` sealed hierarchy, request logging toggle |
| `common/src/main/java/me/rerere/common/android/LogRedaction.kt` | `redactHeaders`, `redactSecrets`, `LogEntry.redacted()` extension |
| `common/src/test/java/me/rerere/common/android/LogRedactionTest.kt` | Unit tests for redaction |
| `app/src/main/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptor.kt` | OkHttp interceptor → `Logging.logRequest(RequestLog)` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/log/LogPage.kt` | Settings + list UI; shows **raw** logs (no `.redacted()`) |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LogsTool.kt` | `buildLogsTool()` → tool name `get_logs`; applies `.redacted()` on export |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalTools.kt` | Registers `logsTool` when `LocalToolOption.Logs` enabled |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalToolOption.kt` | Sealed class; `@SerialName("logs") data object Logs` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantLocalToolPage.kt` | Assistant toggle for Logs local tool |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | Subagent profile chips include `LocalToolOption.Logs` |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` | `manage_subagent_profile` parses `"logs"` → `LocalToolOption.Logs` |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/BuiltinToolUIs.kt` | `GetLogsToolUI` for `get_logs` tool bubble |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | Adds `RequestLoggingInterceptor` to OkHttp |
| `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt` | Syncs `Logging.setRequestLoggingEnabled` from settings on startup |

### Code Patterns

**LogEntry / RequestLog / TextLog** — all defined in `Logging.kt` (lines 8–35): kotlinx-serializable sealed class with `TextLog(message)` and `RequestLog(url, method, headers, body, response, duration, error)`.

**Storage vs redaction** — `Logging` stores entries as captured (raw headers/bodies). Redaction is **read-time** only in `LogsTool` via `selected = allLogs.take(limit).map { it.redacted() }` (`LogsTool.kt:60`). `LogPage` binds `Logging.getRecentLogs()` directly without redaction.

**Request capture** — `RequestLoggingInterceptor` no-ops when `!Logging.isRequestLoggingEnabled()`; otherwise logs full header maps and request body string before/after `chain.proceed`.

**Tool wiring** — `LocalTools.getTools()` adds `logsTool` if assistant/subagent options contain `LocalToolOption.Logs`. Subagent string patch uses serial name `"logs"` (`SubagentTools.kt:292`).

### Related Specs / Task

- `.trellis/tasks/06-27-log-redaction/prd.md` — task PRD (may lag behind implemented code)

## Caveats / Not Found

- No additional `Logging.kt` outside `common/.../Logging.kt`.
- `grep LogRedaction` in `*.kt` hits only `LogRedactionTest.kt` class name; implementation is in `LogRedaction.kt` (functions are top-level, not class `LogRedaction`).
- Full verbatim source: `logging-infrastructure-full-sources.md`, `logging-ui-and-tools-full-sources.md`, plus byte-identical snapshots under `research/*.kt.snapshot` (`LogPage`, `AssistantLocalToolPage`, `AssistantSubagentProfilePage`, `SubagentTools`).