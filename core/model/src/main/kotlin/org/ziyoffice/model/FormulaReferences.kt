package org.ziyoffice.model

/**
 * One A1 reference found inside a formula. Kept separate from the formula
 * parser because structural edits only need to move coordinates; they never
 * need to evaluate an expression.
 */
internal data class FormulaReference(
    val row: Int,
    val column: Int,
    val rowAbsolute: Boolean = false,
    val columnAbsolute: Boolean = false,
) {
    fun render(): String = buildString {
        if (columnAbsolute) append('$')
        append(columnName(column))
        if (rowAbsolute) append('$')
        append(row + 1)
    }
}

/**
 * Rewrites every cell reference in [formula], leaving function names, numbers,
 * operators and string literals untouched.
 *
 * [rewrite] receives both corners of a range. Returning null keeps the original
 * spelling, including `$` anchors; returning a string replaces the reference or
 * the whole `A1:B2` pair. A range is handled as one unit so a deletion can
 * shrink it instead of invalidating each endpoint separately.
 */
internal fun rewriteFormulaReferences(
    formula: String,
    rewrite: (start: FormulaReference, end: FormulaReference) -> String?,
): String {
    if (!formula.startsWith("=")) return formula

    val out = StringBuilder(formula.length)
    var copied = 0
    var i = 1
    while (i < formula.length) {
        if (formula[i] == '"') {
            // A quoted string may contain something that looks like a reference.
            i = stringEnd(formula, i)
            continue
        }

        val parsed = parseReference(formula, i)
        if (parsed == null) {
            i += 1
            continue
        }
        val start = parsed.reference
        val afterStart = parsed.end
        if (!isReferenceBoundaryBefore(formula, i) || !isReferenceBoundaryAfter(formula, afterStart)) {
            i = afterStart
            continue
        }

        // A name followed by "(" is a function call, even if it looks like a
        // reference: LOG10(A1) has a name that the scanner must leave alone.
        val afterName = skipSpaces(formula, afterStart)
        if (afterName < formula.length && formula[afterName] == '(') {
            i = afterStart
            continue
        }

        val colon = skipSpaces(formula, afterStart)
        if (colon < formula.length && formula[colon] == ':') {
            val endStart = skipSpaces(formula, colon + 1)
            val endParsed = parseReference(formula, endStart)
            if (endParsed != null && isReferenceBoundaryAfter(formula, endParsed.end)) {
                val replacement = rewrite(start, endParsed.reference)
                if (replacement != null) {
                    out.append(formula, copied, i)
                    out.append(replacement)
                    copied = endParsed.end
                }
                i = endParsed.end
                continue
            }
        }

        val replacement = rewrite(start, start)
        if (replacement != null) {
            out.append(formula, copied, i)
            out.append(replacement)
            copied = afterStart
        }
        i = afterStart
    }

    if (copied == 0) return formula
    out.append(formula, copied, formula.length)
    return out.toString()
}

private data class ParsedReference(val reference: FormulaReference, val end: Int)

/** Parses `A1`, `$A$1`, `AB12` at [start]; null when no valid reference starts there. */
private fun parseReference(text: String, start: Int): ParsedReference? {
    var i = start
    var columnAbsolute = false
    if (i < text.length && text[i] == '$') {
        columnAbsolute = true
        i += 1
    }
    val lettersStart = i
    while (i < text.length && text[i].isLetter()) i += 1
    val letters = text.substring(lettersStart, i)
    if (letters.length !in 1..3) return null

    var rowAbsolute = false
    if (i < text.length && text[i] == '$') {
        rowAbsolute = true
        i += 1
    }
    val digitsStart = i
    while (i < text.length && text[i].isDigit()) i += 1
    if (i == digitsStart) return null

    val row = text.substring(digitsStart, i).toIntOrNull() ?: return null
    if (row !in 1..MAX_SHEET_ROWS) return null
    val column = columnIndex(letters)
    if (column !in 0 until MAX_SHEET_COLUMNS) return null

    return ParsedReference(
        reference = FormulaReference(
            row = row - 1,
            column = column,
            rowAbsolute = rowAbsolute,
            columnAbsolute = columnAbsolute,
        ),
        end = i,
    )
}

/**
 * A reference may follow an operator, bracket, comma or another reference's
 * colon, but must not be the tail of a longer identifier. A leading `!` means a
 * sheet-qualified name, which this app does not model and must not partially
 * rewrite.
 */
private fun isReferenceBoundaryBefore(text: String, start: Int): Boolean {
    if (start <= 1) return true
    val previous = text[start - 1]
    return !(previous.isLetterOrDigit() || previous == '_' || previous == '$' || previous == '!' || previous == ':')
}

private fun isReferenceBoundaryAfter(text: String, end: Int): Boolean {
    if (end >= text.length) return true
    val next = text[end]
    return !(next.isLetterOrDigit() || next == '_' || next == '$')
}

/** Index just past the quoted string that starts at [start]. */
private fun stringEnd(text: String, start: Int): Int {
    var i = start + 1
    while (i < text.length) {
        if (text[i] == '"') {
            if (i + 1 < text.length && text[i + 1] == '"') {
                i += 2
            } else {
                return i + 1
            }
        } else {
            i += 1
        }
    }
    return text.length
}

private fun skipSpaces(text: String, start: Int): Int {
    var i = start
    while (i < text.length && text[i].isWhitespace()) i += 1
    return i
}
