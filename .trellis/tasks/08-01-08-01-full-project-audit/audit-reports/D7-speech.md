# D7 Speech Module Audit (TTS + ASR)

**Scope:** `speech/` (TTS/ASR implementations) + app integration (`ui/hooks/TTS.kt`, `ui/hooks/ASR.kt`, chat input/actions, settings, tools)  
**Mode:** static read-only analysis (no Gradle / no runtime)  
**Date:** 2026-08-01  
**Module ID:** D7

---

## 1. 链路梳理

### 1.1 TTS 端到端

```
Settings (selectedTTSProviderId, ttsProviders, defaultTTSPlaybackSpeed)
  → PreferencesStore / SettingsStore
  → rememberCustomTtsState() [RouteActivity CompositionLocal]
  → CustomTtsStateImpl → TtsController(context, TTSManager)
       ├─ TextChunker.split(text)
       ├─ TtsSynthesizer → TTSManager.generateSpeech(provider, TTSRequest)
       │     └─ providers/* (System / OpenAI / Gemini / MiniMax / …)
       └─ AudioPlayer (ExoPlayer) play(TTSResponse)

触发入口:
  1) ChatMessageActions 朗读按钮 → LocalTTSState.speak / stop
  2) TTSAutoPlay(generationDoneFlow) → autoPlayTTSAfterGeneration
  3) text_to_speech 工具 → AppEvent.Speak → RouteActivity LaunchedEffect → tts.speak
  4) SettingSpeechPage 试听
  5) TTSController 悬浮条: pause/resume/stop/speed/fastForward
```

### 1.2 ASR 端到端

```
Settings (selectedASRProviderId, asrProviders)
  → rememberCustomAsrState()
  → CustomAsrStateImpl → createController(provider)
       OpenAIRealtime / DashScope / Volcengine / MiMo / Step
  → AudioFocus (GAIN_TRANSIENT_EXCLUSIVE, VOICE_COMMUNICATION)
  → AudioRecord (VOICE_COMMUNICATION, PCM16 mono)
  → WS 流式 or HTTP 分段 → onTranscriptChange(transcript)

触发入口:
  ChatInput AsrButton
    → PermissionRecordAudio
    → asrBaseText = 当前输入
    → asr.start { transcript → setMessageText(base + " " + transcript) }
    → Listening 再点 → asr.stop()
```

### 1.3 生命周期绑定

| 组件 | 创建/销毁 |
|------|----------|
| `rememberCustomTtsState` | `remember {}` + `DisposableEffect` → `cleanup()` → `TtsController.dispose()` |
| `rememberCustomAsrState` | 同上 → `controller.dispose()` + abandon focus |
| Provider 切换 | TTS: `setProvider`; ASR: `dispose` 旧 controller + 新建 |
| 全局挂载 | `RouteActivity.AppRoutes` CompositionLocal + `TTSController()` 悬浮窗 |

### 1.4 默认倍速（近期 “TTS 默认倍速”）

- 持久化键: `DEFAULT_TTS_PLAYBACK_SPEED`（`PreferencesStore`）
- 范围: `coerceIn(0.5f, 2.0f)`，默认 `1.0f`
- 设置 UI: `SettingSpeechPage` → `TTSPlaybackSpeedSetting`
- 应用: `DisposableEffect(…, defaultTTSPlaybackSpeed)` → `ttsState.setSpeed` → `AudioPlayer.setSpeed` → ExoPlayer `PlaybackParameters`
- 会话内可被 `TTSController.SpeedButton` 临时改写；**不回写 Settings**（仅播放态）

---

## 2. 问题清单

### F7-1 — TTS 无 AudioFocus / AudioAttributes

