# Technical Design

## Task Structure

- Parent task coordinates scope, ordering, shared verification, commits, pushing, and issue closure.
- Child #131 is a verification/closure task for an existing implementation.
- Child #132 owns conversation tag infrastructure from Room through drawer/settings UI.
- Child #133 depends on #132 and consumes only its public tag APIs.

## Delivery Order

1. Verify and close #131 without mixing unrelated feature work.
2. Implement, test, install, commit, and close #132.
3. Rebase planning assumptions on the delivered #132 API, then implement and close #133.
4. Run final cross-child regression and confirm all GitHub comments and issue states.

## Boundaries

- Each child gets its own implementation and verification agent; only the final check agent may run Gradle for that child.
- Commits stage only files belonging to the active child plus its Trellis artifacts.
- Existing user changes in `.trellis/spec/`, workspace journals, `AGENTS.md`, `CLAUDE.md`, and `.npmrc` remain untouched unless explicitly required.
- Issue comments and closure happen only after the corresponding commit is pushed and verification evidence is known.

## Rollback

- Child commits remain independently revertible.
- Database migrations are additive; rollback is by reverting the feature commit before release, not by destructive downgrade migration.
- If #132 cannot meet its atomic API contract, #133 remains blocked and is not partially shipped.
