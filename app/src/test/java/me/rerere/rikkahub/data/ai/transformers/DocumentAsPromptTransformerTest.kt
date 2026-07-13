package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
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

        assertEquals(originalParts.size + documents.size, parts.size)
        originalParts.indices.forEach { index ->
            assertSame(originalParts[index], parts[index])
        }

        val prompts = parts.drop(originalParts.size).map { (it as UIMessagePart.Text).text }
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

        assertSame(document, singleDocument[0])
        val prompt = (singleDocument[1] as UIMessagePart.Text).text
        assertTrue(prompt.contains("<UploadFile name=\"only.txt\">"))
        assertTrue(prompt.contains("only body"))
    }

    private fun document(fileName: String): UIMessagePart.Document {
        return UIMessagePart.Document(
            url = "file:///upload/$fileName",
            fileName = fileName,
            mime = "text/plain",
        )
    }
}
