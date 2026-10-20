package org.ziyoffice.model

/**
 * Copy and paste between cells.
 *
 * A copied range is plain tab-separated text, the shape every spreadsheet puts
 * on the clipboard, so a range can travel to another app and back. A cell keeps
 * its own text, so a copied formula stays a formula.
 */

/** The values of [range] as tab-separated text, one line per row. */
fun Sheet.tsvOf(range: CellRange): String {
    val byPosition = cells.associateBy { it.row to it.column }
    return (range.top..range.bottom).joinToString("\n") { row ->
        (range.left..range.right).joinToString("\t") { column ->
            byPosition[row to column]?.value.orEmpty()
        }
    }
}

/**
 * Writes tab-separated [text] into the sheet with its first cell at
 * ([row], [column]). Empty cells in the text clear the cell they land on.
 */
fun Sheet.withPastedText(text: String, row: Int, column: Int): Sheet {
    if (row < 0 || column < 0) return this
    val lines = text.split("\n").map { it.trimEnd('\r') }
    if (lines.isEmpty()) return this

    val pasted = mutableListOf<Pair<Pair<Int, Int>, String>>()
    lines.forEachIndexed { lineIndex, line ->
        line.split("\t").forEachIndexed { columnIndex, value ->
            val target = (row + lineIndex) to (column + columnIndex)
            pasted += target to value
        }
    }
    if (pasted.isEmpty()) return this

    val byPosition = cells.associateBy { it.row to it.column }
    val written = pasted.associate { (position, value) -> position to value }
    val updated = cells.mapNotNull { cell ->
        val value = written[cell.row to cell.column]
        when {
            value == null -> cell
            value.isEmpty() -> cell.copy(value = "", cached = null)
            else -> cell.copy(value = value, cached = null)
        }
    }
    val added = pasted
        .filter { (position, value) -> value.isNotEmpty() && position !in byPosition }
        .map { (position, value) ->
            Cell(position.first, position.second, value)
        }
    val lastRow = pasted.maxOf { it.first.first }
    val lastColumn = pasted.maxOf { it.first.second }
    return copy(
        cells = (updated + added).sortedWith(compareBy({ it.row }, { it.column })),
        rowCount = maxOf(rowCount, lastRow + 1),
        columnCount = maxOf(columnCount, lastColumn + 1),
    )
}
