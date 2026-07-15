<div align="center">
  <img src="docs/icon.png" alt="RikkaRs 应用图标" width="100" />
</div>

# RikkaRs

RikkaRs 是源自 RikkaHub、由 Arsucar 独立维护和发布的原生 Android LLM 客户端，专注智能体工作流、本地工作区与多 Provider 对话。

[English](README_EN.md) | **简体中文** | [繁體中文](README_ZH_TW.md)

[![最新版本](https://img.shields.io/github/v/release/Arsucar/RikkaRs?label=release)](https://github.com/Arsucar/RikkaRs/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![ABI arm64-v8a](https://img.shields.io/badge/ABI-arm64--v8a-blue)](app/build.gradle.kts)
[![分段双重许可](https://img.shields.io/badge/license-segmented%20dual-orange)](LICENSE)

**[下载最新稳定版](https://github.com/Arsucar/RikkaRs/releases/latest)**

<div align="center">
  <img src="docs/img/chat.png" alt="聊天界面" width="150" />
  <img src="docs/img/desktop.png" alt="模型选择器" width="450" />
</div>

## 关于 RikkaRs

RikkaRs 的代码源自 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)，由 Arsucar 在独立仓库 [Arsucar/RikkaRs](https://github.com/Arsucar/RikkaRs) 中独立维护和发布。项目按需同步有价值的上游变更，同时遵循自己的产品路线和发布节奏。

RikkaRs 是非官方发行版，与 RikkaHub 项目及其维护者不存在隶属、关联或背书关系。独立 Release 包名 `me.arsucar.rikka` 使其可与官方 RikkaHub 共存；项目已移除 Firebase，不需要 `google-services.json`。

## 功能特色

### 子代理

- 委托子代理并行执行或排队运行，可分别限制并发数与调用深度，并可随时取消运行。
- 向子代理传入所需上下文并查看完整 transcript；实时观察 token 用量、工具调用和运行状态。
- 子代理通过专用 `finish_work` 信号明确完成工作，主代理可据此可靠收尾。
- 子代理档案可独立配置模型、工作目录（CWD）、工具权限，以及 token、工具调用、耗时、深度和并发等预算。
- 在兼容的 scope 下续接原有历史；完整上下文会持久化，并可在应用或进程重启后基于持久化上下文继续未完成工作。

### Skills

- 管理全局与助手私有 Skills，并按作用域安全地向工作区和子代理提供所需 Skill。
- 通过斜杠补全从对话中显式启用 Skill，浏览更清晰的 Skill 目录卡片。
- 助手可在确认后创建或更新 Skill；私有副本与文件访问边界保持隔离。

### 记忆

- 记忆支持全局、助手和对话三种 scope，可同步、跟随或解除关联，避免不同使用场景互相污染。
- 同时支持普通记忆与结构化表格记忆；表格可定义模板，并按需创建和维护文档及数据行。
- 可设置总注入预算、仅注入相关行，并为每张表选择独立的检索与注入策略。
- 支持写入控制、快照与回滚、导入与导出；未注入的记忆仍可由工具按需检索。

### 上下文控制

- 隐藏消息采用软删除：旧消息从模型上下文排除但仍保留在会话树中，可查看和恢复。
- 支持手动压缩与超限自动压缩；压缩会隐藏并保留旧消息，而不是硬删除。
- 可配置保留的最近消息数量与压缩偏好，并在后续会话中沿用。
- 发送前可用只读的最终上下文检查器核对完成组装、注入和转换后的实际消息。
- 支持会话级模型覆盖，以及最近模型和提示词预设的快速切换；新会话不会误继承旧会话覆盖。

### 工作区与文件

- 使用统一的 **PRoot** 工作区，内置 Shell 与文件工具；也可选择应用专属的外部工作区。
- 外部工作区可通过系统文件管理器访问，便于经 USB 或 PC 管理和交换文件。
- 支持全屏文本编辑、Markdown 渲染预览，并可把任意类型文件作为对话附件。
- 工作区图片可作为多模态工具结果回传；非视觉模型会得到明确的降级说明。
- Shell 与子代理改动过的文件会以 chip 显示在消息下方，方便打开、追踪和继续处理。

### Provider 与模型

- 为多个 Provider 配置自定义 Host、URL、Header、Body 和模型列表。
- 用标签组织和筛选 Provider；模型选择器支持按 Provider 分组、搜索、折叠、收藏及一键展开/收起。
- 可为每个 Provider 分别设置本地 RPM/TPM，在触发服务端限制前控制请求节奏。
- 支持导入 NewAPI 渠道 JSON，以及导入和导出**兼容的 Provider 分享二维码**。
- 可集中整理多渠道与公益站配置，同时保留清晰的 Provider 边界和筛选能力。

### 预设与扩展

- 将多个提示词条目组合为提示词预设，并用预设级 `ModeInjection` 隔离不同模式的注入内容。
- 为助手与子代理档案关联合适的预设，按任务定向注入指令。
- 连接 MCP 服务器时支持 OAuth 2.1、PKCE、动态客户端注册、令牌刷新与断线重连。

### 对话与界面

- 支持会话归档和文件夹整理；助手可归档、恢复，并能接上其最近会话继续工作。
- 多选分享采用紧凑布局，长消息正文可折叠，便于检查和分享较长选择。
- 支持多模态输入与显示，以及 Markdown、LaTeX 和 Mermaid 渲染。
- 提供消息分支、搜索集成、图片生成、TTS、模型与图片生成收藏等日常能力。

### 本地工具与 Web

- 日志诊断会对 Authorization、API Key、Cookie、URL 与请求体中的敏感信息脱敏，并支持多选导出和 AI 可读的 `get_logs`。
- 经 Android 权限授权后，本地工具可读取屏幕使用时间，并查询或创建日历事件。
- 内置本地 Web UI 默认仅监听 `localhost`；开放局域网访问时应启用 JWT，界面会明确提示未鉴权风险。

## 独立发行

| 维度 | RikkaRs |
| --- | --- |
| 维护方式 | 独立仓库、产品路线与发布节奏；按需同步上游变更 |
| Android 身份 | 应用名 **RikkaRs**，Release 包名 `me.arsucar.rikka`，可与官方 RikkaHub 共存 |
| 发布渠道 | 由 Arsucar 在 [RikkaRs Releases](https://github.com/Arsucar/RikkaRs/releases) 独立维护和发布稳定版 |
| 服务依赖 | 已移除 Firebase，无需 `google-services.json` |
| 演进重点 | 智能体编排、持久记忆、上下文控制、本地工作区、Provider/模型管理与扩展能力 |

实现与上游同步规则见[工程指南](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

## 下载与安装

- **稳定版：**[GitHub Releases — 最新版](https://github.com/Arsucar/RikkaRs/releases/latest)
- **最低系统：**Android 8.0（API 26）
- **支持 ABI：**`arm64-v8a`
- **Release 包名：**`me.arsucar.rikka`

独立包名让 RikkaRs 可与官方 RikkaHub 安装在同一设备上。升级或切换构建版本前，请先备份重要数据。

## 构建与贡献

使用 [Android Studio](https://developer.android.com/studio) 和 JDK 17 打开项目。Android 应用采用 Kotlin、Jetpack Compose、Koin、DataStore、Room、Coil、Material You、Navigation 3、OkHttp 和 kotlinx.serialization。项目不使用 Firebase，因此无需 `google-services.json`。包名、CI、发版和上游同步细节见[工程指南](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

适合提交的贡献包括聚焦的缺陷修复、文档修正和可维护性改进；开始较大工作前请先创建 Issue。项目不接受仅涉及翻译的改动、未经讨论的功能实现，以及大规模或由 AI 生成的重构。

## 许可证

RikkaRs 采用 [LICENSE](LICENSE) 规定的用户分段双重许可：符合任一条件——严格非商业用途、个人/教育/研究用途，或个人及组织总用户数不超过 10 人——可按 GNU AGPL v3 免费使用，并须履行该许可证包括公开源代码在内的义务。

商业用途（直接或间接产生商业利益）、总用户数超过 10 人，或希望免除 AGPL v3 义务时，必须事先取得商业许可证；请联系 `re_dev@qq.com`。项目维护者保留更新许可政策的权利，并会通过官方渠道通知。

以上仅为摘要，任何情况下均以 [LICENSE 原文](LICENSE) 为最终效力依据。使用、修改或分发本软件前，请完整阅读许可条款。

## 相关链接

- [RikkaRs 独立仓库](https://github.com/Arsucar/RikkaRs)
- [最新稳定版](https://github.com/Arsucar/RikkaRs/releases/latest)
- [全部 Releases](https://github.com/Arsucar/RikkaRs/releases)
- [更新日志](CHANGELOG.md)
- [工程指南](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)
- [上游 RikkaHub](https://github.com/rikkahub/rikkahub)
