# Batch PR Review — Issues #265–#285

## 目标

审查 9 个 open PR，覆盖 issues #265, #267–#275, #284–#285，确保修复正确、无回归、无遗漏。

## PR 清单

| Issue | PR | 标题 | 改动规模 |
|-------|-----|------|----------|
| #285/#284 | #286 | 子代理 context budget 复用修复 | +31/-4, 6 files |
| #274 | #283 | 代码健壮性 (!! / runCatching / InputStream / printStackTrace) | +77/-28, 10 files |
| #272 | #282 | Compose 性能 (collectAsStateWithLifecycle + items key) | +31/-31, 20 files |
| #269 | #281 | 流式 UI 性能 (reasoning timer + Markdown debounce) | +7/-1, 3 files |
| #273 | #280 | SkillManager lock-free scan + 去重 | +21/-13, 2 files |
| #271 | #279 | runBlocking 替换 | +104/-52, 2 files |
| #275 | #278 | MCP 死代码清理 (-570) | 2 files |
| #270 | #277 | 主线程 IO 修复 | +40/-25, 3 files |
| #265 | #266 | 统一 SkillCard 组件 | +639/-449, 8 files |

## 验收标准

1. 每个 PR 的 diff 已逐行审查
2. 编译错误、数据丢失、并发覆盖、安全问题、明确行为回归 → 必须修复
3. 有清晰复现路径且范围可控的 MEDIUM 问题 → 应当修复
4. 纯风格/命名/可选抽象/低收益清理 → 只记录，不触发修复循环
5. 审查结果以评论形式发布到对应 PR

## 审查策略

- 按改动规模从小到大分批并行派发子代理
- 每个子代理负责 1–2 个 PR 的 diff 审查
- 主代理整合结果并决定是否需要修复
- 仅最后一个检查子代理允许运行 Gradle 编译
