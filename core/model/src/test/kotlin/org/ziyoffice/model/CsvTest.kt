package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvTest {

    private fun Sheet.cellAt(row: Int, column: Int): Cell? =
        cells.firstOrNull { it.row == row && it.column == column }

    @Test
    fun parsesPlainRows() {
        val sheet = parseCsv("Item,Cost\r\nPaper,12.5\r\n", "Budget")

        assertEquals("Budget", sheet.name)
        assertEquals("Item", sheet.cellAt(0, 0)?.value)
        assertEquals("Cost", sheet.cellAt(0, 1)?.value)
        assertEquals("Paper", sheet.cellAt(1, 0)?.value)
        assertEquals("12.5", sheet.cellAt(1, 1)?.value)
        assertEquals(null, sheet.cellAt(2, 0))
    }

    @Test
    fun parsesQuotedFieldsWithCommasQuotesAndNewlines() {
        val sheet = parseCsv("\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\"", "Quoted")

        assertEquals("a,b", sheet.cellAt(0, 0)?.value)
        assertEquals("say \"hi\"", sheet.cellAt(0, 1)?.value)
        assertEquals("line1\nline2", sheet.cellAt(0, 2)?.value)
    }

    @Test
    fun parsesLoneCrAndLf() {
        val sheet = parseCsv("a\rb\nc", "Mixed")

        assertEquals("a", sheet.cellAt(0, 0)?.value)
        assertEquals("b", sheet.cellAt(1, 0)?.value)
        assertEquals("c", sheet.cellAt(2, 0)?.value)
    }

    @Test
    fun emptyFieldsBecomeAbsentCells() {
        val sheet = parseCsv("a,,c", "Gaps")

        assertEquals("a", sheet.cellAt(0, 0)?.value)
        assertEquals(null, sheet.cellAt(0, 1))
        assertEquals("c", sheet.cellAt(0, 2)?.value)
    }

    @Test
    fun quotesFieldsOnlyWhenNeeded() {
        val sheet = Sheet(
            name = "Out",
            cells = listOf(
                Cell(0, 0, "plain"),
                Cell(0, 1, "has,comma"),
                Cell(0, 2, "has\"quote"),
                Cell(0, 3, "has\nnewline"),
            ),
        )

        assertEquals(
            "plain,\"has,comma\",\"has\"\"quote\",\"has\nnewline\"",
            writeCsv(sheet),
        )
    }

    @Test
    fun writesFormulaResultsNotFormulaText() {
        val sheet = Sheet(
            name = "Calc",
            cells = listOf(
                Cell(0, 0, "2"),
                Cell(0, 1, "3"),
                Cell(1, 0, "=A1+B1"),
            ),
        )

        assertEquals("2,3\r\n5", writeCsv(sheet))
    }

    @Test
    fun roundTripsThroughCsv() {
        val original = Sheet(
            name = "Round",
            cells = listOf(
                Cell(0, 0, "Item"),
                Cell(0, 1, "Note"),
                Cell(1, 0, "Ink & toner"),
                Cell(1, 1, "a,b \"c\""),
            ),
        )

        val reread = parseCsv(writeCsv(original), original.name)

        assertEquals(original.cellAt(1, 0)?.value, reread.cellAt(1, 0)?.value)
        assertEquals(original.cellAt(1, 1)?.value, reread.cellAt(1, 1)?.value)
    }

    @Test
    fun emptySheetWritesEmptyText() {
        assertEquals("", writeCsv(Sheet(name = "Blank", cells = emptyList())))
    }
}
