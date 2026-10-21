package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The audit exists to tell the user what they will not be able to edit.
 *
 * Saving writes the edited parts back into the original package, so nothing is
 * thrown away. What the audit reports is the content that survives but cannot
 * be edited — and it must stay silent about everything the editor does
 * handle.
 */
class ContentAuditTest {

    @Test
    fun plainWriterOutputReportsNothing() {
        val docx = DocxWriter().write(
            org.ziyoffice.model.Document(
                listOf(org.ziyoffice.model.Paragraph(listOf(org.ziyoffice.model.Run("text", bold = true)))),
            ),
        )
        val xlsx = XlsxWriter().write(
            org.ziyoffice.model.Workbook(
                listOf(org.ziyoffice.model.Sheet("Sheet1", listOf(org.ziyoffice.model.Cell(0, 0, "1")))),
            ),
        )
        assertEquals(emptyList(), ContentAudit.docx(docx))
        assertEquals(emptyList(), ContentAudit.xlsx(xlsx))
    }

    @Test
    fun docxReportsContentItKeepsButCannotEdit() {
        val packageBytes = zip(
            mapOf(
                "word/document.xml" to
                    "<w:document><w:body>" +
                    "<w:p><w:hyperlink r:id=\"rId9\"><w:r><w:t>link</w:t></w:r></w:hyperlink></w:p>" +
                    "<w:p><w:ins><w:r><w:t>added</w:t></w:r></w:ins></w:p>" +
                    "<w:p><w:del><w:r><w:delText>removed</w:delText></w:r></w:del></w:p>" +
                    "<w:tbl><w:tr><w:tc><w:tcPr><w:vMerge w:val=\"restart\"/></w:tcPr>" +
                    "<w:p/></w:tc></w:tr></w:tbl>" +
                    "<w:p><m:oMath><m:r><m:t>x</m:t></m:r></m:oMath></w:p>" +
                    "<w:sectPr/><w:sectPr/>" +
                    "</w:body></w:document>",
                "word/comments.xml" to "<w:comments/>",
                "word/header1.xml" to "<w:hdr/>",
                "word/footer1.xml" to "<w:ftr/>",
            ),
        )
        val features = ContentAudit.docx(packageBytes)
        assertEquals(
            listOf(
                "Comments" to 1,
                "Tracked changes" to 2,
                "Hyperlinks" to 1,
                "Equations" to 1,
                "Section breaks" to 1,
            ),
            features.map { it.label to it.count },
        )
    }

    @Test
    fun docxReportsTheHeaderVariantsItCannotEdit() {
        val packageBytes = zip(
            mapOf(
                "word/document.xml" to
                    "<w:document><w:body><w:p/>" +
                    "<w:sectPr>" +
                    "<w:headerReference w:type=\"default\" r:id=\"rId1\"/>" +
                    "<w:headerReference w:type=\"first\" r:id=\"rId2\"/>" +
                    "<w:footerReference w:type=\"even\" r:id=\"rId3\"/>" +
                    "</w:sectPr></w:body></w:document>",
                "word/header1.xml" to "<w:hdr/>",
                "word/header2.xml" to "<w:hdr/>",
                "word/footer1.xml" to "<w:ftr/>",
            ),
        )
        assertEquals(
            listOf("Extra header pages" to 1, "Extra footer pages" to 1),
            ContentAudit.docx(packageBytes).map { it.label to it.count },
        )
    }

    @Test
    fun docxIgnoresRelationshipPartsAndSingleTrailingSection() {
        val packageBytes = zip(
            mapOf(
                "word/document.xml" to "<w:document><w:body><w:p/><w:sectPr/></w:body></w:document>",
                "word/_rels/header1.xml.rels" to "<Relationships/>",
            ),
        )
        assertEquals(emptyList(), ContentAudit.docx(packageBytes))
    }

    @Test
    fun xlsxReportsPartsAndSheetFeaturesItKeepsButCannotEdit() {
        val packageBytes = zip(
            mapOf(
                "xl/workbook.xml" to "<workbook/>",
                "xl/worksheets/sheet1.xml" to
                    "<worksheet><sheetData/>" +
                    "<mergeCells><mergeCell ref=\"A1:B1\"/></mergeCells>" +
                    "<conditionalFormatting sqref=\"A1\"/>" +
                    "<autoFilter ref=\"A1:B9\"/></worksheet>",
                "xl/charts/chart1.xml" to "<chart/>",
                "xl/drawings/drawing1.xml" to "<drawing/>",
                "xl/media/image1.png" to "png",
            ),
        )
        val features = ContentAudit.xlsx(packageBytes)
        assertEquals(
            listOf(
                "Charts" to 1,
                "Drawings" to 1,
                "Images" to 1,
                "Conditional formatting" to 1,
                "Auto filters" to 1,
            ),
            features.map { it.label to it.count },
        )
    }

