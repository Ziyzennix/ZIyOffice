package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.ziyoffice.model.BorderStyle
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellBorders
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.CellValue
import org.ziyoffice.model.ColumnWidth
import org.ziyoffice.model.DEFAULT_ROW_HEIGHT_POINTS
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnWidth
import org.ziyoffice.model.evaluateFormula
import org.ziyoffice.model.rowHeight

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
    fun cellBordersSurviveRoundTrip() {
        val sheet = Sheet(
            name = "Bordered",
            cells = listOf(
                Cell(
                    row = 0,
                    column = 0,
                    value = "Header",
                    format = CellFormat(
                        borders = CellBorders(
                            top = BorderStyle.MEDIUM,
                            bottom = BorderStyle.THICK,
                        ),
                    ),
                ),
                Cell(
                    row = 1,
                    column = 0,
                    value = "Body",
                    format = CellFormat(
                        borders = CellBorders(
                            left = BorderStyle.DASHED,
                            right = BorderStyle.DOTTED,
                        ),
                    ),
                ),
            ),
        )

        val bytes = XlsxWriter().write(Workbook(listOf(sheet)))
        val out = reader().read(ByteArrayInputStream(bytes)).sheets.single()

        assertEquals(
            CellBorders(top = BorderStyle.MEDIUM, bottom = BorderStyle.THICK),
            out.cellAt(0, 0)?.format?.borders,
        )
        assertEquals(
            CellBorders(left = BorderStyle.DASHED, right = BorderStyle.DOTTED),
            out.cellAt(1, 0)?.format?.borders,
        )
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
    fun mergedCellsSurviveRoundTrip() {
        val sheet = Sheet(
            name = "Merged",
            cells = listOf(
                Cell(row = 0, column = 0, value = "Quarterly report"),
                Cell(row = 2, column = 0, value = "left"),
            ),
            mergedRanges = listOf(
                CellRange(0, 0, 1, 3),
                CellRange(2, 0, 3, 0),
            ),
        )
        val out = reader().read(
            ByteArrayInputStream(XlsxWriter().write(Workbook(listOf(sheet)))),
        ).sheets.single()

        assertEquals(
            listOf(
                CellRange(0, 0, 1, 3),
                CellRange(2, 0, 3, 0),
            ),
            out.mergedRanges,
        )
        assertEquals("Quarterly report", out.cellAt(0, 0)?.value)
        assertTrue(out.rowCount >= 4)
        assertTrue(out.columnCount >= 4)
    }

    @Test
    fun emptyMergedRangeSurvivesRoundTrip() {
        val sheet = Sheet(
            name = "Merged",
            cells = emptyList(),
            rowCount = 2,
            columnCount = 2,
            mergedRanges = listOf(CellRange(1, 1, 4, 2)),
        )
        val out = reader().read(
            ByteArrayInputStream(XlsxWriter().write(Workbook(listOf(sheet)))),
        ).sheets.single()

        assertEquals(listOf(CellRange(1, 1, 4, 2)), out.mergedRanges)
        assertTrue(out.rowCount >= 5)
        assertTrue(out.columnCount >= 3)
    }

    @Test
    fun columnWidthsAndRowHeightsSurviveRoundTrip() {
        val sheet = Sheet(
            name = "Sized",
            cells = listOf(
                Cell(row = 0, column = 0, value = "Header"),
                Cell(row = 1, column = 0, value = "Body"),
            ),
            columnWidths = listOf(
                ColumnWidth(0, 0, 24.5f),
                ColumnWidth(2, 4, 11f),
            ),
            rowHeights = mapOf(
                0 to 30f,
                // A height without any cells still belongs to the sheet.
                6 to 22f,
            ),
        )
        val out = reader().read(
            ByteArrayInputStream(XlsxWriter().write(Workbook(listOf(sheet)))),
        ).sheets.single()

        assertEquals(
            listOf(ColumnWidth(0, 0, 24.5f), ColumnWidth(2, 4, 11f)),
            out.columnWidths,
        )
        assertEquals(24.5f, out.columnWidth(0), 0.001f)
        assertEquals(11f, out.columnWidth(3), 0.001f)
        assertEquals(30f, out.rowHeight(0), 0.001f)
        assertEquals(22f, out.rowHeight(6), 0.001f)
        assertEquals(DEFAULT_ROW_HEIGHT_POINTS, out.rowHeight(1), 0.001f)
        assertTrue(out.rowCount >= 7)
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
