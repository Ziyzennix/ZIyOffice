package org.ziyoffice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetValidationTest {

    private fun sheet(): Sheet = Sheet(
        name = "Sheet1",
        cells = listOf(
            Cell(row = 0, column = 0, value = "Item"),
            Cell(row = 0, column = 1, value = "Status"),
            Cell(row = 1, column = 0, value = "Paper"),
        ),
        rowCount = 10,
        columnCount = 6,
    )

    @Test
    fun `a validation covers the cells of its range`() {
        val sheet = sheet().withValidation(CellRange(1, 1, 4, 1), listOf("Open", "Done"))

        assertEquals(listOf("Open", "Done"), sheet.validationAt(1, 1)?.values)
        assertEquals(listOf("Open", "Done"), sheet.validationAt(4, 1)?.values)
        assertNull(sheet.validationAt(0, 1))
        assertNull(sheet.validationAt(1, 0))
    }

    @Test
    fun `a new validation replaces one that overlaps it`() {
        val sheet = sheet()
            .withValidation(CellRange(1, 1, 4, 1), listOf("Open"))
            .withValidation(CellRange(2, 1, 3, 1), listOf("Done"))

        assertEquals(1, sheet.validations.size)
        assertEquals(listOf("Done"), sheet.validationAt(2, 1)?.values)
    }

    @Test
    fun `a validation can be removed again`() {
        val sheet = sheet()
            .withValidation(CellRange(1, 1, 4, 1), listOf("Open"))
            .withValidationCleared(CellRange(1, 1, 2, 1))

        assertTrue(sheet.validations.isEmpty())
    }

    @Test
    fun `an empty list of choices clears the dropdown`() {
        val sheet = sheet()
            .withValidation(CellRange(1, 1, 4, 1), listOf("Open"))
            .withValidation(CellRange(1, 1, 4, 1), emptyList())

        assertTrue(sheet.validations.isEmpty())
    }
}
