# 发布解决全部 Issue 的新版本

## Goal

将已关闭的 #102 与 #104 作为 v2.3.25 正式发布，生成可下载的签名 arm64 APK 和 GitHub Release。

## Confirmed Facts

- 当前分支 `release/rikka-arsucar` 与远端同步，源码工作树在创建任务前干净。
- 当前版本为 `versionName = "2.3.24"`、`versionCode = 186`，最新正式 Release 为 v2.3.24。
- #68 已包含在 v2.3.24 CHANGELOG，不应在 v2.3.25 重复记录。
- v2.3.25 的用户可见变更为 #102 Markdown 表格/右抽屉手势修复，以及 #104 子代理上下文缓存复用。
- 唯一正式 workflow 为 `Release APK (arm64)`。
- dispatch 路径只负责 bump、构建、artifact 和版本提交；`v*` tag 路径才创建 GitHub Release。

## Requirements

- 在 `CHANGELOG.md` 顶部新增完整中英双语 `## v2.3.25` 段落，覆盖 #102、#104。
- 先触发 `workflow_dispatch`，让 CI 从 2.3.24/186 bump 到 2.3.25/187，构建签名 arm64 APK并推送版本提交。
- dispatch 成功后拉取/核验 bot bump commit，再给该提交创建 annotated tag `v2.3.25` 并推送。
- tag workflow 必须成功创建非 prerelease GitHub Release，并附带 `rikka-arsucar-v2.3.25-arm64.apk`。
- 禁止使用已删除的 `Release Build` workflow；禁止提交 keystore、`.omc/` 或临时设备产物。

## Acceptance Criteria

- [x] CHANGELOG v2.3.25 中英双语内容已提交并推送。
- [x] dispatch run 成功，`app/build.gradle.kts` 在远端变为 2.3.25/187。
- [x] tag `v2.3.25` 指向版本 bump 后的提交。
- [x] tag run 成功，GitHub Release v2.3.25 为正式发布。
- [x] Release 包含非空 arm64 APK，并记录大小与 SHA-256。
- [x] 本地分支与远端同步，工作树干净，无开放 GitHub issue。

## Out of Scope

- 修改应用功能代码。
- 发布 AAB、universal 或 x86_64 包。
- 运行旧的 `Release Build` workflow。
