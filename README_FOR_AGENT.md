# README FOR AGENT

本文档给后续接手本仓库的 agent 使用，用于快速理解项目结构、构建约束和主要业务链路。

## 项目定位

RikkaHub 是一个原生 Android LLM 聊天客户端，支持多供应商模型接入、多模态输入、Markdown 渲染、搜索、TTS/ASR、MCP、记忆、Prompt 注入、Skills 和内嵌 Web 访问。

仓库路径通常为：

```text
D:\2026Code\Group_android\rikkahub
```

## 技术栈

- Android / Kotlin / Jetpack Compose
- Gradle Kotlin DSL，多模块工程
- Koin 依赖注入
- Room 数据库，DataStore 设置存储
- OkHttp / Retrofit / Ktor
- kotlinx.serialization
- Firebase Analytics / Crashlytics / Remote Config
- React Router + React + TypeScript 的 `web-ui`

## 模块结构

- `app`: 主应用模块，包含 UI、ViewModel、数据仓库、数据库、设置、聊天生成调度、Web API glue 代码。
- `ai`: AI SDK 抽象层，包含 Provider、模型、消息抽象、OpenAI / Google / Claude 实现。
- `search`: 搜索服务 SDK，包含 Exa、Tavily、Zhipu、Bing、Brave、SearXNG 等。
- `speech`: TTS/ASR 相关能力。
- `document`: 文档解析，处理 PDF、DOCX、PPTX、EPUB 等。
- `highlight`: Markdown 代码高亮。
- `material3`: Material 颜色工具扩展。
- `common`: 通用工具与扩展。
- `web`: Android 内嵌 Ktor Web Server 模块，托管 `web-ui` 构建产物。
- `web-ui`: Web 端聊天 UI，React Router + TypeScript。
- `locale-tui`: 本地化维护工具。
- `docs`: 项目图片和文档资源。

## 构建与测试

常用命令：

```powershell
.\gradlew assembleDebug
.\gradlew test
.\gradlew connectedDebugAndroidTest
.\gradlew lint
```

构建注意事项：

- `app/` 下需要提供 `google-services.json`，否则 Firebase 插件相关构建会失败。
- `web` 模块的 `preBuild` 会进入 `web-ui` 执行 `pnpm run build`，并复制静态资源到 `web/src/main/resources/static`。
- 因此完整 Android 构建需要本机可用 `pnpm`，并且 `web-ui` 依赖已安装。
- 当前 App 配置在 `app/build.gradle.kts`：`applicationId=me.rerere.rikkahub`，`minSdk=26`，`compileSdk/targetSdk=37`，`versionName=2.2.6`，`versionCode=162`。

## 入口文件

- `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt`
  - Application 入口。
  - 启动 Koin。
  - 创建通知渠道。
  - 初始化 QuickJS。
  - 清理临时文件。
  - 同步托管文件。
  - 初始化 Firebase Remote Config。
  - 根据设置启动 Web Server。

- `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
  - 主 Activity。
  - 设置 Compose 内容。
  - 初始化 Coil ImageLoader。
  - 管理 Navigation 3 back stack。
  - 注册主要页面路由。
  - 处理分享入口和 PROCESS_TEXT。

## 核心业务模型

- `Assistant`
  - 路径：`app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`
  - 表示一个助手配置。
  - 包含模型、system prompt、头像、temperature、上下文长度、记忆、最近聊天引用、消息模板、preset messages、regex、reasoning、custom headers/body、MCP、本地工具、Skills、时间提醒、Prompt Injection、Lorebook 等。

- `Conversation`
  - 路径：`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`
  - 表示一个持久会话。
  - 通过 `MessageNode` 支持消息分支和重新生成后的候选切换。

- `UIMessage`
  - 路径：`ai/src/main/java/me/rerere/ai/ui/Message.kt`
  - Provider 无关的消息抽象。
  - 支持 text、image、document、audio、video、reasoning、tool call/result 等 parts。
  - Streaming chunk 会合并到 `UIMessage`。

## 聊天生成链路

主要路径：

```text
ChatPage / ChatVM
  -> ChatService
  -> GenerationHandler
  -> ProviderManager
  -> OpenAIProvider / GoogleProvider / ClaudeProvider
