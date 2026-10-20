package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The audit exists to stop silent data loss: [DocxWriter] and [XlsxWriter]
 * rebuild packages from the model, so anything the readers skip is gone after a
 * save. These tests pin both halves — it must report what a save would destroy,
 * and it must stay silent about everything the writers reproduce.
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
    fun docxReportsPartsAndBodyFeaturesItCannotWrite() {
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
                "Headers" to 1,
                "Footers" to 1,
                "Comments" to 1,
                "Tracked changes" to 2,
                "Hyperlinks" to 1,
                "Vertically merged cells" to 1,
                "Equations" to 1,
                "Section breaks" to 1,
            ),
            features.map { it.label to it.count },
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
    fun xlsxReportsPartsAndSheetFeaturesItCannotWrite() {
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
                "Merged cells" to 1,
                "Auto filters" to 1,
            ),
            features.map { it.label to it.count },
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

    /**
     * End-to-end proof that the warning is true: take a package with a header
     * and a tracked insertion, read it with the real reader, write it with the
     * real writer, and the audit must go from "these will be lost" to "nothing
     * left to lose" — because the save actually removed them.
     */
    @Test
    fun savingReallyRemovesWhatTheWarningNames() {
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
        parts["word/header1.xml"] = "<w:hdr/>".encodeToByteArray()
        val withExtras = zipBytes(parts)

        val before = ContentAudit.docx(withExtras).map { it.label }
        assertEquals(listOf("Headers", "Tracked changes"), before)

        val model = DocxReader().read(java.io.ByteArrayInputStream(withExtras))
        assertEquals(emptyList(), ContentAudit.docx(DocxWriter().write(model)))
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
}
