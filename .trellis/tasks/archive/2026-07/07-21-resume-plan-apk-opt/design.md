# Design: Resume Plan.md optimization

## Problem framing

首轮优化在完成少量高置信候选后错误跳到收尾，导致：

1. `Plan.md` 大量审查项未执行却被叙事为“完成”
2. `OPTIMIZATION_REPORT.md` 将局部结果写成最终结案
3. 部分已做工作（R1/R4/T2）未回写清单，造成“未做”假象

本设计保证**清单驱动**的恢复执行与**证据驱动**的跳过，而不是候选耗尽驱动。

## Boundaries

| In | Out |
|----|-----|
| 按 `Plan.md` 顺序审查/实施 P0 剩余 → P4 → 收尾 | 整包回滚已接受的 5 个优化相关 commit |
| 更新 PLAN/NOTES/REPORT/Plan 勾选 | 新增静态分析工具（detekt/ktlint） |
| 可比冷构建与 APK 测量 | 默认 instrumented / 改 CI |
| 聚焦测试 + 最终 JVM test + 需要时 install | 为未验证瓶颈写“性能数字” |

## Source of truth

1. **完成状态**：`Plan.md` 勾选
2. **候选与证据台账**：`OPTIMIZATION_PLAN.md`
3. **跳过/失败细节**：`OPTIMIZATION_NOTES.md`
4. **对外结论文档**：`OPTIMIZATION_REPORT.md`（本轮修订）
5. **协议/目标**：`Prompt.md`（若与 Plan 冲突，以 Prompt 成功标准 + Plan 清单覆盖义务共同约束）

## Execution model

```
For each unchecked Plan item in order:
  research evidence (dependency graph / code / build profile / refs)
  if implementable with proportionate risk:
    change → focused verify → assemble/tests as needed → document → check Plan box
  else:
    write skip evidence (steps taken + artifacts + residual risk) → document → check Plan box as reviewed-skip
Never advance to "阶段 6 收尾" until P0–P4 items are all resolved in Plan.md
```

### Mapping: Plan items → prior work

| Plan section | Prior commits / notes | Resume action |
|--------------|----------------------|---------------|
| 阶段 0 all | baseline docs | Keep checked; re-verify paths only if needed |
| P0 Gradle review + cache/parallel | B1 accepted | Keep; parallel already on |
| P0 api/implementation | B3 “skipped no proof” **insufficient** | **Full audit** with dependency insight + consumer search; implement or evidence-rich skip |
| P0 kapt→KSP | not done | Inventory kapt/ksp usage; cost/benefit table; implement only if low-risk win |
| P0 remove unused plugins/deps/tasks | partial narrative only | Explicit unused proof or skip with search log |
| P0 per-candidate profile | only B1 profiled | Each new B* candidate gets same 3-sample protocol |
| P1 Compose/Room/OkHttp/Coil/main-thread | R1/R4 done; others skipped thin | Re-review each Plan bullet; strengthen skip evidence or implement |
| P2 quality/dead code/coupling | Q1/Q2 thin skip | Reference search + module edge review; no mass delete without proof |
| P3 resources/R8/baseline | A1 analysis only | Complete review artifacts; change only with size/compat proof |
| P4 tests | T1 partial via R1/R4; T2 done | Add tests only for **new** behavior this round; run affected + final test |
| 阶段 6 | report premature | Full remeasure + revise report + docs commit |

## Technical approach by priority

### P0 build

- Use Gradle dependency insight / `dependencies` / module graph for `api` vs `implementation` leaks.
- Prefer fixing **confirmed** leaks that shrink compile classpath without breaking public module contracts.
- KSP migration: list kapt processors; if none or already KSP, document N/A; if present, estimate migration cost vs measured gain before code change.
- Profile gate: same cold command as baseline; accept only median improvement beyond noise **or** document no-gain restore.

### P1 runtime

- Prefer code-evidence bottlenecks already in OPTIMIZATION_PLAN (R2/R3/R5/R6) only if harness or low-risk pure refactor exists.
- Without Macrobenchmark: allow **mechanism-safe** low-risk fixes with regression tests; **forbid** claiming frame-time deltas.
- Compose/Room/OkHttp/Coil: structured code review notes in NOTES even when no code change.

### P2 quality

- detekt/ktlint: confirm N/A once more.
- Dead code: require zero external references + compile after removal.
- Coupling: only fix edges that expand compile/runtime cost with evidence.

### P3 size/startup

- Start from existing A1 APK breakdown if still valid; refresh if APK changed.
- Resource shrink / R8 / baseline profile: analysis mandatory; code change optional with same-variant proof.
- APK size gate ≤5% vs original baseline bytes (82,140,247) unless Prompt defines a new baseline; document which baseline is used.

### P4 / finish

- Tests for new changes only + final `.\gradlew --no-daemon test` (merge with assemble when possible).
- Remeasure cold×3, APK, install if app code changed.
- Report must include “resume round” section and Plan coverage matrix.

## Sub-agent dispatch

| Work | Agent | Compile allowed |
|------|-------|-----------------|
| Code search / impact | trellis-research or general explore | No |
| Implement one candidate | trellis-implement | No (prefer) |
| Spec/quality after batch | trellis-check | **Yes, last only** |
| Main session | orchestration, Plan/docs merge, install gate | Yes only for final install/remeasure |

Every dispatch prompt starts with:  
`Active task: .trellis/tasks/07-21-resume-plan-apk-opt`

## Compatibility / rollback

- One candidate at a time; dirty tree only for current candidate.
- Failed uncommitted work: restore files.
- Failed committed work: exact revert commit, never rewrite history.
- Three same-cause failures → NOTES + move on.

## Risks

| Risk | Mitigation |
|------|------------|
| Again skipping Plan items | Gate: no stage-6 until Plan checkboxes done |
| Build time noise | Fixed protocol; 3 samples; median |
| Over-aggressive dependency cleanup | Consumer search + compile + tests |
| Report honesty regression | Coverage matrix table required in REPORT |
| Memory exhaustion from multi-Gradle | Single compile owner |

## Rollout shape

1. Planning review → `task.py start`
2. Execute Plan residual items
3. Remeasure + revise docs
4. User-facing summary; commit only when user asks (workflow 3.4 still reminds)
