package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableEditsTest {

    @Test
    fun emptyTable3x2() {
        val table = emptyTable(3, 2, borders = true)
        assertEquals(3, table.rows.size)
        assertEquals(2, table.columnCount)
        assertTrue(table.borders)
        for (row in table.rows) {
            assertEquals(2, row.cells.size)
            for (cell in row.cells) {
                assertEquals(1, cell.blocks.size)
                assertTrue(cell.blocks[0] is Paragraph)
            }
        }
    }

    @Test
    fun insertRowAtEnd() {
        val table = emptyTable(2, 2)
        val updated = table.withRowInserted(2)
        assertEquals(3, updated.rows.size)
    }

    @Test
    fun insertRowAtStart() {
        val original = emptyTable(2, 2)
        val p = Paragraph(runs = listOf(Run("row0")))
        val customTable = original.copy(
            rows = listOf(
                TableRow(cells = listOf(TableCell(listOf(p)), TableCell(listOf(Paragraph())))),
                original.rows[1],
            ),
        )
        val updated = customTable.withRowInserted(0)
        assertEquals(3, updated.rows.size)
        // Original row 0 is now row 1
        val cellP = updated.rows[1].cells[0].blocks[0] as Paragraph
        assertEquals("row0", cellP.runs[0].text)
    }

    @Test
    fun removeRowFromMultiRow() {
        val table = emptyTable(3, 2)
        val updated = table.withRowRemoved(1)
        assertEquals(2, updated.rows.size)
    }

    @Test
    fun removeRowFromSingleRow() {
        val table = emptyTable(1, 2)
        val updated = table.withRowRemoved(0)
        assertEquals(1, updated.rows.size)
        assertEquals(2, updated.rows[0].cells.size)
    }

    @Test
    fun insertColumn() {
        val table = emptyTable(2, 2)
        val updated = table.withColumnInserted(1)
        assertEquals(3, updated.columnCount)
        for (row in updated.rows) {
            assertEquals(3, row.cells.size)
        }
    }

    @Test
    fun removeColumn() {
        val table = emptyTable(2, 3)
        val updated = table.withColumnRemoved(1)
        assertEquals(2, updated.columnCount)
        for (row in updated.rows) {
            assertEquals(2, row.cells.size)
        }
    }
}
