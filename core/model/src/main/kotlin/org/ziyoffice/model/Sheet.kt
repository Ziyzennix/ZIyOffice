package org.ziyoffice.model

/**
 * One cell in a sheet. Cells hold either raw text/numbers or a formula starting
 * with '='. Formulas are evaluated lazily against the owning [Sheet].
 */
data class Cell(
    val row: Int,
    val column: Int,
    /** Raw value entered by the user: text, number, or "=..." formula text. */
    val value: String = "",
    /** Display formatting applied to this cell. */
    val format: CellFormat = CellFormat(),
    /** Cached last-computed evaluation; null until evaluated. */
    val cached: CellValue? = null,
)

data class CellFormat(
    /** Bold text. */
    val bold: Boolean = false,
    /** Italic text. */
    val italic: Boolean = false,
    /** Underline text. */
    val underline: Boolean = false,
    /** Strikethrough text. */
    val strike: Boolean = false,
    /** Font size in points; null = automatic. */
    val sizePt: Float? = null,
    /** Text colour as 0xRRGGBB; null = automatic. */
    val colorRgb: Int? = null,
    /** Background fill colour as 0xRRGGBB; null = none. */
    val fillRgb: Int? = null,
    /** Horizontal text alignment. */
    val alignment: CellAlignment = CellAlignment.GENERAL,
    /** Number format pattern, e.g. "0.00", "0.0%", "$#,##0.00". null = general. */
    val numberFormat: String? = null,
)

enum class CellAlignment { GENERAL, LEFT, CENTER, RIGHT }

/** Computed value of a cell — text, number, boolean, or error. */
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
    /** Number of rows; defaults to max row index + 1. */
    val rowCount: Int = 100,
    /** Number of columns; defaults to max column index + 1. */
    val columnCount: Int = 26,
    /** Rows above this index stay put while the rest scrolls; 0 = no frozen rows. */
    val frozenRows: Int = 0,
    /** Blocks of cells drawn as one cell; the top-left cell holds the value. */
    val mergedRanges: List<CellRange> = emptyList(),
    /** Explicit column widths; uncovered columns use [DEFAULT_COLUMN_WIDTH_CHARS]. */
    val columnWidths: List<ColumnWidth> = emptyList(),
    /** Explicit row heights in points; missing rows use [DEFAULT_ROW_HEIGHT_POINTS]. */
    val rowHeights: Map<Int, Float> = emptyMap(),
)

data class Workbook(
    val sheets: List<Sheet>,
    /** Currently active sheet index. */
    val activeSheet: Int = 0,
)
