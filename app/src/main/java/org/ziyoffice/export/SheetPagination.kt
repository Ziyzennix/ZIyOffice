package org.ziyoffice.export

/**
 * Page breaks for the sheet exporter, free of Android types so the one property
 * that matters is testable on the JVM: every row and every column is printed
 * exactly once.
 *
 * The exporter draws from these ranges and never recomputes them, so a fix here
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
     * Splits rows `[first, last]` into pages whose heights fit [availableHeight].
     * [rowHeights] is indexed from [first]. A row taller than the space still
     * gets a page of its own; no row is dropped.
     */
    fun rowPagesByHeight(
        first: Int,
        last: Int,
        rowHeights: List<Float>,
        availableHeight: Float,
    ): List<IntRange> {
        if (last < first) return emptyList()
        val pages = mutableListOf<IntRange>()
        var start = first
        while (start <= last) {
            var end = start
            var used = 0f
            while (end <= last) {
                val height = rowHeights.getOrElse(end - first) { 0f }
                if (end > start && used + height > availableHeight) break
                used += height
                end++
            }
            pages += start..(end - 1).coerceAtLeast(start)
            start = end
        }
        return pages
    }
}
