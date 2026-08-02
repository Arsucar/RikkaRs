# issue-217+216 implement

## 有序 Checklist

1. [ ] Assistant 开关过渡字段 + partial 写 +（可选）助手设置里临时 Switch（#215 前可放助手详情实验区）。
2. [ ] Conversation.variables + MessageNode/消息 snapshot 字段 + Room 编解码/迁移兼容。
3. [ ] VariableMacroTransformer 纯 Kotlin + 单测（嵌套/自我移除/未定义/关闭透传）。
4. [ ] 注册 input 管线顺序。
5. [ ] UpdateVariableOutputTransformer + 单测（成功剥离/失败保留/JSON Patch）。
6. [ ] 注册 output onGenerationFinish；流式缓冲策略。
7. [ ] 分支：新备选 snapshot、selectIndex 恢复、fork 复制。
8. [ ] 原子写 API（Repository/ChatService）。
9. [ ] 右抽屉变量节 UI。
10. [ ] （可选二期）工具通道。
11. [ ] 单测 + installDebug + ST 预设抽样。
12. [ ] 关闭 #217 与 #216 时双 issue 中英交付评论互链。

## 验证命令

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest --tests "*Variable*"
.\gradlew --no-daemon :app:installDebug
```

## Review 门

- [ ] 三消费点统一开关
- [ ] #202 原子写
- [ ] 分支 AC 满足
- [ ] 关闭零副作用
- [ ] 不实现 #215 全页但契约稳定

## 回滚点

- 开关默认 false；transformers 可整文件删。

## 工作量

**L+**（3–5 天）：最大子任务；建议独立实现会话，可再拆 child（宏解析 / MVU / UI / 分支）。

## 风险

- 分支快照设计返工；ST 预设边界 case；与 PromptInjection 顺序错误导致宏不生效。
