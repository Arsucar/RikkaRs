# v2.3.25 Release Audit

- 当前分支与远端同步，版本为 2.3.24/186，开放 issue 为 0。
- dispatch 预期 bump 为 2.3.25/187；四个签名 Secrets、write 权限和正式 workflow 均可用。
- #68 已在 v2.3.24 记录；v2.3.25 只需记录 #102 与 #104。
- dispatch 只生成 artifact 并 push bot 版本提交，不创建 GitHub Release。
- 正式发布顺序必须为：提交 CHANGELOG → dispatch 成功 → 拉取 bot bump commit → tag v2.3.25 → tag run 创建 Release。
- dispatch 与 tag 之间不得并发推送发行分支，tag 必须指向包含 2.3.25/187 的 bot commit。

## Release Result

- CHANGELOG commit：`16977662`。
- dispatch run：`29139819037`，成功；artifact `rikka-arsucar-arm64-29139819037`。
- bot bump commit：`20f05f121e1bcf55627cd3209ebe9f2bf8891ffb`，版本 2.3.25/187。
- annotated tag：`v2.3.25`，指向 bot bump commit。
- tag run：`29140276784`，成功创建正式 Release。
- Release：https://github.com/Arsucar/rikkahub/releases/tag/v2.3.25
- APK：`rikka-arsucar-v2.3.25-arm64.apk`，39,387,856 bytes。
- SHA-256：`90510f61c6249ba309314dc3ec98e0fb36311a0ed5658adac355886c1c3410c6`。