- **file:line:** `speech/.../AudioPlayer.kt:35`, `AudioPlayer.kt:49-52`
- **severity:** HIGH
- **description:** ExoPlayer 以默认配置构建，未设置 `AudioAttributes`（USAGE_ASSISTANCE_ACCESSIBILITY / MEDIA 等），也未 `requestAudioFocus`。与 ASR 的 `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` 不对称。结果：TTS 可能与导航/音乐/通话抢声道；ASR 录音时 TTS 仍可出声；系统音量/专注模式行为不可控。
- **evidence:**
```kotlin
private val player = ExoPlayer.Builder(context).build()
// no setAudioAttributes, no AudioFocusRequest
fun setSpeed(speed: Float) {
    player.playbackParameters = PlaybackParameters(speed)
}
```
- **suggested fix:** `ExoPlayer.Builder` + `AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(CONTENT_TYPE_SPEECH)`；播放前 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`；`onAudioFocusChange` 暂停/恢复；`stop/dispose` abandon。

### F7-2 — ASR 错误路径不释放 mic / WebSocket / 焦点

- **file:line:** `OpenAIRealtimeASRController.kt:241-248`, `DashScopeASRController.kt:250-257`, `VolcengineASRController.kt:302+`；`app/.../ASR.kt:89-94`
- **severity:** HIGH
- **description:**
  1) 多数 provider 的 `setError()` 只改 `status=Error`，**不** `releaseRecorder()` / `webSocket.close()`。`handleServerEvent("error")` 后录音循环与 socket 仍可能存活 → 泄漏 mic、持续上行流量。
  2) `CustomAsrStateImpl.start` 焦点被拒时 **静默 return**，无 Error 状态、无 toast 路径（`errorMessage` 仍 null）。
  3) `Error` 后 `ChatInput` 可再点 start，但旧 WS/recorder 可能仍占用设备。
- **evidence:**
```kotlin
private fun setError(message: String) {
    _state.update { it.copy(status = ASRStatus.Error, errorMessage = message) }
    // no releaseRecorder / webSocket.close / abandon focus
}
// ASR.kt
val result = audioManager.requestAudioFocus(audioFocusRequest)
if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
    controller?.start(onTranscriptChange)
} // else: silent
```
- **suggested fix:** `setError` 统一走 `failAndTeardown(message)`：cancel recorderJob、release AudioRecord、close WS、clear callback；`start` 焦点失败写 Error；`stop/cleanup` 已 abandon focus，Error 路径也要 abandon。

### F7-3 — TTSAutoPlay 忽略 conversationId，存在串话/漏播

- **file:line:** `app/.../TTSAutoPlay.kt:20-35`；`ChatService.kt` 多处 `_generationDoneFlow.emit(conversationId)`
- **severity:** HIGH
- **description:** `generationDoneFlow` 携带完成会话的 `Uuid`，但 collector **完全忽略** `conversationId`，改用 `rememberUpdatedState(conversation)` 的「当前 UI 会话」最后一条 ASSISTANT。若用户在生成完成前切到另一会话，可能朗读错误会话内容，或当前会话实际完成却读了另一条消息；多会话并发生成时更糟。
- **evidence:**
```kotlin
vm.generationDoneFlow.collect { conversationId ->
    if (updatedSetting.displaySetting.autoPlayTTSAfterGeneration) {
        val lastMessage = currentConversation.currentMessages.lastOrNull()
        // conversationId unused
        tts.speak(textToSpeak)
    }
}
```
- **suggested fix:** `if (conversationId != currentConversation.id) return@collect`；或按 id 从 repository 取最终消息再 speak。

### F7-4 — `totalChunks` / 进度语义错误（队列剩余 vs 总会话）

- **file:line:** `TtsController.kt:131`, `TtsController.kt:240-247`, `TTSController.kt:167-171`
- **severity:** MEDIUM
- **description:** `speak` 时 `_totalChunks = queue.size`；worker 每轮 `_totalChunks = queue.size + 1`（剩余含当前）。UI 双环进度 `currentChunkIndex / totalChunks` 随播放 **分母不断缩小**，进度条会“回退/乱跳”，且 `totalChunks` 不再表示会话总片数。`allChunks.size` 才是稳定总数。
- **evidence:**
```kotlin
_totalChunks.update { queue.size }           // speak
_totalChunks.update { queue.size + 1 }       // worker poll
// UI:
playbackState.currentChunkIndex.toFloat() / playbackState.totalChunks
```
- **suggested fix:** 固定 `totalChunks = allChunks.size`；`currentChunk` 用 1-based index；UI 除零保护。

### F7-5 — SystemTTS 每 chunk 新建引擎 + 临时文件竞态/泄漏

- **file:line:** `SystemTTSProvider.kt:29-113`
- **severity:** HIGH（资源/稳定性）
- **description:** 每个合成请求 `TextToSpeech(context, listener)` + `synthesizeToFile` 到 `appTempFolder`。预取窗口 `prefetchCount=4` 可并发 4 个引擎；取消时 `invokeOnCancellation { tts?.shutdown() }` 但 **不删 temp 文件**；`onDone` 与 cancel 竞态可能 double-shutdown 或文件残留。系统 TTS 初始化成本高，长文分片会卡顿/失败率上升。
- **evidence:**
```kotlin
val audioFile = File(tempDir, "tts_${System.currentTimeMillis()}.wav")
// ...
continuation.invokeOnCancellation { tts?.shutdown() } // no audioFile.delete()
tts = TextToSpeech(context, listener)
```
- **suggested fix:** 单例/池化 System TTS；`withContext` 串行化合成；cancellation 与 onDone 互斥锁；finally 删除 temp；考虑 `FILE` 以外的流式 API 若可用。

### F7-6 — ASR `updateProvider` 在录音中直接 dispose，无用户可见过渡

- **file:line:** `app/.../ASR.kt:81-86`, `39-41`
- **severity:** MEDIUM
- **description:** Settings 变更触发 `DisposableEffect` → `controller?.dispose()` 再新建。录音中切换 provider 会硬切，可能丢最终 flush（MiMo/Step 的 stop flush 被 cancel）、焦点未按 stop 路径 abandon（dispose 会 abandon，但 ChatInput 的 asrBaseText 注入半成品）。
- **evidence:**
```kotlin
fun updateProvider(provider: ASRProviderSetting?) {
    controller?.dispose()
    controller = provider?.let { createController(it) }
}
```
- **suggested fix:** 若 `isRecording` 先 `stop()` 等待 Idle 再换；或 UI 录音中禁用 provider 切换。

### F7-7 — 设置页倍速与悬浮条倍速双源冲突

- **file:line:** `TTS.kt:50-57`, `TTSController.kt:189-211`, `PreferencesStore.kt:325/658`
- **severity:** MEDIUM
- **description:** 默认倍速来自 Settings；悬浮条 `SpeedButton` 只改 ExoPlayer 运行时 speed。任意 Settings 重算（provider 列表/选中 id/默认倍速）的 `DisposableEffect` 会 **强制 `setSpeed(defaultTTSPlaybackSpeed)`**，覆盖用户当前会话调速。反之悬浮条调速不持久化，与“默认倍速”产品语义易混淆。SystemTTS 另有 `speechRate`（合成侧），再叠 ExoPlayer speed → 实际语速 = speechRate × playbackSpeed。
- **evidence:**
```kotlin
DisposableEffect(settings.selectedTTSProviderId, settings.ttsProviders, settings.defaultTTSPlaybackSpeed) {
    ttsState.updateProvider(settings.getSelectedTTSProvider())
    ttsState.setSpeed(settings.defaultTTSPlaybackSpeed)
    onDispose { }
}
```
- **suggested fix:** 仅当 `defaultTTSPlaybackSpeed` 变化时应用；会话 speed 单独 State；或悬浮条写回 Settings；文档说明 System speechRate 与 playback speed 关系。

### F7-8 — `AudioPlayer.play` 暂停时 `onIsPlayingChanged` 把状态打成 Paused，但取消路径外 listener 可能堆积

- **file:line:** `AudioPlayer.kt:76-132`
- **severity:** MEDIUM
- **description:** 每次 `play` 添加新 `Player.Listener`，仅在 ENDED/Error/cancel 时 remove。若 `player.stop()` 来自外部 `stop()` 而未走 cont cancellation（例如 `clear()` 后状态 IDLE），listener 可能残留；连续 speak/stop 可能多 listener 回调交错更新 `_playbackState`。
- **evidence:**
```kotlin
player.addListener(listener)
cont.invokeOnCancellation {
    player.removeListener(listener)
    player.stop()
    stopPositionUpdates()
}
// STATE_IDLE path does not removeListener or resume cont
```
- **suggested fix:** `stop/clear` 与 play 共享 generation token；IDLE/stop 时若 cont.active 则 cancel/resume；单 listener 成员复用。

### F7-9 — 合成失败仍递增 processedCount 并继续，用户可能听到残缺朗读且 error 可被后续覆盖

- **file:line:** `TtsController.kt:253-270`
- **severity:** MEDIUM
- **description:** synthesis 失败：写 `_error`、skip 该 chunk、继续队列。playback 失败同理。长文多失败时只保留最后一次 `error`；`isSpeaking` 仍 true 直到队列空。无失败重试/无“失败即停”策略开关。
- **evidence:**
```kotlin
} catch (e: Exception) {
    if (e is CancellationException) throw e
    _error.update { e.message ?: "TTS synthesis error" }
    processedCount++
    continue
}
```
- **suggested fix:** 可配置 fail-fast；错误列表或 sticky error 直到下次 speak；失败 chunk 可重试一次。

### F7-10 — ASR 转录回调可能在非主线程改 Compose 状态（取决于 OkHttp 线程）

- **file:line:** `OpenAIRealtimeASRController.kt:231-238`；`ChatInput.kt:364-367`
- **severity:** MEDIUM
- **description:** `publishTranscript` 用 `scope.launch`（Main.immediate）再调 `onTranscriptChange`——OpenAI/DashScope/Volc 路径相对安全。但 `WebSocketListener.onMessage` 若直接调 `publishTranscript` 内 state update 已在 listener 线程；`_state.update` 本身线程安全，而 `onTranscriptChange` 最终 `state.setMessageText` 必须在主线程。当前 `scope.launch` 保护了 callback；**若未来同步调用 callback 会炸**。更现实的问题：`ChatInput` 每次 partial 全量 `setMessageText`，高频 delta 导致输入框重组抖动/光标丢失风险。
- **evidence:**
```kotlin
asr.start { transcript ->
    state.setMessageText(asrBaseText + spacer + transcript)
}
```
- **suggested fix:** 保持 Main dispatcher 强制；partial 用 debounce；或只在 Stopping/Idle 最终提交（流式预览用独立 Text）。

### F7-11 — TTS 预取缓存持有完整音频 ByteArray，长文内存压力

- **file:line:** `TtsController.kt:54-55`, `285-308`, `TtsSynthesizer.kt:29-42`
- **severity:** MEDIUM
- **description:** `cache: ConcurrentHashMap<UUID, Deferred<TTSResponse>>` 在 stop 前不清已完成项；`collectToResponse` 把整段音频读入内存。长助手回复 × prefetch 4 × 多 provider 失败重试 → 堆内存上升。`finally` 注释写“保留便于重播”但无重播 API。
- **evidence:**
```kotlin
cache.computeIfAbsent(chunk.id) {
    scope.async(Dispatchers.IO) { synthesizer.synthesize(provider, chunk) }
}
// await 后不 remove
```
- **suggested fix:** 播放完 `cache.remove(id)`；限制 in-flight；大音频落盘流式播放。

### F7-12 — pause 后 `resume` 无条件把 status 设为 Playing

- **file:line:** `TtsController.kt:164-176`
- **severity:** LOW/MEDIUM
- **description:** 若 pause 发生在 Buffering/合成阶段（worker 在 `awaitOrCreate`，ExoPlayer 未播），`resume()` 仍 `audio.resume()` + `status=Playing`，与真实状态不符；`isPaused=false` 后 worker 继续，尚可，但 UI 短暂显示 Playing。
- **suggested fix:** resume 时根据 `player.isPlaying` / 是否有 active play cont 设置状态。

### F7-13 — `skipNext` 只 poll 队列，不更新 current/total 与 cache

- **file:line:** `TtsController.kt:188-194`
- **severity:** LOW
- **description:** 跳过下一段不取消对应 prefetch Deferred，仍占网络/内存；`_totalChunks` 更新为 `queue.size` 再次扭曲进度。
- **suggested fix:** poll 后 cancel `cache[id]`；进度用 allChunks 语义。

### F7-14 — speech 模块 Manifest 为空，依赖 app 声明 RECORD_AUDIO

- **file:line:** `speech/src/main/AndroidManifest.xml:1-4`；`app/.../AndroidManifest.xml` 含 `RECORD_AUDIO`
- **severity:** LOW
- **description:** library 未声明 `RECORD_AUDIO`，合并依赖 app。若其他 app 仅依赖 speech 模块会缺权限。当前 monorepo 可接受。
- **suggested fix:** speech manifest 增加 `uses-permission RECORD_AUDIO`。

### F7-15 — TTSController UI 除零与结束后悬浮窗不自动关闭

- **file:line:** `app/.../TTSController.kt:49-54`, `156-171`
- **severity:** LOW
- **description:** `isSpeaking` 变 true 才显示，结束后 `isVisible` 仍 true（需手动取消）。`durationMs==0` 或 `totalChunks==0` 时 progress 为 NaN/Inf 风险。
- **suggested fix:** Ended 自动收起或延迟收起；progress coerce 分母 `max(1, …)`。

### F7-16 — 云 TTS provider 各自 new OkHttpClient，无统一超时/取消与证书配置

- **file:line:** `OpenAITTSProvider.kt:23-25`（同类 providers 模式）
- **severity:** LOW/MEDIUM
- **description:** 每个 provider 私有 `OkHttpClient`，不复用 app 的 Koin `OkHttpClient`（含代理/日志/证书）。与 ASR 注入共享 client 不一致；连接池膨胀；取消依赖 call 所在协程 cancel（execute 可中断但行为依赖版本）。
- **suggested fix:** TTSManager 注入共享 OkHttpClient；`Call` 与 coroutine cancellation 绑定。

### F7-17 — ASR Connecting/Stopping 时按钮 no-op，Error 可重启但未 teardown（叠加 F7-2）

- **file:line:** `ChatInput.kt:356-373`
- **severity:** MEDIUM（与 F7-2 联动）
- **description:** `Connecting/Stopping` 点击忽略，无法强制取消连接中的 WS；用户可能长时间卡在 Connecting（网络 hang 时无超时）。
- **evidence:**
```kotlin
ASRStatus.Connecting, ASRStatus.Stopping -> {}
```
- **suggested fix:** Connecting 允许 cancel → stop/dispose；为 WS 连接加超时。

### F7-18 — `CustomTtsStateImpl` 无用字段 / 死代码

- **file:line:** `TTS.kt:131-133`, `178`
- **severity:** LOW
- **description:** `scope`、`currentJob` 创建后几乎不用（仅 cleanup 置 null），增加维护噪音。
- **suggested fix:** 删除死字段。

---

## 3. 亮点 / 可复用

1. **TtsController 分层清晰**：Chunker / Synthesizer / AudioPlayer / Controller 职责分离，StateFlow 对外 API 稳定，便于替换 provider。
2. **多 ASR 后端统一 `ASRController` 接口**：OpenAI Realtime、DashScope、Volcengine 二进制协议、MiMo/Step HTTP 分段 —— 扩展点干净。
3. **MiMo/Step 分段 flush 设计**（`MAX_SEGMENT_BYTES`、单 flight flushJob、stop 时 final flush）对 HTTP-only ASR 是正确工程取舍。
4. **Audio amplitude 可视化**（RMS → dB 映射 + 环形 buffer）轻量且 UI 效果好。
5. **默认倍速持久化 + coerce 范围** 与设置页联动完整。
6. **ASR 权限走统一 PermissionManager + RECORD_AUDIO**，按钮级请求体验正确。
7. **text_to_speech 工具 + AppEvent.Speak + promptGuidance** 把 provider 语气提示注入 system prompt，产品化程度高。
8. **CompositionLocal 单例 TTS/ASR 状态** 避免每页重建引擎。

---

## 4. 遗漏与风险

| 区域 | 风险 |
|------|------|
| 屏幕熄灭 / 后台 | 无 Lifecycle/ProcessLifecycle 绑定；后台 TTS 可能被杀或继续播（无前台服务/媒体会话） |
| 配置变更 | TTS/ASR 挂在 Activity Composition；config change 若 Activity 重建会 dispose 中断播放/录音（`applicationContext` 减缓了 Context 泄漏但状态仍丢） |
| 通话/蓝牙 | 无 AudioDeviceCallback；VOICE_COMMUNICATION 源在部分 ROM 路由怪异 |
| 并发 recognizer | 全局单 `CustomAsrState`，不会双开；但 Error 未 teardown 时“单实例”仍占 mic |
| 安全 | API key 在 provider 设置中明文 prefs；WS header 带 key（行业常规，需备份加密意识） |
| 测试 | speech 单测偏 Setting 序列化；缺 Controller 竞态/焦点/权限仪器测试 |
| 无障碍 | TTS 未声明为 accessibility 媒体会话；锁屏控制缺失 |
| 自动朗读 | 未校验 conversationId（F7-3）；多 tab/多窗口未验证 |
| SystemTTS 语言 | 仅 `Locale.getDefault()`，忽略内容语言 |
| 主线程 | ExoPlayer 操作在 Main（正确）；合成在 IO（正确）；部分 provider `execute()` 在 IO flow（正确） |

---

## 5. 严重度汇总

| ID | Sev | 一句话 |
|----|-----|--------|
| F7-1 | HIGH | TTS 无 AudioFocus/Attributes |
| F7-2 | HIGH | ASR Error 不释放 mic/WS/焦点；焦点拒绝静默 |
| F7-3 | HIGH | AutoPlay 忽略 conversationId 串话 |
| F7-5 | HIGH | SystemTTS 每片新建引擎+临时文件 |
| F7-4 | MEDIUM | totalChunks 进度语义错误 |
| F7-6 | MEDIUM | 录音中切换 provider 硬 dispose |
| F7-7 | MEDIUM | 默认倍速与会话倍速互相覆盖 |
| F7-8 | MEDIUM | ExoPlayer listener 生命周期 |
| F7-9 | MEDIUM | 合成失败跳过继续 |
| F7-10 | MEDIUM | ASR 高频全量改输入框 |
| F7-11 | MEDIUM | 音频缓存内存 |
| F7-17 | MEDIUM | Connecting 无法取消/无超时 |
| F7-12 | LOW | resume 状态虚高 |
| F7-13 | LOW | skipNext 不取消 prefetch |
| F7-14 | LOW | speech manifest 空 |
| F7-15 | LOW | 悬浮窗/除零 |
| F7-16 | LOW | 独立 OkHttpClient |
| F7-18 | LOW | 死代码 |

**CRITICAL:** 无（未发现确定性崩溃契约级缺陷；最严重为资源泄漏与串话 HIGH）。

---

## 6. 建议修复优先级

1. **P0:** F7-2（ASR teardown）、F7-3（AutoPlay id）、F7-1（TTS focus）  
2. **P1:** F7-5 SystemTTS 池化、F7-4 进度、F7-17 连接取消/超时  
3. **P2:** F7-7 倍速模型、F7-8/11 播放器与缓存、F7-6 provider 切换  
4. **P3:** LOW 清理与 manifest  

---

## 7. 文件索引（审计触及）

**speech:** `TtsController`, `AudioPlayer`, `TtsSynthesizer`, `TextChunker`, `TTSManager`, `SystemTTSProvider`, `OpenAITTSProvider`, `ASRController`, `ASRState`, `AudioAmplitude`, `OpenAIRealtimeASRController`, `DashScopeASRController`, `VolcengineASRController`, `MiMoASRController`, `StepASRController`, `TTSProviderSetting`, `ASRProviderSetting`  

**app:** `ui/hooks/TTS.kt`, `ui/hooks/ASR.kt`, `ChatInput.kt`, `AsrButton.kt`, `TTSController.kt`, `TTSAutoPlay.kt`, `ChatMessageActions.kt`, `TextToSpeechTool.kt`, `RouteActivity.kt`, `PreferencesStore.kt`, `SettingSpeechPage.kt`
