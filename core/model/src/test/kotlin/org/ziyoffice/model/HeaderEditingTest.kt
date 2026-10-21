package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Headers and footers are edited through the same path shape as body text, so these
 * tests pin that the region field routes an edit to the right block list.
 */
class HeaderEditingTest {

    private fun doc() = Document(
        blocks = listOf(Paragraph(runs = listOf(Run("body one"))), Paragraph(runs = listOf(Run("body two")))),
        header = listOf(Paragraph(runs = listOf(Run("header text")))),
        footer = listOf(Paragraph(runs = listOf(Run("footer text")))),
    )

    private fun Paragraph.text() = runs.joinToString("") { it.text }

    @Test
    fun editingAHeaderParagraphLeavesTheBodyAlone() {
        val path = FocusPath(0, region = DocumentRegion.HEADER)
        val edited = doc().mapParagraphAt(path) { it.copy(runs = listOf(Run("new header"))) }

        assertEquals("new header", (edited.header?.single() as Paragraph).text())
        assertEquals("body one", (edited.blocks[0] as Paragraph).text())
        assertEquals("footer text", (edited.footer?.single() as Paragraph).text())
    }

    @Test
    fun editingABodyParagraphLeavesTheHeaderAlone() {
        val edited = doc().mapParagraphAt(FocusPath(0)) {
            it.copy(runs = listOf(Run("new body")))
        }
        assertEquals("new body", (edited.blocks[0] as Paragraph).text())
        assertEquals("header text", (edited.header?.single() as Paragraph).text())
    }

    @Test
    fun splittingAHeaderParagraphAddsToTheHeaderOnly() {
        val path = FocusPath(0, region = DocumentRegion.HEADER)
        val source = doc()
        val paragraph = source.paragraphAt(path)!!
        val split = source
            .mapParagraphAt(path) { it.copy(runs = listOf(Run("head"))) }
            .withParagraphAddedAfter(path, paragraph.copy(runs = listOf(Run("er"))))

        assertEquals(2, split.header?.size)
        assertEquals(2, split.blocks.size)
        assertEquals("head", (split.header!![0] as Paragraph).text())
        assertEquals("er", (split.header!![1] as Paragraph).text())
    }

    @Test
    fun deletingTheOnlyHeaderParagraphLeavesAnEmptyOne() {
        val path = FocusPath(0, region = DocumentRegion.HEADER)
        val after = doc().withParagraphRemovedAt(path)
        // A header part with no paragraph is invalid OOXML, so one stays behind.
        assertEquals(1, after.header?.size)
        assertEquals("", (after.header?.single() as Paragraph).text())
    }

    @Test
    fun aPathIntoAMissingRegionDoesNothing() {
        val withoutHeader = Document(blocks = listOf(Paragraph(runs = listOf(Run("body")))))
        assertNull(withoutHeader.paragraphAt(FocusPath(0, region = DocumentRegion.HEADER)))
        assertEquals(withoutHeader, withoutHeader.mapParagraphAt(FocusPath(0, region = DocumentRegion.HEADER)) { it })
        assertEquals(withoutHeader, withoutHeader.withParagraphRemovedAt(FocusPath(0, region = DocumentRegion.FOOTER)))
    }

    @Test
    fun searchFindsTextInHeadersAndFooters() {
        val matches = doc().findMatches("header text")
        assertEquals(listOf(FocusPath(0, region = DocumentRegion.HEADER)), matches.map { it.path })
    }
}
