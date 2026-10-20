package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellValue
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.evaluateFormula

class XlsxRoundTripTest {

    private fun reader() = XlsxReader()

    @Test
    fun numbersAndTextSurviveRoundTrip() {
        val sheet = Sheet(
            name = "Budget",
            cells = listOf(
                Cell(row = 0, column = 0, value = "Item"),
                Cell(row = 0, column = 1, value = "Cost"),
                Cell(row = 1, column = 0, value = "Paper"),
                Cell(row = 1, column = 1, value = "12.5"),
                Cell(row = 2, column = 0, value = "Ink & toner <xl>"),
                Cell(row = 2, column = 1, value = "40"),
            ),
        )
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val read = reader().read(ByteArrayInputStream(bytes))
        val out = read.sheets.single()

        assertEquals("Budget", out.name)
        assertEquals("Item", out.cellAt(0, 0)?.value)
        assertEquals("Paper", out.cellAt(1, 0)?.value)
        assertEquals("12.5", out.cellAt(1, 1)?.value)
        assertEquals("Ink & toner <xl>", out.cellAt(2, 0)?.value)
        assertEquals("40", out.cellAt(2, 1)?.value)
    }

    @Test
    fun formulasSurviveRoundTripAndStillEvaluate() {
        val sheet = Sheet(
            name = "Calc",
            cells = listOf(
                Cell(row = 0, column = 0, value = "2"),
                Cell(row = 1, column = 0, value = "3"),
                Cell(row = 2, column = 0, value = "=SUM(A1:A2)"),
                Cell(row = 3, column = 0, value = "=A3*2"),
            ),
        )
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()

        assertEquals("=SUM(A1:A2)", out.cellAt(2, 0)?.value)
        assertEquals("=A3*2", out.cellAt(3, 0)?.value)

        val sum = evaluateFormula(out, "=A3").value
        assertEquals(CellValue.Number(5.0), sum)
        assertEquals(CellValue.Number(10.0), evaluateFormula(out, "=A4").value)
    }

    @Test
    fun formattingSurvivesRoundTrip() {
        val format = CellFormat(
            bold = true,
            italic = true,
            underline = true,
            strike = true,
            sizePt = 14f,
            colorRgb = 0xC00000,
            fillRgb = 0xFFF2CC,
            alignment = CellAlignment.CENTER,
            numberFormat = "0.00",
        )
        val sheet = Sheet(
            name = "Styled",
            cells = listOf(Cell(row = 0, column = 0, value = "1", format = format)),
        )
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()
        val readFormat = out.cellAt(0, 0)!!.format

        assertTrue(readFormat.bold)
        assertTrue(readFormat.italic)
        assertTrue(readFormat.underline)
        assertTrue(readFormat.strike)
        assertEquals(14f, readFormat.sizePt)
        assertEquals(0xC00000, readFormat.colorRgb)
        assertEquals(0xFFF2CC, readFormat.fillRgb)
        assertEquals(CellAlignment.CENTER, readFormat.alignment)
        assertEquals("0.00", readFormat.numberFormat)
    }

    @Test
    fun booleansSurviveRoundTrip() {
        val sheet = Sheet(
            name = "Flags",
            cells = listOf(
                Cell(row = 0, column = 0, value = "TRUE"),
                Cell(row = 1, column = 0, value = "FALSE"),
            ),
        )
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()
        assertEquals("TRUE", out.cellAt(0, 0)?.value)
        assertEquals("FALSE", out.cellAt(1, 0)?.value)
    }

    @Test
    fun multipleSheetsAndActiveTabSurvive() {
        val workbook = Workbook(
            sheets = listOf(
                Sheet("First", listOf(Cell(row = 0, column = 0, value = "a"))),
                Sheet("Second", listOf(Cell(row = 0, column = 0, value = "b"))),
                Sheet("Third", listOf(Cell(row = 0, column = 0, value = "c"))),
            ),
            activeSheet = 2,
        )
        val bytes = XlsxWriter().write(workbook)
        val read = reader().read(ByteArrayInputStream(bytes))

        assertEquals(listOf("First", "Second", "Third"), read.sheets.map { it.name })
        assertEquals("a", read.sheets[0].cellAt(0, 0)?.value)
        assertEquals("b", read.sheets[1].cellAt(0, 0)?.value)
        assertEquals("c", read.sheets[2].cellAt(0, 0)?.value)
        assertEquals(2, read.activeSheet)
    }

    @Test
    fun emptySheetGetsUsableGrid() {
        val bytes = XlsxWriter().write(Workbook(listOf(Sheet("Empty", emptyList()))))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()
        assertTrue(out.rowCount >= 100)
        assertTrue(out.columnCount >= 26)
        assertTrue(out.cells.isEmpty())
    }

    @Test
    fun sheetNamesWithXmlSpecialCharactersSurvive() {
        val sheet = Sheet("Q1 & Q2 <draft>", listOf(Cell(row = 0, column = 0, value = "x")))
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()
        assertEquals("Q1 & Q2 <draft>", out.name)
    }

    @Test
    fun writesPackagePartsExcelExpects() {
        val bytes = XlsxWriter().write(Workbook(listOf(Sheet("S", emptyList()))))
        val names = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertTrue("[Content_Types].xml" in names)
        assertTrue("_rels/.rels" in names)
        assertTrue("xl/workbook.xml" in names)
        assertTrue("xl/_rels/workbook.xml.rels" in names)
        assertTrue("xl/styles.xml" in names)
        assertTrue("xl/worksheets/sheet1.xml" in names)
    }

    @Test
    fun frozenRowsSurviveRoundTrip() {
        val sheet = Sheet(
            name = "Frozen",
            cells = listOf(Cell(row = 0, column = 0, value = "Header")),
            frozenRows = 1,
        )
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()

        assertEquals(1, out.frozenRows)
    }

    @Test
    fun unfrozenSheetStaysUnfrozen() {
        val sheet = Sheet(name = "Plain", cells = listOf(Cell(row = 0, column = 0, value = "x")))
        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()

        assertEquals(0, out.frozenRows)
    }

    @Test
    fun formattedEmptyCellSurvivesRoundTrip() {
        val format = CellFormat(bold = true, fillRgb = 0xD8F3E4)
        val sheet = Sheet(
            name = "Prefilled",
            cells = listOf(Cell(row = 2, column = 3, value = "", format = format)),
        )
        val out = reader().read(
            ByteArrayInputStream(XlsxWriter().write(Workbook(listOf(sheet)))),
        ).sheets.single()

        val cell = out.cellAt(2, 3)
        assertEquals("", cell?.value)
        assertEquals(format, cell?.format)
    }

    private fun Sheet.cellAt(row: Int, column: Int): Cell? =
        cells.firstOrNull { it.row == row && it.column == column }
}
