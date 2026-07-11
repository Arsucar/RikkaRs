# Implementation Plan

1. 完成只读审计：分支同步、开放 issue、版本、CHANGELOG、workflow、最近 release/run。
2. 新增 v2.3.25 CHANGELOG 中英双语段落，静态检查并提交推送。
3. 触发 `gh workflow run "Release APK (arm64)" --ref release/rikka-arsucar`，监控到成功。
4. 拉取 bot bump commit，确认 `versionName=2.3.25`、`versionCode=187`，并核验 dispatch artifact。
5. 创建 annotated tag `v2.3.25`，tag message 使用 CHANGELOG v2.3.25 内容，推送 tag。
6. 监控 tag run，验证 GitHub Release、arm64 APK 名称/大小/digest。
7. 更新验收记录，归档任务并记录 journal；推送最终 Trellis 提交。

