# Design

## Task Structure

- Parent task owns scope, integration validation, commits and issue handoff.
- Child #142 owns memory revision persistence/UI.
- Child #143 owns lifecycle-independent backup execution/UI state.
- The children may be implemented in parallel because they touch separate feature areas; shared DI/AppScope edits must be integrated carefully.

## Compatibility

- Preserve current branch and existing uncommitted `.agents/skills/trellis-finish-work/SKILL.md` change; do not mix it into issue commits.
- Do not alter existing backup archive format or previously shipped Room migrations.
- Prefer additive state/services and existing navigation/components.

## Integration Gate

- Run targeted unit tests for both children, Kotlin compilation, and app installation when a device is available.
- Review staged diffs per child and create scoped conventional commits before archiving the parent.
