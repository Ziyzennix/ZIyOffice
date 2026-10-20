package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetStructureTest {

    private fun cell(reference: String, value: String, cached: CellValue? = null): Cell {
        val column = columnIndex(reference.takeWhile { it.isLetter() })
        val row = reference.dropWhile { it.isLetter() }.toInt() - 1
        return Cell(row, column, value, cached = cached)
    }

    private fun Sheet.valueAt(reference: String): String {
        val column = columnIndex(reference.takeWhile { it.isLetter() })
        val row = reference.dropWhile { it.isLetter() }.toInt() - 1
        return cells.single { it.row == row && it.column == column }.value
    }

    private fun Sheet.cellAt(reference: String): Cell {
        val column = columnIndex(reference.takeWhile { it.isLetter() })
        val row = reference.dropWhile { it.isLetter() }.toInt() - 1
        return cells.single { it.row == row && it.column == column }
    }

    @Test
    fun `inserting rows moves cells, merges, heights and the freeze line`() {
        val sheet = Sheet(
            name = "Rows",
            cells = listOf(cell("A1", "top"), cell("B2", "middle"), cell("A3", "below")),
            rowCount = 5,
            columnCount = 4,
            frozenRows = 3,
            mergedRanges = listOf(CellRange(1, 0, 3, 1)),
            rowHeights = mapOf(0 to 20f, 2 to 30f, 4 to 40f),
        )

        val inserted = sheet.insertRows(at = 1, count = 2)

        assertEquals(7, inserted.rowCount)
        assertEquals("top", inserted.valueAt("A1"))
        assertEquals("middle", inserted.valueAt("B4"))
        assertEquals("below", inserted.valueAt("A5"))
        assertEquals(listOf(CellRange(3, 0, 5, 1)), inserted.mergedRanges)
        assertEquals(mapOf(0 to 20f, 4 to 30f, 6 to 40f), inserted.rowHeights)
        assertEquals(5, inserted.frozenRows)
    }

    @Test
    fun `inserting rows rewrites references and clears cached formula results`() {
        val sheet = Sheet(
            name = "Formulas",
            cells = listOf(
                cell(
                    "D10",
                    "=A1+A3+\$A\$5+SUM(B2:B4)+\"A3\"+LOG10(A3)",
                    CellValue.Number(99.0),
                ),
                cell("B8", "=SUM(B10:B12)"),
            ),
            rowCount = 20,
            columnCount = 8,
        )

        val inserted = sheet.insertRows(at = 2, count = 2)

        val moved = inserted.cellAt("D12")
        assertEquals(
            "=A1+A5+\$A\$7+SUM(B2:B6)+\"A3\"+LOG10(A5)",
            moved.value,
        )
        assertNull(moved.cached)
        assertEquals("=SUM(B12:B14)", inserted.valueAt("B10"))
    }

    @Test
    fun `inserting and deleting a row restores a range formula and its values`() {
        val sheet = Sheet(
            name = "Total",
            cells = listOf(
                cell("A1", "1"),
                cell("A2", "2"),
                cell("A3", "3"),
                cell("B1", "=SUM(A1:A3)"),
            ),
            rowCount = 10,
            columnCount = 3,
        )

        val inserted = sheet.insertRows(at = 1, count = 1)
        assertEquals("=SUM(A1:A4)", inserted.valueAt("B1"))
        assertEquals(6.0, (evaluateFormula(inserted, "=SUM(A1:A4)").value as CellValue.Number).number, 0.0)

        val deleted = inserted.deleteRows(at = 1, count = 1)
        assertEquals("=SUM(A1:A3)", deleted.valueAt("B1"))
        assertEquals(6.0, (evaluateFormula(deleted, "=SUM(A1:A3)").value as CellValue.Number).number, 0.0)
    }

    @Test
    fun `a merge spanning an insertion grows with it`() {
        val sheet = Sheet(
            name = "Merged",
            cells = emptyList(),
            rowCount = 8,
            columnCount = 4,
            mergedRanges = listOf(CellRange(1, 0, 3, 1)),
        )

        val inserted = sheet.insertRows(at = 2, count = 2)

        assertEquals(listOf(CellRange(1, 0, 5, 1)), inserted.mergedRanges)
    }

    @Test
    fun `deleting rows shifts cells and shrinks or removes merges`() {
        val sheet = Sheet(
            name = "Rows",
            cells = listOf(
                cell("A1", "top"),
                cell("A3", "deleted"),
                cell("A5", "moved"),
            ),
            rowCount = 8,
            columnCount = 4,
            frozenRows = 5,
            mergedRanges = listOf(
                CellRange(0, 0, 3, 1),
                CellRange(4, 2, 6, 3),
            ),
            rowHeights = mapOf(1 to 24f, 4 to 30f, 6 to 50f),
        )

        val deleted = sheet.deleteRows(at = 2, count = 2)

        assertEquals(6, deleted.rowCount)
        assertEquals("top", deleted.valueAt("A1"))
        assertEquals("moved", deleted.valueAt("A3"))
        assertEquals(listOf(CellRange(0, 0, 1, 1), CellRange(2, 2, 4, 3)), deleted.mergedRanges)
        assertEquals(mapOf(1 to 24f, 2 to 30f, 4 to 50f), deleted.rowHeights)
        assertEquals(3, deleted.frozenRows)
    }

    @Test
    fun `a merge whose anchor row is deleted disappears`() {
        val sheet = Sheet(
            name = "Merged",
            cells = listOf(cell("A3", "anchor")),
            rowCount = 8,
            columnCount = 4,
            mergedRanges = listOf(CellRange(2, 0, 4, 1)),
        )

        val deleted = sheet.deleteRows(at = 2, count = 1)

        assertTrue(deleted.mergedRanges.isEmpty())
    }

    @Test
    fun `deleting rows rewrites surviving formulas and marks deleted references`() {
        val sheet = Sheet(
            name = "Formulas",
            cells = listOf(
                cell("A1", "=A10", CellValue.Number(1.0)),
                cell("B1", "=SUM(B3:B6)", CellValue.Number(2.0)),
                cell("C1", "=D3"),
                cell("D1", "=SUM(D3:D4)"),
                cell("E1", "=A1+LOG10(A10)"),
                cell("E3", "=A1"),
                cell("F5", "=A1"),
            ),
            rowCount = 20,
            columnCount = 8,
        )

        val deleted = sheet.deleteRows(at = 2, count = 2)

        assertEquals("=A8", deleted.valueAt("A1"))
        assertEquals("=SUM(B3:B4)", deleted.valueAt("B1"))
        assertEquals("=#REF!", deleted.valueAt("C1"))
        assertEquals("=SUM(#REF!)", deleted.valueAt("D1"))
        assertEquals("=A1+LOG10(A8)", deleted.valueAt("E1"))
        assertTrue(deleted.cells.none { it.row == 2 && it.column == 4 })
        assertEquals("=A1", deleted.valueAt("F3"))
        assertNull(deleted.cellAt("A1").cached)
        assertNull(deleted.cellAt("B1").cached)
    }

    @Test
    fun `inserting columns shifts cells, merges and widths`() {
        val sheet = Sheet(
            name = "Columns",
            cells = listOf(cell("A1", "left"), cell("B1", "middle"), cell("D1", "right")),
            rowCount = 4,
            columnCount = 6,
            mergedRanges = listOf(CellRange(0, 1, 1, 3)),
            columnWidths = listOf(ColumnWidth(0, 1, 20f), ColumnWidth(4, 4, 30f)),
            rowHeights = mapOf(0 to 24f),
        )

        val inserted = sheet.insertColumns(at = 1, count = 2)

        assertEquals(8, inserted.columnCount)
        assertEquals("left", inserted.valueAt("A1"))
        assertEquals("middle", inserted.valueAt("D1"))
        assertEquals("right", inserted.valueAt("F1"))
        assertEquals(listOf(CellRange(0, 3, 1, 5)), inserted.mergedRanges)
        assertEquals(20f, inserted.columnWidth(0), 0.001f)
        assertEquals(20f, inserted.columnWidth(1), 0.001f)
        assertEquals(20f, inserted.columnWidth(2), 0.001f)
        assertEquals(20f, inserted.columnWidth(3), 0.001f)
        assertEquals(30f, inserted.columnWidth(6), 0.001f)
        assertEquals(mapOf(0 to 24f), inserted.rowHeights)
    }

    @Test
    fun `inserting columns rewrites anchored references and leaves strings alone`() {
        val sheet = Sheet(
            name = "Formulas",
            cells = listOf(
                cell("A1", "=C3+\$C\$3+SUM(B2:D4)+\"C3\"", CellValue.Number(1.0)),
                cell("C1", "=A1"),
            ),
            rowCount = 8,
            columnCount = 8,
        )

        val inserted = sheet.insertColumns(at = 1, count = 2)

        assertEquals("=E3+\$E\$3+SUM(D2:F4)+\"C3\"", inserted.valueAt("A1"))
        assertEquals("=A1", inserted.valueAt("E1"))
        assertNull(inserted.cellAt("A1").cached)
    }

    @Test
    fun `deleting columns shifts cells, shrinks widths and rewrites references`() {
        val sheet = Sheet(
            name = "Columns",
            cells = listOf(
                cell("A1", "=B1+C1+D1+SUM(B2:D2)"),
                cell("D1", "right"),
                cell("E1", "far"),
            ),
            rowCount = 4,
            columnCount = 6,
            mergedRanges = listOf(CellRange(0, 0, 1, 4)),
            columnWidths = listOf(
                ColumnWidth(0, 0, 10f),
                ColumnWidth(1, 3, 20f),
                ColumnWidth(5, 5, 30f),
            ),
        )

        val deleted = sheet.deleteColumns(at = 1, count = 2)

        assertEquals(4, deleted.columnCount)
        assertEquals("=#REF!+#REF!+B1+SUM(B2:B2)", deleted.valueAt("A1"))
        assertEquals("right", deleted.valueAt("B1"))
        assertEquals("far", deleted.valueAt("C1"))
        assertEquals(listOf(CellRange(0, 0, 1, 2)), deleted.mergedRanges)
        assertEquals(10f, deleted.columnWidth(0), 0.001f)
        assertEquals(20f, deleted.columnWidth(1), 0.001f)
        assertEquals(30f, deleted.columnWidth(3), 0.001f)
    }

    @Test
    fun `inserted columns inherit an explicit width from the left`() {
        val sheet = Sheet(
            name = "Widths",
            cells = emptyList(),
            rowCount = 4,
            columnCount = 6,
            columnWidths = listOf(ColumnWidth(0, 1, 20f)),
        )

        val inserted = sheet.insertColumns(at = 2, count = 2)

        assertEquals(20f, inserted.columnWidth(0), 0.001f)
        assertEquals(20f, inserted.columnWidth(1), 0.001f)
        assertEquals(20f, inserted.columnWidth(2), 0.001f)
        assertEquals(20f, inserted.columnWidth(3), 0.001f)
    }

    @Test
    fun `deleting every row or column keeps the sheet addressable`() {
        val sheet = Sheet(
            "Small",
            listOf(cell("A1", "keep"), cell("A3", "gone"), cell("C1", "gone")),
            rowCount = 3,
            columnCount = 3,
        )

        val noRows = sheet.deleteRows(at = 1, count = 10)
        assertEquals(1, noRows.rowCount)
        assertEquals("keep", noRows.valueAt("A1"))

        val noColumns = sheet.deleteColumns(at = 1, count = 10)
        assertEquals(1, noColumns.columnCount)
        assertEquals("keep", noColumns.valueAt("A1"))
    }

    @Test
    fun `invalid structural edits leave the sheet alone`() {
        val sheet = Sheet("Plain", emptyList(), rowCount = 3, columnCount = 3)

        assertEquals(sheet, sheet.insertRows(1, 0))
        assertEquals(sheet, sheet.deleteRows(-1, 1))
        assertEquals(sheet, sheet.deleteRows(3, 1))
        assertEquals(sheet, sheet.insertColumns(1, -2))
        assertEquals(sheet, sheet.deleteColumns(3, 1))
    }
}
