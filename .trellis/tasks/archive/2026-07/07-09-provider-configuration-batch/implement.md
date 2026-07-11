# Provider Configuration Batch Implementation Plan

- [x] Add provider rate-limit data model fields with defaults.
- [x] Preserve rate-limit fields through provider type conversion.
- [x] Add RPM/TPM controls to provider configuration UI.
- [x] Add a shared provider limiter and call it from the unified generation path.
- [x] Remove suggested provider tags from provider filter and tag management
      sources.
- [x] Compile `:app:compileDebugKotlin`.
- [x] Run `git diff --check`.
- [x] Mark PRD acceptance criteria after verification.

## Verification

- `.\gradlew --no-daemon :app:compileDebugKotlin` passed.
- `git diff --check` passed.
- `adb devices` reported no connected devices, so install verification was not run.
