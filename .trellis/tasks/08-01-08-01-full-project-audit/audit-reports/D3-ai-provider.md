# D3 AI Provider 层审计报告

**域**: D3 AI Provider Layer  
**模块根**: `ai/src/main/java/me/rerere/ai`  
**范围**: 静态只读审查（无编译/无 Gradle/无改码）  
**基线参考**: PRD `10b33419` / 当前 worktree 源码  
**审查日期**: 2026-08-01  

---

## 1. 链路梳理

### 1.1 模块拓扑（实际存在）

`ai/` 模块**没有**独立的 `context/`、`completion/`、`handler/` 包；这些能力落在 `app` 的 `GenerationHandler` / transformers。本域实际结构：

| 子路径 | 职责 |
|--------|------|
| `provider/Provider.kt` | 无状态 `Provider<T>` 契约：listModels / generateText / streamText / embedding / image |
| `provider/ProviderManager.kt` | 注册 openai / google / claude，按 `ProviderSetting` 密封类分发 |
| `provider/ProviderSetting.kt` | OpenAI / Google / Claude 配置（baseUrl、key、Vertex、Response API、prompt cache） |
| `provider/Model.kt` | 模型元数据、模态、能力、`providerOverwrite` |
| `provider/providers/*` | 三家客户端 + OpenAI Chat/Response 双实现 + Vertex SA token |
| `registry/` | `ModelRegistry` token 匹配 DSL，推断 vision/tool/reasoning |
| `ui/Message.kt` | `UIMessage` / parts / chunk 合并 / `limitContext` 阶梯截断 |
| `ui/MessageMetadata.kt` | Claude signature / OpenAI encrypted reasoning / Google thoughtSignature |
| `core/` | Role、Tool、ReasoningLevel、TokenUsage |
| `util/` | SSE、ErrorParser、KeyRoulette、FileEncoder、Request merge |

### 1.2 端到端调用链（与 app 交界）

```
ChatService / GenerationHandler / SubagentHost / OcrTransformer / EmbeddingService / ImgGenSession
    → model.findProvider(settings.providers)  [app PreferencesStore]
    → ProviderManager.getProviderByType(setting)
    → prepareProviderInput:
         messages.limitContext(assistant.contextMessageLimit)   // ai/ui
         system prompt + memory + tool systemPrompt
         InputMessageTransformers (placeholder / OCR / document / injection …)  // app
    → ProviderRateLimiter.await(...)  // app，非 ai 模块
    → provider.streamText | generateText
         OpenAI: useResponseApi ? ResponseAPI : ChatCompletionsAPI
         Google: generateContent / streamGenerateContent(+alt=sse) | Vertex SA
         Claude: /messages SSE
    → Flow<MessageChunk> / MessageChunk
    → messages.handleMessageChunk + usage.merge
    → OutputMessageTransformers + 工具循环
```

### 1.3 Provider 选择与 id 映射

- **注册键**: 硬编码字符串 `"openai" | "google" | "claude"`（`ProviderManager` init）。
- **类型映射**: `when (setting)` 密封类分支，**不是**按用户自定义 provider 名字符串。
- **模型级覆盖**: `Model.providerOverwrite` 在 app `PreferencesStore` 解析；AI 层只消费最终 `ProviderSetting`。
- **能力推断**: `ModelRegistry` 对 modelId 做 token 序列匹配（最长分优先），供 UI 与请求构造（temperature 禁用、reasoning 方言、模态）。

### 1.4 流式与错误

- 统一 `SSEEventSource`（自研，基于 OkHttp SSE reader），`callbackFlow` + **`Channel.UNLIMITED`**（修 #1295 静默丢 delta）。
- 失败路径：`parseHttpErrorResponse` → `HttpException`（流式 onFailure）；非流式多处仍 `throw Exception(code + raw body)`。
- **无** provider 层 HTTP 状态重试；`SSE.onRetryChange` 显式忽略；连接层依赖 OkHttp `retryOnConnectionFailure`。

