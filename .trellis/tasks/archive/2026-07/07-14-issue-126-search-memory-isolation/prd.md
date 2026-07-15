# 完成 Issue 126 助手搜索记忆隔离

## Goal

确保搜索结论经记忆工具或记忆表沉淀后严格归属当前助手，不污染其他助手。

## Requirements

1. 搜索工具本身保持只返回本轮 output；经 `memory_tool` 或表格写入时默认绑定当前 assistantId/ASSISTANT scope。
2. 条目标、记忆表、助手记忆页、抽屉、prompt 注入和 tool list 统一使用 GLOBAL + 当前助手 effective 查询。
3. 任意已知 ID 的读取、更新、删除不得绕过助手授权；缺失 assistantId 不得降级为 GLOBAL。
4. 删除助手只清理其 ASSISTANT 数据，不删除 GLOBAL 或其他助手数据。
5. `enableMemory=false` 时不注入。
6. per-assistant 搜索开关/服务选择为 Could；只有不扩大迁移风险且能完整测试时纳入本次。

## Acceptance Criteria

- [ ] A 搜索后写入的记忆在 B 的页面、注入和 tool list 均不可见，GLOBAL 双方可见。
- [ ] #122 模板/文档隔离作为本任务回归项通过。
- [ ] 删除助手、禁用记忆、快速切换和非法 assistantId 行为有测试。
- [ ] 数据/Domain 层完成授权，不依赖 UI-only 过滤。

## Notes

- Must 全部完成后才可关闭；Could 未做时在 issue 评论明确说明后续边界。