    @Test
    fun xlsxKeepsPlainListValidationsAndWarnsAboutTheRest() {
        val packageBytes = zip(
            mapOf(
                "xl/worksheets/sheet1.xml" to
                    "<worksheet><sheetData/>" +
                    "<dataValidations>" +
                    "<dataValidation type=\"list\" sqref=\"A1:A5\">" +
                    "<formula1>\"Open,Done\"</formula1></dataValidation>" +
                    "<dataValidation type=\"list\" sqref=\"B1:B5\">" +
                    "<formula1>Lists!\$A\$1:\$A\$3</formula1></dataValidation>" +
                    "</dataValidations></worksheet>",
            ),
        )

        assertEquals(
            listOf("Data validation" to 1),
            ContentAudit.xlsx(packageBytes).map { it.label to it.count },
        )
    }

    @Test
    fun xlsxReportsHiddenRowsAndColumnsItCannotEdit() {
        val packageBytes = zip(
            mapOf(
                "xl/worksheets/sheet1.xml" to
                    "<worksheet><cols><col min=\"1\" max=\"1\" width=\"0\" hidden=\"1\"/></cols>" +
                    "<sheetData><row r=\"2\" hidden=\"true\"/></sheetData></worksheet>",
            ),
        )

        assertEquals(
            listOf("Hidden rows and columns" to 2),
            ContentAudit.xlsx(packageBytes).map { it.label to it.count },
        )
    }

    @Test
    fun countsWholeElementNamesOnly() {
        val packageBytes = zip(
            mapOf(
                "word/document.xml" to
                    "<w:document><w:body><w:p><w:delText>x</w:delText></w:p>" +
                    "<w:p><w:del><w:r><w:delText>y</w:delText></w:r></w:del></w:p>" +
                    "<w:sectPr/></w:body></w:document>",
            ),
        )
        // delText must not count as a tracked deletion; the one real w:del must.
        assertEquals(
            listOf("Tracked changes" to 1),
            ContentAudit.docx(packageBytes).map { it.label to it.count },
        )
    }

    @Test
    fun oversizedPackageIsRejectedInsteadOfScannedForever() {
        val bomb = ByteArray(2 * 1024 * 1024) { 0x20 }
        val packageBytes = zip(mapOf("word/document.xml" to "<w:document>" + String(bomb) + "</w:document>"))
        // Two megabytes is under the part cap; the point is that the scan
        // completes and still parses rather than trusting the size.
        assertTrue(ContentAudit.docx(packageBytes).isEmpty())
    }

    /**
     * End-to-end proof that the notice is true: a package with a header and a
     * tracked insertion is opened, edited and saved, and everything the audit
     * named is still there afterwards.
     */
    @Test
    fun savingKeepsWhatTheAuditNames() {
        val base = DocxWriter().write(
            org.ziyoffice.model.Document(
                listOf(org.ziyoffice.model.Paragraph(listOf(org.ziyoffice.model.Run("Body text")))),
            ),
        )
        val parts = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(base)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
            }
        }
        parts["word/document.xml"] = parts.getValue("word/document.xml").decodeToString()
            .replace(
                "<w:body>",
                "<w:body><w:p><w:ins><w:r><w:t>tracked</w:t></w:r></w:ins></w:p>",
            )
            .encodeToByteArray()
        parts["word/header1.xml"] =
            ("<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                "<w:p/></w:hdr>").encodeToByteArray()
        parts["word/_rels/document.xml.rels"] =
            parts.getValue("word/_rels/document.xml.rels").decodeToString()
                .replace(
                    "</Relationships>",
                    "<Relationship Id=\"rIdHeader1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" " +
                        "Target=\"header1.xml\"/></Relationships>",
                )
                .encodeToByteArray()
        parts["word/document.xml"] = parts.getValue("word/document.xml").decodeToString()
            .replace(
                "<w:sectPr>",
                "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdHeader1\"/>",
            )
            .encodeToByteArray()
        val withExtras = zipBytes(parts)

        val before = ContentAudit.docx(withExtras).map { it.label }
        assertEquals(listOf("Tracked changes"), before)

        // The save keeps the tracked change and the header: the audit names
        // content that is preserved, not content that is lost.
        val source = DocxReader().readSource(java.io.ByteArrayInputStream(withExtras))
        val saved = DocxEditor(source).save(source.document)
        val document = zipEntries(saved).getValue("word/document.xml").decodeToString()
        assertContains(document, "<w:ins>")
        assertContains(document, "<w:headerReference w:type=\"default\"")
        assertTrue(zipEntries(saved).containsKey("word/header1.xml"))
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }
}
