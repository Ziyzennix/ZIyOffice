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
 * A document shaped the way Word writes them.
 *
 * Everything else in this folder builds its own XML, which is handy but easy to
 * get wrong in the direction that flatters us: Word puts revision ids on every
 * paragraph, declares a pile of namespaces it may or may not use, leans on
 * styles for its formatting and sprinkles proofing markers through the text.
 * This file does the same, so the reader and the writer meet something closer
 * to what a person actually opens.
 */
class DocxWordShapedTest {

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

    /** The namespaces Word puts on a document element. */
    private val namespaces = listOf(
        "xmlns:wpc=\"http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas\"",
        "xmlns:cx=\"http://schemas.microsoft.com/office/drawing/2014/chartex\"",
        "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"",
        "xmlns:o=\"urn:schemas-microsoft-com:office:office\"",
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"",
        "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\"",
        "xmlns:v=\"urn:schemas-microsoft-com:vml\"",
        "xmlns:wp14=\"http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing\"",
        "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"",
        "xmlns:w10=\"urn:schemas-microsoft-com:office:word\"",
        "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"",
        "xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\"",
        "xmlns:w15=\"http://schemas.microsoft.com/office/word/2012/wordml\"",
        "xmlns:wpg=\"http://schemas.microsoft.com/office/word/2010/wordprocessingGroup\"",
        "xmlns:wpi=\"http://schemas.microsoft.com/office/word/2010/wordprocessingInk\"",
        "xmlns:wne=\"http://schemas.microsoft.com/office/word/2006/wordml\"",
        "xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"",
        "mc:Ignorable=\"w14 w15 wp14\"",
    ).joinToString(" ")

    private val contentTypes =
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "<Override PartName=\"/word/settings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml\"/>" +
            "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
            "<Override PartName=\"/word/fontTable.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml\"/>" +
            "<Override PartName=\"/word/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
            "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>" +
            "</Types>"

    private val rootRels =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
            "Target=\"word/document.xml\"/>" +
            "<Relationship Id=\"rId2\" " +
            "Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" " +
            "Target=\"docProps/core.xml\"/>" +
            "<Relationship Id=\"rId3\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" " +
            "Target=\"docProps/app.xml\"/></Relationships>"

    private val documentRels =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" " +
            "Target=\"styles.xml\"/>" +
            "<Relationship Id=\"rId2\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings\" " +
            "Target=\"settings.xml\"/>" +
            "<Relationship Id=\"rId3\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" " +
            "Target=\"numbering.xml\"/>" +
            "<Relationship Id=\"rId4\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable\" " +
            "Target=\"fontTable.xml\"/>" +
            "<Relationship Id=\"rId5\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" " +
            "Target=\"theme/theme1.xml\"/>" +
            "<Relationship Id=\"rId9\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" " +
            "Target=\"https://example.com\" TargetMode=\"External\"/></Relationships>"

    /** The body, with the revision ids and proofing markers Word leaves behind. */
    private val body = """
        <w:p w14:paraId="1A2B3C4D" w14:textId="77777777" w:rsidR="00A1B2C3" w:rsidRDefault="00A1B2C3">
          <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
          <w:r><w:rPr><w:rFonts w:asciiTheme="majorHAnsi" w:hAnsiTheme="majorHAnsi"/></w:rPr><w:t>Real heading</w:t></w:r>
        </w:p>
        <w:p w14:paraId="5E6F7A8B" w14:textId="77777777" w:rsidR="00A1B2C3" w:rsidRDefault="00A1B2C3">
          <w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr>
          <w:proofErr w:type="spellStart"/><w:r><w:t>Hello</w:t></w:r><w:proofErr w:type="spellEnd"/>
          <w:r><w:rPr><w:b/><w:color w:val="FF0000"/></w:rPr><w:t xml:space="preserve"> bold red</w:t></w:r>
        </w:p>
        <w:p w:rsidR="00A1B2C3" w:rsidRDefault="00A1B2C3">
          <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
          <w:r><w:t>A list item</w:t></w:r>
        </w:p>
        <w:p w:rsidR="00A1B2C3">
          <w:hyperlink r:id="rId9" w:history="1"><w:r><w:rPr><w:rStyle w:val="Hyperlink"/></w:rPr><w:t>A link</w:t></w:r></w:hyperlink>
        </w:p>
    """.trimIndent()