```

关键文件：

- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - 管理会话 session。
  - 发送消息、取消生成、保存对话。
  - 维护前后台状态和通知。
  - 协调 transformer、tools、MCP、skills、memory。

- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
  - 构建最终上下文。
  - 应用输入/输出 transformer。
  - 调用 Provider。
  - 处理 tool call、审批、执行结果、循环生成。

- `ai/src/main/java/me/rerere/ai/provider/ProviderManager.kt`
  - 注册默认 provider：`openai`、`google`、`claude`。
  - 根据 `ProviderSetting` 分发到对应实现。

## Message Transformer

输入 transformer 常见包括：

- `TimeReminderTransformer`
- `PromptInjectionTransformer`
- `PlaceholderTransformer`
- `DocumentAsPromptTransformer`
- `OcrTransformer`

输出 transformer 常见包括：

- `ThinkTagTransformer`
- `Base64ImageToLocalFileTransformer`
- `RegexOutputTransformer`

Transformer 位于：

```text
app/src/main/java/me/rerere/rikkahub/data/ai/transformers
```

## 数据层

数据库：

- 路径：`app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
- Room database name：`rikka_hub`
- 当前版本：`20`
- 主要实体：
  - `ConversationEntity`
  - `MessageNodeEntity`
  - `MemoryEntity`
  - `ManagedFileEntity`
  - `FavoriteEntity`
  - `GenMediaEntity`

依赖注入：

- `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`
- `app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt`
- `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt`

## Web Server 与 Web UI

- `web/src/main/java/me/rerere/rikkahub/web/Entry.kt`
  - 启动 Ktor CIO server。
  - 安装 Compression、CORS、SSE、DefaultHeaders。
  - 托管 `static` 资源并支持 SPA。

- `app/src/main/java/me/rerere/rikkahub/web`
  - App 侧 Web API、路由、WebServerManager、NSD 注册等。

- `web-ui/package.json`
  - `pnpm run build`: `react-router build && tsx copy.ts`
  - `pnpm run dev`: Web UI 开发模式。

## 本地化

Android 字符串资源主要在：

```text
app/src/main/res/values*/strings.xml
search/src/main/res/values*/strings.xml
```

已有 locale 包括：

- default
- `zh`
- `zh-rTW`
- `ja`
- `ko-rKR`
- `ru`

如果用户要求本地化更新，优先使用仓库内 `locale-tui` 工作流和对应 agent skill。

## 开发约定

- 遵循 `.editorconfig`：
  - Kotlin / Gradle：4 空格缩进，最大行长 120。
  - XML / JSON：2 空格缩进。
  - Markdown / YAML：2 空格缩进。
- Kotlin 类使用 PascalCase。
- 测试类以 `*Test.kt` 结尾。
- Compose 文案通常应使用 `stringResource(R.string.xxx)`，但如果用户未要求本地化，可以先按功能实现。

## 当前阅读时观察到的状态

- 工作区有未跟踪目录：`.omc/`。
- `.omc/project-memory.json` 是本地工具生成的项目记忆，部分识别不准确：
  - 将包管理器识别为 `npm`，但实际 `web-ui` 构建使用 `pnpm`。
  - 将项目识别为非 monorepo，但实际是多模块 Gradle 工程。
- 本轮阅读未运行 Gradle 构建、测试或 lint。

## 后续改动定位建议

- 改 Android UI：优先看 `app/src/main/java/me/rerere/rikkahub/ui/pages` 和 `app/src/main/java/me/rerere/rikkahub/ui/components`。
- 改聊天发送、生成、工具调用：优先看 `ChatService.kt` 和 `GenerationHandler.kt`。
- 改 Provider 协议转换：优先看 `ai/src/main/java/me/rerere/ai/provider/providers`。
- 改搜索能力：看 `search` 模块。
- 改 TTS/ASR：看 `speech` 模块。
- 改文档解析：看 `document` 模块。
- 改 Web 访问/API：看 `web` 模块、`web-ui`、以及 `app/src/main/java/me/rerere/rikkahub/web`。
