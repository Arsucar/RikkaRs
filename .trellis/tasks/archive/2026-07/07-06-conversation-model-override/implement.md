# Implementation Plan

## Checklist

- [x] Inspect Conversation persistence path and migration mechanism.
- [x] Add nullable `chatModelId` to model/entity/serializer as needed.
- [x] Add repository/update API for conversation model override.
- [x] Change `ChatVM.setChatModel` to update conversation override only.
- [x] Update model display and generation resolution to use the shared fallback chain.
- [x] Confirm assistant settings and web route still update assistant default.
- [x] Add focused tests if persistence/model tests exist.

## Validation

- [x] `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.datastore.ChatModelResolutionTest" --no-daemon`
- [x] `.\gradlew :app:compileDebugKotlin --no-daemon` passed after clearing stale Kotlin/Gradle output state; full output captured in `.trellis/workspace/your-name/compile-debug-kotlin.log`.
- [x] `.\gradlew :app:assembleDebug --no-daemon --console=plain`
- [ ] Manual check with two conversations under one assistant. Blocked: `adb devices` returned no online devices and reconnect to `100.99.129.110:5555` timed out.
