# Issue #140 UI Audit

## Current UI findings

- `AssistantMemoryPage.kt:743-750`：空状态只有一行文字，没有图标、说明或 CTA。
- `AssistantMemoryPage.kt:773-822`：文档卡整卡可点击，但只显示模板名与 scope/revision，删除图标常驻且无 content description。
- `AssistantMemoryPage.kt:855-929`：新增流程是单个 `AlertDialog`；模板列表为不可滚动 `Column`，模板项只显示名称。
- `AssistantMemoryPage.kt:896-927`：私有创建占 confirmButton，全局创建与取消同时塞入 dismissButton，作用域与动作语义混杂。
- `AssistantMemoryPage.kt:670-701`：已存在 `primaryDocumentsByTemplate`，可用于“已添加/打开已有文档”状态，不需要新增数据层协议。

## Reusable repository patterns

- `SkillsPage.kt:247-352` `SkillCard`：整卡点击、图标容器、描述、元数据 pill、overflow 危险操作。
- `WorkspaceSelectSheet.kt:37-161`：可滚动 `ModalBottomSheet` 选择列表、选中 tick、独立管理入口。
- `ExtensionContent.kt:294-318` `ExtensionEmptyState`：带可选文字动作的空状态。
- `Tag.kt:22-68`：适合 scope/status 的紧凑标签。
- `WorkspacePage.kt:493-534`：trim、字段即时错误和无效提交禁用。

## Contracts to preserve

- `.trellis/spec/app/memory-capabilities.md`：文档主列表、文档级删除、Room 成功后导航、错误保留弹层且不显示原始 exception。
- `.trellis/spec/app/ui-localization.md`：新增/修改的可见文本与 content description 必须资源化，default 与简中必须真实覆盖。
- 路由继续使用已持久化的 `MemoryTableDocument.id`；不恢复 `documentId=null` 的旧需求草案。

## Known boundaries

- 数据库未对 `templateId + scopeType + scopeId` 建唯一索引；本任务只通过已加载 UI 状态避免普通重复点击创建。
- 历史同模板额外文档继续显示，不合并、不删除。
- 当前仓库没有 assistant memory page 的 Compose UI test 基架，优先扩展纯 helper 测试并做设备验收。
