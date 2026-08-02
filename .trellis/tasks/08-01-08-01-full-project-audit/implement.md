# Implement: 全项目全流程全链路深度审查与优化

## Phase A：审查（并行派发子代理）

1. 记录基线 `BASE=$(git rev-parse HEAD)`（预期 `10b33419`）。工作树必须干净。
2. 一次性派发 D1–D14 共 14 个 general 子代理（只读审查），每个输出结构化报告到 `audit-reports/<domain>.md`（工作目录临时区，最终并入 review-report）。
3. 每个子代理 prompt 前缀：`Active task: <task path from task.py current>` + 审查协议（design §1）。
4. 空结果/只复述任务 → 最多重派一次。

## Phase B：汇总定级（主代理）

5. 收集 14 份报告，交叉验证（design §3）。
6. 合并去重，按 CRITICAL/HIGH/MEDIUM/LOW 定级，产出修复批次清单。
7. HIGH/CRITICAL 且无交叉验证矛盾的 → 进入 Batch 1。

## Phase C：优化

8. **Batch 1**：CRITICAL/HIGH 修复。实现子代理并行处理不同文件子集；主代理整合 Edit。
9. 每个修复后跑受影响模块聚焦 JVM 单测。
10. **Batch 2**：MEDIUM 修复（改动范围可控者）。
11. **Batch 3**：LOW + 未来风险 → 仅记录。

## Phase D：验证（最后一个验证子代理）

12. 最终一次 Gradle 调用：资源处理 + `:app:compileDebugKotlin` + 全量 JVM 单测（+ AndroidTest 源码编译，若需要）。全部带 `--no-daemon`。
13. fork 不变量复核（applicationId / 无 Firebase / release-apk.yml / #59 压缩）。
14. 有设备：`adb devices` → 无则 `adb connect 100.99.129.110:5555` → `.\gradlew --no-daemon :app:installDebug`；失败重连重试一次。
15. 无设备：`assembleDebug` + 如实记录；勿假称真机通过。

## Phase E：收尾

16. 汇总 `review-report.md`（问题清单/已修复/已记录/验证证据/已知边界）。
17. 按用户确认 commit（不自动 push）。

## 纪律

- 所有 Gradle 命令带 `--no-daemon`；仅最后一个验证子代理允许跑 Gradle。
- 不默认 `:app:lintDebug` / `connectedDebugAndroidTest`。
- 不向上游开 PR；不破坏 fork 不变量。
- 完成 app 模块功能改动后必须执行安装验收。

## Risky files

- `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt`
- Chat/GenerationHandler/ChatVM 相关
- `ai/src/main/java/me/rerere/ai/*` 消息转换与截断
- `app/data/db` 迁移链
- `workspace/**` 安全边界
- 备份/WebDAV/S3 序列化字段

## Rollback points

- 编译大面积红 → reset 到 `BASE`，分批修复。
- 某域审查结论无法验证 → 标记 LOW/待办，不阻塞。
