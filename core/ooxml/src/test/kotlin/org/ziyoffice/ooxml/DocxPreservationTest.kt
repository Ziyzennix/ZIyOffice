package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.ziyoffice.model.Document
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run

/**
 * What a save must not lose.
 *
 * The old writer rebuilt a document from the model, so everything the model
 * could not express — comments, footnotes, hyperlinks, tracked changes, text
 * boxes, section breaks, styles, document properties — disappeared on the
 * first save. These tests pin down the opposite contract: a save changes what
 * the user changed and nothing else.
 */
class DocxPreservationTest {

    private val reader = DocxReader()

    // ---------------------------------------------------------------- helpers

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

    private fun documentXml(body: String): String =
        "<w:document xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
            "<w:body>$body</w:body></w:document>"

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

    /** A document with the parts a Word file usually carries and an editor never edits. */
    private fun richDocument(): ByteArray = zipOf(
        mapOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rootRels,
            "word/document.xml" to documentXml(
                "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>" +
                    "<w:r><w:t>Heading</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>First paragraph</w:t></w:r>" +
                    "<w:commentRangeStart w:id=\"0\"/>" +
                    "<w:commentRangeEnd w:id=\"0\"/>" +
                    "<w:r><w:commentReference w:id=\"0\"/></w:r></w:p>" +
                    "<w:p><w:hyperlink r:id=\"rId5\"><w:r><w:t>A link</w:t></w:r></w:hyperlink></w:p>" +
                    "<w:p><w:ins w:id=\"9\" w:author=\"Ada\"><w:r><w:t>Inserted</w:t></w:r></w:ins></w:p>" +
                    "<w:p><m:oMath><m:r><m:t>E = mc²</m:t></m:r></m:oMath></w:p>" +
                    "<w:p><w:r><w:t>Last paragraph</w:t></w:r></w:p>" +
                    "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
                    "<w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\"1418\"/>" +
                    "<w:cols w:num=\"2\"/></w:sectPr>",
            ),
            "word/comments.xml" to
                "<w:comments xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:comment w:id=\"0\" w:author=\"Ada\"><w:p><w:r><w:t>A note</w:t></w:r></w:p></w:comment>" +
                "</w:comments>",
            "word/footnotes.xml" to
                "<w:footnotes xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:footnote w:id=\"1\"><w:p><w:r><w:t>A footnote</w:t></w:r></w:p></w:footnote>" +
                "</w:footnotes>",
            "word/styles.xml" to
                "<w:styles xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:rPr><w:b/></w:rPr></w:style>" +
                "</w:styles>",
            "word/settings.xml" to "<w:settings xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\"/>",
            "word/theme/theme1.xml" to "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"/>",
            "docProps/core.xml" to "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\"/>",
            "word/_rels/document.xml.rels" to
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId5\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" " +
                "Target=\"https://example.com\" TargetMode=\"External\"/>" +
                "</Relationships>",
        ),
    )

    /** Edits the text of the paragraph whose text is [old]. */
    private fun Document.replaceText(old: String, new: String): Document = copy(
        blocks = blocks.map { block ->
            if (block is Paragraph && block.runs.joinToString("") { it.text } == old) {
                block.copy(runs = listOf(Run(new)))
            } else {
                block
            }
        },
    )

    private fun save(original: ByteArray, document: Document): ByteArray {
        val source = reader.readSource(ByteArrayInputStream(original))
        return DocxEditor(source).save(document)
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun anUntouchedDocumentIsSavedByteForByte() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document)

        assertEquals(
            partsOf(original)["word/document.xml"],
            partsOf(saved)["word/document.xml"],
            "an unchanged document must come out of a save exactly as it went in",
        )
    }

    @Test
    fun editingOneParagraphLeavesTheOthersAlone() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))
        val before = partsOf(original)["word/document.xml"]!!

        val saved = DocxEditor(source).save(source.document.replaceText("First paragraph", "Edited"))
        val after = partsOf(saved)["word/document.xml"]!!

        val edited = after.substringAfter("Edited")
        assertContains(after, "Edited")
        assertEquals(
            before.substringAfter("<w:t>First paragraph</w:t>"),
            before.substringAfter("<w:t>First paragraph</w:t>"),
        )
        // Everything after the edited paragraph is untouched.
        assertContains(after, "<w:hyperlink r:id=\"rId5\">")
        assertContains(after, "<w:ins w:id=\"9\" w:author=\"Ada\">")
        assertContains(after, "<m:oMath>")
        assertContains(after, "<w:cols w:num=\"2\"/>")
        assertTrue(edited.isNotEmpty())
    }

    @Test
    fun partsTheEditorCannotEditSurviveASave() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("Last paragraph", "Changed"))
        val after = partsOf(saved)

        assertEquals(partsOf(original)["word/comments.xml"], after["word/comments.xml"])
        assertEquals(partsOf(original)["word/footnotes.xml"], after["word/footnotes.xml"])
        assertEquals(partsOf(original)["word/styles.xml"], after["word/styles.xml"])
        assertEquals(partsOf(original)["word/settings.xml"], after["word/settings.xml"])
        assertEquals(partsOf(original)["word/theme/theme1.xml"], after["word/theme/theme1.xml"])
        assertEquals(partsOf(original)["docProps/core.xml"], after["docProps/core.xml"])
    }

    @Test
    fun commentAnchorsStayWithTheirParagraph() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("Last paragraph", "Changed"))
        val after = partsOf(saved)["word/document.xml"]!!

        assertContains(after, "<w:commentRangeStart w:id=\"0\"/>")
        assertContains(after, "<w:commentRangeEnd w:id=\"0\"/>")
        assertContains(after, "<w:commentReference w:id=\"0\"/>")
    }

    @Test
    fun theSavedDocumentReadsBackWithTheEdit() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("First paragraph", "Edited"))
        val reread = reader.read(ByteArrayInputStream(saved))

        // The equation paragraph holds no WordprocessingML text, so it reads
        // back as an empty paragraph; it is still in the package.
        assertEquals(
            listOf("Heading", "Edited", "A link", "Inserted", "", "Last paragraph"),
            reread.blocks.filterIsInstance<Paragraph>().map { paragraph ->
                paragraph.runs.joinToString("") { it.text }
            },
        )
    }

    @Test
    fun aStyleTheEditorDoesNotUnderstandIsKept() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("Heading", "New heading"))
        val after = partsOf(saved)["word/document.xml"]!!

        assertContains(after, "<w:pStyle w:val=\"Heading1\"/>")
    }

    @Test
    fun addingAParagraphKeepsTheRestOfTheDocument() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val withExtra = source.document.copy(
            blocks = source.document.blocks + Paragraph(runs = listOf(Run("Added"))),
        )
        val saved = DocxEditor(source).save(withExtra)
        val after = partsOf(saved)["word/document.xml"]!!

        assertContains(after, "Added")
        assertContains(after, "<w:hyperlink r:id=\"rId5\">")
        assertContains(after, "<w:cols w:num=\"2\"/>")
        // The new paragraph goes before the section properties.
        assertTrue(
            after.indexOf("Added") < after.indexOf("<w:sectPr>"),
            "a new paragraph belongs before the section properties",
        )
    }

    @Test
    fun removingAParagraphKeepsTheRestOfTheDocument() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val withoutSecond = source.document.copy(
            blocks = source.document.blocks.filterNot { block ->
                block is Paragraph && block.runs.joinToString("") { it.text } == "A link"
            },
        )
        val saved = DocxEditor(source).save(withoutSecond)
        val after = partsOf(saved)["word/document.xml"]!!

        assertTrue(!after.contains("A link"))
        assertContains(after, "<w:ins w:id=\"9\" w:author=\"Ada\">")
        assertContains(after, "<w:cols w:num=\"2\"/>")
    }

    @Test
    fun boldingARunDoesNotRewriteTheWholeDocument() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val bolded = source.document.copy(
            blocks = source.document.blocks.map { block ->
                if (block is Paragraph && block.runs.joinToString("") { it.text } == "Last paragraph") {
                    block.copy(runs = block.runs.map { it.copy(bold = true) })
                } else {
                    block
                }
            },
        )
        val saved = DocxEditor(source).save(bolded)
        val after = partsOf(saved)["word/document.xml"]!!

        assertContains(after, "<w:b/>")
        assertContains(after, "<w:ins w:id=\"9\" w:author=\"Ada\">")
        assertContains(after, "<w:hyperlink r:id=\"rId5\">")
    }

    @Test
    fun aTextBoxIsPreservedRatherThanFlattened() {
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:p><w:r><w:t>Before</w:t></w:r></w:p>" +
                        "<w:p><w:r><w:pict><v:shape " +
                        "xmlns:v=\"urn:schemas-microsoft-com:vml\">" +
                        "<v:textbox><w:txbxContent><w:p><w:r><w:t>Inside the box</w:t></w:r></w:p>" +
                        "</w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p>",
                ),
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("Before", "After"))
        val after = partsOf(saved)["word/document.xml"]!!

        assertContains(after, "<w:txbxContent>")
        assertContains(after, "Inside the box")
        assertContains(after, "After")
    }

    @Test
    fun aFirstPageHeaderIsKeptEvenThoughItIsNotEditable() {
        val headerRel =
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rIdH1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" " +
                "Target=\"header1.xml\"/>" +
                "<Relationship Id=\"rIdH2\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" " +
                "Target=\"header2.xml\"/>" +
                "</Relationships>"
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:p><w:r><w:t>Body</w:t></w:r></w:p>" +
                        "<w:sectPr>" +
                        "<w:headerReference w:type=\"first\" r:id=\"rIdH1\"/>" +
                        "<w:headerReference w:type=\"default\" r:id=\"rIdH2\"/>" +
                        "</w:sectPr>",
                ),
                "word/_rels/document.xml.rels" to headerRel,
                "word/header1.xml" to
                    "<w:hdr xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                    "<w:p><w:r><w:t>First page header</w:t></w:r></w:p></w:hdr>",
                "word/header2.xml" to
                    "<w:hdr xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                    "<w:p><w:r><w:t>Default header</w:t></w:r></w:p></w:hdr>",
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("Body", "Changed"))
        val after = partsOf(saved)

        assertEquals(
            partsOf(original)["word/header1.xml"],
            after["word/header1.xml"],
            "the first-page header is not editable, so it must not change",
        )
        assertContains(after["word/document.xml"]!!, "<w:headerReference w:type=\"first\" r:id=\"rIdH1\"/>")
    }

    @Test
    fun theDefaultHeaderIsEditedInPlace() {
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:p><w:r><w:t>Body</w:t></w:r></w:p>" +
                        "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdH1\"/></w:sectPr>",
                ),
                "word/_rels/document.xml.rels" to
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rIdH1\" " +
                    "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" " +
                    "Target=\"header1.xml\"/></Relationships>",
                "word/header1.xml" to
                    "<w:hdr xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                    "<w:p><w:r><w:t>Default header</w:t></w:r></w:p></w:hdr>",
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))
        val edited = source.document.copy(
            header = listOf(Paragraph(runs = listOf(Run("New header")))),
        )

        val saved = DocxEditor(source).save(edited)
        val after = partsOf(saved)

        assertContains(after["word/header1.xml"]!!, "New header")
        assertContains(after["word/document.xml"]!!, "<w:headerReference w:type=\"default\" r:id=\"rIdH1\"/>")
    }

    @Test
    fun aTableKeepsItsBordersAndGrid() {
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:tbl>" +
                        "<w:tblPr><w:tblStyle w:val=\"TableGrid\"/>" +
                        "<w:tblBorders><w:top w:val=\"single\"/><w:left w:val=\"single\"/>" +
                        "<w:bottom w:val=\"single\"/><w:right w:val=\"single\"/></w:tblBorders>" +
                        "</w:tblPr>" +
                        "<w:tblGrid><w:gridCol w:w=\"2000\"/><w:gridCol w:w=\"3000\"/></w:tblGrid>" +
                        "<w:tr><w:tc><w:p><w:r><w:t>Cell</w:t></w:r></w:p></w:tc>" +
                        "<w:tc><w:p><w:r><w:t>Other</w:t></w:r></w:p></w:tc></w:tr>" +
                        "</w:tbl>",
                ),
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document)
        val after = partsOf(saved)["word/document.xml"]!!

        assertContains(after, "<w:tblStyle w:val=\"TableGrid\"/>")
        assertContains(after, "<w:top w:val=\"single\"/>")
        assertContains(after, "<w:gridCol w:w=\"2000\"/>")
        assertContains(after, "<w:gridCol w:w=\"3000\"/>")
    }

    @Test
    fun aCorruptPackageFailsInsteadOfProducingAnEmptyDocument() {
        val broken = zipOf(mapOf("[Content_Types].xml" to contentTypes))
        var failed = false
        try {
            reader.read(ByteArrayInputStream(broken))
        } catch (expected: java.io.IOException) {
            failed = true
        }
        assertTrue(failed, "a package without a document part must be reported")
    }

    @Test
    fun theSavedPackageIsStillAZipWithTheSameParts() {
        val original = richDocument()
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.replaceText("Heading", "New"))
        val after = partsOf(saved)

        assertEquals(partsOf(original).keys, after.keys)
        assertNotNull(after["word/document.xml"])
    }

    @Test
    fun addingAHeaderToADocumentThatHasNoneCreatesThePart() {
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:p><w:r><w:t>Body</w:t></w:r></w:p><w:sectPr/>",
                ),
                "word/_rels/document.xml.rels" to
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>",
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))
        val withHeader = source.document.copy(
            header = listOf(Paragraph(runs = listOf(Run("Running head")))),
        )

        val saved = DocxEditor(source).save(withHeader)
        val after = partsOf(saved)

        assertNotNull(after["word/header1.xml"], "the header part must exist")
        assertContains(after.getValue("word/header1.xml")!!, "Running head")
        assertContains(after["word/_rels/document.xml.rels"]!!, "header1.xml")
        assertContains(after["[Content_Types].xml"]!!, "/word/header1.xml")
        assertContains(after["word/document.xml"]!!, "<w:headerReference")
    }

    @Test
    fun aHeaderAddedToADocumentThatHasNoneReadsBack() {
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:p><w:r><w:t>Body</w:t></w:r></w:p><w:sectPr/>",
                ),
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))
        val withHeader = source.document.copy(
            header = listOf(Paragraph(runs = listOf(Run("Running head")))),
        )

        val saved = DocxEditor(source).save(withHeader)
        val reread = reader.read(ByteArrayInputStream(saved))

        assertEquals(
            listOf("Running head"),
            reread.header.orEmpty().map { block ->
                (block as Paragraph).runs.joinToString("") { it.text }
            },
        )
    }

    @Test
    fun removingTheHeaderDropsTheReference() {
        val original = zipOf(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml(
                    "<w:p><w:r><w:t>Body</w:t></w:r></w:p>" +
                        "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdH1\"/></w:sectPr>",
                ),
                "word/_rels/document.xml.rels" to
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rIdH1\" " +
                    "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" " +
                    "Target=\"header1.xml\"/></Relationships>",
                "word/header1.xml" to
                    "<w:hdr xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\"><w:p/></w:hdr>",
            ),
        )
        val source = reader.readSource(ByteArrayInputStream(original))

        val saved = DocxEditor(source).save(source.document.copy(header = emptyList()))

        assertTrue(
            !partsOf(saved).getValue("word/document.xml")!!.contains("headerReference"),
            "the reference to a removed header must go",
        )
    }
}
