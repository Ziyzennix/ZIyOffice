package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What comes off the clipboard and what goes onto it.
 */
class ClipboardTest {

    // ---------------------------------------------------------------- reading

    @Test
    fun plainTextBecomesOneParagraphPerLine() {
        val paragraphs = textToParagraphs("first\nsecond\nthird")
        assertEquals(3, paragraphs.size)
        assertEquals("first", paragraphs[0].toClipboardText())
        assertEquals("third", paragraphs[2].toClipboardText())
    }

    @Test
    fun anEmptyLineIsStillAParagraph() {
        val paragraphs = textToParagraphs("one\n\ntwo")
        assertEquals(3, paragraphs.size)
        assertEquals("", paragraphs[1].toClipboardText())
    }

    @Test
    fun theFormattingOfTheHtmlIsKept() {
        val runs = htmlToRuns("plain <b>bold</b> <i>italic</i> <u>under</u> <s>struck</s>")
        assertEquals(8, runs.size)
        assertEquals("plain ", runs[0].text)
        assertTrue(runs[1].bold)
        assertTrue(runs[3].italic)
        assertTrue(runs[5].underline)
        assertTrue(runs[7].strike)
    }

    @Test
    fun aStrongTagIsBoldAsWell() {
        val runs = htmlToRuns("<strong>bold</strong>")
        assertTrue(runs.single().bold)
    }

    @Test
    fun theFormattingStopsWhereTheTagDoes() {
        val runs = htmlToRuns("<b>bold</b> plain")
        assertTrue(runs[0].bold)
        assertEquals(false, runs[1].bold)
    }

    @Test
    fun nestedTagsBothApply() {
        val runs = htmlToRuns("<b><i>both</i></b>")
        assertTrue(runs.single().bold)
        assertTrue(runs.single().italic)
    }

    @Test
    fun aTagInsideAnotherOneKeepsBoth() {
        val runs = htmlToRuns("<b>bold <i>and italic</i></b>")
        assertTrue(runs[0].bold)
        assertEquals(false, runs[0].italic)
        assertTrue(runs[1].bold)
        assertTrue(runs[1].italic)
    }

    @Test
    fun aLineBreakMakesANewLine() {
        val paragraphs = htmlToParagraphs("one<br>two")
        assertEquals(2, paragraphs.size)
        assertEquals("one", paragraphs[0].toClipboardText())
        assertEquals("two", paragraphs[1].toClipboardText())
    }

    @Test
    fun aBlockMakesANewParagraph() {
        val paragraphs = htmlToParagraphs("<p>one</p><p>two</p>")
        assertEquals(2, paragraphs.size)
        assertEquals("one", paragraphs[0].toClipboardText())
        assertEquals("two", paragraphs[1].toClipboardText())
    }

    @Test
    fun aTagThatIsNotKnownKeepsItsText() {
        val runs = htmlToRuns("a <marquee>wobble</marquee> b")
        assertEquals("a wobble b", runs.joinToString("") { it.text })
    }

    @Test
    fun theEntitiesAreDecoded() {
        val runs = htmlToRuns("a &amp; b &lt; c &gt;&nbsp;d")
        assertEquals("a & b < c > d", runs.joinToString("") { it.text })
    }

    @Test
    fun aNumberedEntityIsDecoded() {
        val runs = htmlToRuns("&#65;&#x42;")
        assertEquals("AB", runs.joinToString("") { it.text })
    }

    @Test
    fun anEntityThatIsNotKnownIsLeftAlone() {
        val runs = htmlToRuns("a &nope; b")
        assertTrue(runs.joinToString("") { it.text }.contains("&nope;"))
    }

    @Test
    fun aStyleAsksForItsSize() {
        val runs = htmlToRuns("<span style=\"font-size:14pt\">big</span>")
        assertEquals(14f, runs.single().sizePt)
    }

    @Test
    fun aStyleAsksForItsColour() {
        val runs = htmlToRuns("<span style=\"color:#ff0000\">red</span>")
        assertEquals(0xFF0000.toInt(), runs.single().colorRgb)
    }

    @Test
    fun aShortColourIsSpreadOut() {
        val runs = htmlToRuns("<span style=\"color:#f00\">red</span>")
        assertEquals(0xFF0000.toInt(), runs.single().colorRgb)
    }

    @Test
    fun aStyleAsksForItsWeight() {
        val runs = htmlToRuns("<span style=\"font-weight:bold\">heavy</span>")
        assertTrue(runs.single().bold)
    }

    @Test
    fun theHeadOfAClipboardFragmentIsIgnored() {
        val runs = htmlToRuns("<meta charset='utf-8'><b>bold</b>")
        assertEquals("bold", runs.joinToString("") { it.text })
        assertTrue(runs.single().bold)
    }

    @Test
    fun aTagThatIsNeverClosedIsText() {
        val runs = htmlToRuns("a < b")
        assertEquals("a < b", runs.joinToString("") { it.text })
    }

    // ---------------------------------------------------------------- writing

    @Test
    fun aParagraphGoesOutAsText() {
        val paragraph = Paragraph(runs = listOf(Run("hello", bold = true), Run(" world")))
        assertEquals("hello world", paragraph.toClipboardText())
    }

    @Test
    fun aParagraphGoesOutAsHtml() {
        val paragraph = Paragraph(runs = listOf(Run("hello", bold = true), Run(" world")))
        assertEquals("<p><b>hello</b> world</p>", paragraph.toClipboardHtml())
    }

