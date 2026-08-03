# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Architecture Overview

### Module Structure

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

### Key Technologies

- **Jetpack Compose**: Modern UI toolkit
- **Koin**: Dependency injection
- **Room**: Database ORM
- **DataStore**: Preferences storage
- **OkHttp**: HTTP client with SSE support
- **Navigation 3**: App navigation
- **Kotlinx Serialization**: JSON handling

### Core Packages (app module)

- `data/`: Data layer with repositories, database entities, and API clients
- `ui/pages/`: Screen implementations and ViewModels
- `ui/components/`: Reusable UI components
- `di/`: Dependency injection modules
- `utils/`: Utility functions and extensions

### Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT, SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables users to regenerate responses and switch between different conversation branches, creating a tree-like conversation structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Build, Test, and Development Commands

使用 Android Studio 或命令行 Gradle：

```bash
./gradlew --no-daemon assembleDebug          # 构建 Debug APK
./gradlew --no-daemon :app:installDebug      # 构建并安装 Debug 到已连接设备/模拟器（见下文「本地验证与装到设备」）
./gradlew --no-daemon test                   # 运行所有模块的 JVM 单元测试
./gradlew --no-daemon connectedDebugAndroidTest  # 运行设备/模拟器上的仪器测试（用户未要求时不要默认跑）
./gradlew --no-daemon lint                   # 运行 Android Lint
```

Rikka-arsucar fork **不需要** `google-services.json`（已移除 Firebase）。
`web` 模块会在 `preBuild` 阶段构建 `web-ui/` 并复制静态资源，需要本地可用 `pnpm`。

## AI 命令约束

- Trellis skill 文件优先读仓库内 `.agents/skills/<skill>/SKILL.md`，不要尝试全局路径 `C:/Users/Administrator/.codex/skills/.system/trellis-*`。
- 查看任务用 `python ./.trellis/scripts/get_context.py`；列任务用 `Get-ChildItem .trellis/tasks -Directory | Where-Object { $_.Name -ne 'archive' }`。
- Windows 下若 `rg` 不存在，改用 `Get-ChildItem -Recurse -File` / `Select-String`；排除 archive 用 `-notlike '*\archive\*'`，不要用易刷屏的正则 `-notmatch '\archive\'`。
- PowerShell 字符串里变量后紧跟冒号时写 `$($name):` 或 `${name}:`，不要写 `$name:`，避免触发变量作用域解析错误。
- PowerShell 多路径递归用 `Get-ChildItem -Path @('path1','path2') -Recurse -File`，不要写 `Get-ChildItem path1 path2 -Recurse`。

### 子代理与编译调度

- 处理任务时先评估是否可拆分派发；代码编辑问题绝对优先使用子代理解决，非极简任务不得由主代理独自硬扛。
- 代码检索、文件定位、影响面分析等任务直接调用子代理；多步骤且可并行的任务应拆分后并行调用多个子代理。
- 主代理只在极简修改、无法派发或最终整合收尾时直接操作；若主代理执行最后一步且包含源代码修改，最后必须执行「本地验证与装到设备」流程。
- 多个子代理并行时，只有最后一个检查子代理允许运行 Gradle 编译、测试或 lint；其他实现/审查子代理只做代码修改、检索或静态审查，避免主机内存耗尽。
- 所有 Gradle 编译、测试、lint、安装命令必须带 `--no-daemon`，避免守护进程堵塞或残留。

## 本地验证与装到设备

用户说「装到手机/设备」「真机验证」「改完安装」，或完成 **app 模块**功能改动且未明确只要编译时，助手应执行安装验收（Windows 下同样用 `.\gradlew`）：

1. 确认设备：先执行 `adb devices`；若没有状态为 `device` 的设备，先执行 `adb connect 100.99.129.110:5555`，再重新执行 `adb devices`。
2. 连接后仍没有状态为 `device` 的设备：说明情况并只做编译，不执行安装。
3. 用户只要快速编译、不要装包：`.\gradlew --no-daemon :app:compileDebugKotlin`。
4. 有可用设备时默认执行：`.\gradlew --no-daemon :app:installDebug`（assemble + adb install）。Debug 包名一般为 `me.arsucar.rikka.debug`。
5. 安装失败时先执行 `adb connect 100.99.129.110:5555` 重新连接固定端口，再重试 `.\gradlew --no-daemon :app:installDebug` 一次。
6. 重试仍失败：汇报 Gradle/adb 末尾错误；常见为无设备、签名冲突、需先卸载旧包。

不要默认跑 `connectedDebugAndroidTest`。

