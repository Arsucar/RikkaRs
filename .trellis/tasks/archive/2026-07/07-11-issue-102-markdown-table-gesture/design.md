# Design

## Gesture Ownership

新增 ChatPage 范围内的水平手势排除状态，通过 CompositionLocal 向消息内容树提供。`DataTable` 在触摸按下时、且 `ScrollState.maxValue > 0` 时 acquire；抬起、取消或协程结束时在 `finally` release。

右抽屉在越过 touch slop、决定 claim 前按起始 pointer ID 读取排除状态。该 pointer 的排除激活时父级不 consume，事件交给表格 `horizontalScroll`。

## State Contract

- 按 pointer ID 使用引用计数而不是全局 Boolean，支持多指和嵌套区域且避免不同手指串扰。
- acquire/release 必须成对，计数不得小于 0。
- ChatPage 外的 CompositionLocal 默认实现为空操作，因此通用 `DataTable` 无行为变化。
- 手势所有权按触摸起点决定：表格外开始后滑入表格仍由抽屉处理。

## Rejected Alternative

不把右抽屉监听简单改到 Main pass；现有注释和行为表明这会让内层/左抽屉先消费，导致右抽屉失效。

## Rollback

改动集中于新的 context、`ChatPage` claim 条件和 `DataTable` 登记逻辑，可按该子任务提交独立回退。
