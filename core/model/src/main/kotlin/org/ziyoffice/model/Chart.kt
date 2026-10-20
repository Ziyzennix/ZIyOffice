package org.ziyoffice.model

/**
 * One bar in a chart: its label and its numeric value.
 */
data class ChartPoint(val label: String, val value: Double)

/**
 * Reads the numbers in [column] into chart points, using the first text cell in
 * the row to its left as the label. A row whose own cell is not a number is
 * skipped rather than plotted as zero, so a header row does not flatten the scale.
 */
fun chartPointsFrom(sheet: Sheet, column: Int, maxPoints: Int = 12): List<ChartPoint> {
    val byPosition = sheet.cells.associateBy { it.row to it.column }
    return (0 until sheet.rowCount)
        .mapNotNull { row ->
            val cell = byPosition[row to column] ?: return@mapNotNull null
            val value = when {
                cell.value.startsWith("=") ->
                    (evaluateFormula(sheet, cell.value).value as? CellValue.Number)?.number
                else -> cell.value.toDoubleOrNull()
            } ?: return@mapNotNull null
            val label = byPosition[row to column - 1]?.value?.takeIf { it.isNotBlank() }
                ?: "Row ${row + 1}"
            ChartPoint(label = label, value = value)
        }
        .take(maxPoints)
}
