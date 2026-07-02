# NewAPI/OneAPI channel JSON import

**Issue:** #19  
**Task:** `.trellis/tasks/07-02-newapi-import`

## Problem

NewAPI / OneAPI (and compatible gateways) commonly export a small JSON “channel connection” blob users copy from the admin UI or share between apps. Today Rikkahub only imports provider config via:

- `ai-provider:v1:` + Base64(`ProviderSetting`) from QR / gallery QR
- Cherry Studio / Chatbox backup files on the backup tab

Users with NewAPI JSON must manually transcribe `key` and `url` into a new OpenAI-compatible provider. There is no clipboard import on the provider settings page.

## Goal

Accept the de-facto NewAPI channel JSON shape, map it to `ProviderSetting.OpenAI`, and add it through the same provider-list flow as QR import (new UUID, prepended to list).

## Requirements

### Input format (v1)

JSON object with:

| Field | Required | Maps to |
|-------|----------|---------|
| `_type` | yes | must equal `"newapi_channel_conn"` |
| `key` | yes (non-blank) | `OpenAI.apiKey` |
| `url` | yes (non-blank) | `OpenAI.baseUrl` (normalized) |

Example:

```json
{"_type":"newapi_channel_conn","key":"sk-xxx","url":"https://api.example.com"}
```

Unknown JSON keys are ignored. No models in payload; imported provider starts with empty `models` (same as share encode behavior).

### Format routing

Any text import path (QR raw string, gallery QR string, clipboard) must:

1. If string starts with `ai-provider:v1:` → existing `decodeProviderSetting` behavior (unchanged).
2. Else if trimmed text parses as JSON with `_type == "newapi_channel_conn"` → NewAPI import path.
3. Else → show user-visible error; no crash.

### UX

- **QR / gallery:** No change to entry points; success path may be NewAPI or v1 after routing.
- **Clipboard:** New action in the provider import dialog on `SettingProviderPage`: read system clipboard plain text, run same router, then name prompt.
- **Name:** NewAPI imports do not include a reliable display name. After successful parse, show a short dialog asking only for provider **name** (default e.g. `"NewAPI"` or derived from URL host). On confirm, apply name and call existing `onAdd` → `copyProvider(Uuid.random())`.

### Out of scope

- Bulk file picker for `.json` on provider page (backup tab unchanged).
- Exporting NewAPI format from Rikkahub share.
- Auto-fetching model list from gateway.

### Constraints

- Do not change Cherry Studio / Chatbox importers or backup tab behavior.
- Do not change `encodeForShare()` output format.
- Invalid Base64 / wrong v1 prefix / wrong NewAPI shape: toast error, same as current QR failure pattern.

## Acceptance criteria

- [ ] Pasting valid NewAPI channel JSON (clipboard action) creates a new OpenAI provider with correct `apiKey` and `baseUrl`, new `id`, empty models.
- [ ] User is prompted only for display name before the provider is saved; cancel dismisses without adding.
- [ ] Scanning or gallery-decoding QR whose payload is NewAPI JSON (not `ai-provider:v1:`) succeeds with the same name prompt and field mapping.
- [ ] Scanning / gallery / clipboard with valid `ai-provider:v1:` still works as today (no extra name prompt if name already in payload).
- [ ] Malformed JSON, wrong `_type`, blank `key`/`url`, or unsupported strings show an error toast and do not add a provider or crash.
- [ ] `ShareSheetTest` (and new unit tests for router + NewAPI parser) pass; Cherry/Chatbox import paths unaffected (no regressions in their modules).

## Notes

- Parent task: `07-02-issue-19-20`.
- Gateway treated as OpenAI-compatible (`ProviderSetting.OpenAI` defaults for paths / flags).