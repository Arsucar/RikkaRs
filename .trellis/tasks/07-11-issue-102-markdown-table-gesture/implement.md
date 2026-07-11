# Implementation Plan

1. 新增 `HorizontalGestureExclusionState`、CompositionLocal 和可测试的 claim 判定。
2. 在 `ChatPage` 提供状态，并在右抽屉 claim 前检查排除状态。
3. 在 `DataTable` 可滚动区域的 pointer 生命周期内 acquire/release，确保 `finally` 清理。
4. 添加 JVM 单测覆盖引用计数、非溢出行为和右抽屉 claim 矩阵。
5. 静态审查所有 `DataTable` 调用路径，确认无需分别修改 Markdown/HTML 渲染器。
6. 最终检查阶段运行聚焦测试、app 编译和 `:app:installDebug`，在设备上验收 GFM/HTML 表格与两侧抽屉。
7. 评论并关闭 #102，更新 PRD/规格和 Trellis 状态。

