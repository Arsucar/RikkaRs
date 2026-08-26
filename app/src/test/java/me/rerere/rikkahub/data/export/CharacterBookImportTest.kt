package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.ui.pages.assistant.detail.detectWorldBookFromCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterBookImportTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String) = json.parseToJsonElement(raw)

    private fun regexEntries(lorebook: Lorebook) = lorebook.entries

    @Test
    fun `v2 character book maps keys insertion order and before char`() {
        val element = parse(
            """
            {
              "name": "Card Book",
              "description": "from card",
              "entries": [
                {
                  "keys": ["alice", "hero"],
                  "content": "Alice is the hero.",
                  "enabled": true,
                  "insertion_order": 42,
                  "case_sensitive": true,
                  "comment": "Alice lore",
                  "constant": true,
                  "position": "before_char"
                }
              ]
            }
            """.trimIndent()
        )

        val lorebook = tryImportCharacterBook(element, "Fallback")

        assertNotNull(lorebook)
        lorebook!!
        assertEquals("Card Book", lorebook.name)
        assertEquals("from card", lorebook.description)
        assertTrue(lorebook.enabled)
        assertEquals(1, lorebook.entries.size)

        val entry = lorebook.entries.single()
        assertEquals("Alice lore", entry.name)
        assertEquals(listOf("alice", "hero"), entry.keywords)
        assertEquals("Alice is the hero.", entry.content)
        assertTrue(entry.enabled)
        assertEquals(42, entry.priority)
        assertTrue(entry.caseSensitive)
        assertTrue(entry.constantActive)
        assertEquals(InjectionPosition.BEFORE_SYSTEM_PROMPT, entry.position)
        assertEquals(4, entry.injectDepth)
        assertFalse(entry.useRegex)
    }

    @Test
    fun `after char maps to after system prompt`() {
        val element = parse(
            """
            {
              "entries": [
                {
                  "keys": ["bob"],
                  "content": "Bob.",
                  "insertion_order": 7,
                  "position": "after_char"
                }
              ]
            }
            """.trimIndent()
        )

        val entry = regexEntries(tryImportCharacterBook(element, "World Book")!!).single()

        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, entry.position)
        assertEquals(7, entry.priority)
        assertEquals("bob", entry.name)
        assertFalse(entry.caseSensitive)
    }

    @Test
    fun `unknown or missing position defaults to after system prompt`() {
        val missing = parse("""{"entries":[{"keys":["k"],"content":"c"}]}""")
        val unknown = parse("""{"entries":[{"keys":["k"],"content":"c","position":"at_depth"}]}""")

        assertEquals(
            InjectionPosition.AFTER_SYSTEM_PROMPT,
            regexEntries(tryImportCharacterBook(missing, "n")!!).single().position,
        )
        assertEquals(
            InjectionPosition.AFTER_SYSTEM_PROMPT,
            regexEntries(tryImportCharacterBook(unknown, "n")!!).single().position,
        )
    }

    @Test
    fun `serial name maps insertion order and case sensitive instead of defaults`() {
        val element = parse(
            """
            {
              "entries": [
                {
                  "keys": ["k"],
                  "content": "c",
                  "insertion_order": 3,
                  "case_sensitive": true
                }
              ]
            }
            """.trimIndent()
        )

        val entry = regexEntries(tryImportCharacterBook(element, "n")!!).single()

        assertEquals(3, entry.priority)
        assertTrue(entry.caseSensitive)
        assertNull(CharacterBookEntry().insertionOrder)
        assertNull(CharacterBookEntry().caseSensitive)
    }

    @Test
    fun `explicit json nulls on optional fields still import entries`() {
        val element = parse(
            """
            {
              "name": null,
              "description": null,
              "entries": [
                {
                  "keys": ["k"],
                  "content": "c",
                  "enabled": null,
                  "insertion_order": null,
                  "case_sensitive": null,
                  "constant": null,
                  "position": null,
                  "extensions": null
                }
              ]
            }
            """.trimIndent()
        )

        val lorebook = tryImportCharacterBook(element, "Fallback")
        assertNotNull(lorebook)
        lorebook!!
        assertEquals("Fallback", lorebook.name)
        val entry = lorebook.entries.single()
        assertEquals(listOf("k"), entry.keywords)
        assertEquals("c", entry.content)
        assertTrue(entry.enabled)
        assertEquals(100, entry.priority)
        assertFalse(entry.caseSensitive)
        assertFalse(entry.constantActive)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, entry.position)
    }

    @Test
    fun `blank book name falls back to provided name`() {
        val unnamed = parse("""{"name":"","entries":[{"keys":["k"],"content":"c"}]}""")
        val missing = parse("""{"entries":[{"keys":["k"],"content":"c"}]}""")

        assertEquals("CardName", tryImportCharacterBook(unnamed, "CardName")!!.name)
        assertEquals("CardName", tryImportCharacterBook(missing, "CardName")!!.name)
    }

    @Test
    fun `entry name prefers comment then name then first key`() {
        val comment = parse(
            """{"entries":[{"keys":["k"],"content":"c","comment":"from comment","name":"from name"}]}"""
        )
        val name = parse(
            """{"entries":[{"keys":["k"],"content":"c","comment":"  ","name":"from name"}]}"""
        )
        val key = parse("""{"entries":[{"keys":["first"],"content":"c"}]}""")

        assertEquals("from comment", regexEntries(tryImportCharacterBook(comment, "n")!!).single().name)
        assertEquals("from name", regexEntries(tryImportCharacterBook(name, "n")!!).single().name)
        assertEquals("first", regexEntries(tryImportCharacterBook(key, "n")!!).single().name)
    }

    @Test
    fun `missing keys and content are included without crashing`() {
        val element = parse(
            """
            {
              "entries": [
                { "enabled": false },
                { "keys": [], "content": "" }
              ]
            }
            """.trimIndent()
        )

        val lorebook = tryImportCharacterBook(element, "n")!!
        val entries = regexEntries(lorebook)

        assertEquals(2, entries.size)
        assertEquals(emptyList<String>(), entries[0].keywords)
        assertEquals("", entries[0].content)
        assertFalse(entries[0].enabled)
        assertEquals("", entries[0].name)
        assertEquals(emptyList<String>(), entries[1].keywords)
        assertEquals("", entries[1].content)
    }

    @Test
    fun `empty character book and empty entries return null`() {
        assertNull(tryImportCharacterBook(parse("{}"), "n"))
        assertNull(tryImportCharacterBook(parse("""{"name":"Book","entries":[]}"""), "n"))
    }

    @Test
    fun `null non object and garbage json return null`() {
        assertNull(tryImportCharacterBook(null, "n"))
        assertNull(tryImportCharacterBook(JsonNull, "n"))
        assertNull(tryImportCharacterBook(JsonPrimitive("not-an-object"), "n"))
        assertNull(tryImportCharacterBook(parse("""["array"]"""), "n"))
        assertNull(tryImportCharacterBook(parse("""{"entries":"nope"}"""), "n"))
    }

    @Test
    fun `standalone world info map shape is not a character book`() {
        val standalone = parse(
            """
            {
              "entries": {
                "0": {
                  "key": ["alice"],
                  "content": "Alice",
                  "disable": false,
                  "order": 10,
                  "position": 0
                }
              }
            }
            """.trimIndent()
        )

        assertNull(tryImportCharacterBook(standalone, "n"))
    }

    @Test
    fun `repeated imports get unique lorebook and entry ids`() {
        val element = parse("""{"entries":[{"keys":["k"],"content":"c"}]}""")

        val first = tryImportCharacterBook(element, "n")!!
        val second = tryImportCharacterBook(element, "n")!!

        assertTrue(first.id != second.id)
        assertTrue(first.entries.single().id != second.entries.single().id)
    }

    @Test
    fun `detectWorldBookFromCard reads canonical character book`() {
        val card = json.parseToJsonElement(
            """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "Alice",
                "character_book": {
                  "name": "Alice World",
                  "entries": [
                    {
                      "keys": ["alice"],
                      "content": "Hero",
                      "insertion_order": 5,
                      "position": "before_char"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val lorebooks = detectWorldBookFromCard(card)

        assertEquals(1, lorebooks.size)
        assertEquals("Alice World", lorebooks.single().name)
        val entry = lorebooks.single().entries.single()
        assertEquals(listOf("alice"), entry.keywords)
        assertEquals(5, entry.priority)
        assertEquals(InjectionPosition.BEFORE_SYSTEM_PROMPT, entry.position)
    }

    @Test
    fun `detectWorldBookFromCard uses extensions character book when canonical is absent`() {
        val card = json.parseToJsonElement(
            """
            {
              "spec": "chara_card_v3",
              "data": {
                "name": "Bob",
                "extensions": {
                  "world": "bob-world.json",
                  "character_book": {
                    "entries": [
                      { "keys": ["bob"], "content": "Sidekick", "position": "after_char" }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val lorebooks = detectWorldBookFromCard(card)

        assertEquals(1, lorebooks.size)
        assertEquals("bob-world.json", lorebooks.single().name)
        assertEquals("Sidekick", lorebooks.single().entries.single().content)
    }

    @Test
    fun `detectWorldBookFromCard treats extensions world string as name not lorebook`() {
        val card = json.parseToJsonElement(
            """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "Carol",
                "extensions": { "world": "carol-world.json" }
              }
            }
            """.trimIndent()
        ).jsonObject

        assertEquals(emptyList<Lorebook>(), detectWorldBookFromCard(card))
    }

    @Test
    fun `detectWorldBookFromCard returns empty when there are no bindings`() {
        val card = json.parseToJsonElement(
            """
            {
              "spec": "chara_card_v2",
              "data": { "name": "Dana" }
            }
            """.trimIndent()
        ).jsonObject

        assertTrue(detectWorldBookFromCard(card).isEmpty())
    }

    @Test
    fun `detectWorldBookFromCard returns empty for empty character book`() {
        val card = json.parseToJsonElement(
            """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "Eve",
                "character_book": { "name": "Empty", "entries": [] }
              }
            }
            """.trimIndent()
        ).jsonObject

        assertTrue(detectWorldBookFromCard(card).isEmpty())
    }
}
