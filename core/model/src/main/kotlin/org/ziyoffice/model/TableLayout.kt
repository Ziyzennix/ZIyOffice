package org.ziyoffice.model

/**
 * Where one table cell belongs in the grid, once horizontal and vertical merges
 * are taken into account.
 *
 * [row] and [column] are grid coordinates; [rowIndex] and [cellIndex] are the
 * position of the same cell inside [Table.rows], which is what [FocusPath]
 * addresses. Keeping both means the renderer can lay out a merged grid while the
 * editor still refers to cells the way the model stores them.
 */
data class PlacedCell(
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
    val cell: TableCell,
    val rowIndex: Int,
    val cellIndex: Int,
)

/**
 * Every cell that draws, with the grid area it covers.
 *
 * A cell that continues a vertical merge is not placed: the cell above it
 * already covers that area, and its content is empty by definition. So a 2-row
 * merge produces one placed cell with `rowSpan = 2`, not two cells.
 *
 * Ragged rows (a row with fewer cells than the grid) are tolerated: the walk
 * simply leaves the remaining columns empty instead of failing.
 */
fun Table.placedCells(): List<PlacedCell> {
    val placed = mutableListOf<PlacedCell>()

    /** Grid column of each cell, per row, so a merge can find its counterpart. */
    val gridStarts = rows.map { row ->
        var column = 0
        row.cells.map { cell ->
            val start = column
            column += cell.columnSpan.coerceAtLeast(1)
            start
        }
    }

    /** Continuation cells an earlier restart already covers: `row to cellIndex`. */
    val covered = mutableSetOf<Pair<Int, Int>>()

    rows.forEachIndexed { rowIndex, row ->
        row.cells.forEachIndexed { cellIndex, cell ->
            if (rowIndex to cellIndex in covered) return@forEachIndexed
            val column = gridStarts[rowIndex][cellIndex]
            val span = cell.columnSpan.coerceAtLeast(1)
            val rowSpan = if (cell.verticalMerge == VerticalMerge.RESTART) {
                val continuations = continuationCellsBelow(rowIndex, column, span, gridStarts)
                continuations.forEach { (r, c) -> covered += r to c }
                continuations.size + 1
            } else {
                1
            }
            placed += PlacedCell(
                row = rowIndex,
                column = column,
                rowSpan = rowSpan,
                columnSpan = span,
                cell = cell,
                rowIndex = rowIndex,
                cellIndex = cellIndex,
            )
        }
    }
    return placed
}

/**
 * The continuation cells a merge opened at [rowIndex] covers, below its own row.
 *
 * Continues only while the cells below line up exactly: same grid column, same
 * horizontal span, and marked as a continuation. A mismatch ends the merge
 * rather than stretching it over a cell that never agreed to it.
 *
 * A continuation with no restart above it is not in this list, and the caller
 * draws it as an ordinary cell. A malformed file must not make content vanish.
 */
private fun Table.continuationCellsBelow(
    rowIndex: Int,
    column: Int,
    span: Int,
    gridStarts: List<List<Int>>,
): List<Pair<Int, Int>> {
    val continuations = mutableListOf<Pair<Int, Int>>()
    var r = rowIndex + 1
    while (r < rows.size) {
        val row = rows[r]
        val starts = gridStarts[r]
        val match = row.cells.withIndex().firstOrNull { (index, candidate) ->
            index < starts.size &&
                starts[index] == column &&
                candidate.columnSpan.coerceAtLeast(1) == span &&
                candidate.verticalMerge == VerticalMerge.CONTINUE
        }
        if (match == null) break
        continuations += r to match.index
        r += 1
    }
    return continuations
}