package org.ziyoffice.pdf

/**
 * Turns the text a user types into the page range to extract.
 *
 * Split needs the same range syntax people already know from print dialogs, and
 * it must be impossible to get a range that silently means something else — a
 * typo should be rejected, not clamped into "all pages". Kept free of Android
 * types so the parsing is unit-testable.
 */
internal object PageRange {

    /**
     * Parses [text] against a document of [pageCount] pages (1-based in the UI).
     *
     * Accepts `3`, `2-5`, `1,4,7`, and open ends `-4` / `5-`, separated by
     * commas or spaces. Returns the selected 1-based page numbers in the order
     * given, without duplicates, or null when the text is not a valid range —
     * so the caller can keep the dialog open with the text intact.
     */
    fun parse(text: String, pageCount: Int): List<Int>? {
        if (pageCount <= 0) return null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val selected = LinkedHashSet<Int>()
        val parts = trimmed.split(',', ';').flatMap { it.trim().split(Regex("\\s+")) }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (part in parts) {
            val range = when {
                part == "-" -> return null
                part.startsWith("-") -> {
                    val end = part.removePrefix("-").toIntOrNull() ?: return null
                    if (end < 1) return null
                    1..end.coerceAtMost(pageCount)
                }
                part.endsWith("-") -> {
                    val start = part.removeSuffix("-").toIntOrNull() ?: return null
                    if (start < 1 || start > pageCount) return null
                    start..pageCount
                }
                part.contains('-') -> {
                    val (rawStart, rawEnd) = part.split('-', limit = 2)
                    val start = rawStart.toIntOrNull() ?: return null
                    val end = rawEnd.toIntOrNull() ?: return null
                    if (start < 1 || end < start || end > pageCount) return null
                    start..end
                }
                else -> {
                    val page = part.toIntOrNull() ?: return null
                    if (page < 1 || page > pageCount) return null
                    page..page
                }
            }
            selected += range
        }
        return selected.toList().takeIf { it.isNotEmpty() }
    }

    /** "1-3, 7" for a set of 1-based pages, collapsing runs. */
    fun describe(pages: List<Int>): String {
        if (pages.isEmpty()) return ""
        val sorted = pages.sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (page in sorted.drop(1)) {
            if (page == previous + 1) {
                previous = page
                continue
            }
            parts += if (start == previous) "$start" else "$start-$previous"
            start = page
            previous = page
        }
        parts += if (start == previous) "$start" else "$start-$previous"
        return parts.joinToString(", ")
    }
}
