# PRD：合并上游 rikkahub 2.4.13 ~ 2.5.0

## 背景
- 本 fork 分支 `release/rikka-arsucar` 当前 v2.3.54（上次合并上游 2.4.12）。
- 上游 `upstream/master` 已到 2.5.0（12ee935e），共 72 个非合并提交、197 文件变更。
- 冲突预检：45 个冲突文件（见 research/merge-evidence.md）。

## 目标
将 `upstream/master`（2.5.0）合并进 `release/rikka-arsucar`，合并后：
1. fork 自有特性全部保留且编译通过；
2. 上游新功能在 fork 中可用；
3. 验证 + CHANGELOG + 发版流程按本仓规约完成。

## fork 必须保留的特性（验收底线）
- 无 Firebase / google-services.json；web 模块（web-ui 构建注入）不被破坏。
- 包名/签名/DI 约定/AGENTS.md 全部规则。
- fork 对 speech TTS providers 等的历史定制（合并时逐文件比对取舍）。
- `me.arsucar.rikka.debug` 调试包名与 fork 版本号体系。

## 上游需要带入的重点功能
- 语音：voice mode 输入队列 + 可选 TTS；ASR server VAD + 火山引擎双向流式；TTS 自动重试。
- 聊天：消息发送队列；连续工具审批丢失修复；关闭自动重试设置；快速模型思考级别。
- Provider：自定义 response api 路径；MaruCode/hy4/glm-5.3/gpt-6 注册。
- OAuth/MCP 重构、备份一致性快照、Chatbox v2 导入、workspace/终端改进、依赖升级。

## 验收标准
- [ ] `.\gradlew --no-daemon :app:assembleDebug` 通过（仅最后的检查子代理可运行编译）。
- [ ] 受影响模块 JVM 测试（ai/speech/app 涉及冲突的测试类）通过。
- [ ] 安装到设备（adb 100.99.129.110:5555）通过一次真机启动验证。
- [ ] CHANGELOG.md 按 docs/CHANGELOG_GUIDE.md 更新，fork 版本号正确处理（上游 tag 2.5.0 不直接使用）。
- [ ] 合并提交推送到 origin（release/rikka-arsucar）由用户确认后执行。

## 排除项
- 不做 connectedDebugAndroidTest。
- 不治理既有 lint 基线。
- 不为合并顺手引入新功能或重构。
- 进行中还没合并前，不 commit 用户未确认的本地 dirty 改动。

## 风险
- 工作区当前有 20 个 dirty 路径，合并前需处置（stash 或由用户确认提交/丢弃）。
- speech 模块 8 个 TTS provider + 2 个 ASR controller 冲突最重，fork 与上游都改过。
- `e5247be0` 依赖升级可能与 fork 的 web 模块 preBuild（pnpm）、无 Firebase 配置冲突。
- ai/ui/Message.kt 与 GenerationLoop.kt 的发送队列改动将触及 fork 的消息分支/tree 逻辑。
