# Resolve open issue backlog

## Goal

Resolve the currently open GitHub issue backlog for the Rikka-arsucar fork:
#43, #44, #45, #46, #47, and #48.

The work should make each issue's expected behavior true in the current codebase,
verify it with appropriate local checks, and close the GitHub issues with concise
evidence when resolved.

## Confirmed Facts

- The active branch is `release/rikka-arsucar`.
- Open issues were read with `gh issue view` on 2026-07-06.
- The repository already has v2.3.18 release commits, but the issues remain open
  and must be verified against current source before closing.

## Requirements

- #43: remove the chat input model clear button while preserving model switching;
  improve the Skills directory/list visual hierarchy in `SkillsPage`/`SkillCard`.
- #44: expand workspace text preview/edit eligibility for common dotfiles,
  extensionless text files, and other common text formats without weakening the
  existing size boundary; render Markdown in read-only workspace view while
  keeping edit mode as the text editor.
- #45: make long `workspace_shell` output truncation and `/tool_outputs/...`
  continuation steps understandable in subagent transcript/UI summaries.
- #46: restore the main-chat subagent `parallelToolExecution` setting so enabled
  assistants can run multiple same-response `spawn_subagent` calls concurrently
  and the tool description advertises that behavior.
- #47: show the `workspace_shell` command in inline tool summaries for both main
  chat and subagent transcript compact rows, including a loading/unknown state
  rather than a misleading output-only shell card.
- #48: ensure OpenAI-compatible Response API tool serialization never emits
  `parameters: null`; null tool schemas must serialize as an empty object schema.
- Preserve unrelated existing behavior and avoid broad refactors.
- Do not default to `connectedDebugAndroidTest`.

## Acceptance Criteria

- [ ] #43 chat input `ModelSelector` no longer enables `allowClear`, while other
  screens that intentionally pass `allowClear = true` remain unchanged.
- [ ] #43 Skills list/card layout is visibly more scannable and keeps import,
  copy, edit, delete, and detail actions available.
- [ ] #44 workspace file menu exposes view/edit for common text dotfiles and
  reasonable extensionless text files; binary-looking files remain excluded by
  name/extension heuristics and repository read limits still apply.
- [ ] #44 read-only `.md`/Markdown workspace view renders formatted Markdown;
  edit mode still opens the editable text area.
- [ ] #45/#47 shell tool UI summaries show command context before output and
  distinguish `/tool_outputs/...` continuation reads.
- [ ] #45/#47 subagent transcript compact rows retain a readable shell command
  snippet instead of showing output only or an opaque truncated JSON blob.
- [ ] #46 main chat generation and `spawn_subagent` tool creation respect
  `assistant.parallelToolExecution`.
- [ ] #48 Response API and any shared OpenAI tool schema serialization path maps
  null parameters to an empty object schema.
- [ ] Relevant JVM/unit tests or compile checks pass; if device install is
  possible, `:app:installDebug` is attempted per repository policy.
- [ ] GitHub issues #43-#48 are closed only after code and verification evidence
  support each acceptance item.

## Notes

- This is a complex, multi-slice task; use `design.md` and `implement.md`.
