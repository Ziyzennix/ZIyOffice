package org.ziyoffice.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The spreadsheet exporter must print every row and column exactly once. These
 * tests pin the pagination that guarantees it, including the cases that used to
 * lose data silently: a sheet longer than one page, and a column too wide to
 * share a page.
 */
class SheetPaginationTest {

    @Test
    fun longSheetContinuesOntoFurtherPagesWithoutLosingRows() {
        val pages = SheetPagination.rowPagesByHeight(
            first = 0,
            last = 44,
            rowHeights = List(45) { 18f },
            availableHeight = 180f,
        )
        assertEquals(listOf(0..9, 10..19, 20..29, 30..39, 40..44), pages)
        // Every row appears exactly once, in order.
        assertEquals((0..44).toList(), pages.flatMap { it.toList() })
    }

    @Test
    fun singlePageWhenEverythingFits() {
        val pages = SheetPagination.rowPagesByHeight(
            first = 0,
            last = 4,
            rowHeights = List(5) { 18f },
            availableHeight = 200f,
        )
        assertEquals(listOf(0..4), pages)
    }

    @Test
    fun rowsStartingBelowZeroIndexAreKept() {
        // Sheets can start at row 5 when the first rows are empty; the range must
        // not be rebased to 0 and print the wrong row numbers.
        val pages = SheetPagination.rowPagesByHeight(
            first = 5,
            last = 12,
            rowHeights = List(8) { 18f },
            availableHeight = 72f,
        )
        assertEquals(listOf(5..8, 9..12), pages)
    }

    @Test
    fun tallRowsGetTheirOwnSpaceAndChangeTheBreak() {
        val pages = SheetPagination.rowPagesByHeight(
            first = 0,
            last = 5,
            rowHeights = listOf(18f, 18f, 60f, 18f, 18f, 18f),
            availableHeight = 70f,
        )
        assertEquals(listOf(0..1, 2..2, 3..5), pages)
        assertEquals((0..5).toList(), pages.flatMap { it.toList() })
    }

    @Test
    fun rowTallerThanThePageStillGetsAPage() {
        val pages = SheetPagination.rowPagesByHeight(
            first = 0,
            last = 2,
            rowHeights = listOf(500f, 18f, 18f),
            availableHeight = 100f,
        )
        assertEquals(listOf(0..0, 1..2), pages)
    }

    @Test
    fun wideSheetSplitsColumnsAndKeepsAllOfThem() {
        val widths = List(6) { 100f }
        val pages = SheetPagination.columnPages(widths, gutter = 20f, pageWidth = 260f)
        // 20 + 100 + 100 fits in 260; the third column would not.
        assertEquals(listOf(0..1, 2..3, 4..5), pages)
        assertEquals((0..5).toList(), pages.flatMap { it.toList() })
    }

    @Test
    fun columnWiderThanThePageStillGetsAPage() {
        val pages = SheetPagination.columnPages(listOf(500f, 100f), gutter = 20f, pageWidth = 200f)
        assertEquals(listOf(0..0, 1..1), pages)
    }

    @Test
    fun emptySheetHasNoPages() {
        assertTrue(SheetPagination.columnPages(emptyList(), 20f, 200f).isEmpty())
        assertTrue(SheetPagination.rowPagesByHeight(3, 2, emptyList(), 100f).isEmpty())
    }

    @Test
    fun aZeroHeightRowStillTerminates() {
        // Defensive: malformed heights must not loop forever.
        val pages = SheetPagination.rowPagesByHeight(
            first = 0,
            last = 2,
            rowHeights = listOf(0f, 0f, 0f),
            availableHeight = 100f,
        )
        assertEquals(listOf(0..2), pages)
    }
}
