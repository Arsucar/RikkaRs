<div align="center">
  <img src="docs/icon.png" alt="RikkaRs App Icon" width="100" />
  <h1>RikkaRs</h1>

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/rikkahub/rikkahub)
[![Ask DeepWiki](https://img.shields.io/badge/zread.ai-blue?style=flat&logo=readthedocs)](https://zread.ai/rikkahub/rikkahub)

A native Android LLM chat client forked from RikkaHub, tuned for agent workflows,
local workspaces, and multi-provider conversations.

[简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_TW.md) | English
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

## About This Fork

This repository is a downstream fork of [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub).
It tracks upstream while publishing an independent Android build named **RikkaRs** from
[Arsucar/rikkahub](https://github.com/Arsucar/rikkahub).

RikkaRs is not the official upstream build. Use upstream RikkaHub if you need upstream release
channels, package identity, or support.

## RikkaHub vs RikkaRs

| Area | Upstream RikkaHub (`rikkahub/rikkahub`) | This fork RikkaRs (`Arsucar/rikkahub`) |
|------|-----------------------------------------|----------------------------------------|
| Release package | `me.rerere.rikkahub` | `me.arsucar.rikka`; debug uses `me.arsucar.rikka.debug` |
| Kotlin namespace | `me.rerere.rikkahub` | Keeps `me.rerere.rikkahub` to reduce upstream merge friction |
| App name | RikkaHub | RikkaRs |
| Release channel | Website and Google Play | GitHub Releases from this fork |
| Firebase | Upstream may use Firebase services | Firebase removed; no `google-services.json` needed |
| CI and releases | Upstream workflow | `Release APK (arm64)` only; arm64 APK, no Firebase, pnpm-backed web build |
| Subagents | Upstream agent/tool behavior | Parallel, queued, delegated subagents with depth/concurrency controls, transcript cards, cancellation, and `finish_work` for subagents |
| Skills | Global skill support | Global and assistant-private Skills, private copy management, safer file scope, slash completion, and richer directory cards |
| Memory | ChatGPT-like memory | Assistant/global memory scopes plus disabled-by-default memory tables with templates, documents, and scope control |
| Workspace | Proot-based workspace | External app-specific workspace storage, full-screen text editing, Markdown read-only preview, dotfile/config detection, and `/tmp` write convenience |
| NewAPI import | Provider QR/import flow | NewAPI `newapi_channel_conn` JSON import alongside QR/provider share payloads |
| Logs and `get_logs` | App logging UI | Logs page export, long-press selective export, AI `get_logs` tool, truncation, and tool-friendly summaries |
| Redacted export | Not a fork focus | Authorization, API keys, cookies, URL secrets, and request-body secrets are redacted for export and `get_logs` |
| Conversation archive | Upstream-supported after merge | Archive support retained with archive-aware search/list behavior |
| Conversation folders | Upstream-supported after merge | Per-assistant folders retained for grouping conversations in the drawer |
| Web access | Embedded web service | Defaults to localhost-only and warns before LAN exposure without JWT |
| Screen time and calendar | Not a core upstream fork focus | Optional local tools for screen usage stats and querying/creating calendar events after permission grants |
| Provider tags | Basic provider settings | `provider.tags` plus provider-setting tag filtering |
| Model picker | Standard model selection | Provider-group collapse, favorite section collapse, expand/collapse all, provider tag filtering, and favorite model grouping |
| Hidden context | Delete/compact behavior | Hide messages as soft deletion; hidden nodes stay visible as excluded context, and compression hides instead of hard-deleting old messages |
| Conversation model override | Assistant model defaults | Per-conversation model override with one-tap clear; new conversations do not inherit old overrides |
| Favorites | Message/favorite foundation | Model favorites, image-generation favorites, favorite collections, and grouped/collapsible favorite views |
| Web localhost + JWT | Configurable web auth | Localhost default plus explicit JWT safety prompts for remote access |
| Engineering notes | Upstream conventions | See [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md) for fork package, CI, release, and Firebase decisions |

## 🚀 Download

🔗 [Download RikkaRs from GitHub Releases](https://github.com/Arsucar/rikkahub/releases)

🔗 Upstream official downloads: [Website](https://rikka-ai.com/download) / [Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

## 💖 Sponsors

|                                         Sponsor                                         | Description                                                                                                                                                                                                                                         |
|:---------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="docs/sponsors/aihubmix.png" alt="Aihubmix" width="50" /><br /><b>Aihubmix</b> | Thanks to <a href="https://aihubmix.com?aff=pG7r">aihubmix.com</a> for their financial support. We recommend using aihubmix as a one-stop shop for mainstream models worldwide. (OpenAI, Claude, Google Gemini, DeepSeek, Qwen, and hundreds more). |
| <img src="docs/sponsors/suixiang.jpg" alt="随想AI中转" width="50" /><br /><b>随想AI中转</b> | 感谢随想AI中转对本项目的赞助！随想AI中转 是一家可靠高效的 API 中继服务提供商，提供 Claude、Codex、Gemini 等的中继服务。注重隐私的中转站·无数据倒卖·无模型掺水，隐私，透明，极速售后。新账户注册每日签到就送 0.5 元测试额度，充值额度 1:1，无需订阅，按量付费。多线路冗余、跨区域容灾、自动故障切换，长链路 SSE 不中断。99.9% 可用性，关键调用从不掉队。 |

## ✨ Features

This list is aligned with this fork's CHANGELOG through **v2.3.19**.

- 🎨 Material You design, predictive back, and dark mode
- 🔄 Multiple provider support with custom API hosts, URLs, headers, request bodies, and model lists
- 🧩 Provider tags, provider tag filtering, NewAPI channel JSON import, and QR provider import/export
- ⭐ Collapsible model picker with provider groups, favorite models, favorite section, expand/collapse all, and tag filters
- 🖼️ Multimodal chat input for images, documents, PDF, DOCX, and common text files
- 📝 Markdown rendering with code highlighting, LaTeX formulas, tables, Mermaid, bold fixes, and rendered read-only Markdown workspace preview
- 🪾 Message branching, message hiding, hidden-context compression, archive, folders, and per-conversation model override
- 📦 Proot workspace with shell/file tools, external workspace storage, full-screen text editor, safer shell policy, and clearer shell transcripts
- 🤖 Agent customization plus subagents with delegation, parallel/queued execution, limits, transcript preview, cancellation, and `finish_work`
- 🛠️ MCP support including OAuth 2.1, token refresh, and reconnect behavior
- 🧠 Memory with assistant/global scopes and disabled-by-default memory tables
- 🧠 Skills library with slash completion, global/private copies, safer file access, and improved Skills directory cards
- 🔍 Search capabilities with Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, and search-result images
- 🖥️ Embedded web access with localhost-only default and JWT warnings for LAN exposure
- 📊 Local diagnostics: Logs page, selective redacted export, and AI-readable `get_logs`
- 📱 Optional local tools for screen time and calendar events after Android permissions are granted
- 📝 AI translation, prompt variables, SillyTavern character card import, assistant avatar crop, and image-generation favorites/collections

## ✨ Contributing

This project is developed using [Android Studio](https://developer.android.com/studio). PRs are
welcome!

Technology stack documentation:

- [Kotlin](https://kotlinlang.org/) (Development language)
- [Koin](https://insert-koin.io/) (Dependency Injection)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI framework)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preference data
  storage)
- [Room](https://developer.android.com/training/data-storage/room) (Database)
- [Coil](https://coil-kt.github.io/coil/) (Image loading)
- [Material You](https://m3.material.io/) (UI design)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (Navigation)
- [Okhttp](https://square.github.io/okhttp/) (HTTP client)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON serialization)

> [!TIP]
> **RikkaRs fork:** Firebase has been removed; you do **not** need `google-services.json`. See [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md).

> [!IMPORTANT]
> The following PRs will be rejected:
> 1. Translation related changes, such as adding new languages or updating existing translations
> 2. Adding new features, this project is opinionated and will not accept pull requests for new features
> 3. Large-scale refactoring and changes generated by AI

## 💰 Donate

* [Patreon](https://patreon.com/rikkahub)
* [爱发电](https://afdian.com/a/reovo)

## ⭐ Star History

If you like RikkaRs, please give this fork a star ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=Arsucar/rikkahub&type=Date)](https://star-history.com/#Arsucar/rikkahub&Date)

## 📄 License

[License](LICENSE)