### 1.5 上下文 / #59

- **阶梯截断**在 `ai`：`List<UIMessage>.limitContext`（`CONTEXT_KEEP_RATIO=0.5` + tool 边界对齐）。
- **#59 自动压缩**、prompt 拼装、placeholder 在 **app**（`GenerationHandler.prepareProviderInput` + transformers + `CompressPrompt`），不在 `ai/` 内。

### 1.6 消息转换要点

| Provider | 历史 tool 边界 | Reasoning 回传 | 系统提示 |
|----------|----------------|----------------|----------|
| ChatCompletions | `groupPartsByToolBoundary` → assistant tool_calls + role=tool | `reasoning_content`（可关 `includeHistoryReasoning`） | messages 内 system role |
| ResponseAPI | function_call + function_call_output | reasoning item + encrypted_content metadata | `instructions` |
| Google | model functionCall + user functionResponse | thought / thoughtSignature metadata | `systemInstruction`（非 IMAGE 输出） |
| Claude | assistant tool_use + user tool_result | thinking + signature metadata | 顶层 `system` 数组 + prompt cache |

---

## 2. 问题清单

### F3-1 — Response API 流式 tool 使用 item `id` 而非 `call_id`（多轮工具契约破坏）

- **文件**: `ai/.../openai/ResponseAPI.kt:515-533`, `:639-658`, `:365-376`
- **严重度**: **CRITICAL**
- **描述**:  
  流式 `response.output_item.added`（`function_call`）把 `item["id"]`（如 `fc_…`）写入 `UIMessagePart.Tool.toolCallId`；`function_call_arguments.done` 用 `item_id` 合并。  
  下一轮 `buildMessages` 却 `put("call_id", tool.toolCallId)`。OpenAI Responses API 要求 `function_call_output.call_id` 为 `call_…`，与 item `id` 不同。  
  非流式 `parseResponseOutput` 正确使用 `output["call_id"]`，**流/非流行为不一致**。
- **证据**:
```kotlin
// stream: toolCallId = item id
val id = item["id"]?.jsonPrimitive?.content ?: error("chunk id not found")
UIMessagePart.Tool(toolCallId = id, ...)

// resend:
put("type", "function_call_output")
put("call_id", tool.toolCallId)  // 可能是 fc_ 而非 call_

// non-stream:
val callId = output["call_id"]...
toolCallId = callId
```
- **建议修复**:  
  解析时同时保存 `id` 与 `call_id`（metadata 或 Tool 字段）；合并参数用 item id；回传 API 始终用 `call_id`。`arguments.done` 按 item_id 定位，输出时映射到 call_id。

---

### F3-2 — Google：自定义 tools 与 BuiltInTools 互相覆盖

- **文件**: `ai/.../GoogleProvider.kt:424-474`
- **严重度**: **HIGH**
- **描述**:  
  先 `put("tools", functionDeclarations…)`，若 `params.model.tools` 非空再 `put("tools", googleSearch/urlContext…)`，**后写覆盖前写**。开启内置搜索/URL 时自定义 function 工具全部丢失。  
  ResponseAPI 已注释「必须写在同一 key」，Google 未做合并。
- **证据**:
```kotlin
if (params.tools.isNotEmpty() && …TOOL) {
    put("tools", buildJsonArray { /* functionDeclarations */ })
}
if (params.model.tools.isNotEmpty()) {
    put("tools", buildJsonArray { /* BuiltInTools only */ })  // 覆盖
}
```
- **建议修复**:  
  合并为单个 `tools` 数组（functionDeclarations 一项 + 各 built-in 项），或明确互斥并在 UI/请求前拒绝组合。

---

### F3-3 — ChatCompletions 流内 `throw error` 而非 `close(error)`

