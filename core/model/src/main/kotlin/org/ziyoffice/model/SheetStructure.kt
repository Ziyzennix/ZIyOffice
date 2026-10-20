package org.ziyoffice.model

/**
 * Whole-row and whole-column edits.
 *
 * These are structural operations, not selections: every cell below or to the
 * right moves, merged ranges grow or shrink, sizing follows its axis, and
 * formulas are rewritten so they keep pointing at the same data. Deleting a
 * referenced row or column produces `#REF!`, matching spreadsheet convention.
 * Cached formula results are cleared because the sheet behind them changed.
 */

fun Sheet.insertRows(at: Int, count: Int): Sheet {
    if (count <= 0 || rowCount >= MAX_SHEET_ROWS) return this
    val rows = count.coerceAtMost(MAX_SHEET_ROWS - rowCount)
    if (rows <= 0) return this
    val start = at.coerceIn(0, rowCount)

    val moved = cells.map { cell ->
        val movedCell = if (cell.row >= start) cell.copy(row = cell.row + rows) else cell
        movedCell.rewriteFormulaForStructure { rewriteRowInsertion(it, start, rows) }
    }.sortedWith(compareBy({ it.row }, { it.column }))

    val merges = mergedRanges.mapNotNull { merge ->
        when {
            merge.top >= start -> merge.copy(top = merge.top + rows, bottom = merge.bottom + rows)
            merge.bottom >= start -> merge.copy(bottom = merge.bottom + rows)
            else -> merge
        }
    }
    val heights = rowHeights.entries.associate { (row, height) ->
        (if (row >= start) row + rows else row) to height
    }

    return copy(
        cells = moved,
        rowCount = rowCount + rows,
        frozenRows = if (frozenRows > start) frozenRows + rows else frozenRows,
        mergedRanges = merges.normaliseMerges(),
        rowHeights = heights,
    )
}

fun Sheet.deleteRows(at: Int, count: Int): Sheet {
    if (count <= 0 || rowCount <= 1 || at !in 0 until rowCount) return this
    val rows = count.coerceAtMost(rowCount - 1)
    val end = at + rows - 1

    val cells = cells.mapNotNull { cell ->
        if (cell.row in at..end) return@mapNotNull null
        val moved = if (cell.row > end) cell.copy(row = cell.row - rows) else cell
        moved.rewriteFormulaForStructure { rewriteRowDeletion(it, at, rows) }
    }.sortedWith(compareBy({ it.row }, { it.column }))

    val merges = mergedRanges.mapNotNull { merge ->
        if (merge.top in at..end) return@mapNotNull null
        val top = if (merge.top > end) merge.top - rows else merge.top
        val bottom = when {
            merge.bottom < at -> merge.bottom
            merge.bottom in at..end -> at - 1
            else -> merge.bottom - rows
        }
        merge.copy(top = top, bottom = bottom)
    }
    val heights = buildMap {
        rowHeights.forEach { (row, height) ->
            if (row !in at..end) put(if (row > end) row - rows else row, height)
        }
    }
    val frozen = when {
        frozenRows <= at -> frozenRows
        frozenRows > end -> frozenRows - rows
        else -> at
    }

    return copy(
        cells = cells,
        rowCount = (rowCount - rows).coerceAtLeast(1),
        frozenRows = frozen,
        mergedRanges = merges.normaliseMerges(),
        rowHeights = heights,
    )
}

fun Sheet.insertColumns(at: Int, count: Int): Sheet {
    if (count <= 0 || columnCount >= MAX_SHEET_COLUMNS) return this
    val columns = count.coerceAtMost(MAX_SHEET_COLUMNS - columnCount)
    if (columns <= 0) return this
    val start = at.coerceIn(0, columnCount)

    val moved = cells.map { cell ->
        val movedCell = if (cell.column >= start) cell.copy(column = cell.column + columns) else cell
        movedCell.rewriteFormulaForStructure { rewriteColumnInsertion(it, start, columns) }
    }.sortedWith(compareBy({ it.row }, { it.column }))

    val merges = mergedRanges.mapNotNull { merge ->
        when {
            merge.left >= start -> merge.copy(left = merge.left + columns, right = merge.right + columns)
            merge.right >= start -> merge.copy(right = merge.right + columns)
            else -> merge
        }
    }
    val inherited = if (start > 0) columnWidth(start - 1) else columnWidth(start)
    val widths = columnWidths.insertColumns(start, columns, inherited)

    return copy(
        cells = moved,
        columnCount = columnCount + columns,
        mergedRanges = merges.normaliseMerges(),
        columnWidths = widths,
    )
}

fun Sheet.deleteColumns(at: Int, count: Int): Sheet {
    if (count <= 0 || columnCount <= 1 || at !in 0 until columnCount) return this
    val columns = count.coerceAtMost(columnCount - 1)
    val end = at + columns - 1

    val cells = cells.mapNotNull { cell ->
        if (cell.column in at..end) return@mapNotNull null
        val moved = if (cell.column > end) cell.copy(column = cell.column - columns) else cell
        moved.rewriteFormulaForStructure { rewriteColumnDeletion(it, at, columns) }
    }.sortedWith(compareBy({ it.row }, { it.column }))

    val merges = mergedRanges.mapNotNull { merge ->
        if (merge.left in at..end) return@mapNotNull null
        val left = if (merge.left > end) merge.left - columns else merge.left
        val right = when {
            merge.right < at -> merge.right
            merge.right in at..end -> at - 1
            else -> merge.right - columns
        }
        merge.copy(left = left, right = right)
    }

    return copy(
        cells = cells,
        columnCount = (columnCount - columns).coerceAtLeast(1),
        mergedRanges = merges.normaliseMerges(),
        columnWidths = columnWidths.deleteColumns(at, end, columns),
    )
}

