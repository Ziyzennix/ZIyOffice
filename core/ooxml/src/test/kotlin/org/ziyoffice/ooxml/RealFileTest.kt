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
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.applyText

/**
 * Files a real office suite wrote.
 *
 * Everything else in this folder builds its own XML, which is easy to get
 * wrong in the direction that flatters us. These two were produced by
 * LibreOffice, so they carry the parts a hand-written fixture never has:
 * style sheets with real style ids, a theme, a numbering part, hyperlink
 * relationships, shared strings and formulas.
 *
 * Regenerate them with:
 *   soffice --headless --convert-to docx report.html
 *   soffice --headless --convert-to xlsx summary.fods
 */
class RealFileTest {

    private val reader = DocxReader()

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("libreoffice/$name")) {
            "missing test fixture: $name"
        }.use { it.readBytes() }

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

    // ------------------------------------------------------------------- word

    @Test
    fun theRealDocumentOpens() {
        val document = reader.read(ByteArrayInputStream(fixture("report.docx")))
        val paragraphs = document.blocks.filterIsInstance<Paragraph>()

        assertTrue(paragraphs.isNotEmpty(), "the document should have paragraphs")
        assertEquals("Quarterly report", paragraphs.first().runs.joinToString("") { it.text })
    }

    @Test
    fun theRealDocumentKeepsItsBoldAndItalic() {
        val document = reader.read(ByteArrayInputStream(fixture("report.docx")))
        val paragraphs = document.blocks.filterIsInstance<Paragraph>()
        val all = paragraphs.flatMap { it.runs }

        assertTrue(all.any { it.bold }, "some run should be bold")
        assertTrue(all.any { it.italic }, "some run should be italic")
    }

    @Test
    fun theRealDocumentKeepsItsList() {
        val document = reader.read(ByteArrayInputStream(fixture("report.docx")))
        val listed = document.blocks.filterIsInstance<Paragraph>().filter { it.list != null }

        assertTrue(listed.isNotEmpty(), "the list items should still be a list")
    }

    @Test
    fun theRealDocumentKeepsItsTable() {
        val document = reader.read(ByteArrayInputStream(fixture("report.docx")))
        val tables = document.blocks.filterIsInstance<org.ziyoffice.model.Table>()

        assertEquals(1, tables.size)
        assertEquals(4, tables.first().rows.size)
    }

    @Test
    fun editingTheRealDocumentLeavesTheRestOfThePackageAlone() {
        val original = fixture("report.docx")
        val source = DocxSource.capture(original)

        // Find the paragraph with the bold run and type at the end of it.
        val target = source.document.blocks.filterIsInstance<Paragraph>()
            .firstOrNull { paragraph -> paragraph.runs.any { it.bold } }
        assertTrue(target != null, "the fixture should have a bold run")

        val edited = source.document.copy(
            blocks = source.document.blocks.map { block ->
                if (block is Paragraph && block.runs.any { it.bold }) {
                    block.applyText(block.runs.joinToString("") { it.text } + " (edited)")
                } else {
                    block
                }
            },
        )

        val saved = DocxEditor(source).save(edited)
        val before = partsOf(original)
        val after = partsOf(saved)

        // The parts the editor has no business touching come through unchanged.
        assertEquals(before["word/styles.xml"], after["word/styles.xml"])
        assertEquals(before["word/numbering.xml"], after["word/numbering.xml"])
        assertEquals(before["word/settings.xml"], after["word/settings.xml"])
        assertEquals(before["word/fontTable.xml"], after["word/fontTable.xml"])
        assertEquals(before["word/theme/theme1.xml"], after["word/theme/theme1.xml"])
        assertEquals(before["docProps/core.xml"], after["docProps/core.xml"])

        // The table and the links are still there.
        assertContains(after.getValue("word/document.xml"), "<w:tbl>")
        assertContains(after.getValue("word/document.xml"), "w:hyperlink")
    }

    @Test
    fun theRealDocumentReadsBackAfterASave() {
        val original = fixture("report.docx")
        val source = DocxSource.capture(original)
        val edited = source.document.copy(
            blocks = source.document.blocks.map { block ->
                if (block is Paragraph && block.runs.any { it.bold }) {
                    block.applyText(block.runs.joinToString("") { it.text } + " (edited)")
                } else {
                    block
                }
            },
        )

        val saved = DocxEditor(source).save(edited)
        val reread = reader.read(ByteArrayInputStream(saved))

        assertTrue(
            reread.blocks.filterIsInstance<Paragraph>().isNotEmpty(),
            "the saved document should still have its paragraphs",
        )
        assertEquals(
            1,
            reread.blocks.filterIsInstance<org.ziyoffice.model.Table>().size,
            "the table should survive a save",
        )
        assertContains(
            partsOf(saved).getValue("word/document.xml"),
            "(edited)",
        )
    }

    @Test
    fun anUntouchedRealDocumentComesOutByteForByte() {
        val original = fixture("report.docx")
        val source = DocxSource.capture(original)

        val saved = DocxEditor(source).save(source.document)

        assertEquals(
            partsOf(original)["word/document.xml"],
            partsOf(saved)["word/document.xml"],
            "a document nobody edited must come out of a save exactly as it went in",
        )
    }

    // ------------------------------------------------------------------ sheet

    @Test
    fun theRealSpreadsheetOpens() {
        val source = XlsxSource.capture(fixture("summary.xlsx"))
        val workbook = source.workbook

        assertEquals(2, workbook.sheets.size)
        assertEquals("Summary", workbook.sheets.first().name)
        assertEquals("Notes", workbook.sheets[1].name)
    }

    @Test
    fun theRealSpreadsheetKeepsItsFormulas() {
        val source = XlsxSource.capture(fixture("summary.xlsx"))
        val cells = source.workbook.sheets.first().cells.associateBy { it.row to it.column }

        assertEquals("=B2*C2", cells[1 to 3]?.value)
        assertEquals("=SUM(B2:B3)", cells[3 to 1]?.value)
    }

    @Test
    fun theRealSpreadsheetRoundTrips() {
        val original = fixture("summary.xlsx")
        val source = XlsxSource.capture(original)

        val saved = XlsxEditor(source).save(source.workbook)
        val reread = XlsxSource.capture(saved).workbook
        val cells = reread.sheets.first().cells.associateBy { it.row to it.column }

        assertEquals("Widget", cells[1 to 0]?.value)
        assertEquals("=B2*C2", cells[1 to 3]?.value)
        assertEquals("=SUM(B2:B3)", cells[3 to 1]?.value)
        // The second sheet is still there.
        assertEquals("Notes", reread.sheets[1].name)
    }

    @Test
    fun editingARealSpreadsheetKeepsTheOtherParts() {
        val original = fixture("summary.xlsx")
        val source = XlsxSource.capture(original)

        val edited = source.workbook.copy(
            sheets = source.workbook.sheets.mapIndexed { index, sheet ->
                if (index == 0) {
                    sheet.copy(
                        cells = sheet.cells.map { cell ->
                            if (cell.row == 1 && cell.column == 0) {
                                cell.copy(value = "Gadget renamed")
                            } else {
                                cell
                            }
                        },
                    )
                } else {
                    sheet
                }
            },
        )

        val saved = XlsxEditor(source).save(edited)
        val before = partsOf(original)
        val after = partsOf(saved)

        assertEquals(before["xl/styles.xml"], after["xl/styles.xml"])
        assertEquals(before["xl/theme/theme1.xml"], after["xl/theme/theme1.xml"])
        assertEquals(before["xl/sharedStrings.xml"], after["xl/sharedStrings.xml"])

        val reread = XlsxSource.capture(saved).workbook
        val cells = reread.sheets.first().cells.associateBy { it.row to it.column }
        assertEquals("Gadget renamed", cells[1 to 0]?.value)
        assertEquals("=B2*C2", cells[1 to 3]?.value)
    }

    @Test
    fun aCellTheUserEditedKeepsItsFormulaWhenTheFormulaIsUntouched() {
        val original = fixture("summary.xlsx")
        val source = XlsxSource.capture(original)

        val edited = source.workbook.copy(
            sheets = source.workbook.sheets.mapIndexed { index, sheet ->
                if (index == 0) {
                    sheet.copy(
                        cells = sheet.cells.map { cell: Cell ->
                            if (cell.row == 1 && cell.column == 0) {
                                cell.copy(value = "Gadget renamed")
                            } else {
                                cell
                            }
                        },
                    )
                } else {
                    sheet
                }
            },
        )

        val saved = XlsxEditor(source).save(edited)
        val reread = XlsxSource.capture(saved).workbook
        val cells = reread.sheets.first().cells.associateBy { it.row to it.column }

        assertEquals("=B2*C2", cells[1 to 3]?.value)
        assertEquals("=SUM(B2:B3)", cells[3 to 1]?.value)
    }
}
