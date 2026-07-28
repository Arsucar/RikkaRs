package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.utils.applyPlaceholders

internal val DEFAULT_SUGGESTION_PROMPT = """
    I will provide you with some chat content in the `<content>` block, including conversations between the User and the AI assistant.
    You need to act as the **User** to reply to the assistant, generating 3~5 appropriate and contextually relevant responses to help the assistant improve its answers.

    Rules:
    1. Reply directly with suggestions, do not add any formatting, and separate suggestions with newlines, no need to add markdown list formats.
    2. Use {locale} language.
    3. Ensure each suggestion is valid.
    4. Each suggestion should not exceed 10 characters.
    5. Imitate the user's previous conversational style.
    6. Act as a User, not an Assistant!

    <content>
    {content}
    </content>
""".trimIndent()


internal val DEFAULT_INPUT_DRAFT_PROMPT = """
    I will provide recent chat context in the `<content>` block, including messages between the User and the AI assistant.
    Write one complete reply draft from the **User's** perspective.

    Rules:
    1. Output only the reply draft, without labels, commentary, alternatives, or surrounding quotes.
    2. Use {locale} language.
    3. Respond naturally to the latest assistant message and remain consistent with the context.
    4. Imitate the user's previous conversational style where possible.
    5. Do not invent personal facts, commitments, or preferences not supported by the context.
    6. Act as the User, not the Assistant.

    {user_instruction}

    <content>
    {content}
    </content>
""".trimIndent()

/**
 * 构建回复草稿 prompt。
 *
 * @param template 模板文本。默认用 [DEFAULT_INPUT_DRAFT_PROMPT]；调用方可传入预设里
 *   reply_draft 内置条目的启用覆盖内容（见 #182 [me.rerere.rikkahub.data.ai.prompts.resolveBuiltinOverride]），
 *   使「预设里编辑的草稿模板」真正生效。占位符（{locale}/{content}/{user_instruction}）
 *   由本函数统一解析，故覆盖模板应保留这些占位符才能拿到运行时值。
 */
internal fun buildInputDraftPrompt(
    locale: String,
    content: String,
    userInstruction: String = "",
    template: String = DEFAULT_INPUT_DRAFT_PROMPT,
): String {
    val instructionBlock = userInstruction.trim().takeIf { it.isNotEmpty() }?.let {
        """
        <user_instruction>
        用户对本次回复的附加要求/意图：
        $it
        </user_instruction>
        请在生成回复草稿时严格遵循上述要求。
        """.trimIndent()
    }.orEmpty()
    return template.applyPlaceholders(
        "locale" to locale,
        "content" to content,
        "user_instruction" to instructionBlock,
    )
}
