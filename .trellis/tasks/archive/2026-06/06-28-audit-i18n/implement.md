# Implement：夜间审计 i18n 修复（P0/P1）

## 优先级与顺序

```
1. [P0] L-02 + L-03 + L-04  资源补齐（locale-tui TUI 批量翻译缺失）
2. [P0] L-01                ImgGen 新 key（locale-tui add）+ ImgGenPage.kt
3. [P1] L-05、L-06、L-07    少量 add + 三个 Kotlin 文件
4. 整体验收与装包
```

---

## 阶段 1：locale-tui 批量对齐（L-02 / L-03 / L-04）

### 1.1 前置检查

- [ ] 确认 `locale-tui/.env` 可用：`uv run --directory locale-tui src/main.py test-connection`
- [ ] **若 locale-tui 连接失败**（无 API key 或网络不可用）：回退方**手工编辑** 5 个 `values-*/strings.xml`。从 `values/strings.xml` 复制缺失 key（PRD 102 项列表 + 15 项简中），手动逐项填写。对 L-01/L-05/L-06/L-07 新 key，在 `values/strings.xml` 添加 en 后手动复制到各 locale 文件并填翻译。此路径虽慢但确保 key 齐全。
- [ ] 记录实施前各 locale 缺失数（用于对比）：

```powershell
$en = Select-String -Path "app\src\main\res\values\strings.xml" -Pattern '<string name="([^"]+)"' | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
foreach ($loc in @("values-zh","values-zh-rTW","values-ja","values-ko-rKR","values-ru")) {
  $f = "app\src\main\res\$loc\strings.xml"
  $keys = Select-String -Path $f -Pattern '<string name="([^"]+)"' | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
  $missing = Compare-Object $en $keys | Where-Object SideIndicator -eq '<=' | Measure-Object
  Write-Host "$loc missing: $($missing.Count)"
}
```

### 1.2 TUI 批量翻译（核心）

- [ ] `uv run --directory locale-tui src/main.py` → 模块 **app**
- [ ] **`m`** Missing 过滤 ON
- [ ] **`t`** Translate missing（覆盖 PRD 中 102+15 key，含 `safe_mode_enter_app`）
- [ ] **`s`** 保存（如需要）
- [ ] 简中 `safe_mode_enter_app` 校验为「进入应用」等非英文回退；不对则：

```bash
uv run --directory locale-tui src/main.py set safe_mode_enter_app "进入应用" -l values-zh -m app
```

### 1.3 L-04 抽验（15 key）

- [ ] `values-zh` 含：`model_list_collapse_all` … `model_list_search`、`safe_mode_enter_app`、`subagent_step_text`、`subagent_step_thinking`（en 参考 `strings.xml:526-537`、`662`、`1372-1373`）

### 1.4 阶段 1 门禁

- [ ] 再次运行 **1.1** 脚本，五 locale **missing: 0**（在实施 L-01 新 key 之前）

---

## 阶段 2：L-01 ImgGenPage

### 2.1 新增 en key（locale-tui add）

- [ ] 按 `design.md` / `prd.md` § L-01 表批量 `add`（英文 value）；**先 grep** `values/strings.xml` 避免与现有 `imggen_page_cancel` 等重复
- [ ] 每批 add 后确认 5 个目标 locale 已写入
- [ ] 建议最低覆盖 PRD 验收项：菜单 363/449、a11y 2808/2822、回收站 2413-2525、分组 1572-1648、gpt-image-2 2627-2734

### 2.2 修改 `ImgGenPage.kt`

- [ ] 替换 `Text("…")` / `contentDescription = "…"` 为 `stringResource`
- [ ] 复用已有 `imggen_page_cancel`、`imggen_page_confirm`、`imggen_page_delete` 等
- [ ] 可选同文件扩展：toast、搜索 placeholder（`:226` 等）以满足 Han grep 门禁

### 2.3 L-01 门禁

```powershell
rg '[\u4e00-\u9fff]' app\src\main\java\me\rerere\rikkahub\ui\pages\imggen\ImgGenPage.kt
```

