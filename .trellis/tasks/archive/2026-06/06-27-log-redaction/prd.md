# 日志脱敏 + AI Logs 本地工具（v2 对齐）

## Goal

对齐上游 rikkahub-sub 的 AI 查日志体验，修复现有实现的问题，并增加日志导出拓展。

## Background

- R1–R8 的基本框架已就位（LogRedaction、LogsTool、LocalToolOption.Logs、UI 开关等）
- 与上游逐行对比后发现以下差异需修复：
  - LogsTool 返回 JSON 可能超 32KB 被 GenerationHandler 截断为 4KB preview，AI 看不到完整日志
  - GetLogsToolUI 额外显示了 count summary，上游只有一个 title
  - SubagentProfilePage 列出了 AskUser 但运行时会被 removeAll，误导
  - 缺少日志导出功能
- Fetch / AskBtw 工具不影响日志功能，不纳入 scope

## Decisions

- **读时脱敏** — `Logging` 存原始，`logsTool` 执行时 `.redacted()`；LogPage 不脱敏
- **脱敏范围：header + body**，URL 不脱敏
- **LogsTool 自行截断** — 每条 RequestLog 的 requestBody / responseHeaders 在序列化前截断到合理长度（如 2KB），避免总量超 GenerationHandler 的 32KB 限导致被全局截断
- **GetLogsToolUI 对齐上游** — 去掉 hasSummary / Summary（只保留 icon + title）
- **SubagentProfilePage 去掉 AskUser** — 运行时已被 removeAll，不应作为可选项
- **LogPage 增加导出** — 支持将日志导出为 JSON 文件到 Downloads 目录

## Requirements

- R1: `LogRedaction.kt` ✅ 已完成
- R2: `LocalToolOption.Logs` ✅ 已完成
- R3: `LogsTool.kt` — 重构：每条 log body 自行截断（requestBody / 单个 header value 截断到 2KB），总 JSON 控制在 ~20KB 内
- R4: `LocalTools.kt` ✅ 已完成
- R5: `SubagentTools.kt` ✅ 已完成
- R6: `AssistantLocalToolPage.kt` ✅ 已完成
- R7: `AssistantSubagentProfilePage.kt` — 从 localToolOptions 列表移除 AskUser
- R8: `strings.xml` ✅ 已完成
- R9: `BuiltinToolUIs.kt` — GetLogsToolUI 去掉 hasSummary / Summary，对齐上游
- R10: `LogPage.kt` — 增加"导出日志"按钮，导出为 JSON 文件到 Downloads

## Acceptance Criteria

- [ ] `LogEntry.redacted()` 对 `RequestLog` 返回脱敏副本，对 `TextLog` 原样返回 ✅
- [ ] `LocalToolOption.Logs` 存在且序列化名为 `"logs"` ✅
- [ ] AI 调用 `get_logs` 返回的日志条目中敏感 header/body 已脱敏
- [ ] `get_logs` 支持 `type`（all/request/text）和 `limit`（1-100）参数
- [ ] LogsTool 返回 JSON 总量不会触发 GenerationHandler 全局截断（单条 body ≤ 2KB）
- [ ] GetLogsToolUI 只显示 title + icon，无 summary count
- [ ] SubagentProfilePage 的 localToolOptions 不包含 AskUser
- [ ] LogPage 有"导出"按钮，点击后日志 JSON 写入 Downloads
- [ ] LogPage UI 行为不变（不脱敏）

## Out of Scope

- Fetch / AskBtw 工具（独立功能，不影响日志）
- LogPage 增加"脱敏开关"
- URL query string 中的 token 脱敏
