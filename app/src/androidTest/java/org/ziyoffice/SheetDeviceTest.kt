package org.ziyoffice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.ooxml.XlsxEditor
import org.ziyoffice.ooxml.XlsxReader
import org.ziyoffice.ooxml.XlsxSource
import org.ziyoffice.ooxml.XlsxWriter

@RunWith(AndroidJUnit4::class)
class SheetDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun scratchFile(name: String): File =
        File(context.filesDir, name).also { it.delete() }

    @Test
    fun spreadsheetRoundTripOnAndroidStoragePreservesFormulasAndFormatting() {
        val file = scratchFile("test-sheet.xlsx")

        val format = CellFormat(bold = true, wrapText = true)
        val originalSheet = Sheet(
            name = "Expenses",
            cells = listOf(
                Cell(row = 0, column = 0, value = "Rent", format = format),
                Cell(row = 0, column = 1, value = "1200", format = CellFormat()),
                Cell(row = 1, column = 0, value = "Food", format = format, note = "Groceries and coffee"),
                Cell(row = 1, column = 1, value = "400", format = CellFormat()),
                Cell(row = 2, column = 0, value = "Total", format = format),
                Cell(row = 2, column = 1, value = "=B1+B2", format = CellFormat(bold = true)),
            ),
        )
        val originalWorkbook = Workbook(listOf(originalSheet))

        val writtenBytes = XlsxWriter().write(originalWorkbook)
        file.writeBytes(writtenBytes)

        // Verify ZIP header
        val header = file.readBytes().take(4)
        assertEquals(listOf(0x50, 0x4B, 0x03, 0x04), header.map { it.toInt() and 0xFF })

        // Read through XlsxSource
        val source = XlsxSource.capture(file.readBytes())
        val readWorkbook = source.workbook
        val readSheet = readWorkbook.sheets.single()

        assertEquals("Expenses", readSheet.name)
        val rentCell = readSheet.cells.first { it.row == 0 && it.column == 0 }
        assertEquals("Rent", rentCell.value)
        assertTrue(rentCell.format.bold)
        assertTrue(rentCell.format.wrapText)

        val foodCell = readSheet.cells.first { it.row == 1 && it.column == 0 }
        assertEquals("Groceries and coffee", foodCell.note)

        // Modify through XlsxEditor on device
        val updatedCells = readSheet.cells.map { cell ->
            if (cell.row == 0 && cell.column == 1) cell.copy(value = "1500") else cell
        }
        val updatedSheet = readSheet.copy(cells = updatedCells)
        val updatedWorkbook = readWorkbook.copy(sheets = listOf(updatedSheet))

        val editedBytes = XlsxEditor(source).save(updatedWorkbook)
        file.writeBytes(editedBytes)

        // Read back final saved workbook
        val finalWorkbook = XlsxReader().read(file.inputStream())
        val finalSheet = finalWorkbook.sheets.single()
        val updatedRentValue = finalSheet.cells.first { it.row == 0 && it.column == 1 }
        assertEquals("1500", updatedRentValue.value)

        val totalFormula = finalSheet.cells.first { it.row == 2 && it.column == 1 }
        assertEquals("=B1+B2", totalFormula.value)
    }
}
