# 冲突预检（git merge-tree --write-tree，未动工作区）

- 分支：HEAD = release/rikka-arsucar (984366a5, v2.3.54, 上游 2.4.12 已合并)
- 对象：upstream/master (12ee935e, chore: bump to 2.5.0)
- 新 tag：2.4.13 / 2.4.14 / 2.4.15 / 2.4.16 / 2.4.17 / 2.5.0
- 规模：72 个非合并提交，197 文件变更，+9292/−3978

## 冲突文件清单（45 个）

### 构建/配置
- gradle/libs.versions.toml
- app/build.gradle.kts
- README.md / README_ZH_TW.md / README_ZH_CN.md（modify/delete：fork 已删中文 README，上游有修改）

### ai 模块
- ai/src/main/java/me/rerere/ai/provider/providers/google/GoogleProvider.kt
- ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt（含 response api 自定义路径功能）
- ai/src/main/java/me/rerere/ai/ui/Message.kt
- ai/src/test/java/.../ResponseApiStreamDecoderTest.kt

### speech 模块（fork 曾改过，最重）
- asr/providers/OpenAIRealtimeASRController.kt
- asr/providers/VolcengineASRController.kt（双向流式新功能）
- tts/provider/providers/{ElevenLabs,FishAudio,Gemini,Groq,OpenAI,Qwen,Step,XAI}TTSProvider.kt（8 个）

### app 模块
- data/ai/GenerationLoop.kt（发送队列相关）
- data/datastore/PreferencesStore.kt
- data/db/dao/MessageNodeDAO.kt、data/db/fts/MessageFtsManager.kt
- data/model/Conversation.kt
- data/sync/S3Sync.kt、data/sync/webdav/WebDavSync.kt
- di/AppModule.kt、di/DataSourceModule.kt
- service/ChatService.kt、service/ConversationSession.kt
- ui/components/ai/ChatInput.kt、ui/components/richtext/HighlightCodeBlock.kt
- ui/pages/assistant/detail/AssistantMemoryPage.kt
- ui/pages/backup/{BackupVM.kt, tabs/ImportExportTab.kt}
- ui/pages/chat/ChatPage.kt、ui/pages/chat/TTSAutoPlay.kt
- ui/pages/extensions/workspace/{WorkspaceDetailPage.kt, WorkspaceTerminalPage.kt}
- ui/pages/search/SearchVM.kt
- ui/pages/setting/{SettingPage.kt, SettingPreferencesUIPage.kt}
- RikkaHubApp.kt

### 测试
- app/src/test/.../PromptInjectionTransformerTest.kt
- app/src/test/.../ConversationSessionTest.kt（add/add）

### 其他模块
- search/src/main/java/me/rerere/search/ExaSearchService.kt
- web-ui/app/components/input/extension-picker.tsx
- workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt

## 上游关键提交（节选，feat 级）
- a621e277 feat(chat): voice mode 队列输入 + 可选 TTS
- 580e05d6 feat(asr): server VAD + Volcengine 双向流式
- 66de8b30 feat: 消息发送队列
- a61d116d feat: 自定义 response api 路径
- 097cdb90 refactor: ChatToolFactory 集中组装
- 41353567 feat: 支持关闭自动重试；c16fe44f 支持自动重试
- 9365c297 快速模型思考级别配置
- 74397a32 MaruCode 提供商；86c85236 hy4；5b58c957 glm-5.3；c73f8972 gpt-6
- 5403bc96/2f05019b TTS 自动重试与稳定性
- 03534d14 qwen audio 3.0 tts；6e26affe mimo tts 音色
- b6df5f04 oauth 流程重构；ef94834a mcp 仅用 client sdk
- 540b9dfa 备份一致性快照；7714f2bc chatbox v2 备份导入
- 5403bc96 系列 workspace/终端改进（104040df, 1d86b3c1, bce78766, b62d29d1）
- e5247be0 依赖升级；12ee935e bump 2.5.0
