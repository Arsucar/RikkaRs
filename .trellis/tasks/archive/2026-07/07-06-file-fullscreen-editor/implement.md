# Implementation Plan

## Checklist

- [x] Extract shared fullscreen text viewer/editor composable.
- [x] Add text-like file detection helper.
- [x] Wire workspace file row menu actions.
- [x] Wire edited-file sheet actions using resolved workspace paths.
- [x] Wire document chip readonly viewer for text-like documents.
- [x] Add user-visible error handling for read/write failures.

## Validation

- [ ] `./gradlew :app:compileDebugKotlin` (attempted; Gradle daemon/cache repeatedly stopped or hung after prior interrupted runs)
- [ ] Manual UI check with `.md`, `.json`, `.kt`, `.log`, and a binary file.
- [x] `git diff --check`
