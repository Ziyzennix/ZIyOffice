package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.ziyoffice.model.Document
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run
import org.ziyoffice.model.withPageNumberInFooter

/**
 * A page number in the footer of a document.
 */
class DocxPageNumberTest {

    private fun packageWith(body: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                    "</Types>").toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                    "</Relationships>").toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(body.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** A document with one paragraph and a footer holding a page number. */
    private fun documentWithFooter(): ByteArray = packageWith(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>" +
            "<w:p><w:r><w:t>Hello</w:t></w:r></w:p>" +
            "<w:sectPr>" +
            "<w:footerReference w:type=\"default\" r:id=\"rIdF\"/>" +
            "</w:sectPr>" +
            "</w:body></w:document>",
    )

    @Test
    fun aPageNumberGoesInTheFooter() {
        val document = Document(blocks = listOf(Paragraph(runs = listOf(Run("Hello")))))
        val withNumber = document.withPageNumberInFooter()
        val footer = assertNotNull(withNumber.footer)
        val field = (footer.last() as Paragraph).runs.single()
        assertEquals("PAGE", field.field)
    }

    @Test
    fun aFooterThatAlreadyHasANumberIsLeftAlone() {
        val document = Document(blocks = listOf(Paragraph(runs = listOf(Run("Hello")))))
        val once = document.withPageNumberInFooter()
        val twice = once.withPageNumberInFooter()
        assertEquals(once.footer?.size, twice.footer?.size)
    }

    @Test
    fun aPageNumberIsWrittenAsAField() {
        val document = Document(blocks = listOf(Paragraph(runs = listOf(Run("Hello")))))
        val bytes = DocxWriter().write(document.withPageNumberInFooter())
        val footer = partOf(bytes, "word/footer1.xml")
        assertNotNull(footer, "the footer should be there")
        assertTrue(footer.contains("fldSimple"), "the number should be a field: $footer")
        assertTrue(footer.contains("PAGE"), "the field should be the page number: $footer")
    }

    @Test
    fun aPageNumberComesBackAsAField() {
        val bytes = DocxWriter().write(
            Document(blocks = listOf(Paragraph(runs = listOf(Run("Hello"))))).withPageNumberInFooter(),
        )
        val read = DocxReader().read(ByteArrayInputStream(bytes))
        val footer = assertNotNull(read.footer)
        val field = (footer.last() as Paragraph).runs.single()
        assertEquals("PAGE", field.field)
    }

    private fun partOf(bytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().decodeToString()
            }
        }
        return null
    }
}
