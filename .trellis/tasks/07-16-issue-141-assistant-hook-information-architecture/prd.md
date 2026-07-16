# Issue 141 优化助手 Hook 信息架构

## Goal

优化助手 Hook 列表与编辑页的信息架构，降低误操作并让配置校验和动作类型更清晰。

## Requirements

- 列表卡整卡进入编辑；常驻 trailing action 只保留启用 Switch。
- 删除移动到低误触入口并带确认；摘要统一显示“模型 · 动作类型”。
- 编辑页按基础、运行、规则、动作分区。
- 保存不可用时显示明确的字段校验错误，而不是仅静默禁用。
- prompt 编辑限制可见高度，避免超长文本占满页面。
- action UI 通过可扩展的类型边界渲染当前 `AddConversationTag`，为未来动作类型留出结构。
- 所有新增文案使用 string resources 并提供简体中文翻译。

## Acceptance Criteria

- [ ] 列表卡点击进入编辑，Switch 仍可独立切换且不会触发导航。
- [ ] 删除不再与编辑并排常驻，确认后可删除 Hook。
- [ ] 摘要不再硬编码单一动作文案，统一为模型和动作类型。
- [ ] 编辑页对无效模型/触发条件/动作配置显示可见错误。
- [ ] prompt 区域有明确高度/行数上限并保持可滚动编辑。
- [ ] 当前动作行为保持不变，相关编译与测试通过。

## Out of Scope

- 不改变 Hook 执行、租约或 exactly-once 持久化协议。
- 不在本 issue 中新增第二种实际动作类型。

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
