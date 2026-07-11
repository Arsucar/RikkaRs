# 修复 Issue 102 Markdown 表格横滑手势冲突

## Goal

用户在聊天消息的 Markdown/HTML 表格内水平滚动时，右滑抽屉不得误触；表格外的左右抽屉手势保持原有行为。

## Confirmed Facts

- 右抽屉由 `ChatPage` 的全屏父级 `pointerInput` 在 `PointerEventPass.Initial` 识别并消费向左拖动。
- `DataTable` 使用 `horizontalScroll`，父级会在表格参与手势仲裁前抢占事件。
- GFM Markdown、MarkdownNew 和 HTML 表格最终都走同一个 `DataTable`。
- 左抽屉使用原生手势仲裁，因此表格横滑时表现正常。

## Requirements

- 触摸从可水平滚动的表格区域开始时，右抽屉不得 claim/consume 该手势。
- 表格仍能左右滚动，聊天列表的纵向滚动不受影响。
- 触摸从表格外开始时，右抽屉仍可正常打开。
- 只对实际溢出、可横向滚动的表格启用排除；非溢出表格不形成手势死区。
- 手势取消、重组、多指或快速重复操作后不得残留排除状态。
- ChatPage 以外复用 `DataTable` 的页面保持原行为。

## Acceptance Criteria

- [ ] 宽 Markdown 表格内向左/向右拖动只滚动表格，不打开右抽屉。
- [ ] 表格外向左拖动仍能打开右抽屉，向右拖动的左抽屉行为不回归。
- [ ] 表格区域纵向拖动仍能滚动消息列表。
- [ ] 非溢出表格不会无条件屏蔽右抽屉。
- [ ] 自动化测试覆盖排除状态与右抽屉 claim 判定。
- [ ] Debug APK 成功安装到设备并完成 GFM/HTML 表格手动验收。
- [ ] #102 留下验证证据评论后关闭。

## Out of Scope

- 重写左右抽屉整体架构。
- 修改 Markdown/HTML 表格解析逻辑。
- 默认运行 `connectedDebugAndroidTest`。

