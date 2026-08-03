# issue-215: 可热插拔的实验性功能配置页 + 迁入已有实验开关

## Goal

实现 GitHub #215：统一「实验性功能」注册表 + 全局/助手两级配置页。并将本轮已落地但散落的实验开关迁入该体系：

| featureId | Scope | 现有字段 | 现入口 |
|-----------|-------|----------|--------|
| `chat_keepalive` | Global | `Settings.enableKeepAliveNotification` | 通知设置页 |
| `checkpoint_cache` | Global | `Settings.enableCheckpointCache` (+ interval 仍可在实验页或附属 UI) | 通知设置页 |
| `variable_system` | Assistant | `Assistant.enableVariableSystem` | 助手记忆页 |

对应 issue: #215。迁入后删除散落 UI；消费点经统一 `resolve()`。

## Requirements

### R1 注册表
- `ExperimentalFeatureRegistry` + `FeatureSpec(id, nameRes/descRes or string keys, scope Global|Assistant, defaultEnabled=false, status experimental|beta|stable)`.
- 新增实验项 = 注册一条 + 消费点 `resolve`；页面骨架不改。

### R2 存储
- `Settings.experimentalFeatures: Map<String, Boolean> = emptyMap()`（仅 Global）。
- `Assistant.experimentalFeatureOverrides: Map<String, Boolean> = emptyMap()`（仅 Assistant）。
- DataStore / Assistants JSON；#202 partial/transform 原子写。
- JSON 损坏 → emptyMap + defaultEnabled。

### R3 resolve
```
Global: settings.experimentalFeatures[id] ?: spec.defaultEnabled
Assistant: assistant.experimentalFeatureOverrides[id] ?: spec.defaultEnabled
```
未知 id → false（或 defaultEnabled）。Scope 串写忽略。

### R4 UI
- 设置主页「模型服务」入口 → `Screen.Experiments`（Global 列表）。
- 助手详情入口 → `Screen.AssistantExperiments(id)`（Assistant 列表）。
- 顶部红字警告；开启须二次确认。
- Empty：暂无实验性功能。
- 可选：助手页「应用到所有助手」。

### R5 迁入既有实验项
- 注册上述三个 featureId。
- 消费点：
  - keepalive / checkpoint：读 Global resolve，不再直接读旧布尔（或 resolve 内部兼容旧键一次迁移）。
  - variable_system：读 Assistant resolve；`isVariableSystemEnabled()` 改为查 overrides / registry。
- **迁移**：首次读取时若旧布尔 true，写入新 Map 并清/保留旧键兼容一版。
- **删除散落 Switch**：通知页 keepalive/checkpoint；记忆页 variable switch。
- checkpoint 间隔（4/8/16/32）：仍为 Settings 字段；可放在实验页 Global 项展开区或保留附属控件仅在 feature 开启时显示（放实验页更佳）。

### R6 边界
- 不迁移语义记忆、WebServer、已稳定功能。
- 429/Clash 若已有「实验」文案但属独立页：本轮可不迁（非本轮散落债核心）；可注册可选 follow-up。

## Acceptance Criteria

- [ ] AC1 设置主页有「实验性功能」入口，渲染 Global 注册项。
- [ ] AC2 助手详情有「实验性功能」入口，助手级开关互不影响。
- [ ] AC3 热插拔：注册表加项即可显示（至少单测/代码结构证明）。
- [ ] AC4 resolve 作用域隔离；默认全关。
- [ ] AC5 关实验项零副作用（与既有 AC 一致）。
- [ ] AC6 旧布尔用户开启状态迁移到 Map 后行为不变。
- [ ] AC7 散落 UI 移除；中英字符串。
- [ ] AC8 单元测试 resolve + 序列化兼容。
- [ ] AC9 compile + installDebug（有设备时）。

## Out of Scope

- 把 429 Clash 整页迁入（可选 follow-up）。
- stable 转正自动化流水线。
- 工具通道等无关功能。

## Notes

- 用户明确要求：#219/#220/#217 依赖 #215 统一入口，必须补做。