    @Test
    fun theCharactersThatMeanSomethingAreEscaped() {
        val paragraph = Paragraph(runs = listOf(Run("a & b < c")))
        assertEquals("<p>a &amp; b &lt; c</p>", paragraph.toClipboardHtml())
    }

    @Test
    fun aDocumentGoesOutOneLinePerParagraph() {
        val document = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("one"))),
                Paragraph(runs = listOf(Run("two"))),
            ),
        )
        assertEquals("one\ntwo", document.toClipboardText())
    }

    @Test
    fun aTableGoesOutWithTabsBetweenTheCells() {
        val document = Document(
            blocks = listOf(
                Table(
                    columnCount = 2,
                    rows = listOf(
                        TableRow(
                            cells = listOf(
                                TableCell(blocks = listOf(Paragraph(runs = listOf(Run("a"))))),
                                TableCell(blocks = listOf(Paragraph(runs = listOf(Run("b"))))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("a\tb", document.toClipboardText())
    }

    @Test
    fun whatGoesOutComesBackIn() {
        val original = Paragraph(
            runs = listOf(
                Run("plain "),
                Run("bold", bold = true),
                Run(" and "),
                Run("italic", italic = true),
            ),
        )
        val back = htmlToRuns(original.toClipboardHtml())
        assertEquals(4, back.size)
        assertTrue(back[1].bold)
        assertTrue(back[3].italic)
        assertEquals(original.toClipboardText(), back.joinToString("") { it.text })
    }

    // ------------------------------------------------------------ a part of it

    @Test
    fun onlyTheSelectedPartIsCopied() {
        val paragraph = Paragraph(runs = listOf(Run("hello world")))
        assertEquals("hello", paragraph.toClipboardText(0, 5))
        assertEquals("world", paragraph.toClipboardText(6, 11))
    }

    @Test
    fun aSelectionThatRunsPastTheEndStopsAtTheEnd() {
        val paragraph = Paragraph(runs = listOf(Run("hello")))
        assertEquals("hello", paragraph.toClipboardText(0, 500))
    }

    @Test
    fun aSelectionThatIsEmptyCopiesNothing() {
        val paragraph = Paragraph(runs = listOf(Run("hello")))
        assertEquals("", paragraph.toClipboardText(2, 2))
    }

    @Test
    fun theFormattingOfTheCopiedPartIsKept() {
        val paragraph = Paragraph(
            runs = listOf(Run("plain "), Run("bold", bold = true), Run(" plain")),
        )
        val html = paragraph.toClipboardHtml(6, 10)
        assertEquals("<p><b>bold</b></p>", html)
    }

    // ---------------------------------------------------------------- pasting

    @Test
    fun aPastedParagraphTakesThePlaceOfTheOneItLandsOn() {
        val document = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("first"))),
                Paragraph(runs = listOf(Run("second"))),
            ),
        )
        val pasted = document.withPastedParagraphs(
            FocusPath(0),
            listOf(Paragraph(runs = listOf(Run("new")))),
        )
        assertEquals(2, pasted.blocks.size)
        assertEquals("new", (pasted.blocks[0] as Paragraph).runs.single().text)
    }

    @Test
    fun theRestOfAPasteGoesAfterTheFirst() {
        val document = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("first"))),
                Paragraph(runs = listOf(Run("second"))),
            ),
        )
        val pasted = document.withPastedParagraphs(
            FocusPath(0),
            listOf(
                Paragraph(runs = listOf(Run("one"))),
                Paragraph(runs = listOf(Run("two"))),
                Paragraph(runs = listOf(Run("three"))),
            ),
        )
        assertEquals(4, pasted.blocks.size)
        assertEquals("one", (pasted.blocks[0] as Paragraph).runs.single().text)
        assertEquals("two", (pasted.blocks[1] as Paragraph).runs.single().text)
        assertEquals("three", (pasted.blocks[2] as Paragraph).runs.single().text)
        assertEquals("second", (pasted.blocks[3] as Paragraph).runs.single().text)
    }

    @Test
    fun aPasteThatIsOnlyBlankChangesNothing() {
        val document = Document(blocks = listOf(Paragraph(runs = listOf(Run("first")))))
        val pasted = document.withPastedParagraphs(
            FocusPath(0),
            listOf(Paragraph(runs = listOf(Run("")))),
        )
        assertEquals("first", (pasted.blocks[0] as Paragraph).runs.single().text)
    }

    @Test
    fun aPasteIntoATableCellStaysInTheCell() {
        val document = Document(
            blocks = listOf(
                Table(
                    columnCount = 1,
                    rows = listOf(
                        TableRow(
                            cells = listOf(
                                TableCell(blocks = listOf(Paragraph(runs = listOf(Run("old"))))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val pasted = document.withPastedParagraphs(
            FocusPath(0, 0, 0, 0),
            listOf(
                Paragraph(runs = listOf(Run("new"))),
                Paragraph(runs = listOf(Run("also new"))),
            ),
        )
        val table = pasted.blocks[0] as Table
        val cell = table.rows[0].cells[0]
        assertEquals(2, cell.blocks.size)
        assertEquals("new", (cell.blocks[0] as Paragraph).runs.single().text)
        assertEquals("also new", (cell.blocks[1] as Paragraph).runs.single().text)
    }
}
