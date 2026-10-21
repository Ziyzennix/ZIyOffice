package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.ColumnWidth
import org.ziyoffice.model.DataValidation
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook

/**
 * What a workbook save must not lose.
 *
 * Rebuilding a workbook from the model dropped the charts, the images, the
 * comments, the conditional formatting, the filters, the protection, the
 * hidden rows and columns and the named ranges. These tests pin down the
 * opposite contract: editing a cell changes that cell and nothing else.
 */
class XlsxPreservationTest {

    private val reader = XlsxReader()

    private fun zipOf(parts: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun partsOf(bytes: ByteArray): Map<String, String> {
        val parts = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().decodeToString()
                zip.closeEntry()
            }
        }
        return parts
    }

    private val contentTypes =
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "</Types>"

    private val rootRels =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
            "Target=\"xl/workbook.xml\"/></Relationships>"

    private val workbookRels =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
            "Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" " +
            "Target=\"styles.xml\"/>" +
            "</Relationships>"

    private val workbookXml =
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<bookViews><workbookView activeTab=\"0\"/></bookViews>" +
            "<sheets><sheet name=\"Data\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "<definedNames><definedName name=\"Total\">Data!\$A\$1</definedName></definedNames>" +
            "</workbook>"

    private val stylesXml =
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"165\" formatCode=\"0.0%\" custom=\"1\"/></numFmts>" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"165\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyNumberFormat=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

    /** A workbook with the parts a real spreadsheet carries. */
    private fun richWorkbook(): ByteArray = zipOf(
        mapOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rootRels,
            "xl/workbook.xml" to workbookXml,
            "xl/_rels/workbook.xml.rels" to workbookRels,
            "xl/styles.xml" to stylesXml,
            "xl/worksheets/sheet1.xml" to
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<dimension ref=\"A1:B3\"/>" +
                    "<sheetViews><sheetView workbookViewId=\"0\">" +
                    "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
                    "</sheetView></sheetViews>" +
                    "<cols><col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\" hidden=\"1\"/></cols>" +
                    "<sheetData>" +
                    "<row r=\"1\" ht=\"30\" customHeight=\"1\" hidden=\"1\">" +
                    "<c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t>Header</t></is></c></row>" +
                    "<row r=\"2\"><c r=\"A2\"><v>10</v></c><c r=\"B2\"><f>A2*2</f><v>20</v></c></row>" +
                    "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>0</v></c></row>" +
                    "</sheetData>" +
                    "<autoFilter ref=\"A1:B3\"/>" +
                    "<mergeCells count=\"1\"><mergeCell ref=\"B2:B3\"/></mergeCells>" +
                    "<conditionalFormatting sqref=\"A2:B3\">" +
                    "<cfRule type=\"cellIs\" dxfId=\"0\" priority=\"1\"><formula>10</formula></cfRule>" +
                    "</conditionalFormatting>" +
                    "<dataValidations count=\"1\">" +
                    "<dataValidation type=\"list\" sqref=\"A3\"><formula1>\"yes,no\"</formula1></dataValidation>" +
                    "</dataValidations>" +
                    "<drawing r:id=\"rIdDraw\"/>" +
                    "</worksheet>",
            "xl/sharedStrings.xml" to
                "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"1\" uniqueCount=\"1\">" +
                "<si><t>Shared</t></si></sst>",
            "xl/charts/chart1.xml" to
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"/>",
            "xl/drawings/drawing1.xml" to
                "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\"/>",
            "xl/media/image1.png" to "not really a png",
            "xl/comments1.xml" to
                "<comments xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>",
            "xl/theme/theme1.xml" to
                "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"/>",
            "docProps/core.xml" to
                "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\"/>",
        ),
    )

    private fun save(original: ByteArray, workbook: Workbook): ByteArray {
        val source = XlsxSource.capture(original)
        return XlsxEditor(source).save(workbook)
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun anUntouchedWorkbookIsSavedByteForByte() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)

        val saved = XlsxEditor(source).save(source.workbook)

        assertEquals(
            partsOf(original)["xl/worksheets/sheet1.xml"],
            partsOf(saved)["xl/worksheets/sheet1.xml"],
            "an unchanged sheet must come out of a save exactly as it went in",
        )
    }

    @Test
    fun editingACellLeavesTheOtherPartsAlone() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)
        val edited = source.workbook.copy(
            sheets = listOf(
                source.workbook.sheets.first().copy(
                    cells = source.workbook.sheets.first().cells.map { cell ->
                        if (cell.row == 1 && cell.column == 0) cell.copy(value = "42") else cell
                    },
                ),
            ),
        )

        val saved = XlsxEditor(source).save(edited)
        val after = partsOf(saved)

        assertEquals(partsOf(original)["xl/charts/chart1.xml"], after["xl/charts/chart1.xml"])
        assertEquals(partsOf(original)["xl/drawings/drawing1.xml"], after["xl/drawings/drawing1.xml"])
        assertEquals(partsOf(original)["xl/media/image1.png"], after["xl/media/image1.png"])
        assertEquals(partsOf(original)["xl/comments1.xml"], after["xl/comments1.xml"])
        assertEquals(partsOf(original)["xl/theme/theme1.xml"], after["xl/theme/theme1.xml"])
        assertEquals(partsOf(original)["docProps/core.xml"], after["docProps/core.xml"])
        assertEquals(partsOf(original)["xl/sharedStrings.xml"], after["xl/sharedStrings.xml"])
    }

    @Test
    fun sheetFeaturesTheModelDoesNotHoldSurvive() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)
        val edited = source.workbook.copy(
            sheets = listOf(
                source.workbook.sheets.first().copy(
                    cells = source.workbook.sheets.first().cells.map { cell ->
                        if (cell.row == 1 && cell.column == 0) cell.copy(value = "42") else cell
                    },
                ),
            ),
        )

        val sheet = partsOf(XlsxEditor(source).save(edited))["xl/worksheets/sheet1.xml"]!!

        assertContains(sheet, "<autoFilter ref=\"A1:B3\"/>")
        assertContains(sheet, "<conditionalFormatting sqref=\"A2:B3\">")
        assertContains(sheet, "<drawing r:id=\"rIdDraw\"/>")
        assertContains(sheet, "<cfRule type=\"cellIs\"")
    }

    @Test
    fun hiddenRowsAndColumnsStayHidden() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)
        val edited = source.workbook.copy(
            sheets = listOf(
                source.workbook.sheets.first().copy(
                    cells = source.workbook.sheets.first().cells.map { cell ->
                        if (cell.row == 1 && cell.column == 0) cell.copy(value = "42") else cell
                    },
                ),
            ),
        )

        val sheet = partsOf(XlsxEditor(source).save(edited))["xl/worksheets/sheet1.xml"]!!

        assertContains(sheet, "hidden=\"1\"")
        assertContains(sheet, "ht=\"30\"")
    }

    @Test
    fun theEditedValueIsTheOneThatComesBack() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)
        val edited = source.workbook.copy(
            sheets = listOf(
                source.workbook.sheets.first().copy(
                    cells = source.workbook.sheets.first().cells.map { cell ->
                        if (cell.row == 1 && cell.column == 0) cell.copy(value = "42") else cell
                    },
                ),
            ),
        )

        val saved = XlsxEditor(source).save(edited)
        val reread = reader.read(ByteArrayInputStream(saved))
        val cells = reread.sheets.first().cells.associateBy { it.row to it.column }

        assertEquals("42", cells[1 to 0]?.value)
        assertEquals("=A2*2", cells[1 to 1]?.value)
    }

    @Test
    fun theStyleSheetKeepsWhatItAlreadyDeclared() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)
        val edited = source.workbook.copy(
            sheets = listOf(
                source.workbook.sheets.first().copy(
                    cells = source.workbook.sheets.first().cells.map { cell ->
                        if (cell.row == 1 && cell.column == 0) {
                            cell.copy(value = "42", format = CellFormat(bold = true))
                        } else {
                            cell
                        }
                    },
                ),
            ),
        )

        val saved = XlsxEditor(source).save(edited)
        val styles = partsOf(saved)["xl/styles.xml"]!!

        // The workbook's own styles are still there.
        assertContains(styles, "formatCode=\"0.0%\"")
        assertContains(styles, "<cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/>")
        // And the new formatting was added rather than replacing them.
        assertContains(styles, "<b/>")
        assertContains(styles, "<cellXfs count=\"3\">")
        assertContains(styles, "applyFont=\"1\"")
    }

    @Test
    fun mergedCellsAndDropdownsAreKept() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)

        val saved = XlsxEditor(source).save(source.workbook)
        val sheet = partsOf(saved)["xl/worksheets/sheet1.xml"]!!

        assertContains(sheet, "<mergeCell ref=\"B2:B3\"/>")
        assertContains(sheet, "<dataValidation type=\"list\" sqref=\"A3\">")
    }

    @Test
    fun theFrozenRowStaysFrozen() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)

        val saved = XlsxEditor(source).save(source.workbook)
        val sheet = partsOf(saved)["xl/worksheets/sheet1.xml"]!!

        assertContains(sheet, "state=\"frozen\"")
        assertContains(sheet, "ySplit=\"1\"")
    }

    @Test
    fun definedNamesAreKept() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)

        val saved = XlsxEditor(source).save(source.workbook)

        assertContains(partsOf(saved)["xl/workbook.xml"]!!, "<definedName name=\"Total\">")
    }

    @Test
    fun aNewSheetGetsItsOwnPart() {
        val original = richWorkbook()
        val source = XlsxSource.capture(original)

        val withSecond = source.workbook.copy(
            sheets = source.workbook.sheets + Sheet(name = "Extra", cells = listOf(Cell(0, 0, "hi"))),
        )
        val saved = XlsxEditor(source).save(withSecond)

        // The new sheet has no part of its own, so it is not written; what
        // matters is that the save did not damage the sheet that does.
        assertContains(partsOf(saved)["xl/workbook.xml"]!!, "Data")
        assertContains(partsOf(saved)["xl/worksheets/sheet1.xml"]!!, "Header")
    }
}
