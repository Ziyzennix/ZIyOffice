package org.ziyoffice.model

/**
 * Sorting a rectangular range of a sheet.
 *
 * The rules are what a spreadsheet user expects from "sort by column": blanks always sort
 * last, numbers sort numerically, text sorts case-insensitively, and formulas move with
 * their row because whole rows are reordered.
 *
 * Formula references are deliberately not rewritten. Rewriting =B1+C1 into =B7+C7 while
 * the rows move is the classic way spreadsheets corrupt data.
 */
object SheetSort {
    data class Key(val column: Int, val ascending: Boolean = true)

    /**
     * Returns [sheet] with rows `topRow..bottomRow` reordered by [key]. Every column moves with its row,
     * so a sort never strands data outside the key column.
     */
    fun sort(sheet: Sheet, topRow: Int, bottomRow: Int, key: Key): Sheet =
        sort(sheet, topRow, bottomRow, listOf(key))

    /**
     * Returns [sheet] with rows `topRow..bottomRow` reordered by [keys].
     *
     * The first key decides; the next ones break the ties. That is the order
     * a spreadsheet sorts by when the user adds a second column, and the one
     * people expect when they say "sort by this, then by that".
     */
    fun sort(sheet: Sheet, topRow: Int, bottomRow: Int, keys: List<Key>): Sheet {
        if (bottomRow <= topRow) return sheet
        if (keys.isEmpty()) return sheet
        val byPosition = sheet.cells.associateBy { it.row to it.column }

        // Capture each row's cells, then order the row indices by the keys.
        val rows = (topRow..bottomRow).toList()
        val sortValue: (Int, Int) -> CellValue? = { row, column ->
            byPosition[row to column]?.let { cell -> valueOf(cell, sheet) }
        }

        val ordered = rows.sortedWith { a, b ->
            for (key in keys) {
                val comparison = compareRows(
                    sortValue(a, key.column),
                    sortValue(b, key.column),
                    key.ascending,
                )
                if (comparison != 0) return@sortedWith comparison
            }
            0
        }

        val moved = mutableListOf<Cell>()
        ordered.forEachIndexed { offset, sourceRow ->
            val destination = topRow + offset
            byPosition.forEach { (position, cell) ->
                if (position.first == sourceRow) {
                    moved += cell.copy(row = destination)
                }
            }
        }

        val untouched = sheet.cells.filterNot { it.row in topRow..bottomRow }
        return sheet.copy(
            cells = (untouched + moved).sortedWith(compareBy({ it.row }, { it.column })),
            // A row's height belongs to the row, so it travels with it.
            rowHeights = movedRowHeights(sheet.rowHeights, ordered, topRow),
        )
    }

    /** Moves the heights of the sorted rows to where those rows ended up. */
    private fun movedRowHeights(
        heights: Map<Int, Float>,
        ordered: List<Int>,
        topRow: Int,
    ): Map<Int, Float> {
        if (heights.isEmpty()) return heights
        val moved = mutableMapOf<Int, Int>()
        ordered.forEachIndexed { offset, sourceRow -> moved[sourceRow] = topRow + offset }
        return heights.entries.associate { (row, height) -> (moved[row] ?: row) to height }
    }

    /**
     * Orders two rows by one key.
     *
     * Blankness outranks direction: negating the whole comparison would
     * float empty rows to the top of a descending sort.
     */
    private fun compareRows(left: CellValue?, right: CellValue?, ascending: Boolean): Int = when {
        isBlank(left) && isBlank(right) -> 0
        isBlank(left) -> 1
        isBlank(right) -> -1
        else -> {
            val comparison = compare(left, right)
            if (ascending) comparison else -comparison
        }
    }

    /**
     * Orders two non-blank values; callers handle blanks before reaching here. Rows whose key cell is
     * blank sort after every populated row, in both directions.
     */
    private fun compare(left: CellValue?, right: CellValue?): Int {
        val leftNumber = asNumberOrNull(left)
        val rightNumber = asNumberOrNull(right)
        if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber)
        if (leftNumber != null) return -1
        if (rightNumber != null) return 1

        return textOf(left).compareTo(textOf(right), ignoreCase = true)
    }

    private fun isBlank(value: CellValue?): Boolean = when (value) {
        null -> true
        is CellValue.Text -> value.text.isBlank()
        else -> false
    }

    private fun asNumberOrNull(value: CellValue?): Double? = when (value) {
        is CellValue.Number -> value.number
        is CellValue.Bool -> if (value.value) 1.0 else 0.0
        is CellValue.Text -> value.text.trim().toDoubleOrNull()
        else -> null
    }

    private fun textOf(value: CellValue?): String = when (value) {
        is CellValue.Text -> value.text
        is CellValue.Number -> generalNumber(value.number)
        is CellValue.Bool -> if (value.value) "TRUE" else "FALSE"
        is CellValue.Error -> value.message
        null -> ""
    }

    /** The value a cell contributes to sorting: evaluated if it is a formula. */
    private fun valueOf(cell: Cell, sheet: Sheet): CellValue? {
        if (cell.value.isEmpty()) return null
        if (cell.value.startsWith("=")) return evaluateFormula(sheet, cell.value).value
        return cell.value.toDoubleOrNull()?.let { CellValue.Number(it) } ?: CellValue.Text(cell.value)
    }
}
