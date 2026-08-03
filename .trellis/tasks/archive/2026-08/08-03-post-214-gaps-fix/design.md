# Design: Post-214 gaps

## #222

One-line fix in `RouteActivity.kt`: annotate `data object SettingClash : Screen` with `@Serializable` like every other Screen.

## #217 MVU XML

`UpdateVariableParser.parseOps(body)` today requires body to be JSON. ST outputs:

```xml
<UpdateVariable>
  <Analysis>...</Analysis>
  <JSONPatch>
  [{"op":"replace","path":"/x","value":"y"}]
  </JSONPatch>
</UpdateVariable>
```

Approach:
1. If direct JSON parse fails, extract inner text of `<JSONPatch>…</JSONPatch>` (case-insensitive) and parse that as JSON array/object.
2. Optionally ignore `<Analysis>` entirely.
3. Keep fail-closed: if still unparsable → applied=false, original text.

## #224 Clash debug panel

### Data

```kotlin
data class ClashRetryTrace(...)
data class SwitchAttempt(...)
object ClashRetryTracer {
  // ArrayDeque max 20, Mutex, StateFlow for UI
  fun record(...); fun clear(); fun snapshot(): List<ClashRetryTrace>
  fun formatForCopy(traces): String // host only, no keys/body
}
```

Koin single; inject into `AIRequestInterceptor` and `SettingClashPage` (or VM).

### Interceptor instrumentation

On every `response.code == 429`:
- Always append a trace (even skip paths).
- Reasons: `maxRetries<=0`, `provider unmatched`, `rotation disabled`, switch failures, per-attempt switch+replay, exhausted.

Non-429: no record.

### UI

`SettingClashPage` CardGroup “调试面板”: LazyColumn of expandable cards, empty state, Clear + Copy buttons, Toast on copy.

### Strings

`values` + `values-zh` for panel title, empty, clear, copy, field labels.

## Comments workflow

After verification commands, use `gh issue comment` with explicit `- [x]` / `- [ ]` per AC from issue body, citing commit SHAs and command results.