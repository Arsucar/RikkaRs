# Implementation Plan

## Checklist

- [x] Confirm product decision: include app-specific external workspace files.
- [x] Read Android backup rule docs and current manifest/XML before editing.
- [x] Update `app/src/main/res/xml/backup_rules.xml`.
- [x] Update `app/src/main/res/xml/data_extraction_rules.xml`.
- [x] If excluding, check whether backup UI copy needs a small clarification. Not applicable because external workspace files are included.
- [x] Run XML/resource validation.

## Validation

- `.\gradlew :app:processDebugResources`
- If nearby app resource changes are made, run `.\gradlew :app:compileDebugKotlin`.

## Risky Files

- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- Optional UI copy only if needed: backup page strings/resources.

## Rollback

- Revert only the XML backup rule edits and any optional copy changes.
- Do not change workspace storage settings or migrate files as rollback for this task.
