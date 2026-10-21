package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import org.ziyoffice.model.Document
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.Workbook

/**
 * What ZiyOffice writes about your documents.
 *
 * A document ZiyOffice *creates* carries no author, company, edit history or
 * application metadata at all: the writers never emit a `docProps/` part, so
 * there is nothing to leak. This test is the receipt.
 *
 * A document ZiyOffice *edits* keeps the metadata it came with. That is the
 * opposite of a leak (it is the author's own file, unchanged) and the
 * alternative would quietly rewrite somebody's document properties. What
 * matters is that ZiyOffice adds none of its own, which the second test pins
 * down.
 */
class MetadataScrubTest {

    @Test
    fun writtenPackagesContainNoDocumentProperties() {
        val docx = DocxWriter().write(Document(listOf(Paragraph(listOf(Run("text"))))))
        val xlsx = XlsxWriter().write(Workbook(listOf(Sheet("Sheet1", emptyList()))))
        val pptx = PptxWriter().create(SlideDeck.blank())

        listOf("docx" to docx, "xlsx" to xlsx, "pptx" to pptx).forEach { (name, bytes) ->
            val parts = entryNames(bytes)
            assertFalse(parts.any { it.startsWith("docProps/") }, "$name kept docProps")
            assertFalse(parts.any { it.contains("core.xml") }, "$name kept core.xml")
            assertFalse(parts.any { it.contains("app.xml") }, "$name kept app.xml")
        }
    }

    @Test
    fun editingADocumentAddsNoMetadataOfItsOwn() {
        val original = Document(listOf(Paragraph(listOf(Run("text")))))
        val bytes = DocxWriter().write(original)
        val source = DocxReader().readSource(java.io.ByteArrayInputStream(bytes))
        val saved = DocxEditor(source).save(source.document)

        assertFalse(entryNames(saved).any { it.startsWith("docProps/") })
    }

    private fun entryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) names += (zip.nextEntry?.name ?: break)
        }
        return names
    }
}
