package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Names a formula can use instead of a cell reference.
 */
class NamedRangeTest {

    private fun sheetWith(values: Map<String, String>): Sheet = Sheet(
        name = "Sheet1",
        cells = values.map { (reference, value) ->
            val corner = CellRange.parse("$reference:$reference")!!.let { CellRangeCorner(it.top, it.left) }
            Cell(row = corner.row, column = corner.column, value = value)
        },
        rowCount = 10,
        columnCount = 10,
    )

    @Test
    fun aNameStandsForItsRange() {
        val sheet = sheetWith(mapOf("A1" to "10", "A2" to "20", "A3" to "30"))
        val names = listOf(
            NamedRange("Sales", CellRange(top = 0, left = 0, bottom = 2, right = 0)),
        )
        val result = evaluateFormula(sheet, "=SUM(Sales)", names)
        assertEquals(CellValue.Number(60.0), result.value)
    }

    @Test
    fun aNameIsUsedLikeACellReference() {
        val sheet = sheetWith(mapOf("A1" to "4"))
        val names = listOf(
            NamedRange("Price", CellRange(top = 0, left = 0, bottom = 0, right = 0)),
        )
        val result = evaluateFormula(sheet, "=Price*2", names)
        assertEquals(CellValue.Number(8.0), result.value)
    }

    @Test
    fun aNameOverSeveralCellsIsSummed() {
        val sheet = sheetWith(mapOf("A1" to "4", "B1" to "6"))
        val names = listOf(
            NamedRange("Prices", CellRange(top = 0, left = 0, bottom = 0, right = 1)),
        )
        val result = evaluateFormula(sheet, "=SUM(Prices)", names)
        assertEquals(CellValue.Number(10.0), result.value)
    }

    @Test
    fun aNameThatIsNotThereIsAnError() {
        val sheet = sheetWith(mapOf("A1" to "1"))
        val result = evaluateFormula(sheet, "=Nothing", emptyList())
        assertTrue(result.value is CellValue.Error, "an unknown name should be an error")
    }

    @Test
    fun aNameIsNotConfusedWithACellReference() {
        val sheet = sheetWith(mapOf("A1" to "7"))
        val result = evaluateFormula(sheet, "=A1", emptyList())
        assertEquals(CellValue.Number(7.0), result.value)
    }

    @Test
    fun aNameIsMatchedWithoutRegardToCase() {
        val sheet = sheetWith(mapOf("A1" to "3"))
        val names = listOf(
            NamedRange("Sales", CellRange(top = 0, left = 0, bottom = 0, right = 0)),
        )
        val result = evaluateFormula(sheet, "=sales", names)
        assertEquals(CellValue.Number(3.0), result.value)
    }

    @Test
    fun aNameThatBelongsToOneSheetIsOnlyUsedThere() {
        val sheet = sheetWith(mapOf("A1" to "5"))
        val onThisSheet = NamedRange("Local", CellRange(0, 0, 0, 0), sheetIndex = 0)
        val onAnother = NamedRange("Local", CellRange(0, 0, 0, 0), sheetIndex = 1)
        assertTrue(onThisSheet.appliesTo(0))
        assertTrue(!onThisSheet.appliesTo(1))
        assertTrue(onAnother.appliesTo(1))
    }

    @Test
    fun aNameThatIsUsedEverywhereAppliesToEverySheet() {
        val name = NamedRange("Total", CellRange(0, 0, 0, 0))
        assertTrue(name.appliesTo(0))
        assertTrue(name.appliesTo(7))
    }
}
