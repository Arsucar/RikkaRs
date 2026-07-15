# 修复 Issues 129 130 工作区图片读取

## Goal

让 `workspace_read_file` 读取 jpg/png 等图片时把真实视觉内容交给多模态模型，而不是只返回成功文本。

## Requirements

1. 核验并保持当前图片读取返回的 `UIMessagePart.Image(file://upload/...)`，以 provider 回归测试证明消息协议保留 MIME 与真实字节。
2. provider 转换链必须与用户直接发送图片的成熟路径复用，覆盖支持图片的供应商。
3. 非图片文本/二进制文件行为不回归；尺寸、格式、读取错误给出明确错误而非假成功。
4. workspace 路径沙箱和权限校验不因图片支持被绕过。
5. #129/#130 内容相同，只实现一次；关闭较晚的 #130 时标为 #129 duplicate（或反向，保持提交引用一致）。

## Acceptance Criteria

- [ ] 读取标准 jpg/png 后，工具结果包含 provider 可消费图片内容；Claude、OpenAI Chat/Responses、Google 序列化测试证明图片进入请求。
- [ ] 不支持视觉的模型得到明确兼容行为，不静默声称已看见。
- [ ] 文本读取、安全边界、超限和无效图片有自动化测试。
- [ ] #129/#130 均得到修复或重复关闭说明。

## Notes

- 当前 HEAD 已存在完整多模态链路，本任务以补齐 workspace 场景端到端/provider 回归为主；测试若暴露真实断点再修产品代码。
