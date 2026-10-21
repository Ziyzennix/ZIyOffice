package org.ziyoffice.model

/**
 * CSV import/export for Sheets. RFC 4180: quoted fields may hold commas, quotes
 * (doubled) and newlines; records end with CRLF, LF or a lone CR.
 */

/** Parses [text] into a [Sheet] named [name]; empty fields become absent cells. */
fun parseCsv(text: String, name: String): Sheet {
    val cells = mutableListOf<Cell>()
    val field = StringBuilder()
    var row = 0
    var column = 0
    var quoted = false
    var pendingRow = false

    fun endField() {
        if (field.isNotEmpty()) cells += Cell(row = row, column = column, value = field.toString())
        field.setLength(0)
        column++
        pendingRow = true
    }

    fun endRow() {
        endField()
        row++
        column = 0
        pendingRow = false
    }

    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (quoted) {
            when {
                ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                ch == '"' -> quoted = false
                else -> field.append(ch)
            }
        } else {
            when (ch) {
                '"' -> quoted = true
                ',' -> endField()
                '\r' -> {
                    // CRLF is one record break; a lone CR is one too.
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                '\n' -> endRow()
                else -> field.append(ch)
            }
        }
        i++
    }
    if (pendingRow || field.isNotEmpty()) endRow()

    return Sheet(
        name = name,
        cells = cells,
        rowCount = maxOf(MIN_ROWS, row + 1),
        columnCount = maxOf(MIN_COLUMNS, column + 1),
    )
}

/**
 * Renders [sheet] as CSV. Formula cells are written as their computed value: CSV has
 * no formula syntax, so writing `=SUM(A1:A2)` would be a lie.
 */
fun writeCsv(sheet: Sheet): String {
    val maxRow = sheet.cells.maxOfOrNull { it.row } ?: return ""
    val byPosition = sheet.cells.associateBy { it.row to it.column }

    return buildString {
        for (row in 0..maxRow) {
            if (row > 0) append("\r\n")
            // Trailing empty fields carry no data; emit only up to the last one
            // that does, so a short row does not gain phantom commas.
            val maxColumn = sheet.cells.filter { it.row == row }.maxOfOrNull { it.column } ?: -1
            for (column in 0..maxColumn) {
                if (column > 0) append(',')
                val cell = byPosition[row to column]
                val raw = cell?.value.orEmpty()
                val shown = if (raw.startsWith("=")) {
                    computedText(sheet, raw)
                } else {
                    // A formatted cell exports the number its format renders, so a
                    // "0.00" cell does not silently become the raw float in CSV.
                    val number = raw.toDoubleOrNull()
                    if (number != null) formatNumber(number, cell?.format?.numberFormat) else raw
                }
                append(quoteField(shown))
            }
        }
    }
}

private fun computedText(sheet: Sheet, formula: String): String =
    when (val value = evaluateFormula(sheet, formula).value) {
        is CellValue.Number -> formatNumber(value.number)
        is CellValue.Text -> value.text
        is CellValue.Bool -> if (value.value) "TRUE" else "FALSE"
        is CellValue.Error -> value.message
    }

private fun quoteField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

private const val MIN_ROWS = 100
private const val MIN_COLUMNS = 26
