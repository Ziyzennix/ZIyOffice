package org.ziyoffice.ui

import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind

/**
 * Resolves a list item's display marker ("•", "1.", "a.", "iv.") and advances
 * the counter for that (numId, level). Shared by the editor and the PDF
 * exporter so both render the same numbers.
 */
internal fun listMarker(list: ListInfo, counters: MutableMap<Pair<Int, Int>, Int>): String {
    if (list.kind == ListKind.BULLET) return "\u2022"
    val key = list.numId to list.level
    val value = (counters[key] ?: (list.start - 1)) + 1
    counters[key] = value
    return when (list.kind) {
        ListKind.NUMBER, ListKind.OTHER -> "$value."
        ListKind.LETTER_LOWER -> "${'a' + (value - 1) % 26}."
        ListKind.LETTER_UPPER -> "${'A' + (value - 1) % 26}."
        ListKind.ROMAN_LOWER -> "${toRoman(value).lowercase()}."
        ListKind.ROMAN_UPPER -> "${toRoman(value)}."
        ListKind.BULLET -> "\u2022"
    }
}

internal fun toRoman(value: Int): String {
    if (value <= 0) return value.toString()
    val symbols = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
    val result = StringBuilder()
    var remaining = value
    for ((amount, symbol) in symbols) {
        while (remaining >= amount) {
            result.append(symbol)
            remaining -= amount
        }
    }
    return result.toString()
}
