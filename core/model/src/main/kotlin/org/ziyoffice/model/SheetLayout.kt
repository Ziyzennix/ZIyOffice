package org.ziyoffice.model

/**
 * A run of columns sharing an explicit width, mirroring the `<col min max>`
 * element in a worksheet. Widths are Excel character units: 8.43 is the default
 * Calibri column, and 255 is the format's maximum.
 */
data class ColumnWidth(
    val firstColumn: Int,
    val lastColumn: Int,
    val width: Float,
) {
    val columnSpan: Int get() = lastColumn - firstColumn + 1

    fun covers(column: Int): Boolean = column in firstColumn..lastColumn
}

/** Default column width in Excel character units. */
const val DEFAULT_COLUMN_WIDTH_CHARS = 8.43f

/** Default row height in points, the unit worksheet row heights use. */
const val DEFAULT_ROW_HEIGHT_POINTS = 15f

const val MIN_COLUMN_WIDTH_CHARS = 1f
const val MAX_COLUMN_WIDTH_CHARS = 255f
const val MIN_ROW_HEIGHT_POINTS = 8f
const val MAX_ROW_HEIGHT_POINTS = 409f

/** XFD, the last column an `.xlsx` worksheet can address. */
const val MAX_SHEET_COLUMNS = 16_384

/** The last row an `.xlsx` worksheet can address. */
const val MAX_SHEET_ROWS = 1_048_576

/** The explicit width for [column], or the worksheet default. */
fun Sheet.columnWidth(column: Int): Float =
    columnWidths.lastOrNull { it.covers(column) }?.width ?: DEFAULT_COLUMN_WIDTH_CHARS

/** The explicit height for [row], or the worksheet default. */
fun Sheet.rowHeight(row: Int): Float = rowHeights[row] ?: DEFAULT_ROW_HEIGHT_POINTS

/**
 * Sets one column's width, splitting any wider run that used to cover it so the
 * remaining columns keep their old width.
 */
fun Sheet.withColumnWidth(column: Int, width: Float): Sheet {
    if (column !in 0 until MAX_SHEET_COLUMNS) return this
    val clamped = width.coerceIn(MIN_COLUMN_WIDTH_CHARS, MAX_COLUMN_WIDTH_CHARS)
    val rebuilt = columnWidths.flatMap { range ->
        when {
            !range.covers(column) -> listOf(range)
            range.firstColumn == range.lastColumn -> emptyList()
            else -> buildList {
                if (column > range.firstColumn) {
                    add(range.copy(lastColumn = column - 1))
                }
                if (column < range.lastColumn) {
                    add(range.copy(firstColumn = column + 1))
                }
            }
        }
    } + ColumnWidth(column, column, clamped)
    return copy(columnWidths = rebuilt.sortedWith(compareBy({ it.firstColumn }, { it.lastColumn })))
}

/** Sets one row's height in points. */
fun Sheet.withRowHeight(row: Int, height: Float): Sheet {
    if (row < 0) return this
    val clamped = height.coerceIn(MIN_ROW_HEIGHT_POINTS, MAX_ROW_HEIGHT_POINTS)
    return copy(rowHeights = rowHeights + (row to clamped))
}

/**
 * Drops malformed and non-positive widths from a file, clamps the rest to the
 * spreadsheet limits, and sorts them so round trips stay stable.
 */
fun List<ColumnWidth>.normaliseColumnWidths(): List<ColumnWidth> =
    filter {
        it.firstColumn in 0 until MAX_SHEET_COLUMNS &&
            it.lastColumn >= it.firstColumn &&
            it.width > 0f
    }
        .map {
            it.copy(
                lastColumn = it.lastColumn.coerceAtMost(MAX_SHEET_COLUMNS - 1),
                width = it.width.coerceIn(MIN_COLUMN_WIDTH_CHARS, MAX_COLUMN_WIDTH_CHARS),
            )
        }
        .sortedWith(compareBy({ it.firstColumn }, { it.lastColumn }))
