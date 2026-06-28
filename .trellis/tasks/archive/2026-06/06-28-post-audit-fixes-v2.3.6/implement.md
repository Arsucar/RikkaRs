# Implement: v2.3.6 Post-Audit Fixes (父任务)

> 父任务负责调度与最终验收，业务实现见各子任务 implement.md。

## 执行顺序

- [ ] **0.1** 确认 4 个子任务已创建并链接为本任务子任务（`task.py list --tree`）。
- [ ] **0.2** 父任务 `task.py start`（或保持 `pending`，由各子任务 `start` 推进；本任务以协调为主）。

## 并行派发（Phase 2）

- [ ] **1.1** 同时启动子代理 A / B / C（各自 `trellis-implement`，prompt 以 `Active task: <子任务路径>` 开头）。
- [ ] **1.2** 同时启动子代理 D 编写测试骨架（不跑完整回归，等 A/B/C 就绪）。
- [ ] **1.3** A/B/C 代码就绪后，D 运行针对性回归 + `test` + `lint`。
- [ ] **1.4** **仅最后一个**子代理执行 `compileDebugKotlin --no-daemon`。

## 最终验收（Phase 3，父任务）

- [ ] **2.1** `git diff --stat release/rikka-arsucar...HEAD` 仅含范围内文件（无 `.omc/`、`*.jks`、无关本地工具文件）。
- [ ] **2.2** `git diff origin/release/rikka-arsucar...HEAD` 无意外文件。
- [ ] **2.3** `.\gradlew :app:installDebug --no-daemon` 装机验收（按 AGENTS「装到设备」流程）。
- [ ] **2.4** CHANGELOG `v2.3.6` 补「子代理流式一致性 / shell 启发式拦截」条目。
- [ ] **2.5** 提醒用户 commit（不自动 commit）。

## 验证命令

```bash
git diff --stat release/rikka-arsucar...HEAD
git diff --name-only origin/release/rikka-arsucar...HEAD
.\gradlew :app:installDebug --no-daemon
```

## 回滚点

- 子任务 commit 独立 → 单独 revert。
- 父任务不直接改业务代码，无独立回滚。
