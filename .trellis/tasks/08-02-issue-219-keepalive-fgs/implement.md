# issue-219 implement

## 有序 Checklist

1. [ ] Settings 字段 + PreferencesStore partial + 通知页 Switch + 权限流。
2. [ ] Manifest 声明 ChatGenerationService + specialUse meta。
3. [ ] 渠道 chat_keepalive。
4. [ ] 实现 ChatGenerationService（对照 WebServerService）。
5. [ ] ChatService 钩子 + AtomicInteger 计数。
6. [ ] 与 ChatNotificationManager 消双通知。
7. [ ] PendingIntent 跳转 Chat(id)。
8. [ ] 手动矩阵 + installDebug。

## 验证命令

```powershell
adb devices  # 或 connect 100.99.129.110:5555
.\gradlew --no-daemon :app:installDebug
adb shell dumpsys activity services | Select-String -Pattern ChatGeneration
```

矩阵：开关×权限×成功/失败/取消×多对话。

## Review 门

- [ ] 默认关
- [ ] 计数无泄漏
- [ ] 无双通知
- [ ] SecurityException 不崩

## 回滚点

- 整服务 + 开关 revert。

## 工作量

**M–L**（1.5–2 天）。

## 风险

- 通知 ID 冲突；ROM 差异。
