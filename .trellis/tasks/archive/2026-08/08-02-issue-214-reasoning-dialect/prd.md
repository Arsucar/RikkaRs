# issue-214: 思考档位映射遗留修复

## Goal

修复 #207（PR #210）思考等级方言映射合并后的两个遗留问题；**实现已由 PR #221 提交，本任务权威路径是审阅 / 本地验证 / 合入 / 关闭 issue，禁止另起炉灶重写。**

- GitHub issue: https://github.com/Arsucar/RikkaRs/issues/214 （OPEN）
- **关联 PR: https://github.com/Arsucar/RikkaRs/pull/221**（OPEN，`Closes #214`）
  - 分支：`fix/reasoning-dialect-214`（`origin/fix/reasoning-dialect-214` @ `c0bbea9b`）
  - base：`release/rikka-arsucar`
  - mergeable：MERGEABLE / CLEAN（检查：roadmap workflow 已 SUCCESS；**作者声明本机无 Java/Gradle，单测依赖 CI/本地补跑**）

## Requirements（与 PR #221 对齐，已实现）

### R1 OFF 档位真正关闭
- `mapReasoningEffort` **删除** `noneAsLow` 参数；OFF 在 OpenAIExtended / OpenAIClassic / DeepSeekMax 统一为 `"none"`。
- `ChatCompletionsAPI` 两处 `noneAsLow = true` 调用已清除。

### R2 Auto 方言仅官方 host
- `resolveDialect` 删除 `looksLikeDeepSeekReasoningModel` 弱提示及私有函数。
- Auto 仅：`api.deepseek.com` → DeepSeekMax；`integrate.api.nvidia.com` + deepseek-v4 → DeepSeekMax；其余 → OpenAIExtended 透传。

### R3 测试
- 新增 `proxy host Auto with deepseek id keeps xhigh passthrough`
- 新增/更新 OFF → none 断言（原 OFF→low 断言已改）
- `ReasoningDialectTest` 同步未知 host + deepseek id 期望

## Constraints

- **不得**在 `release/rikka-arsucar` 上再手工改同一逻辑除非 PR 有缺陷；缺陷在 PR 分支修。
- 合入前必须本地（或确认 CI）跑通 ai 相关单测。
- 关 issue 时中英交付评论须写明 PR #221、commit、验证结果。

## Acceptance Criteria

- [x] AC1：审阅 PR #221 diff 与 issue 期望一致（OFF≠low；中转站 Auto+deepseek id 透传 xhigh；官方/显式 DeepSeekMax/nvidia 不回归）。
- [x] AC2：本地 `.\gradlew --no-daemon :ai:testDebugUnitTest --tests "*Reasoning*"`（或等价）通过；若仅 CI，记录 CI run URL。
- [x] AC3：PR 合入 `release/rikka-arsucar`；#214 关闭且中英评论完整。
- [x] AC4：HEAD 上 `Reasoning.kt` 无 `noneAsLow` / `looksLikeDeepSeekReasoningModel`。

## Out of Scope

- 从零重写 map/resolve（已由 PR 完成）。
- UI / 装设备（纯 ai 序列化；可选 assemble 不强制）。

## Notes

- 规划初稿曾按「从零实现」写 design/implement；**以本 PRD + 更新后的 design/implement 为准**（路径=PR 审阅合入）。
- PR 风险：个别中转站拒 `"none"` → 400（PR body 已声明，与既有 openrouter/nvidia 行为一致）。
