<div align="center">
  <img src="docs/icon.png" alt="App 圖標" width="100" />
  <h1>RikkaHub</h1>

一個原生Android LLM 聊天客戶端，支持切換不同的供應商進行聊天 🤖💬

點擊加入我們的Discord伺服器 👉 [【RikkaHub】](https://discord.gg/9weBqxe5c4)

[English](README.md) | 繁體中文 | [简体中文](README_ZH_CN.md)

</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

## 關於本 Fork

本倉庫是 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 的下游 Fork，會跟隨上游合併，同時發布更偏向智能體工作流的 Android 版本 **RikkaRs**。

和上游相比，使用者能直接感知到的差異包括：

- **獨立應用身份**：安裝後應用名稱顯示為 RikkaRs，並使用本 fork 的圖標與發布版本。
- **更偏智能體的使用方式**：增強子智能體/委託流程，支持斜線技能補全、助手級工作目錄和更清晰的工具呼叫記錄。
- **工作區文件更容易存取**：工作區項目文件可放到 Android 應用專屬外部儲存目錄，便於透過文件管理器、USB 或電腦直接編輯。
- **本地診斷能力**：可查看請求/文字日誌，匯出時自動脫敏；使用者啟用後，也可讓 AI 工具讀取脫敏後的日誌輔助排查。
- **更多設備上下文工具**：在使用者授予權限後，助手可使用螢幕使用時間、日曆等本地設備資訊。
- **供應商匯入更方便**：支持匯入 NewAPI 渠道 JSON，並保留二維碼匯入/匯出供應商配置。
- **遠端存取預設更保守**：內建 Web 服務預設僅監聽本機；未啟用 JWT 且準備開放到區域網路時會給出更明確的提醒。

本 fork 不是上游官方版本；如果你需要上游官方行為與支持，請使用
[rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)。

## 🚀 下載

🔗 [從 GitHub Releases 下載本 fork](https://github.com/Arsucar/rikkahub/releases)（RikkaRs）

🔗 上游官方版本：[官網下載](https://rikka-ai.com/download) / [Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

## 💖 贊助商

|                                         贊助商                                         | 介紹                                                                                                                                              |
|:-----------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="docs/sponsors/aihubmix.png" alt="Aihubmix" width="50" /><br /><b>Aihubmix</b> | 感謝 <a href="https://aihubmix.com?aff=pG7r">aihubmix.com</a> 的資金支持。我們推薦使用 aihubmix 作為全球主流模型的一站式服務平台。（OpenAI、Claude、Google Gemini、DeepSeek、Qwen 以及數百種其他模型）。 |
| <img src="docs/sponsors/suixiang.jpg" alt="隨想AI網關" width="50" /><br /><b>隨想AI網關</b> | 感謝隨想AI網關對本項目的贊助！隨想AI網關 是一家可靠高效的 API 中繼服務提供商，提供 Claude、Codex、Gemini 等的中繼服務。注重隱私的中轉站·無數據倒賣·無模型摻水，隱私，透明，極速售後。新帳戶註冊每日簽到就送 0.5 元測試額度，儲值額度 1:1，無需訂閱，按量付費。多線路冗餘、跨區域容災、自動故障切換，長鏈路 SSE 不中斷。99.9% 可用性，關鍵呼叫從不掉隊。 |

## ✨ 功能特色

- 🎨 現代化安卓APP設計（Material You / 預測性返回）和 🌙 暗色模式
- 📦 工作區：基於 proot 的 Linux 智能體環境
- 🖥️ Web多端訪問支持
- 🛠️ MCP 支持
- 🔄 多種類型的供應商支持，自定義 API / URL / 模型（目前支持 OpenAI、Google、Anthropic）
- 🖼️ 多模態輸入支持
- 📝 Markdown 渲染（支持代碼高亮、數學公式、表格、Mermaid）
- 🔍 搜尋功能（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity、..）
- 🧩 Prompt 變量（模型名稱、時間等）
- 🤳 二維碼導出和導入提供商
- 🤖 智能體自定義
- 🧠 類ChatGPT記憶功能
- 📝 AI翻譯
- 🌐 自定義HTTP請求頭和請求體

## ✨ 貢獻

本項目使用[Android Studio](https://developer.android.com/studio)開發，歡迎提交PR

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
> **Rikka-arsucar fork** 已移除 Firebase，**不需要** `google-services.json`。詳見 [docs/RIKKA_ARSUCAR_FORK_AND_CI.md](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

> [!IMPORTANT]  
> 以下PR將被拒絕：
> 1. 添加新語言，因為添加新語言會增加後續本地化的工作量
> 2. 添加新功能，這個項目是有態度的
> 3. AI生成的大規模重構和更改

## 💰 捐贈

* [Patreon](https://patreon.com/rikkahub)
* [愛發電](https://afdian.com/a/reovo)

## ⭐ Star History

如果喜歡這個項目，請給個Star ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=re-ovo/rikkahub&type=Date)](https://star-history.com/#re-ovo/rikkahub&Date)

## 📄 許可證

[License](LICENSE) 
