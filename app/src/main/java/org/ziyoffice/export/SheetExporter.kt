package org.ziyoffice.export

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import java.io.OutputStream
import org.ziyoffice.model.BorderStyle
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.DEFAULT_COLUMN_WIDTH_CHARS
import org.ziyoffice.model.DEFAULT_ROW_HEIGHT_POINTS
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnName
import org.ziyoffice.model.columnWidth
import org.ziyoffice.model.evaluateFormula
import org.ziyoffice.model.formatCellValue
import org.ziyoffice.model.formatNumber
import org.ziyoffice.model.rowHeight

/**
 * Exports a [Workbook] to a paginated PDF using only the platform's [PdfDocument].
 *
 * Each sheet starts on a new page, and only the rectangle that holds data is
 * printed. Column letters and a row-number gutter make it look like a
 * spreadsheet. Values are the strings the grid shows, so a formula prints its
 * result, not `=SUM(A1:A9)`. Custom column widths and row heights scale onto
 * the page and stay proportional to each other.
 *
 * The page shows what the grid shows: the cell fill, the faint line every cell
 * carries, the borders the format asks for, the text colour and the alignment.
 * A bordered table that loses its borders in the PDF is not a faithful export.
 *
 * A sheet too wide or too long continues onto further pages with the column
 * header repeated and every row printed exactly once. Nothing is truncated to
 * make a page fit; dropping rows silently would be worse than no export.
 */
object SheetExporter {

    private const val PAGE_WIDTH = 842 // A4 landscape at 72 dpi: tables are wide
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 32f
    private const val ROW_HEIGHT = 18f
    private const val HEADER_HEIGHT = 20f
    private const val DEFAULT_PRINT_COLUMN_WIDTH = 56f
    private const val MIN_PRINT_COLUMN_WIDTH = 20f
    private const val MAX_PRINT_COLUMN_WIDTH = 500f
    private const val MAX_COLUMN_WIDTH = 190f
    private const val MIN_PRINT_ROW_HEIGHT = 10f
    private const val MAX_PRINT_ROW_HEIGHT = 240f
    private const val CELL_PADDING = 4f
    private const val GUTTER_PADDING = 6f

    /** One printable column: its sheet index and the width its content needs. */
    private class Column(val index: Int, val width: Float)

