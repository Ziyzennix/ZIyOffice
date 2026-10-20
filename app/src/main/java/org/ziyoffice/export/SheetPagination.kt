package org.ziyoffice.export

/**
 * Page breaks for the spreadsheet exporter, kept free of Android types so the
 * one property that matters — every row and every column printed exactly once —
 * is testable on the JVM.
 *
 * The exporter draws from these ranges; it never recomputes them, so a fix here
 * is a fix to what actually prints.
 */
internal object SheetPagination {

    /**
     * Splits [count] columns into pages that fit [pageWidth] after the row-number
     * [gutter]. A column wider than the page still gets a page of its own rather
     * than being dropped.
     *
     * Returns half-open index ranges into the column list: `[start, end)`.
     */
    fun columnPages(
        columnWidths: List<Float>,
        gutter: Float,
        pageWidth: Float,
    ): List<IntRange> {
        if (columnWidths.isEmpty()) return emptyList()
        val pages = mutableListOf<IntRange>()
        var start = 0
        while (start < columnWidths.size) {
            var end = start
            var width = gutter
            while (end < columnWidths.size && width + columnWidths[end] <= pageWidth) {
                width += columnWidths[end]
                end++
            }
            if (end == start) end = start + 1
            pages += start until end
            start = end
        }
        return pages
    }

    /**
     * Splits rows `[first, last]` (inclusive sheet indices) into pages of at most
     * [rowsPerPage] rows. The final page keeps the remainder; no row is dropped.
     */
    fun rowPages(first: Int, last: Int, rowsPerPage: Int): List<IntRange> {
        if (last < first) return emptyList()
        val perPage = rowsPerPage.coerceAtLeast(1)
        val pages = mutableListOf<IntRange>()
        var start = first
        while (start <= last) {
            val end = minOf(last, start + perPage - 1)
            pages += start..end
            start = end + 1
        }
        return pages
    }
}
