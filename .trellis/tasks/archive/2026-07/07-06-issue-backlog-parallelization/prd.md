# Issue backlog parallelization plan

## Goal

Classify open issues #31, #33-#42 into independently verifiable Trellis child tasks so multiple worktrees can proceed in parallel without overlapping risky files unnecessarily.

## Confirmed Facts

- #34 and #37 describe the same workspace `/skills/<skill>/SKILL.md` read failure and should be handled together.
- #35 is the detailed Git pack write subproblem from #33 and should be handled as the canonical Git task.
- #33 also contains separate subagent cancellation and delegation-tool issues; those belong with subagent tool policy rather than Git storage.
- #39 and #41 both touch memory data/model/tool/UI surfaces. #39 should be treated as the near-term existing-memory scope change; #41 is a larger memory-table roadmap and should not be implemented concurrently against the same files unless migrations are coordinated.
- #40 and #31 are mostly independent UI/data-flow features and are suitable for parallel worktrees.

## Requirements

- Maintain a parent task that owns the cross-issue task map and dependency notes.
- Maintain child tasks for independently shippable work:
  - `07-06-skills-access-scope` for #34, #36, #37, #38.
  - `07-06-subagent-tool-policy` for #42 and #33 subagent/delegation parts.
  - `07-06-workspace-git-pack` for #35 and #33 Git parts.
  - `07-06-file-fullscreen-editor` for #40.
  - `07-06-conversation-model-override` for #31.
  - `07-06-memory-scope-table-roadmap` for #39 and #41 sequencing.
- Each child task must name suggested branch/worktree labels, affected areas, acceptance criteria, and dependencies.
- Do not start implementation from the parent task. Start a child task when ready to work on that deliverable.

## Acceptance Criteria

- [ ] Parent task lists all relevant open issues and their child-task assignment.
- [ ] Every child task has a PRD with testable acceptance criteria.
- [ ] Complex child tasks have `design.md` and `implement.md` before implementation starts.
- [ ] Dependency and conflict risks are explicit, especially Room migrations and shared ChatService/GenerationHandler edits.
- [ ] No code implementation is started as part of this classification task.

## Out of Scope

- Fixing any issue directly.
- Closing GitHub issues.
- Creating actual Git worktrees or branches.
