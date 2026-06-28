# Repository Guidelines

本文档面向贡献者，概述本仓库的模块结构、开发流程，便于快速上手并保持一致的协作质量。

## Build, Test, and Development Commands

使用 Android Studio 或命令行 Gradle：

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew :app:installDebug      # 构建并安装 Debug 到已连接设备/模拟器（见下文「本地验证与装到设备」）
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew connectedDebugAndroidTest  # 运行设备/模拟器上的仪器测试（用户未要求时不要默认跑）
./gradlew lint                   # 运行 Android Lint
```

Rikka-arsucar fork **不需要** `google-services.json`（已移除 Firebase）。
`web` 模块会在 `preBuild` 阶段构建 `web-ui/` 并复制静态资源，需要本地可用 `pnpm`。

## 本地验证与装到设备

用户说「装到手机/设备」「真机验证」「改完安装」，或完成 **app 模块**功能改动且未明确只要编译时，助手应执行安装验收（Windows 下同样用 `.\gradlew`）：

1. 确认设备：`adb devices`（至少一台状态为 `device`；无设备则说明情况并只做编译）。
2. 默认：`.\gradlew :app:installDebug`（assemble + adb install）。Debug 包名一般为 `me.arsucar.rikka.debug`。
3. 用户只要快速编译、不要装包：`.\gradlew :app:compileDebugKotlin`。
4. 安装失败：汇报 Gradle/adb 末尾错误；常见为无设备、签名冲突、需先卸载旧包。

不要默认跑 `connectedDebugAndroidTest`。

## AI 改文件（Edit 工具）

- 参数必须用 camelCase：`filePath`、`oldString`、`newString`。
- 禁止 `old_string` / `new_string` 等 snake_case；禁止漏传 `oldString` 或 `newString`。
- 每次 Edit 前用 Read 核对片段；`oldString` 须与文件原文一致（含缩进）。

## Coding Style & Naming Conventions

本仓库使用 `.editorconfig` 统一格式：

- Kotlin/Gradle 脚本：4 空格缩进，最大行长 120。
- XML/JSON：2 空格缩进。
- Markdown/YAML：2 空格缩进，允许尾随空格（用于对齐）。

命名习惯：模块名为小写目录（如 `ai/`、`speech/`），Kotlin 类遵循 PascalCase，测试类以 `*Test` 结尾。

## Testing Guidelines

测试框架以 JUnit/AndroidX Test 为主。未设定强制覆盖率门槛，但新逻辑应配套新增/更新测试。测试文件命名建议：

- 单元测试：`FooTest.kt`
- 仪器测试：`FooInstrumentedTest.kt` 或 `*Test.kt`

## Git Commit and Upstream PR Rules

- Keep `master` clean and aligned with `origin/master` for upstream contributions.
- Do not open an upstream PR from local setup branches such as `local/agent-trellis-setup`.
- Local agent/Trellis files are for this workspace only unless the user explicitly asks to contribute them upstream:
  `.agents/`, `.codex/`, `.omc/`, `.trellis/`, `README_FOR_AGENT.md`, and Trellis-only changes in `AGENTS.md`.
- When preparing an upstream PR, start from a clean upstream base:
  ```bash
  git switch master
  git pull --ff-only
  git switch -c feat/<change-name>
  ```
- Before pushing or opening a PR, verify the PR diff does not include local tooling files:
  ```bash
  git diff --name-only origin/master...HEAD
  git diff --stat origin/master...HEAD
  ```
- If local agent setup needs to be saved, commit it only on a dedicated local branch and do not push that branch unless
  the user explicitly confirms it is intended for the remote.

## Rikka-arsucar：提交与发包

在分支 `release/rikka-arsucar` 上开发；勿提交 `*.jks`、`.omc/`。

**正式发版 workflow 名称仅为 `Release APK (arm64)`**（`.github/workflows/release-apk.yml`）。**禁止**使用上游遗留的 `Release Build` workflow（已删除；无 Firebase、无 submodule/pnpm，不适用于本 fork）。

```bash
git add <文件> && git commit -m "…" && git push origin release/rikka-arsucar
```

发 arm64 正式包有两种路径（方案 B，详见 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`）：

**路径 A — 推送 tag（会创建 GitHub Release + arm64 APK，CI 不 bump 版本）**

1. 先更新 `CHANGELOG.md`。
2. 确保 `app/build.gradle.kts` 中 `versionName` 与 tag 一致（如 tag `v2.3.6` → `versionName = "2.3.6"`），`versionCode` 已递增并已 push。
3. 打标签并推送：

```bash
git tag -a vX.Y.Z -m "…" && git push origin vX.Y.Z
```

**路径 B — `workflow_dispatch`（自动 bump `versionCode` / `versionName` 并 push commit）**

```bash
gh workflow run "Release APK (arm64)" --ref release/rikka-arsucar
```

**发版前必须先更新 `CHANGELOG.md`**（见下文「更新日志维护」）。tag 路径不会在 CI 中改版本号；dispatch 路径会在构建成功后自动 bump 并 push。

重打标签：先 `git push origin :refs/tags/vX.Y.Z` 删远程标签，再重新 `tag` + `push`。

本机已配置 **`gh`（GitHub CLI）**，可用其操作 Actions / Release 等。

更多见 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`。

### 更新日志维护

发版时更新 `CHANGELOG.md` — 流程和格式见 `docs/CHANGELOG_GUIDE.md`。

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.
<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

AI assistants should actively follow the Trellis workflow in this repository.

If a Trellis command is available on your platform, use it instead of manual steps. Examples include `/trellis:continue` and `/trellis:finish-work`.

If Trellis commands are not available, manually follow `.trellis/workflow.md` instead of skipping Trellis.

`.trellis/` may be read and used during local development, but should not be included in upstream PRs unless explicitly requested by the user.


If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->
