# Implementation Plan

1. Activate and implement child #142; verify repository/DAO/UI behavior with targeted tests.
2. Activate and implement child #143; verify coordinator lifecycle/cancellation behavior with targeted tests.
3. Run cross-child compile and device installation according to repository rules.
4. Run Trellis quality review, update relevant specs only if a reusable convention was learned.
5. Commit each issue separately, archive both children and the parent, record the journal, then close/comment on issues only after evidence is complete.

## Rollback Points

- #142: revert revision UI/DI/DAO changes as one scoped commit.
- #143: revert task coordinator and tab integration as one scoped commit.
- Keep the independent finish-work skill edit unstaged throughout issue commits.
