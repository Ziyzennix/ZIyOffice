package org.ziyoffice.export

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.StrikethroughSpan
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.ui.listMarker

/**
 * Exports a [Document] to a real PDF with the platform's [PdfDocument] and text
 * layout. No third-party code, no network.
 *
 * Fidelity is layout-based, not pixel-based: paragraphs flow onto A4 pages,
 * styled runs keep their styling, lists keep their markers, page breaks are
 * honoured, images scale to the text column and tables are drawn as a grid.
 */
object PdfExporter {
    /** Writes the rendered PDF; the caller owns the stream. */
    fun export(document: Document, output: OutputStream) {
        Renderer(output).run(document)
    }
}

private const val PAGE_WIDTH = 595 // A4 at 72 dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 57f // 2 cm
private const val DEFAULT_SIZE_PT = 11f
private const val DEFAULT_SPACING_TWIPS = 160
private const val DEFAULT_LIST_INDENT_TWIPS = 720
private const val DEFAULT_TEXT_COLOR = 0xFF23262B.toInt()
private const val HEADER_FOOTER_GAP = 8f

private class Renderer(private val output: OutputStream) {
    private val pdf = PdfDocument()
    private val contentWidth = PAGE_WIDTH - 2 * MARGIN
    private val counters = mutableMapOf<Pair<Int, Int>, Int>()
    private val rulePaint = Paint().apply {
        color = 0xFF9AA09B.toInt()
        strokeWidth = 0.7f
        style = Paint.Style.STROKE
    }
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas = Canvas()
    private var y = MARGIN

    private var headerBlocks: List<Block> = emptyList()
    private var footerBlocks: List<Block> = emptyList()

    fun run(document: Document) {
        headerBlocks = document.header.orEmpty()
        footerBlocks = document.footer.orEmpty()
        try {
            newPage()
            drawBlocks(document.blocks, contentWidth, 0f)
            finishPage()
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun newPage() {
        finishPage()
        pageNumber += 1
        val started = pdf.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
        )
        page = started
        canvas = started.canvas
        y = MARGIN
        drawHeader()
    }

    /**
     * The header is drawn at the top margin of every page and the body starts
     * below it, so header text can never overlap the first paragraph.
     */
    private fun drawHeader() {
        if (headerBlocks.isEmpty()) return
        val height = drawBand(headerBlocks, MARGIN)
        y = MARGIN + height + HEADER_FOOTER_GAP
    }

    private fun finishPage() {
        val current = page
        if (current != null) {
            if (footerBlocks.isNotEmpty()) {
                val height = bandHeight(footerBlocks)
                drawBand(footerBlocks, PAGE_HEIGHT - MARGIN - height)
            }
            pdf.finishPage(current)
        }
        page = null
    }

    private fun drawBand(blocks: List<Block>, top: Float): Float {
        val savedY = y
        y = top
        blocks.forEach { block ->
            when (block) {
                is Paragraph -> drawParagraph(block, contentWidth, 0f)
                is Table -> drawTable(block, contentWidth, 0f)
            }
        }
        val height = y - top
        y = savedY
        return height
    }

    /**
     * Height the footer will occupy. Measured with the same layout code the page
     * uses, and with a throwaway counter map so a measurement does not advance
     * the document's real list numbering.
     */
    private fun bandHeight(blocks: List<Block>): Float {
        val measureCounters = mutableMapOf<Pair<Int, Int>, Int>()
        var height = 0f
        blocks.forEach { block ->
            when (block) {
                is Paragraph -> {
                    val marker = block.list?.let { listMarker(it, measureCounters) }
                    val layout = layoutOf(block, paragraphText(block, marker), contentWidth)
                    val before = (block.spacingBeforeTwips ?: 0) / 20f
                    val after = (block.spacingAfterTwips ?: DEFAULT_SPACING_TWIPS) / 20f
                    height += before + layout.height + after
                }
                is Table -> height += block.rows.size * 24f
            }
        }
        return height
    }

    private fun ensureSpace(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN && y > MARGIN) newPage()
    }

    private fun drawBlocks(blocks: List<Block>, width: Float, indentBase: Float) {
        for (block in blocks) {
            when (block) {
                is Paragraph -> drawParagraph(block, width, indentBase)
                is Table -> drawTable(block, width, indentBase)
            }
        }
    }

    private fun drawParagraph(paragraph: Paragraph, width: Float, indentBase: Float) {
        if (paragraph.pageBreakBefore && y > MARGIN) newPage()
        val marker = paragraph.list?.let { listMarker(it, counters) }
        val indentTwips = paragraph.indentTwips
            ?: paragraph.list?.let { DEFAULT_LIST_INDENT_TWIPS * (it.level + 1) }
            ?: 0
        val indent = max(0f, indentTwips / 20f)
        val x = MARGIN + indentBase + indent
        val textWidth = (width - indent).coerceAtLeast(40f)
        val layout = layoutOf(paragraph, paragraphText(paragraph, marker), textWidth)
        val before = (paragraph.spacingBeforeTwips ?: 0) / 20f
        val after = (paragraph.spacingAfterTwips ?: DEFAULT_SPACING_TWIPS) / 20f
        ensureSpace(before + layout.height + after)
        y += before
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + after
        paragraph.images.forEach { image -> drawImage(image, x, textWidth) }
    }

