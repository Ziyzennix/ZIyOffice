package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.model.TableCell
import org.ziyoffice.model.TableRow

/**
 * Round-trip tests: what the writer emits must come back identical through the
 * reader for everything the model can currently express.
 */
class DocxWriterTest {

    private val reader = DocxReader()
    private val writer = DocxWriter()

    private fun roundTrip(document: Document): Document =
        reader.read(ByteArrayInputStream(writer.write(document)))

    @Test
    fun roundTripsParagraphsAndFormatting() {
        val document = Document(
            blocks = listOf(
                Paragraph(
                    runs = listOf(Run("Hello", bold = true, sizePt = 14f)),
                    spacingAfterTwips = 160,
                ),
                Paragraph(
                    runs = listOf(
                        Run("Centered", italic = true, underline = true, sizePt = 11f, colorRgb = 0x2F6FED),
                        Run(" tail", sizePt = 11f),
                    ),
                    alignment = ParagraphAlignment.CENTER,
                    spacingBeforeTwips = 240,
                    spacingAfterTwips = 160,
                    lineSpacingPercent = 150,
                    pageBreakBefore = true,
                ),
                Paragraph(runs = emptyList(), indentTwips = 720),
            ),
        )

        val reRead = roundTrip(document)

        assertEquals(3, reRead.blocks.size)
        val paragraphs = reRead.blocks.map { it as Paragraph }
        assertEquals("Hello", paragraphs[0].text())
        assertEquals(Run("Hello", bold = true, sizePt = 14f), paragraphs[0].runs.single())
        assertEquals(160, paragraphs[0].spacingAfterTwips)

        assertEquals(ParagraphAlignment.CENTER, paragraphs[1].alignment)
        assertEquals(240, paragraphs[1].spacingBeforeTwips)
        assertEquals(150, paragraphs[1].lineSpacingPercent)
        assertEquals(true, paragraphs[1].pageBreakBefore)
        assertEquals(listOf("Centered", " tail"), paragraphs[1].runs.map { it.text })
        assertEquals(true, paragraphs[1].runs[0].italic)
        assertEquals(true, paragraphs[1].runs[0].underline)
        assertEquals(0x2F6FED, paragraphs[1].runs[0].colorRgb)

        assertEquals(720, paragraphs[2].indentTwips)
        assertEquals(emptyList(), paragraphs[2].runs)
    }

    @Test
    fun roundTripsNumbersAndBreaksInsideText() {
        val document = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("A\tB\nC", sizePt = 11f))),
            ),
        )

        val reRead = roundTrip(document)

        assertEquals("A\tB\nC", (reRead.blocks.single() as Paragraph).text())
    }

    @Test
    fun roundTripsLists() {
        val document = Document(
            blocks = listOf(
                Paragraph(
                    runs = listOf(Run("One", sizePt = 11f)),
                    list = ListInfo(numId = 1, level = 0, kind = ListKind.NUMBER, start = 1),
                ),
                Paragraph(
                    runs = listOf(Run("Two", sizePt = 11f)),
                    list = ListInfo(numId = 1, level = 0, kind = ListKind.NUMBER, start = 1),
                ),
                Paragraph(
                    runs = listOf(Run("Bullet", sizePt = 11f)),
                    list = ListInfo(numId = 2, level = 1, kind = ListKind.BULLET, start = 1),
                ),
            ),
        )

        val paragraphs = roundTrip(document).blocks.map { it as Paragraph }

        assertEquals(ListInfo(numId = 1, level = 0, kind = ListKind.NUMBER, start = 1), paragraphs[0].list)
        assertEquals(ListInfo(numId = 1, level = 0, kind = ListKind.NUMBER, start = 1), paragraphs[1].list)
        assertEquals(ListInfo(numId = 2, level = 1, kind = ListKind.BULLET, start = 1), paragraphs[2].list)
    }

    @Test
    fun roundTripsTables() {
        val table = Table(
            rows = listOf(
                TableRow(
                    cells = listOf(
                        TableCell(listOf(Paragraph(runs = listOf(Run("Quarter", bold = true, sizePt = 11f))))),
                        TableCell(listOf(Paragraph(runs = listOf(Run("Revenue", bold = true, sizePt = 11f))))),
                    ),
                    header = true,
                ),
                TableRow(
                    cells = listOf(
                        TableCell(listOf(Paragraph(runs = listOf(Run("Q1", sizePt = 11f))))),
                        TableCell(
                            blocks = listOf(Paragraph(runs = listOf(Run("wide", sizePt = 11f)))),
                            columnSpan = 1,
                        ),
                    ),
                ),
            ),
            columnCount = 2,
            borders = true,
        )

        val reRead = roundTrip(Document(blocks = listOf(table))).blocks.single() as Table

        assertEquals(2, reRead.columnCount)
        assertEquals(true, reRead.borders)
        assertEquals(2, reRead.rows.size)
        assertEquals(true, reRead.rows[0].header)
        assertEquals(false, reRead.rows[1].header)
        assertEquals("Quarter", (reRead.rows[0].cells[0].blocks.single() as Paragraph).text())
        assertEquals("Q1", (reRead.rows[1].cells[0].blocks.single() as Paragraph).text())
    }

    @Test
    fun roundTripsImages() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val document = Document(
            blocks = listOf(
                Paragraph(
                    runs = listOf(Run("Before", sizePt = 11f)),
                    images = listOf(InlineImage(pngBytes, widthDp = 100f, heightDp = 50f)),
                ),
            ),
        )

        val reRead = roundTrip(document).blocks.single() as Paragraph

        assertEquals("Before", reRead.text())
        val image = reRead.images.single()
        assertContentEquals(pngBytes, image.bytes)
        assertEquals(100f, image.widthDp)
        assertEquals(50f, image.heightDp)
    }

    @Test
    fun escapesSpecialCharacters() {
        val document = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("a < b & c > d \"quoted\"", sizePt = 11f))),
            ),
        )

        val reRead = roundTrip(document)

        assertEquals("a < b & c > d \"quoted\"", (reRead.blocks.single() as Paragraph).text())
    }

    @Test
    fun roundTripsStrike() {
        val document = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("gone", strike = true, sizePt = 11f))),
            ),
        )
        val reRead = roundTrip(document)
        val p = reRead.blocks.single() as Paragraph
        assertEquals(true, p.runs.single().strike)
    }

    @Test
    fun roundTripsWebpImage() {
        val webpBytes = ByteArray(16) { 0 }
        webpBytes[0] = 'R'.code.toByte()
        webpBytes[1] = 'I'.code.toByte()
        webpBytes[2] = 'F'.code.toByte()
        webpBytes[3] = 'F'.code.toByte()
        webpBytes[8] = 'W'.code.toByte()
        webpBytes[9] = 'E'.code.toByte()
        webpBytes[10] = 'B'.code.toByte()
        webpBytes[11] = 'P'.code.toByte()
        val document = Document(
            blocks = listOf(
                Paragraph(
                    images = listOf(InlineImage(webpBytes, 10f, 10f)),
                ),
            ),
        )
        val reRead = roundTrip(document).blocks.single() as Paragraph
        val image = reRead.images.single()
        assertContentEquals(webpBytes, image.bytes)
        assertEquals("webp", DocxWriter.imageExtension(image.bytes))
    }

    private fun Paragraph.text(): String = runs.joinToString("") { it.text }
}
