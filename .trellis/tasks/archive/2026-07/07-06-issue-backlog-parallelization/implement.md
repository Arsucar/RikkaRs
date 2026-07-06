# Implementation Plan

This parent task is planning-only.

## Checklist

- [x] Verify task tree links include all six child tasks.
- [x] Review each child PRD before starting implementation.
- [x] Pick the next child based on priority and conflict risk.
- [x] Process children from the parent task map.
- [x] Run Trellis `task.py start` only for selected children, not the parent.

## Recommended Order

1. `07-06-subagent-tool-policy` because #42 is a narrow correctness bug and may reduce noisy tool-loop behavior.
2. `07-06-skills-access-scope` because it groups duplicate skill-read failures and the access-boundary feature.
3. `07-06-workspace-git-pack` because it may require device/rootfs reproduction.
4. `07-06-conversation-model-override` and `07-06-file-fullscreen-editor` in parallel.
5. `07-06-memory-scope-table-roadmap`, starting with #39 before any #41 implementation.

## Validation

- [x] `python ./.trellis/scripts/get_context.py`
- [x] Parent `children` list contains all six planned children.
- [x] All six planned children are complete/archived or have a roadmap artifact.