    private fun drawImage(image: InlineImage, x: Float, maxWidth: Float) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size) ?: return
        // dp (1/96 inch) -> points (1/72 inch); same 0.75 factor for both.
        val naturalWidth = (image.widthDp ?: (bitmap.width * 0.75f)) * 0.75f
        val naturalHeight = (image.heightDp ?: (bitmap.height * 0.75f)) * 0.75f
        if (naturalWidth <= 0f || naturalHeight <= 0f) return
        val maxHeight = PAGE_HEIGHT - 2 * MARGIN
        val scale = min(
            1f,
            min(maxWidth / naturalWidth, maxHeight / naturalHeight),
        )
        val width = naturalWidth * scale
        val height = naturalHeight * scale
        ensureSpace(height + 6f)
        canvas.drawBitmap(bitmap, null, RectF(x, y + 4f, x + width, y + 4f + height), null)
        y += height + 6f
    }

    private fun drawTable(table: Table, width: Float, indentBase: Float) {
        val columns = table.columnCount.coerceAtLeast(1)
        val columnWidth = width / columns
        val padding = 5f
        for (row in table.rows) {
            var cursor = MARGIN + indentBase
            var rowHeight = 0f
            val cells = mutableListOf<Triple<StaticLayout, Float, Float>>()
            for (cell in row.cells) {
                val span = cell.columnSpan.coerceAtLeast(1)
                val cellWidth = (columnWidth * span - 2 * padding).coerceAtLeast(12f)
                val insideWidth = columnWidth * span
                val layout = layoutOf(
                    paragraph = firstParagraphOf(cell.blocks),
                    text = cellText(cell.blocks),
                    width = cellWidth,
                )
                cells += Triple(layout, cursor, insideWidth)
                rowHeight = max(rowHeight, layout.height + 2 * padding)
                cursor += insideWidth
            }
            ensureSpace(rowHeight)
            for ((layout, cellX, _) in cells) {
                canvas.save()
                canvas.translate(cellX + padding, y + padding)
                layout.draw(canvas)
                canvas.restore()
            }
            if (table.borders) {
                val left = MARGIN + indentBase
                val right = cursor
                canvas.drawLine(left, y + rowHeight, right, y + rowHeight, rulePaint)
                for ((_, cellX, _) in cells.dropLast(1)) {
                    canvas.drawLine(cellX, y, cellX, y + rowHeight, rulePaint)
                }
                canvas.drawLine(right, y, right, y + rowHeight, rulePaint)
            }
            y += rowHeight
        }
        if (table.borders) y += 4f
    }

    /** One paragraph (first block) governs alignment/style of a whole cell. */
    private fun firstParagraphOf(blocks: List<Block>): Paragraph? =
        blocks.filterIsInstance<Paragraph>().firstOrNull()

    private fun cellText(blocks: List<Block>): Spanned {
        val builder = SpannableStringBuilder()
        for (block in blocks) {
            if (builder.isNotEmpty()) builder.append('\n')
            when (block) {
                is Paragraph -> appendRuns(builder, block.runs)
                is Table -> {
                    // A nested table flattens to its text rows.
                    block.rows.forEachIndexed { index, row ->
                        if (index > 0) builder.append('\n')
                        row.cells.forEachIndexed { cellIndex, cell ->
                            if (cellIndex > 0) builder.append("  ")
                            builder.append(cellText(cell.blocks))
                        }
                    }
                }
            }
        }
        if (builder.isEmpty()) builder.append('\u00A0')
        return builder
    }

    private fun paragraphText(paragraph: Paragraph, marker: String?): Spanned {
        val builder = SpannableStringBuilder()
        val prefix = marker?.let { "$it  " }
        if (prefix != null) {
            builder.append(prefix)
            // The marker borrows the first run's style so it sits with its text.
            paragraph.runs.firstOrNull()?.let { run ->
                applyRunSpans(builder, run, 0, prefix.length)
            }
        }
        appendRuns(builder, paragraph.runs)
        if (builder.isEmpty()) builder.append('\u00A0')
        return builder
    }

    private fun appendRuns(builder: SpannableStringBuilder, runs: List<Run>) {
        for (run in runs) {
            val start = builder.length
            builder.append(run.text)
            val end = builder.length
            if (end > start) applyRunSpans(builder, run, start, end)
        }
    }

    private fun applyRunSpans(
        builder: SpannableStringBuilder,
        run: Run,
        start: Int,
        end: Int,
    ) {
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val style = when {
            run.bold && run.italic -> Typeface.BOLD_ITALIC
            run.bold -> Typeface.BOLD
            run.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (style != Typeface.NORMAL) builder.setSpan(StyleSpan(style), start, end, flags)
        if (run.underline) builder.setSpan(UnderlineSpan(), start, end, flags)
        if (run.strike) builder.setSpan(StrikethroughSpan(), start, end, flags)
        builder.setSpan(
            AbsoluteSizeSpan((run.sizePt ?: DEFAULT_SIZE_PT).roundToInt(), false),
            start,
            end,
            flags,
        )
        val color = run.colorRgb?.let { 0xFF000000.toInt() or it } ?: DEFAULT_TEXT_COLOR
        builder.setSpan(ForegroundColorSpan(color), start, end, flags)
    }

    private fun layoutOf(paragraph: Paragraph?, text: Spanned, width: Float): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = DEFAULT_SIZE_PT
            color = DEFAULT_TEXT_COLOR
            typeface = Typeface.SANS_SERIF
        }
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.roundToInt().coerceAtLeast(12))
            .setAlignment((paragraph?.alignment ?: ParagraphAlignment.START).toLayoutAlignment())
            .setIncludePad(false)
        // The line spacing percentage scales the line height, as in the editor
        // (100% = the default leading).
        paragraph?.lineSpacingPercent?.let { builder.setLineSpacing(0f, it / 100f) }
        if (paragraph?.alignment == ParagraphAlignment.JUSTIFY) {
            builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
        }
        return builder.build()
    }
}

private fun ParagraphAlignment.toLayoutAlignment(): Layout.Alignment = when (this) {
    ParagraphAlignment.START -> Layout.Alignment.ALIGN_NORMAL
    ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
    ParagraphAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    ParagraphAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
}
