# Repository Guidelines

本文档面向贡献者，概述本仓库的模块结构、开发流程，便于快速上手并保持一致的协作质量。

## Build, Test, and Development Commands

使用 Android Studio 或命令行 Gradle：

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew connectedDebugAndroidTest  # 运行设备/模拟器上的仪器测试
./gradlew lint                   # 运行 Android Lint
```

下游 **Rikka-arsucar** fork 已移除 Firebase，**不需要** `google-services.json`。
`web` 模块会在 `preBuild` 阶段构建 `web-ui/` 并复制静态资源，需要本地可用 `pnpm`（首次在 `web-ui/` 执行 `pnpm install`）。

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

## Rikka-arsucar 下游：提交与发版（Agent 必读）

完整说明见 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`。本 fork **不向** `rikkahub/rikkahub` 上游提 PR。

| 项 | 约定 |
|----|------|
| 发行分支 | `release/rikka-arsucar` |
| applicationId | release `me.arsucar.rikka`，debug `me.arsucar.rikka.debug` |
| 本地构建 | **仅** `./gradlew assembleDebug`（Windows：`gradlew.bat assembleDebug`） |
| 正式 APK | **仅 CI**（`.github/workflows/release-apk.yml`），本地不要 `assembleRelease` 发版 |

### 提交代码

1. 在 `release/rikka-arsucar` 上改代码并验证 Debug 构建。
2. **不要提交**：`*.jks`、`local.properties`、`.omc/`（已在 `.gitignore`）。
3. 提交并推送：
   ```bash
   git add <改动的业务/workflow/文档文件>
   git commit -m "feat|fix|chore: ..."
   git push origin release/rikka-arsucar
   ```

### 触发 CI 发版

Workflow：**Release APK (arm64)**（`release-apk.yml`）。

| 触发方式 | 行为 |
|----------|------|
| 推送标签 `v*`（如 `v2.3.2`） | 用当前 `app/build.gradle.kts` 版本构建 signed arm64 APK；创建 **GitHub Release** 并附 `rikka-arsucar-<tag>-arm64.apk`；保留 Actions Artifact |
| Actions 页 **Run workflow**（`workflow_dispatch`） | 构建前 **自动 bump** `versionCode` / `versionName`（patch +1），成功后 **commit + push** 版本号；**不**创建 GitHub Release（仅 Artifact） |

**重打同一标签以重新构建并发布 Release**（标签需指向含最新 workflow 的提交）：

```bash
git checkout release/rikka-arsucar
git pull --ff-only origin release/rikka-arsucar
git tag -d v2.3.2
git push origin :refs/tags/v2.3.2
git tag -a v2.3.2 -m "Rikka-arsucar 2.3.2"
git push origin v2.3.2
```

可选（需已安装并登录 `gh`）：

```bash
gh workflow run "Release APK (arm64)" --ref release/rikka-arsucar
```

### 仓库与密钥（人工一次）

- Fork：`origin` → `Arsucar/rikkahub`；上游 `upstream` → `rikkahub/rikkahub`。
- Actions Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- Settings → Actions → General → Workflow permissions：**Read and write**。
- CI checkout 需 **子模块** `material3/material-color-utilities`（workflow 已 `submodules: recursive`）。

### 下载产物

- **Releases** 页：仅 **tag 触发** 且构建成功后有 APK。
- **Actions** → 某次运行 → **Artifacts**：任意成功构建均可下载（含 `workflow_dispatch`）。

已成功构建的 Artifact APK 为 **已签名 release**，可直接安装分发；同 keystore 的后续 release 可覆盖升级。

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
