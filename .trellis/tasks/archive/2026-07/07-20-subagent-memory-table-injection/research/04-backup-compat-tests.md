# Research: Backup compat & tests

## Compat
- `SubagentProfile` new field with default empty set → old JSON decodes fine (`ignoreUnknownKeys` / defaults pattern already in `SubagentModelTest` legacy cases).

## Tests to extend
- `SubagentModelTest.kt` — roundtrip new field.
- `SubagentRuntimeTest.kt` / host-related — injection wiring if testable.
- New pure resolver tests.

## UI tests
- No hard requirement for Compose instrumentation; pure UI state helpers optional.