    private fun wordDocument(): ByteArray = zipOf(
        mapOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rootRels,
            "word/document.xml" to
                "<w:document $namespaces><w:body>$body" +
                "<w:sectPr w:rsidR=\"00A1B2C3\" w:rsidSect=\"00A1B2C3\">" +
                "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
                "<w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\"1418\" " +
                "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>" +
                "<w:cols w:space=\"708\"/><w:docGrid w:linePitch=\"360\"/></w:sectPr>" +
                "</w:body></w:document>",
            "word/_rels/document.xml.rels" to documentRels,
            "word/styles.xml" to
                "<w:styles xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:docDefaults><w:rPrDefault><w:rPr>" +
                "<w:rFonts w:asciiTheme=\"minorHAnsi\" w:hAnsiTheme=\"minorHAnsi\"/>" +
                "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>" +
                "</w:rPr></w:rPrDefault><w:pPrDefault/></w:docDefaults>" +
                "<w:latentStyles w:defLockedState=\"0\" w:defUIPriority=\"99\" w:defSemiHidden=\"1\" " +
                "w:defUnhideWhenUsed=\"1\" w:defQFormat=\"0\" w:count=\"376\">" +
                "<w:lsdException w:name=\"Normal\" w:uiPriority=\"0\" w:qFormat=\"1\"/>" +
                "</w:latentStyles>" +
                "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">" +
                "<w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\">" +
                "<w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/>" +
                "<w:next w:val=\"Normal\"/><w:qFormat/>" +
                "<w:pPr><w:keepNext/><w:outlineLvl w:val=\"0\"/></w:pPr>" +
                "<w:rPr><w:rFonts w:asciiTheme=\"majorHAnsi\" w:hAnsiTheme=\"majorHAnsi\"/>" +
                "<w:color w:val=\"2F5496\"/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/></w:rPr></w:style>" +
                "<w:style w:type=\"character\" w:styleId=\"Hyperlink\">" +
                "<w:name w:val=\"Hyperlink\"/><w:rPr><w:color w:val=\"0563C1\"/>" +
                "<w:u w:val=\"single\"/></w:rPr></w:style>" +
                "</w:styles>",
            "word/settings.xml" to
                "<w:settings xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:zoom w:percent=\"100\"/><w:defaultTabStop w:val=\"708\"/>" +
                "<w:characterSpacingControl w:val=\"doNotCompress\"/>" +
                "<w:compat><w:compatSetting w:name=\"compatibilityMode\" " +
                "w:uri=\"http://schemas.microsoft.com/office/word\" w:val=\"15\"/></w:compat>" +
                "<w:rsids><w:rsidRoot w:val=\"00A1B2C3\"/><w:rsid w:val=\"00A1B2C3\"/></w:rsids>" +
                "</w:settings>",
            "word/numbering.xml" to
                "<w:numbering xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/>" +
                "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/>" +
                "<w:lvlText w:val=\"\"/><w:lvlJc w:val=\"left\"/>" +
                "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr>" +
                "<w:rPr><w:rFonts w:ascii=\"Symbol\" w:hAnsi=\"Symbol\" w:hint=\"default\"/></w:rPr>" +
                "</w:lvl></w:abstractNum>" +
                "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>",
            "word/fontTable.xml" to
                "<w:fonts xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:font w:name=\"Calibri\"><w:family w:val=\"swiss\"/></w:font>" +
                "<w:font w:name=\"Symbol\"><w:family w:val=\"roman\"/></w:font></w:fonts>",
            "word/theme/theme1.xml" to
                "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "name=\"Office Theme\"><a:themeElements>" +
                "<a:clrScheme name=\"Office\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>" +
                "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1></a:clrScheme>" +
                "<a:fontScheme name=\"Office\"><a:majorFont><a:latin typeface=\"Calibri Light\"/></a:majorFont>" +
                "<a:minorFont><a:latin typeface=\"Calibri\"/></a:minorFont></a:fontScheme>" +
                "<a:fmtScheme name=\"Office\"/></a:themeElements></a:theme>",
            "docProps/core.xml" to
                "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                "<dc:title>Quarterly report</dc:title><dc:creator>Ada</dc:creator>" +
                "<cp:lastModifiedBy>Ada</cp:lastModifiedBy></cp:coreProperties>",
            "docProps/app.xml" to
                "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
                "<Application>Microsoft Office Word</Application><Pages>1</Pages></Properties>",
        ),
    )

    @Test
    fun aWordShapedDocumentOpens() {
        val paragraphs = reader.read(ByteArrayInputStream(wordDocument()))
            .blocks.filterIsInstance<Paragraph>()

        assertEquals(
            listOf("Real heading", "Hello bold red", "A list item", "A link"),
            paragraphs.map { it.runs.joinToString("") { run -> run.text } },
        )
    }

    @Test
    fun theStyleSuppliesTheFormatting() {
        val paragraphs = reader.read(ByteArrayInputStream(wordDocument()))
            .blocks.filterIsInstance<Paragraph>()

        val heading = paragraphs.first()
        assertEquals("Heading1", heading.styleId)
        // Heading1 in the styles part sets the size, so the heading is 16 pt.
        assertEquals(16f, heading.runs.first().sizePt)
    }

    @Test
    fun theListIsRecognised() {
        val paragraphs = reader.read(ByteArrayInputStream(wordDocument()))
            .blocks.filterIsInstance<Paragraph>()

        assertEquals(1, paragraphs[2].list?.numId)
    }

    @Test
    fun editingOneParagraphLeavesTheRestOfTheFileAlone() {
        val original = wordDocument()
        val source = DocxSource.capture(original)
        val edited = source.document.copy(
            blocks = source.document.blocks.map { block ->
                if (block is Paragraph && block.runs.joinToString("") { it.text } == "Hello bold red") {
                    block.applyText("Hello bold red, edited")
                } else {
                    block
                }
            },
        )

        val saved = DocxEditor(source).save(edited)
        val parts = partsOf(saved)
        val xml = parts.getValue("word/document.xml")

        // The parts the editor has no business touching come through unchanged.
        assertEquals(partsOf(original)["word/styles.xml"], parts["word/styles.xml"])
        assertEquals(partsOf(original)["word/numbering.xml"], parts["word/numbering.xml"])
        assertEquals(partsOf(original)["word/settings.xml"], parts["word/settings.xml"])
        assertEquals(partsOf(original)["word/theme/theme1.xml"], parts["word/theme/theme1.xml"])
        assertEquals(partsOf(original)["docProps/core.xml"], parts["docProps/core.xml"])
        // The heading and the link are still there.
        assertContains(xml, "Real heading")
        assertContains(xml, "<w:hyperlink r:id=\"rId9\"")
        assertContains(xml, "<w:numId w:val=\"1\"/>")

        // The text comes back as it was typed, even though the runs kept their
        // own boundaries.
        val paragraphs = reader.read(ByteArrayInputStream(saved)).blocks.filterIsInstance<Paragraph>()
        assertEquals(
            listOf("Real heading", "Hello bold red, edited", "A list item", "A link"),
            paragraphs.map { it.runs.joinToString("") { run -> run.text } },
        )
    }

    @Test
    fun theBoldRedRunKeepsItsFormatting() {
        val original = wordDocument()
        val source = DocxSource.capture(original)
        val edited = source.document.copy(
            blocks = source.document.blocks.map { block ->
                if (block is Paragraph && block.runs.joinToString("") { it.text } == "Hello bold red") {
                    block.applyText("Hello bold red, edited")
                } else {
                    block
                }
            },
        )

        val saved = DocxEditor(source).save(edited)
        val paragraphs = reader.read(ByteArrayInputStream(saved)).blocks.filterIsInstance<Paragraph>()

        assertTrue(
            paragraphs[1].runs.any { it.bold && it.text.contains("bold red") },
            "the red bold run should still be bold: ${paragraphs[1].runs}",
        )
    }
}
