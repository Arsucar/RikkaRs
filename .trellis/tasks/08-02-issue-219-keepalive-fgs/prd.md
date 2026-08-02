# issue-219: 生成期间常驻前台服务保活（实验性）

## Goal

实验性功能（默认关）：对话生成期间启动 Foreground Service 并展示 ongoing 常驻通知，结束/取消后 stopSelf。提升进程存活优先级，降低长任务被杀概率。与 #220 互补。

对应 GitHub issue: #219。

## Requirements

### R1 默认关零 FGS
- 关：无 FGS、无常驻通知，与现状一致。

### R2 ChatGenerationService
- 对照 `WebServerService`：specialUse + `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`。
- `sendMessage` 生成开始：`startForegroundService` → `startForeground`。
- 结束：onSuccess / onCompletion / onCancel / 异常兜底 → 引用计数归零后 `stopSelf`。
- 多对话并发：单实例 + active 计数，任一结束不误停。

### R3 通知
- 新渠道 `chat_keepalive`（LOW，ongoing）。
- 文案：任务进行中 + 摘要；可复用 live update 节流（1000ms）。
- 点击 → 对应对话（PendingIntent → 路由 Chat(id)）。
- 与现有 `chat_live_update` **不双通知**（常驻生效时抑制或合并 live update）。

### R4 设置
- 设置→通知页 Switch「生成期间常驻通知（实验性）」，默认关。
- 未授 `POST_NOTIFICATIONS` 走既有请求流程（`SettingPreferencesNotificationPage`）。
- `Settings.enableKeepAliveNotification: Boolean = false`，#202 partial 写。

### R5 启动合规
- 启动时机为用户前台操作（发消息/批准工具），不触发后台启动 FGS 限制。

## Constraints

- Manifest 已有 `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `POST_NOTIFICATIONS`；新增 service 声明。
- OEM 可能 SecurityException 拒 FGS（WebServerService 已 catch）：失败 log，不崩，生成继续。
- 新字符串中英本地化。
- 不依赖 #215 / #220。

## Acceptance Criteria

- [x] AC1 默认关无 FGS/常驻通知。
- [x] AC2 开后开始生成即 ongoing 通知可见。（代码；真机点按待人工）
- [x] AC3 成功/失败/取消后通知消失、服务停（dumpsys activity services 可验）。（代码 stop 路径已修；人工 dumpsys 可选）
- [x] AC4 通知点击进对应对话。（代码；冷启与 live-update 同局限）
- [~] AC5 切后台长时间挂起存活率优于未开（手动「不保留活动」抽样）。
- [x] AC6 无通知权限时不崩溃；开关行为符合既有权限流。
- [x] AC7 无双通知/闪烁。
- [x] AC8 中英本地化。
- [x] AC9 installDebug 真机验收。

## Out of Scope

- 忽略电池优化白名单。
- WorkManager 方案。
- 不实现检查点（#220）。

## Notes

- 现状：`WebServerService` specialUse 先例；`RikkaHubApp` 三渠道；`ChatService` Koin 单例非 Service；`ChatNotificationManager` 只展示不保活。
