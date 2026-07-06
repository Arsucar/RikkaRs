# Research: issue 33 and 35 closure readiness

- Query: Audit remaining GitHub issues #33 and #35 for closure readiness from code, tests, task artifacts, terminal logs, and issue text.
- Scope: mixed
- Date: 2026-07-06

## Findings

### Issue Status / Scope

- `gh issue view 33` reports #33 is still OPEN: "工作区 /workspace 下 git pack 写入失败、子代理误报取消与 delegation 模式 workspace_shell 不可用".
- `gh issue view 35` reports #35 is still OPEN: "git pull 在工作区内失败，.git/objects/pack 无法写入 pack 文件".
- `.trellis/tasks/07-06-issue-backlog-completion/issue-closure-matrix.md` records #33 and #35 as implemented/tested but not manually validated in the app terminal / AI shell.
- `.trellis/tasks/07-06-issue-backlog-completion/implement.md` keeps #33/#35 closure unchecked pending remaining manual checks.

### Files Found

- `workspace/src/main/java/me/rerere/workspace/WorkspaceProotCommandBuilder.kt` - shared proot arg builder; default disables `--link2symlink`.
- `workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt` - AI `workspace_shell` proot command uses shared builder.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceTerminalSession.kt` - in-app terminal proot args use the same shared builder.
- `app/src/main/java/me/rerere/rikkahub/data/model/WorkspaceFilesStorage.kt` - models PRIVATE vs EXTERNAL Git pack support.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspacePage.kt` - surfaces EXTERNAL Git pack warning in current storage card and picker dialog.
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` - delegation-only root tool list filters workspace tools to read-only and passes explicit user-cancel reason only from `stopGeneration`.
- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` - unavailable tool calls now return structured "not available in this assistant mode" output with available tools.
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt` - default subagent cancel reason is neutral; user-cancel wording requires explicit reason.
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` - delegation-only prompt says no write or shell execution tools and to use only listed tools.

### Acceptance Criteria Confirmed Statically

- #33 Git pack slice: normal proot workspace shells no longer add `--link2symlink`.
  - `WorkspaceProotCommandBuilder.kt:5-19` defaults `emulateHardLinksWithSymlinks = false` and only adds `--link2symlink` when explicitly enabled.
  - `ProotShellRunner.kt:61-73` delegates AI shell proot args to `WorkspaceProotCommandBuilder`.
  - `WorkspaceTerminalSession.kt:65-80` delegates in-app terminal proot args to `WorkspaceProotCommandBuilder`.
- #35 storage-mode behavior: PRIVATE is declared Git-pack-compatible; EXTERNAL is not claimed compatible and warns users.
  - `WorkspaceFilesStorage.kt:6-12` makes `supportsWorkspaceGitPackWrites()` true only for PRIVATE.
  - `WorkspacePage.kt:308-314` shows the Git warning when current storage does not support pack writes.
  - `WorkspacePage.kt:351-357` shows the same warning when EXTERNAL is selected in the picker.
  - `app/src/main/res/values/strings.xml:1395` warns that Git clone/fetch/pull may fail on app external storage and recommends app-private storage.
- #33 delegation-only tool policy: root assistant in delegation-only mode does not expose `workspace_shell`; if a model calls a filtered tool, it gets a clear structured unavailable-tool result.
  - `ChatService.kt:706-717` limits local tools in delegation-only mode.
  - `ChatService.kt:745-750` requests workspace tools with `readOnly = delegateOnly`.
  - `ChatService.kt:868-895` returns only `workspace_read_file` when `readOnly` is true.
  - `SubagentTools.kt:63-71` tells delegation-only agents they have no write or shell execution tools.
  - `GenerationHandler.kt:520-522` returns "Tool '<name>' is not available in this assistant mode" plus available tool names.
- #33 subagent cancellation semantics: default/internal stop is no longer reported as user cancellation.
  - `SubagentHost.kt:30-31` defines separate user and neutral stop reasons.
  - `SubagentHost.kt:74-85` defaults `requestCancel()` to the neutral stop reason.
  - `SubagentHost.kt:601-606` maps only `SUBAGENT_USER_CANCEL_REASON` to "Task cancelled by user".
  - `ChatService.kt:2097-2105` passes `SUBAGENT_USER_CANCEL_REASON` only from the explicit user stop path.

### Tests / Terminal Evidence

