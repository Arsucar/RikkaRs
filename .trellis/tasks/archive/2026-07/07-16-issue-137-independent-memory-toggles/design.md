# Issue #137 技术设计

## Capability Contract

新增或完善纯 Kotlin 的统一能力解析：

```text
normalMemoryEnabled = assistant.enableMemory
memoryTableEnabled = settings.enableMemoryTable && assistant.enableMemoryTable
```

以 `MemoryCapabilities` 值对象（或等价集中 helper）作为运行时与 UI 派生状态的单一规则。现有 `shouldEnableMemoryTable` 可委托给同一规则或继续作为兼容入口，但不得出现第三套条件。

## Runtime Data Flow

`Settings + Assistant → resolveMemoryCapabilities → ChatService`：

- `normalMemoryEnabled` 控制普通 MemoryRepository 读取；关闭时传空列表给 GenerationHandler。
- `GenerationHandler` 继续只用普通 capability 控制普通 memory prompt 与 `memory_tool`。
- `memoryTableEnabled` 独立控制模板/文档读取、MemoryTableInjectionTransformer 和 `buildMemoryTableToolsIfEnabled`。
- GenerationHandler 无条件合并 ChatService 的非普通记忆工具，保证 OFF/ON 时 `memory_table_tool` 保留。
- Preview 与真实生成共用 `prepareGenerationRequest`，防止上下文检查器和实际请求状态漂移。

MemoryTableRepository 的有效文档/模板与 scope 过滤保持现有隔离逻辑，不引入普通记忆参数。

## UI State

### AssistantMemoryPage

- 三个 Switch 分别写入 `Assistant.enableMemory`、`Settings.enableMemoryTable`、`Assistant.enableMemoryTable`。
- 删除全局表格切换对 `memoryTableAutoSyncEnabled` 的副作用。
- 助手级表格 Switch 保持 `checked = assistant.enableMemoryTable`，仅在全局表格关闭时禁用并展示明确原因。

### AssistantToolsPage

页面读取 `LocalSettings.current`，工具组模型区分：

- `checkedPreference`：助手保存的偏好值。
- `active`：应用全局门控后的实际能力，用于计数和工具列表展开。
- `controlEnabled`：Switch 是否可操作。
- `disabledReason`：全局表格关闭时的可访问文本原因。

普通记忆组只读取 `Assistant.enableMemory`；表格组的 checked 值保留助手偏好，active 使用统一 capability，controlEnabled 只取决于全局表格开关。

页面当前存在硬编码用户文案；本任务触及的工具组标题、计数、状态和描述使用 Android string resources，并按 app 本地化规范提供英文默认值与真实简体中文翻译。

## Persistence and Failure Semantics

不修改 Settings/Assistant 字段或序列化格式。UI 不保存独立本地开关副本，直接从状态流渲染；写入失败时状态流不会确认错误值，重组后回到权威持久化状态。快速切换不同字段通过现有 copy/update 路径分别写入，实施时需检查是否存在基于陈旧 Assistant 快照覆盖另一字段的风险。

## Tests

- 参数化 `MemoryCapabilitiesTest` 覆盖四组合和全局 OFF/助手偏好 ON。
- 更新 MemoryTable 模型测试，确保旧 helper 与统一解析一致。
- 为 AssistantToolsPage 的纯状态/计数 helper 添加 JVM 测试，验证 checked/active/controlEnabled/原因。
- 对生成准备增加回归：普通记忆关闭不加载/传递普通 memories；表格 ON 时表格工具与 Transformer 仍存在。
- 保持 MemoryTableRepository、MemoryTableTools、MemoryTableInjectionTransformer、PreferencesStore 相关既有测试通过。

## Rollback

无 schema 或格式迁移，独立提交可安全回滚；回滚后只恢复旧门控行为，不影响已保存数据。
