# Implementation Plan

## Checklist

- [x] Inspect existing skill path tests and workspace tool path tests.
- [x] Decide storage layout or metadata model for assistant-private skills.
- [x] Add path-resolution tests for `/skills`, `use_skill(path)`, symlinks, and traversal.
- [x] Implement skill path resolver changes.
- [x] Implement workspace known-bind resolution or update prompt/error behavior.
- [x] Update UI/tool filtering so private skills are visible only to authorized assistants.
- [x] Run focused tests, then `./gradlew test` or the narrow module test command.

## Validation

- [x] `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.files.SkillPathsTest" --tests "me.rerere.rikkahub.data.ai.tools.SkillsToolsTest" --tests "me.rerere.rikkahub.data.ai.tools.WorkspaceKnownMountTest" --no-daemon`
- [x] `.\gradlew :app:compileDebugKotlin --no-daemon`
- [x] `adb install -r -d app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` after Gradle install hit a device-side temporary APK parse failure.
- [x] `adb devices` found `100.99.129.110:5555` in `device` state.
- [x] `.\gradlew :app:installDebug --no-daemon`
