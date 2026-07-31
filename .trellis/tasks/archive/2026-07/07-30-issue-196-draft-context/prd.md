# 草稿上下文组装优化

GitHub issue: #196

## Goal

将「写回复草稿」上下文组装从复用 `summaryAsText` 改为专用函数，并把注入参数提升到 `Preset.draftContext` 可配置。

## Requirements

### 数据模型

- `Preset.draftContext: DraftContextConfig?`（null = 代码默认，序列化向后兼容）
- `DraftContextConfig`：`messageCount`(8)、`maxCharsPerMessage`(0=不截断)、`includeMedia`、`includeTools`、`includeReasoning`、`keepLatestMessageIntact`

### 组装函数

- 新增 `UIMessage.toDraftContextText(config, isLatest)`，**不改**公共 `summaryAsText`
- 截断：`takeLast` 保尾；`keepLatestMessageIntact` 时最新消息不截断
- 非文本按开关占位：`[图片]` / `[文件: name]` / `[工具: …]` / `[推理: …]`

### 集成

- `ChatService.generateInputDraft` 读对应预设的 `draftContext`，null 用 `DraftContextConfig()` 默认（默认行为相对现状已升级）
- 预设详情页：仅当存在 `builtinKey == "reply_draft"` 时显示配置分区

## Non-Goals

- 不改标题生成/建议/压缩的 `summaryAsText` 路径
- 不做风格模仿或两段式生成

## Acceptance Criteria

- [ ] DraftContextConfig 挂 Preset，序列化向后兼容
- [ ] toDraftContextText：保尾、keepLatest、各开关占位
- [ ] generateInputDraft 读配置并 null 兜底
- [ ] 预设详情条件分区 + 数字/开关 UI 与校验
- [ ] 旧预设无字段导入不崩；导出含 draftContext
- [ ] 单测覆盖组装函数与 null 兜底

## Complexity

Complex：需 `design.md` + `implement.md`（数据模型 + UI + ChatService 链路）。
