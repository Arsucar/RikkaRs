# issue-219 design

## 边界

| 层 | 改动 |
|----|------|
| `ChatGenerationService` | 新 Service |
| AndroidManifest | service 声明 + specialUse property |
| RikkaHubApp | 渠道 `chat_keepalive` |
| ChatService | 生命周期钩子 + 引用计数 |
| ChatNotificationManager | 与 live update 协调 |
| Settings + 通知设置页 | 开关 |
| 路由 PendingIntent | Chat(id) |

## 契约

### Service
- Actions: START / STOP 或仅靠 start/stopForegroundService。
- `NOTIFICATION_ID` 独立（勿与 WebServer 2001、live update 冲突）。
- `startForegroundCompat` 同 WebServer：UPSIDE_DOWN_CAKE 起 SPECIAL_USE；catch SecurityException。

### 引用计数
```kotlin
// ChatService 或独立 KeepAliveController
AtomicInteger activeGenerations
onGenerationStart: if (enabled && inc()==1) startService
onGenerationEnd: if (dec()==0) stopService
```

### 双通知
- 推荐：keepalive 开启且 FGS 运行时，live update 不再单独 notify 同内容；或 FGS 通知承担 live 文案更新。
- 实现时读 `ChatNotificationManager` 事件订阅，合并到一处。

### PendingIntent
- 优先复用现有 chat 完成通知的跳转构造；携带 conversationId。

### Settings
```kotlin
enableKeepAliveNotification: Boolean = false
```
partial update，禁止全量 writeFullSettings。

## 数据流

```
sendMessage start → count++ → start FGS + ongoing
progress events → throttle update notification text
end/cancel/error → count-- → count==0 → stopForeground + stopSelf
```

## 取舍

| 方案 | 结论 |
|------|------|
| 仅通知不 FGS | 无保活，拒绝 |
| WorkManager | 不适合长连接生成，拒绝 |
| specialUse 复用 WebServer 模式 | **采用** |

## 兼容 / 回滚

- 默认关；回滚删 service + 开关。

## 风险

- OEM 拒 specialUse：降级无 FGS，已 catch。
- 计数泄漏导致常驻不消失：所有结束路径（含异常）必须 dec；加超时兜底可选。
- 后台启动限制：仅用户前台触发 start。
