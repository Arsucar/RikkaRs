# Issue 137 解耦普通记忆与记忆表格

## Goal

让普通记忆与记忆表格成为真正独立的两种能力，使用户可自由使用 OFF/OFF、ON/OFF、OFF/ON、ON/ON 四种组合，且 UI、持久化和生成运行时语义一致。

## Requirements

- 普通记忆有效状态只由 `Assistant.enableMemory` 决定；记忆表格有效状态只由 `Settings.enableMemoryTable && Assistant.enableMemoryTable` 决定。
- 普通记忆关闭时，不读取/注入普通记忆、不暴露 `memory_tool`，但不得影响记忆表格模板/文档读取、注入或 `memory_table_tool` 的 read/query/upsert/patch/delete。
- 记忆表格关闭时，不读取/注入表格或暴露表格工具，但普通记忆链路保持正常。
- 全局表格开关关闭时保留助手级 `enableMemoryTable` 偏好；重新开启全局开关后恢复原选择。
- 普通记忆、全局表格和助手级表格 Switch 只能更新各自字段，不得顺带重置另一开关或 `memoryTableAutoSyncEnabled`。
- `AssistantMemoryPage` 与 `AssistantToolsPage` 使用同一能力判定；赋能工具页需要显示全局门控原因并禁止无效切换，但不能借用普通记忆状态。
- 现有三个持久化字段名、默认值、备份导入/恢复语义保持兼容，不新增 Room schema。
- UI 状态直接来源于 Settings/Assistant 状态流，不引入会显示虚假成功的本地乐观副本；新增或修改文案进入字符串资源并提供简体中文翻译。

## Acceptance Criteria

- [ ] 纯 Kotlin 测试覆盖四种开关组合并证明两个 capability 无交叉依赖。
- [ ] 普通记忆 OFF、表格 ON 时，表格模板/文档加载、Transformer 注入和 `memory_table_tool` 均启用，普通记忆读取/prompt/tool 均禁用。
- [ ] 普通记忆 ON、表格 OFF 时只启用普通记忆链路；ON/ON 无重复工具，OFF/OFF 无额外数据加载。
- [ ] `AssistantMemoryPage` 和 `AssistantToolsPage` 对两个开关显示一致；切换普通记忆不改变或禁用表格。
- [ ] 全局表格 OFF 时助手级偏好仍保持 checked 值但实际能力不可用并显示原因；重新开启后恢复实际能力。
- [ ] 全局表格切换只修改 `Settings.enableMemoryTable`，不重置助手偏好或自动同步偏好。
- [ ] 现有 Settings/Assistant 序列化默认值和备份兼容测试保持通过。
- [ ] app 编译、相关 JVM 测试、静态检查和可用设备上的安装验收通过。

## Confirmed Facts

- `shouldEnableMemoryTable(settingsEnabled, assistantEnabled)` 已正确实现表格双层门控。
- `ChatService.prepareGenerationRequest` 已用表格 capability 独立加载模板/文档、注册 Transformer 和构建表格工具。
- `GenerationHandler.buildToolsForStep` 只用 `Assistant.enableMemory` 控制普通 `memory_tool`，随后无条件合并 ChatService 提供的其他工具，因此表格工具不会被普通记忆开关包裹。
- 当前缺口包括：`ChatService` 无条件读取普通记忆；全局表格 Switch 会顺带重置 `memoryTableAutoSyncEnabled`；`AssistantToolsPage` 只看助手级表格开关且没有全局门控/原因。
- `AssistantMemoryPage` 的表格入口和助手级 Switch 已只依赖表格自身的全局门控，不依赖普通记忆。

## Out of Scope

- 合并三个布尔字段为枚举或改变现有备份格式。
- 实现目前尚未启用的记忆表格自动同步功能本身。
- 为本功能引入新的 Analytics SDK 或远程服务。
