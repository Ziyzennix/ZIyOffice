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
    /** The names a formula can use instead of a cell reference. */
    val namedRanges: List<NamedRange> = emptyList(),
)

/**
 * A name a formula can use instead of a cell reference.
 *
 * A name stands for a range of cells. "Sales" is easier to read in a
 * formula than "B2:B40", and it survives rows being inserted.
 */
data class NamedRange(
    /** The name as the user typed it. */
    val name: String,
    /** The range the name stands for. */
    val range: CellRange,
    /**
     * The sheet the range is on, or -1 when the name is used on every sheet.
     *
     * A name that belongs to one sheet is only understood in that sheet's
     * formulas, which is what a spreadsheet does.
     */
    val sheetIndex: Int = -1,
) {
    /** True when the name can be used in a formula on the sheet at [index]. */
    fun appliesTo(index: Int): Boolean = sheetIndex < 0 || sheetIndex == index
}
