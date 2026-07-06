# Implementation Plan

## Phase 0: Audit and Task Evidence

- [x] Build an issue closure matrix for `#31`, `#33`-`#42`.
- [x] For each previously implemented issue, map acceptance criteria to code/tests/manual evidence.
- [x] Identify missing UI refinements before closing any feature issue.
- [x] Keep `task.json.commit` and issue comments aligned with actual commits.

## Phase 1: Validate and Close Existing Implementations

- [x] Validate `#31` per-conversation model override with tests; added explicit clear-to-default UI affordance.
- [x] Validate `#40` fullscreen text file UI on device for text and binary files.
- [x] Validate skill access issues `#34/#36/#37/#38` with focused tests; #36 global Skill detail and assistant-private UI path passed device UI validation.
- [x] Validate git pack and delegation/cancellation slices of `#33/#35/#42` with focused tests.
- [x] Fix discovered code/UI gaps before issue closure: terminal `--link2symlink`, assistant-private skill UI, global Skill detail private-copy UI, memory table scope/tests, #40 localization and text limit.

## Phase 2: Implement #39

- [x] Read app data/UI specs and memory-related code.
- [x] Add memory scope data model and Room migration.
- [x] Update DAO/repository queries for local + global memory reads.
- [x] Update generation and `memory_tool` scope handling.
- [x] Update assistant memory UI with scope display and edit controls.
- [x] Add focused unit tests for repository reads and tool behavior.
- [x] Cover migration/UI behavior in final installed-device validation.

## Phase 2 Validation

- [x] `.\gradlew --no-daemon :app:compileDebugKotlin --rerun-tasks --console=plain`
- [x] `.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.tools.MemoryToolsTest" --tests "me.rerere.rikkahub.data.repository.MemoryRepositoryTest" --rerun-tasks --console=plain`
- [x] Manual UI check for creating local/global memories and switching existing memory scope.

## Phase 3: Implement #41

- [x] Add disabled-by-default settings and assistant gate.
- [x] Add table memory entities/repository interfaces.
- [x] Add UI entry for memory tables and basic management.
- [x] Add injection/tool registration behind gates; auto sync remains disabled/no-job.
- [x] Add zero-intrusion tests for disabled mode.
- [x] Generate Room schema `30.json`.
- [x] Add conversation/assistant/global table tool scopes and focused transformer/tool tests.

## Phase 3 Validation

- [x] `.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.model.MemoryTableTest" --tests "me.rerere.rikkahub.data.repository.MemoryTableRepositoryTest" --tests "me.rerere.rikkahub.data.ai.tools.MemoryToolsTest" --tests "me.rerere.rikkahub.data.repository.MemoryRepositoryTest" --console=plain`
- [x] `git diff --check`
- [x] Focused `MemoryTableInjectionTransformerTest`, `MemoryTableToolsTest`, and `MemoryTableRepositoryTest` passed in final validation.
- [x] Manual UI check for global/assistant memory table switches, template CRUD, and assistant document CRUD.
- [x] Manual validation that disabled mode remains visibly gated: disabled auto-sync copy is shown, and unit coverage verifies no reads/tools/injection while disabled.

## Phase 4: Final Verification and Closure

- [x] `.\gradlew --no-daemon lint --console=plain`
- [x] `.\gradlew --no-daemon test --console=plain`
- [x] `adb devices`
- [x] `.\gradlew --no-daemon :app:installDebug --console=plain`
- [x] If install fails, `adb connect 100.99.129.110:5555`, then retry install once. Not needed; first install succeeded.
- [x] #36 device UI validation: bottom-right more/options -> 扩展管理 -> Skills -> 管理 -> global Skill detail shows global/private-copy controls; `复制到助手` creates the assistant-private copy and Assistant Extensions -> Skills shows it as `助手私有`.
- [x] Comment and close GitHub issues with validation evidence. Closed with evidence: `#31`, `#33`, `#34`, `#35`, `#36`, `#37`, `#38`, `#39`, `#40`, `#41`, `#42`.

### Current Blocker

None. Remaining manual checks for `#31`, `#33/#35`, `#39`, `#40`, and `#41` were completed on the unlocked device on 2026-07-06.

## Risk Notes

- Room migration ordering is high risk because `#31` already changed conversation schema and `#39/#41` will change memory schemas.
- `#41` is broad; keep gates strict and prove disabled mode first before adding mutation or auto-sync.
- Do not run Gradle from multiple agents in parallel.
