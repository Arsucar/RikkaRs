# Implementation plan: NewAPI channel JSON import

## Preconditions

- [ ] Read `research/*.md` and this task’s `prd.md` / `design.md`.
- [ ] `task.py start` only after PRD/design/implement review (parent may gate).

## Checklist

### 1. Parser + decoder (no UI)

- [ ] Add `NewApiChannelImporter.kt` with `parseChannelConn(JsonElement): ProviderSetting.OpenAI` and validation for `_type`, `key`, `url`.
- [ ] Add `normalizeGatewayBaseUrl` (local or shared util) for `url` → `baseUrl`.
- [ ] Add `ProviderImportDecoder.kt` with `decodeProviderImportText(String): ProviderSetting` routing v1 vs NewAPI per design.
- [ ] Add `ProviderImportResult` sealed type **if** using name-dialog split at decoder layer (`Complete` / `NeedsName`); map v1 → `Complete`, NewAPI → `NeedsName`.

### 2. Unit tests

- [ ] `NewApiChannelImporterTest`: happy path, blank key, wrong `_type`, invalid JSON element type.
- [ ] `ProviderImportDecoderTest` (or extend `ShareSheetTest`): v1 round-trip via router; NewAPI sample; garbage string throws.
- [ ] Run `.\gradlew :app:testDebugUnitTest --tests "*ShareSheetTest*" --tests "*NewApi*" --tests "*ProviderImport*" --no-daemon`.

### 3. Clipboard helper

- [ ] Add or reuse clipboard read in `ContextUtil.kt` (plain text only).

### 4. SettingProviderPage UI

- [ ] Import dialog: add “Paste from clipboard” button + strings (`values/strings.xml`; localize only if user/product asks).
- [ ] Implement name `AlertDialog` state for `NeedsName` path.
- [ ] Refactor `handleQRResult` / `handleImageQRCode` to use decoder + name flow.
- [ ] Wire clipboard button: read → decode → name dialog or direct `onAdd`.

### 5. Integration / manual QA

- [ ] `adb devices` → `.\gradlew :app:installDebug --no-daemon`.
- [ ] Manual: paste NewAPI JSON → name → provider appears with key/URL.
- [ ] Manual: existing v1 QR share still imports.
- [ ] Smoke: backup tab Cherry import unchanged (no code edits there expected).

### 6. Quality gate

- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon`
- [ ] `.\gradlew :app:lint --no-daemon` (or project-standard lint scope)
- [ ] Trellis `trellis-check` / task `check.jsonl` specs if configured.

## Review gates

- After step 2: decoder API frozen before UI work.
- After step 4: UX review name default and error toasts.
- Before finish: all acceptance criteria in `prd.md` checked.

## Rollback

- Revert commits touching `ProviderImportDecoder`, `NewApiChannelImporter`, and `SettingProviderPage` only; no schema migration involved.

## Suggested commit message

`feat(settings): import NewAPI channel JSON via clipboard and QR`