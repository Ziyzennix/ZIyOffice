package org.ziyoffice.model

/**
 * One cell in a sheet. Cells hold either raw text/numbers or a formula starting with
 * '='. Formulas are evaluated lazily against the owning [Sheet].
 */
data class Cell(
    val row: Int,
    val column: Int,
    /** Raw value entered by the user: text, number, or "=..." formula text. */
    val value: String = "",
    val format: CellFormat = CellFormat(),
    /** Cached last-computed evaluation; null until evaluated. */
    val cached: CellValue? = null,
)

data class CellFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sizePt: Float? = null,
    val colorRgb: Int? = null,
    val fillRgb: Int? = null,
    val alignment: CellAlignment = CellAlignment.GENERAL,
    /** Number format pattern, e.g. "0.00", "0.0%", "$#,##0.00". null = general. */
    val numberFormat: String? = null,
    val borders: CellBorders = CellBorders(),
)

enum class CellAlignment { GENERAL, LEFT, CENTER, RIGHT }

/** Computed value of a cell: text, number, boolean, or error. */
sealed interface CellValue {
    data class Text(val text: String) : CellValue
    data class Number(val number: Double) : CellValue
    data class Bool(val value: Boolean) : CellValue
    data class Error(val message: String) : CellValue
}

data class Sheet(
    val name: String,
    /** Cells in arbitrary order; out of range cells are simply absent. */
    val cells: List<Cell>,
    val rowCount: Int = 100,
    val columnCount: Int = 26,
    /** Rows above this index stay put while the rest scrolls; 0 = no frozen rows. */
    val frozenRows: Int = 0,
    /** Blocks of cells drawn as one cell; the top-left cell holds the value. */
    val mergedRanges: List<CellRange> = emptyList(),
    /** Explicit column widths; uncovered columns use [DEFAULT_COLUMN_WIDTH_CHARS]. */
    val columnWidths: List<ColumnWidth> = emptyList(),
    /** Explicit row heights in points; missing rows use [DEFAULT_ROW_HEIGHT_POINTS]. */
    val rowHeights: Map<Int, Float> = emptyMap(),
    val validations: List<DataValidation> = emptyList(),
)

data class Workbook(
    val sheets: List<Sheet>,
    val activeSheet: Int = 0,
)
