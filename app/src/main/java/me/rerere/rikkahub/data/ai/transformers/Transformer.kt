package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

class TransformerContext(
    val context: Context,
    val model: Model,
    val assistant: Assistant,
    val settings: Settings,
    val conversationModeInjectionIds: Set<Uuid> = emptySet(),
    val conversationLorebookIds: Set<Uuid> = emptySet(),
    val processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    val workspaceCwd: String? = null,
    val executionMode: TransformerExecutionMode = TransformerExecutionMode.Send,
    val workspaceToolAvailable: Boolean = false,
    /**
     * Working copy of conversation variables for this generation (#217/#216).
     * Populated by ChatService/GenerationHandler when variable system is enabled;
     * mutated by VariableMacroTransformer / UpdateVariableOutputTransformer;
     * persisted atomically after input transform and after generation finish.
     */
    val conversationVariables: MutableMap<String, String>? = null,
)

enum class TransformerExecutionMode {
    Send,
    Preview,
}

enum class PreviewTransformPolicy {
    Unsupported,
    SideEffectFree,
}

class PreviewSideEffectRequiredException(message: String) : IllegalStateException(message)

interface MessageTransformer {
    /**
     * 消息转换器，用于对消息进行转换
     *
     * 对于输入消息，消息会转换被提供给API模块
     *
     * 对于输出消息，会对消息输出chunk进行转换
     */
    suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages
    }
}

interface InputMessageTransformer : MessageTransformer {
    /** New transformers fail closed in preview until their side effects are explicitly audited. */
    val previewPolicy: PreviewTransformPolicy
        get() = PreviewTransformPolicy.Unsupported
}

interface OutputMessageTransformer : MessageTransformer {
    /**
     * 一个视觉的转换，例如转换think tag为reasoning parts
     * 但是不实际转换消息，因为流式输出需要处理消息delta chunk
     * 不能还没结束生成就transform，因此提供一个visualTransform
     */
    suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages
    }

    /**
     * 消息生成完成后调用
     */
    suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages
    }
}

suspend fun List<UIMessage>.transforms(
    transformers: List<MessageTransformer>,
    context: Context,
    model: Model,
    assistant: Assistant,
    settings: Settings,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    workspaceCwd: String? = null,
    executionMode: TransformerExecutionMode = TransformerExecutionMode.Send,
    workspaceToolAvailable: Boolean = false,
    conversationVariables: MutableMap<String, String>? = null,
): List<UIMessage> {
    val ctx = TransformerContext(
        context = context,
        model = model,
        assistant = assistant,
        settings = settings,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
        processingStatus = processingStatus,
        workspaceCwd = workspaceCwd,
        executionMode = executionMode,
        workspaceToolAvailable = workspaceToolAvailable,
        conversationVariables = conversationVariables,
    )
    return transformers.fold(this) { acc, transformer ->
        if (
            executionMode == TransformerExecutionMode.Preview &&
            transformer is InputMessageTransformer &&
            transformer.previewPolicy == PreviewTransformPolicy.Unsupported
        ) {
            throw PreviewSideEffectRequiredException(
                "${transformer::class.simpleName ?: "Unknown transformer"} cannot run without side effects in preview",
            )
        }
        transformer.transform(ctx, acc)
    }
}

suspend fun List<UIMessage>.visualTransforms(
    transformers: List<MessageTransformer>,
    context: Context,
    model: Model,
    assistant: Assistant,
    settings: Settings,
    conversationVariables: MutableMap<String, String>? = null,
): List<UIMessage> {
    val ctx = TransformerContext(
        context = context,
        model = model,
        assistant = assistant,
        settings = settings,
        conversationVariables = conversationVariables,
    )
    return transformers.fold(this) { acc, transformer ->
        if (transformer is OutputMessageTransformer) {
            transformer.visualTransform(ctx, acc)
        } else {
            acc
        }
    }
}

suspend fun List<UIMessage>.onGenerationFinish(
    transformers: List<MessageTransformer>,
    context: Context,
    model: Model,
    assistant: Assistant,
    settings: Settings,
    conversationVariables: MutableMap<String, String>? = null,
): List<UIMessage> {
    val ctx = TransformerContext(
        context = context,
        model = model,
        assistant = assistant,
        settings = settings,
        conversationVariables = conversationVariables,
    )
    return transformers.fold(this) { acc, transformer ->
        if (transformer is OutputMessageTransformer) {
            transformer.onGenerationFinish(ctx, acc)
        } else {
            acc
        }
    }
}
