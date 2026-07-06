<div align="center">
  <img src="docs/icon.png" alt="RikkaRs App 圖標" width="100" />
  <h1>RikkaRs</h1>

一個基於 RikkaHub 的原生 Android LLM 聊天客戶端 Fork，面向智能體工作流、
本地工作區與多供應商對話體驗。

[English](README.md) | 繁體中文 | [简体中文](README_ZH_CN.md)
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

## 關於本 Fork

本倉庫是 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 的下游 Fork，會跟隨上游合併，
同時從 [Arsucar/rikkahub](https://github.com/Arsucar/rikkahub) 發布獨立 Android 版本 **RikkaRs**。

RikkaRs 不是上游官方版本；如果你需要上游官方發布渠道、包名或支持，請使用上游 RikkaHub。

## RikkaHub vs RikkaRs

| 維度 | 上游 RikkaHub（`rikkahub/rikkahub`） | 本 Fork RikkaRs（`Arsucar/rikkahub`） |
|------|--------------------------------------|--------------------------------------|
| Release 包名 | `me.rerere.rikkahub` | `me.arsucar.rikka`；Debug 為 `me.arsucar.rikka.debug` |
| Kotlin namespace | `me.rerere.rikkahub` | 仍保留 `me.rerere.rikkahub`，降低合併上游成本 |
| 應用名 | RikkaHub | RikkaRs |
| 發行渠道 | 官網與 Google Play | 本 fork 的 GitHub Releases |
| Firebase | 上游可能使用 Firebase 服務 | 已移除 Firebase，不需要 `google-services.json` |
| CI 與發版 | 上游 workflow | 僅使用 `Release APK (arm64)`；arm64 APK、無 Firebase、CI 構建 web-ui |
| 子代理 | 上游智能體/工具行為 | 並行、排隊、委託子代理，支持深度/並發限制、transcript 卡片、取消與子代理專用 `finish_work` |
| Skills | 全局 Skill 支持 | 全局與助手私有 Skill、私有副本管理、更安全的文件作用域、斜線補全與更清晰的目錄卡片 |
| 記憶 | 類 ChatGPT 記憶 | 助手私有/全局記憶作用域，新增默認關閉的記憶表、模板、文件與作用域控制 |
| 工作區 | 基於 proot 的工作區 | 外部應用專屬儲存、全屏文字編輯、Markdown 只讀渲染預覽、dotfile/配置文件識別與 `/tmp` 寫入便利 |
| NewAPI 匯入 | 供應商二維碼/匯入流程 | 支持 NewAPI `newapi_channel_conn` JSON，與二維碼/分享格式自動分流 |
| 日誌與 `get_logs` | 應用日誌 UI | 日誌頁匯出、長按多選匯出、AI `get_logs` 工具、截斷與更適合工具讀取的摘要 |
| 脫敏匯出 | 不是本 fork 重點 | 匯出與 `get_logs` 會脫敏 Authorization、API Key、Cookie、URL 密鑰與請求體密鑰 |
| 會話歸檔 | 合併上游後可用 | 保留歸檔能力，並兼容歸檔搜尋/列表 |
| 會話文件夾 | 合併上游後可用 | 保留按助手分組的會話文件夾 |
| Web 訪問 | 內建 Web 服務 | 默認只監聽 localhost；未啟用 JWT 且準備開放 LAN 時給出警告 |
| 螢幕時間/日曆 | 不是上游核心差異 | 授權後可讓本地工具讀取螢幕使用時間、查詢/建立日曆事件 |
| 供應商標籤 | 基礎供應商設定 | `provider.tags` 與設定頁 Tag 篩選 |
| 模型列表 | 標準模型選擇 | 按供應商折疊、收藏區折疊、一鍵展開/收起、供應商標籤過濾與模型收藏分組 |
| 隱藏上下文 | 刪除/壓縮行為 | 隱藏消息作為軟刪除；隱藏節點仍可見但不進上下文，壓縮上下文默認隱藏舊消息而非硬刪 |
| 會話級模型覆蓋 | 助手默認模型 | 單會話模型覆蓋與一鍵清回助手默認，新會話不繼承舊會話覆蓋 |
| 收藏 | 消息/收藏基礎能力 | 模型收藏、圖生收藏、收藏集合，以及分組/折疊的收藏視圖 |
| Web localhost + JWT | 可配置 Web 認證 | 默認 localhost，並在遠端訪問未啟用 JWT 時明確提示風險 |
| 工程說明 | 上游約定 | Fork 包名、CI、發版與 Firebase 決策見 [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md) |

## 🚀 下載

🔗 [從 GitHub Releases 下載 RikkaRs](https://github.com/Arsucar/rikkahub/releases)

🔗 上游官方版本：[官網下載](https://rikka-ai.com/download) / [Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

## 💖 贊助商

|                                         贊助商                                         | 介紹                                                                                                                                              |
|:-----------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="docs/sponsors/aihubmix.png" alt="Aihubmix" width="50" /><br /><b>Aihubmix</b> | 感謝 <a href="https://aihubmix.com?aff=pG7r">aihubmix.com</a> 的資金支持。我們推薦使用 aihubmix 作為全球主流模型的一站式服務平台。（OpenAI、Claude、Google Gemini、DeepSeek、Qwen 以及數百種其他模型）。 |
| <img src="docs/sponsors/suixiang.jpg" alt="隨想AI網關" width="50" /><br /><b>隨想AI網關</b> | 感謝隨想AI網關對本項目的贊助！隨想AI網關 是一家可靠高效的 API 中繼服務提供商，提供 Claude、Codex、Gemini 等的中繼服務。注重隱私的中轉站·無數據倒賣·無模型摻水，隱私，透明，極速售後。新帳戶註冊每日簽到就送 0.5 元測試額度，儲值額度 1:1，無需訂閱，按量付費。多線路冗餘、跨區域容災、自動故障切換，長鏈路 SSE 不中斷。99.9% 可用性，關鍵呼叫從不掉隊。 |

## ✨ 功能特色

本列表已對齊本 fork 的 CHANGELOG 至 **v2.3.19**。

- 🎨 Material You 設計、預測性返回與暗色模式
- 🔄 多供應商支持：自定義 API 地址、URL、請求頭、請求體與模型列表
- 🧩 供應商標籤、Tag 篩選、NewAPI 渠道 JSON 匯入，以及二維碼匯入/匯出供應商
- ⭐ 可折疊模型選擇器：按供應商分組、收藏模型、收藏區、一鍵展開/收起與標籤過濾
- 🖼️ 多模態聊天輸入：圖片、文件、PDF、DOCX 與常見文字文件
- 📝 Markdown 渲染：代碼高亮、LaTeX 公式、表格、Mermaid、粗體修復，以及工作區 Markdown 只讀渲染預覽
- 🪾 消息分支、隱藏消息、隱藏上下文壓縮、會話歸檔、會話文件夾與會話級模型覆蓋
- 📦 Proot 工作區：Shell/文件工具、外部儲存、全屏文字編輯、更保守的 shell 策略與更清晰的 shell transcript
- 🤖 助手自定義與子代理：委託、並行/排隊執行、限制、transcript 預覽、取消與 `finish_work`
- 🛠️ MCP 支持：OAuth 2.1、令牌刷新與按需重連
- 🧠 助手/全局記憶作用域，以及默認關閉的記憶表
- 🧠 Skills 庫：斜線補全、全局/私有副本、更安全的文件訪問與優化後的 Skills 目錄卡片
- 🔍 搜尋能力：Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等，並支持搜尋結果圖片
- 🖥️ 內建 Web 訪問：默認 localhost，僅在明確配置後開放遠端，並提示 JWT 風險
- 📊 本地診斷：日誌頁、多選脫敏匯出，以及 AI 可讀取的 `get_logs`
- 📱 授權後可用的本地工具：螢幕使用時間、日曆查詢與建立
- 📝 AI 翻譯、Prompt 變量、SillyTavern 角色卡匯入、助手頭像裁剪、圖生收藏與收藏集合

## ✨ 貢獻

本項目使用[Android Studio](https://developer.android.com/studio)開發，歡迎提交PR。

技術棧文檔:

- [Kotlin](https://kotlinlang.org/) (開發語言)
- [Koin](https://insert-koin.io/) (依賴注入)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI 框架)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore?hl=zh-cn#preferences-datastore) (
  偏好數據存儲)
- [Room](https://developer.android.com/training/data-storage/room) (數據庫)
- [Coil](https://coil-kt.github.io/coil/) (圖片加載)
- [Material You](https://m3.material.io/) (UI 設計)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (導航)
- [Okhttp](https://square.github.io/okhttp/) (HTTP 客戶端)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (Json序列化)

> [!TIP]
> **RikkaRs fork** 已移除 Firebase，**不需要** `google-services.json`。詳見 [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

> [!IMPORTANT]
> 以下PR將被拒絕：
> 1. 添加新語言，因為添加新語言會增加後續本地化的工作量
> 2. 添加新功能，這個項目是有態度的
> 3. AI生成的大規模重構和更改

## 💰 捐贈

* [Patreon](https://patreon.com/rikkahub)
* [愛發電](https://afdian.com/a/reovo)

## ⭐ Star History

如果喜歡 RikkaRs，請給這個 fork 一個 Star ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=Arsucar/rikkahub&type=Date)](https://star-history.com/#Arsucar/rikkahub&Date)

## 📄 許可證

[License](LICENSE)
