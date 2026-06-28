# 子代理与UI优化总任务

## Goal

统筹四个子任务的交付，确保子代理流式输出、全局配置入口、提供商列表UI优化在集成时无冲突，最终体验一致。

## Child Task Map

| Slug | Deliverable | Dependency |
|------|-------------|------------|
| `06-27-sub-agent-streaming-ui` | 流式输出 + think/summary 块 UI | 无外部依赖，优先实施 |
| `06-27-sub-agent-global-config` | 全局配置入口（扩展管理） | 依赖 streaming-ui 的 data model 变更（metadata schema 稳定后） |
| `06-27-provider-list-ui-opt` | 提供商列表UI信息展示优化 | 无外部依赖，可与 streaming-ui 并行 |

## Cross-Child Acceptance Criteria

- [ ] 所有子任务各自的 AC 全部通过
- [ ] 流式子代理卡片与全局配置的子代理 profile 选择器共存无冲突
- [ ] 提供商列表改动不影响模型选择器（ModelList）已有的收藏/搜索功能
- [ ] 无重复 ViewModel/Store 调用冲突

## Out of Scope

- 子代理 AI 日志完善（你提到但暂未拆为子任务，后续可追加 child）

## Open Questions

- 无（子任务各自内部的问题在各自 PRD 中记录）
