# PRD: Fix all open bugs from issue tracker

## Goal

Batch-fix all 7 open bugs reported in GitHub issues, grouped into 5 independently verifiable child tasks.

## Scope

| Child task | Issues | Summary |
|------------|--------|---------|
| `07-01-bug-compress-hidden-stats` | #24, #25 | Compress hidden-count leak into model text + message count stats include hidden nodes |
| `07-01-bug-subagent-control-ui` | #21, #18, #22 | Subagent tool_loop_steps/tool_calls semantics, cancellation chain, transcript tool UI |
| `07-01-bug-fts-ambiguous-col` | #26 | FTS search JOIN ORDER BY `update_at` ambiguous column crash |
| `07-01-bug-duplicate-default-assistant` | #23 | Two default assistants with same display name for new users |
| `07-01-bug-shell-policy-redirect` | #16 | Workspace shell policy false-positive on `2>/dev/null` |

## Acceptance Criteria

- All 5 child tasks pass their individual acceptance criteria
- No regression in existing features (build + install passes)
- Each child task can be verified independently

## Out of Scope

- Feature requests (#19, #20) — not bugs
- Already-closed issues

## Dependencies

- Child tasks are independent; no cross-child blocking
- `bug-subagent-control-ui` is the largest; if time-constrained, the 3 simple bugs (#26, #23, #16) can ship first