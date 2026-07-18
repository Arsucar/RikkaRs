Delivered and verified on the target branch `release/rikka-arsucar`.

## Resolved

- Reduced top-level Hook actions to Tag management / Sync memory table. Tag management now has one allowlist, with strategy expressed by the evaluation prompt.
- Replaced the old parallel action choices with a Select and collapsed the prompt by default. The editor no longer exposes the former Add/Transition modes or pseudo-multiselect condition controls.
- Migrated legacy `ADD_CONVERSATION_TAG` and `TRANSITION_CONVERSATION_TAGS` configurations to unified Tag management while preserving Sync configuration and Preview/Run/Retry paths.
- Added strict multi-operation tag changes with exact top-level fields, operation-count and allowlist validation. All operations are validated before the transactional commit, and the former Issue-evidence hard gate is removed.

## Verification

- Fix commit: `0f078ae1c4c4810ac3b9d891e384ceb4e0500004`
- Release commit: `2e3c841a861c79b62724b781ebffe180606a416a`
- Released version: `v2.3.33`; the tag was verified to contain the fix commit.
- GitHub Release: https://github.com/Arsucar/RikkaRs/releases/tag/v2.3.33
- arm64 APK: `rikka-arsucar-v2.3.33-arm64.apk`
- APK SHA-256: `46c7640c47bf1a49aca8a9178708b2c1cba8d2a95688bb3c654607377108dbc1`
- Executed: `./gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.model.ConversationHookTest" --tests "me.rerere.rikkahub.service.hooks.HookOutputParserTest" --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantHooksPageTest"`
- Result: `BUILD SUCCESSFUL`; 37 tests across three classes, with 0 failures, 0 errors, and 0 skipped tests. `:app:compileDebugKotlin` also passed in the same task graph.

## Code locations

- Unified action model and migration: `ConversationHook.kt`
- Action Select, allowlist, and collapsed prompt UI: `AssistantHooksPage.kt`
- Strict multi-operation parser: `HookOutputParser.kt`
- Allowlist and tag-existence preflight: `ManageConversationTagsHookAction.kt`
- Atomic Room commit: `ConversationTagHookCommitter.kt`
- Release notes: the `v2.3.33` section in `CHANGELOG.md`.

## Known boundaries

- `adb devices` reported the configured device `100.99.129.110:5555` as `offline`, so `installDebug` was not run in this verification pass. This comment does not claim device-level UI density or manual Preview/Run/Retry acceptance.
- Fail-closed zero-write behavior was statically verified through the action/committer paths. Existing automated tests directly cover migration, editor boundaries, and parser behavior, but there is not yet a dedicated action/committer transaction regression test. That coverage gap remains tracked by the cross-layer test work in #152.
