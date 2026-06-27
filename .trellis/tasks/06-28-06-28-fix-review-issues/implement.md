# 实现计划

## 执行顺序

依赖关系：F3、F4、F5、F6、F7、F8 可并行；F1、F2 需要谨慎分析后可并行；F9、F10、F11 优先级最低。

### Round 1（P0 - 并行）
1. **F1**: 修复 `SubagentHost.kt` 协程竞态
2. **F2**: 修复 `ChatService.kt` CAS 竞态
3. **F3**: 修复 `ModelList.kt` remember 依赖

### Round 2（P1 - 并行）
4. **F4**: 修复 `AssistantSubagentPage.kt` generateCloneName 缺参数
5. **F5**: 删除 `ExtensionSubagentProfilePage.kt` 未用 import
6. **F6**: 修复 `ChatService.kt` null 保护
7. **F7**: 修复 `ChatService.kt` profileName 转义
8. **F8**: 修复 `SubagentRegistry.kt` 空列表处理

### Round 3（P2）
9. **F9**: 补全 i18n 翻译
10. **F10**: 补充单元测试
11. **F11**: 补充 contentDescription

## 验证命令
```bash
./gradlew :app:compileDebugKotlin
./gradlew test
```

## 回滚点
- Round 1 完成后可单独提交
- Round 2 完成后可单独提交
- 最终统一提交
