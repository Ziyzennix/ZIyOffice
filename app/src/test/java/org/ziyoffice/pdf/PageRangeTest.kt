package org.ziyoffice.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Split is destructive in the sense that a wrong range produces a file missing
 * pages the user wanted. The parser therefore rejects anything it cannot read
 * exactly instead of clamping, and these tests pin that.
 */
class PageRangeTest {

    @Test
    fun singlePageAndSimpleRange() {
        assertEquals(listOf(3), PageRange.parse("3", 10))
        assertEquals(listOf(2, 3, 4, 5), PageRange.parse("2-5", 10))
    }

    @Test
    fun commaSeparatedPagesKeepTheirOrderWithoutDuplicates() {
        assertEquals(listOf(1, 4, 7), PageRange.parse("1,4,7", 10))
        assertEquals(listOf(1, 2, 4), PageRange.parse("1,2,1,4", 10))
        assertEquals(listOf(1, 2, 3, 5), PageRange.parse("1-3, 5", 10))
    }

    @Test
    fun openEndsRunToTheDocumentEdges() {
        assertEquals(listOf(1, 2, 3, 4), PageRange.parse("-4", 10))
        assertEquals(listOf(7, 8, 9, 10), PageRange.parse("7-", 10))
    }

    @Test
    fun spacesSeparatePagesAsWellAsCommas() {
        assertEquals(listOf(1, 3, 5), PageRange.parse("1 3 5", 10))
    }

    @Test
    fun outOfRangeIsRejectedRatherThanClamped() {
        // A clamped "9-20" would silently produce 9-10 and look successful.
        assertNull(PageRange.parse("9-20", 10))
        assertNull(PageRange.parse("11", 10))
        assertNull(PageRange.parse("0", 10))
        assertNull(PageRange.parse("0-3", 10))
    }

    @Test
    fun reversedAndMalformedRangesAreRejected() {
        assertNull(PageRange.parse("5-2", 10))
        assertNull(PageRange.parse("abc", 10))
        assertNull(PageRange.parse("1-", 0))
        assertNull(PageRange.parse("", 10))
        assertNull(PageRange.parse("  ", 10))
        assertNull(PageRange.parse("-", 10))
    }

    @Test
    fun straySeparatorsAreToleratedRatherThanRejected() {
        // "1,," is an obvious typo with one reading; refusing it would make the
        // dialog feel broken. Anything ambiguous is still rejected above.
        assertEquals(listOf(1), PageRange.parse("1,,", 10))
        assertEquals(listOf(1, 2), PageRange.parse(" 1 , 2 ,", 10))
    }

    @Test
    fun describeCollapsesRuns() {
        assertEquals("1-3, 7", PageRange.describe(listOf(1, 2, 3, 7)))
        assertEquals("4", PageRange.describe(listOf(4)))
        assertEquals("", PageRange.describe(emptyList()))
    }
}
