# Design: API 上游错误监控 (#191)

## Goal

按 provider/model 持久化每次 LLM API 调用的成功/失败与延迟，并在 StatsPage 展示健康度聚合与详情。

## Architecture

```
GenerationHandler.generateInternal / ChatService 后台 generateText
  └── ApiCallRecorder.begin() → insert PENDING
  └── success / failure → update SUCCESS|ERROR|CANCELLED + latency + errorType

ApiCallRecordDAO
  └── aggregate by providerId+modelId+timeRange
  └── recent calls for detail sheet

ApiCallHealthCompute (pure)
  └── classifyError(Throwable) → ApiCallErrorType
  └── aggregate rows → ApiModelHealth
  └── isAlmostUnavailable

StatsVM (SWR)
  └── load message_stats (existing) + api health in parallel
  └── timeRange 7d/30d/all

StatsPage
  └── ApiHealthCard + SegmentedButton + list rows + ModalBottomSheet detail
```

## Data model

Table `api_call_records` (Room v47, Migration_46_47):

| Column | Type | Notes |
|--------|------|--------|
| id | TEXT PK | Uuid string |
| provider_id | TEXT | ProviderSetting.id |
| provider_name | TEXT | Display snapshot |
| model_id | TEXT | Model.modelId (API id) |
| model_display_name | TEXT | Display snapshot |
| request_at | INTEGER | epoch ms |
| response_at | INTEGER? | epoch ms |
| status | TEXT | PENDING/SUCCESS/ERROR/TIMEOUT/CANCELLED |
| latency_ms | INTEGER? | |
| token_input | INTEGER? | |
| token_output | INTEGER? | |
| error_type | TEXT? | AUTH/RATE_LIMIT/TIMEOUT/CONTENT_FILTER/SERVER/NETWORK/UNKNOWN |
| error_code | TEXT? | e.g. HTTP 429 |
| error_message | TEXT? | short sanitized summary (max ~200 chars) |

Indices: `(request_at)`, `(provider_id, model_id, request_at)`.

No FK to conversations/messages (independent analytics; survive chat deletes).

## Write path

1. **Primary**: `GenerationHandler.generateInternal` — every real provider stream/generate step (incl. multi-tool steps).
2. **Secondary (MVP optional same helper)**: title/suggestion/background `generateText` in ChatService via shared `ApiCallRecorder.record(...)`.
3. Cancellation → status CANCELLED (not counted as failure for availability).
4. PENDING left after process kill: ignored in aggregates (status IN SUCCESS/ERROR/TIMEOUT only for rates); no startup cleanup in MVP.

## Error classification

`ApiCallErrorClassifier.classify(throwable)`:

- TIMEOUT: SocketTimeoutException, TimeoutException, message contains timeout
- NETWORK: UnknownHost, ConnectException, SSL, generic IOException
- AUTH: HTTP 401/403 or message auth/unauthorized/invalid api key
- RATE_LIMIT: HTTP 429 or rate limit
- CONTENT_FILTER: content_filter / content filter / safety / moderation
- SERVER: HTTP 5xx
- UNKNOWN: else

Extract error_code from leading `HTTP NNN` in HttpException message when present.

## Aggregation / availability

Per (providerId, modelId) in time range:

- totalCount, successCount, errorCount (ERROR+TIMEOUT)
- successRate = success / (success+error) excluding CANCELLED/PENDING
- avgLatencyMs from non-null latency on SUCCESS
- lastErrorAt, lastErrorType, lastSuccessAt
- almostUnavailable if: last 5 completed calls all failed, OR total completed ≥ 5 and errorRate ≥ 0.8

## UI

- StatsPage card「API 健康度」below existing grid
- SegmentedButton: 7 days / 30 days / All
- Overview chips: total calls, overall success %, failures, avg latency
- Rows sorted by call count desc; red hint when almostUnavailable
- Tap row → ModalBottomSheet recent ≤ 30 calls
- Empty: no records yet copy
- SWR: keep prior apiHealth while refreshing (mirror AppStats isRefreshing)

## Migration

- AppDatabase version 46 → 47
- Manual `Migration_46_47` CREATE TABLE + indices
- Register in DataSourceModule
- Do not touch message_stats tables

## DI

- `apiCallRecordDao()` from AppDatabase
- `ApiCallRecorder` single
- Inject into GenerationHandler + ChatService
- StatsVM gains ApiCallRecordDAO

## Non-goals (MVP)

- Charts / pie / trend
- Provider filter chips (time range only)
- PENDING cleanup on boot
- Full request/response body storage

## UI verification matrix (applicable)

| Case | Expectation |
|------|-------------|
| Empty | Empty copy, no crash |
| Loading first | Full page spinner only when no cache |
| Refresh with cache | Prior data + toolbar spinner |
| Many models | LazyColumn scroll; sheet scroll |
| Almost unavailable | Visible warning text + color |
| Dark/light | Material3 cards |
| Strings | en + zh resources |
| A11y | progress contentDescription for success rate |
