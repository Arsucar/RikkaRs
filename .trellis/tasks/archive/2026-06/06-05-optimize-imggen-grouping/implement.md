# Optimize Image Generation Grouping - Implementation Plan

## Checklist

- [x] Load project coding guidance with `trellis-before-dev` before editing production code.
- [x] Replace `buildPromptComponents()` / Union-Find usage with a greedy exact-prompt clustering helper.
- [x] Add `MAX_VARIANT_SEARCH_WINDOW = 30` near the existing grouping constants.
- [x] Ensure candidate admission checks the full proposed cluster template, not only pairwise connectivity.
- [x] Preserve exact prompt grouping and final newest-first sorting.
- [x] Refactor common prefix/suffix helpers to use `commonPrefixWith` / `commonSuffixWith` without suffix-prefix overlap.
- [x] Add JVM tests for the A-B-C transitive-match case.
- [x] Add or update a test that proves matching is bounded by the 30-group recent window.
- [x] Run the focused app JVM grouping test command.

## Validation Commands

```bash
./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.imggen.ImgGenGroupingTest"
```

If the focused command is unavailable in the local Gradle setup, run:

```bash
./gradlew :app:testDebugUnitTest
```

## Risky Files

- `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenGroupingTest.kt`

## Review Gates Before Start

- `prd.md` has no open questions.
- `design.md` documents the fixed window and template-stable greedy behavior.
- User approves moving from planning to implementation.

## Rollback Point

Before editing `ImgGenVM.kt`, inspect the current diff and avoid touching unrelated local changes such as
`.omc/sessions/`. If the new tests reveal unacceptable grouping regressions, revert only the grouping helper changes
made for this task and keep unrelated files intact.
