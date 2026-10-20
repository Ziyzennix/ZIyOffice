package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetLayoutTest {

    @Test
    fun `missing sizes fall back to the worksheet defaults`() {
        val sheet = Sheet("Plain", emptyList())

        assertEquals(DEFAULT_COLUMN_WIDTH_CHARS, sheet.columnWidth(0), 0.001f)
        assertEquals(DEFAULT_ROW_HEIGHT_POINTS, sheet.rowHeight(0), 0.001f)
    }

    @Test
    fun `a width run is split when one column is resized`() {
        val sheet = Sheet(
            "Wide",
            emptyList(),
            columnWidths = listOf(
                ColumnWidth(0, 3, 12f),
                ColumnWidth(5, 5, 20f),
            ),
        )

        val resized = sheet.withColumnWidth(2, 30f)

        assertEquals(
            listOf(
                ColumnWidth(0, 1, 12f),
                ColumnWidth(2, 2, 30f),
                ColumnWidth(3, 3, 12f),
                ColumnWidth(5, 5, 20f),
            ),
            resized.columnWidths,
        )
        assertEquals(12f, resized.columnWidth(0), 0.001f)
        assertEquals(30f, resized.columnWidth(2), 0.001f)
        assertEquals(20f, resized.columnWidth(5), 0.001f)
        assertEquals(DEFAULT_COLUMN_WIDTH_CHARS, resized.columnWidth(4), 0.001f)
    }

    @Test
    fun `resizing the first column of a run leaves the rest alone`() {
        val sheet = Sheet(
            "Wide",
            emptyList(),
            columnWidths = listOf(ColumnWidth(2, 4, 15f)),
        )

        val resized = sheet.withColumnWidth(2, 25f)

        assertEquals(
            listOf(ColumnWidth(2, 2, 25f), ColumnWidth(3, 4, 15f)),
            resized.columnWidths,
        )
    }

    @Test
    fun `column width is clamped to the xlsx range`() {
        val sheet = Sheet("Plain", emptyList())

        assertEquals(
            MAX_COLUMN_WIDTH_CHARS,
            sheet.withColumnWidth(0, 1000f).columnWidth(0),
            0.001f,
        )
        assertEquals(
            MIN_COLUMN_WIDTH_CHARS,
            sheet.withColumnWidth(0, 0f).columnWidth(0),
            0.001f,
        )
        assertNull(sheet.withColumnWidth(-1, 20f).columnWidths.firstOrNull())
    }

    @Test
    fun `row height is stored per row and clamped`() {
        val sheet = Sheet("Tall", emptyList())

        val resized = sheet
            .withRowHeight(3, 36f)
            .withRowHeight(8, 1000f)

        assertEquals(36f, resized.rowHeight(3), 0.001f)
        assertEquals(MAX_ROW_HEIGHT_POINTS, resized.rowHeight(8), 0.001f)
        assertEquals(DEFAULT_ROW_HEIGHT_POINTS, resized.rowHeight(4), 0.001f)
        assertEquals(listOf(3, 8), resized.rowHeights.keys.sorted())
    }

    @Test
    fun `malformed widths are dropped and valid ones are clamped`() {
        val normalised = listOf(
            ColumnWidth(-1, 2, 10f),
            ColumnWidth(2, 1, 10f),
            ColumnWidth(0, 0, 0f),
            ColumnWidth(1, 2, 900f),
            ColumnWidth(5, MAX_SHEET_COLUMNS + 5, 10f),
        ).normaliseColumnWidths()

        assertEquals(
            listOf(
                ColumnWidth(1, 2, MAX_COLUMN_WIDTH_CHARS),
                ColumnWidth(5, MAX_SHEET_COLUMNS - 1, 10f),
            ),
            normalised,
        )
    }
}
