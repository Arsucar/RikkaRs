package me.rerere.rikkahub.data.ai.prompts

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

    <content>
    {content}
    </content>
""".trimIndent()
