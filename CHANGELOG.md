# Changelog

All notable changes to the Rikka-Arsucar fork will be documented in this file.

## v2.3.5

### New Features

- **Log Redaction** — Sensitive headers (Authorization, API keys, cookies) and body secrets in AI request logs are now automatically redacted before being exposed to the AI via the `get_logs` tool. Raw logs in LogPage remain unredacted for the user.

- **`get_logs` AI Tool** — New local tool that lets the AI read app runtime logs (HTTP request logs + text logs) for debugging. Supports `type` filter (all/request/text) and `limit` param (1–32). Per-entry body/header truncation (2KB) + total payload cap (16KB) prevent GenerationHandler global truncation.

- **LogPage Export** — Export button on the Logs page saves logs as JSON via system file picker. Exported content is redacted (no API keys in file).

- **Subagent System** — Full subagent MVP: data model + settings UI (Phase A), permission layer (Phase B), runtime engine (Phase C), and chat tool cards UI (Phase D). Subagent profile page now lists Logs instead of AskUser.

- **Slash Command Skill Completion** — Type `/` in chat input to discover and apply skill prompts from the skills library.

- **Search Results with Images** — Web search results now include images, shown in AI messages and expandable sheet.

- **Screen Time Tool** — New local tool that lets the AI read the device's screen usage stats after the user grants Usage Access permission.

- **Image Generation Preview** — Fullscreen preview now shows the model name and a "Copy prompt" button.

### Fixes

- **Request Logging Persistence** — The "Record requests" toggle state is now persisted to DataStore across app restarts.

- **SubagentProfilePage** — Removed misleading AskUser option from local tools list.

## v2.3.4

### Fixes

- **Favorites CI Build** — Include `ImageFavoriteAdapter`, `FavoriteMeta`, and settings in the release build to fix missing-class runtime errors.

## v2.3.3

### Fixes

- **Image Generation** — Fix concurrency slot management, favorites grouping, and review feedback issues.
