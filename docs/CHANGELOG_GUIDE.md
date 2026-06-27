# 更新日志维护指南

`CHANGELOG.md` 是本 Fork 的**中英双语**更新日志，每次发版必须按以下流程操作。

## 发版流程

1. **新增版本段落** — 在 `CHANGELOG.md` 顶部（`---` 分隔线之后）按已有格式添加 `## v<版本号>` 章节，每条变更写两遍：先中文、下一行写对应英文，保持对照。
2. **提交变更** — `git add CHANGELOG.md && git commit -m "docs: update CHANGELOG for v<版本号>"`。
3. **截取 Release 说明** — 从目标版本段落中提取中英文内容，按上游 release 旧格式组织：先「更新内容: + 中文列表」，空行后「Updates: + 英文列表」。
4. **打标签** — `git tag -a v<版本号> -m "<Release 说明>"`，tag message 即为 Release body。
5. **推送** — `git push origin release/rikka-arsucar && git push origin v<版本号>`；如需创建 GitHub Release 可用 `gh release create v<版本号> --title "<版本号>" --notes "<Release 说明>"`。

## 格式模板（每条变更）

```
- **中文标题** — 中文说明。
  **English Title** — English description.
```

## AI 助手触发条件

当用户请求「发版 / 打标签 / 推版本 / release / 发布新版本」时，AI 助手应：

1. 读取 `CHANGELOG.md`，确认目标版本段落已存在且内容完整；
2. 若不存在，先按本指南格式补写并提交；
3. 从对应版本段落截取中英双语内容，作为 `git tag -a` 的消息体和 GitHub Release body；
4. 再执行打标签和推送。
