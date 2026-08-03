# Implement checklist

## Code

- [x] R1 #222: `@Serializable` on `Screen.SettingClash`
- [x] R2 #217: XML `<JSONPatch>` extraction in `UpdateVariableParser` + tests
- [x] R3 #224: `ClashRetryTrace` / `ClashRetryTracer` + Koin
- [x] R4 #224: Instrument `AIRequestInterceptor` all 429 paths
- [x] R5 #224: `SettingClashPage` debug panel UI + i18n
- [x] R6 Unit tests: tracer ring buffer; MVU XML (interceptor path via code review)

## Validate

- [x] Focused unit tests + compileDebugKotlin BUILD SUCCESSFUL
- [x] assembleDebug SUCCESS; no adb device → no installDebug
- [x] Only main agent ran Gradle

## Comments

- [x] #222, #224 closed with CN+EN checked lists; PRs #223/#225 closed as superseded by `1d188aa2`
- [x] #214–#220 re-verify comments with honest checked/unchecked AC
- [x] #216/#217 note MVU XML patch commit

## Rollback

Revert the three feature commits independently; comments are append-only.