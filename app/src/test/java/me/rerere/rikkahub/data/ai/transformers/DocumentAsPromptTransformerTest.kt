package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.UploadInjectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAsPromptTransformerTest {
    @Test
    fun `document prompts are appended in attachment order`() {
        val documents = listOf(
            document("a.txt"),
            document("b.txt"),
            document("c.txt"),
        )
        val originalParts = listOf(
            UIMessagePart.Text("before"),
            documents[0],
            UIMessagePart.Text("between"),
            documents[1],
            documents[2],
        )
        val parts = originalParts.toMutableList()

        appendDocumentPromptsInOrder(
            parts = parts,
            readContent = { "body-${it.fileName}" },
            resolvePath = { if (it.fileName == "a.txt") "/upload/a.txt" else null },
        )

        assertEquals(
            listOf(
                UIMessagePart.Text("before"),
                UIMessagePart.Text("between"),
            ),
            parts.filterIsInstance<UIMessagePart.Text>().take(2),
        )
        assertFalse(parts.any { it is UIMessagePart.Document })
        assertEquals(2 + documents.size, parts.size)

        val prompts = parts.drop(2).map { (it as UIMessagePart.Text).text }
        assertTrue(prompts[0].contains("<UploadFile name=\"a.txt\" path=\"/upload/a.txt\">"))
        assertTrue(prompts[0].contains("body-a.txt"))
        assertTrue(prompts[1].contains("<UploadFile name=\"b.txt\">"))
        assertTrue(prompts[1].contains("body-b.txt"))
        assertTrue(prompts[2].contains("<UploadFile name=\"c.txt\">"))
        assertTrue(prompts[2].contains("body-c.txt"))
    }

    @Test
    fun `single and empty document lists keep existing parts stable`() {
        val noDocuments = mutableListOf<UIMessagePart>(UIMessagePart.Text("text only"))
        appendDocumentPromptsInOrder(noDocuments, { error("not called") }, { error("not called") })
        assertEquals(listOf(UIMessagePart.Text("text only")), noDocuments)

        val document = document("only.txt")
        val singleDocument = mutableListOf<UIMessagePart>(document)
        appendDocumentPromptsInOrder(singleDocument, { "only body" }, { null })

        assertFalse(singleDocument.any { it is UIMessagePart.Document })
        assertEquals(1, singleDocument.size)
        val prompt = (singleDocument[0] as UIMessagePart.Text).text
        assertTrue(prompt.contains("<UploadFile name=\"only.txt\">"))
        assertTrue(prompt.contains("only body"))
    }

    @Test
    fun `path only with workspace ready injects name and path stub without body`() {
        val document = document("notes.txt")
        val parts = mutableListOf<UIMessagePart>(document)
        var readCalled = false

        appendDocumentPromptsInOrder(
            parts = parts,
            readContent = {
                readCalled = true
                "should-not-appear"
            },
            resolvePath = { "/upload/notes.txt" },
            mode = UploadInjectMode.PATH_ONLY,
            workspaceReady = true,
        )

        assertFalse(readCalled)
        assertEquals(2, parts.size)
        val prompt = (parts[1] as UIMessagePart.Text).text
        assertEquals(
            """<UploadFile name="notes.txt" path="/upload/notes.txt"></UploadFile>""",
            prompt,
        )
        assertFalse(prompt.contains("should-not-appear"))
        assertFalse(prompt.contains("```"))
    }

    @Test
    fun `path only without resolvable path keeps name-only stub`() {
        val document = document("orphan.txt")
        val parts = mutableListOf<UIMessagePart>(document)

        appendDocumentPromptsInOrder(
            parts = parts,
            readContent = { error("not called") },
            resolvePath = { null },
            mode = UploadInjectMode.PATH_ONLY,
            workspaceReady = true,
        )

        assertEquals(2, parts.size)
        val prompt = (parts[1] as UIMessagePart.Text).text
        assertEquals("""<UploadFile name="orphan.txt"></UploadFile>""", prompt)
        assertFalse(prompt.contains("```"))
    }

    @Test
    fun `path only falls back to full body when workspace is unavailable`() {
        val document = document("report.txt")
        val parts = mutableListOf<UIMessagePart>(document)

        appendDocumentPromptsInOrder(
            parts = parts,
            readContent = { "full-body-report" },
            resolvePath = { "/upload/report.txt" },
            mode = UploadInjectMode.PATH_ONLY,
            workspaceReady = false,
        )

        assertEquals(2, parts.size)
        val prompt = (parts[1] as UIMessagePart.Text).text
        assertTrue(prompt.contains("<UploadFile name=\"report.txt\" path=\"/upload/report.txt\">"))
        assertTrue(prompt.contains("full-body-report"))
        assertTrue(prompt.contains("```"))
    }

    @Test
    fun `full body mode injects content even when workspace is ready`() {
        val documents = listOf(document("a.txt"), document("b.txt"))
        val parts = documents.toMutableList<UIMessagePart>()

        appendDocumentPromptsInOrder(
            parts = parts,
            readContent = { "body-${it.fileName}" },
            resolvePath = { "/upload/${it.fileName}" },
            mode = UploadInjectMode.FULL_BODY,
            workspaceReady = true,
        )

        val prompts = parts.drop(documents.size).map { (it as UIMessagePart.Text).text }
        assertEquals(2, prompts.size)
        assertTrue(prompts[0].contains("body-a.txt"))
        assertTrue(prompts[1].contains("body-b.txt"))
        assertTrue(prompts[0].contains("path=\"/upload/a.txt\""))
        assertTrue(prompts[1].contains("path=\"/upload/b.txt\""))
    }

    private fun document(fileName: String): UIMessagePart.Document {
        return UIMessagePart.Document(
            url = "file:///upload/$fileName",
            fileName = fileName,
            mime = "text/plain",
        )
    }
}
