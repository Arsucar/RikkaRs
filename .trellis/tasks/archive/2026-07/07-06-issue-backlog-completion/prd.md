# Issue backlog completion and closure

## Goal

Finish the open issue backlog that was previously misclassified as complete. Completion means the product behavior meets the GitHub issue expectations, required UI is present for user-facing features, verification evidence is recorded, and the corresponding GitHub issue is commented/closed only after validation.

## Confirmed Facts

- Prior archived Trellis tasks under `archive/2026-07/07-06-*` were marked `completed` even though many PRD acceptance boxes remained unchecked and `task.json.commit` was `null`.
- GitHub issues `#31`, `#33`-`#42` are still open except `#32`, which is closed and out of this task.
- Implemented but not fully closed:
  - `#31`: per-conversation chat model override has code in `fa25f6c6`, but manual two-conversation validation and issue closure are missing.
  - `#40`: fullscreen text file view/edit has code in `a57ca25d`, but manual UI validation and issue closure are missing.
  - `#34/#36/#37/#38`: skill access scope has code in `ee34efe7`, but PRD checkboxes, issue comments/closure, and task evidence are incomplete.
  - `#42` and part of `#33`: subagent finish_work/tool policy has code in `44e28c1f`, but issue closure and complete evidence are missing.
  - `#35` and Git part of `#33`: workspace git pack reliability has code in `201969f0` and `de4c6ff0`, but closure evidence is incomplete.
- Not implemented:
  - `#39`: per-memory global/local scope is only planned.
  - `#41`: generic memory table feature is only planned and must remain zero-intrusion when disabled.

## Requirements

- Re-audit every open issue `#31`, `#33`-`#42` against current code and issue text before claiming completion.
- Implement missing product behavior for `#39` and the required phases of `#41`.
- Add or refine UI for user-facing features:
  - `#31`: make the conversation model override discoverable and provide a clear way to return to assistant default.
  - `#39`: memory list must show per-memory scope and allow changing local/global scope.
  - `#40`: validate existing fullscreen file UI and fix usability/localization gaps found during validation.
  - `#41`: add visible disabled-by-default memory table controls and management UI for implemented phases.
- Preserve compatibility:
  - Existing conversations and memories must migrate without data loss.
  - Existing assistant/global memory behavior must have a documented migration path.
  - When memory tables are disabled, there must be no prompt injection, no table tool registration, no background sync job, no token cost, and no table document read.
- Use sub-agents first for non-trivial code search, implementation, and review when the platform allows it. If the agent thread limit blocks dispatch, continue locally and record that limitation.
- Verification must use Gradle commands with `--no-daemon`.
- Final source-code changes must be installed to the connected device. If install fails, reconnect `100.99.129.110:5555` and retry once.
- Do not close a GitHub issue until its behavior is implemented, verified, and a comment links the evidence.

## Acceptance Criteria

- [ ] Issue closure table is recorded with one row per open issue `#31`, `#33`-`#42`, including code commit(s), validation evidence, and closure/comment status.
- [ ] `#31` passes two-conversation manual validation: conversation A override does not alter assistant default or conversation B; new conversations start with no override.
- [ ] `#33` is fully split and closed: git pack behavior, delegation-only tool availability, and subagent cancellation semantics each have evidence.
- [ ] `#34/#37` can read enabled `/skills/<skill>/SKILL.md` through the expected tool path or the prompt/tool behavior no longer contradicts itself.
- [ ] `#36` global versus assistant-private skill visibility is enforced and visible/documented enough for users.
- [ ] `#38` `use_skill(name, path)` supports approved in-skill subfiles and safe symlink targets while rejecting traversal/unauthorized paths.
- [ ] `#35` `/workspace` git clone/pull behavior is verified or unsupported storage modes are blocked/warned in UI.
- [ ] `#39` per-memory scope is implemented with migration, repository read/write semantics, memory tool support, and UI.
- [ ] `#40` fullscreen text view/edit is manually validated for `.md`, `.json`, `.kt`, `.log`, and a binary/unknown file.
- [ ] `#41` memory table functionality is implemented to the agreed staged scope with disabled zero-intrusion proof.
- [ ] `#42` main agent excludes `finish_work`; subagents still receive it; tests/manual evidence cover both.
- [ ] `.\gradlew --no-daemon lint --console=plain` passes.
- [ ] `.\gradlew --no-daemon test --console=plain` passes.
- [ ] `.\gradlew --no-daemon :app:installDebug --console=plain` installs to device, with reconnect retry if needed.
- [ ] GitHub issues are commented with evidence and closed only after the matching criteria above pass.

## Out of Scope

- Closing issues based only on prior Trellis archive status.
- Treating planning-only artifacts as implementation.
- Running `connectedDebugAndroidTest` unless explicitly requested.
