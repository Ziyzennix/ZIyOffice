package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.model.TableCell
import org.ziyoffice.model.TableRow

class DocxCorpusTest {

    private val reader = DocxReader()
    private val writer = DocxWriter()

    private fun roundTrip(d: Document): Document =
        reader.read(ByteArrayInputStream(writer.write(d)))

    @Test
    fun realisticDocumentSurvivesRoundTrip() {
        val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val doc = Document(
            blocks = listOf(
                Paragraph(
                    runs = listOf(Run("Quarterly Strategy Report", bold = true, sizePt = 24f)),
                    spacingAfterTwips = 240,
                ),
                Paragraph(
                    runs = listOf(Run("This document summarizes our Q1 results and projections.", sizePt = 11f)),
                    spacingAfterTwips = 160,
                ),
                Paragraph(
                    runs = listOf(Run("Revenue exceeded target by 14%", sizePt = 11f)),
                    list = ListInfo(1, 0, ListKind.BULLET),
                ),
                Paragraph(
                    runs = listOf(Run("Open European office", sizePt = 11f)),
                    list = ListInfo(2, 0, ListKind.NUMBER),
                ),
                Table(
                    rows = listOf(
                        TableRow(
                            cells = listOf(
                                TableCell(listOf(Paragraph(runs = listOf(Run("Metric", bold = true, sizePt = 11f))))),
                                TableCell(listOf(Paragraph(runs = listOf(Run("Value", bold = true, sizePt = 11f))))),
                            ),
                            header = true,
                        ),
                        TableRow(
                            cells = listOf(
                                TableCell(listOf(Paragraph(runs = listOf(Run("Growth", bold = true, sizePt = 11f))))),
                                TableCell(listOf(Paragraph(runs = listOf(Run("+22%", bold = true, sizePt = 11f))))),
                            ),
                            header = false,
                        ),
                    ),
                    columnCount = 2,
                    borders = true,
                ),
                Paragraph(
                    runs = listOf(Run("Draft notes (confidential)", strike = true, sizePt = 11f)),
                    images = listOf(InlineImage(fakePng, widthDp = 120f, heightDp = 80f)),
                ),
            ),
        )

        val result = roundTrip(doc)

        assertEquals(6, result.blocks.size)

        // Title
        val title = result.blocks[0] as Paragraph
        assertEquals("Quarterly Strategy Report", title.runs.single().text)
        assertEquals(true, title.runs.single().bold)
        assertEquals(24f, title.runs.single().sizePt)

        // Bullet & Numbered
        val bullet = result.blocks[2] as Paragraph
        assertNotNull(bullet.list)
        assertEquals(ListKind.BULLET, bullet.list?.kind)

        val numbered = result.blocks[3] as Paragraph
        assertNotNull(numbered.list)
        assertEquals(ListKind.NUMBER, numbered.list?.kind)

        // Table
        val table = result.blocks[4] as Table
        assertEquals(2, table.columnCount)
        assertTrue(table.borders)
        assertTrue(table.rows[0].header)
        val headerCellText = (table.rows[0].cells[0].blocks[0] as Paragraph).runs.single().text
        assertEquals("Metric", headerCellText)

        // Last paragraph
        val last = result.blocks[5] as Paragraph
        assertTrue(last.runs.single().strike)
        assertEquals(1, last.images.size)
        val image = last.images.single()
        assertEquals(120f, image.widthDp)
        assertEquals(80f, image.heightDp)
    }
}
