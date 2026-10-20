package org.ziyoffice.model

/**
 * A rectangular block of cells, 0-based inclusive edges.
 *
 * The constructor never trusts a file: a range with its bottom above its top
 * is normalised by [of] before it reaches the model.
 */
data class CellRange(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
) {
    val rowSpan: Int get() = bottom - top + 1
    val columnSpan: Int get() = right - left + 1

    fun contains(row: Int, column: Int): Boolean =
        row in top..bottom && column in left..right

    fun intersects(other: CellRange): Boolean =
        top <= other.bottom && bottom >= other.top &&
            left <= other.right && right >= other.left

    companion object {
        /** Normalises two corners, so either order produces the same range. */
        fun of(a: CellRangeCorner, b: CellRangeCorner): CellRange = CellRange(
            top = minOf(a.row, b.row),
            left = minOf(a.column, b.column),
            bottom = maxOf(a.row, b.row),
            right = maxOf(a.column, b.column),
        )

        /** A1-notation range ("A1:C3") to a model range, or null if malformed. */
        fun parse(reference: String): CellRange? {
            val parts = reference.split(':')
            if (parts.size != 2) return null
            val first = parseCorner(parts[0]) ?: return null
            val second = parseCorner(parts[1]) ?: return null
            return of(first, second)
        }

        private fun parseCorner(text: String): CellRangeCorner? {
            val letters = text.takeWhile { it.isLetter() }
            val digits = text.dropWhile { it.isLetter() }
            if (letters.isEmpty() || digits.isEmpty()) return null
            val column = columnIndex(letters)
            val row = digits.toIntOrNull()?.minus(1) ?: return null
            if (row < 0 || column < 0) return null
            return CellRangeCorner(row, column)
        }
    }
}

/** A row/column pair used while building a [CellRange]. */
data class CellRangeCorner(val row: Int, val column: Int)

/** The merged block covering this cell, if any. */
fun Sheet.mergeAt(row: Int, column: Int): CellRange? =
    mergedRanges.firstOrNull { it.contains(row, column) }

/** Whether this cell is the top-left cell of a merged block. */
fun Sheet.isMergeAnchor(row: Int, column: Int): Boolean =
    mergedRanges.any { it.top == row && it.left == column }

/**
 * Merges [range] into the sheet.
 *
 * Ranges that overlap an existing merge replace it, so the grid can never carry
 * two claims on the same cell. Values outside the top-left cell are cleared:
 * that is what every spreadsheet does, and keeping invisible values would make
 * "unmerge" reveal text the user thought they had removed.
 */
fun Sheet.withMergedRange(range: CellRange): Sheet {
    val clamped = range.copy(
        top = range.top.coerceAtLeast(0),
        left = range.left.coerceAtLeast(0),
        bottom = range.bottom.coerceAtLeast(0),
        right = range.right.coerceAtLeast(0),
    )
    if (clamped.rowSpan <= 1 && clamped.columnSpan <= 1) return this

    val anchor = clamped.top to clamped.left
    val remaining = mergedRanges.filterNot { it.intersects(clamped) } + clamped
    val cells = cells.filterNot { cell ->
        clamped.contains(cell.row, cell.column) &&
            (cell.row to cell.column) != anchor &&
            cell.value.isNotEmpty()
    }
    return copy(
        cells = cells,
        rowCount = maxOf(rowCount, clamped.bottom + 1),
        columnCount = maxOf(columnCount, clamped.right + 1),
        mergedRanges = remaining.normaliseMerges(),
    )
}

/** Removes the merged block covering [row], [column], if there is one. */
fun Sheet.withMergeClearedAt(row: Int, column: Int): Sheet {
    val merge = mergeAt(row, column) ?: return this
    return copy(mergedRanges = mergedRanges.filterNot { it == merge })
}

/** Removes every merged block that touches [range], like Excel's unmerge. */
fun Sheet.withMergesClearedIn(range: CellRange): Sheet =
    copy(mergedRanges = mergedRanges.filterNot { it.intersects(range) })

/**
 * Drops malformed, duplicate and overlapping ranges and sorts them for stable
 * saves. Overlaps are invalid in a spreadsheet; keeping the first is the only
 * order-independent reading, and drawing both would hide a cell twice.
 */
fun List<CellRange>.normaliseMerges(): List<CellRange> {
    val valid = filter {
        it.top >= 0 && it.left >= 0 && it.bottom >= it.top && it.right >= it.left &&
            (it.rowSpan > 1 || it.columnSpan > 1)
    }.sortedWith(compareBy({ it.top }, { it.left }, { it.bottom }, { it.right }))

    val kept = mutableListOf<CellRange>()
    valid.forEach { range ->
        if (kept.none { it.intersects(range) }) kept += range
    }
    return kept
}

/**
 * One row band of the on-screen grid: [rowCount] model rows compressed into one
 * item because a vertical merge runs through them.
 */
data class SheetRowBand(val startRow: Int, val rowCount: Int) {
    val endRow: Int get() = startRow + rowCount - 1
}

/**
 * A cell's place in the rendered grid. [rowSpan] and [columnSpan] are the size
 * of the cell's rectangle in grid units; a continuation cell of a merge is not
 * placed at all, because its anchor already covers the area.
 */
data class SheetCellPlacement(
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
)

/**
 * Splits the sheet's rows into bands that a lazy list can render without
 * clipping a vertical merge. A band grows until no merge that starts inside it
 * reaches past its end.
 */
fun Sheet.rowBands(): List<SheetRowBand> {
    val rows = rowCount.coerceAtLeast(1)
    val starts = mergedRanges
        .filter { it.bottom > it.top }
        .groupBy { it.top }

    val bands = mutableListOf<SheetRowBand>()
    var row = 0
    while (row < rows) {
        var span = 1
        while (true) {
            val next = (row until (row + span).coerceAtMost(rows)).fold(span) { acc, r ->
                val reach = starts[r].orEmpty().maxOfOrNull { it.bottom - row + 1 } ?: 0
                maxOf(acc, reach)
            }.coerceAtMost(rows - row)
            if (next <= span) break
            span = next
        }
        bands += SheetRowBand(row, span)
        row += span
    }
    return bands
}

/**
 * Every cell that draws in [band], with merged anchors carrying their real
 * span. Cells without a merge are still returned: the grid needs their borders,
 * backgrounds and tap targets even when they hold no value.
 */
fun Sheet.cellPlacements(band: SheetRowBand): List<SheetCellPlacement> {
    val placements = mutableListOf<SheetCellPlacement>()
    for (row in band.startRow..band.endRow) {
        var column = 0
        while (column < columnCount.coerceAtLeast(1)) {
            val merge = mergeAt(row, column)
            when {
                merge == null -> {
                    placements += SheetCellPlacement(row, column, 1, 1)
                    column += 1
                }
                merge.top == row && merge.left == column -> {
                    placements += SheetCellPlacement(
                        row = row,
                        column = column,
                        rowSpan = merge.rowSpan.coerceAtMost(band.endRow - row + 1),
                        columnSpan = merge.columnSpan,
                    )
                    column += merge.columnSpan.coerceAtLeast(1)
                }
                else -> column += 1
            }
        }
    }
    return placements
}
