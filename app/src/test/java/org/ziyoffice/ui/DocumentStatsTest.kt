package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.model.TableCell
import org.ziyoffice.model.TableRow

class DocumentStatsTest {

    @Test
    fun emptyDocumentHasZeroWordsAndCharacters() {
        val stats = computeDocumentStats(emptyList())
        assertEquals(0, stats.words)
        assertEquals(0, stats.charactersNoSpaces)
        assertEquals(0, stats.charactersWithSpaces)
        assertEquals(0, stats.paragraphs)
        assertEquals(0, stats.nonBlankParagraphs)
    }

    @Test
    fun calculatesSingleParagraphAccurately() {
        // "Hello World" -> 2 words, 10 non-space chars, 11 total chars
        val blocks = listOf(Paragraph(runs = listOf(Run("Hello World"))))
        val stats = computeDocumentStats(blocks)
        assertEquals(2, stats.words)
        assertEquals(10, stats.charactersNoSpaces)
        assertEquals(11, stats.charactersWithSpaces)
        assertEquals(1, stats.paragraphs)
        assertEquals(1, stats.nonBlankParagraphs)
    }

    @Test
    fun countsEmptyAndWhitespaceParagraphs() {
        val blocks = listOf(
            Paragraph(runs = listOf(Run("First"))),
            Paragraph(runs = listOf(Run(""))),
            Paragraph(runs = listOf(Run("   "))),
            Paragraph(runs = listOf(Run("Second"))),
        )
        val stats = computeDocumentStats(blocks)
        assertEquals(2, stats.words)
        assertEquals(11, stats.charactersNoSpaces)
        assertEquals(4, stats.paragraphs)
    }

    @Test
    fun includesTablesInStatistics() {
        val table = Table(
            rows = listOf(
                TableRow(
                    cells = listOf(
                        TableCell(blocks = listOf(Paragraph(runs = listOf(Run("Cell A1"))))),
                        TableCell(blocks = listOf(Paragraph(runs = listOf(Run("Cell B1"))))),
                    ),
                ),
            ),
            columnCount = 2,
        )
        val stats = computeDocumentStats(listOf(table))
        assertEquals(4, stats.words)
        assertEquals(12, stats.charactersNoSpaces)
        assertEquals(14, stats.charactersWithSpaces)
        assertEquals(2, stats.paragraphs)
        assertEquals(2, stats.nonBlankParagraphs)
    }
}
