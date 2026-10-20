package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellRangeTest {

    @Test
    fun `parses A1 notation`() {
        assertEquals(CellRange(0, 0, 0, 0), CellRange.parse("A1:A1"))
        assertEquals(CellRange(0, 0, 2, 2), CellRange.parse("A1:C3"))
        assertEquals(CellRange(4, 1, 4, 3), CellRange.parse("B5:D5"))
    }

    @Test
    fun `parsing is tolerant of reversed corners`() {
        assertEquals(CellRange(0, 0, 2, 2), CellRange.parse("C3:A1"))
    }

    @Test
    fun `malformed references are rejected`() {
        assertNull(CellRange.parse("A1"))
        assertNull(CellRange.parse("A0:B2"))
        assertNull(CellRange.parse("1:2"))
        assertNull(CellRange.parse("A:B"))
    }

    @Test
    fun `merge clears values hidden by the merge but keeps the anchor`() {
        val sheet = Sheet(
            name = "Merged",
            cells = listOf(
                Cell(0, 0, "kept"),
                Cell(0, 1, "hidden"),
                Cell(1, 0, "hidden too"),
                Cell(1, 1, "also hidden"),
                Cell(5, 0, "outside"),
            ),
        )

        val merged = sheet.withMergedRange(CellRange(0, 0, 1, 1))

        assertEquals(listOf(CellRange(0, 0, 1, 1)), merged.mergedRanges)
        assertEquals(2, merged.cells.size)
        assertEquals("kept", merged.cells.single { it.row == 0 && it.column == 0 }.value)
        assertEquals("outside", merged.cells.single { it.row == 5 }.value)
    }

    @Test
    fun `merging beyond the current extent grows the sheet`() {
        val sheet = Sheet("Small", emptyList(), rowCount = 2, columnCount = 2)

        val merged = sheet.withMergedRange(CellRange(1, 1, 3, 4))

        assertEquals(4, merged.rowCount)
        assertEquals(5, merged.columnCount)
    }

    @Test
    fun `a single cell cannot be merged`() {
        val sheet = Sheet("Plain", emptyList())
        assertEquals(sheet, sheet.withMergedRange(CellRange(2, 2, 2, 2)))
    }

    @Test
    fun `unmerging leaves the value in place`() {
        val sheet = Sheet(
            "Merged",
            cells = listOf(Cell(0, 0, "value")),
            mergedRanges = listOf(CellRange(0, 0, 1, 1)),
        )

        val cleared = sheet.withMergeClearedAt(1, 1)

        assertTrue(cleared.mergedRanges.isEmpty())
        assertEquals("value", cleared.cells.single().value)
    }

    @Test
    fun `unmerging a selection removes every merge it touches`() {
        val sheet = Sheet(
            "Merged",
            emptyList(),
            mergedRanges = listOf(
                CellRange(0, 0, 1, 1),
                CellRange(0, 3, 1, 4),
                CellRange(5, 0, 6, 0),
            ),
        )

        val cleared = sheet.withMergesClearedIn(CellRange(0, 1, 5, 3))

        assertEquals(listOf(CellRange(5, 0, 6, 0)), cleared.mergedRanges)
    }

    @Test
    fun `bands compress the rows a vertical merge covers`() {
        val sheet = Sheet(
            "Merged",
            emptyList(),
            rowCount = 10,
            mergedRanges = listOf(CellRange(2, 0, 4, 0)),
        )

        val bands = sheet.rowBands()

        assertEquals(listOf(1, 1, 3), bands.take(3).map { it.rowCount })
        assertEquals(listOf(0, 1, 2), bands.take(3).map { it.startRow })
        assertEquals(10, bands.sumOf { it.rowCount })
    }

    @Test
    fun `a band grows when a merge starts inside it`() {
        val sheet = Sheet(
            "Nested",
            emptyList(),
            rowCount = 8,
            mergedRanges = listOf(
                CellRange(0, 0, 4, 0),
                CellRange(1, 1, 6, 1),
            ),
        )

        assertEquals(listOf(7, 1), sheet.rowBands().map { it.rowCount })
    }

    @Test
    fun `horizontal merge skips continuation cells in the placement list`() {
        val sheet = Sheet(
            "Wide",
            emptyList(),
            rowCount = 2,
            columnCount = 3,
            mergedRanges = listOf(CellRange(0, 0, 0, 1)),
        )

        val cells = sheet.cellPlacements(sheet.rowBands().first())

        assertEquals(listOf(0 to 0, 0 to 2), cells.map { it.row to it.column })
        assertEquals(2, cells.first().columnSpan)
        assertEquals(1, cells.last().columnSpan)
    }

    @Test
    fun `vertical merge in a band is one placement with a row span`() {
        val sheet = Sheet(
            "Tall",
            emptyList(),
            rowCount = 4,
            columnCount = 2,
            mergedRanges = listOf(CellRange(0, 0, 1, 0)),
        )

        val band = sheet.rowBands().first()
        val cells = sheet.cellPlacements(band)

        val merged = cells.single { it.column == 0 }
        assertEquals(2, merged.rowSpan)
        // The second row still has its other column.
        assertEquals(1, cells.single { it.row == 1 && it.column == 1 }.rowSpan)
    }

    @Test
    fun `overlapping merges are reduced to one`() {
        val normalised = listOf(
            CellRange(0, 0, 1, 1),
            CellRange(1, 1, 2, 2),
            CellRange(5, 5, 6, 6),
        ).normaliseMerges()

        assertEquals(listOf(CellRange(0, 0, 1, 1), CellRange(5, 5, 6, 6)), normalised)
    }
}
