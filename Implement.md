# 执行规则

## 启动前
1. 完整读取 AGENTS.md、Trellis 指南和四个 Goal 配套文件。
2. 记录初始 `git status`；Goal 启动前已有的文件和改动归用户所有，不得删除、覆盖或清理。
3. 先完成 baseline，再修改源码或构建配置。
4. 将候选写入 `OPTIMIZATION_PLAN.md`，至少包含证据、预期收益、风险、验证命令和状态。

## 每个候选的闭环
1. 搜索目标符号、配置值、调用点和跨模块消费者。
2. 只修改一个可独立验证、可独立回滚的候选。
3. 先运行最聚焦的静态检查或模块测试。
4. 运行 `./gradlew --no-daemon assembleDebug`；失败则不得提交。
5. app 功能改动按 AGENTS.md 执行设备安装验收；开发期可先聚焦验证，最终必须补齐。
6. 在 `Documentation.md` 记录命令、结果、耗时、指标变化和验证边界。
7. 只暂存本候选及其文档，不使用宽泛的 `git add -A`。
8. 创建原子 commit，并确认提交内容不包含无关文件。

## 收益判定
- 构建性能：使用 Prompt.md 的同环境三次中位数协议；单次最快结果不能作为结论。
- APK：比较同一变体、ABI 和路径的精确字节数，不混用 debug/release 或 universal/arm64。
- 运行时：必须说明瓶颈证据和验证方法；仅有“看起来更快”的代码变化不能宣称已提升。
- 依赖/插件替换：记录新增与移除项、传递依赖变化、兼容性、构建时间和 APK 影响。
- 架构/模块边界：先验证公开契约及全部消费者，拆小提交，禁止顺手进行全局重写。

## 失败与回滚
- baseline 本身失败：停止进入对应优化阶段，记录原始错误和解除阻塞所需输入。
- 未提交候选失败：只恢复本候选引入的改动；不得恢复或覆盖 Goal 启动前的用户改动。
- 已提交候选在后续验证中失败：使用指向准确 commit 的独立 revert commit，随后验证并记录。
- 同一候选因同一原因连续失败 3 次：停止尝试，写入 `OPTIMIZATION_NOTES.md`，转向独立候选。
- 禁止 `git reset --hard`、盲目 `git revert HEAD` 或批量清理工作区。
- 磁盘不足可执行 `./gradlew --no-daemon clean` 后重试，但须记录其对缓存与测量的影响。

## Commit 规范
- `perf(build): ...`
- `perf(compose): ...`
- `perf(room): ...`
- `refactor(common): ...`
- `chore(resources): ...`
- `test(module): ...`
- 最终报告固定为 `docs: add overnight optimization report`

每个 commit 必须能独立解释、验证和 revert。提交前执行 `git diff --cached --check` 并检查 staged diff。

## 禁止操作
- `git push` 或修改 `.git/config`
- 修改 LICENSE、applicationId、签名配置、`.gitmodules` 或 CI release/publish pipeline
- 删除测试或用户可感知功能
- 为满足指标而伪造、挑选或混用不可比数据
- 为运行静态分析或 release 构建而擅自增加工具、改签名或修改发布流程
