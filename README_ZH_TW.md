<div align="center">
  <img src="docs/icon.png" alt="RikkaRs 應用程式圖示" width="100" />
</div>

# RikkaRs

RikkaRs 是源自 RikkaHub、由 Arsucar 獨立維護和發布的原生 Android LLM 用戶端，專注代理工作流程、本機工作區與多 Provider 對話。

[English](README_EN.md) | [简体中文](README.md) | **繁體中文**

[![最新版本](https://img.shields.io/github/v/release/Arsucar/RikkaRs?label=release)](https://github.com/Arsucar/RikkaRs/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![ABI arm64-v8a](https://img.shields.io/badge/ABI-arm64--v8a-blue)](app/build.gradle.kts)
[![分段雙重授權](https://img.shields.io/badge/license-segmented%20dual-orange)](LICENSE)

**[下載最新穩定版](https://github.com/Arsucar/RikkaRs/releases/latest)**

<div align="center">
  <img src="docs/img/chat.png" alt="聊天介面" width="150" />
  <img src="docs/img/desktop.png" alt="模型選擇器" width="450" />
</div>

## 關於 RikkaRs

RikkaRs 的程式碼源自 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)，由 Arsucar 在獨立儲存庫 [Arsucar/RikkaRs](https://github.com/Arsucar/RikkaRs) 中獨立維護和發布。專案按需同步有價值的上游變更，同時依循自己的產品藍圖與發布節奏。

RikkaRs 是非官方發行版，與 RikkaHub 專案及其維護者沒有隸屬、關聯或背書關係。獨立的 Release 套件名稱 `me.arsucar.rikka` 讓它能與官方 RikkaHub 共存；專案已移除 Firebase，不需要 `google-services.json`。

## 功能特色

### 子代理

- 委派子代理平行執行或排隊運作，可分別限制並行數與呼叫深度，並可隨時取消執行。
- 將所需上下文傳入子代理並查看完整 transcript；即時觀察 token 用量、工具呼叫與執行狀態。
- 子代理透過專用 `finish_work` 訊號明確完成工作，讓主代理能可靠收尾。
- 每個子代理設定檔可獨立配置模型、工作目錄（CWD）、工具權限，以及 token、工具呼叫、耗時、深度和並行等預算。
- 在相容的 scope 下續接原有歷史；完整上下文會持久保存，並可在應用程式或程序重新啟動後，基於持久化上下文繼續未完成工作。

### Skills

- 管理全域與助手私有 Skills，並依 scope 安全地向工作區和子代理提供所需 Skill。
- 透過斜線補全從對話中明確啟用 Skill，並瀏覽更清楚的 Skill 目錄卡片。
- 助手可在確認後建立或更新 Skill；私有副本與檔案存取邊界維持隔離。

### 記憶

- 記憶支援全域、助手和對話三種 scope，可同步、跟隨或解除關聯，避免不同使用情境互相污染。
- 同時支援一般記憶與結構化表格記憶；表格可定義範本，並按需建立及維護文件與資料列。
- 可設定總注入預算、僅注入相關資料列，並為每張表選擇獨立的擷取與注入策略。
- 支援寫入控制、快照與回復、匯入與匯出；未注入的記憶仍可由工具按需檢索。

### 上下文控制

- 隱藏訊息採用軟刪除：舊訊息會從模型上下文排除，但仍保留於對話樹中，可供查看與復原。
- 支援手動壓縮與超限自動壓縮；壓縮會隱藏並保留舊訊息，而非永久刪除。
- 可設定保留的最近訊息數量與壓縮偏好，並在後續對話中沿用。
- 傳送前可用唯讀的最終上下文檢查器，核對完成組裝、注入與轉換後的實際訊息。
- 支援對話層級模型覆寫，以及最近模型和提示詞預設的快速切換；新對話不會誤用舊對話的覆寫設定。

### 工作區與檔案

- 使用統一的 **PRoot** 工作區，內建 Shell 與檔案工具；也可選擇應用程式專屬的外部工作區。
- 外部工作區可透過系統檔案管理器存取，方便經由 USB 或 PC 管理與交換檔案。
- 支援全螢幕文字編輯、Markdown 渲染預覽，並可將任意類型檔案作為對話附件。
- 工作區圖片可作為多模態工具結果回傳；非視覺模型會取得明確的替代說明。
- Shell 與子代理改動過的檔案會以 chip 顯示在訊息下方，方便開啟、追蹤與繼續處理。

### Provider 與模型

- 為多個 Provider 設定自訂 Host、URL、Header、Body 和模型清單。
- 以標籤組織和篩選 Provider；模型選擇器支援按 Provider 分組、搜尋、摺疊、收藏及一鍵展開／收合。
- 可為每個 Provider 分別設定本機 RPM/TPM，在觸發伺服器端限制前控制請求節奏。
- 支援匯入 NewAPI 渠道 JSON，以及匯入和匯出**相容的 Provider 分享 QR Code**。
- 可集中整理多個渠道與公益站設定，同時保留清楚的 Provider 邊界和篩選能力。

### 預設與擴充

- 將多個提示詞項目組合為提示詞預設，並以預設層級的 `ModeInjection` 隔離不同模式的注入內容。
- 為助手與子代理設定檔關聯合適的預設，按任務定向注入指令。
- 連接 MCP 伺服器時支援 OAuth 2.1、PKCE、動態用戶端註冊、權杖更新與斷線重連。

### 對話與介面

- 支援對話封存與資料夾整理；助手可封存、還原，並能接續其最近對話繼續工作。
- 多選分享採用緊湊版面，較長的訊息正文可摺疊，方便檢查與分享大量選取內容。
- 支援多模態輸入與顯示，以及 Markdown、LaTeX 和 Mermaid 渲染。
- 提供訊息分支、搜尋整合、圖片生成、TTS、模型與圖片生成收藏等日常功能。

### 本機工具與 Web

- 日誌診斷會遮蔽 Authorization、API Key、Cookie、URL 與請求內容中的敏感資訊，並支援多選匯出和 AI 可讀的 `get_logs`。
- 取得 Android 權限後，本機工具可讀取螢幕使用時間，並查詢或建立行事曆事件。
- 內建本機 Web UI 預設僅監聽 `localhost`；開放區域網路存取時應啟用 JWT，介面會明確提示未驗證的風險。

## 獨立發行

| 面向 | RikkaRs |
| --- | --- |
| 維護方式 | 獨立儲存庫、產品藍圖與發布節奏；按需同步上游變更 |
| Android 身分 | 應用程式名稱 **RikkaRs**，Release 套件名稱 `me.arsucar.rikka`，可與官方 RikkaHub 共存 |
| 發布管道 | 由 Arsucar 在 [RikkaRs Releases](https://github.com/Arsucar/RikkaRs/releases) 獨立維護和發布穩定版 |
| 服務相依性 | 已移除 Firebase，無需 `google-services.json` |
| 發展重點 | 代理協作、持久記憶、上下文控制、本機工作區、Provider／模型管理與擴充能力 |

實作與上游同步規則請見[工程指南](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

## 下載與安裝

- **穩定版：**[GitHub Releases — 最新版](https://github.com/Arsucar/RikkaRs/releases/latest)
- **最低系統：**Android 8.0（API 26）
- **支援 ABI：**`arm64-v8a`
- **Release 套件名稱：**`me.arsucar.rikka`

獨立套件名稱讓 RikkaRs 可與官方 RikkaHub 安裝在同一台裝置上。升級或切換建置版本前，請先備份重要資料。

## 建置與貢獻

使用 [Android Studio](https://developer.android.com/studio) 和 JDK 17 開啟專案。Android 應用程式採用 Kotlin、Jetpack Compose、Koin、DataStore、Room、Coil、Material You、Navigation 3、OkHttp 和 kotlinx.serialization。專案不使用 Firebase，因此無需 `google-services.json`。套件名稱、CI、發布和上游同步細節請見[工程指南](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)。

適合提交的貢獻包括聚焦的缺陷修正、文件修正與可維護性改善；開始較大工作前請先建立 Issue。專案不接受僅涉及翻譯的變更、未經討論的功能實作，以及大規模或由 AI 生成的重構。

## 授權條款

RikkaRs 採用 [LICENSE](LICENSE) 規定的使用者分段雙重授權：符合任一條件——嚴格非商業用途、個人／教育／研究用途，或個人及組織總使用者數不超過 10 人——即可依 GNU AGPL v3 免費使用，並須履行該授權包括公開原始碼在內的義務。

商業用途（直接或間接產生商業利益）、總使用者數超過 10 人，或希望免除 AGPL v3 義務時，必須事先取得商業授權；請聯絡 `re_dev@qq.com`。專案維護者保留更新授權政策的權利，並會透過官方管道通知。

以上僅為摘要，任何情況均以 [LICENSE 原文](LICENSE) 為最終效力依據。使用、修改或散布本軟體前，請完整閱讀授權條款。

## 相關連結

- [RikkaRs 獨立儲存庫](https://github.com/Arsucar/RikkaRs)
- [最新穩定版](https://github.com/Arsucar/RikkaRs/releases/latest)
- [所有 Releases](https://github.com/Arsucar/RikkaRs/releases)
- [更新日誌](CHANGELOG.md)
- [工程指南](docs/RIKKA_ARSUCAR_FORK_AND_CI.md)
- [上游 RikkaHub](https://github.com/rikkahub/rikkahub)