- **文件**: `ai/.../openai/ChatCompletionsAPI.kt:179-181`
- **严重度**: **HIGH**
- **描述**:  
  SSE `onEvent` 中若 chunk 含 `error` 字段则 `throw error`。依赖 `SSEEventSource.processResponse` 外层 catch 转 `onFailure`。与 Claude 的 `close(error)`、正常 `onFailure→close(exception)` 不一致；在部分 OkHttp SSE 路径上可能变成未结构化失败或仅日志。
- **证据**:
```kotlin
if (it["error"] != null) {
    val error = it["error"]!!.parseErrorDetail()
    throw error
}
```
- **建议修复**: 与 Claude 对齐：`close(error); return`，禁止在 listener 回调里抛业务异常。

---

### F3-4 — 全量请求体 `Log.i` / `println` 泄露提示与工具参数

- **文件**:  
  `ChatCompletionsAPI.kt:97,155`  
  `ResponseAPI.kt:96,133,682`  
  `ClaudeProvider.kt:133,181-185`  
  `GoogleProvider.kt:244`  
  `OpenAIProvider.kt:253`
- **严重度**: **HIGH**（安全 / 隐私）
- **描述**:  
  生产路径对完整 JSON 请求体打 `Log.i`；Claude 还逐条 `Log.i` messages；ResponseAPI `parseResponseOutput` `println(jsonObject)`。logcat / 崩溃收集可带走用户对话、工具参数、文件名等敏感内容。错误解析侧已限制 preview 长度，请求侧未对称处理。
- **建议修复**:  
  默认 debug-only 或 redact（截断 messages/content、打码 key）；Release 禁止全文 body 日志。

---

### F3-5 — 非流式错误未走 `parseHttpErrorResponse`，原始 body 可能进 UI

- **文件**:  
  `ChatCompletionsAPI.kt:100-101`  
  `ResponseAPI.kt:99-100`  
  `ClaudeProvider.kt:136-137`  
  `GoogleProvider.kt:189-190`  
  `OpenAIProvider` listModels/balance/embedding/image 多处 `error("… ${response.body?.string()}")`
- **严重度**: **MEDIUM**
- **描述**:  
  流式失败已用 `parseHttpErrorResponse`（HTML/非 JSON 安全摘要）。非流式仍拼接完整 body。上游 HTML/代理诊断页会直接抛给用户；与 ErrorParser 测试「不暴露 private proxy diagnostic token」目标冲突。
- **建议修复**: 统一 `throw parseHttpErrorResponse(response, bodyRaw)`。

---

### F3-6 — Claude `listModels` 使用阻塞 `execute()` 且未 `use{}`

- **文件**: `ai/.../ClaudeProvider.kt:90-96`
- **严重度**: **MEDIUM**
- **描述**:  
  其他路径用 `await()`；此处 `client.newCall(request).execute()`，协程取消不取消 call；response 未 `use`，极端情况连接泄漏。
- **建议修复**: `client.newCall(request).await()` + body 消费模式与 OpenAI 一致。

---

### F3-7 — Claude 默认 `max_tokens = 64_000`

- **文件**: `ClaudeProvider.kt:301`
- **严重度**: **MEDIUM**
- **描述**:  
  `params.maxTokens` 为空时写死 `64000`。部分 Claude / 中转模型上限更低时请求直接 400；也放大账单风险。
- **建议修复**: 按模型注册表或更保守默认（如 8192/模型能力字段）；空则省略或使用 provider 文档默认。

---

### F3-8 — Claude `redacted_thinking` 被丢弃

- **文件**: `ClaudeProvider.kt:570-573`
- **严重度**: **MEDIUM**
- **描述**:  
  `redacted_thinking` 仅 `println(data)`，不写入 parts。多轮带 thinking 时缺少 redacted 块/签名，后续请求可能违反 Anthropic thinking 连续性要求。
- **建议修复**: 持久化为 Reasoning（或专用 part）并原样回传。

---