- `workspace/src/test/java/me/rerere/workspace/ProotShellRunnerTest.kt:10-24` verifies AI shell proot args omit `--link2symlink` by default and retain opt-in compatibility.
- `app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceTerminalSessionTest.kt:10-33` verifies terminal args omit `--link2symlink` and bind `/workspace` plus `/skills`.
- `app/src/test/java/me/rerere/rikkahub/data/model/WorkspaceFilesStorageTest.kt:9-15` verifies PRIVATE supports Git pack writes and EXTERNAL does not claim support.
- `app/src/test/java/me/rerere/rikkahub/data/repository/WorkspaceStorageMigratorTest.kt:10-39` verifies migration copy verification still behaves as data-copy validation.
- `app/src/test/java/me/rerere/rikkahub/data/ai/subagent/SubagentRuntimeTest.kt:243-263` verifies neutral default cancellation and explicit user-cancel reason separation.
- `app/src/test/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionTest.kt:72-99` verifies subagent read-only/full workspace tool access behavior.
- Generated XML confirms zero failures/errors:
  - `workspace/build/test-results/testDebugUnitTest/TEST-me.rerere.workspace.ProotShellRunnerTest.xml`: 2 tests, 0 failures, 0 errors.
  - `app/build/test-results/testDebugUnitTest/TEST-me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionTest.xml`: 2 tests, 0 failures, 0 errors.
  - `app/build/test-results/testDebugUnitTest/TEST-me.rerere.rikkahub.data.model.WorkspaceFilesStorageTest.xml`: 2 tests, 0 failures, 0 errors.
  - `app/build/test-results/testDebugUnitTest/TEST-me.rerere.rikkahub.data.repository.WorkspaceStorageMigratorTest.xml`: 2 tests, 0 failures, 0 errors.
  - `app/build/test-results/testDebugUnitTest/TEST-me.rerere.rikkahub.data.ai.subagent.SubagentRuntimeTest.xml`: 18 tests, 0 failures, 0 errors.
  - `app/build/test-results/testDebugUnitTest/TEST-me.rerere.rikkahub.data.ai.subagent.SubagentPermissionTest.xml`: 21 tests, 0 failures, 0 errors.
- Stored terminal logs:
  - `.trellis/workspace/your-name/final-focused-workspace-tests.log` shows `:workspace:testDebugUnitTest --tests me.rerere.workspace.ProotShellRunnerTest` BUILD SUCCESSFUL.
  - `.trellis/workspace/your-name/final-test.log` shows full `test` BUILD SUCCESSFUL.
  - `.trellis/workspace/your-name/final-lint.log` shows `lint` BUILD SUCCESSFUL.
  - `.trellis/workspace/your-name/final-install-debug.log` shows `:app:installDebug` installed `app-arm64-v8a-debug.apk` on `PJF110 - 16`.

### Minimal Manual / Device Smoke Before Close

- Open the installed debug app on the connected/unlocked device and verify Workspace storage UI:
  - Workspace page -> storage setting.
  - When current or selected storage is EXTERNAL, confirm the Git pack warning is visible.
  - PRIVATE should show no Git pack warning.
- In the in-app Workspace Terminal for a shell-ready PRIVATE workspace, run:
  - `cd /workspace && rm -rf git-smoke && git clone --depth 1 https://github.com/octocat/Hello-World.git git-smoke`
  - `cd /workspace/git-smoke && git fetch origin && git pull --ff-only`
  - `ls -la .git/objects/pack | head`
  - Expected: clone/fetch/pull complete without pack rename errors and without `.l2s.tmp_*` residue.
- In an assistant bound to the same shell-ready workspace with `workspace_shell` available, ask it to run the same git smoke through `workspace_shell`.
  - Expected: same success as terminal, proving AI shell and terminal use aligned proot args.
- In a delegation-only assistant bound to the same workspace, ask for a task that would normally need shell execution.
  - Expected: prompt/tool behavior does not expose `workspace_shell`; if the model still calls it, tool output is the clear unavailable-tool result, not `Tool not found` or a stack trace.
- Optional cancellation smoke for #33: spawn a long-running subagent task and do not press stop; a non-user interruption should not summarize as "Task cancelled by user". Pressing stop manually should still use user-cancel wording.

### Related Specs

- `.trellis/spec/workspace/storage-policy.md` - PRIVATE supports Git pack writes; EXTERNAL does not; UI must warn for EXTERNAL.
- `.trellis/spec/app/subagent-runtime.md` - delegation-only prompt/tool availability, unavailable-tool output, and cancellation reason contracts.

### External References

- GitHub issue #33: https://github.com/Arsucar/rikkahub/issues/33
- GitHub issue #35: https://github.com/Arsucar/rikkahub/issues/35

## Caveats / Not Found

- Follow-up device proot git smoke completed on 2026-07-06 after the device was unlocked: `adb shell run-as me.arsucar.rikka.debug sh /data/local/tmp/run_current_proot_git.sh` reported `git version 2.43.0`, `packs=1 l2s=0 files=1`. This confirms current workspace git pack files are written and no `.l2s` residue remains.
- Therefore #33 and #35 are ready for evidence comments and closure.
- No direct unit test was found for `GenerationHandler`'s unavailable-tool JSON branch; closure evidence is static code/spec plus delegation prompt/tool filtering tests.
- Existing final install/app launch evidence is sufficient for build/install health, but not for #33/#35 closure by itself.
- The Trellis current-task pointer was unset during this audit; output path was taken from the user-provided active task path.
