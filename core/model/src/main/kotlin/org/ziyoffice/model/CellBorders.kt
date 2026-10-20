package org.ziyoffice.model

/**
 * The line around a cell.
 *
 * A border is stored per cell and per edge, which is what a worksheet keeps.
 * A table drawn with "all borders" is one where every cell carries all four
 * edges, and one drawn with a box carries the outer edges only.
 */
enum class BorderStyle {
    NONE,
    THIN,
    MEDIUM,
    THICK,
    DASHED,
    DOTTED,
}

/** The four edges of a cell. */
data class CellBorders(
    val top: BorderStyle = BorderStyle.NONE,
    val bottom: BorderStyle = BorderStyle.NONE,
    val left: BorderStyle = BorderStyle.NONE,
    val right: BorderStyle = BorderStyle.NONE,
) {
    val isEmpty: Boolean
        get() = top == BorderStyle.NONE &&
            bottom == BorderStyle.NONE &&
            left == BorderStyle.NONE &&
            right == BorderStyle.NONE
}