### F3-9 — Google 流式解析失败只 printStackTrace，不 fail flow

- **文件**: `GoogleProvider.kt:298-301`
- **严重度**: **MEDIUM**
- **描述**:  
  `onEvent` 内 `catch` 后 `printStackTrace` + `println`，**不 close**。畸形 SSE 会静默跳过，UI 可能表现为「卡住后空回复」而非明确错误。
- **建议修复**: 可恢复则 skip+metric；连续/致命解析错误 `close(e)`。

---

### F3-10 — Google functionCall 每次 `Uuid.random()` 作为 toolCallId

- **文件**: `GoogleProvider.kt:567-576`
- **严重度**: **MEDIUM**
- **描述**:  
  Gemini 不返回稳定 call id，本地生成 UUID。单轮执行靠「未执行 Tool」列表尚可；同一 assistant 消息多 tool 的 UI/审批/持久化依赖 id 稳定。流式若重复解析同一 call 可能产生重复 Tool part（取决于上游是否重发完整 functionCall）。回传仅用 `name`，与 id 无关，**API 层可工作**，本地关联脆弱。
- **建议修复**: 流式用稳定合成键（index+name+args hash）；或 metadata 存服务端 id（若有）。

---

### F3-11 — KeyRoulette LRU 把 API key 明文写入 cache 文件

- **文件**: `ai/.../util/KeyRoulette.kt:16-19,50-51,89-108`
- **严重度**: **MEDIUM**（安全）
- **描述**:  
  `cacheDir/lru_key_roulette.json` 结构为 `Map<providerId, Map<apiKey, lastUsed>>`，**完整 apiKey 明文**落盘。root/备份/共享存储场景可泄露。`saveCache` 失败静默吞掉。
- **建议修复**: 只存 key 指纹（SHA-256）；或加密 SharedPreferences；禁止明文 key 作 map key。

---

### F3-12 — `handleMessageChunk` 对 delta.role 敏感，空 role 默认 ASSISTANT 掩盖问题

- **文件**:  
  `ui/Message.kt:222-233`  
  `ChatCompletionsAPI.kt:720-723`
- **严重度**: **MEDIUM**
- **描述**:  
  若 `last.role != message.role` 会**新开一条**消息。ChatCompletions `parseMessage` 在 role 缺失时默认 `ASSISTANT`。多数 delta 无 role 时依赖默认尚可；若错误解析成 USER/SYSTEM 会拆消息树，破坏 tool 合并与 UI 节点。
- **建议修复**: delta 缺 role 时继承当前流式消息 role，不要 `valueOf` 默认后参与 role 比较分支。

---

### F3-13 — Image 流式合并固定 `data:image/png;base64` 前缀

- **文件**: `ui/Message.kt:56-69`
- **严重度**: **MEDIUM**
- **描述**:  
  首个 Image delta 强制 PNG data URL；OpenAI/Google 可能返回 jpeg/webp。下游 `require(url.startsWith("data:image"))` 与 mime 假设可能失败或错误解码。
- **建议修复**: 前缀带真实 mime；或仅存 raw base64 + metadata.mime。

---

### F3-14 — SSE 非 `text/event-stream` 直接失败（部分兼容网关）

- **文件**: `util/SSE.kt:48-54,87-90`
- **严重度**: **MEDIUM**
- **描述**:  
  `isEventStream` 要求 `text/event-stream`。部分中转返回 `application/json` 或 `text/plain` 的伪 SSE，直接 `IllegalStateException`。产品面向大量自定义 baseUrl，兼容性风险高。
- **建议修复**: 可配置宽松模式；或 content-type 含 event-stream / 空 type 时尝试按行解析。

---

### F3-15 — `ProviderManager.getProvider` 文档与行为不符

- **文件**: `ProviderManager.kt:37-40,47-48`
- **严重度**: **LOW**
- **描述**: KDoc 写「不存在返回 null」，实际 `throw IllegalArgumentException`。
- **建议修复**: 改文档或改为可空 API。

