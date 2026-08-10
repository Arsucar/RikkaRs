# PRD: feat(#237) 统一状态反馈 EmptyState/Shimmer/Sonner

## Goal

建立统一 Loading（Shimmer 骨架）/ Empty / Error 组件，并把 3 个高频页渐进替换；LogPage 反馈统一为 Sonner（ToasterContext）。

## Background

- `ui/modifier/Shimmer.kt` 已实现但引用 0 次；页面 loading 大量 `CircularProgressIndicator`
- 无统一 EmptyState/ErrorState；各页手写空态/错误
- LogPage 用系统 Toast，其他页用 Sonner → 分裂

权威：issue #237。方案 **A：统一组件 + 渐进替换 3 高频页**（会话列表 / 统计 / 日志）。

本任务 **lightweight：仅 PRD**（无 design/implement 强制）。

## Requirements

### 组件（`ui/components/ui/`）

1. `EmptyState(icon, title, description, action?)` — 图标 + 主/次文案 + 可选按钮
2. `ErrorState(title, message, retry?)` — errorContainer 配色 + 可选重试
3. 启用现有 `Shimmer`：形状贴近真实内容；骨架→内容可用 crossfade

### 渐进替换（3 页）

1. **会话列表** — Loading→Shimmer；空/错→统一组件
2. **统计页** — 同上
3. **日志页** — 同上；**Toast → ToasterContext（Sonner）**

### 约束

- MaterialTheme + CustomColors；Dark Mode
- 文案 `strings.xml`（若不阻塞可先硬编码，优先功能）
- 无导航变更；无数据模型变更
- 不要求一次替换全部 77 处 CircularProgressIndicator

## Non-goals

- 全应用一次性替换所有 loading/空态
- 新 analytics 事件
- 仅建组件不替换任何页（方案 B，本任务不采用）

## Acceptance Criteria

- [ ] AC1: `EmptyState` / `ErrorState` 落地于 `ui/components/ui/`，可被复用
- [ ] AC2: Shimmer 在会话列表与统计页 loading 使用（替换页面级转圈）
- [ ] AC3: 会话列表 / 统计 / 日志 三页空态或错误态至少一处改为统一组件（issue：替换 3 高频页空/错态）
- [ ] AC4: LogPage 用户反馈走 Sonner/ToasterContext，不再用系统 Toast 作为主通道
- [ ] AC5: Dark Mode 下组件可读；重试/空态操作按钮在需要处可用
- [ ] AC6: 截图或手动对比 loading/empty/error 三态（测试 checklist）

## 实现提示（非设计文档）

| 项 | 路径提示 |
|---|---|
| Shimmer | `app/.../ui/modifier/Shimmer.kt` |
| 新组件 | `app/.../ui/components/ui/` |
| 会话列表 | Chat 相关 list 页 / VM `emptyMessage` 等 |
| 统计 | 统计页 loading/empty |
| 日志 | LogPage Toast → ToasterContext |

## Notes

- 复杂跨层行为无；PRD-only 足够启动实现
- 其余页面可后续任务继续替换
