package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartPointsTest {

    private fun sheet(vararg cells: Pair<String, String>): Sheet = Sheet(
        name = "S",
        cells = cells.map { (ref, value) ->
            val letters = ref.takeWhile { it.isLetter() }
            val column = letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
            Cell(row = ref.dropWhile { it.isLetter() }.toInt() - 1, column = column, value = value)
        },
        rowCount = 10,
        columnCount = 5,
    )

    @Test
    fun pairsValuesWithTheLabelToTheirLeft() {
        val points = chartPointsFrom(
            sheet("A1" to "Paper", "B1" to "10", "A2" to "Ink", "B2" to "25"),
            column = 1,
        )
        assertEquals(listOf("Paper", "Ink"), points.map { it.label })
        assertEquals(listOf(10.0, 25.0), points.map { it.value })
    }

    @Test
    fun skipsRowsWithoutANumber() {
        val points = chartPointsFrom(
            sheet("A1" to "Item", "B1" to "Cost", "A2" to "Paper", "B2" to "10"),
            column = 1,
        )
        assertEquals(1, points.size)
        assertEquals("Paper", points.single().label)
    }

    @Test
    fun fallsBackToARowLabelWhenThereIsNoLeftColumn() {
        val points = chartPointsFrom(sheet("A1" to "7"), column = 0)
        assertEquals("Row 1", points.single().label)
    }

    @Test
    fun evaluatesFormulaCells() {
        val points = chartPointsFrom(
            sheet("A1" to "x", "B1" to "3", "A2" to "y", "B2" to "=B1*2"),
            column = 1,
        )
        assertEquals(listOf(3.0, 6.0), points.map { it.value })
    }

    @Test
    fun capsTheNumberOfBars() {
        val cells = (1..20).flatMap { row ->
            listOf("A$row" to "l$row", "B$row" to "$row")
        }.toTypedArray()
        val points = chartPointsFrom(sheet(*cells), column = 1)
        assertTrue(points.size <= 12, "chart must stay readable, got ${points.size}")
    }
}
