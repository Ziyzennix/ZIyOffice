package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusPathTest {

    @Test
    fun focusPathsIncludesCells() {
        val doc = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("Body"))),
                emptyTable(2, 2),
            ),
        )
        val paths = doc.allFocusPaths()
        assertEquals(5, paths.size)
        assertEquals(FocusPath(0), paths[0])
        assertEquals(FocusPath(1, 0, 0, 0), paths[1])
        assertEquals(FocusPath(1, 0, 1, 0), paths[2])
        assertEquals(FocusPath(1, 1, 0, 0), paths[3])
        assertEquals(FocusPath(1, 1, 1, 0), paths[4])
    }

    @Test
    fun paragraphAtBody() {
        val p = Paragraph(runs = listOf(Run("Body")))
        val doc = Document(blocks = listOf(p))
        assertEquals(p, doc.paragraphAt(FocusPath(0)))
    }

    @Test
    fun paragraphAtCell() {
        val cellP = Paragraph(runs = listOf(Run("Cell(0,0)")))
        val table = Table(
            rows = listOf(
                TableRow(cells = listOf(TableCell(listOf(cellP)))),
            ),
            columnCount = 1,
        )
        val doc = Document(blocks = listOf(Paragraph(), table))
        assertEquals(cellP, doc.paragraphAt(FocusPath(1, 0, 0, 0)))
    }

    @Test
    fun mapParagraphAtCell() {
        val bodyP = Paragraph(runs = listOf(Run("Body text")))
        val cellP = Paragraph(runs = listOf(Run("Old cell text")))
        val table = Table(
            rows = listOf(
                TableRow(cells = listOf(TableCell(listOf(cellP)))),
            ),
            columnCount = 1,
        )
        val doc = Document(blocks = listOf(bodyP, table))

        val updated = doc.mapParagraphAt(FocusPath(1, 0, 0, 0)) {
            it.copy(runs = listOf(Run("New cell text")))
        }

        // Verify cell paragraph updated
        val updatedCellP = updated.paragraphAt(FocusPath(1, 0, 0, 0))
        assertEquals("New cell text", updatedCellP?.runs?.single()?.text)

        // Verify body paragraph unchanged
        val updatedBodyP = updated.paragraphAt(FocusPath(0))
        assertEquals("Body text", updatedBodyP?.runs?.single()?.text)
    }

    @Test
    fun paragraphAtOutOfBounds() {
        val doc = Document(blocks = listOf(Paragraph()))
        assertNull(doc.paragraphAt(FocusPath(5)))
        assertNull(doc.paragraphAt(FocusPath(0, 0, 0, 0)))
    }
}
