# Design

## Task Tree

| Child | Issues | Suggested branch | Parallel safety |
|---|---:|---|---|
| `07-06-skills-access-scope` | #34 #36 #37 #38 | `fix/skills-access-scope` | Avoid parallel edits with workspace mount or skill manager changes. |
| `07-06-subagent-tool-policy` | #42 plus #33 subagent/delegation | `fix/subagent-tool-policy` | Conflicts with code touching `ChatService`, `GenerationHandler`, and subagent permission builders. |
| `07-06-workspace-git-pack` | #35 plus #33 Git | `fix/workspace-git-pack` | Mostly workspace/proot/storage; can run beside subagent work if shared prompt text is coordinated. |
| `07-06-file-fullscreen-editor` | #40 | `feat/file-fullscreen-editor` | UI/workspace file actions; low conflict with other groups. |
| `07-06-conversation-model-override` | #31 | `feat/conversation-model-override` | Watch Room/entity migrations if memory work also changes `Conversation`. |
| `07-06-memory-scope-table-roadmap` | #39 #41 | `feat/memory-scope-table-roadmap` | Do #39 first or split #41 into later children; high migration and repository conflict risk. |

## Coordination Rules

- Treat this parent as planning-only. It does not own code edits.
- Start only one child per worktree.
- If two children require the same Room migration number, rebase one before implementation or allocate migration sequencing explicitly.
- If two children edit `ChatService` or `GenerationHandler`, merge the smaller bug fix first before larger feature work.
- Keep local Trellis files out of upstream PRs unless the user explicitly asks to include them.

## Issue Dedupe

- #34 and #37: duplicate root cause. One implementation can close both.
- #35 and #33 Git portion: #35 is the canonical detailed task.
- #33 non-Git portions: belong to subagent tool policy.