## AI 改文件（Edit 工具）

- Claude Code 的 Edit 工具参数用 snake_case：`file_path`、`old_string`、`new_string`（`replace_all` 可选）。
- 每次 Edit 前先用 Read 核对片段；`old_string` 须与文件原文完全一致（含缩进），否则会失败。
- 需替换全部匹配时用 `replace_all: true`，不要用不唯一的 `old_string`。

> 注：Codex（AGENTS.md）使用 camelCase 参数 `filePath`/`oldString`/`newString`；本节已按 Claude Code 约定改写，勿混用。

## Development Guidelines

### UI Development

- Follow Material Design 3 principles
- Use existing UI components from `ui/components/`
- Reference `SettingProviderPage.kt` for page layout patterns
- Use `FormItem` for consistent form layouts
- Implement proper state management with ViewModels
- Use `Lucide.XXX` for icons, and import `import com.composables.icons.lucide.XXX` for each icon
- Use `LocalToaster.current` for toast messages

### Coding Style & Naming Conventions

本仓库使用 `.editorconfig` 统一格式：

- Kotlin/Gradle 脚本：4 空格缩进，最大行长 120。
- XML/JSON：2 空格缩进。
- Markdown/YAML：2 空格缩进，允许尾随空格（用于对齐）。

命名习惯：模块名为小写目录（如 `ai/`、`speech/`），Kotlin 类遵循 PascalCase，测试类以 `*Test` 结尾。

### Testing Guidelines

测试框架以 JUnit/AndroidX Test 为主。未设定强制覆盖率门槛，但新逻辑应配套新增/更新测试。测试文件命名建议：

- 单元测试：`FooTest.kt`
- 仪器测试：`FooInstrumentedTest.kt` 或 `*Test.kt`

### Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- If the user explicitly requests localization, all languages should be supported.
- English(en) is the default language. Chinese(zh), Japanese(ja), Traditional Chinese(zh-rTW), Korean(ko-rKR), and
  Russian(ru) are supported.
- When localization is needed, use the `locale-tui-localization` skill for managing string resources.

## Git Commit and Remote Rules

- Do not open pull requests to the upstream repository. Development for this fork is pushed to the user's own `origin`.
- Trellis task artifacts under `.trellis/tasks/` may be committed and pushed to `origin` with the work they document.
- Local agent setup files remain workspace-only unless the user explicitly asks to push them:
  `.agents/`, `.codex/`, `.omc/`, `README_FOR_AGENT.md`, and agent-only changes in `AGENTS.md`.

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

本机已配置 **`gh`（GitHub CLI）**，可用其操作 Actions / Release 等；关 issue / 评论请用终端 `gh issue close` / `gh issue comment`，勿依赖 MCP 的 `github_*` 工具（token 常无 issue 写权限）。

### GitHub Issue 关闭评论规范

- 关闭 issue 前，必须发布完整中文交付评论，至少包含 `解决点`、`验证`、`定位`、`已知边界`。
- 中文评论必须写明目标分支、修复提交、正式版本（若已发布），以及实际执行的测试、编译或安装结果。
- 不得使用一份通用验证模板批量覆盖不同 issue；每条评论必须对应其真实实现、验证证据和边界。
- 中文评论后必须另发一条独立英文版，且事实、提交、版本、验证结果和已知边界与中文版一致。
- 重复 issue 也必须说明主 issue、共享修复提交、验证结果和后续追踪边界。
- 未执行或未通过的安装、测试、lint 必须如实说明，不得描述为成功。
- **验证勾选清单**：`验证` 段必须把 issue 原文的「验收条件」和/或「实现 Checklist」**逐条复制为 `- [x]` / `- [ ]`**，对照真实证据勾选；未跑、未通过、未验证项保持 `- [ ]` 并在「已知边界」注明原因。不得只写一句「测试通过」代替逐项勾选，也不得无证据批量打勾。
- 关闭 issue 时一并写明关联 PR（`关联 PR：…`）与主线修复提交 SHA；若该提交来自直推而非 PR，注明 `直推 + superseded PR #…`。
- 跨 issue 复核（非本次关闭）同样发中英勾选评论，引用复核提交 SHA 与本轮实际执行的验证命令结果；已关 issue 追加评论而非重开，除非发现新 HIGH/CRITICAL 回归。
- 使用 `gh issue comment` / `gh api` 操作；完成后重新读取 issue 评论，确认中文评论已更新且英文评论已单独发布。

更多见 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`。

### 更新日志维护

发版时更新 `CHANGELOG.md` — 流程和格式见 `docs/CHANGELOG_GUIDE.md`。
