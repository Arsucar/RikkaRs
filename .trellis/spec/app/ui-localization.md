# UI Localization Contract

## Scenario: New or Changed Android UI Text

### 1. Scope / Trigger

- Trigger: adding or changing any user-visible Compose/Android text, label, button, error, empty state, content description, enum display, palette name, or status.
- Applies to the `app` module and feature-module Android resources.

### 2. Signatures

- Default English resource: `src/main/res/values/strings.xml`.
- Required Simplified Chinese resource: `src/main/res/values-zh/strings.xml`.
- Compose access: `stringResource(R.string.key)` or `LocalResources.current.getString(...)` when resources are needed outside a composable text call.
- Localization updates use the repository `locale-tui-localization` workflow.

### 3. Contracts

- Every new or modified user-visible UI value must be resource-backed.
- Delivery requires a real Simplified Chinese translation; copying the English source into `values-zh` is not acceptable fallback.
- Resource keys, formatting placeholders, quantities, escaping, and line breaks must remain compatible across default and Simplified Chinese resources.
- Internal enum names and storage keys such as `MODEL_NOT_FOUND`, `red`, or `colorKey` must be mapped to localized display resources.
- User data, model output, tag names, Hook names, and sanitized provider messages remain dynamic data and are not translated.
- Android XML string apostrophes must use the resource escape (`Couldn\'t`), even when an XML parser accepts the raw character; AAPT otherwise reports an invalid escape while flattening values.
- If `locale-tui` adds the source key but automatic translation fails, treat every untranslated configured locale as missing work. Verify and fill the locale files instead of accepting the command's zero exit code as translation success.

### 4. Validation & Error Matrix

- Default key missing -> resource compilation failure; block delivery.
- Simplified Chinese key missing -> fallback UI; block delivery for new/changed UI.
- Simplified Chinese value exactly equals English -> review and translate unless it is an approved proper noun.
- Placeholder set differs -> formatting/runtime risk; block delivery.
- `locale-tui` reports provider/region translation errors -> source key may exist but translated keys are absent; inspect every configured locale before delivery.
- Direct `Text("...")`, `contentDescription = "..."`, enum `.name`, or raw internal key shown to users -> replace with a resource mapping.

### 5. Good/Base/Bad Cases

- Good: `HookErrorCode.MODEL_NOT_FOUND` maps to `R.string.hook_error_model_not_found` in English and Simplified Chinese.
- Base: user-created tag names are displayed unchanged.
- Bad: `Text(colorKey)` displays `indigo` directly.
- Bad: add English keys to every locale file to silence `MissingTranslation` without translating Simplified Chinese.

### 6. Tests Required

- Compare target key coverage between `values` and `values-zh`.
- For repositories with additional configured locales, parse every `values-*` XML and assert the complete target-key set exists in each file.
- Assert formatting placeholder sets are identical.
- Audit changed UI files for hardcoded user-visible literals and raw enum/storage-key display.
- Run `:app:processDebugResources`, `:app:compileDebugKotlin`, and `git diff --check`.
- For app UI changes, follow the device connection and `:app:installDebug` acceptance flow.

### 7. Wrong vs Correct

#### Wrong

```kotlin
Text(errorCode.name)
Text(colorKey)
```

#### Correct

```kotlin
Text(stringResource(errorCode.displayStringRes()))
Text(stringResource(colorKey.displayStringRes()))
```

The display layer owns localized labels while domain/storage identifiers remain stable.
