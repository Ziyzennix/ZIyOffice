package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.ziyoffice.model.Block
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Table

class DocxReaderTest {

    private val reader = DocxReader()

    @Test
    fun readsParagraphs() {
        val bytes = docx(
            document(
                """
                <w:p><w:r><w:t>Hello ZiyOffice</w:t></w:r></w:p>
                <w:p><w:r><w:t>Second paragraph</w:t></w:r></w:p>
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf("Hello ZiyOffice", "Second paragraph"),
            paragraphTexts(bytes),
        )
    }

    @Test
    fun joinsRunsWithinAParagraph() {
        val bytes = docx(document("<w:p><w:r><w:t>Hel</w:t></w:r><w:r><w:t>lo</w:t></w:r></w:p>"))

        assertEquals(listOf("Hello"), paragraphTexts(bytes))
    }

    @Test
    fun handlesTabsAndBreaks() {
        val bytes = docx(
            document(
                "<w:p><w:r><w:t>A</w:t><w:tab/><w:t>B</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>C</w:t><w:br/><w:t>D</w:t></w:r></w:p>",
            ),
        )

        assertEquals(listOf("A\tB", "C\nD"), paragraphTexts(bytes))
    }

    @Test
    fun emitsEmptyParagraphs() {
        val bytes = docx(document("<w:p></w:p><w:p><w:r><w:t>x</w:t></w:r></w:p>"))

        val paragraphs = paragraphsOf(bytes)

        assertEquals(listOf("", "x"), paragraphs.map { it.text() })
        assertEquals(emptyList(), paragraphs.first().runs)
    }

    @Test
    fun ignoresTextOutsideWT() {
        val bytes = docx(
            document("<w:p><w:r><w:instrText>FIELDCODE</w:instrText><w:t>visible</w:t></w:r></w:p>"),
        )

        assertEquals(listOf("visible"), paragraphTexts(bytes))
    }

    @Test
    fun acceptsDifferentNamespacePrefixes() {
        val bytes = docx(
            document("<x:p><x:r><x:t>prefix safe</x:t></x:r></x:p>", prefix = "x"),
        )

        assertEquals(listOf("prefix safe"), paragraphTexts(bytes))
    }

    @Test
    fun skipsUnrelatedZipEntries() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/other.xml"))
            zip.write("<ignored/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(document("<w:p><w:r><w:t>found</w:t></w:r></w:p>").toByteArray())
            zip.closeEntry()
        }

        assertEquals(listOf("found"), paragraphTexts(out.toByteArray()))
    }

    @Test
    fun failsWhenDocumentPartIsMissing() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()
        }

        assertFailsWith<IOException> { reader.read(ByteArrayInputStream(out.toByteArray())) }
    }

    @Test
    fun readsBoldItalicAndUnderline() {
        val bytes = docx(
            document(
                "<w:p>" +
                    "<w:r><w:rPr><w:b/><w:i/><w:u/></w:rPr><w:t>styled</w:t></w:r>" +
                    "<w:r><w:rPr><w:b w:val=\"0\"/><w:i w:val=\"false\"/><w:u w:val=\"none\"/></w:rPr><w:t>plain</w:t></w:r>" +
                    "</w:p>",
            ),
        )

        val runs = paragraphsOf(bytes).first().runs

        assertEquals(2, runs.size)
        assertEquals(true, runs[0].bold)
        assertEquals(true, runs[0].italic)
        assertEquals(true, runs[0].underline)
        assertEquals(false, runs[1].bold)
        assertEquals(false, runs[1].italic)
        assertEquals(false, runs[1].underline)
    }

    @Test
    fun readsStrikethroughRun() {
        val bytes = docx(
            document("<w:p><w:r><w:rPr><w:strike/></w:rPr><w:t>x</w:t></w:r></w:p>"),
        )
        assertEquals(true, paragraphsOf(bytes).single().runs.single().strike)
    }

    @Test
    fun readsStrikethroughFromStyleChain() {
        val stylesXml = styles(
            "<w:style w:type=\"paragraph\" w:styleId=\"Striked\"><w:rPr><w:strike/></w:rPr></w:style>",
        )
        val bytes = docx(
            document("<w:p><w:pPr><w:pStyle w:val=\"Striked\"/></w:pPr><w:r><w:t>struck</w:t></w:r></w:p>"),
            stylesXml = stylesXml,
        )
        assertEquals(true, paragraphsOf(bytes).single().runs.single().strike)
    }

    @Test
    fun readsSizeAndColor() {
        val bytes = docx(
            document(
                "<w:p>" +
                    "<w:r><w:rPr><w:sz w:val=\"28\"/><w:color w:val=\"2F6FED\"/></w:rPr><w:t>x</w:t></w:r>" +
                    "<w:r><w:rPr><w:color w:val=\"auto\"/></w:rPr><w:t>y</w:t></w:r>" +
                    "</w:p>",
            ),
        )

        val runs = paragraphsOf(bytes).first().runs

        assertEquals(14f, runs[0].sizePt)
        assertEquals(0x2F6FED, runs[0].colorRgb)
        assertEquals(null, runs[1].sizePt)
        assertEquals(null, runs[1].colorRgb)
    }

    @Test
    fun readsParagraphAlignment() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>c</w:t></w:r></w:p>" +
                    "<w:p><w:pPr><w:jc w:val=\"both\"/></w:pPr><w:r><w:t>j</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>d</w:t></w:r></w:p>",
            ),
        )

        val alignments = paragraphsOf(bytes).map { it.alignment }

        assertEquals(
            listOf(ParagraphAlignment.CENTER, ParagraphAlignment.JUSTIFY, ParagraphAlignment.START),
            alignments,
        )
    }

    @Test
    fun readsIndentation() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:ind w:left=\"709\" w:hanging=\"283\"/></w:pPr><w:r><w:t>x</w:t></w:r></w:p>",
            ),
        )

        assertEquals(709, paragraphsOf(bytes).single().indentTwips)
    }

    @Test
    fun ignoresParagraphMarkRunProperties() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:rPr><w:b/></w:rPr></w:pPr><w:r><w:t>x</w:t></w:r></w:p>",
            ),
        )

        val runs = paragraphsOf(bytes).first().runs

        assertEquals(1, runs.size)
        assertEquals(false, runs[0].bold)
    }

    @Test
    fun resolvesPageBreaksFromParagraphPropertyAndRunBreak() {
        val bytes = docx(
            document(
                "<w:p><w:r><w:t>page one</w:t></w:r></w:p>" +
                    "<w:p><w:pPr><w:pageBreakBefore/></w:pPr><w:r><w:t>direct</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>break at end</w:t></w:r><w:r><w:br w:type=\"page\"/></w:r></w:p>" +
                    "<w:p><w:r><w:t>after run break</w:t></w:r></w:p>",
            ),
        )

        val paragraphs = paragraphsOf(bytes)

        assertEquals(false, paragraphs[0].pageBreakBefore)
        assertEquals(true, paragraphs[1].pageBreakBefore)
        // A run-level page break belongs to the paragraph that follows it and
        // must not leak into the text.
        assertEquals(false, paragraphs[2].pageBreakBefore)
        assertEquals("break at end", paragraphs[2].text())
        assertEquals(true, paragraphs[3].pageBreakBefore)
    }

    @Test
    fun resolvesParagraphSpacingFromStylesAndDirectFormatting() {
        val stylesXml = styles(
            "<w:docDefaults><w:pPrDefault><w:pPr><w:spacing w:after=\"160\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading\">" +
                "<w:pPr><w:spacing w:before=\"240\" w:after=\"283\"/></w:pPr></w:style>",
        )
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:pStyle w:val=\"Heading\"/></w:pPr><w:r><w:t>h</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>body</w:t></w:r></w:p>" +
                    "<w:p><w:pPr><w:spacing w:after=\"0\"/></w:pPr><w:r><w:t>tight</w:t></w:r></w:p>",
            ),
            stylesXml = stylesXml,
        )

        val paragraphs = paragraphsOf(bytes)

        assertEquals(240, paragraphs[0].spacingBeforeTwips)
        assertEquals(283, paragraphs[0].spacingAfterTwips)
        assertEquals(null, paragraphs[1].spacingBeforeTwips)
        assertEquals(160, paragraphs[1].spacingAfterTwips)
        assertEquals(0, paragraphs[2].spacingAfterTwips)
    }

    @Test
    fun resolvesLineSpacingAndIgnoresExactRules() {
        val stylesXml = styles(
            "<w:docDefaults><w:pPrDefault><w:pPr>" +
                "<w:spacing w:line=\"276\" w:lineRule=\"auto\"/>" +
                "</w:pPr></w:pPrDefault></w:docDefaults>" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Wide\">" +
                "<w:pPr><w:spacing w:line=\"480\" w:lineRule=\"auto\"/></w:pPr></w:style>",
        )
        val bytes = docx(
            document(
                "<w:p><w:r><w:t>default</w:t></w:r></w:p>" +
                    "<w:p><w:pPr><w:pStyle w:val=\"Wide\"/></w:pPr><w:r><w:t>wide</w:t></w:r></w:p>" +
                    "<w:p><w:pPr><w:spacing w:line=\"360\" w:lineRule=\"auto\"/></w:pPr>" +
                    "<w:r><w:t>one and a half</w:t></w:r></w:p>" +
                    "<w:p><w:pPr><w:spacing w:line=\"400\" w:lineRule=\"exact\"/></w:pPr>" +
                    "<w:r><w:t>fixed height</w:t></w:r></w:p>",
            ),
            stylesXml = stylesXml,
        )

        val paragraphs = paragraphsOf(bytes)

        assertEquals(115, paragraphs[0].lineSpacingPercent)
        assertEquals(200, paragraphs[1].lineSpacingPercent)
        assertEquals(150, paragraphs[2].lineSpacingPercent)
        // An exact line height is an absolute rule, not a percentage: left unset.
        assertEquals(null, paragraphs[3].lineSpacingPercent)
    }

    private val styleChain = styles(
        "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Normal\"><w:rPr><w:sz w:val=\"24\"/></w:rPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading\"><w:basedOn w:val=\"Normal\"/>" +
            "<w:rPr><w:sz w:val=\"28\"/></w:rPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:basedOn w:val=\"Heading\"/>" +
            "<w:rPr><w:b/><w:sz w:val=\"48\"/></w:rPr></w:style>",
    )

    @Test
    fun resolvesStyleChainAndDocumentDefaults() {
        val bytes = docx(
            document("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Title</w:t></w:r></w:p>"),
            stylesXml = styleChain,
        )

        val run = paragraphsOf(bytes).single().runs.single()

        assertEquals(true, run.bold)
        assertEquals(24f, run.sizePt)
    }

    @Test
    fun documentDefaultsApplyWithoutAStyle() {
        val bytes = docx(
            document("<w:p><w:r><w:t>body</w:t></w:r></w:p>"),
            stylesXml = styleChain,
        )

        val run = paragraphsOf(bytes).single().runs.single()

        assertEquals(false, run.bold)
        assertEquals(11f, run.sizePt)
    }

    @Test
    fun runFormattingOverridesStyle() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>" +
                    "<w:r><w:rPr><w:b w:val=\"0\"/><w:sz w:val=\"20\"/></w:rPr><w:t>x</w:t></w:r></w:p>",
            ),
            stylesXml = styleChain,
        )

        val run = paragraphsOf(bytes).single().runs.single()

        assertEquals(false, run.bold)
        assertEquals(10f, run.sizePt)
    }

    @Test
    fun appliesTheDefaultParagraphStyleWhenPresent() {
        val stylesXml = styles(
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">" +
                "<w:rPr><w:i/><w:sz w:val=\"30\"/></w:rPr></w:style>",
        )
        val bytes = docx(document("<w:p><w:r><w:t>x</w:t></w:r></w:p>"), stylesXml = stylesXml)

        val run = paragraphsOf(bytes).single().runs.single()

        assertEquals(true, run.italic)
        assertEquals(15f, run.sizePt)
    }

    @Test
    fun resolvesBulletNumbering() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>" +
                    "<w:r><w:t>item</w:t></w:r></w:p>",
            ),
            numberingXml = numbering(
                "<w:abstractNum w:abstractNumId=\"7\"><w:lvl w:ilvl=\"0\">" +
                    "<w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/></w:lvl></w:abstractNum>" +
                    "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"7\"/></w:num>",
            ),
        )

        val list = paragraphsOf(bytes).single().list

        assertEquals(ListInfo(numId = 1, level = 0, kind = ListKind.BULLET, start = 1), list)
    }

    @Test
    fun resolvesDecimalNumberingWithStart() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"1\"/><w:numId w:val=\"2\"/></w:numPr></w:pPr>" +
                    "<w:r><w:t>third</w:t></w:r></w:p>",
            ),
            numberingXml = numbering(
                "<w:abstractNum w:abstractNumId=\"3\"><w:lvl w:ilvl=\"1\">" +
                    "<w:start w:val=\"3\"/><w:numFmt w:val=\"decimal\"/></w:lvl></w:abstractNum>" +
                    "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"3\"/></w:num>",
            ),
        )

        val list = paragraphsOf(bytes).single().list

        assertEquals(ListInfo(numId = 2, level = 1, kind = ListKind.NUMBER, start = 3), list)
    }

    @Test
    fun numIdZeroMeansNotAList() {
        val bytes = docx(
            document(
                "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"0\"/></w:numPr></w:pPr>" +
                    "<w:r><w:t>plain</w:t></w:r></w:p>",
            ),
        )

        assertEquals(null, paragraphsOf(bytes).single().list)
    }

    private val twoByTwoTable = """
        <w:tbl>
          <w:tblPr><w:tblStyle w:val="Table1"/><w:tblBorders>
            <w:top w:val="single"/><w:left w:val="single"/><w:bottom w:val="single"/>
            <w:right w:val="single"/><w:insideH w:val="single"/><w:insideV w:val="single"/>
          </w:tblBorders></w:tblPr>
          <w:tblGrid><w:gridCol/><w:gridCol/></w:tblGrid>
          <w:tr><w:trPr><w:tblHeader/></w:trPr>
            <w:tc><w:p><w:r><w:t>Quarter</w:t></w:r></w:p></w:tc>
            <w:tc><w:p><w:r><w:t>Revenue</w:t></w:r></w:p></w:tc>
          </w:tr>
          <w:tr>
            <w:tc><w:p><w:r><w:t>Q1</w:t></w:r></w:p></w:tc>
            <w:tc><w:p><w:r><w:t>120</w:t></w:r></w:p></w:tc>
          </w:tr>
        </w:tbl>
    """.trimIndent()

    @Test
    fun parsesTablesAsBlocksInOrder() {
        val bytes = docx(
            document(
                "<w:p><w:r><w:t>before</w:t></w:r></w:p>" +
                    twoByTwoTable +
                    "<w:p><w:r><w:t>after</w:t></w:r></w:p>",
            ),
        )

        val blocks = blocksOf(bytes)

        assertEquals(3, blocks.size)
        assertIs<Paragraph>(blocks[0])
        assertIs<Table>(blocks[1])
        assertIs<Paragraph>(blocks[2])
        assertEquals("before", (blocks[0] as Paragraph).text())
        assertEquals("after", (blocks[2] as Paragraph).text())

        val table = blocks[1] as Table
        assertEquals(2, table.rows.size)
        assertEquals(2, table.columnCount)
        assertEquals(true, table.borders)
        assertEquals(true, table.rows[0].header)
        assertEquals(false, table.rows[1].header)
        assertEquals(2, table.rows[0].cells.size)
        assertEquals("Quarter", table.rows[0].cells[0].text())
        assertEquals("120", table.rows[1].cells[1].text())
    }

    @Test
    fun tableWithoutBordersHasNone() {
        val bytes = docx(
            document(
                "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>x</w:t></w:r></w:p></w:tc></w:tr></w:tbl>",
            ),
        )

        val table = blocksOf(bytes).single() as Table

        assertEquals(false, table.borders)
        assertEquals(1, table.columnCount)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun takesBordersFromTheTableStyle() {
        val stylesXml = styles(
            "<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:tblPr><w:tblBorders>" +
                "<w:top w:val=\"single\"/></w:tblBorders></w:tblPr></w:style>",
        )
        val bytes = docx(
            document(
                "<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/></w:tblPr>" +
                    "<w:tr><w:tc><w:p><w:r><w:t>x</w:t></w:r></w:p></w:tc></w:tr></w:tbl>",
            ),
            stylesXml = stylesXml,
        )

        assertEquals(true, (blocksOf(bytes).single() as Table).borders)
    }

    @Test
    fun takesBordersFromCells() {
        val bytes = docx(
            document(
                "<w:tbl><w:tr><w:tc><w:tcPr><w:tcBorders>" +
                    "<w:top w:val=\"double\"/><w:left w:val=\"double\"/>" +
                    "</w:tcBorders></w:tcPr><w:p><w:r><w:t>x</w:t></w:r></w:p></w:tc></w:tr></w:tbl>",
            ),
        )

        assertEquals(true, (blocksOf(bytes).single() as Table).borders)
    }

    @Test
    fun readsGridSpan() {
        val bytes = docx(
            document(
                "<w:tbl><w:tr><w:tc><w:tcPr><w:gridSpan w:val=\"2\"/></w:tcPr>" +
                    "<w:p><w:r><w:t>wide</w:t></w:r></w:p></w:tc></w:tr></w:tbl>",
            ),
        )

        val table = blocksOf(bytes).single() as Table

        assertEquals(2, table.rows.single().cells.single().columnSpan)
        assertEquals(2, table.columnCount)
    }

    @Test
    fun readsNestedTableInsideCell() {
        val nested = "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>inner</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
        val bytes = docx(
            document(
                "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>outer</w:t></w:r></w:p>$nested</w:tc></w:tr></w:tbl>",
            ),
        )

        val cell = (blocksOf(bytes).single() as Table).rows.single().cells.single()

        assertEquals(2, cell.blocks.size)
        assertIs<Table>(cell.blocks[1])
    }

    private val relationshipsNamespace =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    @Test
    fun readsInlineImages() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val bytes = docx(
            document(
                "<w:p><w:r><w:drawing><wp:inline>" +
                    "<wp:extent cx=\"952500\" cy=\"476250\"/>" +
                    "<a:graphic><a:blip r:embed=\"rId5\"/></a:graphic>" +
                    "</wp:inline></w:drawing></w:r></w:p>",
                namespaces = "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                    "xmlns:r=\"$relationshipsNamespace\"",
            ),
            extraParts = mapOf(
                "word/_rels/document.xml.rels" to (
                    "<Relationships><Relationship Id=\"rId5\" " +
                        "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                        "Target=\"media/image1.png\"/></Relationships>"
                    ).toByteArray(),
                "word/media/image1.png" to pngBytes,
            ),
        )

        val image = paragraphsOf(bytes).single().images.single()

        assertContentEquals(pngBytes, image.bytes)
        assertEquals(100f, image.widthDp)
        assertEquals(50f, image.heightDp)
    }

    @Test
    fun skipsImagesWithMissingRelationship() {
        val bytes = docx(
            document(
                "<w:p><w:r><w:drawing><a:blip r:embed=\"rId404\"/></w:drawing></w:r></w:p>",
                namespaces = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                    "xmlns:r=\"$relationshipsNamespace\"",
            ),
        )

        assertEquals(emptyList(), paragraphsOf(bytes).single().images)
    }

    @Test
    fun skipsExternalImageTargets() {
        val bytes = docx(
            document(
                "<w:p><w:r><w:drawing><a:blip r:embed=\"rId6\"/></w:drawing></w:r></w:p>",
                namespaces = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                    "xmlns:r=\"$relationshipsNamespace\"",
            ),
            extraParts = mapOf(
                "word/_rels/document.xml.rels" to (
                    "<Relationships><Relationship Id=\"rId6\" " +
                        "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                        "Target=\"https://example.com/tracker.png\" TargetMode=\"External\"/></Relationships>"
                    ).toByteArray(),
            ),
        )

        assertEquals(emptyList(), paragraphsOf(bytes).single().images)
    }

    @Test
    fun readsLegacyVmlImages() {
        val gifBytes = byteArrayOf(0x47, 0x49, 0x46)
        val bytes = docx(
            document(
                "<w:p><w:r><w:pict><v:shape><v:imagedata r:id=\"rId7\"/></v:shape></w:pict></w:r></w:p>",
                namespaces = "xmlns:v=\"urn:schemas-microsoft-com:vml\" " +
                    "xmlns:r=\"$relationshipsNamespace\"",
            ),
            extraParts = mapOf(
                "word/_rels/document.xml.rels" to (
                    "<Relationships><Relationship Id=\"rId7\" " +
                        "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                        "Target=\"media/image2.gif\"/></Relationships>"
                    ).toByteArray(),
                "word/media/image2.gif" to gifBytes,
            ),
        )

        val image = paragraphsOf(bytes).single().images.single()

        assertContentEquals(gifBytes, image.bytes)
        assertEquals(null, image.widthDp)
    }

    private fun blocksOf(bytes: ByteArray): List<Block> =
        reader.read(ByteArrayInputStream(bytes)).blocks

    private fun paragraphsOf(bytes: ByteArray): List<Paragraph> =
        blocksOf(bytes).filterIsInstance<Paragraph>()

    private fun paragraphTexts(bytes: ByteArray): List<String> =
        paragraphsOf(bytes).map { it.text() }

    private fun Paragraph.text(): String = runs.joinToString("") { it.text }

    private fun org.ziyoffice.model.TableCell.text(): String =
        blocks.filterIsInstance<Paragraph>().joinToString("\n") { it.text() }

    private fun document(body: String, prefix: String = "w", namespaces: String = ""): String =
        "<$prefix:document xmlns:$prefix=\"$WORDPROCESSINGML_NAMESPACE\" $namespaces>" +
            "<$prefix:body>$body</$prefix:body></$prefix:document>"

    private fun styles(body: String): String =
        "<w:styles xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">$body</w:styles>"

    private fun numbering(body: String): String =
        "<w:numbering xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">$body</w:numbering>"

    private fun docx(
        documentXml: String,
        stylesXml: String? = null,
        numberingXml: String? = null,
        extraParts: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
            stylesXml?.let {
                zip.putNextEntry(ZipEntry("word/styles.xml"))
                zip.write(it.toByteArray())
                zip.closeEntry()
            }
            numberingXml?.let {
                zip.putNextEntry(ZipEntry("word/numbering.xml"))
                zip.write(it.toByteArray())
                zip.closeEntry()
            }
            extraParts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
