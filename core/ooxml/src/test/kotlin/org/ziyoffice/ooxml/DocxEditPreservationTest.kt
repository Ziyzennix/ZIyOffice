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
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.applyText

/**
 * What happens to the parts of a paragraph the user did not touch.
 *
 * The editor used to replace a paragraph's whole run list with one run, so a
 * single keystroke emptied every run after the first: the bold half of a
 * sentence went plain, a hyperlink kept its wrapper but lost its text, a
 * comment anchor was left spanning nothing and a tracked insertion was thrown
 * away. These tests pin down the other behaviour.
 */
class DocxEditPreservationTest {

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

    private val documentRels =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId5\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" " +
            "Target=\"https://example.com\" TargetMode=\"External\"/></Relationships>"

    private fun documentXml(body: String): String =
        "<w:document xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<w:body>$body</w:body></w:document>"

    /** A document with one paragraph, plus whatever parts it needs. */
    private fun documentWith(body: String): ByteArray = zipOf(
        mapOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rootRels,
            "word/document.xml" to documentXml(body),
            "word/_rels/document.xml.rels" to documentRels,
        ),
    )

    /** Types the user's text over the paragraph and saves it. */
    private fun typeInto(original: ByteArray, newText: String): ByteArray {
        val document = reader.read(ByteArrayInputStream(original))
        val edited = document.copy(
            blocks = document.blocks.map { block ->
                if (block is Paragraph) block.applyText(newText) else block
            },
        )
        return DocxEditor(DocxSource.capture(original)).save(edited)
    }

    private fun bodyOf(saved: ByteArray): String =
        partsOf(saved).getValue("word/document.xml")
            .substringAfter("<w:body>").substringBefore("</w:body>")

    private fun textOf(saved: ByteArray): String =
        reader.read(ByteArrayInputStream(saved)).blocks.filterIsInstance<Paragraph>()
            .joinToString("") { it.runs.joinToString("") { run -> run.text } }

    @Test
    fun boldOnALaterRunSurvivesAnEdit() {
        val original = documentWith(
            "<w:p><w:r><w:t>Hello </w:t></w:r>" +
                "<w:r><w:rPr><w:b/></w:rPr><w:t>world</w:t></w:r></w:p>",
        )

        val saved = typeInto(original, "Hello world, edited")

        val after = reader.read(ByteArrayInputStream(saved)).blocks.filterIsInstance<Paragraph>().first()
        assertEquals("Hello world, edited", after.runs.joinToString("") { it.text })
        assertTrue(
            after.runs.any { it.bold && it.text.contains("world") },
            "the bold run must still be bold: ${after.runs}",
        )
    }

    @Test
    fun aHyperlinkKeepsItsTextAndItsTarget() {
        val original = documentWith(
            "<w:p><w:r><w:t>See </w:t></w:r>" +
                "<w:hyperlink r:id=\"rId5\"><w:r><w:t>A link</w:t></w:r></w:hyperlink></w:p>",
        )

        val saved = typeInto(original, "See A link now")

        val body = bodyOf(saved)
        assertContains(body, "<w:hyperlink r:id=\"rId5\">")
        assertContains(body, "A link")
        assertEquals("See A link now", textOf(saved))
    }

    @Test
    fun aCommentAnchorKeepsItsText() {
        val original = documentWith(
            "<w:p><w:r><w:t>Before </w:t></w:r>" +
                "<w:commentRangeStart w:id=\"0\"/><w:r><w:t>Inside</w:t></w:r>" +
                "<w:commentRangeEnd w:id=\"0\"/><w:r><w:commentReference w:id=\"0\"/></w:r></w:p>",
        )

        val saved = typeInto(original, "Before Inside and after")

        val body = bodyOf(saved)
        assertContains(body, "<w:commentRangeStart w:id=\"0\"/>")
        assertContains(body, "<w:commentRangeEnd w:id=\"0\"/>")
        assertEquals("Before Inside and after", textOf(saved))
    }

    @Test
    fun aTrackedInsertionKeepsItsText() {
        val original = documentWith(
            "<w:p><w:r><w:t>Before </w:t></w:r>" +
                "<w:ins w:id=\"9\" w:author=\"Ada\"><w:r><w:t>Inserted</w:t></w:r></w:ins></w:p>",
        )

        val saved = typeInto(original, "Before Inserted")

        val body = bodyOf(saved)
        assertContains(body, "<w:ins w:id=\"9\" w:author=\"Ada\">")
        assertContains(body, "Inserted")
    }

    @Test
    fun typingAtTheEndKeepsTheParagraphInOneRun() {
        val original = documentWith("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>")

        val saved = typeInto(original, "Hello!")

        assertEquals("Hello!", textOf(saved))
        assertEquals(1, bodyOf(saved).split("<w:r>").size - 1)
    }

    @Test
    fun textInsertedInTheMiddleLandsInTheRightPlace() {
        val original = documentWith("<w:p><w:r><w:t>Hello world</w:t></w:r></w:p>")

        val saved = typeInto(original, "Hello brave world")

        assertEquals("Hello brave world", textOf(saved))
    }

    @Test
    fun deletingTextRemovesItAndKeepsTheRest() {
        val original = documentWith(
            "<w:p><w:r><w:t>Hello </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>world</w:t></w:r></w:p>",
        )

        val saved = typeInto(original, "Hello ")

        assertEquals("Hello ", textOf(saved))
    }
}
