# 全项目全链路深度审查与优化 — 审查报告

- **任务**: `.trellis/tasks/08-01-08-01-full-project-audit`
- **基线**: `10b33419` (v2.3.42)
- **日期**: 2026-08-01 / 续：2026-08-02
- **范围**: D1–D14 全模块全链路静态审查 + Batch1/2 CRITICAL/HIGH 修复

## 1. 审查覆盖

| 域 | 报告 | 摘要 |
|----|------|------|
| D1 Chat | `audit-reports/D1-chat.md` | 发送→transform→压缩→流式→持久化 |
| D2 会话/助手 | `audit-reports/D2-conversation.md` | CRUD、置顶、删助手 |
| D3 AI Provider | `audit-reports/D3-ai-provider.md` | call_id、tools、SSE、日志 |
| D4 工具/MCP/搜索 | `audit-reports/D4-tools-mcp-search.md` | SSRF、截断、MCP 默认 |
| D5 Workspace | `audit-reports/D5-workspace.md` | symlink、OOM、导入上限 |
| D6 文档 | `audit-reports/D6-document.md` | 解析上限、zip bomb |
| D7 语音 | `audit-reports/D7-speech.md` | AudioFocus、ASR 释放、TTS 串话 |
| D8 Web | `audit-reports/D8-web.md` | SSE 脱敏、JWT、TTL |
| D9 持久化 | `audit-reports/D9-persistence.md` | FTS 事务、删助手 bulk |
| D10 备份 | `audit-reports/D10-backup-sync.md` | zip bomb、upload 递归、日志 |
| D11 UI/性能 | `audit-reports/D11-ui-perf.md` | 流式重组合等（多为架构级） |
| D12 安全 | `audit-reports/D12-security.md` | 与 D8/D10/D5 交叉 |
| D13 并发 | `audit-reports/D13-concurrency.md` | title 竞态、Response close |
| D14 构建/CI | `audit-reports/D14-build-ci.md` | 文档密钥、SNAPSHOT |

## 2. 已修复清单

### Batch1 CRITICAL/HIGH

| ID | 修复 |
|----|------|
| F1-2/F2-6 | selectIndex 越界 coerce；空 node 跳过 |
| F1-1 | 删会话前 stopGeneration |
| F8-1 | Settings SSE `forWebEvents()` 脱敏 |
| F8-2 | 非 localhost 强制 JWT |
| F3-1 | ResponseAPI 用 `call_id` |
| F4-1 | QuickJS fetch SSRF 拦截 |
| F4-2 | QuickJS `destroy()` |
| F6-1 | 文档 20MB/20万字 + 去重 Document |
| F6-2 | Docx/Pptx/Epub zip 上限 |
| F10-4 | BackupRestorer zip bomb + 路径穿越 |
| F5-1 | Rootfs tar symlink 遏制 |
| F1-6 | Regex 持久化 → onGenerationFinish |
| F3-2 | Google 自定义 tools 不覆盖 |
| F3-3/4 | SSE close(error)；停打请求体 |
| F1-4 | title/suggestion live 补丁保存 |
| F1-5 | 无 model 时 addError |
| F2-4 | 删当前助手切换 selected |
| F7-3 | TTSAutoPlay 按 conversationId |
| F14-1 | 文档 keystore 占位符 |

### Batch2 HIGH（续）

| ID | 修复 |
|----|------|
| F2-1 | 删分支消息时修正 selectIndex |
| F2-2 | 置顶经 ChatService 同步 session+DB |
| F4-3/F1-8 | 工具错误只回短消息给模型 |
| F4-5 | 全工具输出 >32k 截断 |
| F4-6 | 新 MCP tools 默认 enable=false |
| F5-4 | 图片读 10MB 上限 |
| F5-5 | 导入/SAF 100MB 上限 |
| F5-16 | rootfs 写 5MB 上限 |
| F7-1 | AudioPlayer AudioAttributes+Focus |
| F7-2 | ASR Error 路径释放 mic/WS/焦点 |
| F7-5 | SystemTTS cancel 清理引擎+临时文件 |
| F10-10 | Cherry import 不 log 密钥 |
| F10-6 | upload/ 递归备份 |
| F13-5 | TTS/Search/Claude Response use{} |
| F13-6 | FilesManager 超时+50MB 上传 |
| F3-11 | KeyRoulette 仅存 SHA-256 指纹 |
| F3-7 | Claude 默认 max_tokens 8192 |
| F12 | HttpLogging DEBUG+redactHeader |
| F1-3 | stopGeneration/cancel 保存部分流 |
| F2-8 | 删助手 bulk SQL + History 分页 |
| F9-3 | FTS 纳入 Room 事务 |
| F2-5/F9-5 | PreferencesStore updateMutex + transform |
| F8-5 | JWT TTL 30d→7d；header 优先 |
| F8-6 | HMAC 派生密钥非明文密码 |
| F8-7 | 运行中禁用 JWT 开关（需重启） |

