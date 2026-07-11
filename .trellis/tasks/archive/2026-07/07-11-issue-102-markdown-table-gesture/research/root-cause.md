# Issue 102 Root Cause

- `ChatPage.kt` 的右抽屉父级手势在 `PointerEventPass.Initial` 读取并消费水平向左拖动。
- `DataTable.kt` 的 `Modifier.horizontalScroll(hScroll)` 尚未参与仲裁时事件已被父级抢占。
- `Markdown.kt`、`MarkdownNew.kt`、`SimpleHtmlBlock.kt` 的表格都汇聚到 `DataTable`，因此统一修复该组件即可覆盖聊天表格。
- 推荐按触摸起点登记 ChatPage 范围的排除状态，而不是依赖滞后的 `isScrollInProgress`。

