package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellBordersTest {

    private fun sheet(): Sheet = Sheet(
        name = "Table",
        cells = listOf(
            Cell(row = 0, column = 0, value = "Item"),
            Cell(row = 0, column = 1, value = "Cost"),
            Cell(row = 1, column = 0, value = "Paper"),
            Cell(row = 1, column = 1, value = "12"),
        ),
        rowCount = 10,
        columnCount = 6,
    )

    private fun Sheet.bordersAt(row: Int, column: Int): CellBorders =
        cells.first { it.row == row && it.column == column }.format.borders

    @Test
    fun `all borders draw every edge of every cell`() {
        val bordered = sheet().withBorders(CellRange(0, 0, 1, 1), BorderKind.ALL)

        val full = CellBorders(
            top = BorderStyle.THIN,
            bottom = BorderStyle.THIN,
            left = BorderStyle.THIN,
            right = BorderStyle.THIN,
        )
        assertEquals(full, bordered.bordersAt(0, 0))
        assertEquals(full, bordered.bordersAt(1, 1))
    }

    @Test
    fun `a box draws the outline of the selection only`() {
        val boxed = sheet().withBorders(CellRange(0, 0, 1, 1), BorderKind.BOX)

        assertEquals(
            CellBorders(
                top = BorderStyle.THIN,
                bottom = BorderStyle.NONE,
                left = BorderStyle.THIN,
                right = BorderStyle.NONE,
            ),
            boxed.bordersAt(0, 0),
        )
        assertEquals(
            CellBorders(
                top = BorderStyle.NONE,
                bottom = BorderStyle.THIN,
                left = BorderStyle.NONE,
                right = BorderStyle.THIN,
            ),
            boxed.bordersAt(1, 1),
        )
    }

    @Test
    fun `a single edge can be drawn on its own`() {
        val top = sheet().withBorders(CellRange(0, 0, 1, 1), BorderKind.TOP)

        // The edge is drawn on every cell of the selection, the way a
        // spreadsheet draws a rule between rows.
        assertEquals(BorderStyle.THIN, top.bordersAt(0, 0).top)
        assertEquals(BorderStyle.THIN, top.bordersAt(0, 1).top)
        assertEquals(BorderStyle.THIN, top.bordersAt(1, 0).top)
        assertEquals(BorderStyle.NONE, top.bordersAt(1, 0).left)
    }

    @Test
    fun `none removes the borders again`() {
        val cleared = sheet()
            .withBorders(CellRange(0, 0, 1, 1), BorderKind.ALL)
            .withBorders(CellRange(0, 0, 1, 1), BorderKind.NONE)

        assertTrue(cleared.bordersAt(0, 0).isEmpty)
        assertTrue(cleared.bordersAt(1, 1).isEmpty)
    }

    @Test
    fun `cells outside the selection keep their own borders`() {
        val sheet = sheet().withBorders(CellRange(0, 0, 0, 0), BorderKind.ALL)
        val bordered = sheet.withBorders(CellRange(1, 1, 1, 1), BorderKind.ALL)

        assertEquals(BorderStyle.THIN, bordered.bordersAt(0, 0).top)
        assertEquals(BorderStyle.THIN, bordered.bordersAt(1, 1).top)
    }

    @Test
    fun `a cell that only carries a border is kept`() {
        val bordered = Sheet("Empty", emptyList(), rowCount = 4, columnCount = 4)
            .withBorders(CellRange(0, 0, 0, 0), BorderKind.ALL)

        assertEquals(1, bordered.cells.size)
        assertEquals(BorderStyle.THIN, bordered.cells.single().format.borders.top)
    }
}
