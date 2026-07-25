# 预设增强：内置提示词可编辑/开关，System 注入可拖拽排序

对应 GitHub issue #182。

## Goal

将现有「预设 = ModeInjection ID 容器」升级为「可编辑、可开关、可排序的提示词预设」，对标 SillyTavern 预设体验：预设内条目可见可编辑可开关可排序；系统内置提示词（回复草稿、建议回复、记忆表向导、工作区向导等）纳入预设可引用/覆盖/开关/调位；`BEFORE_SYSTEM_PROMPT` / `AFTER_SYSTEM_PROMPT` 类注入支持拖拽排序控制最终拼接顺序。

## Scope

- 注入位置枚举沿用现有 5 值，不新增：`BEFORE_SYSTEM_PROMPT` / `AFTER_SYSTEM_PROMPT` / `TOP_OF_CHAT` / `BOTTOM_OF_CHAT` / `AT_DEPTH`。
- 助手扩展绑定仍绑定 `presetIds`，绑定行为不变。
- 只读、不新增后台轮询；不把项目路径/diff 注入模型上下文或明文日志。

## Requirements

### 数据模型
- 新增 `PresetEntry` sealed 类型，三个变体：
  - `Custom{ id, enabled, order, position, injectDepth, name, content, role }`
  - `Builtin{ id, enabled, order, position, injectDepth, builtinKey, overrideContent?, overridePosition, role }`
  - `Reference{ id, enabled, order, position, injectDepth, modeInjectionId, role }`
- `Preset` 新增 `entries: List<PresetEntry>`；保留 `modeInjectionIds` / `disabledEntryIds` 用于迁移，迁移后可弃用。

### BuiltinPromptRegistry
- 集中注册内置模板 key、默认内容、默认 role、STATIC/DYNAMIC 标记、是否可覆盖、supportedVariables。
- DYNAMIC 模板（workspace/memory_tables）可编辑，运行时替换 `{{workspace_name}}` / `{{cwd}}` / `{{memory_tables}}` 等宏。

### 注入流程
1. 解析助手 `presetIds` → 展开各 preset 的 `entries`（仅 `enabled==true`）。
2. 按类型与 position 分组；组内按 `order` 排序。
3. Builtin 解析内容（含 override 与动态宏）；Reference 解析全局 ModeInjection。
4. `PromptInjectionTransformer.applyInjections`：同 position 组按 order 拼接进 system；非 system 位置仍按 TOP/BOTTOM/AT_DEPTH + depth。

### 迁移
- 旧 `modeInjectionIds` → 复制为 `Custom`（内容快照，`enabled = id !in disabledEntryIds`）。
- 默认预设追加系统 Builtin 条目（默认开启或与现有功能开关对齐）。
- 迁移为「快照」语义：Custom 不随全局 ModeInjection 后续修改同步；需链接全局时用 Reference。

### UI（PresetDetailPage，新建）
- 入口：扩展管理 → 提示词页 → 预设 Tab → 点击预设进入 PresetDetailPage。
- 三区块独立不混排：系统条目（Builtin）/ 自定义条目（Custom）/ 全局引用（Reference）。
- 每条：Switch 开关、名称、position chip、预览、拖拽手柄（适用时）、滑动删除。
- 编辑 BottomSheet：内容、位置、depth、role、enabled；底部可用魔法变量 chips（点击插入）。
- Material3、复用现有 PromptPage 组件风格、Dark Mode、中英 string、开关可达。

## Acceptance Criteria

- [ ] AC1：预设详情可见三类条目，可增删改、一键开关（禁用后不注入、配置保留）。
- [ ] AC2：`BEFORE`/`AFTER_SYSTEM` 的系统与自定义条目可拖拽改顺序，导出上下文中 system 拼接顺序与之一致。
- [ ] AC3：自定义条目按 position 分组，不与系统条目混排；`AT_DEPTH` 按 depth + order 生效。
- [ ] AC4：内置提示词（至少：回复草稿、建议回复、记忆表向导、工作区向导）可在预设中引用/开关/覆盖模板，魔法变量保留且 UI 展示可插入列表。
- [ ] AC5：旧预设迁移后条目为 Custom 快照，行为不回归；新字段向后兼容。
- [ ] AC6：未启用对应功能时（如无 workspace / 关 memory table），对应 Builtin 不注入或空内容，不报错。

## Verification

- 注入顺序单测；迁移单测；开关/空内容边缘案例单测。
- `:app:compileDebugKotlin` 与 `:app:testDebugUnitTest`（`--no-daemon`）通过。
- 本地化 string en/zh（若要求全量则 6 语言）。

## Notes

- 相关历史：#65（ModeInjection 预设聚合，已关闭）；本 issue 在其之上做可编辑条目、内置模板与 system 内排序。
- 规模极大，属完整 PRD 级特性；本任务当前阶段仅产出 spec（prd/design/implement），不写实现代码。
