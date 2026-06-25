# Rikka-arsucar 下游 Fork 与 GitHub Actions 发版指南

> **用途**：给新开 AI 窗口或协作者用的实施说明书。按本文执行即可把本仓库变成可与上游 **RikkaHub**（`me.rerere.rikkahub`）共存的独立下游，并在 **GitHub Actions 页面手动打正式 arm64 APK**，每次发版自动 bump 版本并 push 回仓库。  
> **约定**：正式 Release **不在本地构建**；本地只做 **Debug** 功能验证。

---

## 1. 背景与目标

| 项目 | 说明 |
|------|------|
| 上游仓库 | `https://github.com/rikkahub/rikkahub`（remote 名常为 `upstream`） |
| 本 Fork | `https://github.com/Arsucar/rikkahub`（remote 名常为 `origin`） |
| 当前开发分支（历史） | `local/agent-trellis-setup`（含功能合并 + Trellis/Agent 文件） |
| 目标 | 发布独立应用 **Rikka-arsucar**，与上游 **同时安装** |
| 构建方式 | **仅 CI** 打 signed **release** APK；本地 `assembleDebug` |

---

## 2. 已确认的产品决策（勿擅自改）

### 2.1 身份与共存

| 项 | 值 |
|----|-----|
| **applicationId（release）** | `me.arsucar.rikka` |
| **applicationId（debug）** | `me.arsucar.rikka.debug`（保留 `applicationIdSuffix = ".debug"`） |
| **namespace / Kotlin 包名** | **不改**，仍为 `me.rerere.rikkahub`（方案 A：只改外部包名，便于 merge 上游） |
| **显示名称 `app_name`** | `Rikka-arsucar` |
| **与上游关系** | 独立下游，非上游 PR |

`AndroidManifest` 中 `FileProvider`、`DocumentsProvider` 等 authority 使用 `${applicationId}`，改 applicationId 后会自动隔离，无需手改 authority 字符串。

**未强制、可后续做**：启动器图标与上游区分（角标/换色）。

### 2.2 Firebase

- **彻底移除** Firebase（Analytics / Crashlytics / Remote Config）。
- **不**使用上游 `google-services.json`（包名绑定且数据进上游项目）。
- 崩溃：沿用 `me.rerere.rikkahub.utils.CrashHandler`（本地 SharedPreferences + Safe Mode），暂不接第三方。

### 2.3 签名

- Release 使用**专用 keystore**，密码沿用本地习惯：`Mima1234_`。
- **Key alias**：`rikka-arsucar`。
- Keystore **不进 Git**；CI 通过 **GitHub Secrets** 注入。
- **注意**：当前 `local.properties` 使用 `keystore.path` / `keystore.password`，而 `app/build.gradle.kts` 读取的是 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`——**字段不一致，本地 release 签名可能从未生效**。实施时必须统一。

### 2.4 web-ui 构建

- 修改 `web/build.gradle.kts`：去掉 `zsh -ic "pnpm run build"`，改为 CI/Windows 可用的 `pnpm` 调用。
- CI 安装 Node + pnpm，`pnpm install --frozen-lockfile` 后由 Gradle `preBuild` 触发 `buildWebUi`。
- `web/src/main/resources/static` **未纳入 git**（0 文件），每次完整构建必须先编 web-ui。

### 2.5 Git 推送策略

- 新建**发行分支**（推荐名：`release/rikka-arsucar`），在此分支完成 fork 改造 + workflow。
- **允许**将 `.trellis/`、`.agents/`、`.codex/`、`README_FOR_AGENT.md` 等一并 push 到 **自己的 Fork**（用户接受 AI 工作流文件公开）。
- **不要**向 `rikkahub/rikkahub` 上游提交 Trellis 专用改动（见根目录 `AGENTS.md`）。

### 2.6 CI 产物与版本

| 项 | 规则 |
|----|------|
| 产物 | **仅 arm64-v8a** 的 **signed release APK**（不要 universal、不要 x86_64、不要 AAB，除非日后单独决策） |
| 触发 | **`workflow_dispatch` only**（Actions 页手动 Run） |
| versionCode | 每次正式 workflow **+1** |
| versionName | **patch +1**（如 `2.3.2` → `2.3.3`） |
| 版本写回 Git | **是**：构建成功后 **commit + push** 到触发 workflow 的分支 |
| 本地正式包 | **不做** |

当前 `app/build.gradle.kts` 基线（实施前以文件为准）：`versionCode = 165`，`versionName = "2.3.2"`。

---

## 3. 仓库现状速查（实施前核对）

```bash
git remote -v
# origin  -> Arsucar/rikkahub
# upstream -> rikkahub/rikkahub

