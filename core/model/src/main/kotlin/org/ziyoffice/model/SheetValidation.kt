package org.ziyoffice.model

/**
 * A list of allowed values for a range of cells.
 *
 * A validation is the dropdown a table cell offers: the sheet keeps the
 * choices, and the editor shows them when the cell is edited. Only a plain
 * list of values is modelled — a rule that points at another range is read
 * but not kept.
 */
data class DataValidation(
    val range: CellRange,
    val values: List<String>,
)

/** The validation that covers this cell, if any. */
fun Sheet.validationAt(row: Int, column: Int): DataValidation? =
    validations.firstOrNull { it.range.contains(row, column) }

/**
 * Gives [range] a dropdown with [values]. A validation that already covered
 * part of the range is replaced, so a cell never ends up with two rules.
 */
fun Sheet.withValidation(range: CellRange, values: List<String>): Sheet {
    if (values.isEmpty()) return withValidationCleared(range)
    val clamped = range.copy(
        top = range.top.coerceAtLeast(0),
        left = range.left.coerceAtLeast(0),
        bottom = range.bottom.coerceAtLeast(0),
        right = range.right.coerceAtLeast(0),
    )
    val kept = validations.filterNot { it.range.intersects(clamped) }
    return copy(validations = kept + DataValidation(clamped, values))
}

/** Removes every validation that touches [range]. */
fun Sheet.withValidationCleared(range: CellRange): Sheet =
    copy(validations = validations.filterNot { it.range.intersects(range) })
