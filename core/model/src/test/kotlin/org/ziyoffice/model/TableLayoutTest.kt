package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TableLayoutTest {

    private fun paragraph(text: String) = Paragraph(listOf(Run(text)))

    @Test
    fun `plain table places every cell at its own grid position`() {
        val table = Table(
            rows = listOf(
                TableRow(listOf(TableCell(listOf(paragraph("a"))), TableCell(listOf(paragraph("b"))))),
                TableRow(listOf(TableCell(listOf(paragraph("c"))), TableCell(listOf(paragraph("d"))))),
            ),
            columnCount = 2,
        )

        val placed = table.placedCells()

        assertEquals(listOf(0, 0, 1, 1), placed.map { it.row })
        assertEquals(listOf(0, 1, 0, 1), placed.map { it.column })
        assertEquals(List(4) { 1 }, placed.map { it.rowSpan })
        assertEquals(List(4) { 1 }, placed.map { it.columnSpan })
        assertEquals(listOf(0, 1, 0, 1), placed.map { it.cellIndex })
    }

    @Test
    fun `horizontal span shifts the columns of the cells after it`() {
        val table = Table(
            rows = listOf(
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("wide")), columnSpan = 2),
                        TableCell(listOf(paragraph("right"))),
                    ),
                ),
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("a"))),
                        TableCell(listOf(paragraph("b"))),
                        TableCell(listOf(paragraph("c"))),
                    ),
                ),
            ),
            columnCount = 3,
        )

        val placed = table.placedCells()

        assertEquals(listOf(0, 2), placed.take(2).map { it.column })
        assertEquals(listOf(2, 1), placed.take(2).map { it.columnSpan })
        assertEquals(listOf(0, 1), placed.take(2).map { it.cellIndex })
        assertEquals(listOf(0, 1, 2), placed.drop(2).map { it.column })
    }

    @Test
    fun `vertical merge becomes one placed cell covering both rows`() {
        val table = Table(
            rows = listOf(
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("merged")), verticalMerge = VerticalMerge.RESTART),
                        TableCell(listOf(paragraph("a"))),
                    ),
                ),
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("")), verticalMerge = VerticalMerge.CONTINUE),
                        TableCell(listOf(paragraph("b"))),
                    ),
                ),
            ),
            columnCount = 2,
        )

        val placed = table.placedCells()

        val merged = placed.single { it.column == 0 }
        assertEquals(0, merged.row)
        assertEquals(2, merged.rowSpan)
        assertEquals(3, placed.size)
        // The continuation cell keeps its model coordinates for editing.
        assertEquals(0, merged.rowIndex)
        assertEquals(0, merged.cellIndex)
        assertEquals("merged", (merged.cell.blocks.single() as Paragraph).text())
    }

    @Test
    fun `merge stops at a cell that does not continue it`() {
        val table = Table(
            rows = listOf(
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("top")), verticalMerge = VerticalMerge.RESTART),
                        TableCell(listOf(paragraph("a"))),
                    ),
                ),
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("not merged"))),
                        TableCell(listOf(paragraph("b"))),
                    ),
                ),
            ),
            columnCount = 2,
        )

        val placed = table.placedCells()

        assertEquals(4, placed.size)
        assertEquals(1, placed.single { it.cellIndex == 0 && it.row == 0 }.rowSpan)
    }

    @Test
    fun `merge needs the continuation in the same grid column`() {
        val table = Table(
            rows = listOf(
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("top"))),
                        TableCell(listOf(paragraph("right")), verticalMerge = VerticalMerge.RESTART),
                    ),
                ),
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("continues in the wrong column")), verticalMerge = VerticalMerge.CONTINUE),
                        TableCell(listOf(paragraph("b"))),
                    ),
                ),
            ),
            columnCount = 2,
        )

        val placed = table.placedCells()

        // The left continuation has no restart above it, so it is still drawn;
        // the right restart has no continuation below, so it stays one row.
        assertEquals(4, placed.size)
        assertEquals(1, placed.single { it.row == 1 && it.cellIndex == 0 }.rowSpan)
        assertEquals(1, placed.single { it.row == 0 && it.cellIndex == 1 }.rowSpan)
    }

    @Test
    fun `a ragged row leaves trailing grid columns empty without failing`() {
        val table = Table(
            rows = listOf(
                TableRow(
                    listOf(
                        TableCell(listOf(paragraph("a"))),
                        TableCell(listOf(paragraph("b"))),
                    ),
                ),
                TableRow(listOf(TableCell(listOf(paragraph("c"))))),
            ),
            columnCount = 2,
        )

        val placed = table.placedCells()

        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0), placed.map { it.row to it.column })
    }

    private fun Paragraph.text(): String = runs.joinToString("") { it.text }
}
