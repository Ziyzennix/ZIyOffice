package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bugs the code review turned up, pinned down so they stay fixed.
 */
class ModelFixesTest {

    // ------------------------------------------------------------ number format

    @Test
    fun aCurrencyPrefixWithAnEIsStillANumberFormat() {
        // "EUR " has an E in it, but it is text the user typed, not scientific
        // notation.
        val formatted = formatCellValue(CellValue.Number(12.5), "\"EUR \"0.00")
        assertEquals("EUR 12.50", formatted)
    }

    @Test
    fun aCurrencyPrefixWithASlashIsStillANumberFormat() {
        val formatted = formatCellValue(CellValue.Number(3.0), "\"/wk\"0.0")
        assertEquals("/wk3.0", formatted)
    }

    @Test
    fun scientificNotationStillFallsBackToGeneral() {
        assertEquals("1200", formatCellValue(CellValue.Number(1200.0), "0.00E+00"))
    }

    @Test
    fun aFractionStillFallsBackToGeneral() {
        assertEquals("0.5", formatCellValue(CellValue.Number(0.5), "0/1"))
    }

    // ---------------------------------------------------------------- sorting

    @Test
    fun aRowHeightTravelsWithItsRow() {
        val sheet = Sheet(
            name = "S",
            cells = listOf(
                Cell(row = 0, column = 0, value = "b"),
                Cell(row = 1, column = 0, value = "a"),
            ),
            rowCount = 2,
            columnCount = 1,
            rowHeights = mapOf(0 to 40f, 1 to 10f),
        )

        val sorted = SheetSort.sort(sheet, 0, 1, SheetSort.Key(column = 0, ascending = true))

        // Row 1 ("a") is now row 0, so it keeps the 10 pt height it had.
        assertEquals(10f, sorted.rowHeights[0])
        assertEquals(40f, sorted.rowHeights[1])
        assertEquals("a", sorted.cells.first { it.row == 0 }.value)
    }

    @Test
    fun aHeightOutsideTheSortedRangeStaysPut() {
        val sheet = Sheet(
            name = "S",
            cells = listOf(Cell(row = 1, column = 0, value = "a")),
            rowCount = 3,
            columnCount = 1,
            rowHeights = mapOf(2 to 33f),
        )

        val sorted = SheetSort.sort(sheet, 0, 1, SheetSort.Key(column = 0))

        assertEquals(33f, sorted.rowHeights[2])
    }

    // ---------------------------------------------------------- formula limits

    @Test
    fun aColumnPastXfdIsRejected() {
        val result = evaluateFormula(
            Sheet(name = "S", cells = emptyList(), rowCount = 100, columnCount = 26),
            "=AAAA1",
        )
        assertTrue(
            result.value is CellValue.Error,
            "a column past XFD should not evaluate: ${result.value}",
        )
    }

    @Test
    fun aRowPastTheLastOneIsRejected() {
        val result = evaluateFormula(
            Sheet(name = "S", cells = emptyList(), rowCount = 100, columnCount = 26),
            "=A99999999",
        )
        assertTrue(
            result.value is CellValue.Error,
            "a row past the last one should not evaluate: ${result.value}",
        )
    }

    @Test
    fun anOrdinaryReferenceStillWorks() {
        val sheet = Sheet(
            name = "S",
            cells = listOf(Cell(row = 0, column = 0, value = "7")),
            rowCount = 100,
            columnCount = 26,
        )
        val result = evaluateFormula(sheet, "=A1*2")
        assertEquals(CellValue.Number(14.0), result.value)
    }
}