- [ ] 生产路径 **0** 命中（或仅剩注释；`@Preview` 若含中文按 PRD L-11 可保留，但与验收「Han 应为 0」冲突时以 PRD § L-01 为准：生产路径清零）

---

## 阶段 3：L-05 / L-06 / L-07

### L-05 TTS

- [ ] `locale-tui add` 八个 `setting_tts_configure_step_*` key（-m app）
- [ ] `TTSProviderConfigure.kt` `StepTTSConfiguration`：1122、1130、1276、1314、1331、1351、1388、1399 → `stringResource`

### L-06 ASR

- [ ] `add setting_asr_configure_step_hotwords_placeholder`
- [ ] `ASRProviderConfigure.kt:529` → `stringResource(R.string.setting_asr_configure_step_hotwords_placeholder)`

### L-07 捐赠

- [ ] `add donate_page_afdian_title`（en: `Afdian`）
- [ ] `SettingDonatePage.kt:119` → `stringResource(R.string.donate_page_afdian_title)`
- [ ] 可选：`set donate_page_afdian_title "爱发电" -l values-zh -m app`

---

## 阶段 4：整体验收

### 4.1 Key 一致性

- [ ] 运行 **1.1** 脚本，五 locale **missing: 0**（含 L-01 新增 key 后）

### 4.2 硬编码扫描（Kotlin）

```powershell
rg '[\u4e00-\u9fff]' app\src\main\java\me\rerere\rikkahub\ui\pages\imggen\ImgGenPage.kt
rg '[\u4e00-\u9fff]' app\src\main\java\me\rerere\rikkahub\ui\pages\setting\components\TTSProviderConfigure.kt
rg '[\u4e00-\u9fff]' app\src\main\java\me\rerere\rikkahub\ui\pages\setting\components\ASRProviderConfigure.kt
rg '[\u4e00-\u9fff]' app\src\main\java\me\rerere\rikkahub\ui\pages\setting\SettingDonatePage.kt
```

- [ ] L-05：`StepTTSConfiguration` 内无中文 description/placeholder（模型列表中文可为已知残留，记录到任务笔记）
- [ ] L-06、L-07：对应文件无 CJK 硬编码

### 4.3 编译与装包

```powershell
adb devices
.\gradlew :app:compileDebugKotlin
.\gradlew :app:installDebug
```

- [ ] `compileDebugKotlin` 通过

### 4.4 手工抽测（建议）

| 语言 | 检查点 |
|------|--------|
| en | SafeMode「Enter App」、捐赠标题「Afdian」、TTS Step 英文说明 |
| zh | SafeMode「进入应用」、捐赠「爱发电」、图生回收站/分组非英文夹硬编码 |
| ja | 子代理/模型列表 a11y 非英文回退（有翻译前提下） |

---

## 审查节点（Review gates）

| 节点 | 条件 |
|------|------|
| G1 | 阶段 1 完成，五 locale missing=0（L-01 add 前） |
| G2 | L-01 ImgGenPage Han grep 清零 + 新 key 已同步五 locale |
| G3 | P1 三文件改完 + 全量 key diff + compile 通过 |
| G4 | `installDebug` 成功（有设备时） |

---

## 回滚计划

1. **未提交**：`git checkout -- app/src/main/res app/src/main/java/...` 按文件回退
2. **已提交**：`git revert <commit>` 单提交回滚（资源与 Kotlin 同 commit 时一并恢复）
3. **locale-tui 误翻**：对单 key 用 `locale-tui set` 恢复；或从 revert 前的 `strings.xml` 拷贝单条
4. **部分完成 P0**：可只 revert L-01 Kotlin 而保留 L-02 资源（不推荐拆散，优先整任务 revert）

---

## 任务关闭清单（对齐 PRD §6）

- [ ] **P0**：L-01、L-02、L-03 验收通过
- [ ] **P1**：L-04～L-07 验收通过
- [ ] `app` 五 locale 与 `values/` key 集合一致
- [ ] commit 说明列出主要 `values-*` 与 Kotlin 文件；优先注明「locale-tui TUI translate + add」