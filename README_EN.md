<div align="center">
  <img src="docs/icon.png" alt="RikkaRs app icon" width="100" />
</div>

# RikkaRs

RikkaRs is a native Android LLM client originating from RikkaHub, independently maintained and released by Arsucar, and focused on agent workflows, local workspaces, and multi-provider conversations.

**English** | [简体中文](README.md) | [繁體中文](README_ZH_TW.md)

[![Latest release](https://img.shields.io/github/v/release/Arsucar/RikkaRs?label=release)](https://github.com/Arsucar/RikkaRs/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![ABI arm64-v8a](https://img.shields.io/badge/ABI-arm64--v8a-blue)](app/build.gradle.kts)
[![Segmented dual license](https://img.shields.io/badge/license-segmented%20dual-orange)](LICENSE)

**[Download the latest stable release](https://github.com/Arsucar/RikkaRs/releases/latest)**

<div align="center">
  <img src="docs/img/chat.png" alt="Chat interface" width="150" />
  <img src="docs/img/desktop.png" alt="Model picker" width="450" />
</div>

## About RikkaRs

RikkaRs originates from [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) and is independently maintained and released by Arsucar in the separate [Arsucar/RikkaRs](https://github.com/Arsucar/RikkaRs) repository. Valuable upstream changes are synchronized as needed while the project follows its own product roadmap and release cadence.

RikkaRs is an unofficial distribution and has no affiliation with or endorsement from the RikkaHub project or its maintainers. Its independent release package, `me.arsucar.rikka`, allows it to coexist with official RikkaHub; Firebase has been removed, so `google-services.json` is not required.

## Highlights

### Subagents

- Delegate subagents to run in parallel or in queues, with separate concurrency and invocation-depth limits, and cancel runs at any time.
- Pass required context to subagents and inspect complete transcripts; observe token usage, tool calls, and run status in real time.
- Subagents use the dedicated `finish_work` signal to declare completion so the main agent can finish reliably.
- Configure each subagent profile with its own model, working directory (CWD), tool permissions, and budgets for tokens, tool calls, duration, depth, and concurrency.
- Continue existing history under compatible scopes; full contexts are persisted so unfinished work can continue from persisted context after an app or process restart.

### Skills

- Manage global and assistant-private Skills, exposing required Skills safely to workspaces and subagents according to scope.
- Explicitly activate a Skill from chat with slash completion and browse clearer Skill directory cards.
- Let the assistant create or update Skills after confirmation while keeping private copies and file-access boundaries isolated.

### Memory

- Use global, assistant, and conversation memory scopes; synchronize, follow, or detach associations to prevent unrelated use cases from contaminating each other.
- Use both ordinary memories and structured memory tables; define table templates and create or maintain documents and rows as needed.
- Set a total injection budget, inject only relevant rows, and select an independent retrieval and injection strategy for each table.
- Control writes, create snapshots and roll back, import and export data, and retrieve non-injected memories on demand through tools.

### Context control

- Hide messages with soft deletion: old messages are excluded from model context but remain visible and recoverable in the conversation tree.
- Compress context manually or automatically when limits are reached; compression hides and retains old messages instead of hard-deleting them.
- Configure how many recent messages to retain and preserve compression preferences for later conversations.
- Before sending, use the read-only final context inspector to verify the actual messages after assembly, injection, and transformation.
- Override models per conversation and quickly switch recent models and prompt presets; new conversations do not accidentally inherit an old override.

### Workspaces and files

- Use a consistent **PRoot** workspace with Shell and file tools, or select an app-specific external workspace.
- Access external workspaces through the system file manager for easy file management and exchange over USB or from a PC.
- Edit text in full screen, render Markdown previews, and attach files of any type to conversations.
- Return workspace images as multimodal tool results; non-vision models receive an explicit fallback explanation.
- Show files changed by Shell and subagent tools as chips below messages so they remain easy to open, track, and continue editing.

### Providers and models

- Configure multiple providers with custom hosts, URLs, headers, bodies, and model lists.
- Organize and filter providers with tags; group, search, collapse, favorite, or expand/collapse all models in the picker.
- Set local RPM/TPM limits independently for each provider to pace requests before server-side limits are reached.
- Import NewAPI channel JSON, and import or export **compatible provider-sharing QR codes**.
- Organize configurations from multiple channels and public-benefit services centrally while retaining clear provider boundaries and filtering.

### Presets and extensions

- Combine prompt entries into prompt presets and isolate mode-specific injections with preset-scoped `ModeInjection`.
- Associate appropriate presets with assistant and subagent profiles for task-specific instruction injection.
- Connect MCP servers with OAuth 2.1, PKCE, dynamic client registration, token refresh, and reconnection support.

### Conversations and interface

- Archive conversations and organize them in folders; archive or restore assistants and resume each assistant's most recent conversation.
- Use a compact multi-select sharing layout with collapsible long message bodies for reviewing and sharing large selections.
- Use multimodal input and display with Markdown, LaTeX, and Mermaid rendering.
- Access everyday features including message branching, search integrations, image generation, TTS, and model and image-generation favorites.

### Local tools and Web

- Diagnose logs with Authorization, API keys, cookies, URLs, and request-body secrets redacted; selectively export entries and use AI-readable `get_logs`.
- After Android permissions are granted, local tools can read screen time and query or create calendar events.
- The built-in local Web UI listens only on `localhost` by default; enable JWT for LAN access, with an explicit warning when authentication is absent.

## Independent distribution

| Area | RikkaRs |
| --- | --- |
| Maintenance | Independent repository, product roadmap, and release cadence; upstream changes are synchronized as needed |
| Android identity | App name **RikkaRs**, release package `me.arsucar.rikka`; it can coexist with official RikkaHub |
| Distribution | Stable builds are independently maintained and released by Arsucar on [RikkaRs Releases](https://github.com/Arsucar/RikkaRs/releases) |
| Services | Firebase has been removed; `google-services.json` is not required |
| Focus | Agent orchestration, persistent memory, context control, local workspaces, provider/model management, and extensibility |

Implementation and upstream synchronization rules are documented in the [engineering guide](docs/RIKKA_ARSUCAR_FORK_AND_CI.md).

## Download and installation

- **Stable release:** [GitHub Releases — latest](https://github.com/Arsucar/RikkaRs/releases/latest)
- **Minimum system:** Android 8.0 (API 26)
- **Supported ABI:** `arm64-v8a`
- **Release package:** `me.arsucar.rikka`

The independent package allows RikkaRs and official RikkaHub to be installed on the same device. Back up important data before upgrading or switching builds.

## Build and contribute

Open the project with [Android Studio](https://developer.android.com/studio) and JDK 17. The Android app uses Kotlin, Jetpack Compose, Koin, DataStore, Room, Coil, Material You, Navigation 3, OkHttp, and kotlinx.serialization. Firebase is not used, so `google-services.json` is unnecessary. See the [engineering guide](docs/RIKKA_ARSUCAR_FORK_AND_CI.md) for package, CI, release, and upstream synchronization details.

Suitable contributions include focused bug fixes, documentation corrections, and maintainability improvements; open an issue before substantial work. Translation-only changes, unsolicited feature implementations, and large-scale or AI-generated refactors are not accepted.

## License

RikkaRs uses the user-segmented dual-licensing model specified in [LICENSE](LICENSE). Free use under GNU AGPL v3 is available when any one condition applies: use is strictly non-commercial; use is personal, educational, or for research; or the individual or organization has no more than 10 total users. All AGPL obligations, including source availability, still apply.

A commercial license must be obtained in advance for commercial use that directly or indirectly generates commercial benefit, use by more than 10 total users, or exemption from AGPL v3 obligations; contact `re_dev@qq.com`. The project maintainer reserves the right to update the licensing policy and will announce updates through official channels.

This is only a summary. In every case, the original [LICENSE text](LICENSE) is authoritative. Read it in full before using, modifying, or distributing the software.

## Related links

- [Independent RikkaRs repository](https://github.com/Arsucar/RikkaRs)
- [Latest stable release](https://github.com/Arsucar/RikkaRs/releases/latest)
- [All releases](https://github.com/Arsucar/RikkaRs/releases)
- [Changelog](CHANGELOG.md)
- [Engineering guide](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)
- [Upstream RikkaHub](https://github.com/rikkahub/rikkahub)
