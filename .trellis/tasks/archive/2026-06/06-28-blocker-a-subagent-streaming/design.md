# Design: Blocker A — Subagent Streaming JSON/Metadata Consistency

## 现状定位

| 位置 | 行为 |
|------|------|
| `ChatService.updateSubagentProgress` (~1157-1161) | 写 `partialOutputText` = `{"profile_name":..,"succeeded":false,"streaming":true}` |
| `ChatService.cleanStaleStreamingMetadata` (1202-1234) | 仅改 `metadata.subagent_streaming` = false，**不**碰 `text` JSON |
| `ChatService` onCompletion 兜底 (~664-671) | 调 `cleanStaleStreamingMetadata` |
| `SubagentToolUIs` | 读 `metadata.subagent_streaming`（不读 `text` JSON），当前 UI 不受影响；但其他消费者可能读 `text` |

## 方案

在 `cleanStaleStreamingMetadata` 清理 `UIMessagePart.Text` 时，**同时**：

1. 若 `metadata` 含 `subagent_streaming=true` → 置 `false`（现有逻辑）。
2. 若 `text` 可解析为 JSON 且含 `"streaming":true`（或非 false）→ 改写为 `"streaming":false`，**保持其他字段不变**；解析失败则原样返回（不破坏非 JSON 文本）。

实现要点：
- 用 `runCatching { Json.decodeFromString<JsonObject>(textPart.text) }` 容错解析。
- 仅当解析出 JsonObject 且含 `streaming` 键时改写；其余情况不动 `text`。
- 用 `JsonInstant` / 已有 `json` 实例保持编码风格一致。

## 数据流

```
updateSubagentProgress
  └─ 写 text JSON {streaming:true} + metadata {subagent_streaming:true}
       │
       ▼
（流式结束 / 失败 / 取消 / hydrateFromDb）
  └─ cleanStaleStreamingMetadata
       ├─ metadata.subagent_streaming → false   ✅ 已有
       └─ text JSON streaming → false           ✅ 新增（本任务）
```

## 边界与兼容

- `text` 不是 JSON（例如最终结果摘要文本）→ 解析失败 → 原样返回，零影响。
- `text` 是 JSON 但无 `streaming` 键 → 不改。
- `text` 是 JSON 且 `streaming` 已为 false → 幂等，不改。
- DB 已落库的 stale 记录：`hydrateConversationFromDb` 已会调 `cleanStaleStreamingMetadata` 并 `saveConversation`；本任务的 text 同步会随之写回，无需额外路径。

## 风险

- **低**：改写 JSON 时键顺序 / 空格变化 → 序列化幂等性取决于 `Json` 配置；用现有 `json` 实例可保持一致。
- **低**：`SubagentToolUIs.subagentToolContentKey` 含 `textLen`，改 `streaming:true→false` 不改变长度（true/false 长度不同：4 vs 5）→ 长度会变 → `remember` 键变化 → 重组一次，符合预期（状态确实变了）。

## 不做

- 不把 `streaming` 字段从 `text` JSON 移除（会破坏 transcript 解析）。
- 不改 `updateSubagentProgress` 写入逻辑（流式进行中仍写 true）。
- 不新增第二套清理函数（复用 `cleanStaleStreamingMetadata`）。
