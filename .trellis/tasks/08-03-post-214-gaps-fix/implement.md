# Implement checklist

## Code

- [ ] R1 #222: `@Serializable` on `Screen.SettingClash`
- [ ] R2 #217: XML `<JSONPatch>` extraction in `UpdateVariableParser` + tests
- [ ] R3 #224: `ClashRetryTrace` / `ClashRetryTracer` + Koin
- [ ] R4 #224: Instrument `AIRequestInterceptor` all 429 paths
- [ ] R5 #224: `SettingClashPage` debug panel UI + i18n
- [ ] R6 Unit tests: tracer ring buffer; interceptor skip reasons (if testable without full OkHttp); MVU XML

## Validate

- [ ] `.\gradlew --no-daemon :app:testDebugUnitTest --tests "*UpdateVariable*" --tests "*Clash*"` (and any new test class)
- [ ] `.\gradlew --no-daemon :app:compileDebugKotlin` (or installDebug if device)
- [ ] Only last agent may run Gradle compile

## Comments

- [ ] #222, #224 close/update with CN+EN checked lists after fix
- [ ] #214–#220 re-verify comments with honest checked/unchecked AC from audit + this round
- [ ] #216/#217 note MVU XML fix commit if landed

## Rollback

Revert the three feature commits independently; comments are append-only.