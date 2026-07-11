# Design

## Release Sequence

采用 dispatch + tag 两阶段：

1. 提交 v2.3.25 CHANGELOG，但暂不手动改版本号。
2. `workflow_dispatch` 在 CI 工作树中 bump 到 2.3.25/187，构建成功后由 bot commit + push。
3. 拉取 bot commit，确认版本与 CHANGELOG 一致。
4. annotated tag `v2.3.25` 指向 bot bump commit；tag push 触发同一 workflow。
5. tag workflow 不再 bump，直接用提交内 2.3.25/187 构建并创建正式 Release。

## Safety Gates

- dispatch 失败时不会产生 tag，不继续发布。
- bump commit 与预期版本不一致时停止，不创建 tag。
- tag 前确认远端不存在 `v2.3.25`，Release 前确认 workflow 名称和事件正确。
- Release 资产必须是 arm64 APK，且 digest/大小可读取。

## Rollback

- CHANGELOG 提交可正常追加修复，不重写历史。
- 若 tag workflow 失败，修复后删除失败 tag（本地/远端）并按仓库重打标签流程重新触发。
- 不删除已成功的历史 Release。

