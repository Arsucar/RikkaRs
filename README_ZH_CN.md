<div align="center">
  <img src="docs/icon.png" alt="RikkaRs App 图标" width="100" />
  <h1>RikkaRs</h1>

一个基于 RikkaHub 的原生 Android LLM 聊天客户端 Fork，面向智能体工作流、
本地工作区与多供应商对话体验。

[English](README.md) | [繁體中文](README_ZH_TW.md) | 简体中文
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

## 关于本 Fork

本仓库是 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 的下游 Fork，会跟随上游合并，
同时从 [Arsucar/rikkahub](https://github.com/Arsucar/rikkahub) 发布独立 Android 版本 **RikkaRs**。

RikkaRs 不是上游官方版本；如果你需要上游官方发布渠道、包名或支持，请使用上游 RikkaHub。

## RikkaHub vs RikkaRs

| 维度 | 上游 RikkaHub（`rikkahub/rikkahub`） | 本 Fork RikkaRs（`Arsucar/rikkahub`） |
|------|--------------------------------------|--------------------------------------|
| Release 包名 | `me.rerere.rikkahub` | `me.arsucar.rikka`；Debug 为 `me.arsucar.rikka.debug` |
| Kotlin namespace | `me.rerere.rikkahub` | 仍保留 `me.rerere.rikkahub`，降低合并上游成本 |
| 应用名 | RikkaHub | RikkaRs |
| 发行渠道 | 官网与 Google Play | 本 fork 的 GitHub Releases |
| Firebase | 上游可能使用 Firebase 服务 | 已移除 Firebase，不需要 `google-services.json` |
| CI 与发版 | 上游 workflow | 仅使用 `Release APK (arm64)`；arm64 APK、无 Firebase、CI 构建 web-ui |
| 子代理 | 上游智能体/工具行为 | 并行、排队、委托子代理，支持深度/并发限制、transcript 卡片、取消与子代理专用 `finish_work` |
| Skills | 全局 Skill 支持 | 全局与助手私有 Skill、私有副本管理、更安全的文件作用域、斜杠补全与更清晰的目录卡片 |
| 记忆 | 类 ChatGPT 记忆 | 助手私有/全局记忆作用域，新增默认关闭的记忆表、模板、文档与作用域控制 |
| 工作区 | 基于 proot 的工作区 | 外部应用专属存储、全屏文本编辑、Markdown 只读渲染预览、dotfile/配置文件识别与 `/tmp` 写入便利 |
| NewAPI 导入 | 提供商二维码/导入流程 | 支持 NewAPI `newapi_channel_conn` JSON，与二维码/分享格式自动分流 |
| 日志与 `get_logs` | 应用日志 UI | 日志页导出、长按多选导出、AI `get_logs` 工具、截断与更适合工具读取的摘要 |
| 脱敏导出 | 不是本 fork 重点 | 导出与 `get_logs` 会脱敏 Authorization、API Key、Cookie、URL 密钥与请求体密钥 |
| 会话归档 | 合并上游后可用 | 保留归档能力，并兼容归档搜索/列表 |
| 会话文件夹 | 合并上游后可用 | 保留按助手分组的会话文件夹 |
| Web 访问 | 内置 Web 服务 | 默认只监听 localhost；未启用 JWT 且准备开放 LAN 时给出警告 |
| 屏幕时间/日历 | 不是上游核心差异 | 授权后可让本地工具读取屏幕使用时间、查询/创建日历事件 |
| 提供商标签 | 基础提供商设置 | `provider.tags` 与设置页 Tag 筛选 |
| 模型列表 | 标准模型选择 | 按提供商折叠、收藏区折叠、一键展开/收起、提供商标签过滤与模型收藏分组 |
| 隐藏上下文 | 删除/压缩行为 | 隐藏消息作为软删除；隐藏节点仍可见但不进上下文，压缩上下文默认隐藏旧消息而非硬删 |
| 会话级模型覆盖 | 助手默认模型 | 单会话模型覆盖与一键清回助手默认，新会话不继承旧会话覆盖 |
| 收藏 | 消息/收藏基础能力 | 模型收藏、图生收藏、收藏集合，以及分组/折叠的收藏视图 |
| Web localhost + JWT | 可配置 Web 认证 | 默认 localhost，并在远程访问未启用 JWT 时明确提示风险 |
| 工程说明 | 上游约定 | Fork 包名、CI、发版与 Firebase 决策见 [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md) |

## 🚀 下载

🔗 [从 GitHub Releases 下载 RikkaRs](https://github.com/Arsucar/rikkahub/releases)

🔗 上游官方版本：[官网下载](https://rikka-ai.com/download) / [Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

## 💖 赞助商

|                                         赞助商                                         | 介绍                                                                                                                                              |
|:-----------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="docs/sponsors/aihubmix.png" alt="Aihubmix" width="50" /><br /><b>Aihubmix</b> | 感谢 <a href="https://aihubmix.com?aff=pG7r">aihubmix.com</a> 的资金支持。我们推荐使用 aihubmix 作为全球主流模型的一站式服务平台。（OpenAI、Claude、Google Gemini、DeepSeek、Qwen 以及数百种其他模型）。 |
| <img src="docs/sponsors/suixiang.jpg" alt="随想AI网关" width="50" /><br /><b>随想AI网关</b> | 感谢随想AI网关对本项目的赞助！随想AI网关 是一家可靠高效的 API 中继服务提供商，提供 Claude、Codex、Gemini 等的中继服务。注重隐私的中转站·无数据倒卖·无模型掺水，隐私，透明，极速售后。新账户注册每日签到就送 0.5 元测试额度，充值额度 1:1，无需订阅，按量付费。多线路冗余、跨区域容灾、自动故障切换，长链路 SSE 不中断。99.9% 可用性，关键调用从不掉队。 |

## ✨ 功能特色

本列表已对齐本 fork 的 CHANGELOG 至 **v2.3.19**。

- 🎨 Material You 设计、预测性返回与暗色模式
- 🔄 多供应商支持：自定义 API 地址、URL、请求头、请求体与模型列表
- 🧩 提供商标签、Tag 筛选、NewAPI 渠道 JSON 导入，以及二维码导入/导出提供商
- ⭐ 可折叠模型选择器：按提供商分组、收藏模型、收藏区、一键展开/收起与标签过滤
- 🖼️ 多模态聊天输入：图片、文档、PDF、DOCX 与常见文本文件
- 📝 Markdown 渲染：代码高亮、LaTeX 公式、表格、Mermaid、粗体修复，以及工作区 Markdown 只读渲染预览
- 🪾 消息分支、隐藏消息、隐藏上下文压缩、会话归档、会话文件夹与会话级模型覆盖
- 📦 Proot 工作区：Shell/文件工具、外部存储、全屏文本编辑、更保守的 shell 策略与更清晰的 shell transcript
- 🤖 助手自定义与子代理：委托、并行/排队执行、限制、transcript 预览、取消与 `finish_work`
- 🛠️ MCP 支持：OAuth 2.1、令牌刷新与按需重连
- 🧠 助手/全局记忆作用域，以及默认关闭的记忆表
- 🧠 Skills 库：斜杠补全、全局/私有副本、更安全的文件访问与优化后的 Skills 目录卡片
- 🔍 搜索能力：Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等，并支持搜索结果图片
- 🖥️ 内置 Web 访问：默认 localhost，仅在明确配置后开放远程，并提示 JWT 风险
- 📊 本地诊断：日志页、多选脱敏导出，以及 AI 可读取的 `get_logs`
- 📱 授权后可用的本地工具：屏幕使用时间、日历查询与创建
- 📝 AI 翻译、Prompt 变量、SillyTavern 角色卡导入、助手头像裁剪、图生收藏与收藏集合

## ✨ 贡献

本项目使用[Android Studio](https://developer.android.com/studio)开发，欢迎提交PR。

技术栈文档:

- [Kotlin](https://kotlinlang.org/) (开发语言)
- [Koin](https://insert-koin.io/) (依赖注入)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI 框架)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore?hl=zh-cn#preferences-datastore) (
  偏好数据存储)
- [Room](https://developer.android.com/training/data-storage/room) (数据库)
- [Coil](https://coil-kt.github.io/coil/) (图片加载)
- [Material You](https://m3.material.io/) (UI 设计)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (导航)
- [Okhttp](https://square.github.io/okhttp/) (HTTP 客户端)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (Json序列化)

> [!TIP]
> **RikkaRs fork** 已移除 Firebase，**不需要** `google-services.json`。详见 [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

> [!IMPORTANT]
> 以下PR将被拒绝：
> 1. 添加新语言，因为添加新语言会增加后续本地化的工作量
> 2. 添加新功能，这个项目是有态度的
> 3. AI生成的大规模重构和更改

## 💰 捐赠

* [Patreon](https://patreon.com/rikkahub)
* [爱发电](https://afdian.com/a/reovo)

## ⭐ Star History

如果喜欢 RikkaRs，请给这个 fork 一个 Star ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=Arsucar/rikkahub&type=Date)](https://star-history.com/#Arsucar/rikkahub&Date)

## 📄 许可证

[License](LICENSE)
