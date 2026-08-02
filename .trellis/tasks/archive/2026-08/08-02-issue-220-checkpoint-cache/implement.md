# issue-220 implement

## 有序 Checklist

1. [ ] Settings 字段 + PreferencesStore partial 写 + 设置页 UI（开关+间隔灰化）。
2. [ ] Conversation/Entity 检查点元数据 + Repository 编解码。
3. [ ] GenerationChunk.Messages 暴露 stepIndex（或 ChatService 计数）。
4. [ ] ChatService：触发逻辑 + lastCheckpointStep + 失败降级；评估 skipFts。
5. [ ] 完成态落盘时清除 checkpoint 标记 / 设 Final。
6. [ ] 进入对话恢复提示。
7. [ ] 单测：触发间隔、元数据、恢复判定。
8. [ ] 手动杀进程矩阵 + installDebug。

## 验证命令

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest --tests "*Checkpoint*"
.\gradlew --no-daemon :app:installDebug
# adb shell am kill <package> 后重开对话
```

## Review 门

- [ ] 默认关零行为变化
- [ ] #202 部分写
- [ ] 失败不阻断生成
- [ ] 提示仅检查点

## 回滚点

- 开关默认 false；代码可 feature 整块 revert。

## 工作量

**L**（2–3 天）：落盘链路 + 元数据 + UI + 杀进程验证。

## 风险

- FTS 性能；Room 迁移；与 #219 同时开时的资源占用（可接受）。
