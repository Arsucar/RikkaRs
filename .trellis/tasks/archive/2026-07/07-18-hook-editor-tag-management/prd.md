# feat: 重构 Hook 编辑器为单一标签管理（allowlist + 提示词策略）

## Goal

收敛 Assistant Hook 编辑页在 #147 / #148 落地后的碎片化：标签相关能力统一为「标签管理」（一份 allowlist + 评估提示词策略），顶级动作仅保留「标签管理」与「同步记忆表」；运行时合并为单一 multi-op handler；旧 Add / Transition / Sync 可迁移编辑与执行。

## Source

- GitHub issue: https://github.com/Arsucar/RikkaRs/issues/150
- 关联：#147（同步记忆表，已关闭）、#148（Issue 完成/进行中标签联动）
- Research: `.trellis/tasks/07-18-hook-editor-tag-management/research/hook-editor-tag-action-model.md`
- Spec: `.trellis/spec/app/conversation-tags-and-hooks.md`

## Background

### 现状（代码）

- 编辑器三枚并列 chip：`ADD_CONVERSATION_TAG` / `TRANSITION_CONVERSATION_TAGS` / `SYNC_MEMORY_TABLE`。
- Add：真多选 allowlist + 单 tag apply；Transition：固定 add/remove + Issue 证据硬门控；Sync：记忆表。
- 评估提示词常驻展开；Transition 完成/进行中为伪多选单选 chip 墙。
- Hooks 存于 `Assistant.hooks` 多态 JSON；历史执行行存 `HookActionType` 枚举名（`valueOf` 严格）。
- Preview/Run/Retry 仅 Sync。

### 产品定稿

- 顶级动作仅：标签管理、同步记忆表（Select，禁止三 chip）。
- 标签管理：一份 allowlist + 策略写在评估提示词；无模式/双权限/条件下拉。
- 评估提示词默认折叠或压缩；首屏密度提高。
- UI 纯展示，逻辑下沉 service/domain。

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | 运行时统一深度 | **B**：本轮合并单一 multi-op 标签管理 handler + 统一编辑器 |
| D2 | Issue 证据硬门控 | **B1**：去掉硬门控；策略只在提示词 + 模型 ops |
| D3 | 非法/越权 ops | **C1**：整单 fail-closed（任一条非法则不写任何标签） |
| D4 | 配置持久化 | 新 `manage_conversation_tags` + `MANAGE_CONVERSATION_TAGS`；加载时把旧 Add/Transition **规范化**为新类型；历史 DB 旧枚举名仍可展示 |
| D5 | 模型输出协议 | 严格 JSON：`decision` + `operations` + `reason`（镜像 Sync 风格；skip 时 operations 必须空） |
| D6 | ops 语义 | `operations[]` 每项 `{ "op": "add"\|"remove", "tagId": "<uuid>" }`；仅 allowlist 内；上限见 design |
| D7 | 旧 Transition 提示词 | 迁移时若 prompt 为空或仍是默认模板，可注入策略提示；用户自定义 prompt **不覆盖** |

## Requirements

### R1 — 编辑器信息架构

- 入口：助手详情 → Hooks → 编辑 Hook。
- 顶栏：返回 / 标题 / 保存。
- 基础：名称 + 启用；触发与评估模型一行摘要。
- 动作类型：Select，仅「标签管理」「同步记忆表」。
- 评估提示词：默认折叠/压缩占位，可展开；supporting 按动作类型区分。
- 列表：区分标签管理 vs 记忆表；标签管理显示 allowlist 摘要。
- 关键配置尽量首屏可见。

### R2 — 标签管理配置面

- 说明：本 Hook 可操作的标签范围。
- allowlist：FilterChip 真多选；不可用标签可清理。
- 禁止：模式 segmented、完成/进行中 chip 墙、条件下拉、双权限栏、Issue 表单门控。
- 校验：名称/模型/触发/提示词非空；allowlist 非空且全部 ∈ 目录。

### R3 — 同步记忆表

- #147 字段与 Preview/Run/Retry 不丢失、不 silent break。
- 布局紧凑，不与标签配置重复套壳。

### R4 — 迁移与兼容

| 旧配置 | 规范化结果 |
|--------|------------|
| `add_conversation_tag` | `manage_conversation_tags`，allowlist=`allowedTagIds` |
| `transition_conversation_tags` | `manage_conversation_tags`，allowlist=`{add,remove}` 并集；丢弃 filter 字段（不再驱动门控） |
| `sync_memory_table` | 不变 |

- 旧 JSON 可解码；保存后写新 SerialName。
- 历史 `ADD_CONVERSATION_TAG` / `TRANSITION_CONVERSATION_TAGS` 行可展示（别名映射），不得 `valueOf` 崩溃。
- Sync `configurationHash` / retry 匹配语义不因本改动破坏。

### R5 — 运行时

- 单一 `ManageConversationTags` handler（或等价命名）。
- prepare：解析 allowlist、冻结 id→name 注入提示；**不**跑 GitHub 证据硬门控。
- parse：严格 JSON（D5/D6）；schema 错误 → `SCHEMA_MISMATCH` / `INVALID_JSON`。
- execute：SKIP → Skipped；APPLY 时校验每条 op（allowlist、tag 存在、op 合法、数量上限）；任一条失败 → 整单不写（C1）。
- commit：单事务内按序 apply add/remove；0 变更 → SKIPPED；有变更 → SUCCESS。
- 标签词表：不得按名创建。
- 删除/停用旧 Add/Transition handler 注册路径（兼容仅配置解码层）。

### R6 — 文案

- 用户可见字符串：resource + 简体中文。
- 列表/编辑器/历史统一「标签管理」「同步记忆表」。

### R7 — 测试与验收

- 序列化 round-trip、旧 JSON 迁移、parser、allowlist fail-closed、整单拒绝、编辑器校验、列表摘要、history 旧枚举展示。
- 编译通过；有设备则 installDebug 验收编辑页。

## Acceptance Criteria

1. 编辑器无并列「添加会话标签 / 转换标签」；标签统一「标签管理」。
2. 标签管理仅 allowlist + 评估提示词；无模式/双权限/条件下拉/Issue 表单。
3. 无完成/进行中伪多选单选 chip 墙。
4. 评估提示词默认不撑满半屏；首屏密度提高。
5. 旧 Add / Transition / Sync 可迁移编辑与执行；列表与 history 不回归。
6. 同步记忆表能力与 Preview/Run/Retry 不丢失。
7. 越权/非法 ops 整单 fail-closed；不按名建标签；无 Issue 硬门控。
8. 相关单测/编译通过；有设备时装包验收。

## Out of Scope

- 扩展 #147 记忆表业务规则。
- 为标签新增 Preview/Run/Retry。
- 标签词表/关系写入契约重构。
- 完整规则引擎/条件 UI。
- 上游 PR。

## Known behavioral deltas (accepted)

- 旧 Transition：不再因缺 Issue 证据而 pre-provider SKIP；策略改由提示词 + 模型决定。
- 旧 Add：由单 tag apply 变为 multi-op；模型可一次 add/remove 多条（受上限与 allowlist 约束）。
- 历史执行仍显示旧 action 类型标签映射；新执行写 `MANAGE_CONVERSATION_TAGS`。
