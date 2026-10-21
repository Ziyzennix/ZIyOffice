package org.ziyoffice.model

/**
 * Find and replace inside a sheet. A formula is searched through the text it quotes,
 * never through its references or function names: replacing a label inside ="Paper" is
 * safe, replacing SUM in =SUM(A1:A2) would not be.
 */

data class SheetMatch(val row: Int, val column: Int)

/** What a replace run changed. */
data class SheetReplaceResult(
    val sheet: Sheet,
    val occurrences: Int,
    val cells: Int,
)

/** Every cell whose text holds the term, in reading order. */
fun Sheet.findMatches(
    query: String,
    ignoreCase: Boolean = true,
    wholeCell: Boolean = false,
): List<SheetMatch> {
    if (query.isEmpty()) return emptyList()
    return cells
        .sortedWith(compareBy({ it.row }, { it.column }))
        .filter { cell ->
            val text = searchableText(cell)
            when {
                wholeCell -> text.equals(query, ignoreCase = ignoreCase)
                else -> text.contains(query, ignoreCase = ignoreCase)
            }
        }
        .map { SheetMatch(it.row, it.column) }
}

/**
 * Replaces every occurrence of [query] and returns the changed sheet. [wholeCell] only
 * applies to plain cells: a formula is searched inside the text it quotes, so a
 * whole-cell rule there has no clear meaning.
 */
fun Sheet.replaceText(
    query: String,
    replacement: String,
    ignoreCase: Boolean = true,
    wholeCell: Boolean = false,
): SheetReplaceResult {
    if (query.isEmpty()) return SheetReplaceResult(this, 0, 0)
    var occurrences = 0
    var changedCells = 0
    val updated = cells.map { cell ->
        val (value, count) = replaceWithinCell(cell, query, replacement, ignoreCase, wholeCell)
        if (count == 0) {
            cell
        } else {
            occurrences += count
            changedCells += 1
            cell.copy(value = value, cached = null)
        }
    }
    return SheetReplaceResult(
        sheet = copy(cells = updated),
        occurrences = occurrences,
        cells = changedCells,
    )
}

/** Replaces the term in one cell, returns the sheet and how many times it hit. */
fun Sheet.replaceInCell(
    row: Int,
    column: Int,
    query: String,
    replacement: String,
    ignoreCase: Boolean = true,
): Pair<Sheet, Int> {
    if (query.isEmpty()) return this to 0
    val target = cells.firstOrNull { it.row == row && it.column == column } ?: return this to 0
    val (value, count) = replaceWithinCell(target, query, replacement, ignoreCase, wholeCell = false)
    if (count == 0) return this to 0
    val updated = cells.map { if (it === target) it.copy(value = value, cached = null) else it }
    return copy(cells = updated) to count
}

private fun replaceWithinCell(
    cell: Cell,
    query: String,
    replacement: String,
    ignoreCase: Boolean,
    wholeCell: Boolean,
): Pair<String, Int> {
    if (!cell.value.startsWith("=")) {
        if (wholeCell) {
            if (!cell.value.equals(query, ignoreCase = ignoreCase)) return cell.value to 0
            return replacement to 1
        }
        if (!cell.value.contains(query, ignoreCase = ignoreCase)) return cell.value to 0
        return replaceAllOccurrences(cell.value, query, replacement, ignoreCase)
    }

    // A whole-cell rule is about a cell's own text, and a formula is not one,
    // so it stays untouched.
    if (wholeCell) return cell.value to 0

    val spans = stringLiteralSpans(cell.value)
    if (spans.isEmpty()) return cell.value to 0
    var count = 0
    val builder = StringBuilder(cell.value.length)
    var copied = 0
    for (span in spans) {
        val literal = cell.value.substring(span.first, span.last + 1)
        if (!literal.contains(query, ignoreCase = ignoreCase)) continue
        val (replaced, hits) = replaceAllOccurrences(literal, query, replacement, ignoreCase)
        builder.append(cell.value, copied, span.first)
        builder.append(replaced)
        count += hits
        copied = span.last + 1
    }
    if (count == 0) return cell.value to 0
    builder.append(cell.value, copied, cell.value.length)
    return builder.toString() to count
}

private fun replaceAllOccurrences(
    text: String,
    query: String,
    replacement: String,
    ignoreCase: Boolean,
): Pair<String, Int> {
    if (query.isEmpty()) return text to 0
    val result = StringBuilder(text.length)
    var count = 0
    var index = 0
    while (index < text.length) {
        val found = text.indexOf(query, index, ignoreCase = ignoreCase)
        if (found < 0) break
        result.append(text, index, found).append(replacement)
        index = found + query.length
        count += 1
    }
    result.append(text, index, text.length)
    return result.toString() to count
}

/** The text a search sees: a plain cell shows itself, a formula shows its quoted text. */
private fun searchableText(cell: Cell): String {
    if (!cell.value.startsWith("=")) return cell.value
    val spans = stringLiteralSpans(cell.value)
    if (spans.isEmpty()) return ""
    return spans.joinToString(" ") { cell.value.substring(it.first, it.last + 1) }
}

/**
 * The character ranges a formula keeps inside quotes. A doubled quote is a literal
 * quote, so it does not end the text.
 */
private fun stringLiteralSpans(formula: String): List<IntRange> {
    val spans = mutableListOf<IntRange>()
    var index = 0
    while (index < formula.length) {
        if (formula[index] != '"') {
            index += 1
            continue
        }
        val start = index + 1
        var cursor = start
        while (cursor < formula.length) {
            if (formula[cursor] == '"') {
                if (cursor + 1 < formula.length && formula[cursor + 1] == '"') {
                    cursor += 2
                    continue
                }
                break
            }
            cursor += 1
        }
        val end = minOf(cursor, formula.length)
        if (end > start) spans += start..(end - 1)
        index = cursor + 1
    }
    return spans
}
