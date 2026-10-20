package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EditingTest {

    @Test
    fun formatRangeSplitsAtBoundaries() {
        val runs = listOf(
            Run("AAA"),
            Run("BBB"),
            Run("CCC"),
        )
        val formatted = runs.formatRange(2..7) { it.copy(bold = true) }
        val expected = listOf(
            Run("AA", bold = false),
            Run("A", bold = true),
            Run("BBB", bold = true),
            Run("C", bold = true),
            Run("CC", bold = false),
        )
        assertEquals(expected, formatted)
    }

    @Test
    fun formatRangeWholeRuns() {
        val runs = listOf(Run("Hello "), Run("world"))
        val formatted = runs.formatRange(0..runs.textLength()) { it.copy(italic = true) }
        val expected = listOf(Run("Hello ", italic = true), Run("world", italic = true))
        assertEquals(expected, formatted)
    }

    @Test
    fun formatRangeEmptyRange() {
        val runs = listOf(Run("Hello"))
        val formatted = runs.formatRange(IntRange(3, 2)) { it.copy(bold = true) }
        assertEquals(runs, formatted)
    }

    @Test
    fun formatRangeEmptyList() {
        val runs = emptyList<Run>()
        val formatted = runs.formatRange(0..5) { it.copy(bold = true) }
        assertEquals(emptyList(), formatted)
    }

    @Test
    fun replaceAllBasic() {
        val runs = listOf(Run("Hello world"))
        val (result, count) = runs.replaceAll("world", "earth")
        assertEquals(1, count)
        assertEquals(listOf(Run("Hello earth")), result)
    }

    @Test
    fun replaceAllAcrossRuns() {
        val runs = listOf(
            Run("Hel", bold = true),
            Run("lo wor", italic = true),
            Run("ld", underline = true),
        )
        val (result, count) = runs.replaceAll("lo wor", "X")
        assertEquals(1, count)
        val expected = listOf(
            Run("Hel", bold = true),
            Run("X", italic = true),
            Run("ld", underline = true),
        )
        assertEquals(expected, result)
    }

    @Test
    fun replaceAllCaseInsensitive() {
        val runs = listOf(Run("Hello WORLD"))
        val (result, count) = runs.replaceAll("world", "earth", ignoreCase = true)
        assertEquals(1, count)
        assertEquals(listOf(Run("Hello earth")), result)
    }

    @Test
    fun replaceAllNoMatch() {
        val runs = listOf(Run("Hello world"))
        val (result, count) = runs.replaceAll("missing", "replacement")
        assertEquals(0, count)
        assertEquals(runs, result)
    }

    @Test
    fun runStrikeDefault() {
        assertFalse(Run("x").strike)
    }

    @Test
    fun findMatchesBasic() {
        val doc = Document(blocks = listOf(Paragraph(runs = listOf(Run("Hello world")))))
        val matches = doc.findMatches("world")
        assertEquals(1, matches.size)
        assertEquals(FocusPath(0), matches[0].path)
        assertEquals(6, matches[0].start)
        assertEquals(11, matches[0].end)
    }

    @Test
    fun findMatchesAcrossCells() {
        val doc = Document(
            blocks = listOf(
                Table(
                    rows = listOf(
                        TableRow(cells = listOf(TableCell(listOf(Paragraph(runs = listOf(Run("abc"))))))),
                    ),
                    columnCount = 1,
                ),
            ),
        )
        val matches = doc.findMatches("abc")
        assertEquals(1, matches.size)
        assertEquals(FocusPath(0, 0, 0, 0), matches[0].path)
        assertEquals(0, matches[0].start)
        assertEquals(3, matches[0].end)
    }

    @Test
    fun documentReplaceAll() {
        val doc = Document(blocks = listOf(Paragraph(runs = listOf(Run("Hello world")))))
        val (newDoc, count) = doc.replaceAll("world", "earth")
        assertEquals(1, count)
        val p = newDoc.blocks[0] as Paragraph
        assertEquals("Hello earth", p.runs.single().text)
    }
}
