package org.ziyoffice.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import java.io.OutputStream
import org.ziyoffice.model.Cell
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
 * Each sheet starts on a new page. Only the rectangle that actually holds data is
 * printed — a sheet with four used columns does not produce a hundred empty
 * pages — with column letters and a fixed row-number gutter, the way a
 * spreadsheet prints. Values are the same computed strings the grid shows, so a
 * formula prints its result, not `=SUM(A1:A9)`. Explicit column widths and row
 * heights scale onto the page while staying proportional to each other.
 *
 * A sheet too wide or too long for one page continues onto further pages with
 * the column header repeated and every row printed exactly once. Nothing is
 * truncated to make the page fit: an export that silently dropped rows would be
 * worse than no export at all.
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
            val rulePaint = Paint().apply {
                color = 0xFFC4CCC8.toInt()
                strokeWidth = 0.5f
                style = Paint.Style.STROKE
            }
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
                        canvas.drawLine(MARGIN, y + 4f, x, y + 4f, rulePaint)
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
                                val text = texts[row to column.index]
                                if (text != null) {
                                    val cell = byPos[row to column.index]
                                    val paint =
                                        if (cell?.format?.bold == true) boldCellPaint else cellPaint
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
                                    canvas.save()
                                    // Clip so a long value cannot spill into its
                                    // neighbour and turn the table into mush.
                                    canvas.clipRect(
                                        x,
                                        y + 1f,
                                        x + spanWidth,
                                        y + printHeight,
                                    )
                                    canvas.drawText(text, x + CELL_PADDING, baseline, paint)
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
