# Validation

## Passed

- `git diff --check`
- After the card-layout follow-up, `.\gradlew --no-daemon :app:assembleDebug` passed and produced
  `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` at `2026-07-17 00:20:10 +08:00`.
- Follow-up APK SHA-256: `43E371A883D58868F600D8BA7A8F589F16C04A46E725DE586F27F5FFE455E9AE`.
- Follow-up APK shared for primary-device testing: `https://gofile.io/d/TpTbTi`.
- Template deduplication/management follow-up passed `processDebugResources`,
  `compileDebugKotlin`, focused `MemoryTableRepositoryTest` +
  `AssistantMemoryTableScopeTest`, and `assembleDebug`.
- Template-management APK built at `2026-07-17 01:51:26 +08:00`, size `84,879,819` bytes,
  SHA-256 `9A55499F1FDF5DD7688C2789D9EDD5F974DB21C01694CA59ACB06B5E7E754F7F`.
- Template-management APK shared for primary-device testing: `https://gofile.io/d/sAKnR5`.
- Android resource processing completed as part of the Gradle verification/install builds.
- Kotlin compilation completed before the focused unit test and before `installDebug` reached the device-install task.
- `AssistantMemoryTableScopeTest`: 12 tests, 0 failures, 0 errors.
  - Report: `app/build/test-results/testDebugUnitTest/TEST-me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryTableScopeTest.xml`
- Six configured `strings.xml` files parse successfully; new keys are unique and format placeholders match.

## Incomplete / Failed

- `:app:lintDebug` ran for an extended period and was terminated; full lint completion is not claimed.
- Device `100.99.129.110:5555` was online, but `:app:installDebug` failed while pushing the APK with
  `java.io.IOException: 你的主机中的软件中止了一个已建立的连接。`
- The required reconnect and single retry produced the same ADB transport failure. Installation success and on-device visual inspection are not claimed.
- A later explicit reinstall attempt also failed through three transport paths:
  - `adb install -r` ended after the wireless device stopped responding;
  - non-streaming chunk upload reached the third chunk before the device became offline;
  - device-side HTTP download over Tailscale reached about 19% at roughly 20 KB/s before the device became offline.
- Device temporary APK/chunk files were cleaned after reconnecting. The previously installed `me.arsucar.rikka.debug` package remains present, but the new build was not installed.

## Known Boundaries

- UI state prevents ordinary duplicate creation when a primary document is already loaded, but no database uniqueness constraint was added.
- Historical extra documents remain visible and are not merged or deleted.