---

### F3-16 — `getBalance` 默认 `"TODO"`，仅 OpenAI 实现

- **文件**: `Provider.kt:22-24`；Google/Claude 无 override
- **严重度**: **LOW**
- **描述**: UI `ProviderBalanceText` 对非 OpenAI 可能显示字面 "TODO"。
- **建议修复**: 未实现抛 `UnsupportedOperationException` 或返回空并在 UI 隐藏。

---

### F3-17 — `configureReferHeaders` 硬编码推广码 / 品牌 Referer

- **文件**: `util/Request.kt:24-38`
- **严重度**: **LOW**（fork 身份 / 隐私）
- **描述**:  
  `aihubmix.com` → `APP-Code: DKHA9468`；`openrouter.ai` → `X-Title: RikkaHub` + `HTTP-Referer: https://rikka-ai.com`。fork（arsucar）仍上报上游品牌，可能非预期。
- **建议修复**: 可配置或 fork 默认改包名/站点；关闭推广头选项。

---

### F3-18 — `ProviderSetting` 大量 `var` + `@Composable` 描述在序列化模型中

- **文件**: `ProviderSetting.kt:65-75` 等
- **严重度**: **LOW**
- **描述**: 数据类可变字段 + Transient Composable 使 setting 兼作 UI 模型，增加意外就地变异与测试难度；`ai` 模块依赖 Compose。
- **建议修复**: 长期拆 UI 描述到 app；setting 保持 val 不可变。

---

### F3-19 — TokenUsage.merge 用「>0 覆盖」策略，无法表示合法 0

- **文件**: `core/Usage.kt:13-35`
- **严重度**: **LOW**
- **描述**: `other.promptTokens > 0` 才更新，流式中间 usage 为 0 的 chunk 不会清空，但若服务真实返回 0 completion 也可能保留旧值。一般可接受，边界不精确。
- **建议修复**: 使用可空字段区分「未提供」与「0」。

---

### F3-20 — 无 provider 层超时/重试策略（依赖全局 OkHttp）

- **文件**: app `DataSourceModule.kt:411-417`；`SSE.kt:114-115`
- **严重度**: **LOW**（设计债，记入风险）
- **描述**:  
  全局 `readTimeout=10min`、`retryOnConnectionFailure=true`；ai 层无 429/5xx 退避。`ProviderRateLimit` 仅数据字段，限流在 app `ProviderRateLimiter`。长读超时下挂起连接占用线程/内存。
- **建议修复**: 文档化即可；可选 per-provider 超时与 429 Retry-After。

---

### F3-21 — ResponseAPI 流式 tool 参数：`arguments.done` 可能整段覆盖而非增量

- **文件**: `ResponseAPI.kt:639-658` + `Message.kt` Tool.merge
- **严重度**: **LOW**（需结合上游事件确认）
- **描述**:  
  `function_call_arguments.done` 发送完整 `arguments` 字符串；`Tool.merge` 做 **字符串拼接** `input + other.input`。若此前 delta 已累加参数，done 再给全文会导致 **参数重复 JSON**。若只有 done 无 delta 则正常。
- **建议修复**: done 事件应 **替换** input 而非 merge 拼接；或 done 时 input 置空再设全文。

---

### F3-22 — ChatCompletions `parseAnnotations` 未知 type 直接 `error()`

- **文件**: `ChatCompletionsAPI.kt:787-802`
- **严重度**: **LOW**
- **描述**: 新 annotation 类型会打断整条流/整次解析。
- **建议修复**: 忽略未知类型并日志。

---

## 3. 亮点 / 可复用

