# Implementation Plan

1. 触发 `gh workflow run "Daily Build" --ref release/rikka-arsucar`。
2. 获取新 run id，并用 `gh run watch <id> --exit-status` 等待结果。
3. 验证 `gh release view nightly` 返回 prerelease 且 APK 资产非空。
4. 若失败，检查日志，修复 workflow 后重跑；所有 Gradle 命令保持 `--no-daemon`。
5. 在 #68 评论合并 commit、正式 release run、Daily Build run 和 nightly 资产证据，然后关闭。
6. 更新 PRD 勾选、完成 Trellis 检查并归档。

