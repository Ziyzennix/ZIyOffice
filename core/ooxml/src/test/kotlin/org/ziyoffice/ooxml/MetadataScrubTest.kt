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
 * The writers rebuild every package from the model, so author, company, edit
 * history and application metadata are absent by construction. This test is the
 * receipt: no docProps part may appear in any file we write.
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

    private fun entryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) names += (zip.nextEntry?.name ?: break)
        }
        return names
    }
}
