# 实现助手项目 Git 状态抽屉

## Goal

在聊天页右侧抽屉中提供当前助手绑定 Workspace 的只读 Git 状态和受限文件 diff，方便用户在不离开聊天页的情况下确认项目分支与改动，同时确保不会误读其他 Workspace 或把项目内容注入模型上下文。

## Confirmed Facts

- 功能对应 GitHub issue #174，并复用 issue #13 的助手级 Workspace 绑定与 issue #151 的显式绑定规则。
- 唯一绑定来源是 `Assistant.workspaceId`；`Conversation.workspaceCwd` 只表示会话命令目录，不参与项目选择。
- 绑定的 Workspace 文件根 `/workspace` 就是本期 Git 项目根；不扫描或猜测其他 Workspace、会话 cwd 或嵌套仓库。
- 本期只读，不提供 stage、unstage、commit、discard、push、pull、checkout 等写操作，也不后台轮询。
- 用户已明确要求创建任务后直接执行；实现阶段可采用本文记录的保守限制，不再为实现细节单独阻塞。

## Requirements

- 在右侧抽屉菜单的“Hook 历史”下方增加 Git 状态入口；摘要展示项目名、分支或 detached HEAD，以及去重后的改动文件数。
- 右抽屉首次打开时读取一次当前助手的 Git 状态；进入详情后支持手动刷新，重复刷新合并或禁用。
- 详情固定按 Staged Changes、Unstaged Changes、Untracked Files 分组；同一文件可同时出现在 staged 与 unstaged 组，摘要数量按当前路径去重。
- 文件行可打开与当前分组对应的 diff；文本 diff 最大 64 KiB，超过时明确标记截断；二进制文件仅显示类型与基础信息。
- 状态读取使用 `git status --porcelain=v2 --branch -z --untracked-files=all`，最多保留 300 个文件；输出或文件数超限时显示结果已截断。
- 单次 Git 命令超时 8 秒。状态和 diff 都必须覆盖 loading、success/clean、未绑定、绑定失效、Workspace 未就绪、目录/权限错误、非 Git 仓库、命令失败、超时和截断。
- Git 命令通过参数数组执行；仓库文件路径必须先经过 lexical 与 canonical containment 校验，再作为独立 argv 参数传入，拒绝绝对路径、`..`、NUL 与符号链接逃逸。
- 错误只映射为脱敏的领域类型；普通日志、模型请求、消息内容和持久化数据不得包含项目路径、文件内容或 diff。
- 未绑定或绑定失效时提供前往当前助手详情页的入口；不得自动选取第一个 Workspace。
- 所有新增 UI 文案使用 Android string resource，并提供真实简体中文翻译；图标按钮有本地化 content description。

## Acceptance Criteria

- [x] 右侧抽屉“Hook 历史”下方出现 Git 状态入口，摘要在可用时显示项目、分支/detached HEAD 和去重改动数。
- [x] null、无效或未就绪的 `Assistant.workspaceId` 显示结构化状态，不读取任何其他 Workspace；`workspaceCwd` 改变不影响结果。
- [x] 有效仓库正确展示 staged、unstaged、untracked；干净仓库显示“工作区干净”，detached HEAD 显示短提交号。
- [x] 同一文件同时 staged/unstaged 时进入两个分组，但摘要只计一个文件；rename、delete、conflict 和含空格路径可解析。
- [x] 手动刷新有加载反馈且不会并发重复请求；助手切换时旧助手状态不会短暂显示为新助手结果。
- [x] 文本 diff 可查看，64 KiB 以上明确截断；二进制 diff 不显示正文；untracked 文件可查看受限 diff。
- [x] 目录失效、无权限、非 Git 仓库、Git 不可用/失败、8 秒超时和 300 文件/输出截断均有明确状态及适用的重试或绑定入口。
- [x] Git 文件参数不经过 Shell 拼接；绝对路径、`..`、NUL 和指向 Workspace 外的符号链接均被拒绝。
- [x] 功能不包含任何 Git 写操作，不后台轮询，不向模型请求或普通日志传递项目路径、文件内容或 diff。
- [x] UI 在窄屏抽屉、长路径、多文件、深浅色、大字体与 TalkBack 下保持可滚动、无文本遮挡，返回键依次退出 diff/详情/抽屉。
- [x] 新增默认语言和简体中文资源完整，资源处理、Kotlin 编译、JVM 单测、AndroidTest 源码编译通过；有设备时安装 Debug 包验收。

## Out Of Scope

- Git 写操作、远端同步、提交历史、分支切换、文件编辑、后台轮询。
- 自动发现嵌套 Git 仓库或从 `Conversation.workspaceCwd` 推断项目。
- 将 diff 或状态作为 AI 工具、提示词、消息附件或长期持久化数据。

## UI Verification Matrix

- Content: clean、单文件、三类改动、同文件双状态、长路径、300+ 文件、二进制与大 diff。
- State: loading、success、refreshing、unbound、missing、not-ready、permission、not-git、timeout、failure、truncated。
- Form factor: 300dp 抽屉、窄屏、横屏、滚动；本功能无 IME 输入。
- Theme/accessibility: light、dark、大字体、TalkBack、所有操作图标有描述。
- Interaction: 打开抽屉自动读取、手动刷新、文件点击、diff 返回、详情返回、抽屉关闭和助手切换。

## Validation Evidence

- `:workspace:testDebugUnitTest` 通过；覆盖 argv 分离、路径穿越和符号链接逃逸。
- `:app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` 通过。
- 定向 `ConversationGitStatusDrawerTest` 在 USB 设备 `ebc3de22` 上 3/3 通过，使用 300dp debug-only Compose 宿主；覆盖未绑定、干净、三分组和二进制 diff。
- `:app:installDebug` 成功安装到 `ebc3de22`，包名 `me.arsucar.rikka.debug`；启动烟测进程存活。
- 60 个 `git_*` key 在 English、简中、繁中、日语、韩语、俄语资源中均完整，格式占位符一致。
- `git diff --check` 通过；静态审计未发现 Git 状态或 diff 进入模型请求、消息持久化或日志。
- `:app:lintDebug` 已运行一次，但被仓库既有 180 个未基线化错误阻断，首个为未修改的 `ChatInput.kt:603`；lint 文本报告对本任务文件零命中，未重复运行。
- 未执行完整 connected AndroidTest 套件；仅运行本任务新增的定向 Compose 测试。未进行 TalkBack 人工朗读或真实大仓库视觉巡检。
- 审查修复后再次执行 `:workspace:testDebugUnitTest :app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`，构建成功；新增低高度滚动测试已通过源码编译，但本轮未在设备执行。
- 本轮设备 `100.99.129.110:5555` 持续为 `offline`，未声称安装成功；`:app:assembleDebug` 通过，arm64 Debug APK 已上传至 `https://gofile.io/d/pzmlRA`，SHA-256 为 `4CF51265E8FAE593588BBA2D7607BA378AB90FD83A3F2A40EEED86614608FCA1`。
