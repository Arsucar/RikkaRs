# Changelog / 更新日志

All notable changes to the Rikka-Arsucar fork will be documented in this file.
本文件记录 Rikka-Arsucar 下游 Fork 的所有重要变更。

> **发版流程**：每次推送版本标签前，必须先在本文件中新增对应版本段落并提交。
> AI 助手在用户请求「发版 / 打标签 / 推版本」时，应：
> 1. 读取本文件，确认目标版本段落已存在且内容完整；
> 2. 若不存在，先补写并提交；
> 3. 从对应版本段落截取中英双语内容，作为 `git tag -a` 的消息体和 GitHub Release body；
> 4. 再执行打标签和推送。

---

## v2.3.5

### 新功能 / New Features

- **日志脱敏** — AI 通过 `get_logs` 工具读取日志时，敏感标头（Authorization、API Key、Cookie）和请求体中的密钥字段自动替换为 `***REDACTED***`；LogPage 中的原始日志不受影响。
  **Log Redaction** — Sensitive headers (Authorization, API keys, cookies) and body secrets are automatically redacted to `***REDACTED***` when the AI reads logs via the `get_logs` tool. Raw logs in LogPage remain unredacted for the user.

- **`get_logs` AI 工具** — 新增本地工具让 AI 读取应用运行日志（HTTP 请求 + 文本日志），支持 `type` 过滤（all/request/text）和 `limit` 参数（1–32）。每条 body/hea- der 截断至 2KB，总 JSON 控制在 16KB 以内，防止 GenerationHandler 全局截断。
  **`get_logs` AI Tool** — New local tool that lets the AI read app runtime logs (HTTP request logs + text logs). Supports `type` filter (all/request/text) and `limit` param (1–32). Per-entry body/header truncation (2KB) + total payload cap (16KB) prevent GenerationHandler global truncation.

- **日志导出** — LogPage 新增导出按钮，通过系统文件选择器保存日志为 JSON 文件，导出内容已脱敏。
  **Log Export** — New export button on the Logs page saves logs as JSON via the system file picker. Exported content is redacted (no API keys in file).

- **子智能体系统** — 完整子智能体 MVP：数据模型 + 设置 UI（Phase A）、权限层（Phase B）、运行时引擎（Phase C）、聊天工具卡片 UI（Phase D）。子智能体配置页的本地工具列表移除了 AskUser，新增日志工具。
  **Subagent System** — Full subagent MVP: data model + settings UI (Phase A), permission layer (Phase B), runtime engine (Phase C), and chat tool cards UI (Phase D). Subagent profile page now lists Logs instead of AskUser.

- **斜杠命令技能补全** — 在聊天输入框输入 `/` 即可发现和应用技能库中的技能提示词。
  **Slash Command Skill Completion** — Type `/` in chat input to discover and apply skill prompts from the skills library.

- **搜索结果图片** — 网页搜索结果现在包含图片，显示在 AI 消息和可展开的 Sheet 中。
  **Search Results with Images** — Web search results now include images, shown in AI messages and expandable sheet.

- **屏幕使用时间工具** — 新增本地工具，用户授予使用情况访问权限后，AI 可读取设备屏幕使用时间。
  **Screen Time Tool** — New local tool that lets the AI read the device's screen usage stats after the user grants Usage Access permission.

- **图片生成全屏预览** — 全屏预览现在显示模型名称，并提供「复制提示词」按钮。
  **Image Generation Preview** — Fullscreen preview now shows the model name and a "Copy prompt" button.

### 修复 / Fixes

- **请求日志持久化** — 「记录请求」开关状态现在会持久化到 DataStore，重启应用后保留。
  **Request Logging Persistence** — The "Record requests" toggle state is now persisted to DataStore across app restarts.

- **子智能体配置页** — 从本地工具列表中移除了误导性的 AskUser 选项。
  **SubagentProfilePage** — Removed misleading AskUser option from local tools list.

---

## v2.3.4

### 修复 / Fixes

- **收藏 CI 构建** — 将 `ImageFavoriteAdapter`、`FavoriteMeta` 及相关设置纳入 Release 构建，修复运行时类缺失错误。
  **Favorites CI Build** — Include `ImageFavoriteAdapter`, `FavoriteMeta`, and settings in the release build to fix missing-class runtime errors.

---

## v2.3.3

### 修复 / Fixes

- **图片生成** — 修复并发槽位管理、收藏分组及审查反馈问题。
  **Image Generation** — Fix concurrency slot management, favorites grouping, and review feedback issues.
