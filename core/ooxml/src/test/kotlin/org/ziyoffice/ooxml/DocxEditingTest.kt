package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.ziyoffice.model.Document
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run

/**
 * The awkward cases of editing a paragraph in place.
 *
 * A paragraph is not just a run of text: it can hold a hyperlink, a tab, a
 * line break, characters that have to be escaped and spaces that have to be
 * kept. A writer that rebuilds it from the model loses those; a writer that
 * patches it has to keep them.
 */
class DocxEditingTest {

    private val reader = DocxReader()

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

    private fun documentXml(body: String): String =
        "<w:document xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<w:body>$body</w:body></w:document>"

    private fun packageOf(body: String): ByteArray = zipOf(
        mapOf(
            "word/document.xml" to documentXml(body),
        ),
    )

    private fun save(original: ByteArray, document: Document): ByteArray {
        val source = reader.readSource(ByteArrayInputStream(original))
        return DocxEditor(source).save(document)
    }

    private fun documentXmlOf(bytes: ByteArray): String {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    out.write(zip.readBytes())
                }
                zip.closeEntry()
            }
        }
        return out.toByteArray().decodeToString()
    }

    /** Replaces the text of the first paragraph. */
    private fun Document.replaceFirstParagraph(text: String): Document = copy(
        blocks = blocks.mapIndexed { index, block ->
            if (index == 0 && block is Paragraph) block.copy(runs = listOf(Run(text))) else block
        },
    )

    @Test
    fun aHyperlinkKeepsItsWrapperWhenItsTextIsEdited() {
        val original = packageOf(
            "<w:p><w:hyperlink r:id=\"rId5\"><w:r><w:t>old</w:t></w:r></w:hyperlink></w:p>",
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceFirstParagraph("new"))
        val xml = documentXmlOf(saved)

        assertContains(xml, "<w:hyperlink r:id=\"rId5\">")
        assertContains(xml, "new")
        assertFalse(xml.contains(">old<"))
    }

    @Test
    fun aTabAndALineBreakAreWrittenWhenTheTextKeepsThem() {
        val original = packageOf(
            "<w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t><w:br/><w:t>c</w:t></w:r></w:p>",
        )
        val source = reader.readSource(ByteArrayInputStream(original))
        val paragraph = source.document.blocks[0] as Paragraph
        val edited = source.document.copy(
            blocks = listOf(paragraph.copy(runs = listOf(Run("x\ty\nz")))),
        )

        val saved = DocxEditor(source).save(edited)
        val xml = documentXmlOf(saved)

        assertContains(xml, "<w:tab/>")
        assertContains(xml, "<w:br/>")
        val reread = reader.read(ByteArrayInputStream(saved))
        assertEquals("x\ty\nz", (reread.blocks[0] as Paragraph).runs.single().text)
    }

    @Test
    fun aTabIsRemovedWhenTheTextNoLongerHasIt() {
        val original = packageOf(
            "<w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t></w:r></w:p>",
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceFirstParagraph("plain"))
        val xml = documentXmlOf(saved)

        assertFalse(xml.contains("<w:tab/>"), "the tab was part of the text the user replaced")
        assertContains(xml, "plain")
    }

    @Test
    fun specialCharactersAreEscaped() {
        val original = packageOf("<w:p><w:r><w:t>old</w:t></w:r></w:p>")
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceFirstParagraph("a & b < c > d"))
        val xml = documentXmlOf(saved)

        assertContains(xml, "a &amp; b &lt; c &gt; d")
        // The file must still parse.
        val reread = reader.read(ByteArrayInputStream(saved))
        assertEquals("a & b < c > d", (reread.blocks[0] as Paragraph).runs.single().text)
    }

    @Test
    fun leadingAndTrailingSpacesAreKept() {
        val original = packageOf("<w:p><w:r><w:t>old</w:t></w:r></w:p>")
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceFirstParagraph("  spaced  "))
        val xml = documentXmlOf(saved)

        assertContains(xml, "xml:space=\"preserve\"")
        val reread = reader.read(ByteArrayInputStream(saved))
        assertEquals("  spaced  ", (reread.blocks[0] as Paragraph).runs.single().text)
    }

    @Test
    fun aParagraphInsideATableCellCanBeEdited() {
        val original = packageOf(
            "<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\"/></w:tblBorders></w:tblPr>" +
                "<w:tblGrid><w:gridCol/></w:tblGrid>" +
                "<w:tr><w:tc><w:p><w:r><w:t>old</w:t></w:r></w:p></w:tc></w:tr></w:tbl>",
        )
        val source = reader.readSource(ByteArrayInputStream(original))
        val table = source.document.blocks[0] as org.ziyoffice.model.Table
        val edited = source.document.copy(
            blocks = listOf(
                table.copy(
                    rows = table.rows.map { row ->
                        row.copy(
                            cells = row.cells.map { cell ->
                                cell.copy(
                                    blocks = listOf(
                                        Paragraph(runs = listOf(Run("new"))),
                                    ),
                                )
                            },
                        )
                    },
                ),
            ),
        )

        val saved = DocxEditor(source).save(edited)
        val xml = documentXmlOf(saved)

        assertContains(xml, "new")
        assertContains(xml, "<w:top w:val=\"single\"/>")
        assertContains(xml, "<w:gridCol/>")
    }

    @Test
    fun anEmptyParagraphStaysAParagraph() {
        val original = packageOf("<w:p><w:r><w:t>old</w:t></w:r></w:p>")
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceFirstParagraph(""))
        val xml = documentXmlOf(saved)

        assertContains(xml, "<w:p")
        val reread = reader.read(ByteArrayInputStream(saved))
        assertEquals("", (reread.blocks[0] as Paragraph).runs.joinToString("") { it.text })
    }

    @Test
    fun aRunThatKeepsItsFormattingDoesNotGainARedundantProperty() {
        val original = packageOf("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>old</w:t></w:r></w:p>")
        val source = reader.readSource(ByteArrayInputStream(original))
        val paragraph = source.document.blocks[0] as Paragraph
        // The text changes; the bold stays.
        val edited = source.document.copy(
            blocks = listOf(paragraph.copy(runs = paragraph.runs.map { it.copy(text = "new") })),
        )

        val saved = DocxEditor(source).save(edited)
        val xml = documentXmlOf(saved)

        // The bold was already there and still applies; it is not written twice.
        assertEquals(1, Regex("<w:b/>").findAll(xml).count())
        assertContains(xml, "new")
    }

    @Test
    fun aRunThatLosesItsFormattingSaysSo() {
        val original = packageOf("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>old</w:t></w:r></w:p>")
        val source = reader.readSource(ByteArrayInputStream(original))
        val paragraph = source.document.blocks[0] as Paragraph
        val edited = source.document.copy(
            blocks = listOf(paragraph.copy(runs = paragraph.runs.map { it.copy(bold = false) })),
        )

        val saved = DocxEditor(source).save(edited)
        val xml = documentXmlOf(saved)

        assertContains(xml, "<w:b w:val=\"0\"/>")
        val reread = reader.read(ByteArrayInputStream(saved))
        assertFalse((reread.blocks[0] as Paragraph).runs.single().bold)
    }

    @Test
    fun aParagraphWithSeveralRunsKeepsThemApart() {
        val original = packageOf(
            "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>one</w:t></w:r>" +
                "<w:r><w:t>two</w:t></w:r></w:p>",
        )
        val source = reader.readSource(ByteArrayInputStream(original))
        val paragraph = source.document.blocks[0] as Paragraph
        val edited = source.document.copy(
            blocks = listOf(
                paragraph.copy(
                    runs = listOf(Run("ONE", bold = true), Run("two")),
                ),
            ),
        )

        val saved = DocxEditor(source).save(edited)
        val reread = reader.read(ByteArrayInputStream(saved))
        val runs = (reread.blocks[0] as Paragraph).runs

        assertEquals(2, runs.size)
        assertEquals("ONE", runs[0].text)
        assertTrue(runs[0].bold)
        assertEquals("two", runs[1].text)
        assertFalse(runs[1].bold)
    }

    @Test
    fun aPageBreakSurvivesAnEditOfTheSameParagraph() {
        val original = packageOf(
            "<w:p><w:r><w:t>first</w:t></w:r></w:p>" +
                "<w:p><w:r><w:t>second</w:t></w:r></w:p>",
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceFirstParagraph("changed"))
        val xml = documentXmlOf(saved)

        assertContains(xml, "changed")
        assertContains(xml, "<w:p")
        assertTrue(Regex("<w:p").findAll(xml).count() >= 2)
    }
}
