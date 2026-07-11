# Group and resolve open issues

## Goal

Triage and resolve the current open GitHub issue backlog in a controlled order.
Handle all non-advanced work as one coordinated batch, while deferring advanced
memory-table capabilities to the final phase. Keep the upstream merge issue
separate from this batch because it has a very large diff surface.

Source issue set: open issues in `Arsucar/rikkahub` as of 2026-07-09:
#68, #70-#101.

## Requirements

- Create a grouped execution plan for the open issues, preserving issue
  dependencies and minimizing rework.
- Process non-advanced issues in the main batch:
  - Chat/message UX: #70, #80, #101.
  - Preset and extension management: #72, #73, #75, #78.
  - Subagent UX/observability: #74, #76, #79.
  - Provider/model configuration: #71, #77, #82.
  - Memory-table foundation, safety, observability, and row semantics:
    #81, #83, #84, #85, #86, #87, #88, #89, #90, #91, #92, #95.
- Defer advanced memory-table features until the end:
  #93, #94, #96, #97, #98, #99, #100.
- Treat upstream merge #68 as a separate follow-up task, not part of this
  implementation batch.
- Preserve existing user or generated working-tree changes. Current known
  uncommitted files before this task:
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ai/completion/PresetCompletionProvider.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PromptPage.kt`
- Use Trellis child tasks for independently verifiable packages instead of
  implementing every issue inside the parent task.
- Use sub-agents for implementation and checks where the work is not trivial.
- Do not run device/instrumented tests unless explicitly needed. Run Gradle
  commands with `--no-daemon`.

## Acceptance Criteria

- [x] Issues are grouped into child tasks with clear scope and dependency order.
- [x] Non-advanced issues are either implemented, explicitly marked blocked, or
      moved to a justified follow-up.
- [x] Advanced memory-table issues are intentionally queued last and not mixed
      into foundation fixes.
- [x] #68 remains separate from the batch and is not accidentally included in
      the implementation diff.
- [x] Every implemented child task has focused validation appropriate to its
      risk, with failures reported.
- [x] GitHub issue closure/commenting is only done after code is implemented
      and verified.
- [x] Final summary maps completed work back to issue numbers.

## Final Scope Decisions

- #89 full conversation-level memory-table UI was not implemented in this batch.
  #84 was handled with the short-term safety fallback: existing conversation
  documents can be listed, and new conversation-scope writes are rejected until
  the UI/lifecycle task is implemented.
- #93/#94/#96/#97/#98/#99/#100 were converted into final-phase planning with a
  recommended dependency order rather than implemented in the broad batch.
- #68 upstream merge remains a separate follow-up.

## Notes

- User direction: "记忆表高级功能放置最后，其他一次处理".