git branch --show-current
```

- **无** `.github/workflows/`（需新建）。
- `app/google-services.json` 本地可能存在，**未跟踪**；移除 Firebase 后不再需要。
- Release 配置：`isMinifyEnabled = true`，`isShrinkResources = true`。
- ABI splits：当前含 `arm64-v8a`、`x86_64`，且 `isUniversalApk = true`（打 bundle 时 split 会关）。CI 只发 arm64 时需 **收窄 split 或 workflow 只上传 arm64 产物**（见 §6.4）。

---

## 4. 实施总览（推荐顺序）

1. 从当前工作树创建分支 `release/rikka-arsucar`。
2. 改 `applicationId` + `app_name`。
3. 移除 Firebase（Gradle + Kotlin + 资源 + Koin）。
4. 统一 release 签名配置；生成 keystore；配置 GitHub Secrets。
5. 改 `web/build.gradle.kts` 的 `buildWebUi`。
6. 调整 ABI split（仅 arm64 出包或 CI 只取 arm64 APK）。
7. 新增 `.github/workflows/release-apk.yml`（bump 版本 → 构建 → artifact → commit push）。
8. 本地 `./gradlew assembleDebug` 验证。
9. Push 分支；在 GitHub Actions 手动 Run；下载 artifact 安装测试。
10. （可选）将 GitHub 默认分支改为 `release/rikka-arsucar`。

---

## 5. 逐步修改说明

### 5.1 `app/build.gradle.kts`

**applicationId**

```kotlin
defaultConfig {
    applicationId = "me.arsucar.rikka"
    // minSdk, targetSdk, versionCode, versionName 保持不变直至 CI bump
}
```

**移除插件**（`app` 模块 `plugins { }` 块）：

- `alias(libs.plugins.google.services)`
- `alias(libs.plugins.firebase.crashlytics)`

**移除依赖**（`dependencies` 块中的 Firebase 相关）：

- `platform(libs.firebase.bom)`
- `libs.firebase.analytics`
- `libs.firebase.crashlytics`
- `libs.firebase.config`

**签名**（与 `local.properties` / CI 统一键名）：

```kotlin
signingConfigs {
    create("release") {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
            val storeFilePath = localProperties.getProperty("storeFile")
                ?: localProperties.getProperty("keystore.path") // 兼容旧键名
            val storePasswordValue = localProperties.getProperty("storePassword")
                ?: localProperties.getProperty("keystore.password")
            val keyAliasValue = localProperties.getProperty("keyAlias")
                ?: localProperties.getProperty("key.alias")
            val keyPasswordValue = localProperties.getProperty("keyPassword")
                ?: localProperties.getProperty("key.password")
            if (storeFilePath != null && storePasswordValue != null &&
                keyAliasValue != null && keyPasswordValue != null
            ) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }
}
```

CI 无 `local.properties` 时，应通过 **环境变量** 或 workflow 写入的 `local.properties` / `gradle.properties` 提供相同四元组（见 §7）。

### 5.2 根目录 `build.gradle.kts`

可从 `plugins { }` 中删除（若全项目无其他模块使用）：

- `alias(libs.plugins.google.services) apply false`
- `alias(libs.plugins.firebase.crashlytics) apply false`

`libs.versions.toml` 中的 Firebase 版本条目可删可留（删更干净，merge 上游时可能冲突，按需处理）。

### 5.3 应用显示名

- `app/src/main/res/values/strings.xml`：`<string name="app_name">Rikka-arsucar</string>`
- 若需多语言一致，同步改 `values-zh`、`values-zh-rTW` 等中的 `app_name`（用户未强制 i18n，至少改默认 `values`）。

### 5.4 移除 Firebase — Kotlin / DI

| 文件 | 操作 |
|------|------|
| `app/.../RikkaHubApp.kt` | 删除 Remote Config 初始化块（约 87–94 行）及相关 import |
| `app/.../di/AppModule.kt` | 删除 `Firebase.crashlytics` / `remoteConfig` / `analytics` 三个 `single { }` 及 Firebase import |
| `app/.../ui/pages/chat/ChatVM.kt` | 删除构造函数参数 `FirebaseAnalytics`；删除所有 `analytics.logEvent(...)`；删除 import |
| `app/.../di/ViewModelModule.kt` | `ChatVM(...)` 构造调用去掉 `analytics = get()`（若有） |
| `app/.../data/ai/AIRequestInterceptor.kt` | 改为无参 `class AIRequestInterceptor : Interceptor`，删除 `remoteConfig` 字段与 import（逻辑已是 pass-through） |
| `app/.../di/DataSourceModule.kt` | `.addInterceptor(AIRequestInterceptor(remoteConfig = get()))` → `.addInterceptor(AIRequestInterceptor())` |

**资源**

- 删除 `app/src/main/res/xml/remote_config_defaults.xml`（若无其他引用）。

**全局搜索验收**（`app/src/main/java`）：

```text
不应再出现：Firebase、FirebaseAnalytics、FirebaseRemoteConfig、crashlytics、google-services
```

### 5.5 `web/build.gradle.kts` — 跨平台 buildWebUi

将：

```kotlin
commandLine("zsh", "-ic", "pnpm run build")
```

改为在 Windows / Linux 均可用的方式，例如：

```kotlin
val isWindows = System.getProperty("os.name").lowercase().contains("win")
commandLine(
    if (isWindows) "cmd" else "sh",
    if (isWindows) "/c" else "-c",
    if (isWindows) "pnpm run build" else "pnpm run build"
)
```

或 Gradle 7+：

```kotlin
commandLine("pnpm", "run", "build")
```

（要求 PATH 中有 `pnpm`；CI 用 `pnpm/action-setup` 即可。）

### 5.6 仅 arm64-v8a Release APK

**方案 A（推荐，Gradle 层）**：在 `app/build.gradle.kts` 的 `splits.abi` 中只保留：

```kotlin
include("arm64-v8a")
isUniversalApk = false
```

这样 `assembleRelease` 主要产出 `app-arm64-v8a-release.apk`。

**方案 B**：保留多 ABI，workflow 的 `upload-artifact` 只打包：

```text
app/build/outputs/apk/release/*arm64-v8a*release*.apk
```

与用户决策「只打包 apk v8」一致即可。

---

## 6. Keystore 与 GitHub Secrets

### 6.1 本地生成（一次性）

在仓库根目录（路径自定，**不要 commit**）：

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore rikka-arsucar-release.jks \
  -alias rikka-arsucar -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'Mima1234_' -keypass 'Mima1234_' \
  -dname "CN=Rikka-arsucar, OU=Arsucar, O=Arsucar, L=Unknown, ST=Unknown, C=CN"
```

**务必备份** `.jks` 到安全位置；丢失则无法对旧安装用户做同签名升级。

### 6.2 写入 GitHub Secrets（Repository → Settings → Secrets and variables → Actions）

| Secret 名 | 内容 |
|-----------|------|
| `KEYSTORE_BASE64` | `base64` 编码后的整个 `.jks` 文件（Linux: `base64 -w0 rikka-arsucar-release.jks`；macOS 无 `-w0` 则注意换行） |
| `KEYSTORE_PASSWORD` | `Mima1234_` |
| `KEY_ALIAS` | `rikka-arsucar` |
| `KEY_PASSWORD` | `Mima1234_` |

### 6.3 CI 中还原 keystore

Workflow 步骤示例逻辑：

1. `echo "$KEYSTORE_BASE64" | base64 -d > rikka-arsucar-release.jks`
2. 写入 `local.properties`（仅 CI，不 commit）：

```properties
sdk.dir=/usr/local/lib/android/sdk
storeFile=rikka-arsucar-release.jks
storePassword=***
keyAlias=rikka-arsucar
keyPassword=***
```

（`sdk.dir` 由 `android-actions/setup-android` 或 `ANDROID_SDK_ROOT` 约定；也可用环境变量驱动 Gradle，需与 `signingConfigs` 读取方式一致。）

---

## 7. GitHub Actions Workflow 规格

**文件路径**：`.github/workflows/release-apk.yml`

### 7.1 权限

版本 bump 后要 push，需：

```yaml
permissions:
  contents: write
```

并使用 `actions/checkout@v4` 的 `token` 或默认 `GITHUB_TOKEN`（仓库 Settings → Actions → General → Workflow permissions 选 **Read and write**）。

### 7.2 触发

```yaml
on:
  workflow_dispatch:
```

（可选日后加 `workflow_dispatch` 输入 `skip_bump`，本次决策不需要。）

### 7.3 作业步骤（逻辑顺序）

1. **Checkout**（`fetch-depth: 0` 若需 tag，当前可 `1`）。
2. **Bump version**（在 `app/build.gradle.kts`）：
   - 读取 `versionCode`（Int）→ `+1`
   - 读取 `versionName`（String，形如 `x.y.z`）→ patch `z + 1`（注意纯数字解析，避免 `-beta` 后缀除非日后支持）
   - 可用 `sed`/Python 脚本；**必须**同时改 `versionCode` 与 `versionName`。
3. **Setup JDK 17**（与项目 `JavaVersion.VERSION_17` 一致）。
4. **Setup Android SDK**（`android-actions/setup-android` 或 `gradle` 自带机制）。
5. **Setup Node + pnpm**：
   - `actions/setup-node`（读 `web-ui/package.json` 的 `engines` 若有）
   - `pnpm/action-setup`
   - `working-directory: web-ui` → `pnpm install --frozen-lockfile`
6. **Decode keystore + write local.properties**（§6.3）。
7. **Build**：`./gradlew :app:assembleRelease --no-daemon`（Windows runner 用 `gradlew.bat`；推荐 `ubuntu-latest`）。
8. **Upload artifact**：arm64 release APK，保留天数建议 30–90。
9. **Commit version bump**：
   - `git config user.name` / `user.email`（bot 身份即可）
   - `git add app/build.gradle.kts`
   - `git commit -m "chore(release): bump version to X.Y.Z (N)"`
   - `git push`
10. **失败时**：不要 push 版本号（用 `if: success()` 包住 push 步骤）。

### 7.4 Workflow 骨架（供新 AI 粘贴后微调）

```yaml
name: Release APK (arm64)

on:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Bump versionCode and versionName
        run: |
          python3 << 'PY'
          import re, pathlib
          p = pathlib.Path("app/build.gradle.kts")
          t = p.read_text(encoding="utf-8")
          vc = int(re.search(r"versionCode\s*=\s*(\d+)", t).group(1))
          vn = re.search(r'versionName\s*=\s*"([^"]+)"', t).group(1)
          parts = vn.split(".")
          parts[-1] = str(int(parts[-1]) + 1)
          vn_new = ".".join(parts)
          vc_new = vc + 1
          t = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {vc_new}", t, count=1)
          t = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{vn_new}"', t, count=1)
          p.write_text(t, encoding="utf-8")
          print(f"Bumped to {vn_new} ({vc_new})")
          PY

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - uses: android-actions/setup-android@v3

      - uses: pnpm/action-setup@v4
        with:
          version: 9

      - uses: actions/setup-node@v4
        with:
          node-version: "22"
          cache: pnpm
          cache-dependency-path: web-ui/pnpm-lock.yaml

      - name: Install web-ui dependencies
        working-directory: web-ui
        run: pnpm install --frozen-lockfile

      - name: Prepare signing
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > rikka-arsucar-release.jks
          cat >> local.properties << EOF
          storeFile=rikka-arsucar-release.jks
          storePassword=${KEYSTORE_PASSWORD}
          keyAlias=${KEY_ALIAS}
          keyPassword=${KEY_PASSWORD}
          EOF

      - name: Assemble release
        run: chmod +x gradlew && ./gradlew :app:assembleRelease --no-daemon

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: rikka-arsucar-arm64-release
          path: app/build/outputs/apk/release/*arm64-v8a*release*.apk

      - name: Commit version bump
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add app/build.gradle.kts
          git commit -m "chore(release): bump version after CI build" || exit 0
          git push
```

实施者需根据实际 APK 文件名、SDK 安装方式、Gradle 签名是否能在无 `sdk.dir` 时通过 `ANDROID_HOME` 工作做一次试跑修正。

---

## 8. 本地开发（仅 Debug）

```bash
./gradlew assembleDebug
# Windows: gradlew.bat assembleDebug
```

- 安装包 ID：`me.arsucar.rikka.debug`
- 与上游 `me.rerere.rikkahub` 及本下游 release `me.arsucar.rikka` **均可共存**（三个不同 applicationId）。

**不要**在本地依赖 `assembleRelease` 作为发版手段；发版只走 Actions。

---

## 9. 首次发布检查清单

- [ ] 分支 `release/rikka-arsucar` 已 push 到 `origin`
- [ ] 四个 Signing Secrets 已配置
- [ ] Actions workflow permissions = Read and write
- [ ] `./gradlew assembleDebug` 本地通过
- [ ] Actions Run 成功，artifact 可下载
- [ ] 真机安装 release APK，与上游 RikkaHub 同时存在
- [ ] 设置里版本号与 bump 后一致
- [ ] 第二次 Run：versionCode 递增、可覆盖安装上一次 **同签名** 的 release

---

## 10. 仍缺什么 / 不在范围内

| 项 | 状态 |
|----|------|
| `.github/workflows/release-apk.yml` | **需新建** |
| Keystore + Secrets | **需人工生成并配置** |
| applicationId / app_name / Firebase / web Gradle / ABI | **需按 §5 改代码** |
| `google-services.json` | **不需要**（已移除 Firebase） |
| Play 商店 / AAB | **未选** |
| 自动 GitHub Release 附件 / tag | **未选**（仅 workflow artifact） |
| 换图标 | **可选** |

---

## 11. 给新 AI 窗口的启动提示（复制即用）

```text
请阅读仓库内 docs/RIKKA_ARSUCAR_FORK_AND_CI.md，按「§4 实施总览」完整落地：
1) applicationId=me.arsucar.rikka，app_name=Rikka-arsucar；
2) 移除全部 Firebase；
3) 修复 web/build.gradle.kts 的 pnpm 调用；
4) release 仅 arm64-v8a APK；
5) 统一签名配置并添加 .github/workflows/release-apk.yml（workflow_dispatch，自动 bump versionCode/versionName 并 push）；
6) 本地只验证 assembleDebug。
不要向 upstream 提 PR。完成后列出改动的文件与验证命令。
```

---

## 12. 风险与 merge 上游提示

- 仅改 **applicationId** 时，合并 `upstream/master` 仍以应用代码为主；注意勿把上游重新引入的 Firebase 插件/依赖 blindly 合并回来，需保持本 fork「无 Firebase」策略。
- Trellis 文件在 fork 公开无妨，但 **upstream PR diff 不要包含** `.trellis/`、`.codex/` 等（见 `AGENTS.md`）。
- Deep link `rikkahub://` scheme 与上游相同**不阻止共存**（按包名区分应用）；若日后要做品牌隔离可再改 scheme。

---

*文档版本：与 2026-06-25 需求访谈结论一致。实施时以仓库内实际 `build.gradle.kts` 为准。*