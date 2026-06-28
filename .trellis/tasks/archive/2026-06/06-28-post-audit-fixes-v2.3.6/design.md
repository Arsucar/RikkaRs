# Design: v2.3.6 Post-Audit Fixes

## 边界

- 父任务：任务地图、跨子任务验收、最终合并到 `release/rikka-arsucar`。**不**直接改业务代码。
- 子任务 A/B/C：独立实现，互不依赖代码改动；可并行。
- 子任务 D：测试与回归，**依赖** A/B/C 代码就绪后才能跑针对性回归，但可先并行编写测试骨架。

## 任务依赖与并行度

```
A (subagent streaming)  ──┐
B (shell hardening)     ──┼──> D (tests/regression) ──> 父（合并 + 验收）
C (non-blockers)        ──┘
```

- A、B、C 可同时启动。
- D 的测试编写可与 A/B/C 并行；D 的**运行验证**需在 A/B/C 代码就绪后。
- 多个子代理同时改代码时，**只有最后一个**做完整 `compileDebugKotlin`，避免主机内存耗尽（AGENTS 规则）。

## 合并策略

1. 子任务各自在 `release/rikka-arsucar` 上提交（或各自短分支后 cherry-pick）。
2. 父任务负责确认 `git diff --stat release/rikka-arsucar...HEAD` 仅含范围内文件。
3. 冲突优先在子任务内解决；父任务仅做最终整合与跨子任务回归。

## 验证命令（统一）

```bash
.\gradlew :app:compileDebugKotlin --no-daemon      # 最后一个子代理
.\gradlew test --no-daemon                          # D 子代理
.\gradlew lint --no-daemon                          # D 子代理
adb devices                                          # 父任务装到设备前
.\gradlew :app:installDebug                         # 父任务最终验收
```

`--no-daemon` 必须带，避免 Gradle daemon 阻塞并行子代理。

## 回滚

- 每个子任务独立 commit；若某子任务验证失败，`git revert <sha>` 不影响其他子任务。
- 父任务不 squash 子任务 commit，便于 bisect。

## 兼容性

- 不改 DB schema（`MessageNodeEntity` / `ConversationEntity` 不动）。
- 不改 DataStore key（`WEB_SERVER_LOCALHOST_ONLY` 语义不变）。
- 不改公开发版 workflow。
