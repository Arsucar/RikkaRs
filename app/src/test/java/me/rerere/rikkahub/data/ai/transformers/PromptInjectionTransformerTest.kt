package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.migratedWithEntries
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptInjectionTransformerTest {

    // region Helper functions
    private fun createAssistant(
        modeInjectionIds: Set<Uuid> = emptySet(),
        presetIds: Set<Uuid> = emptySet(),
        lorebookIds: Set<Uuid> = emptySet(),
        allowConversationPromptInjection: Boolean = false
    ) = Assistant(
        modeInjectionIds = modeInjectionIds,
        presetIds = presetIds,
        lorebookIds = lorebookIds,
        allowConversationPromptInjection = allowConversationPromptInjection
    )

    private fun createModeInjection(
        id: Uuid = Uuid.random(),
        name: String = "Test Injection",
        enabled: Boolean = true,
        priority: Int = 0,
        position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        content: String = "Injected content",
        injectDepth: Int = 4,
        role: MessageRole = MessageRole.USER
    ) = PromptInjection.ModeInjection(
        id = id,
        name = name,
        enabled = enabled,
        priority = priority,
        position = position,
        content = content,
        injectDepth = injectDepth,
        role = role
    )

    private fun createRegexInjection(
        id: Uuid = Uuid.random(),
        name: String = "Test Regex",
        enabled: Boolean = true,
        priority: Int = 0,
        position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        content: String = "Regex injected content",
        injectDepth: Int = 4,
        role: MessageRole = MessageRole.USER,
        keywords: List<String> = listOf("trigger"),
        useRegex: Boolean = false,
        caseSensitive: Boolean = false,
        scanDepth: Int = 5,
        constantActive: Boolean = false
    ) = PromptInjection.RegexInjection(
        id = id,
        name = name,
        enabled = enabled,
        priority = priority,
        position = position,
        content = content,
        injectDepth = injectDepth,
        role = role,
        keywords = keywords,
        useRegex = useRegex,
        caseSensitive = caseSensitive,
        scanDepth = scanDepth,
        constantActive = constantActive
    )

    private fun createLorebook(
        id: Uuid = Uuid.random(),
        name: String = "Test Lorebook",
        enabled: Boolean = true,
        entries: List<PromptInjection.RegexInjection> = emptyList()
    ) = Lorebook(
        id = id,
        name = name,
        enabled = enabled,
        entries = entries
    )

    private fun getMessageText(message: UIMessage): String {
        return message.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("") { it.text }
    }

    private fun createAssistantWithUnexecutedTool(toolCallId: String, toolName: String): UIMessage {
        return UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = toolCallId,
                    toolName = toolName,
                    input = "{}",
                    output = emptyList()
                )
            )
        )
    }

    private fun createAssistantWithExecutedTool(toolCallId: String, toolName: String): UIMessage {
        return UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = toolCallId,
                    toolName = toolName,
                    input = "{}",
                    output = listOf(UIMessagePart.Text("result"))
                )
            )
        )
    }
    // endregion

    // region No injection tests
    @Test
    fun `no injections should return original messages`() {
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi there!")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(),
            modeInjections = emptyList(),
            lorebooks = emptyList()
        )

        assertEquals(messages, result)
    }

    @Test
    fun `disabled mode injection should not be applied`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            enabled = false,
            content = "Should not appear"
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(messages, result)
    }

    @Test
    fun `unlinked mode injection should not be applied`() {
        val injection = createModeInjection(content = "Should not appear")

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(), // No linked injections
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(messages, result)
    }

    @Test
    fun `preset mode injections should be applied from assistant binding`() {
        val presetId = Uuid.random()
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            content = "Preset content"
        )
        val preset = Preset(
            id = presetId,
            modeInjectionIds = setOf(injectionId),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                presetIds = setOf(presetId),
                allowConversationPromptInjection = true,
            ),
            modeInjections = listOf(injection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        assertTrue(getMessageText(result.first()).contains("Preset content"))
    }

    @Test
    fun `preset disabled entry should not be applied`() {
        val presetId = Uuid.random()
        val enabledId = Uuid.random()
        val disabledId = Uuid.random()
        val enabledInjection = createModeInjection(
            id = enabledId,
            content = "Enabled preset content"
        )
        val disabledInjection = createModeInjection(
            id = disabledId,
            content = "Disabled preset content"
        )
        val preset = Preset(
            id = presetId,
            modeInjectionIds = setOf(enabledId, disabledId),
            disabledEntryIds = setOf(disabledId),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = listOf(enabledInjection, disabledInjection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )
        val systemText = getMessageText(result.first())

        assertTrue(systemText.contains("Enabled preset content"))
        assertFalse(systemText.contains("Disabled preset content"))
    }

    @Test
    fun `conversation mode injection should apply only when assistant allows it`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            content = "Conversation content"
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val disabledResult = transformMessages(
            messages = messages,
            assistant = createAssistant(allowConversationPromptInjection = false),
            modeInjections = listOf(injection),
            lorebooks = emptyList(),
            conversationModeInjectionIds = setOf(injectionId)
        )
        val enabledResult = transformMessages(
            messages = messages,
            assistant = createAssistant(allowConversationPromptInjection = true),
            modeInjections = listOf(injection),
            lorebooks = emptyList(),
            conversationModeInjectionIds = setOf(injectionId)
        )

        assertEquals(messages, disabledResult)
        assertTrue(getMessageText(enabledResult.first()).contains("Conversation content"))
    }

    @Test
    fun `assistant mode injection should be ignored when conversation injection is allowed`() {
        val assistantInjectionId = Uuid.random()
        val conversationInjectionId = Uuid.random()
        val assistantInjection = createModeInjection(
            id = assistantInjectionId,
            content = "Assistant content"
        )
        val conversationInjection = createModeInjection(
            id = conversationInjectionId,
            content = "Conversation content"
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                modeInjectionIds = setOf(assistantInjectionId),
                allowConversationPromptInjection = true
            ),
            modeInjections = listOf(assistantInjection, conversationInjection),
            lorebooks = emptyList(),
            conversationModeInjectionIds = setOf(conversationInjectionId)
        )
        val systemText = getMessageText(result.first())

        assertFalse(systemText.contains("Assistant content"))
        assertTrue(systemText.contains("Conversation content"))
    }

    @Test
    fun `conversation lorebook should apply only when assistant allows it`() {
        val lorebookId = Uuid.random()
        val entry = createRegexInjection(
            keywords = listOf("Hello"),
            content = "Conversation lorebook content"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(entry)
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val disabledResult = transformMessages(
            messages = messages,
            assistant = createAssistant(allowConversationPromptInjection = false),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook),
            conversationLorebookIds = setOf(lorebookId)
        )
        val enabledResult = transformMessages(
            messages = messages,
            assistant = createAssistant(allowConversationPromptInjection = true),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook),
            conversationLorebookIds = setOf(lorebookId)
        )

        assertEquals(messages, disabledResult)
        assertTrue(getMessageText(enabledResult.first()).contains("Conversation lorebook content"))
    }

    @Test
    fun `assistant lorebook should be ignored when conversation injection is allowed`() {
        val assistantLorebookId = Uuid.random()
        val conversationLorebookId = Uuid.random()
        val assistantLorebook = createLorebook(
            id = assistantLorebookId,
            entries = listOf(
                createRegexInjection(
                    keywords = listOf("Hello"),
                    content = "Assistant lorebook content"
                )
            )
        )
        val conversationLorebook = createLorebook(
            id = conversationLorebookId,
            entries = listOf(
                createRegexInjection(
                    keywords = listOf("Hello"),
                    content = "Conversation lorebook content"
                )
            )
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                lorebookIds = setOf(assistantLorebookId),
                allowConversationPromptInjection = true
            ),
            modeInjections = emptyList(),
            lorebooks = listOf(assistantLorebook, conversationLorebook),
            conversationLorebookIds = setOf(conversationLorebookId)
        )
        val systemText = getMessageText(result.first())

        assertFalse(systemText.contains("Assistant lorebook content"))
        assertTrue(systemText.contains("Conversation lorebook content"))
    }
    // endregion

    // region AFTER_SYSTEM_PROMPT tests
    @Test
    fun `mode injection with AFTER_SYSTEM_PROMPT should append to system message`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            content = "Appended content"
        )

        val messages = listOf(
            UIMessage.system("Original system prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(2, result.size)
        val systemText = getMessageText(result[0])
        assertTrue(systemText.startsWith("Original system prompt"))
        assertTrue(systemText.endsWith("Appended content"))
    }
    // endregion

    // region BEFORE_SYSTEM_PROMPT tests
    @Test
    fun `mode injection with BEFORE_SYSTEM_PROMPT should prepend to system message`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
            content = "Prepended content"
        )

        val messages = listOf(
            UIMessage.system("Original system prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(2, result.size)
        val systemText = getMessageText(result[0])
        assertTrue(systemText.startsWith("Prepended content"))
        assertTrue(systemText.contains("Original system prompt"))
    }

    @Test
    fun `injection without existing system message should create new system message`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            content = "New system content"
        )

        val messages = listOf(
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(3, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals("New system content", getMessageText(result[0]))
    }
    // endregion

    // region TOP_OF_CHAT tests
    @Test
    fun `mode injection with TOP_OF_CHAT should insert before first user message`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.TOP_OF_CHAT,
            content = "Top of chat content"
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(4, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals("System prompt", getMessageText(result[0]))
        assertEquals(MessageRole.USER, result[1].role)
        assertEquals("Top of chat content", getMessageText(result[1]))
        assertEquals(MessageRole.USER, result[2].role)
    }
    // endregion

    // region BOTTOM_OF_CHAT tests
    @Test
    fun `mode injection with BOTTOM_OF_CHAT should insert before last message`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.BOTTOM_OF_CHAT,
            content = "Bottom of chat content"
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!"),
            UIMessage.user("How are you?")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(5, result.size)
        assertEquals(MessageRole.USER, result[3].role)
        assertEquals("Bottom of chat content", getMessageText(result[3]))
        assertEquals(MessageRole.USER, result[4].role)
        assertEquals("How are you?", getMessageText(result[4]))
    }
    // endregion

    // region AT_DEPTH tests
    @Test
    fun `mode injection with AT_DEPTH should insert at specified depth from end`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 2,
            content = "At depth 2 content"
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Message 1"),
            UIMessage.assistant("Response 1"),
            UIMessage.user("Message 2"),
            UIMessage.assistant("Response 2")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        // depth=2 means insert before the 2nd message from the end
        // Original: [System, User1, Asst1, User2, Asst2] (5 messages)
        // Insert at index 5-2=3, so: [System, User1, Asst1, Injected, User2, Asst2]
        assertEquals(6, result.size)
        assertEquals(MessageRole.USER, result[3].role)
        assertEquals("At depth 2 content", getMessageText(result[3]))
        assertEquals(MessageRole.USER, result[4].role)
        assertEquals("Message 2", getMessageText(result[4]))
    }

    @Test
    fun `AT_DEPTH with depth 1 should insert before last message`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 1,
            content = "Before last"
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(4, result.size)
        assertEquals("Before last", getMessageText(result[2]))
        assertEquals("Hi!", getMessageText(result[3]))
    }

    @Test
    fun `AT_DEPTH with depth larger than message count should insert at beginning`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 100,
            content = "Large depth content"
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(3, result.size)
        assertEquals("Large depth content", getMessageText(result[0]))
    }

    @Test
    fun `multiple AT_DEPTH injections with different depths should all apply`() {
        val id1 = Uuid.random()
        val id2 = Uuid.random()

        val injections = listOf(
            createModeInjection(
                id = id1,
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 1,
                content = "Depth 1"
            ),
            createModeInjection(
                id = id2,
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 3,
                content = "Depth 3"
            )
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Message 1"),
            UIMessage.assistant("Response 1"),
            UIMessage.user("Message 2"),
            UIMessage.assistant("Response 2")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(id1, id2)),
            modeInjections = injections,
            lorebooks = emptyList()
        )

        // Both should be inserted
        assertEquals(7, result.size)
        assertTrue(result.any { getMessageText(it).contains("Depth 1") })
        assertTrue(result.any { getMessageText(it).contains("Depth 3") })
    }

    @Test
    fun `multiple AT_DEPTH injections with same depth should be merged`() {
        val id1 = Uuid.random()
        val id2 = Uuid.random()

        val injections = listOf(
            createModeInjection(
                id = id1,
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 2,
                priority = 10,
                content = "Higher priority"
            ),
            createModeInjection(
                id = id2,
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 2,
                priority = 5,
                content = "Lower priority"
            )
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(id1, id2)),
            modeInjections = injections,
            lorebooks = emptyList()
        )

        // Same depth injections should be merged into one message
        assertEquals(4, result.size)
        val injectedText = getMessageText(result[1])
        assertTrue(injectedText.contains("Higher priority"))
        assertTrue(injectedText.contains("Lower priority"))
        // Higher priority should come first
        assertTrue(injectedText.indexOf("Higher priority") < injectedText.indexOf("Lower priority"))
    }
    // endregion

    // region Priority tests
    @Test
    fun `injections should be ordered by priority descending`() {
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        val id3 = Uuid.random()

        val injections = listOf(
            createModeInjection(id = id1, priority = 1, content = "Priority 1"),
            createModeInjection(id = id2, priority = 3, content = "Priority 3"),
            createModeInjection(id = id3, priority = 2, content = "Priority 2")
        )

        val messages = listOf(
            UIMessage.system("System"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(id1, id2, id3)),
            modeInjections = injections,
            lorebooks = emptyList()
        )

        val systemText = getMessageText(result[0])
        // Higher priority should come first when joining
        assertTrue(systemText.contains("Priority 3"))
        assertTrue(systemText.indexOf("Priority 3") < systemText.indexOf("Priority 2"))
        assertTrue(systemText.indexOf("Priority 2") < systemText.indexOf("Priority 1"))
    }
    // endregion

    // region Lorebook tests
    @Test
    fun `lorebook with keyword match should trigger injection`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("magic"),
            content = "Magic system explanation"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Tell me about magic")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("Magic system explanation"))
    }

    @Test
    fun `lorebook without keyword match should not trigger injection`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("magic"),
            content = "Should not appear"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Tell me about science")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        assertEquals(2, result.size)
        val systemText = getMessageText(result[0])
        assertEquals("System prompt", systemText)
    }

    @Test
    fun `lorebook with constantActive should always trigger`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = emptyList(),
            constantActive = true,
            content = "Always active content"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Any message")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("Always active content"))
    }

    @Test
    fun `lorebook with case insensitive match should trigger`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("MAGIC"),
            caseSensitive = false,
            content = "Case insensitive match"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("tell me about magic")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("Case insensitive match"))
    }

    @Test
    fun `lorebook with case sensitive match should not trigger on different case`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("MAGIC"),
            caseSensitive = true,
            content = "Should not appear"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("tell me about magic")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        assertEquals(2, result.size)
        val systemText = getMessageText(result[0])
        assertEquals("System prompt", systemText)
    }

    @Test
    fun `lorebook with regex pattern should match`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("mag.*spell"),
            useRegex = true,
            content = "Regex match content"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Can you explain magic and spell casting?")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("Regex match content"))
    }

    @Test
    fun `scanDepth should limit message scanning range`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("old keyword"),
            scanDepth = 2, // 只扫描最近2条消息
            content = "Should not appear"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Message with old keyword"), // 第1条用户消息（超出扫描范围）
            UIMessage.assistant("Response 1"),
            UIMessage.user("Message 2"),
            UIMessage.assistant("Response 2"),
            UIMessage.user("Latest message") // 最近的消息，不包含关键词
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        // 关键词在第1条用户消息中，但 scanDepth=2 只扫描最后2条
        // 所以不应该触发注入
        assertEquals(6, result.size)
        val systemText = getMessageText(result[0])
        assertEquals("System prompt", systemText)
    }

    @Test
    fun `scanDepth should trigger when keyword is within range`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("latest"),
            scanDepth = 2,
            content = "Triggered content"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Old message"),
            UIMessage.assistant("Response"),
            UIMessage.user("This is the latest message") // 在扫描范围内
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("Triggered content"))
    }

    @Test
    fun `different entries should use their own scanDepth`() {
        val lorebookId = Uuid.random()
        val shallowEntry = createRegexInjection(
            keywords = listOf("old keyword"),
            scanDepth = 1, // 只扫描最后1条
            content = "Shallow scan content"
        )
        val deepEntry = createRegexInjection(
            keywords = listOf("old keyword"),
            scanDepth = 10, // 扫描最后10条
            content = "Deep scan content"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(shallowEntry, deepEntry)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Message with old keyword"), // 较早的消息
            UIMessage.assistant("Response 1"),
            UIMessage.user("Response 2"),
            UIMessage.assistant("Response 3"),
            UIMessage.user("Latest message without keyword")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        // shallowEntry (scanDepth=1) 不应触发，因为最后1条消息不含关键词
        assertTrue(!systemText.contains("Shallow scan content"))
        // deepEntry (scanDepth=10) 应该触发，因为早期消息包含关键词
        assertTrue(systemText.contains("Deep scan content"))
    }

    @Test
    fun `disabled world book should not trigger`() {
        val lorebookId = Uuid.random()
        val regexInjection = createRegexInjection(
            keywords = listOf("magic"),
            content = "Should not appear"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            enabled = false,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Tell me about magic")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(lorebook)
        )

        assertEquals(2, result.size)
        val systemText = getMessageText(result[0])
        assertEquals("System prompt", systemText)
    }
    // endregion

    // region Multiple injections tests
    @Test
    fun `multiple injections at different positions should all apply`() {
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        val id3 = Uuid.random()

        val injections = listOf(
            createModeInjection(
                id = id1,
                position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                content = "Before"
            ),
            createModeInjection(
                id = id2,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                content = "After"
            ),
            createModeInjection(
                id = id3,
                position = InjectionPosition.TOP_OF_CHAT,
                content = "Top"
            )
        )

        val messages = listOf(
            UIMessage.system("System"),
            UIMessage.user("Hello")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(id1, id2, id3)),
            modeInjections = injections,
            lorebooks = emptyList()
        )

        assertEquals(3, result.size)
        val systemText = getMessageText(result[0])
        assertTrue(systemText.startsWith("Before"))
        assertTrue(systemText.contains("System"))
        assertTrue(systemText.endsWith("After"))
        assertEquals("Top", getMessageText(result[1]))
    }

    @Test
    fun `combined mode injection and world book should both apply`() {
        val modeId = Uuid.random()
        val lorebookId = Uuid.random()

        val modeInjection = createModeInjection(
            id = modeId,
            content = "Mode content"
        )

        val regexInjection = createRegexInjection(
            keywords = listOf("hello"),
            content = "WorldBook content"
        )
        val lorebook = createLorebook(
            id = lorebookId,
            entries = listOf(regexInjection)
        )

        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("hello world")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                modeInjectionIds = setOf(modeId),
                lorebookIds = setOf(lorebookId)
            ),
            modeInjections = listOf(modeInjection),
            lorebooks = listOf(lorebook)
        )

        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("Mode content"))
        assertTrue(systemText.contains("WorldBook content"))
    }
    // endregion

    // region collectInjections tests
    @Test
    fun `collectInjections should return empty for no matching conditions`() {
        val result = collectInjections(
            messages = listOf(UIMessage.user("Hello")),
            assistant = createAssistant(),
            modeInjections = listOf(createModeInjection()),
            lorebooks = emptyList()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `collectInjections should collect linked and enabled mode injections`() {
        val id1 = Uuid.random()
        val id2 = Uuid.random()

        val injections = listOf(
            createModeInjection(id = id1, enabled = true),
            createModeInjection(id = id2, enabled = false)
        )

        val result = collectInjections(
            messages = listOf(UIMessage.user("Hello")),
            assistant = createAssistant(modeInjectionIds = setOf(id1, id2)),
            modeInjections = injections,
            lorebooks = emptyList()
        )

        assertEquals(1, result.size)
        assertEquals(id1, result[0].id)
    }
    // endregion

    // region applyInjections tests
    @Test
    fun `applyInjections with empty map should return original messages`() {
        val messages = listOf(
            UIMessage.system("System"),
            UIMessage.user("Hello")
        )

        val result = applyInjections(messages, emptyMap())

        assertEquals(messages, result)
    }

    @Test
    fun `applyInjections should handle messages without system message`() {
        val injection = createModeInjection(
            position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
            content = "Before content"
        )

        val messages = listOf(
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!")
        )

        val result = applyInjections(
            messages,
            mapOf(InjectionPosition.BEFORE_SYSTEM_PROMPT to listOf(injection))
        )

        assertEquals(3, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals("Before content", getMessageText(result[0]))
    }
    // endregion

    // region findSafeInsertIndex tests
    @Test
    fun `findSafeInsertIndex should not insert between USER and ASSISTANT with tools`() {
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Call a tool"),
            createAssistantWithUnexecutedTool("call_1", "tool")
        )

        // 尝试在索引 2（USER 和 ASSISTANT(tool) 之间）插入，应该移到 USER 之前
        val safeIndex = findSafeInsertIndex(messages, 2)
        assertEquals(1, safeIndex)
    }

    @Test
    fun `findSafeInsertIndex should allow insert before ASSISTANT without tools`() {
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!")
        )

        // ASSISTANT 没有 tool，直接插入不受限制
        val safeIndex = findSafeInsertIndex(messages, 2)
        assertEquals(2, safeIndex)
    }

    @Test
    fun `findSafeInsertIndex should return original index when no tools`() {
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi!"),
            UIMessage.user("How are you?")
        )

        assertEquals(3, findSafeInsertIndex(messages, 3))
        assertEquals(2, findSafeInsertIndex(messages, 2))
        assertEquals(0, findSafeInsertIndex(messages, 0))
    }

    @Test
    fun `BOTTOM_OF_CHAT should not inject between USER and ASSISTANT with tools`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.BOTTOM_OF_CHAT,
            content = "Bottom injection"
        )

        // 消息序列: SYSTEM -> USER -> ASSISTANT(tool)
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Call a tool"),
            createAssistantWithUnexecutedTool("call_1", "tool")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(4, result.size)

        // 注入应该在 USER 之前，而不是 USER 和 ASSISTANT(tool) 之间
        val injectedIndex = result.indexOfFirst { getMessageText(it).contains("Bottom injection") }
        val originalUserIndex = result.indexOfFirst { getMessageText(it).contains("Call a tool") }
        val assistantWithToolIndex = result.indexOfFirst { it.getTools().isNotEmpty() }

        assertTrue(injectedIndex < originalUserIndex)
        assertEquals(originalUserIndex + 1, assistantWithToolIndex)
    }

    @Test
    fun `AT_DEPTH should not inject between USER and ASSISTANT with tools`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 1,
            content = "Depth injection"
        )

        // 消息序列: SYSTEM -> USER -> ASSISTANT(tool)
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Call a tool"),
            createAssistantWithUnexecutedTool("call_1", "tool")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(4, result.size)

        val injectedIndex = result.indexOfFirst { getMessageText(it).contains("Depth injection") }
        val originalUserIndex = result.indexOfFirst { getMessageText(it).contains("Call a tool") }
        val assistantWithToolIndex = result.indexOfFirst { it.getTools().isNotEmpty() }

        assertTrue(injectedIndex < originalUserIndex)
        assertEquals(originalUserIndex + 1, assistantWithToolIndex)
    }

    @Test
    fun `injection after ASSISTANT with tools should work normally`() {
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            position = InjectionPosition.BOTTOM_OF_CHAT,
            content = "Bottom injection"
        )

        // 消息序列: SYSTEM -> USER -> ASSISTANT(executed tool) -> ASSISTANT(final) -> USER
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Call a tool"),
            createAssistantWithExecutedTool("call_1", "tool"),
            UIMessage.assistant("Here's the result"),
            UIMessage.user("Thanks!")
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(modeInjectionIds = setOf(injectionId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList()
        )

        assertEquals(6, result.size)

        // 注入应该在最后一条用户消息之前
        val injectedIndex = result.indexOfFirst { getMessageText(it).contains("Bottom injection") }
        val lastUserIndex = result.indexOfLast { it.role == MessageRole.USER && getMessageText(it) == "Thanks!" }
        assertEquals(lastUserIndex - 1, injectedIndex)
    }
    // endregion

    // region Preset entries tests (#182)
    @Test
    fun `preset custom entries should inject in ascending order regardless of list order`() {
        val presetId = Uuid.random()
        // entries 列表故意乱序，order 决定最终拼接顺序
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Custom(
                    order = 2,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Third",
                ),
                PresetEntry.Custom(
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "First",
                ),
                PresetEntry.Custom(
                    order = 1,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Second",
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        // AC2: system 拼接顺序严格等于 entry.order
        assertTrue(systemText.indexOf("First") < systemText.indexOf("Second"))
        assertTrue(systemText.indexOf("Second") < systemText.indexOf("Third"))
    }

    @Test
    fun `preset disabled custom entry should not be injected`() {
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Custom(order = 0, content = "Kept entry", enabled = true),
                PresetEntry.Custom(order = 1, content = "Skipped entry", enabled = false),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        // AC1: 禁用条目不注入，其余照常
        assertTrue(systemText.contains("Kept entry"))
        assertFalse(systemText.contains("Skipped entry"))
    }

    @Test
    fun `preset custom entries should not mix across positions`() {
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Custom(
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "System block",
                ),
                PresetEntry.Custom(
                    order = 0,
                    position = InjectionPosition.TOP_OF_CHAT,
                    content = "Top block",
                    role = MessageRole.USER,
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        // AC3: 按 position 分组，system 条目并入 system 消息，TOP_OF_CHAT 独立成消息
        val systemText = getMessageText(result[0])
        assertTrue(systemText.contains("System block"))
        assertFalse(systemText.contains("Top block"))
        assertEquals("Top block", getMessageText(result[1]))
    }

    @Test
    fun `preset builtin entry is config-only and must not be injected`() {
        // #182 config-only 语义：内置模板 injectable=false，真实注入由各自专用 transformer /
        // 特性流程完成（建议流程等），预设注入路径对 Builtin 一律跳过，避免双注入。
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Builtin(
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    builtinKey = BuiltinPromptRegistry.KEY_SUGGESTION,
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        // Builtin (injectable=false) 不产生任何注入：消息原样返回。
        assertEquals(messages, result)
    }

    @Test
    fun `preset builtin entry with override is still config-only and not injected`() {
        // #182 config-only 语义：即便用户在预设里覆盖了内置模板文案，预设注入路径依然跳过；
        // 覆盖内容只影响该模板专用流程的展示/编辑，不会经预设直接进对话。
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Builtin(
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    builtinKey = BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE,
                    overrideContent = "Custom workspace override text",
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        // override 内容不会被预设路径注入。
        val systemText = getMessageText(result.first())
        assertFalse(systemText.contains("Custom workspace override text"))
        assertEquals(messages, result)
    }

    @Test
    fun `preset builtin entry with unknown key should be skipped without error`() {
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Builtin(
                    order = 0,
                    builtinKey = "totally_unknown_key",
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        // AC6: 未知 key 跳过、不报错、不注入
        assertEquals(messages, result)
    }

    @Test
    fun `preset builtin dynamic template is config-only and macro is never leaked`() {
        // #182 config-only 语义：动态内置模板（workspace_guide 等）的真实注入由
        // WorkspaceReminderTransformer 完成。预设路径跳过 Builtin，因此既不会注入解析后的宏，
        // 也不会把未解析的字面宏（如 {{workspace_name}}）泄漏进 system 文本。
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Builtin(
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    builtinKey = BuiltinPromptRegistry.KEY_WORKSPACE_GUIDE,
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        // 未注入任何内容：既无解析后的值，也无未解析的字面宏泄漏。
        val systemText = getMessageText(result.first())
        assertFalse(systemText.contains("/workspace/proj"))
        assertFalse(systemText.contains("{{workspace_name}}"))
        assertFalse(systemText.contains("{{cwd}}"))
        assertEquals(messages, result)
    }

    @Test
    fun `preset reference entry should resolve global mode injection`() {
        val presetId = Uuid.random()
        val injectionId = Uuid.random()
        val injection = createModeInjection(
            id = injectionId,
            content = "Referenced global content",
        )
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Reference(
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    modeInjectionId = injectionId,
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = listOf(injection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        assertTrue(systemText.contains("Referenced global content"))
    }

    @Test
    fun `direct binding and reference to the same global injection should inject once`() {
        val target = createModeInjection(content = "Reference dedup marker")
        val preset = Preset(
            entries = listOf(PresetEntry.Reference(modeInjectionId = target.id)),
        )

        val result = transformMessages(
            messages = listOf(UIMessage.system("System prompt"), UIMessage.user("Hello")),
            assistant = createAssistant(
                modeInjectionIds = setOf(target.id),
                presetIds = setOf(preset.id),
            ),
            modeInjections = listOf(target),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val occurrences = getMessageText(result.first()).split(target.content).size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun `different custom entries with equal content should remain independent`() {
        val marker = "Equal custom marker"
        val preset = Preset(
            entries = listOf(
                PresetEntry.Custom(order = 0, content = marker),
                PresetEntry.Custom(order = 1, content = marker),
            ),
        )

        val result = transformMessages(
            messages = listOf(UIMessage.system("System prompt"), UIMessage.user("Hello")),
            assistant = createAssistant(presetIds = setOf(preset.id)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val occurrences = getMessageText(result.first()).split(marker).size - 1
        assertEquals(2, occurrences)
    }

    @Test
    fun `preset reference entry to deleted injection should be skipped`() {
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Reference(
                    order = 0,
                    modeInjectionId = Uuid.random(), // 全局不存在
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        // 引用失效则跳过，不报错
        assertEquals(messages, result)
    }
    // endregion

    // region Preset migration tests (#182)
    @Test
    fun `migratedWithEntries should snapshot old modeInjectionIds as custom entries`() {
        val idHigh = Uuid.random()
        val idLow = Uuid.random()
        val injections = listOf(
            createModeInjection(id = idHigh, priority = 10, content = "High priority", name = "High"),
            createModeInjection(id = idLow, priority = 1, content = "Low priority", name = "Low"),
        )
        val preset = Preset(
            id = Uuid.random(),
            modeInjectionIds = setOf(idHigh, idLow),
        )

        val migrated = preset.migratedWithEntries(injections)

        assertTrue(migrated.hasEntries())
        assertEquals(2, migrated.entries.size)
        // priority DESC → order 0..n；High(priority=10) 排前
        val first = migrated.entries.first { it.order == 0 } as PresetEntry.Custom
        val second = migrated.entries.first { it.order == 1 } as PresetEntry.Custom
        assertEquals("High priority", first.content)
        assertEquals("Low priority", second.content)
    }

    @Test
    fun `migratedWithEntries should mark disabled entries as not enabled`() {
        val enabledId = Uuid.random()
        val disabledId = Uuid.random()
        val injections = listOf(
            createModeInjection(id = enabledId, content = "Enabled content"),
            createModeInjection(id = disabledId, content = "Disabled content"),
        )
        val preset = Preset(
            id = Uuid.random(),
            modeInjectionIds = setOf(enabledId, disabledId),
            disabledEntryIds = setOf(disabledId),
        )

        val migrated = preset.migratedWithEntries(injections)

        val enabledEntry = migrated.entries.first { (it as PresetEntry.Custom).content == "Enabled content" }
        val disabledEntry = migrated.entries.first { (it as PresetEntry.Custom).content == "Disabled content" }
        assertTrue(enabledEntry.enabled)
        assertFalse(disabledEntry.enabled)
    }

    @Test
    fun `migratedWithEntries should be idempotent`() {
        val id = Uuid.random()
        val injections = listOf(createModeInjection(id = id, content = "Content"))
        val preset = Preset(id = Uuid.random(), modeInjectionIds = setOf(id))

        val once = preset.migratedWithEntries(injections)
        val twice = once.migratedWithEntries(injections)

        assertEquals(once.entries, twice.entries)
    }

    @Test
    fun `migratedWithEntries should mark empty preset as entries model`() {
        val preset = Preset(id = Uuid.random())

        val migrated = preset.migratedWithEntries(emptyList())

        assertTrue(migrated.hasEntries())
        assertTrue(migrated.entries.isEmpty())
    }

    @Test
    fun `migrated preset should inject equivalently to legacy id path`() {
        val idA = Uuid.random()
        val idB = Uuid.random()
        val injections = listOf(
            createModeInjection(
                id = idA,
                priority = 10,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                content = "Alpha",
            ),
            createModeInjection(
                id = idB,
                priority = 5,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                content = "Beta",
            ),
        )
        val presetId = Uuid.random()
        val legacyPreset = Preset(id = presetId, modeInjectionIds = setOf(idA, idB))
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )
        val assistant = createAssistant(presetIds = setOf(presetId))

        val legacyResult = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = injections,
            lorebooks = emptyList(),
            presets = listOf(legacyPreset),
        )
        val migratedResult = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = injections,
            lorebooks = emptyList(),
            presets = listOf(legacyPreset.migratedWithEntries(injections)),
        )

        // AC5: 迁移后 system 拼接文本与旧路径一致（priority→order 锁定顺序）
        assertEquals(getMessageText(legacyResult.first()), getMessageText(migratedResult.first()))
    }

    @Test
    fun `direct binding and preset entry sharing one id should inject only once`() {
        // #182 去重：同一 injection 既被 assistant 直连绑定，又出现在预设 entries（模拟迁移后快照），
        // collectInjections 按 id 去重（step1 记录 injectedIds，step1b add 失败即跳过），
        // 保证内容只注入一次，避免直连 + entries 双注入。
        val sharedId = Uuid.random()
        val presetId = Uuid.random()
        val marker = "Shared duplicated marker"
        val globalInjection = createModeInjection(
            id = sharedId,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            content = marker,
        )
        // 预设条目复用同一 id（迁移快照会把全局 id 原样带入 Custom.id）
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Custom(
                    id = sharedId,
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = marker,
                ),
            ),
        )
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                modeInjectionIds = setOf(sharedId),
                presetIds = setOf(presetId),
            ),
            modeInjections = listOf(globalInjection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        // 内容只出现一次（未去重会出现两次）
        val occurrences = systemText.split(marker).size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun `migratedWithEntries should inherit globally disabled injection as disabled entry`() {
        // #182 enabled 继承：锁定旧路径 filter{it.enabled} 语义——全局 enabled=false 的注入
        // 迁移后对应 Custom 条目 enabled 也必须为 false（即便它不在 disabledEntryIds 中）。
        val disabledGlobalId = Uuid.random()
        val injections = listOf(
            createModeInjection(id = disabledGlobalId, enabled = false, content = "Globally disabled content"),
        )
        val preset = Preset(
            id = Uuid.random(),
            modeInjectionIds = setOf(disabledGlobalId),
            // 注意：不放进 disabledEntryIds，enabled=false 应仅来自全局 injection.enabled
        )

        val migrated = preset.migratedWithEntries(injections)

        assertEquals(1, migrated.entries.size)
        val entry = migrated.entries.first() as PresetEntry.Custom
        assertEquals("Globally disabled content", entry.content)
        assertFalse(entry.enabled)
    }

    @Test
    fun `migratedWithEntries should record original priority as legacyPriority`() {
        // #182 legacyPriority 存在性：迁移后 Custom.legacyPriority 必须等于原 injection.priority，
        // 供 resolvePresetEntry 混排时参与全局排序（防排序回归）。
        val idHigh = Uuid.random()
        val idLow = Uuid.random()
        val injections = listOf(
            createModeInjection(id = idHigh, priority = 10, content = "High"),
            createModeInjection(id = idLow, priority = 1, content = "Low"),
        )
        val preset = Preset(id = Uuid.random(), modeInjectionIds = setOf(idHigh, idLow))

        val migrated = preset.migratedWithEntries(injections)

        val high = migrated.entries.first { (it as PresetEntry.Custom).content == "High" } as PresetEntry.Custom
        val low = migrated.entries.first { (it as PresetEntry.Custom).content == "Low" } as PresetEntry.Custom
        assertEquals(10, high.legacyPriority)
        assertEquals(1, low.legacyPriority)
    }

    @Test
    fun `migrated preset entry and direct binding should keep legacy priority ordering when mixed`() {
        // #182 legacyPriority 排序锁定（混合来源）：
        // - Alpha(priority=10) 经预设迁移条目注入（legacyPriority=10 参与混排）
        // - Beta(priority=5) 经 assistant 直连绑定注入
        // 二者都 AFTER_SYSTEM_PROMPT。迁移前（legacy 展开）Alpha 在 Beta 前；迁移后必须保持一致，
        // 不因预设条目改走 -order 而排序翻转。
        val idAlpha = Uuid.random()
        val idBeta = Uuid.random()
        val injections = listOf(
            createModeInjection(
                id = idAlpha,
                priority = 10,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                content = "Alpha",
            ),
            createModeInjection(
                id = idBeta,
                priority = 5,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                content = "Beta",
            ),
        )
        val presetId = Uuid.random()
        // 预设仅承载 Alpha；Beta 由 assistant 直连绑定
        val legacyPreset = Preset(id = presetId, modeInjectionIds = setOf(idAlpha))
        val messages = listOf(
            UIMessage.system("System prompt"),
            UIMessage.user("Hello"),
        )
        val assistant = createAssistant(
            modeInjectionIds = setOf(idBeta),
            presetIds = setOf(presetId),
        )

        val legacyResult = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = injections,
            lorebooks = emptyList(),
            presets = listOf(legacyPreset),
        )
        val migratedResult = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = injections,
            lorebooks = emptyList(),
            presets = listOf(legacyPreset.migratedWithEntries(injections)),
        )

        val legacyText = getMessageText(legacyResult.first())
        val migratedText = getMessageText(migratedResult.first())
        // 迁移前后 system 拼接文本一致
        assertEquals(legacyText, migratedText)
        // 混排顺序锁定：Alpha(priority=10) 在 Beta(priority=5) 之前
        assertTrue(migratedText.indexOf("Alpha") < migratedText.indexOf("Beta"))
    }

    @Test
    fun `migration keeps global list order when priorities are equal`() {
        val idFirst = Uuid.random()
        val idSecond = Uuid.random()
        val injections = listOf(
            createModeInjection(id = idFirst, priority = 5, content = "First"),
            createModeInjection(id = idSecond, priority = 5, content = "Second"),
        )
        val preset = Preset(modeInjectionIds = linkedSetOf(idSecond, idFirst))

        val migrated = preset.migratedWithEntries(injections)

        assertEquals(listOf("First", "Second"), migrated.entries.map {
            (it as PresetEntry.Custom).content
        })
    }

    @Test
    fun `equal priority mixed sources keep legacy mode and lorebook order`() {
        val presetInjection = createModeInjection(priority = 5, content = "Preset mode")
        val directInjection = createModeInjection(priority = 5, content = "Direct mode")
        val lorebookEntry = createRegexInjection(
            priority = 5,
            content = "Lorebook entry",
            constantActive = true,
        )
        val presetId = Uuid.random()
        val lorebook = Lorebook(id = Uuid.random(), entries = listOf(lorebookEntry))
        val legacyPreset = Preset(
            id = presetId,
            modeInjectionIds = setOf(presetInjection.id),
        )
        val assistant = createAssistant(
            modeInjectionIds = setOf(directInjection.id),
            presetIds = setOf(presetId),
            lorebookIds = setOf(lorebook.id),
        )
        val modeInjections = listOf(presetInjection, directInjection)
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Hello"))

        val legacyResult = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = modeInjections,
            lorebooks = listOf(lorebook),
            presets = listOf(legacyPreset),
        )
        val migratedResult = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = modeInjections,
            lorebooks = listOf(lorebook),
            presets = listOf(legacyPreset.migratedWithEntries(modeInjections)),
        )

        val legacyText = getMessageText(legacyResult.first())
        val migratedText = getMessageText(migratedResult.first())
        assertEquals(legacyText, migratedText)
        assertTrue(migratedText.indexOf("Preset mode") < migratedText.indexOf("Direct mode"))
        assertTrue(migratedText.indexOf("Direct mode") < migratedText.indexOf("Lorebook entry"))
    }

    @Test
    fun `new preset injection follows the same type sections shown by detail page`() {
        val referenceTarget = createModeInjection(content = "Reference")
        val presetId = Uuid.random()
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Reference(
                    order = 0,
                    modeInjectionId = referenceTarget.id,
                ),
                PresetEntry.Custom(
                    order = 0,
                    content = "Custom",
                ),
            ),
        )
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Hello"))

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(presetIds = setOf(presetId)),
            modeInjections = listOf(referenceTarget),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        assertTrue(systemText.indexOf("Custom") < systemText.indexOf("Reference"))
    }
    // endregion

    // region #205 assembly-time read-only dedupe tests
    @Test
    fun `dual-bound direct and preset entry should inject preset path content once`() {
        // #205: 直连 + 启用预设条目双绑时，组装期只读过滤直连（不再加载期删除），
        // 输出只有预设路径一条注入，不出现重复、也不注入被过滤的直连内容。
        val sharedId = Uuid.random()
        val presetId = Uuid.random()
        val marker = "Preset snapshot marker"
        val globalInjection = createModeInjection(
            id = sharedId,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            content = "Global direct content",
        )
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Custom(
                    id = sharedId,
                    order = 0,
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = marker,
                ),
            ),
        )
        val messages = listOf(UIMessage.system("System prompt"), UIMessage.user("Hello"))

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                modeInjectionIds = setOf(sharedId),
                presetIds = setOf(presetId),
            ),
            modeInjections = listOf(globalInjection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        // 预设快照内容注入一次；直连内容被过滤（不出现），保证双绑不双注入
        assertEquals(1, systemText.split(marker).size - 1)
        assertFalse(systemText.contains("Global direct content"))
    }

    @Test
    fun `direct binding matching reference entry id should not double inject`() {
        // #205: 直连 id 与 Reference 条目 id 相同（entry.id 计入 boundPresetInjectionIds），
        // 组装期过滤直连，仅经 reference 路径注入一次，消除 reference 路径双注入残留。
        val entryId = Uuid.random()
        val targetId = Uuid.random()
        val presetId = Uuid.random()
        val directInjection = createModeInjection(id = entryId, content = "Direct entry-id content")
        val targetInjection = createModeInjection(id = targetId, content = "Referenced target content")
        val preset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Reference(
                    id = entryId,
                    order = 0,
                    modeInjectionId = targetId,
                ),
            ),
        )
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Hello"))

        val result = transformMessages(
            messages = messages,
            assistant = createAssistant(
                modeInjectionIds = setOf(entryId),
                presetIds = setOf(presetId),
            ),
            modeInjections = listOf(directInjection, targetInjection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        val systemText = getMessageText(result.first())
        // 仅 reference 目标注入一次；entry.id 的直连被过滤，不出现直连内容
        assertEquals(1, systemText.split("Referenced target content").size - 1)
        assertFalse(systemText.contains("Direct entry-id content"))
    }

    @Test
    fun `disabled preset entry restores direct binding without residue`() {
        // #205 + #201: 预设条目启用时直连被过滤（只走预设路径）；
        // 条目禁用后直连恢复生效，且不出现「预设条目残留 + 直连」双路径。
        val sharedId = Uuid.random()
        val presetId = Uuid.random()
        val globalContent = "Global direct content"
        val snapshotContent = "Preset snapshot content"
        val globalInjection = createModeInjection(
            id = sharedId,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            content = globalContent,
        )
        val enabledPreset = Preset(
            id = presetId,
            entries = listOf(
                PresetEntry.Custom(id = sharedId, order = 0, enabled = true, content = snapshotContent),
            ),
        )
        val disabledPreset = enabledPreset.copy(
            entries = listOf(
                PresetEntry.Custom(id = sharedId, order = 0, enabled = false, content = snapshotContent),
            ),
        )
        val messages = listOf(UIMessage.system("System prompt"), UIMessage.user("Hello"))
        val assistant = createAssistant(
            modeInjectionIds = setOf(sharedId),
            presetIds = setOf(presetId),
        )

        val withEntry = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = listOf(globalInjection),
            lorebooks = emptyList(),
            presets = listOf(enabledPreset),
        )
        val afterDisable = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = listOf(globalInjection),
            lorebooks = emptyList(),
            presets = listOf(disabledPreset),
        )

        val withEntryText = getMessageText(withEntry.first())
        val afterDisableText = getMessageText(afterDisable.first())
        // 启用：只注入预设快照内容一次，无直连内容
        assertEquals(1, withEntryText.split(snapshotContent).size - 1)
        assertFalse(withEntryText.contains(globalContent))
        // 禁用：直连恢复生效且无预设残留（#201 场景：关闭预设后 system 无残留旧注入）
        assertEquals(1, afterDisableText.split(globalContent).size - 1)
        assertFalse(afterDisableText.contains(snapshotContent))
    }

    @Test
    fun `collectInjections should not return duplicate ids for dual-bound direct and preset entry`() {
        val sharedId = Uuid.random()
        val presetId = Uuid.random()
        val globalInjection = createModeInjection(id = sharedId, content = "shared")
        val preset = Preset(
            id = presetId,
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap")),
        )

        val result = collectInjections(
            messages = listOf(UIMessage.user("Hello")),
            assistant = createAssistant(
                modeInjectionIds = setOf(sharedId),
                presetIds = setOf(presetId),
            ),
            modeInjections = listOf(globalInjection),
            lorebooks = emptyList(),
            presets = listOf(preset),
        )

        assertEquals(1, result.size)
    }
    // endregion
}
