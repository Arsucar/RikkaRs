# Issue Closure Matrix

| Issue | Current evidence | Remaining work before close |
|---|---|---|
| #31 | Code commit `fa25f6c6`; focused `ChatModelResolutionTest`/`ConversationTest` passed; current task adds explicit clear-to-default UI in chat model icon path. Final `lint`, `test`, and `:app:installDebug` passed on 2026-07-06. Device UI validation on 2026-07-06 confirmed an old conversation can switch to `glm-5.2`, clear back to assistant default `grok-composer-2.5-fast`, and a new conversation does not inherit the old conversation override. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #33 | Split across git pack, cancellation, delegation-only tool policy commits; focused workspace/subagent tests passed. Current task removed hardcoded terminal `--link2symlink` by sharing `WorkspaceProotCommandBuilder`; `WorkspaceTerminalSessionTest` and `ProotShellRunnerTest` passed. Final `lint`, `test`, and install passed. Device proot git smoke on 2026-07-06 passed through `run-as me.arsucar.rikka.debug sh /data/local/tmp/run_current_proot_git.sh`: `git version 2.43.0`, `packs=1 l2s=0 files=1`. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #34 | Code commit `ee34efe7`; focused `WorkspaceKnownMountTest`/skills tests passed. Final `lint`, `test`, and install passed. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #35 | Code commits `201969f0` and `de4c6ff0`; current task also aligned in-app terminal and AI shell proot args. `WorkspaceFilesStorageTest`, `WorkspaceStorageMigratorTest`, `WorkspaceTerminalSessionTest`, and `ProotShellRunnerTest` passed. Final `lint`, `test`, and install passed. Device proot git smoke on 2026-07-06 passed in the current workspace path with pack files created and no `.l2s` residue: `packs=1 l2s=0 files=1`. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #36 | Runtime/private skill visibility tests passed; current task adds Assistant Extensions UI for assistant-private skill create/import/edit/delete and keeps global Skills page global-only. Follow-up adds global Skill detail UI for assistant-private copies. `SkillFileImportReaderTest`, `SkillCopyFilesTest`, and `SkillAssistantTargetsTest` passed. Device UI validation on 2026-07-06 confirmed: bottom-right more/options -> 扩展管理 -> Skills -> 管理 -> global Skill detail shows 全局 Skill panel, assistant selector, 管理助手 Skills, 复制到助手; copying creates a private copy, disables the copy button, and Assistant Extensions -> Skills shows the copied Skill as 助手私有. Latest `lint`, `test`, and install passed. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #37 | Same root as #34; code commit `ee34efe7`; focused `/skills` mount tests passed. Final `lint`, `test`, and install passed. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #38 | Code commit `ee34efe7`; focused `SkillPathsTest`/`SkillsToolsTest` symlink tests passed. Final `lint`, `test`, and install passed. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #39 | Current task implements `MemoryScope`, DB scope column, effective reads, tool scope parameter, default-scope compatibility, and UI scope switching. `MemoryToolsTest`/`MemoryRepositoryTest` passed. Final `lint`, `test`, and install passed. Device UI validation on 2026-07-06 confirmed list labels show `仅本助手` / `全局`, the edit dialog exposes a `全局` switch, saving changes scope, and switching back restores assistant-only scope. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #40 | Code commit `a57ca25d` for fullscreen text file editor; current task fixes hardcoded `View`, localizes file errors, and aligns workspace text read/write limit to 5MB. Final `lint`, `test`, and install passed. Device UI validation on 2026-07-06 used workspace `897d1ef7-ce1e-4aaf-8e11-47a1e44408e9`, Files tab, temporary `issue40-ui` files. `.log`, `.json`, `.kt`, and `.md` menus showed `查看` and `编辑`; `.log` `查看` opened read-only fullscreen editor with only `确定`; `.log` `编辑` opened `取消` / `保存`, saved marker `issue40-edited`, and adb readback confirmed persistence. `blob.bin` menu showed only `导出` / `分享` / `删除`, with no text view/edit actions. Temporary validation files were removed. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #41 | Current task implements gated memory tables with separate Room tables, repository CRUD, assistant/global UI, conversation/assistant/global tool scopes, injection limits, disabled no-read/no-tool tests, schema `30.json`, and auto-sync disabled. `MemoryTableInjectionTransformerTest`, `MemoryTableToolsTest`, and `MemoryTableRepositoryTest` passed. Final `lint`, `test`, and install passed. Device UI validation on 2026-07-06 confirmed global/assistant memory table switches, disabled auto-sync explanatory text, template CRUD, document CRUD, scope controls, revision display, and cleanup of temporary data. | Commented with evidence and closed on GitHub on 2026-07-06. |
| #42 | Code commit `44e28c1f`; focused `SubagentRuntimeTest`/`SubagentPermissionTest`/`FinishWorkToolTest` passed. Final `lint`, `test`, and install passed. | Commented with evidence and closed on GitHub on 2026-07-06. |

## Final Verification Evidence

- `git diff --check` passed.
- `.\gradlew --no-daemon :app:compileDebugKotlin --console=plain` passed after the #36 global-detail follow-up.
- `.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.files.SkillCopyFilesTest" --tests "me.rerere.rikkahub.ui.pages.extensions.skills.SkillAssistantTargetsTest" --console=plain` passed.
- Focused app tests passed for memory table, flat memory, private skill import, and workspace terminal.
- `.\gradlew --no-daemon :workspace:testDebugUnitTest --tests "me.rerere.workspace.ProotShellRunnerTest" --console=plain` passed.
- `.\gradlew --no-daemon lint --console=plain` passed after the #36 global-detail follow-up.
- `.\gradlew --no-daemon test --console=plain` passed after the #36 global-detail follow-up.
- `adb devices` showed `100.99.129.110:5555 device`.
- `.\gradlew --no-daemon :app:installDebug --console=plain` installed `app-arm64-v8a-debug.apk` on device `PJF110 - 16` after the #36 global-detail follow-up.
- Device app smoke launch started process `me.arsucar.rikka.debug` PID `4184`; screenshot saved to `.trellis/workspace/your-name/final-app-smoke.png`.
- Device UI validation for #36 global detail path passed on unlocked device: global Skill detail exposed the assistant-private copy section and copying to the default assistant surfaced the Skill as assistant-private in Assistant Extensions -> Skills.
- Device UI validation for #31 passed: conversation override, clear-to-default, and new-conversation default behavior were confirmed on the installed debug app.
- Device proot git smoke for #33/#35 passed with `git version 2.43.0`, `packs=1 l2s=0 files=1`.
- Device UI validation for #39 passed: per-memory assistant/global labels and edit scope switching persisted and were switched back.
- Device UI validation for #40 passed: workspace `.log/.json/.kt/.md` files exposed `查看`/`编辑`, fullscreen view/edit worked, edit persisted to disk, and binary `blob.bin` did not expose text actions.
- Device UI validation for #41 passed: memory table switches, template CRUD, document CRUD, scope controls, revision display, and disabled auto-sync copy were confirmed; temporary data was removed.

## Blocking Manual Gate

All remaining issue-specific manual gates are complete. GitHub issues `#31`, `#33`, `#35`, `#39`, `#40`, and `#41` were commented with evidence and closed on 2026-07-06.