    fun export(workbook: Workbook, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            val sheets = workbook.sheets.ifEmpty { listOf(Sheet("Sheet1", emptyList())) }
            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = 0xFF3F4946.toInt()
            }
            val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                typeface = Typeface.SANS_SERIF
                color = 0xFF191C1B.toInt()
            }
            val boldCellPaint = TextPaint(cellPaint).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val gridPaint = Paint().apply {
                color = 0xFFC4CCC8.toInt()
                strokeWidth = 0.5f
                style = Paint.Style.STROKE
            }
            val borderPaints = BorderStyle.entries
                .filter { it != BorderStyle.NONE }
                .associateWith { style -> borderPaint(style) }
            val fillPaint = Paint().apply { style = Paint.Style.FILL }
            val textPaints = mutableMapOf<Pair<Boolean, Int>, TextPaint>()
            var pageNumber = 0
            sheets.forEach { sheet ->
                val bounds = usedBounds(sheet) ?: return@forEach
                val firstRow = bounds.first.first
                val lastRow = bounds.second.first
                val byPos = sheet.cells.associateBy { it.row to it.column }
                val merges = sheet.mergedRanges
                val anchors = merges.associateBy { it.top to it.left }
                val covered = buildSet {
                    merges.forEach { merge ->
                        for (row in merge.top..merge.bottom) {
                            for (column in merge.left..merge.right) {
                                if (row != merge.top || column != merge.left) add(row to column)
                            }
                        }
                    }
                }
                val texts = mutableMapOf<Pair<Int, Int>, String>()
                // Explicit sheet widths set the starting geometry; content can
                // still widen a narrow column, but never narrows a wide one.
                val widths = FloatArray(bounds.second.second - bounds.first.second + 1) { index ->
                    printColumnWidth(sheet, bounds.first.second + index)
                }
                for (row in firstRow..lastRow) {
                    for (column in bounds.first.second..bounds.second.second) {
                        if (row to column in covered) continue
                        val text = displayValue(byPos[row to column], sheet)
                        if (text.isEmpty()) continue
                        texts[row to column] = text
                        // A merged cell's text is measured against the width of
                        // the whole block below, not against one narrow column.
                        if (row to column !in anchors) {
                            val index = column - bounds.first.second
                            val needed = (cellPaint.measureText(text) + 2 * CELL_PADDING)
                                .coerceAtMost(MAX_COLUMN_WIDTH)
                            widths[index] = widths[index].coerceAtLeast(needed)
                        }
                    }
                }
                anchors.values.forEach { merge ->
                    val text = texts[merge.top to merge.left] ?: return@forEach
                    val first = merge.left.coerceAtLeast(bounds.first.second)
                    val last = merge.right.coerceAtMost(bounds.second.second)
                    if (last < first) return@forEach
                    val required = cellPaint.measureText(text) + 2 * CELL_PADDING
                    val available = (first..last).sumOf { column ->
                        widths[column - bounds.first.second].toDouble()
                    }.toFloat()
                    if (required > available) {
                        val extra = (required - available) / (last - first + 1)
                        for (column in first..last) {
                            widths[column - bounds.first.second] += extra
                        }
                    }
                }
                val columns = widths.mapIndexed { index, width ->
                    Column(bounds.first.second + index, width)
                }
                // The gutter is sized by the largest row number, never by the
                // first data column: a wide A column must not push the numbers
                // into a gap.
                val gutter = headerPaint.measureText((lastRow + 1).toString()) + 2 * GUTTER_PADDING
                val rowHeights = (firstRow..lastRow).map { row -> printRowHeight(sheet, row) }
                val availableRowsHeight =
                    PAGE_HEIGHT - 2 * MARGIN - HEADER_HEIGHT - ROW_HEIGHT

                // Page breaks come from the tested helper, so "every row and
                // column prints exactly once" is a property, not an accident.
                SheetPagination.columnPages(
                    columnWidths = columns.map { it.width },
                    gutter = gutter,
                    pageWidth = PAGE_WIDTH - 2 * MARGIN,
                ).forEach { columnRange ->
                    val start = columnRange.first
                    val end = columnRange.last + 1
                    SheetPagination.rowPagesByHeight(
                        first = firstRow,
                        last = lastRow,
                        rowHeights = rowHeights,
                        availableHeight = availableRowsHeight,
                    ).forEach { rowRange ->
                        val rowStart = rowRange.first
                        val rowEnd = rowRange.last
                        pageNumber++
                        val page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
                        )
                        val canvas = page.canvas
                        var y = MARGIN
                        val range = if (end - start == 1) {
                            columnName(columns[start].index)
                        } else {
                            "${columnName(columns[start].index)}\u2013" +
                                columnName(columns[end - 1].index)
                        }
                        canvas.drawText(
                            "${sheet.name}  \u00B7  columns $range  \u00B7  " +
                                "rows ${rowStart + 1}\u2013${rowEnd + 1}",
                            MARGIN,
                            y,
                            headerPaint,
                        )
                        y += HEADER_HEIGHT

                        var x = MARGIN + gutter
                        columns.subList(start, end).forEach { column ->
                            canvas.drawText(columnName(column.index), x + CELL_PADDING, y, headerPaint)
                            x += column.width
                        }
                        canvas.drawLine(MARGIN, y + 4f, x, y + 4f, gridPaint)
                        y += ROW_HEIGHT

                        for (row in rowStart..rowEnd) {
                            val printHeight = rowHeights.getOrElse(row - firstRow) { ROW_HEIGHT }
                            val baseline = y + printHeight - 3f
                            x = MARGIN
                            canvas.drawText(
                                (row + 1).toString(),
                                x + GUTTER_PADDING,
                                baseline,
                                headerPaint,
                            )
                            x += gutter
                            columns.subList(start, end).forEach { column ->
                                val cell = byPos[row to column.index]
                                val format = cell?.format ?: CellFormat()
                                // A merged anchor owns the width of its whole
                                // block. Without this a title spanning A:C
                                // would be clipped back to column A.
                                val merge = anchors[row to column.index]
                                var spanWidth = column.width
                                if (merge != null) {
                                    var index = start + (column.index - columns[start].index)
                                    while (index + 1 < end &&
                                        columns[index + 1].index <= merge.right
                                    ) {
                                        index++
                                        spanWidth += columns[index].width
                                    }
                                }
                                val isCovered = row to column.index in covered
                                if (!isCovered) {
                                    // Fill, grid and borders are drawn for the
                                    // cell itself; a cell hidden behind a merge
                                    // is part of its anchor's block and must
                                    // not draw a line through it.
                                    drawCellBackground(
                                        canvas = canvas,
                                        format = format,
                                        left = x,
                                        top = y,
                                        right = x + spanWidth,
                                        bottom = y + printHeight,
                                        fillPaint = fillPaint,
                                        gridPaint = gridPaint,
                                        borderPaints = borderPaints,
                                    )
                                }
                                val text = texts[row to column.index]
                                if (text != null) {
                                    val paint = textPaintFor(
                                        format = format,
                                        plain = cellPaint,
                                        boldPaint = boldCellPaint,
                                        cache = textPaints,
                                    )
                                    val textX = when (format.alignment) {
                                        CellAlignment.CENTER ->
                                            x + (spanWidth - paint.measureText(text)) / 2f
                                        CellAlignment.RIGHT ->
                                            x + spanWidth - CELL_PADDING - paint.measureText(text)
                                        else -> x + CELL_PADDING
                                    }
                                    canvas.save()
                                    // Clip so a long value cannot spill into its
                                    // neighbour and turn the table into mush.
                                    canvas.clipRect(
                                        x,
                                        y + 1f,
                                        x + spanWidth,
                                        y + printHeight,
                                    )
                                    canvas.drawText(text, textX, baseline, paint)
                                    canvas.restore()
                                }
                                x += column.width
                            }
                            y += printHeight
                        }
                        pdf.finishPage(page)
                    }
                }
            }
            if (pageNumber == 0) {
                // Every sheet was empty: one honest page instead of a zero-page file.
                val page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create(),
                )
                page.canvas.drawText(
                    "This workbook has no data to print.",
                    MARGIN,
                    MARGIN + 20f,
                    cellPaint,
                )
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    /** Excel character units mapped onto the PDF's default 56-point column. */
    private fun printColumnWidth(sheet: Sheet, column: Int): Float =
        (DEFAULT_PRINT_COLUMN_WIDTH * (sheet.columnWidth(column) / DEFAULT_COLUMN_WIDTH_CHARS))
            .coerceIn(MIN_PRINT_COLUMN_WIDTH, MAX_PRINT_COLUMN_WIDTH)

    /**
     * Draws what the cell looks like on screen: its fill, the faint grid every
     * cell carries, and the borders the format asks for.
     */
    private fun drawCellBackground(
        canvas: Canvas,
        format: CellFormat,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        fillPaint: Paint,
        gridPaint: Paint,
        borderPaints: Map<BorderStyle, Paint>,
    ) {
        format.fillRgb?.let { rgb ->
            fillPaint.color = 0xFF000000.toInt() or rgb
            canvas.drawRect(left, top, right, bottom, fillPaint)
        }
        // The faint line every cell carries on screen, so an empty table still
        // reads as a table.
        canvas.drawRect(left, top, right, bottom, gridPaint)

        val borders = format.borders
        if (borders.isEmpty) return
        borders.top.takeIf { it != BorderStyle.NONE }?.let { style ->
            canvas.drawLine(left, top, right, top, borderPaints.getValue(style))
        }
        borders.bottom.takeIf { it != BorderStyle.NONE }?.let { style ->
            canvas.drawLine(left, bottom, right, bottom, borderPaints.getValue(style))
        }
        borders.left.takeIf { it != BorderStyle.NONE }?.let { style ->
            canvas.drawLine(left, top, left, bottom, borderPaints.getValue(style))
        }
        borders.right.takeIf { it != BorderStyle.NONE }?.let { style ->
            canvas.drawLine(right, top, right, bottom, borderPaints.getValue(style))
        }
    }

    /** A paint for the cell's own border style. */
    private fun borderPaint(lineStyle: BorderStyle): Paint = Paint().apply {
        color = 0xFF191C1B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = when (lineStyle) {
            BorderStyle.MEDIUM -> 1.5f
            BorderStyle.THICK -> 2.25f
            else -> 0.75f
        }
        when (lineStyle) {
            BorderStyle.DASHED -> {
                pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            }
            BorderStyle.DOTTED -> {
                pathEffect = DashPathEffect(floatArrayOf(1f, 2f), 0f)
                strokeCap = Paint.Cap.ROUND
            }
            else -> Unit
        }
    }

    /** The paint a cell's text is drawn with, honouring bold and colour. */
    private fun textPaintFor(
        format: CellFormat,
        plain: TextPaint,
        boldPaint: TextPaint,
        cache: MutableMap<Pair<Boolean, Int>, TextPaint>,
    ): TextPaint {
        val colour = format.colorRgb ?: return if (format.bold) boldPaint else plain
        return cache.getOrPut(format.bold to colour) {
            TextPaint(if (format.bold) boldPaint else plain).apply {
                this.color = 0xFF000000.toInt() or colour
            }
        }
    }

    /** Worksheet row points mapped onto the PDF's default 18-point row. */
    private fun printRowHeight(sheet: Sheet, row: Int): Float =
        (ROW_HEIGHT * (sheet.rowHeight(row) / DEFAULT_ROW_HEIGHT_POINTS))
            .coerceIn(MIN_PRINT_ROW_HEIGHT, MAX_PRINT_ROW_HEIGHT)

    /**
     * The used rectangle as ((firstRow, firstColumn), (lastRow, lastColumn)), or
     * null when no cell holds anything. Empty cells that only carry formatting
     * still count: a pre-formatted table is something the user made.
     */
    private fun usedBounds(sheet: Sheet): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val used = sheet.cells.filter { it.value.isNotEmpty() || it.format != CellFormat() }
        if (used.isEmpty()) return null
        val firstRow = used.minOf { it.row }
        val lastRow = used.maxOf { it.row }
        val firstColumn = used.minOf { it.column }
        val lastColumn = used.maxOf { it.column }
        // A merge belongs to the printed area when it touches a used cell, and
        // the whole block must fit: a three-column title merge anchored in A
        // makes C part of the used width.
        val mergedBounds = sheet.mergedRanges.filter { merge ->
            merge.top <= lastRow &&
                merge.bottom >= firstRow &&
                merge.left <= lastColumn &&
                merge.right >= firstColumn
        }
        return Pair(
            minOf(firstRow, mergedBounds.minOfOrNull { it.top } ?: firstRow) to
                minOf(firstColumn, mergedBounds.minOfOrNull { it.left } ?: firstColumn),
            maxOf(lastRow, mergedBounds.maxOfOrNull { it.bottom } ?: lastRow) to
                maxOf(lastColumn, mergedBounds.maxOfOrNull { it.right } ?: lastColumn),
        )
    }

    /** Same strings the grid shows: formula results and formatted numbers. */
    private fun displayValue(cell: Cell?, sheet: Sheet): String {
        if (cell == null || cell.value.isEmpty()) return ""
        val raw = cell.value
        if (raw.startsWith("=")) {
            return formatCellValue(evaluateFormula(sheet, raw).value, cell.format.numberFormat)
        }
        val number = raw.toDoubleOrNull()
        return if (number != null) formatNumber(number, cell.format.numberFormat) else raw
    }
}
