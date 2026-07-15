# Issue #135：助手级网络搜索持久化与 Workspace 媒体预览

## Goal

在保留 RikkaRs 当前生成请求、工具构建与 Workspace 文本/Markdown 能力边界的前提下，选择性整合上游的两项能力：让每个 Assistant 独立持久化网络搜索开关，并为 Workspace 增加安全的图片内置预览及其他文件外部打开能力。

## User Value

- 不同助手可按用途独立启用或关闭网络搜索，升级与重启后配置不会丢失或互相覆盖。
- Workspace 用户可直接预览图片，并把视频或其他文件安全交给系统应用打开。
- 现有文本、Markdown 编辑/预览以及 LINUX 区只读行为不发生回归。

## Requirements

### Assistant 网络搜索

- 网络搜索状态由 Assistant 自身持久化，不再以全局 Settings 值作为运行时来源。
- Assistant 创建与编辑界面可设置该状态；全局 Settings 不再展示对应开关。
- 从仍保存全局 `enable_web_search` 的旧版本升级时，将旧布尔值一次性、原子地应用到所有已有 Assistant。
- 迁移成功后不得在后续启动中再次覆盖用户对单个 Assistant 的修改；迁移失败不得留下部分更新状态，并允许安全重试。
- 没有现有 Assistant 时迁移仍可完成；旧格式或缺失字段采用兼容默认值；新建 Assistant 使用与现有产品默认行为兼容的明确默认值。
- 多 Assistant 编辑只能更新目标 Assistant，不得影响其他 Assistant。
- 生成请求必须继续经过现有 `PreparedGenerationRequest`，工具必须继续由现有 `buildGenerationTools` 组装；网络搜索工具是否加入由当前 Assistant 的持久化值决定。
- 网络失败、工具不可用或空结果继续沿用现有生成链路的错误处理。

### Workspace 媒体处理

- 文本与 Markdown 继续使用当前 `TextFileUtil` 以及现有编辑/预览入口。
- 图片点击后在当前 Workspace 导航上下文中展示现有风格的 `ImagePreviewDialog`，关闭后回到原文件列表。
- 视频及其他非文本文件通过 `FileProvider` 生成 `content://` URI，并用 `ACTION_VIEW`、正确 MIME type 和临时只读权限交给系统应用。
- 文件不存在、路径无效、图片损坏、MIME 未知、无匹配应用或 URI 授权失败时不得崩溃，并提供明确、可本地化的即时反馈。
- 外部应用返回后 Workspace 状态应保持；本地媒体预览不依赖网络。
- LINUX 区只能读取、预览或外部打开，不展示或启用编辑、保存等写入操作，也不得产生文件写入。
- 不新增或移植职责重复的 `WorkspaceFileEditorPage`、`readTextForPreview` 或平行的 Workspace 文本模型/缓存。
- 不为本功能单独引入新的 Analytics 体系，也不得记录搜索内容、文件内容或敏感路径。

## Acceptance Criteria

- [ ] AC1：全局 Settings 不再展示网络搜索开关；每个 Assistant 的创建/编辑界面均可独立设置网络搜索，重启后值保持不变。
- [ ] AC2：旧全局 `enable_web_search=true/false` 一次性、原子地迁移到所有现有 Assistant；迁移完成后再次启动不会覆盖助手级修改。
- [ ] AC3：不同 Assistant 发起生成时，是否构建网络搜索工具由各自配置决定，并继续使用 `PreparedGenerationRequest` / `buildGenerationTools` 架构。
- [ ] AC4：现有文本及 Markdown 编辑/预览行为保持不变，继续使用 `TextFileUtil`；代码中没有新增 `WorkspaceFileEditorPage` / `readTextForPreview`。
- [ ] AC5：Workspace 图片可通过 `ImagePreviewDialog` 预览，覆盖成功、文件不存在、解码失败和深色模式路径。
- [ ] AC6：视频和其他文件通过 `FileProvider` + `ACTION_VIEW` 打开，仅使用 content URI 与临时只读授权；无可处理应用或授权失败时提示且不崩溃。
- [ ] AC7：LINUX 区保持只读，不出现可用编辑/保存入口，也不会产生文件写入。
- [ ] AC8：迁移、助手级工具启用判断、文件类型分流及异常路径具有自动化测试，现有生成与 Workspace 文本/Markdown 测试无回归。
- [ ] AC9：相关 Compose UI 使用项目现有组件、主题、触控与无障碍约定；新增用户可见文本提供默认资源与简体中文翻译。
- [ ] AC10：目标单测、`:app:compileDebugKotlin`、`git diff --check` 通过；设备可用时完成 `:app:installDebug` 安装验收。

## Out of Scope

- 整段替换当前 generation 请求准备或工具构建实现。
- 引入重复的 Workspace 文本编辑页面或文本读取 helper。
- 在应用内实现视频播放器或通用文件查看器。
- 为本功能新增独立埋点体系、Deep link 或特殊转场动画。

## Source

- GitHub Issue：<https://github.com/Arsucar/RikkaRs/issues/135>
- 目标分支：`release/rikka-arsucar`
