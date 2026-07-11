# 审计并完成 Issue 68 上游同步

## Goal

证明上游 `rikkahub/rikkahub` 至 `cf55cbbb` 的同步、fork 正式发版 workflow 和新增 Daily Build workflow 均可用，然后关闭 #68。

## Confirmed Facts

- 合并提交 `42b2101d` 的第二父提交为 `cf55cbbb`；上游 v2.4.1 tag 也在当前 HEAD 祖先链中。
- `daily-build.yml` 与 fork 的 `release-apk.yml` 同时存在。
- 合并后的 v2.3.24 正式发版 Actions 已成功并生成 arm64 APK。
- 当前没有 Daily Build run 记录，也没有 `nightly` prerelease。

## Requirements

- 保持正式 workflow 名称 `Release APK (arm64)` 和现有 tag/dispatch 语义。
- Daily Build 必须能在 `release/rikka-arsucar` 上成功运行并创建/更新 `nightly` prerelease。
- 不因验证而修改版本号或触发正式发版。

## Acceptance Criteria

- [x] `cf55cbbb` 和 v2.4.1 均为当前分支祖先。
- [x] daily-build 与 release-apk 两个 workflow 共存且适配无 Firebase fork。
- [x] 合并后正式 arm64 release 构建成功。
- [x] 手动触发的 Daily Build 成功完成。
- [x] `nightly` GitHub prerelease 存在并包含 APK 资产。
- [x] #68 留下验证证据评论后关闭。

## Out of Scope

- 再次合并已同步的上游提交。
- 改动正式发版版本号或创建新的正式 release。
