package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.applyText

/**
 * Content controls, which is how Word writes a cover page, a table of
 * contents or anything that came from a template.
 *
 * The reader used to walk straight past them, so the whole document looked
 * empty. Now the content inside is read, and a save keeps the control around
 * it.
 */
class DocxContentControlTest {

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
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"

    private val rootRels =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
            "Target=\"word/document.xml\"/></Relationships>"

    private fun document(body: String): ByteArray = zipOf(
        mapOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rootRels,
            "word/document.xml" to
                "<w:document xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:body>$body</w:body></w:document>",
        ),
    )

    @Test
    fun theTextInsideAContentControlIsRead() {
        val bytes = document(
            "<w:sdt><w:sdtPr><w:alias w:val=\"Title\"/></w:sdtPr>" +
                "<w:sdtContent><w:p><w:r><w:t>Inside the control</w:t></w:r></w:p>" +
                "</w:sdtContent></w:sdt>",
        )

        val paragraphs = reader.read(ByteArrayInputStream(bytes)).blocks.filterIsInstance<Paragraph>()

        assertEquals(1, paragraphs.size)
        assertEquals("Inside the control", paragraphs.first().runs.joinToString("") { it.text })
    }

    @Test
    fun aContentControlNextToAParagraphKeepsBoth() {
        val bytes = document(
            "<w:p><w:r><w:t>Before</w:t></w:r></w:p>" +
                "<w:sdt><w:sdtContent><w:p><w:r><w:t>Inside</w:t></w:r></w:p></w:sdtContent></w:sdt>" +
                "<w:p><w:r><w:t>After</w:t></w:r></w:p>",
        )

        val paragraphs = reader.read(ByteArrayInputStream(bytes)).blocks.filterIsInstance<Paragraph>()

        assertEquals(
            listOf("Before", "Inside", "After"),
            paragraphs.map { it.runs.joinToString("") { run -> run.text } },
        )
    }

    @Test
    fun aSaveKeepsTheControlAroundTheContent() {
        val bytes = document(
            "<w:sdt><w:sdtPr><w:alias w:val=\"Title\"/></w:sdtPr>" +
                "<w:sdtContent><w:p><w:r><w:t>Inside</w:t></w:r></w:p>" +
                "</w:sdtContent></w:sdt>",
        )

        val source = DocxSource.capture(bytes)
        val edited = source.document.copy(
            blocks = source.document.blocks.map { block ->
                if (block is Paragraph) block.applyText("Inside, edited") else block
            },
        )
        val saved = DocxEditor(source).save(edited)
        val xml = partsOf(saved).getValue("word/document.xml")

        assertTrue(xml.contains("<w:sdt>"), "the content control must survive a save")
        assertTrue(xml.contains("Inside, edited"))

        val reread = reader.read(ByteArrayInputStream(saved)).blocks.filterIsInstance<Paragraph>()
        assertEquals(listOf("Inside, edited"), reread.map { it.runs.joinToString("") { r -> r.text } })
    }
}
