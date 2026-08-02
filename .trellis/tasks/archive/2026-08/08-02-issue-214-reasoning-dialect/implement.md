# issue-214 implement（审阅合入 PR #221）

## 有序 Checklist

1. [x] `git fetch origin fix/reasoning-dialect-214`；读 PR #221 全文与 diff。
2. [x] 对照 #214 AC：OFF→none；proxy+Auto+deepseek id→xhigh；官方 deepseek / 显式 DeepSeekMax / nvidia v4 不回归。
3. [x] 本地检出或 worktree 跑测：
   ```powershell
   git checkout fix/reasoning-dialect-214   # 或 merge 到临时分支
   .\gradlew --no-daemon :ai:testDebugUnitTest --tests "me.rerere.ai.core.ReasoningDialectTest" --tests "me.rerere.ai.provider.providers.openai.ChatCompletionsAPIReasoningDialectTest"
   ```
4. [x] 通过后合入：`gh pr merge 221 --merge`（或 squash，与仓库习惯一致；**勿**对上游开 PR）。→ merge commit `9f3df56d`
5. [x] 合入后在 `release/rikka-arsucar` 再跑一遍同上测试确认 HEAD。→ BUILD SUCCESSFUL
6. [x] 关 #214：中英交付评论（解决点 / 验证 / 定位 / 已知边界；写明 PR #221、commit、测试结果）。Closes 已自动关 issue；中英评论已发。
7. [x] 若测试红：在 PR 分支修，**不要**在本任务目录另写平行实现。→ 测试绿，无需修

## 验证命令

```powershell
.\gradlew --no-daemon :ai:testDebugUnitTest --tests "me.rerere.ai.core.ReasoningDialectTest" --tests "me.rerere.ai.provider.providers.openai.ChatCompletionsAPIReasoningDialectTest"
```

不默认 installDebug。

## Review 门

- [ ] Diff 与 issue 一致且无无关重构
- [ ] 单测绿
- [ ] #214 关闭评论合规

## 回滚点

- `git revert` 合入提交；或 `gh pr close` 若尚未 merge。

## 工作量

**S**：审阅 + 单测 + merge（≤数小时）。**实现代码已在 PR 内。**

## 风险

- PR 作者未本地编译：合入责任方必须跑测。
- 自动 Closes 依赖 merge 到默认/关联分支；确认 base 为 `release/rikka-arsucar`。