1. **无状态 Provider + Setting 注入**：易测、易多实例，Koin 单例只持 OkHttp/Context。  
2. **`groupPartsByToolBoundary`**：三家 provider 共用，保证 tool_call 与 result 相邻，有扎实单测（OpenAI/Google/Claude/ResponseAPI MessageTest）。  
3. **`limitContext` 阶梯滞回**：利于 prompt cache；`alignContextStart` 避免拆开 tool 对；`MessageTest` 覆盖充分。  
4. **流式 `Channel.UNLIMITED`**：明确修复 #1295 静默丢字。  
5. **`ErrorParser`**：HTML/非 JSON/SSE 序列错误收敛为安全 `HttpException`，有单测防诊断 token 泄露。  
6. **`PartMetadata` 类型化**：Claude signature / OpenAI encrypted / Google thoughtSignature 跨会话回传设计清晰。  
7. **`ModelRegistry` DSL**：可扩展的 modelId → 能力推断，避免散落 if-else。  
8. **OpenAI 双栈**：Chat Completions + Responses，host 级 reasoning 方言表（硅基流动、Moonshot keep=all、DeepSeek 等）工程化程度高。  
9. **FileEncoder**：EXIF 方向、采样压缩、强制 JPEG 兼容性、HEIF 魔数识别。  
10. **KeyRoulette LRU**：多 key 轮询 + 过期清理（安全存储需改进，算法本身可复用）。

---

## 4. 遗漏与风险

| 项 | 说明 |
|----|------|
| **范围边界** | PRD 写的 context/completion/handler 主要在 **app**；本报告在交界处标注，未展开 D1 Chat 全量。 |
| **#59 压缩** | 不在 ai 模块；与 `limitContext` 叠加策略需 D1 联审（双重压缩 / 顺序）。 |
| **无本地 token 估算** | ai 层不做 tiktoken 类估算，超窗依赖服务端报错或 app 压缩。 |
| **并发** | Provider 实例无状态；KeyRoulette 有全局文件锁；Vertex token `ConcurrentHashMap` 缓存合理。 |
| **测试缺口** | ResponseAPI **流式** tool id/`call_id` 无集成测；Google tools 覆盖无测；错误路径非流式未统一。 |
| **中转兼容** | host 特判表维护成本高，未知 host 走 OpenAI 默认 reasoning_effort，易 400。 |
| **安全** | 请求体日志 + LRU key 文件 + Referer 品牌头是 fork 上线前优先治理项。 |
| **取消** | `awaitClose { eventSource.cancel() }` 良好；Claude listModels `execute` 是例外。 |

---

## 5. 严重度汇总

| 级别 | ID |
|------|-----|
| CRITICAL | F3-1 |
| HIGH | F3-2, F3-3, F3-4 |
| MEDIUM | F3-5 … F3-14 |
| LOW | F3-15 … F3-22 |

**建议修复优先序**: F3-1 → F3-2 → F3-3 → F3-4 → F3-5/F3-11 → 其余 MEDIUM。

---

## 6. 文件索引（主路径）

```
ai/src/main/java/me/rerere/ai/
  provider/Provider.kt, ProviderManager.kt, ProviderSetting.kt, Model.kt
  provider/providers/OpenAIProvider.kt, GoogleProvider.kt, ClaudeProvider.kt
  provider/providers/openai/{ChatCompletionsAPI,ResponseAPI,OpenAIImpl,OpenAIToolSchema}.kt
  provider/providers/ProviderMessageUtils.kt
  provider/providers/vertex/ServiceAccountTokenProvider.kt
  registry/{ModelRegistry,ModelDsl}.kt
  ui/{Message,MessageMetadata,Image,ImageOptions}.kt
  core/{Tool,Usage,Reasoning,MessageRole}.kt
  util/{SSE,ErrorParser,KeyRoulette,FileEncoder,Request,Json,Serializer}.kt
```

**交界（非本模块实现，审计引用）**:  
`app/.../data/ai/GenerationHandler.kt`（limitContext 调用、stream 收集、工具循环）  
`app/.../di/DataSourceModule.kt`（OkHttp 超时、ProviderManager DI）
