# issue-217+216: 对话级变量系统（合并）

## Goal

新增对话级变量系统，兼容 SillyTavern 预设/世界书中的变量宏与模型输出 MVU（`<UpdateVariable>`）协议，使依赖变量的 ST 预设（如梦鲸思客V4）在 RikkaHub 恢复能力。

**合并说明**：GitHub #217 与 #216 为同一功能的两次提交，宏/MVU/助手开关/右抽屉 UI 重叠。本任务以两者并集为权威；实现与关闭 issue 时双 issue 互链。

对应：#217、#216。依赖 #215（实验页）未落地时用 Assistant 过渡开关字段。

## Requirements

### R1 宏解析（发送前）
- 新 `VariableMacroTransformer`（InputMessageTransformer），注册在 **PromptInjectionTransformer 之后**（注入拼装完成再统一解析）。
- 覆盖：ModeInjection 预设、Lorebook 世界书、自定义系统提示等全部注入文本。
- `{{getvar::name}}`：只读替换；未定义→空串。
- `{{setvar::name::value}}` / `{{addvar::name::value}}`：写 variables 后**自我移除**，不进模型上下文。
- 嵌套宏：先内后外递归（含 setvar 值内 `{{getglobalvar::}}` 等；全局变量本期可读最小实现或空串回退，写全局列为后续）。
- 世界书以变量为触发条件（ST `{{var::x}}==y`）**本期不做**。

### R2 存储
- `Conversation.variables: Map<String, String>`，Room JSON 列（仿 modeInjectionIds 等集合字段）。
- 写入**原子 transform**（#202），禁读快照全量覆盖。
- 删会话清理；备份/恢复随 ConversationEntity。
- addvar 上限：单变量最大长度 + 变量总数上限，超限截断/拒绝并提示。

### R3 MVU 输出解析
- 新 OutputMessageTransformer，`onGenerationFinish`：解析 `<UpdateVariable>…</UpdateVariable>`。
- 严格解析（仿 HookOutputParser）；JSON Patch 路径 `/变量名` + add/replace/remove（先字符串值）。
- 成功剥离（不显示、不写历史）；失败保留原文。
- 流式：缓冲至闭合再解析，防半块。

### R4 分支语义
- 重新生成新备选：快照当前 variables 到该分支。
- `selectIndex` 切换：恢复对应分支变量快照。
- `forkConversationAtMessage`：复制当前 variables。
- **设计关键**：仅 Conversation 顶层 map 不足以表达多分支——需 per-branch 快照（见 design）。

### R5 开关（助手级）
- `featureId: variable_system`，默认 false。
- 消费点三处全读同一开关：① 宏解析挂载 ② MVU 解析 ③ 右抽屉入口。
- 关闭：魔法字符原样透传、不解析输出块、无抽屉入口，零副作用。
- **#215 未落地**：Assistant 过渡字段（推荐直接 `experimentalFeatureOverrides: Map<String,Boolean>` 或最小 `enableVariableSystem: Boolean`）；#215 落地后迁移，消费点 API 稳定。

### R6 UI
- 右抽屉「变量」节：列表/编辑/新增/删除确认；Empty 态。
- 编辑 Dialog：`heightIn` + `verticalScroll`。
- 仅开关开时渲染。

### R7 工具通道（可选，默认关）
- 仿 MemoryTableTools；可二期。

## Constraints

- 无新权限；无 Hilt 新依赖（Koin 现有）。
- 旧配置无字段→默认关；新字段旧版本忽略。
- 并发发送不丢变量更新。

## Acceptance Criteria

- [ ] AC1 未开助手：宏原样透传，无解析/剥离/告警。
- [ ] AC2 开启：setvar→getvar 正确；addvar 追加；嵌套先内后外；未定义 getvar→空串。
- [ ] AC3 世界书条目宏与预设同规则解析。
- [ ] AC4 MVU 更新变量并剥离；失败保留原文。
- [ ] AC5 分支切换/新备选/fork 变量语义正确。
- [ ] AC6 助手 A 开 B 关互不影响；开关即时。
- [ ] AC7 关闭零副作用。
- [ ] AC8 并发写不丢。
- [ ] AC9 配置互读兼容；重启/备份后开关与变量保持。
- [ ] AC10 右抽屉 UI 完整；中英字符串。
- [ ] AC11 installDebug + 导入 ST 预设抽样验收。

## Out of Scope

- #215 完整实验页（仅契约/过渡字段）。
- 变量触发世界书条件。
- 完整全局变量读写 UI（getglobalvar 最小兼容即可）。
- 工具通道可二期。

## Notes

- #216 vs #217：无功能级冲突；#217 显式世界书；#216 列全局后续更细。并集实现。
- 现状：PlaceholderTransformer 仅只读 11 宏；Conversation 无 variables；Output onGenerationFinish 管线存在。
