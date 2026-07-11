# Design

## Approach

本任务不需要源代码变更。以 git 祖先关系、workflow 文件、既有正式 release run 和一次新的 Daily Build run 作为四类权威证据。

## External State Changes

- 通过 `workflow_dispatch` 触发 `Daily Build`。
- workflow 成功后由其更新固定 `nightly` prerelease。
- 验证完成后评论并关闭 #68。

## Failure Handling

- 若首次 run 失败，读取失败 job/step 日志并在当前 workflow 范围内修复。
- 不删除正式 release，不修改 v* tag。

