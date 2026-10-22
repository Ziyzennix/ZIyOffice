package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Text that came out of a picture, turned into a document. */
class DocumentFromTextTest {

    @Test
    fun oneParagraphPerLine() {
        val document = documentFromText("The first line\nThe second line")
        assertEquals(2, document.blocks.size)
        assertEquals("The first line", (document.blocks[0] as Paragraph).runs.single().text)
    }

    @Test
    fun aBlankLineIsAParagraphBreak() {
        val document = documentFromText("A\n\nB")
        assertEquals(3, document.blocks.size)
        assertTrue((document.blocks[1] as Paragraph).runs.isEmpty())
    }

    @Test
    fun aRunOfBlankLinesIsOneBreak() {
        val document = documentFromText("A\n\n\n\nB")
        assertEquals(3, document.blocks.size)
    }

    @Test
    fun aPageThatEndsInBlankLinesDoesNotEndInAnEmptyParagraph() {
        val document = documentFromText("A\n\n\n")
        assertEquals(1, document.blocks.size)
    }

    @Test
    fun emptyTextIsOneEmptyParagraph() {
        val document = documentFromText("")
        assertEquals(1, document.blocks.size)
        assertTrue((document.blocks.single() as Paragraph).runs.isEmpty())
    }

    @Test
    fun aCarriageReturnIsALineEndingToo() {
        val document = documentFromText("A\r\nB")
        assertEquals(2, document.blocks.size)
    }

    @Test
    fun leadingBlankLinesAreSkipped() {
        val document = documentFromText("\n\n\nFirst line\nSecond line")
        assertEquals(2, document.blocks.size)
        assertEquals("First line", (document.blocks[0] as Paragraph).runs.single().text)
        assertEquals("Second line", (document.blocks[1] as Paragraph).runs.single().text)
    }

    @Test
    fun whitespaceOnlyLinesTreatedAsBlank() {
        val document = documentFromText("Paragraph one\n   \n\t\nParagraph two")
        assertEquals(3, document.blocks.size)
        assertEquals("Paragraph one", (document.blocks[0] as Paragraph).runs.single().text)
        assertTrue((document.blocks[1] as Paragraph).runs.isEmpty())
        assertEquals("Paragraph two", (document.blocks[2] as Paragraph).runs.single().text)
    }

    @Test
    fun trailingWhitespaceOnLinesIsTrimmed() {
        val document = documentFromText("Line with spaces   \nLine with tabs\t\t")
        assertEquals(2, document.blocks.size)
        assertEquals("Line with spaces", (document.blocks[0] as Paragraph).runs.single().text)
        assertEquals("Line with tabs", (document.blocks[1] as Paragraph).runs.single().text)
    }

    @Test
    fun multiParagraphDocumentStructure() {
        val rawScan = """
            Title of Scanned Document

            This is the first paragraph with multiple sentences.
            It continues on the second line without a blank line break.

            This is the second distinct paragraph after a blank line.
        """.trimIndent()
        val document = documentFromText(rawScan)
        assertEquals(6, document.blocks.size)
        assertEquals("Title of Scanned Document", (document.blocks[0] as Paragraph).runs.single().text)
        assertTrue((document.blocks[1] as Paragraph).runs.isEmpty())
        assertEquals("This is the first paragraph with multiple sentences.", (document.blocks[2] as Paragraph).runs.single().text)
        assertEquals("It continues on the second line without a blank line break.", (document.blocks[3] as Paragraph).runs.single().text)
        assertTrue((document.blocks[4] as Paragraph).runs.isEmpty())
        assertEquals("This is the second distinct paragraph after a blank line.", (document.blocks[5] as Paragraph).runs.single().text)
    }
}
