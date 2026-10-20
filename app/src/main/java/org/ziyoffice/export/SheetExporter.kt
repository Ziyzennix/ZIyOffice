package org.ziyoffice.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import java.io.OutputStream
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnName
import org.ziyoffice.model.evaluateFormula
import org.ziyoffice.model.formatCellValue
import org.ziyoffice.model.formatNumber

/**
 * Exports a [Workbook] to a paginated PDF using only the platform's [PdfDocument].
 *
 * Each sheet starts on a new page. Only the rectangle that actually holds data is
 * printed — a sheet with four used columns does not produce a hundred empty
 * pages — with column letters and a fixed row-number gutter, the way a
 * spreadsheet prints. Values are the same computed strings the grid shows, so a
 * formula prints its result, not `=SUM(A1:A9)`.
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
    private const val MIN_COLUMN_WIDTH = 56f
    private const val MAX_COLUMN_WIDTH = 190f
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
                val texts = mutableMapOf<Pair<Int, Int>, String>()
                val columns = (bounds.first.second..bounds.second.second).map { column ->
                    var widest = MIN_COLUMN_WIDTH
                    for (row in firstRow..lastRow) {
                        val text = displayValue(byPos[row to column], sheet)
                        if (text.isNotEmpty()) {
                            texts[row to column] = text
                            widest = widest.coerceAtLeast(
                                cellPaint.measureText(text) + 2 * CELL_PADDING,
                            )
                        }
                    }
                    Column(column, widest.coerceAtMost(MAX_COLUMN_WIDTH))
                }
                // The gutter is sized by the largest row number, never by the
                // first data column: a wide A column must not push the numbers
                // into a gap.
                val gutter = headerPaint.measureText((lastRow + 1).toString()) + 2 * GUTTER_PADDING
                val rowsPerPage = maxOf(
                    1,
                    ((PAGE_HEIGHT - 2 * MARGIN - HEADER_HEIGHT - ROW_HEIGHT) / ROW_HEIGHT).toInt(),
                )

                // Page breaks come from the tested helper, so "every row and
                // column prints exactly once" is a property, not an accident.
                SheetPagination.columnPages(
                    columnWidths = columns.map { it.width },
                    gutter = gutter,
                    pageWidth = PAGE_WIDTH - 2 * MARGIN,
                ).forEach { columnRange ->
                    val start = columnRange.first
                    val end = columnRange.last + 1
                    SheetPagination.rowPages(firstRow, lastRow, rowsPerPage).forEach { rowRange ->
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
                            x = MARGIN
                            canvas.drawText(
                                (row + 1).toString(),
                                x + GUTTER_PADDING,
                                y,
                                headerPaint,
                            )
                            x += gutter
                            columns.subList(start, end).forEach { column ->
                                val text = texts[row to column.index]
                                if (text != null) {
                                    val cell = byPos[row to column.index]
                                    val paint =
                                        if (cell?.format?.bold == true) boldCellPaint else cellPaint
                                    canvas.save()
                                    // Clip so a long value cannot spill into its
                                    // neighbour and turn the table into mush.
                                    canvas.clipRect(
                                        x,
                                        y - ROW_HEIGHT + 3f,
                                        x + column.width,
                                        y + 3f,
                                    )
                                    canvas.drawText(text, x + CELL_PADDING, y, paint)
                                    canvas.restore()
                                }
                                x += column.width
                            }
                            y += ROW_HEIGHT
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

    /**
     * The used rectangle as ((firstRow, firstColumn), (lastRow, lastColumn)), or
     * null when no cell holds anything. Empty cells that only carry formatting
     * still count: a pre-formatted table is something the user made.
     */
    private fun usedBounds(sheet: Sheet): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val used = sheet.cells.filter { it.value.isNotEmpty() || it.format != CellFormat() }
        if (used.isEmpty()) return null
        return Pair(
            used.minOf { it.row } to used.minOf { it.column },
            used.maxOf { it.row } to used.maxOf { it.column },
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
