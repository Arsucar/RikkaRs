# PRD: feat(#259+#242) 删除独立注入模块与 Reference

## Goal

完全删除「独立注入 / 模式注入」双路径，注入能力只保留 **预设条目（PresetEntry）** 单一路径；删除过程中将存量 `PresetEntry.Reference` **迁移为内容等效的 Custom 快照**（非 tombstone、非静默丢弃）。

## Background

两套注入并存：`Settings.modeInjections` + 助手/对话直连绑定，与 #182 预设条目。双路径导致 #201/#205 类问题。#182 已覆盖能力。

**权威定调（父任务 + 用户）**：

- **#259 覆盖 #242**
- Reference 处理：**Reference → Custom 快照迁移**，**不采用** #242 的 tombstone 空壳方案
- #242 随 #259 一并关闭，不再单独实现 tombstone

权威 AC：issue #259 正文；#242 仅作历史对照（其 tombstone AC 作废）。

## Requirements

### 删除范围（产品可见）

1. 聊天扩展面板 tab「独立注入」删除，pager 5→4
2. 助手扩展页「独立注入」小节删除
3. PromptPage 全局注入绑定/新建/编辑/Reference 创建路径删除
4. AssistantPromptPage `allowConversationPromptInjection` 开关删除
5. FilesPicker 注入计数删除
6. Web 相关 injections 端点与 DTO 字段删除
7. 字符串 6 语言清理；空态文案去「模式注入」措辞

### 数据与迁移

1. 删除 `Settings.modeInjections`（key `mode_injections`）及读写/默认值/sanitize 路径
2. 删除 `Assistant.modeInjectionIds`、`allowConversationPromptInjection`
3. 删除 `Conversation.modeInjectionIds` + Room 列（见 design 版本号）
4. **升级时**：每个 `PresetEntry.Reference` 将目标 content/position/role 等复制为 `PresetEntry.Custom`，再删除 Reference 类型与 `mode_injections`
5. `PromptInjection` 密封类本体保留（Regex 等仍用）；`ModeInjection` 子类删除，解析输出改内部类型
6. 执行链路仅保留 entries 展开（`PromptInjectionTransformer`）

### 约束

- 旧 JSON `ignoreUnknownKeys` 不崩；全量写盘前须完成迁移/清洗策略并有测试锁定
- 不采用「直接丢弃 Reference」
- 不采用 #242 tombstone 只读过滤作为最终态
- DEFAULT_MODE_INJECTIONS 消失为可接受行为变更（须发版说明）
- Web 外部客户端需同步（破坏性 API）

## Non-goals

- 保留独立注入仅藏 UI
- 直连绑定并入预设页仍双路径
- #242 tombstone 最终方案

## Acceptance Criteria（汇总 #259）

- [ ] AC1: 代码库无 `modeInjection` / `ModeInjection` / `mode_injections` / `conversationModeInjectionIds` / `allowConversationPromptInjection` 残留（迁移期临时代码除外，合并后应清）
- [ ] AC2: 预设条目注入与删除前等效；已有 Reference **全部**迁移为内容等效 Custom
- [ ] AC3: 聊天扩展面板 5→4 tab，无「独立注入」
- [ ] AC4: 助手扩展 / PromptPage / AssistantPromptPage 无独立注入 UI
- [ ] AC5: 升级：老配置加载不崩溃；独立注入按方案迁移/清理；备份恢复正常
- [ ] AC6: Room 迁移通过（版本见 design：在 #258 之后则为 51→52）
- [ ] AC7: Web 端点与 DTO 无 modeInjectionIds 残留
- [ ] AC8: 全量相关单测通过；CI/本地 compile 通过
- [ ] AC9: 导出→重导入不产生 Reference；旧备份恢复不因 Reference discriminator 崩溃
- [ ] AC10: #242 关闭理由写明「由 #259 Reference→Custom 取代 tombstone」

## 行为变更（须发版说明）

- 默认 Learning Mode 等 DEFAULT_MODE_INJECTIONS 不再注入
- 全局注入不再可挂助手/对话直连；仅预设 Custom/Builtin
- Web API 删除 injections 相关字段/端点
