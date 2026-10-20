package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetFindTest {

    private fun sheet(): Sheet = Sheet(
        name = "Sheet1",
        cells = listOf(
            Cell(row = 0, column = 0, value = "Paper"),
            Cell(row = 0, column = 1, value = "10"),
            Cell(row = 1, column = 0, value = "paperclip"),
            Cell(row = 1, column = 1, value = "=B1*2"),
            Cell(row = 2, column = 0, value = "=\"Paper box\""),
            Cell(row = 2, column = 1, value = "=SUM(B1:B2)"),
        ),
        rowCount = 10,
        columnCount = 6,
    )

    @Test
    fun `matches are found in reading order and ignore case by default`() {
        val hits = sheet().findMatches("paper")

        assertEquals(listOf(SheetMatch(0, 0), SheetMatch(1, 0), SheetMatch(2, 0)), hits)
    }

    @Test
    fun `matching case can be asked for`() {
        val hits = sheet().findMatches("paper", ignoreCase = false)

        assertEquals(listOf(SheetMatch(1, 0)), hits)
    }

    @Test
    fun `a formula is searched through the text it quotes`() {
        val sheet = sheet()

        assertTrue(sheet.findMatches("Paper box").isNotEmpty())
        // A function name and a reference are not text a search may touch.
        assertTrue(sheet.findMatches("SUM").isEmpty())
        assertTrue(sheet.findMatches("B1").isEmpty())
    }

    @Test
    fun `replacing rewrites text cells and counts the hits`() {
        val result = sheet().replaceText("paper", "card")

        assertEquals(3, result.occurrences)
        assertEquals(3, result.cells)
        assertEquals("card", result.sheet.cells.first { it.row == 0 && it.column == 0 }.value)
        assertEquals("cardclip", result.sheet.cells.first { it.row == 1 && it.column == 0 }.value)
        assertEquals(
            "=\"card box\"",
            result.sheet.cells.first { it.row == 2 && it.column == 0 }.value,
        )
    }

    @Test
    fun `replacing leaves formulas alone except for quoted text`() {
        val result = sheet().replaceText("Paper", "Card")

        assertEquals(3, result.occurrences)
        assertEquals("=B1*2", result.sheet.cells.first { it.row == 1 && it.column == 1 }.value)
        assertEquals(
            "=\"Card box\"",
            result.sheet.cells.first { it.row == 2 && it.column == 0 }.value,
        )
    }

    @Test
    fun `a whole cell rule only replaces an exact cell`() {
        val result = sheet().replaceText("paper", "card", wholeCell = true)

        assertEquals(1, result.occurrences)
        assertEquals("card", result.sheet.cells.first { it.row == 0 && it.column == 0 }.value)
        assertEquals("paperclip", result.sheet.cells.first { it.row == 1 && it.column == 0 }.value)
    }

    @Test
    fun `one cell can be replaced on its own`() {
        val (updated, count) = sheet().replaceInCell(0, 0, "Paper", "Card")

        assertEquals(1, count)
        assertEquals("Card", updated.cells.first { it.row == 0 && it.column == 0 }.value)
        assertEquals("paperclip", updated.cells.first { it.row == 1 && it.column == 0 }.value)
    }

    @Test
    fun `an empty term changes nothing`() {
        val sheet = sheet()

        assertTrue(sheet.findMatches("").isEmpty())
        assertEquals(0, sheet.replaceText("", "x").occurrences)
    }
}
