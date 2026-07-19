# 实现 GitHub issues #158-#162

## Goal

在现有 HookDispatcher、HookRepository、Action Registry 和冻结上下文基础上，完成版本化 Hook 事件、关键词预筛、工具最终失败、子代理终态及错误经验记忆沉淀，并关闭 #158-#162。

## Requirements

- #158：单一版本化 HookEvent/HookEventType，稳定 eventId，旧成功 trigger 兼容；编辑器用单选事件，不新增第二套 dispatcher。
- #159：仅最终文本本地预筛 issue/issues、GitHub Issue URL、基础 #123；未命中零 Run/零 AI，命中证据规范化、限长、幂等。
- #160：仅最终工具/Shell 失败触发；重试中、后续成功、自愈和取消抑制；主会话/子代理字段脱敏并按 logical turn 幂等。
- #161：SubagentResult 持久化、pending tool 终结和上下文冻结后生成 SUBAGENT_COMPLETED；准确记录上下文完整性和裁剪原因。
- #162：复用 SYNC_MEMORY_TABLE Action；非长期错误、上下文不足、脱敏失败、去重查询失败均零写入；sourceEventId 幂等。
- 所有 Hook 失败只审计，不阻塞主会话或子代理。
- UI 仅提供单一事件选择和关键词简单输入，策略继续由评估提示词表达。
- 新增文案覆盖六套 app locale；验证资源/Kotlin/JVM/AndroidTest 源码并安装 Debug 到设备。
- 每条 issue 发布独立中英文交付评论、回读确认后关闭。

## Acceptance Criteria

- [ ] 四种事件可序列化、路由、稳定去重，旧 Hook 行为不变。
- [ ] 关键词未命中不创建 Run，命中规范化与边界测试通过。
- [ ] 中间失败、自愈、取消不触发，最终失败 exactly-once。
- [ ] 四种子代理终态在上下文稳定后触发，完整性语义准确。
- [ ] 错误经验新增/更新/跳过与 sourceEventId 幂等由现有记忆表事务保证。
- [ ] UI、持久化、dispatcher、lease/timeout 与非阻塞测试通过。
- [ ] #158-#162 双语评论已回读且均 CLOSED。
