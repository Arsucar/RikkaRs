# issue-214 design（PR #221 既定方案）

## 权威来源

实现以 **PR #221** / 分支 `fix/reasoning-dialect-214` 为准，不在本任务另设计分叉方案。

| 项 | 值 |
|----|-----|
| PR | https://github.com/Arsucar/RikkaRs/pull/221 |
| Commit | `c0bbea9b` — `fix(#214): OFF sends none not low; Auto deepseek recognition limited to official hosts` |
| Files | `Reasoning.kt`, `ChatCompletionsAPI.kt`, `ReasoningDialectTest.kt`, `ChatCompletionsAPIReasoningDialectTest.kt` |

## 已落地契约（diff 摘要）

### mapReasoningEffort
- 删除参数 `noneAsLow`。
- OpenAIExtended：`level.effort`（OFF → `"none"`）。
- OpenAIClassic / DeepSeekMax：OFF 固定 `"none"`。

### resolveDialect
- 删除 model-id 弱提示；未知 host → OpenAIExtended。
- 官方 host 规则保留；`explicit != Auto` 仍优先。

### ChatCompletionsAPI
- nvidia 非 v4 / 通用 else：不再传 `noneAsLow = true`。

## 本任务设计工作

无新设计；仅：

1. Code review PR 与 #214 期望、与本仓库 #207 风险段声明是否一致。
2. 验证策略：优先本地 Gradle ai unit test；PR 作者环境无 JDK 时由合入者补跑。
3. 合入后确认 mainline 无残留 `noneAsLow` / `looksLikeDeepSeekReasoningModel`。

## 若 review 发现缺陷

- 在 `fix/reasoning-dialect-214` 上追加 commit，仍经 PR #221 合入。
- 不新开平行实现 PR，除非 #221 废弃。

## 兼容 / 回滚

- 与 PR body 一致：`git revert` 合入 commit。
- 行为变化：中转站 OFF low→none；中转站 Auto+deepseek id max→xhigh。

## 风险

- `"none"` 被部分 compat 网关拒绝。
- 依赖显式 DeepSeekMax 的中转站用户需在模型设置改方言（预期）。
