package org.ziyoffice.model

/**
 * The line around a cell, stored per cell and per edge the way a worksheet keeps it.
 * "All borders" puts all four edges on every cell, a box only the outer ones.
 */
enum class BorderStyle {
    NONE,
    THIN,
    MEDIUM,
    THICK,
    DASHED,
    DOTTED,
}

data class CellBorders(
    val top: BorderStyle = BorderStyle.NONE,
    val bottom: BorderStyle = BorderStyle.NONE,
    val left: BorderStyle = BorderStyle.NONE,
    val right: BorderStyle = BorderStyle.NONE,
) {
    val isEmpty: Boolean
        get() = top == BorderStyle.NONE &&
            bottom == BorderStyle.NONE &&
            left == BorderStyle.NONE &&
            right == BorderStyle.NONE
}

enum class BorderKind { NONE, TOP, BOTTOM, LEFT, RIGHT, ALL, BOX }

/**
 * Draws borders on every cell of [range]. "All" draws the four edges of every cell,
 * "box" draws the outline of the range so the edges on the inside stay free.
 */
fun Sheet.withBorders(range: CellRange, kind: BorderKind): Sheet {
    val positions = buildSet {
        for (row in range.top..range.bottom) {
            for (column in range.left..range.right) add(row to column)
        }
    }
    val byPosition = cells.associateBy { it.row to it.column }
    val outside = cells.filterNot { (it.row to it.column) in positions }
    val bordered = positions.mapNotNull { (row, column) ->
        val existing = byPosition[row to column]
        val format = (existing?.format ?: CellFormat()).copy(
            borders = bordersFor(kind, row, column, range),
        )
        val value = existing?.value.orEmpty()
        if (value.isNotEmpty() || format != CellFormat()) {
            Cell(row, column, value, format, existing?.cached)
        } else {
            null
        }
    }
    return copy(cells = (outside + bordered).sortedWith(compareBy({ it.row }, { it.column })))
}

/** The edges one cell of the selection carries for a border action. */
private fun bordersFor(
    kind: BorderKind,
    row: Int,
    column: Int,
    range: CellRange,
): CellBorders = when (kind) {
    BorderKind.NONE -> CellBorders()
    BorderKind.TOP -> CellBorders(top = BorderStyle.THIN)
    BorderKind.BOTTOM -> CellBorders(bottom = BorderStyle.THIN)
    BorderKind.LEFT -> CellBorders(left = BorderStyle.THIN)
    BorderKind.RIGHT -> CellBorders(right = BorderStyle.THIN)
    BorderKind.ALL -> CellBorders(
        top = BorderStyle.THIN,
        bottom = BorderStyle.THIN,
        left = BorderStyle.THIN,
        right = BorderStyle.THIN,
    )
    BorderKind.BOX -> CellBorders(
        top = if (row == range.top) BorderStyle.THIN else BorderStyle.NONE,
        bottom = if (row == range.bottom) BorderStyle.THIN else BorderStyle.NONE,
        left = if (column == range.left) BorderStyle.THIN else BorderStyle.NONE,
        right = if (column == range.right) BorderStyle.THIN else BorderStyle.NONE,
    )
}
