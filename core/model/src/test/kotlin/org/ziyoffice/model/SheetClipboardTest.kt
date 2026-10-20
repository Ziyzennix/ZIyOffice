package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetClipboardTest {

    private fun sheet(): Sheet = Sheet(
        name = "Sheet1",
        cells = listOf(
            Cell(row = 0, column = 0, value = "Item"),
            Cell(row = 0, column = 1, value = "Cost"),
            Cell(row = 1, column = 0, value = "Paper"),
            Cell(row = 1, column = 1, value = "12"),
            Cell(row = 2, column = 0, value = "=B2*2"),
        ),
        rowCount = 10,
        columnCount = 6,
    )

    @Test
    fun `a range is copied as tab separated text`() {
        val text = sheet().tsvOf(CellRange(0, 0, 1, 1))

        assertEquals("Item\tCost\nPaper\t12", text)
    }

    @Test
    fun `pasted text lands in the cells under the cursor`() {
        val pasted = sheet().withPastedText("Ink\t5\nRoll\t7", row = 3, column = 0)

        assertEquals("Ink", pasted.valueAt("A4"))
        assertEquals("5", pasted.valueAt("B4"))
        assertEquals("Roll", pasted.valueAt("A5"))
        assertEquals("7", pasted.valueAt("B5"))
    }

    @Test
    fun `pasting over a cell replaces it and keeps its format`() {
        val sheet = Sheet(
            "Sheet1",
            listOf(Cell(row = 0, column = 0, value = "old", format = CellFormat(bold = true))),
            rowCount = 4,
            columnCount = 4,
        )

        val pasted = sheet.withPastedText("new", row = 0, column = 0)

        assertEquals("new", pasted.valueAt("A1"))
        assertTrue(pasted.cells.single().format.bold)
    }

    @Test
    fun `an empty field in the pasted text clears the cell`() {
        val pasted = sheet().withPastedText("\t5", row = 1, column = 0)

        assertEquals("", pasted.valueAt("A2"))
        assertEquals("5", pasted.valueAt("B2"))
    }

    @Test
    fun `a formula stays a formula when it is pasted`() {
        val pasted = sheet().withPastedText("=B2*2", row = 3, column = 0)

        assertEquals("=B2*2", pasted.valueAt("A4"))
    }

    @Test
    fun `pasting past the edge grows the sheet`() {
        val pasted = sheet().withPastedText("x", row = 20, column = 30)

        assertTrue(pasted.rowCount > 20)
        assertTrue(pasted.columnCount > 30)
    }

    private fun Sheet.valueAt(reference: String): String {
        val column = columnIndex(reference.takeWhile { it.isLetter() })
        val row = reference.dropWhile { it.isLetter() }.toInt() - 1
        return cells.first { it.row == row && it.column == column }.value
    }
}
