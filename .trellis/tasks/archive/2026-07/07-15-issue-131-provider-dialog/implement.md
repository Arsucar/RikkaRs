# Execution Plan

1. Confirm commit `31570c1e` remains present and inspect the two affected Compose layouts.
2. Run `git diff --check` and `./gradlew --no-daemon :app:compileDebugKotlin`.
3. Check `adb devices`; reconnect the fixed device endpoint if necessary.
4. If a device is available, run `./gradlew --no-daemon :app:installDebug` and manually verify small-screen scrolling, keyboard visibility, provider switching, and switch alignment.
5. Add only narrowly scoped hardening if device verification exposes a remaining overlap.
6. Push the fix commit, publish distinct Chinese and English delivery comments, reread both, then close #131.

## Rollback Point

- No new source edit is expected. Any optional hardening must be isolated from the already committed core fix.
