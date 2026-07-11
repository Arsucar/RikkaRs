# Deferred Upstream Merge

## Goal

Keep upstream merge #68 separate from local issue fixes to avoid conflict and
verification churn.

## Requirements

- Defer #68 until the local issue batch is complete or the user explicitly
  changes priority.
- When executed, merge upstream v2.4.1 while preserving the fork's
  `release-apk.yml` workflow and accepting compatible `daily-build.yml`
  behavior.

## Acceptance Criteria

- [x] No upstream merge changes are included in the current local issue-fix
      batch.
- [x] Future #68 implementation has its own design/validation pass.

## Decision

#68 remains intentionally separate. This batch did not merge upstream v2.4.1 or
touch release workflow strategy beyond the local issue-fix scope.
