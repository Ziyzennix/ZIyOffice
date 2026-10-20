package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetSortTest {

    private fun sheet(vararg cells: Pair<String, String>): Sheet = Sheet(
        name = "Sheet1",
        cells = cells.map { (ref, value) ->
            val column = columnIndex(ref.takeWhile { it.isLetter() })
            val row = ref.dropWhile { it.isLetter() }.toInt() - 1
            Cell(row = row, column = column, value = value)
        },
        rowCount = 20,
        columnCount = 10,
    )

    private fun column(sheet: Sheet, index: Int): List<String> =
        (0..4).map { row ->
            sheet.cells.firstOrNull { it.row == row && it.column == index }?.value.orEmpty()
        }

    @Test
    fun sortsTextAscendingCaseInsensitively() {
        val sorted = SheetSort.sort(
            sheet("A1" to "pear", "A2" to "Apple", "A3" to "banana"),
            topRow = 0,
            bottomRow = 2,
            key = SheetSort.Key(column = 0),
        )
        assertEquals(listOf("Apple", "banana", "pear"), column(sorted, 0).take(3))
    }

    @Test
    fun sortsDescending() {
        val sorted = SheetSort.sort(
            sheet("A1" to "1", "A2" to "10", "A3" to "2"),
            topRow = 0,
            bottomRow = 2,
            key = SheetSort.Key(column = 0, ascending = false),
        )
        assertEquals(listOf("10", "2", "1"), column(sorted, 0).take(3))
    }

    @Test
    fun numbersSortNumericallyNotLexically() {
        val sorted = SheetSort.sort(
            sheet("A1" to "9", "A2" to "10", "A3" to "100"),
            topRow = 0,
            bottomRow = 2,
            key = SheetSort.Key(column = 0),
        )
        assertEquals(listOf("9", "10", "100"), column(sorted, 0).take(3))
    }

    @Test
    fun blanksSortLastInBothDirections() {
        val ascending = SheetSort.sort(
            sheet("A1" to "b", "A2" to "", "A3" to "a"),
            topRow = 0,
            bottomRow = 2,
            key = SheetSort.Key(column = 0, ascending = true),
        )
        assertEquals(listOf("a", "b", ""), column(ascending, 0).take(3))

        val descending = SheetSort.sort(
            sheet("A1" to "b", "A2" to "", "A3" to "a"),
            topRow = 0,
            bottomRow = 2,
            key = SheetSort.Key(column = 0, ascending = false),
        )
        assertEquals(listOf("b", "a", ""), column(descending, 0).take(3))
    }

    @Test
    fun wholeRowsMoveTogether() {
        val sorted = SheetSort.sort(
            sheet("A1" to "2", "B1" to "two", "A2" to "1", "B2" to "one"),
            topRow = 0,
            bottomRow = 1,
            key = SheetSort.Key(column = 0),
        )
        assertEquals(listOf("1", "2"), column(sorted, 0).take(2))
        assertEquals(listOf("one", "two"), column(sorted, 1).take(2))
    }

    @Test
    fun rowsOutsideTheRangeAreUntouched() {
        val sorted = SheetSort.sort(
            sheet("A1" to "keep", "A2" to "z", "A3" to "a"),
            topRow = 1,
            bottomRow = 2,
            key = SheetSort.Key(column = 0),
        )
        assertEquals("keep", column(sorted, 0).first())
        assertEquals(listOf("a", "z"), column(sorted, 0).drop(1).take(2))
    }

    @Test
    fun singleRowRangeIsANoOp() {
        val original = sheet("A1" to "only")
        assertEquals(original, SheetSort.sort(original, 0, 0, SheetSort.Key(0)))
    }
}