/* ------------------------- formula rewriting ------------------------- */

private fun Cell.rewriteFormulaForStructure(transform: (String) -> String): Cell =
    if (value.startsWith("=")) copy(value = transform(value), cached = null) else this

private fun rewriteRowInsertion(formula: String, at: Int, count: Int): String =
    rewriteFormulaReferences(formula) { start, end ->
        val newStart = if (start.row >= at) start.copy(row = start.row + count) else start
        val newEnd = if (end.row >= at) end.copy(row = end.row + count) else end
        if (newStart == start && newEnd == end) {
            null
        } else {
            renderFormulaReference(newStart, newEnd, start != end)
        }
    }

private fun rewriteColumnInsertion(formula: String, at: Int, count: Int): String =
    rewriteFormulaReferences(formula) { start, end ->
        val newStart = if (start.column >= at) start.copy(column = start.column + count) else start
        val newEnd = if (end.column >= at) end.copy(column = end.column + count) else end
        if (newStart == start && newEnd == end) {
            null
        } else {
            renderFormulaReference(newStart, newEnd, start != end)
        }
    }

private fun rewriteRowDeletion(formula: String, at: Int, count: Int): String =
    rewriteFormulaReferences(formula) { start, end ->
        val isRange = start != end
        val top = if (start.row <= end.row) start else end
        val bottom = if (start.row <= end.row) end else start
        val span = survivingSpan(top.row, bottom.row, at, count)
            ?: return@rewriteFormulaReferences "#REF!"
        val newTop = top.copy(row = span.first)
        val newBottom = bottom.copy(row = span.last)
        if (newTop == top && newBottom == bottom) {
            null
        } else {
            renderFormulaReference(newTop, newBottom, isRange)
        }
    }

private fun rewriteColumnDeletion(formula: String, at: Int, count: Int): String =
    rewriteFormulaReferences(formula) { start, end ->
        val isRange = start != end
        val left = if (start.column <= end.column) start else end
        val right = if (start.column <= end.column) end else start
        val span = survivingSpan(left.column, right.column, at, count)
            ?: return@rewriteFormulaReferences "#REF!"
        val newLeft = left.copy(column = span.first)
        val newRight = right.copy(column = span.last)
        if (newLeft == left && newRight == right) {
            null
        } else {
            renderFormulaReference(newLeft, newRight, isRange)
        }
    }

/**
 * The 0-based indices that survive when [at]..[at]+[count]-1 is deleted from
 * [first]..[last], or null when the whole span disappears.
 */
private fun survivingSpan(first: Int, last: Int, at: Int, count: Int): IntRange? {
    val end = at + count - 1
    if (last < at) return first..last
    if (first > end) return (first - count)..(last - count)
    val top = if (first < at) first else at
    val bottom = if (last > end) last - count else at - 1
    return if (bottom < top) null else top..bottom
}

private fun renderFormulaReference(
    start: FormulaReference,
    end: FormulaReference,
    isRange: Boolean,
): String = if (isRange) "${start.render()}:${end.render()}" else start.render()

/* ------------------------------ sizing ------------------------------ */

private fun List<ColumnWidth>.insertColumns(
    at: Int,
    count: Int,
    inheritedWidth: Float,
): List<ColumnWidth> {
    val result = mutableListOf<ColumnWidth>()
    forEach { width ->
        when {
            width.lastColumn < at -> result += width
            width.firstColumn >= at -> result += width.copy(
                firstColumn = width.firstColumn + count,
                lastColumn = width.lastColumn + count,
            )
            else -> {
                result += width.copy(lastColumn = at - 1)
                result += ColumnWidth(at, at + count - 1, width.width)
                result += width.copy(
                    firstColumn = at + count,
                    lastColumn = width.lastColumn + count,
                )
            }
        }
    }
    if (
        inheritedWidth != DEFAULT_COLUMN_WIDTH_CHARS &&
        result.none { it.covers(at) }
    ) {
        result += ColumnWidth(at, at + count - 1, inheritedWidth)
    }
    return result.normaliseColumnWidths()
}

private fun List<ColumnWidth>.deleteColumns(
    at: Int,
    end: Int,
    count: Int,
): List<ColumnWidth> {
    val result = mutableListOf<ColumnWidth>()
    forEach { width ->
        when {
            width.lastColumn < at || width.firstColumn > end ->
                result += if (width.firstColumn > end) {
                    width.copy(
                        firstColumn = width.firstColumn - count,
                        lastColumn = width.lastColumn - count,
                    )
                } else {
                    width
                }
            else -> {
                if (width.firstColumn < at) {
                    result += width.copy(lastColumn = at - 1)
                }
                if (width.lastColumn > end) {
                    result += width.copy(
                        firstColumn = at,
                        lastColumn = width.lastColumn - count,
                    )
                }
            }
        }
    }
    return result.normaliseColumnWidths()
}
