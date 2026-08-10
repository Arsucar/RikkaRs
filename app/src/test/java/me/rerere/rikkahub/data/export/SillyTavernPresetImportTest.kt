package me.rerere.rikkahub.data.export

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SillyTavern 提示词预设导入测试（见 issue #188）。
 *
 * `import(context, uri)` 需要 Android Context，这里只覆盖 Context-free 的
 * [PresetSerializer.tryImportSillyTavernPreset] 与映射函数。
 */
class SillyTavernPresetImportTest {

    private fun customEntries(preset: Preset): List<PresetEntry.Custom> =
        preset.entries.map { it as PresetEntry.Custom }

    // ---------------------------------------------------------------- 完整预设

    private val fullPresetJson = """
        {
          "impersonation_prompt": "Write as {{user}}.",
          "wi_format": "[Details: {{wi}}]",
          "prompts": [
            {
              "identifier": "main",
              "name": "Main Prompt",
              "role": "system",
              "content": "You are {{char}}.",
              "enabled": true,
              "injection_position": 0
            },
            {
              "identifier": "chatHistory",
              "name": "Chat History",
              "marker": true
            },
            {
              "identifier": "jailbreak",
              "name": "Jailbreak",
              "role": "user",
              "content": "Stay in character.",
              "enabled": true,
              "injection_position": 1,
              "injection_depth": 2
            },
            {
              "identifier": "nsfw",
              "name": "NSFW",
              "role": "assistant",
              "content": "Understood.",
              "enabled": false,
              "injection_position": 0
            }
          ],
          "prompt_order": [
            { "character_id": 100000, "order": [] },
            {
              "character_id": 100001,
              "order": [
                { "identifier": "main", "enabled": true },
                { "identifier": "chatHistory", "enabled": true },
                { "identifier": "jailbreak", "enabled": true },
                { "identifier": "nsfw", "enabled": true },
                { "identifier": "ghost-does-not-exist", "enabled": true }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `full silly tavern preset maps order position depth role and enabled`() {
        val preset = PresetSerializer.tryImportSillyTavernPreset(fullPresetJson, "My ST Preset")

        assertNotNull(preset)
        preset as Preset
        assertEquals("My ST Preset", preset.name)
        assertEquals(PRESET_ENTRIES_VERSION, preset.entriesVersion)

        // marker 条目与 prompt_order 里不存在的 identifier 都不产生条目
        val entries = customEntries(preset)
        assertEquals(3, entries.size)
        assertEquals(listOf("Main Prompt", "Jailbreak", "NSFW"), entries.map { it.name })
        // order 按最终顺序重编号 0..n
        assertEquals(listOf(0, 1, 2), entries.map { it.order })

        val main = entries[0]
        assertEquals("You are {{char}}.", main.content)
        // ST 的 system 只用于把条目定位到 system 块，落到条目上归一为 USER（该位置 role 被忽略）
        assertEquals(MessageRole.USER, main.role)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, main.position)
        assertEquals(4, main.injectDepth)
        assertTrue(main.enabled)

        val jailbreak = entries[1]
        assertEquals(MessageRole.USER, jailbreak.role)
        assertEquals(InjectionPosition.AT_DEPTH, jailbreak.position)
        assertEquals(2, jailbreak.injectDepth)
        assertTrue(jailbreak.enabled)

        val nsfw = entries[2]
        assertEquals(MessageRole.ASSISTANT, nsfw.role)
        assertEquals(InjectionPosition.TOP_OF_CHAT, nsfw.position)
        // prompts[].enabled = false，即使 prompt_order 里 enabled = true 也应禁用
        assertFalse(nsfw.enabled)
    }

    @Test
    fun `entry ids are unique across repeated imports`() {
        val first = PresetSerializer.tryImportSillyTavernPreset(fullPresetJson, "a")!!
        val second = PresetSerializer.tryImportSillyTavernPreset(fullPresetJson, "a")!!

        val ids = (first.entries + second.entries).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(first.id != second.id)
    }

    // ------------------------------------------------------------ prompt_order

    @Test
    fun `prompt order drives sequence and ands enabled flags`() {
        val json = """
            {
              "prompts": [
                { "identifier": "a", "name": "A", "role": "system", "content": "a", "enabled": true },
                { "identifier": "b", "name": "B", "role": "system", "content": "b", "enabled": true },
                { "identifier": "c", "name": "C", "role": "system", "content": "c", "enabled": false }
              ],
              "prompt_order": [
                {
                  "character_id": 100001,
                  "order": [
                    { "identifier": "c", "enabled": true },
                    { "identifier": "b", "enabled": false },
                    { "identifier": "a", "enabled": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(listOf("C", "B", "A"), entries.map { it.name })
        // c: prompts=false & order=true -> false; b: prompts=true & order=false -> false; a: 两者 true -> true
        assertEquals(listOf(false, false, true), entries.map { it.enabled })
    }

    @Test
    fun `prompt order prefers the global default group over a non empty dummy group`() {
        val json = """
            {
              "prompts": [
                { "identifier": "a", "name": "A", "role": "system", "content": "a" },
                { "identifier": "b", "name": "B", "role": "system", "content": "b" }
              ],
              "prompt_order": [
                {
                  "character_id": 100000,
                  "order": [
                    { "identifier": "a", "enabled": true }
                  ]
                },
                {
                  "character_id": 100001,
                  "order": [
                    { "identifier": "b", "enabled": true },
                    { "identifier": "a", "enabled": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        // 100000 是 dummy 组，即使非空也应让位给全局默认组 100001
        assertEquals(listOf("B", "A"), entries.map { it.name })
    }

    @Test
    fun `prompt order falls back to first non empty group when default group is absent`() {
        val json = """
            {
              "prompts": [
                { "identifier": "a", "name": "A", "role": "system", "content": "a" },
                { "identifier": "b", "name": "B", "role": "system", "content": "b" }
              ],
              "prompt_order": [
                { "character_id": 42, "order": [] },
                {
                  "character_id": 7,
                  "order": [
                    { "identifier": "b", "enabled": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(listOf("B"), entries.map { it.name })
    }

    @Test
    fun `missing prompt order falls back to prompts declaration order`() {
        val json = """
            {
              "prompts": [
                { "identifier": "z", "name": "Z", "role": "system", "content": "z" },
                { "identifier": "y", "name": "Y", "role": "system", "content": "y" }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(listOf("Z", "Y"), entries.map { it.name })
        // 无 prompt_order 时全部视为启用
        assertTrue(entries.all { it.enabled })
    }

    @Test
    fun `prompt order entries referencing unknown identifiers are skipped`() {
        val json = """
            {
              "prompts": [
                { "identifier": "known", "name": "Known", "role": "system", "content": "hi" }
              ],
              "prompt_order": [
                {
                  "character_id": 100001,
                  "order": [
                    { "identifier": "phantom", "enabled": true },
                    { "identifier": "known", "enabled": true },
                    { "identifier": "", "enabled": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(listOf("Known"), entries.map { it.name })
    }

    @Test
    fun `duplicate identifiers keep the first prompt`() {
        val json = """
            {
              "prompts": [
                { "identifier": "dup", "name": "First", "role": "system", "content": "first" },
                { "identifier": "dup", "name": "Second", "role": "system", "content": "second" }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(1, entries.size)
        assertEquals("First", entries.single().name)
        assertEquals("first", entries.single().content)
    }

    @Test
    fun `duplicate identifiers in prompt order are only emitted once`() {
        val json = """
            {
              "prompts": [
                { "identifier": "one", "name": "One", "role": "system", "content": "one" }
              ],
              "prompt_order": [
                {
                  "character_id": 100001,
                  "order": [
                    { "identifier": "one", "enabled": true },
                    { "identifier": "one", "enabled": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(1, entries.size)
    }

    // --------------------------------------------------------------- 跳过规则

    @Test
    fun `marker prompts are skipped and counted in description`() {
        val json = """
            {
              "prompts": [
                { "identifier": "worldInfoBefore", "name": "World Info (before)", "marker": true },
                { "identifier": "charDescription", "name": "Char Description", "marker": true },
                { "identifier": "real", "name": "Real", "role": "system", "content": "body" }
              ]
            }
        """.trimIndent()

        val preset = PresetSerializer.tryImportSillyTavernPreset(json, "p")!!

        assertEquals(listOf("Real"), customEntries(preset).map { it.name })
        assertTrue(preset.description.contains("Skipped 2 SillyTavern marker prompt(s)."))
    }

    @Test
    fun `blank content non marker prompts are skipped`() {
        val json = """
            {
              "prompts": [
                { "identifier": "empty", "name": "Empty", "role": "system", "content": "" },
                { "identifier": "blank", "name": "Blank", "role": "system", "content": "   " },
                { "identifier": "absent", "name": "Absent", "role": "system" },
                { "identifier": "kept", "name": "Kept", "role": "system", "content": "body" }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(listOf("Kept"), entries.map { it.name })
    }

    @Test
    fun `name falls back to identifier when name is blank`() {
        val json = """
            {
              "prompts": [
                { "identifier": "fallback-id", "name": "", "role": "system", "content": "body" }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals("fallback-id", entries.single().name)
    }

    // --------------------------------------------------------------- 位置映射

    @Test
    fun `injection position and role matrix maps to preset positions`() {
        // absolute（1）一律 AT_DEPTH，与 role 无关
        MessageRole.entries.forEach { role ->
            assertEquals(
                InjectionPosition.AT_DEPTH,
                PresetSerializer.mapSillyTavernPresetPosition(1, role),
            )
        }

        // relative（0）/ 非法 / 缺失：system 进 system 块，其余进对话开头
        listOf(0, 2, 99, -1, null).forEach { position ->
            assertEquals(
                InjectionPosition.AFTER_SYSTEM_PROMPT,
                PresetSerializer.mapSillyTavernPresetPosition(position, MessageRole.SYSTEM),
            )
            assertEquals(
                InjectionPosition.TOP_OF_CHAT,
                PresetSerializer.mapSillyTavernPresetPosition(position, MessageRole.USER),
            )
            assertEquals(
                InjectionPosition.TOP_OF_CHAT,
                PresetSerializer.mapSillyTavernPresetPosition(position, MessageRole.ASSISTANT),
            )
        }
    }

    @Test
    fun `absolute injection position defaults depth to four when depth is missing`() {
        val json = """
            {
              "prompts": [
                {
                  "identifier": "deep",
                  "name": "Deep",
                  "role": "user",
                  "content": "body",
                  "injection_position": 1
                }
              ]
            }
        """.trimIndent()

        val entry = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!).single()

        assertEquals(InjectionPosition.AT_DEPTH, entry.position)
        assertEquals(4, entry.injectDepth)
    }

    @Test
    fun `role mapping is case insensitive and defaults to system`() {
        assertEquals(MessageRole.USER, PresetSerializer.mapSillyTavernPromptRole("user"))
        assertEquals(MessageRole.USER, PresetSerializer.mapSillyTavernPromptRole("USER"))
        assertEquals(MessageRole.ASSISTANT, PresetSerializer.mapSillyTavernPromptRole("Assistant"))
        assertEquals(MessageRole.SYSTEM, PresetSerializer.mapSillyTavernPromptRole("system"))
        assertEquals(MessageRole.SYSTEM, PresetSerializer.mapSillyTavernPromptRole("bogus-role"))
        assertEquals(MessageRole.SYSTEM, PresetSerializer.mapSillyTavernPromptRole(""))
        assertEquals(MessageRole.SYSTEM, PresetSerializer.mapSillyTavernPromptRole(null))
    }

    @Test
    fun `missing or illegal role in json is treated as system for positioning`() {
        val json = """
            {
              "prompts": [
                { "identifier": "noRole", "name": "No Role", "content": "a" },
                { "identifier": "badRole", "name": "Bad Role", "role": "moderator", "content": "b" }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(2, entries.size)
        // 默认按 SYSTEM 处理，意味着默认位置进 system 块
        assertTrue(entries.all { it.position == InjectionPosition.AFTER_SYSTEM_PROMPT })
        // 但条目本身不得携带 SYSTEM（PresetEntry 契约只承认 USER / ASSISTANT）
        assertTrue(entries.none { it.role == MessageRole.SYSTEM })
        assertTrue(entries.all { it.role == MessageRole.USER })
    }

    @Test
    fun `entry role never leaks system into preset entries`() {
        assertEquals(
            MessageRole.USER,
            PresetSerializer.normalizeSillyTavernEntryRole(MessageRole.SYSTEM),
        )
        assertEquals(
            MessageRole.USER,
            PresetSerializer.normalizeSillyTavernEntryRole(MessageRole.USER),
        )
        assertEquals(
            MessageRole.ASSISTANT,
            PresetSerializer.normalizeSillyTavernEntryRole(MessageRole.ASSISTANT),
        )
        // 兜底：TOOL 之类的非法取值也不得落进条目
        assertEquals(
            MessageRole.USER,
            PresetSerializer.normalizeSillyTavernEntryRole(MessageRole.TOOL),
        )
    }

    @Test
    fun `system prompts at depth are imported as user role entries`() {
        // injection_position = 1 + role = system：归一前会产出 AT_DEPTH 的 SYSTEM 条目，
        // 注入期被 groupBy{role} 拆成额外一条消息并打乱同深度顺序（见 issue #188 审查）
        val json = """
            {
              "prompts": [
                {
                  "identifier": "deepSystem",
                  "name": "Deep System",
                  "role": "system",
                  "content": "rules",
                  "injection_position": 1,
                  "injection_depth": 3
                },
                {
                  "identifier": "deepUser",
                  "name": "Deep User",
                  "role": "user",
                  "content": "reminder",
                  "injection_position": 1,
                  "injection_depth": 3
                }
              ]
            }
        """.trimIndent()

        val entries = customEntries(PresetSerializer.tryImportSillyTavernPreset(json, "p")!!)

        assertEquals(listOf("Deep System", "Deep User"), entries.map { it.name })
        assertTrue(entries.all { it.position == InjectionPosition.AT_DEPTH })
        assertTrue(entries.all { it.injectDepth == 3 })
        // 同深度条目 role 一致，注入时不会被拆成两条消息、顺序不翻转
        assertEquals(listOf(MessageRole.USER, MessageRole.USER), entries.map { it.role })
    }

    // ----------------------------------------------------------------- 不识别

    @Test
    fun `json without prompts is not recognized`() {
        assertNull(PresetSerializer.tryImportSillyTavernPreset("""{"temperature":1.0}""", "p"))
    }

    @Test
    fun `empty prompts array is not recognized`() {
        assertNull(PresetSerializer.tryImportSillyTavernPreset("""{"prompts":[]}""", "p"))
    }

    @Test
    fun `null prompts value is not recognized`() {
        assertNull(PresetSerializer.tryImportSillyTavernPreset("""{"prompts":null}""", "p"))
    }

    @Test
    fun `preset with only marker prompts is not recognized`() {
        val json = """
            {
              "prompts": [
                { "identifier": "chatHistory", "name": "Chat History", "marker": true },
                { "identifier": "dialogueExamples", "name": "Examples", "marker": true }
              ]
            }
        """.trimIndent()

        assertNull(PresetSerializer.tryImportSillyTavernPreset(json, "p"))
    }

    @Test
    fun `preset whose prompts are all blank is not recognized`() {
        val json = """{"prompts":[{"identifier":"a","name":"A","content":""}]}"""

        assertNull(PresetSerializer.tryImportSillyTavernPreset(json, "p"))
    }

    @Test
    fun `malformed json is not recognized`() {
        assertNull(PresetSerializer.tryImportSillyTavernPreset("{not json", "p"))
        assertNull(PresetSerializer.tryImportSillyTavernPreset("", "p"))
        // prompts 类型不对时解析异常被吞掉，返回 null 而不是崩溃
        assertNull(PresetSerializer.tryImportSillyTavernPreset("""{"prompts":"nope"}""", "p"))
    }

    @Test
    fun `native preset json is not consumed by the silly tavern branch`() {
        val native = PresetSerializer.exportToJson(
            Preset(
                name = "Native",
                entries = listOf(PresetEntry.Custom(name = "n", content = "body")),
                entriesVersion = PRESET_ENTRIES_VERSION,
            )
        )

        assertNull(PresetSerializer.tryImportSillyTavernPreset(native, "Native"))
    }

    // ------------------------------------------------------------- description

    @Test
    fun `unmapped top level settings are listed in description`() {
        val json = """
            {
              "impersonation_prompt": "a",
              "new_chat_prompt": "b",
              "new_group_chat_prompt": "c",
              "new_example_chat_prompt": "d",
              "continue_nudge_prompt": "e",
              "scenario_format": "f",
              "personality_format": "g",
              "group_nudge_prompt": "h",
              "wi_format": "i",
              "character_id": 100001,
              "prompts": [
                { "identifier": "a", "name": "A", "role": "system", "content": "body" }
              ]
            }
        """.trimIndent()

        val description = PresetSerializer.tryImportSillyTavernPreset(json, "p")!!.description

        listOf(
            "impersonation_prompt",
            "new_chat_prompt",
            "new_group_chat_prompt",
            "new_example_chat_prompt",
            "continue_nudge_prompt",
            "scenario_format",
            "personality_format",
            "group_nudge_prompt",
            "wi_format",
        ).forEach { key ->
            assertTrue("description should mention $key", description.contains(key))
        }
        // 没有 marker 条目时不追加 marker 提示
        assertFalse(description.contains("marker prompt"))
    }

    @Test
    fun `description is empty when nothing was dropped`() {
        val json = """
            {
              "prompts": [
                { "identifier": "a", "name": "A", "role": "system", "content": "body" }
              ]
            }
        """.trimIndent()

        assertEquals("", PresetSerializer.tryImportSillyTavernPreset(json, "p")!!.description)
    }

    @Test
    fun `blank file name falls back to a generated name`() {
        val json = """{"prompts":[{"identifier":"a","name":"A","role":"system","content":"body"}]}"""

        assertTrue(PresetSerializer.tryImportSillyTavernPreset(json, null)!!.name.isNotBlank())
        assertTrue(PresetSerializer.tryImportSillyTavernPreset(json, "  ")!!.name.isNotBlank())
    }

    // ------------------------------------------------------- R5 自有格式导入修复

    @Test
    fun `native import resets preset and entry ids`() {
        val original = Preset(
            name = "Native",
            entries = listOf(
                PresetEntry.Custom(name = "custom", content = "body"),
                PresetEntry.Builtin(builtinKey = "suggestion"),
            ),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val json = PresetSerializer.exportToJson(original)

        val imported = PresetSerializer.tryImportNative(json)!!

        assertTrue(imported.id != original.id)
        assertEquals(original.entries.size, imported.entries.size)
        val originalIds = original.entries.map { it.id }.toSet()
        assertTrue(imported.entries.none { it.id in originalIds })
        // 变体与内容保持不变，只有 id 重随机
        assertEquals(
            original.entries.map { it::class },
            imported.entries.map { it::class },
        )
        assertEquals("body", (imported.entries[0] as PresetEntry.Custom).content)
    }

    @Test
    fun `native import ignores export data of another type`() {
        val lorebookJson = LorebookSerializer.exportToJson(Lorebook(name = "L"))

        assertNull(PresetSerializer.tryImportNative(lorebookJson))
    }
}
