package org.ziyoffice.ooxml

import kotlin.test.Test
import org.ziyoffice.model.withLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a new paragraph ends up when a slide gains one.
 *
 * [PptxWriter.rewriteSlideText] splices the edited paragraphs back into the
 * slide XML and appends any paragraph the user added. The position of that
 * appended paragraph was computed from an offset in the *original* XML while
 * being inserted into the *rebuilt* string, so it drifted as soon as the
 * edits changed the length of the text. The extra paragraph then landed in
 * the middle of the slide, sometimes inside a shape and sometimes outside
 * the tree.
 */
class PptxTextRewriteTest {

    private val writer = PptxWriter()

    private val slideWithTwoParagraphs =
        "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">" +
            "<p:cSld><p:spTree>" +
            "<p:sp><p:txBody><a:p><a:r><a:t>First</a:t></a:r></a:p></p:txBody></p:sp>" +
            "<p:sp><p:txBody><a:p><a:r><a:t>Second</a:t></a:r></a:p></p:txBody></p:sp>" +
            "</p:spTree></p:cSld></p:sld>"

    @Test
    fun anAddedParagraphGoesAfterTheLastParagraph() {
        val result = writer.rewriteSlideText(slideWithTwoParagraphs, listOf("One", "Two", "Three"))

        val third = result.indexOf("Three")
        val second = result.indexOf("Two")
        val treeEnd = result.indexOf("</p:spTree>")

        assertTrue(third > second, "the new paragraph must come after the last one")
        assertTrue(third < treeEnd, "the new paragraph must stay inside the shape tree")
    }

    @Test
    fun anAddedParagraphGoesAfterTheLastParagraphWhenTheEditsChangeTheLength() {
        // The edits make the text much longer, so an offset taken from the
        // original XML no longer points at the end of the rebuilt string.
        val result = writer.rewriteSlideText(
            slideWithTwoParagraphs,
            listOf("A considerably longer first line", "And a much longer second line", "Third"),
        )

        val third = result.indexOf("Third")
        val second = result.indexOf("And a much longer second line")
        val treeEnd = result.indexOf("</p:spTree>")

        assertTrue(third > second, "the new paragraph must come after the last one")
        assertTrue(third < treeEnd, "the new paragraph must stay inside the shape tree")
    }

    @Test
    fun anAddedParagraphGoesAfterTheLastParagraphWhenTheEditsShortenTheText() {
        val result = writer.rewriteSlideText(
            slideWithTwoParagraphs,
            listOf("A", "B", "C"),
        )

        val third = result.indexOf("C")
        val treeEnd = result.indexOf("</p:spTree>")

        assertTrue(third > result.indexOf("B"))
        assertTrue(third < treeEnd)
    }

    @Test
    fun theExistingParagraphsKeepTheirOrder() {
        val result = writer.rewriteSlideText(slideWithTwoParagraphs, listOf("One", "Two", "Three"))

        assertTrue(result.indexOf("One") < result.indexOf("Two"))
        assertTrue(result.indexOf("Two") < result.indexOf("Three"))
        assertEquals(3, Regex("<a:p>").findAll(result).count())
    }

    @Test
    fun aSlideThatGainsAParagraphReadsBackWithIt() {
        val original = writer.create(
            org.ziyoffice.model.SlideDeck.blank(1).withLines(0, listOf("Title", "Body")),
        )
        val edited = writer.edit(
            original,
            org.ziyoffice.model.SlideDeck.blank(1).withLines(0, listOf("Title", "Body", "Extra")),
        )
        val read = PptxReader().read(java.io.ByteArrayInputStream(edited))

        assertEquals(listOf("Title", "Body", "Extra"), read.slides.single().lines)
    }
}
