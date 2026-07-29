# Repository Guidelines

本文档面向贡献者，概述本仓库的模块结构、开发流程，便于快速上手并保持一致的协作质量。

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
- 目标明确、风险较低且通常只修改 1–2 个文件的小任务，默认不创建 Trellis 任务，也不再询问用户是否创建；直接修改并做相称验证。新功能、复杂行为变更、跨模块修改或需要方案取舍的工作仍应按 Trellis 流程先征得建任务同意。
- Windows 下若 `rg` 不存在，改用 `Get-ChildItem -Recurse -File` / `Select-String`；排除 archive 用 `-notlike '*\archive\*'`，不要用易刷屏的正则 `-notmatch '\archive\'`。
- PowerShell 字符串里变量后紧跟冒号时写 `$($name):` 或 `${name}:`，不要写 `$name:`，避免触发变量作用域解析错误。
- PowerShell 多路径递归用 `Get-ChildItem -Path @('path1','path2') -Recurse -File`，不要写 `Get-ChildItem path1 path2 -Recurse`。

### 子代理与编译调度

- 不要在探索前给任务贴“简单/中等/复杂”标签，也不要根据这种预判限制子代理数量。派发数量由当前已经识别出的独立探索或核验问题决定，上限仅受平台并发能力约束。
- 每个子代理必须对应一个可独立回答的问题，并且至少满足一项：减少主线程宽重读取、与其他探索并行、提供真正独立的核验。同一问题不得重复派发，除非存在明确的安全或数据一致性风险。
- 首轮审查应一次性派发当前已知的独立问题域；修复后的复核只检查本轮 diff、首轮发现和修复新跨越的边界。只有出现新的独立问题域或 HIGH/CRITICAL 证据时才增加审查，不按预设轮数机械继续或停止。
- 子代理返回空结果、只复述任务或未执行核验时最多重派一次；再次无实质结果后由主代理直接完成，不继续重试。
- 主代理负责方案取舍、确切代码修改和最终整合；已知位置的小文件、即将修改的具体代码和单一事实直接处理，不为满足“使用子代理”而派发。
- 多个子代理并行时，只有最后一个检查子代理允许运行 Gradle 编译、测试或 lint；其他实现/审查子代理只做代码修改、检索或静态审查，避免主机内存耗尽。
- 所有 Gradle 编译、测试、lint、安装命令必须带 `--no-daemon`，避免守护进程堵塞或残留。
- 普通 app 功能修改在代码冻结前只运行聚焦测试；完成主要修复后，再把资源处理、Kotlin 编译、完整 JVM 单测和 AndroidTest 源码编译合并到一次最终 Gradle 调用。
- 不按任务等级预设 Gradle 启动次数；每次启动必须回答一个此前没有证据覆盖的验证问题。能合并的资源处理、编译、JVM 单测和 AndroidTest 源码编译应合并，相关生产代码没有行为变化时不得重复等价验证。注释、任务文档和测试代码修改不使已通过的生产代码验证失效。
- 本仓库规则优先于通用 skill 的“运行 lint”建议。不要为普通任务默认运行全量 `:app:lintDebug`；仅在正式发版、Manifest/Gradle/SDK/API/大范围 Compose 或资源等 lint 高风险修改、lint 基线治理、定期质量检查，或用户明确要求时运行。
- 已知全量 lint 存在历史基线失败时，优先读取/过滤已有报告或检查改动文件；每轮最多运行一次 lint，确认仅为历史基线问题后不得重复运行或顺手治理基线。

### 审查收敛与范围控制

- 用户要求“审查变更，如有问题优化”时，必须修复：编译错误、数据丢失、并发覆盖、安全问题、明确行为回归和最终验收契约冲突。
- 有清晰复现路径且修改范围可控的 MEDIUM 问题应当修复；纯风格、命名、可选抽象、低收益清理和没有直接证据的未来风险默认只记录，不得因此重新打开修复循环。
- 只有新发现的 HIGH/CRITICAL 问题可以触发额外“修改 → 验证 → 复核”循环；LOW/MEDIUM 清理不得触发全量重测、重新安装或新一轮全仓审查。
- 实现前只做一次需求归一化：明确 PRD、design、implement 和用户最新决定之间的最终权威关系。标记为“已推翻/最终定调”的决定覆盖旧计划，后续不得把被推翻的要求重新纳入验收。
- 每次准备新增验证、复核或重构前，先检查它是否提供新的证据；与已通过检查等价、覆盖范围更窄或仅重复确认的操作应跳过。
- 不根据“任务复杂度”预估流程或设置硬时限。墙钟时间达到 20 分钟时检查是否在重复审查、重复 Gradle、处理可选问题或让收尾重构使既有验证失效；达到 45 分钟时向用户说明已完成、剩余和耗时来源，停止可选优化，但不得因此跳过实现正确性所需的工作。
- 代码冻结后不再做非必要生产代码重构。若收尾阶段仅发现命名、注释或可选 helper 提取，记录到后续任务，不得为了它使测试或设备安装失效。

## 本地验证与装到设备

用户说「装到手机/设备」「真机验证」「改完安装」，或完成 **app 模块**功能改动且未明确只要编译时，助手应执行安装验收（Windows 下同样用 `.\gradlew`）：

安装只在生产代码、资源和导航已经冻结，且最终测试/编译完成后执行；开发过程中不要安装中间构建。安装成功后若只修改测试、文档或注释，不重新安装。

1. 确认设备：先执行 `adb devices`；若没有状态为 `device` 的设备，先执行 `adb connect 100.99.129.110:5555`，再重新执行 `adb devices`。
2. 连接后仍没有状态为 `device` 的设备：停止重复重连，执行 `.\gradlew --no-daemon :app:assembleDebug`，将 `app/build/outputs/apk/debug/app-debug.apk` 上传到 GoFile；GoFile 不可用时可改用其他免费临时文件渠道。向用户返回下载链接、SHA-256、对应 commit、包名 `me.arsucar.rikka.debug` 和人工验收清单。上传失败时如实报告本地 APK 路径和错误，不得声称已安装或通过真机验证。
3. 用户只要快速编译、不要装包：`.\gradlew --no-daemon :app:compileDebugKotlin`。
4. 有可用设备时默认执行：`.\gradlew --no-daemon :app:installDebug`（assemble + adb install）。Debug 包名一般为 `me.arsucar.rikka.debug`。
5. 安装失败时先执行 `adb connect 100.99.129.110:5555` 重新连接固定端口，再重试 `.\gradlew --no-daemon :app:installDebug` 一次。
6. 重试仍失败：汇报 Gradle/adb 末尾错误；常见为无设备、签名冲突、需先卸载旧包。
7. `installDebug` 加一次重试是单轮安装总预算；不得再调用直接 `adb install` 形成第三次隐式重试。安装或传输连续 120 秒无输出可判定为挂起，终止孤儿 Gradle/adb 子进程后按真实失败结果汇报。

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
- 使用 `gh issue comment` / `gh api` 操作；完成后重新读取 issue 评论，确认中文评论已更新且英文评论已单独发布。

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

Trellis task artifacts under `.trellis/tasks/` may be committed and pushed to the user's own remote repository.


If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->
