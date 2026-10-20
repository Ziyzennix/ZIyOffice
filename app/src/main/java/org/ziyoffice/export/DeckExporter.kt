package org.ziyoffice.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import org.ziyoffice.model.SlideDeck

/**
 * Exports a [SlideDeck] to PDF: one landscape page per slide, title first.
 *
 * This is a text export, not a pixel copy of the original deck — images, themes
 * and animation are not rendered. It shows exactly what this app can read.
 */
object DeckExporter {

    private const val PAGE_WIDTH = 842 // A4 landscape at 72 dpi
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 48f

    fun export(deck: SlideDeck, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                typeface = Typeface.SANS_SERIF
            }
            deck.slides.forEachIndexed { index, slide ->
                val page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create(),
                )
                var y = MARGIN
                slide.lines.forEachIndexed { lineIndex, line ->
                    if (line.isEmpty()) {
                        y += 12f
                        return@forEachIndexed
                    }
                    val paint = if (lineIndex == 0) titlePaint else bodyPaint
                    val layout = StaticLayout.Builder
                        .obtain(line, 0, line.length, paint, (PAGE_WIDTH - 2 * MARGIN).toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                    page.canvas.save()
                    page.canvas.translate(MARGIN, y)
                    layout.draw(page.canvas)
                    page.canvas.restore()
                    y += layout.height + if (lineIndex == 0) 24f else 12f
                }
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }
}
