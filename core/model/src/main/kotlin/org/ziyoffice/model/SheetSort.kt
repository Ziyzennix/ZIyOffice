package org.ziyoffice.model

/**
 * Sorting a rectangular range of a sheet.
 *
 * The rules are what a spreadsheet user expects from "sort by column": blanks
 * always sort last, numbers sort numerically, text sorts case-insensitively,
 * and formulas move with their row because whole rows are reordered.
 *
 * Formula references are deliberately not rewritten. Rewriting =B1+C1 into
 * =B7+C7 while the target rows move is the classic way spreadsheets corrupt
 * data; moving whole rows keeps every relative reference pointing at the same
 * neighbour it pointed at before.
 */
object SheetSort {
    /** Column to order by, and which way. */
    data class Key(val column: Int, val ascending: Boolean = true)

    /**
     * Returns [sheet] with rows `topRow..bottomRow` reordered by [key].
     *
     * Columns outside `leftColumn..rightColumn` are moved together with their row,
     * so a sort never strands data that sits outside the sorted range. Cells that
     * are not in the range and not in the key column keep their row position only
     * when they are outside the row range; inside it they travel with their row.
     */
    fun sort(sheet: Sheet, topRow: Int, bottomRow: Int, key: Key): Sheet {
        if (bottomRow <= topRow) return sheet
        val byPosition = sheet.cells.associateBy { it.row to it.column }

        // Capture each row's cells, then order the row indices by the key column.
        val rows = (topRow..bottomRow).toList()
        val sortValue: (Int) -> CellValue? = { row ->
            byPosition[row to key.column]?.let { cell -> valueOf(cell, sheet) }
        }

        val ordered = rows.sortedWith { a, b ->
            val left = sortValue(a)
            val right = sortValue(b)
            // Blankness outranks direction: negating the whole comparison would
            // float empty rows to the top of a descending sort.
            when {
                isBlank(left) && isBlank(right) -> 0
                isBlank(left) -> 1
                isBlank(right) -> -1
                else -> {
                    val comparison = compare(left, right)
                    if (key.ascending) comparison else -comparison
                }
            }
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
        )
    }

    /**
     * Rows whose key cell is blank sort after every populated row, in both
     * directions — that is the behaviour users rely on when sorting a partially
     * filled column.
     */
    /** Orders two non-blank values; callers handle blanks before reaching here. */
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
