# Issue 140 记忆表文档列表与新增流程

## Goal

让助手记忆页围绕已创建的 `MemoryTableDocument` 展示和操作，降低模板与文档混淆，并提供明确的新增表格流程。

## Requirements

- 主列表只展示已创建的文档；每张卡整卡打开对应文档。
- 加号打开“新增表格”弹窗，可选择 effective template，或进入私有/全局模板创建。
- 模板创建必须成功持久化后才能创建/导航到文档；失败时保留弹窗并显示错误。
- 删除卡片只删除文档，不隐式删除模板。
- 复用现有 `MemoryTableRepository`、`AssistantDetailVM` 和路由模型，不新增平行持久化协议。
- 为新增状态与持久化顺序补充测试；用户可见文案使用 string resources。

## Acceptance Criteria

- [ ] 文档列表为空时显示现有空状态；有文档时不再渲染未使用模板卡片。
- [ ] 选择 effective template 可成功创建文档并在成功后导航。
- [ ] 创建私有/全局模板的失败不会提前导航；成功后可继续创建文档。
- [ ] 删除文档不会删除其模板，刷新后模板仍可用于新建。
- [ ] `:app:compileDebugKotlin` 与相关测试通过。

## Out of Scope

- 不改变会话归档（#136）或助手归档。
- 不重做独立的模板管理页面。

## Goal

TBD.

## Requirements

- TBD

## Acceptance Criteria

- [ ] TBD

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
