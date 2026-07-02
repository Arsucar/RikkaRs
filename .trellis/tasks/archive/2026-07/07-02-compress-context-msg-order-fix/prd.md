# fix: 压缩上下文后消息排序错乱与节点隐藏 (#28)

## Goal

修复 GitHub issue #28：执行「压缩上下文」后，继续聊天时流式生成的消息写入错误的 `MessageNode` 槽位，导致界面消息顺序错乱、节点意外隐藏、消息丢失。

## Background

`compressConversation` 将旧节点标记 `hidden=true` 并在 `messageNodes` 中插入摘要节点，使物理下标与可见消息下标不再一一对应。但 `updateCurrentMessages` 仍用「可见列表下标」直接读写 `messageNodes[物理下标]`，导致写错节点。

## Requirements

- `updateCurrentMessages` 必须按 `UIMessage.id` 精确匹配到对应的可见 `MessageNode` 进行更新，不能依赖物理下标 = 可见下标的假设。
- 当 `messages` 列表长度超过当前可见节点数时，新节点应追加到 `messageNodes` 末尾（而非物理中间位置）。
- 修复不得破坏非压缩场景下的现有行为（无 hidden 节点时与原逻辑等价）。
- 修复后 `compressConversation`、流式生成、子代理进度更新、regenerate 等路径均不再出现下标错位。

## Acceptance Criteria

- [ ] 压缩上下文后继续聊天，AI 回复正确追加到末尾可见节点，不写入 hidden 节点。
- [ ] 压缩后 regenerate / 子代理流式更新正常，无消息错位或丢失。
- [ ] 无压缩的正常对话行为不变（回归测试通过）。
- [ ] 新增单元测试覆盖 leading hidden + summary 场景下的 `updateCurrentMessages`。
- [ ] 编译通过（`.\gradlew :app:compileDebugKotlin --no-daemon`）。

## Out of Scope

- `ChatList` 的 `loading` 指示器在 hidden/summary 存在时指向问题（独立 UX 项）。
- 压缩逻辑本身的物理重排（上层写回已修复则非必须）。
