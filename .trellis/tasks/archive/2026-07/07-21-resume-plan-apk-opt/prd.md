# Resume Plan.md APK optimization from unfinished P0-P4

## Goal

从 `Plan.md` **真实未完成项**恢复过夜 APK/构建优化工作：逐项完成或形成充分审查/跳过证据，更新清单与候选台账，重新采集最终指标，并修订 `OPTIMIZATION_REPORT.md` 等结论文档。禁止再用“安全候选已耗尽”替代 `Plan.md` 审查义务。

## Background / prior state

- 首轮已交付且仍有效的提交（保留，不整包回滚）：
  - `de7d45e8` baseline/plan docs
  - `a8575336` B1 parallel Gradle（冷构建约 -4.62%）
  - `9e225402` R1 markdown template cache
  - `fe91967d` R4 LRU key roulette cache
  - `a3316a88` T2 workspace scanner temp cleanup
  - `e54e7e92` 过早的 overnight report（需修订，不可作为“Plan 完成”证据）
- `Plan.md` 勾选与实际执行严重不一致（用户审计）：
  - 阶段 0：7/7 完成
  - P0：2/6（缺 4 项）
  - P1–P4 与收尾：清单上几乎全未勾选；部分工作（R1/R4/T2、报告草稿）做过但未反向核对清单
- 首轮冷构建改善约 **4.36%**，**未达 -20%** 目标；APK 增幅约 **+0.002%**（≤5% 已满足）
- 权威约束仍在仓库 `Prompt.md`（若存在）；候选台账为 `OPTIMIZATION_PLAN.md`；跳过理由为 `OPTIMIZATION_NOTES.md`

## Requirements

### R1 — 清单权威与恢复点

1. 以根目录 `Plan.md` 为**完成状态权威**；任何“完成/跳过”必须回写 `Plan.md` 勾选，并同步 `OPTIMIZATION_PLAN.md` / `OPTIMIZATION_NOTES.md`。
2. 恢复执行顺序固定为：
   - **P0 第 3 项起**（`api`/`implementation` 泄漏与不必要模块依赖）
   - 然后 P0 剩余 → P1 → P2 → P3 → P4 → 阶段 6 收尾
3. 已接受的 B1/R1/R4/T2 **不得重复实施**；须在对应 Plan 项上补记“已完成+证据引用”，避免重复劳动。

### R2 — 审查义务（不得用耗尽论替代）

对每一条 `Plan.md` 未勾选项，必须二选一并落盘：

- **实施**：有代码/配置证据 + 可比较验证 + 行为兼容；或
- **充分跳过**：写明已执行的审查步骤、证据路径、风险与为何不实施；**“无安全候选” alone 不算完成**。

### R3 — 测量与门禁

1. 冷构建协议与首轮一致（同一 host/产物路径/ABI）：  
   `.\gradlew --no-daemon clean assembleDebug --profile --no-build-cache --no-configuration-cache --quiet`，三次中位数。
2. 同一 debug APK 产物：路径、变体、ABI、精确字节数、SHA-256 必须记录；相对 baseline 增幅 **≤5%**（与 `Plan.md` P3 验证一致）。
3. 构建成功；受影响聚焦测试通过；最终 JVM `test` 通过。
4. detekt/ktlint 未配置则记 N/A，**不为指标新增工具**。
5. 不默认跑 `connectedDebugAndroidTest`；不改 CI workflow。
6. app 功能改动按 AGENTS.md 做设备安装验收（`adb devices` / 必要时 `adb connect 100.99.129.110:5555` / `installDebug`）。
7. Gradle 一律 `--no-daemon`；多子代理时**仅最后一个检查代理**可编译。

### R4 — 文档与结论诚实性

1. 全部 Plan 项处理完后，重采最终指标并**修订** `OPTIMIZATION_REPORT.md`（区分：达到目标 / 改善未达标 / N/A / 未验证 / 本轮新实施）。
2. 报告必须写明：首轮过早结案错误、本轮恢复范围、仍未达标的目标（若冷构建仍未 -20%）。
3. 最终文档提交信息遵循仓库约定（如修订 report 的 docs commit）；**不**在未获用户明确要求时 push/发版。

### R5 — 安全与范围

1. 不引入破坏反射/R8/原生加载的高风险改动，除非有同变体验证。
2. 不再生 lint baseline；不把历史 lint 失败归因于本轮优化。
3. 不修改用户未要求的 agent-only 配置目录推送策略。

## Out of scope

- 为凑 -20% 而引入未验证的激进依赖删除或破坏兼容性的模块拆分
- 新增 Macrobenchmark/Perfetto 基础设施（可作为推荐，非本任务必达，除非为实现某 P1 项所必需且用户同意扩大范围）
- 正式发版、打 tag、开 PR 到 upstream

## Acceptance Criteria

- [x] `Plan.md` 每一条 P0–P4 与阶段 6 项均已勾选，或明确标记为已审查跳过并在 NOTES/PLAN 有对应证据
- [x] 恢复起点为 P0「api/implementation 泄漏」及之后全部未完成项，无跳阶段宣称完成
- [x] 每个实施候选：证据 → 验证 → 文档；失败可逆（未提交则恢复工作区；已提交则独立 revert commit）
- [x] 最终冷构建三次中位数、APK 字节/SHA-256、JVM test 结果写入修订后的 `OPTIMIZATION_REPORT.md`
- [x] 报告诚实标注目标达成状态；不得声称“Plan 已完整执行”除非清单全覆盖
- [x] 有 app 源码改动时完成 installDebug 或按 AGENTS.md 上传 APK 回退路径
- [x] 无 secrets 提交；无未授权 push

## Notes

- 本任务为**恢复执行 + 纠正文档**，不是从零 baseline。
- 父任务可直接承载全部阶段工作；若后续单阶段过大再拆子任务。
