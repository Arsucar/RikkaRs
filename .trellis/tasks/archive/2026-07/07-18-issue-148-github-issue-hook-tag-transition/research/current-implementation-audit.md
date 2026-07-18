# 当前实现审计摘要

- #147 已建立 action-specific Handler/Registry/Dispatcher 与 handler-owned atomic terminalization；Transition 应沿用而非新建调度链。
- AddTag 当前只在标签事务内复查 lease，标签副作用与 execution terminalization 分事务，且 source active 只在事务外检查。
- ConversationTag add/remove 已是唯一关系写边界并具备幂等语义；组合事务必须 remove 后 add 以支持 20 标签满额交换。
- MessageNodeDAO 缺少 conversation/node 窄读取，可新增查询在事务内验证 hidden/selectIndex/source message，无需 schema migration。
- v42 generalized audit 字段足以保存 tag transition summary/diff，无需 v43。
- Final-success 只保证最终消息和 tools 已完成；`toText()` 不含 tool output。V1 使用最终 assistant 文本的严格 ref + 成功词证据，避免读取/持久化 raw tool/subagent transcript。
- Hook editor 现有 validation/action label 是集中纯函数边界，但 tag flow 无法区分 loading/empty/error；#148 需要显式 UI state。
