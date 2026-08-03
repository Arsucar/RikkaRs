package me.rerere.rikkahub.data.ai.clash

/**
 * 单次节点切换动作的记录。
 *
 * 用于 Clash 429 重试链路中追踪每一次切换备用节点的尝试。
 *
 * @param nodeName 切换到的备用节点名称；null 表示切换动作本身失败（异常发生在切换之前）
 * @param success 切换动作是否成功
 * @param error 切换失败原因（Clash API 不可达 / 组不存在 / 无备用节点等）
 * @param replayedCode 切换后重放请求拿到的状态码；重放抛 IOException 时为 null
 */
data class SwitchAttempt(
    val nodeName: String?,   // null 表示切换动作本身失败（异常在切换前）
    val success: Boolean,     // 切换动作是否成功
    val error: String?,       // 切换失败原因（Clash API 不可达/组不存在/无备用节点等）
    val replayedCode: Int?,   // 切换后重放请求拿到的状态码；重放抛 IOException 时为 null
)

/**
 * 一次完整的 Clash 429 重试链路追踪记录。
 *
 * 供调试面板展示一次请求从最初 429、多次节点切换重试、到最终结果的全过程。
 * 本模型为纯 Kotlin 数据类，不依赖任何 Android 组件。
 *
 * @property timestamp 记录创建时间（System.currentTimeMillis()）
 * @property requestHost 原始请求 host
 * @property responseCode 最初的 429 状态码
 * @property matchedProvider 匹配到的 provider.name；null = 未匹配
 * @property rotationEnabled 匹配到的 provider 的 enable429IpRotation
 * @property maxRetries 允许的最大重试次数
 * @property switches 依次进行的节点切换尝试
 * @property finalCode 最终结果（重放成功 = 200 等 / 仍 429 / 透传 429）
 * @property skippedReason 前置检查失败分类，稳定代码：SKIP_MAX_RETRIES / SKIP_NO_PROVIDER / SKIP_ROTATION_DISABLED
 * @property exhausted 重试是否耗尽仍 429
 */
data class ClashRetryTrace(
    val timestamp: Long,              // System.currentTimeMillis()
    val requestHost: String,          // 原始请求 host
    val responseCode: Int,            // 最初的 429
    val matchedProvider: String?,     // 匹配到的 provider.name；null=未匹配
    val rotationEnabled: Boolean,     // 匹配到的 provider 的 enable429IpRotation
    val maxRetries: Int,
    val switches: List<SwitchAttempt> = emptyList(),
    val finalCode: Int?,              // 最终结果（重放成功=200 等 / 仍 429 / 透传 429）
    val skippedReason: String? = null,// 前置检查失败分类，稳定代码：SKIP_MAX_RETRIES / SKIP_NO_PROVIDER / SKIP_ROTATION_DISABLED
    val exhausted: Boolean = false,   // 重试是否耗尽仍 429
)