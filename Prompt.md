# RikkaRs 彻夜无人值守优化 — 规格

## 目标
对 RikkaRs（Kotlin/Compose/Koin/Room/OkHttp/Coil，多模块）执行有证据、可回滚的深度优化，覆盖构建性能、运行时性能、代码质量、APK 体积和测试健壮性。

## 成功目标
- `./gradlew --no-daemon assembleDebug` 零错误
- `./gradlew --no-daemon test` 全部通过
- 可比的 Gradle `--profile` 冷构建时间相对 baseline 下降至少 20%
- 同一变体、同一 ABI、同一产物的 APK 精确字节数增幅不超过 5%
- 仓库已配置的 detekt/ktlint 警告数下降至少 50%
- app 功能改动按 AGENTS.md 完成设备安装验收；无设备时如实记录缺口

20% 和 50% 是优化目标，不是伪造数据或引入无收益改动的理由。若安全候选耗尽仍未达到，必须在最终报告中标为未达成并提供真实数据。

## 硬约束（不可触碰）
- 不修改 LICENSE、applicationId、签名配置、`.gitmodules` 或 CI release/publish pipeline
- 不执行 `git push`，不修改 `.git/config`
- 不删除测试，不破坏或移除任何用户可感知功能
- 不丢弃 Goal 启动前已经存在的工作区改动
- 不使用 `git reset --hard`，不盲目执行 `git revert HEAD`
- 每个 commit 前必须通过 `./gradlew --no-daemon assembleDebug`
- 逻辑变更必须通过对应模块的聚焦测试
- 遵守 AGENTS.md 与 Trellis 工作流

## 允许且鼓励（必须有证据）
- kapt → KSP 等构建插件迁移
- 替换或移除确认无用、过重或泄漏到 API 的依赖
- 删除经引用搜索和构建验证确认的 dead code、废弃类和重复实现
- 调整模块依赖方向与 `api` / `implementation` 边界
- 为明确瓶颈重构模块内部实现，例如主线程同步 IO、重复解析或重复订阅
- 优化 Room 查询/索引、Compose 重组、OkHttp/Coil 配置及热路径分配
- 调整 ProGuard/R8 规则、资源收缩和 baseline profile
- 补充能保护优化行为的单元测试

新增或替换第三方依赖必须说明净收益、兼容性和替代方案，且不得仅为满足指标而引入 detekt/ktlint 等工具。模块边界或架构调整必须保持公开契约兼容，并拆为可独立回滚的提交。

## 明确非目标
- UI/UX 改版或产品功能开发
- 无测量依据的全局架构重写、DI/网络栈迁移
- 为跑通 release 构建而修改签名配置
- 修改任何发布自动化

## 测量协议
- 开始修改前记录 commit、分支、JDK、Gradle/AGP、主机环境和 `git status`
- 冷构建主指标：相同环境与缓存策略下，运行三次相同的 `clean assembleDebug --profile`，比较 baseline 与最终结果的中位数
- 可另记 no-op/incremental 构建，但不得与冷构建混为同一指标
- APK 使用开始时选定的同一产物路径比较精确字节数；若 release 变体原本不可构建，不修改签名来强行测量
- 静态分析只使用仓库启动时已经存在且可运行的任务；未配置则标记 N/A
- 每项运行时优化记录瓶颈证据、预期机制及可执行验证；不得只凭直觉宣称性能提升

## 交付物
- 一系列可独立 revert 的原子 commit
- `OPTIMIZATION_PLAN.md`（候选、证据、风险、状态、结果）
- `Documentation.md`（实时命令、结果和指标变化）
- `OPTIMIZATION_NOTES.md`（失败、跳过项和边界；有内容时创建）
- `OPTIMIZATION_REPORT.md`（最终事实报告）

## 完成条件
- 所有安全且有证据的 P0–P4 候选已完成或有理由地跳过
- 最终 assembleDebug 与 test 通过
- 所有指标均给出 baseline、最终值、测量方法和是否达标
- 已完成适用的设备验收，或明确记录未执行原因
- `OPTIMIZATION_REPORT.md` 已生成并以 `docs: add overnight optimization report` 提交