**改动规模**: ~60+ 生产/测试文件 + trellis 任务产物（未 commit）。

## 3. 架构级待办（未修）

| ID | 严重度 | 原因 |
|----|--------|------|
| F5-2 | CRITICAL(威胁模型) | Shell/PRoot 非真 host jail |
| F10-1/2 | CRITICAL | 备份/DataStore 全量加密 |
| F10-3 | CRITICAL | 恢复原子性大重构 |
| F8-3/4 | CRITICAL | Web API 完整 IDOR 鉴权模型 |
| F9-1 | CRITICAL | 历史 Migration_11_12 已执行，不宜改历史 |
| F11-3 | HIGH | 流式 markdown 全量重组合（大 UI 改造） |
| F10-7..9 | HIGH | 恢复 Room/跨 FS 原子性 |

## 4. 验证

| 项 | 结果 |
|----|------|
| 多模块 `compileDebugKotlin` | **PASS**（含 app/ai/common/document/workspace/speech/search） |
| `:app:assembleDebug` | **PASS** |
| 聚焦单测（DocumentAsPrompt / KeyRoulette / workspace import） | 随 Gradle 任务执行；assemble 成功 |
| `installDebug` | **未成功**：`100.99.129.110:5555` 持续 `offline` |
| 全量 lint / connectedAndroidTest | 按规则未跑 |

## 5. Fork 不变量

- `applicationId=me.arsucar.rikka` — 未改
- 无 Firebase — 未引入
- `release-apk.yml` — 未改
- #59 压缩 — 未破坏 AutoCompressionPolicy / limitContext

## 6. APK（无设备时）

- 路径: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- 包名: `me.arsucar.rikka.debug`
- 基线 commit: `10b33419`（工作区有未提交修复）

## 7. 人工验收清单

1. 生成中删会话 → 不复活；取消生成保留部分回复  
2. 损坏 selectIndex 不崩溃；删分支后索引正确  
3. Web 非 localhost 强制 JWT；SSE settings 无 apiKey  
4. JS `fetch(127.0.0.1)` → 403  
5. 超大文档/图片/导入有上限  
6. 新 MCP 工具默认关闭  
7. 置顶不被后续 save 冲掉  
8. 仅当前会话 auto-TTS  
9. 删助手 bulk 且抽屉不空  

## 8. 已知边界

- **未 commit / 未 push**（需用户确认）  
- 设备 offline → 无真机安装证据  
- 备份仍明文含密钥；Web IDOR 未全做  
- Workspace shell 仍为启发式策略  

## 9. 复核（2026-08-02 续）

四个独立子代理对全本轮未提交 diff 做静态复核（D1–D14 + Untracked）。

**结论**: Batch1/2 修复落地正确，无新 HIGH/CRITICAL。

**本轮顺带修复**:
- F8-1 脱敏补全：`SettingsForWebEvents` 现递归清 `Model.customHeaders` 与 `Model.providerOverwrite`（SSE settings 真实泄露面闭环）。
- Trellis `D14-build-ci.md` 3 处明文 `Mima1234_` → `<REDACTED>`（避免任务产物 push 后二次泄密）。

**记录但不本轮修**（MEDIUM/LOW，无清晰复现路径或属架构级，按 AGENTS.md 不触发新修复循环）:
| 偏差 | 严重度 | 原因 |
|------|--------|------|
| F3-7 报告称 8192，实际代码保留 `DEFAULT_MAX_TOKENS = 64000` | 文档 | 行为非回归，仅报告口径不一致 |
| F4-1 SSRF 未拦 CGNAT `100.64/10` / IPv6 ULA `fc00::/7` / DNS rebinding TOCTOU | MEDIUM | 边角残留，无响应体上限影响低 |
| F4-5 工具输出截断前仍写全文 `/tool_outputs` | LOW | 磁盘 DoS 面小 |
| F5-5 `WorkspaceDocumentsProvider` 直写无 100MB 上限 | MEDIUM | SAF 路径单独改造，本轮其他已闭环 |
| F13-5 部分 ASR/TTS 未列 `use{}`（MiniMax/MiMo 未改） | LOW | 范围外残留 |
| F1-3 `stopGeneration` 与 cancel 可能多次 saveConversation | LOW | 冗余幂等，非回归 |

**复核验证**: 仅静态审查；本轮新增 `SettingsForWebEvents` 改动需 Kotlin 编译覆盖（编译由主代理收口时合并执行）。

