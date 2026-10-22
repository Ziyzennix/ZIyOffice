package org.ziyoffice.model

/**
 * The document model: paragraphs and styled runs, lists, tables, headers and
 * footers. Pure Kotlin, so it is unit-testable on the JVM and reusable by
 * another front end later.
 */
data class Document(
    val blocks: List<Block>,
    /**
     * The header shown on every page, or null when the document has none.
     *
     * Headers and footers are blocks, not plain text, because Word allows tables
     * and pictures in them. Only the default variant is modelled: a document
     * with different first-page or even-page headers keeps its body but loses
     * those variants, which [org.ziyoffice.ooxml.ContentAudit] reports before a
     * save.
     */
    val header: List<Block>? = null,
    /** The footer shown on every page, or null when the document has none. */
    val footer: List<Block>? = null,
)

/** A block-level element of the document body, in document order. */
sealed interface Block

data class Paragraph(
    val runs: List<Run> = emptyList(),
    val alignment: ParagraphAlignment = ParagraphAlignment.START,
    /** Paragraph style id (`w:pStyle`); null when the paragraph uses the default style. */
    val styleId: String? = null,
    /** List or bullet membership, resolved from the numbering part; null for body text. */
    val list: ListInfo? = null,
    /** Left indentation in twips (1440 = 1 inch); null when the document does not specify one. */
    val indentTwips: Int? = null,
    /** Inline images anchored in this paragraph, in document order. */
    val images: List<InlineImage> = emptyList(),
    /** Space before this paragraph in twips, resolved from styles/direct formatting. */
    val spacingBeforeTwips: Int? = null,
    /** Space after this paragraph in twips, resolved from styles/direct formatting. */
    val spacingAfterTwips: Int? = null,
    /**
     * Line height inside this paragraph as a percentage of the normal line height
     * (`w:line` with the automatic rule; 100 = single spacing).
     */
    val lineSpacingPercent: Int? = null,
    /** True when this paragraph begins a new page (`w:pageBreakBefore`). */
    val pageBreakBefore: Boolean = false,
) : Block

/**
 * An image embedded in the document, still in its original bytes (PNG, JPEG, ...).
 * The UI layer decodes it with downsampling; unsupported formats (EMF, WMF, SVG)
 * fail to decode there and are shown as an honest placeholder.
 */
class InlineImage(
    val bytes: ByteArray,
    /** Display width in dp (1/96 inch); null when the document gives no extent. */
    val widthDp: Float? = null,
    /** Display height in dp; null when the document gives no extent. */
    val heightDp: Float? = null,
)

data class Table(
    val rows: List<TableRow>,
    /** The table grid width in columns (from `w:tblGrid`, or derived from cell spans). */
    val columnCount: Int,
    /** True when the table draws borders (its own `tblBorders` or its table style's). */
    val borders: Boolean = false,
) : Block

data class TableRow(
    val cells: List<TableCell>,
    /** `w:tblHeader`, a repeating header row. */
    val header: Boolean = false,
)

data class TableCell(
    /** Cell content blocks (paragraphs; nested tables are legal OOXML). */
    val blocks: List<Block>,
    /** `w:gridSpan`, how many grid columns this cell spans. */
    val columnSpan: Int = 1,
    /** `w:vMerge`, whether this cell starts, continues or ignores a vertical merge. */
    val verticalMerge: VerticalMerge = VerticalMerge.NONE,
)

/**
 * A cell's part in a vertical merge.
 *
 * Word stores a vertical merge on every cell it covers: the top cell restarts
 * it and holds the content, and each cell below it continues the merge with no
 * content of its own. Modelling both states is what lets a save put the same
 * structure back; dropping the continuation cells would silently un-merge the
 * table on the next save.
 */
enum class VerticalMerge {
    /** Not vertically merged. */
    NONE,
    /** `w:vMerge w:val="restart"`: the top cell, which holds the content. */
    RESTART,
    /** `w:vMerge` with no value (or "continue"): covered by the cell above. */
    CONTINUE,
}

/**
 * A paragraph's list membership, already resolved through `numbering.xml`
 * (num → abstractNum → level) so the renderer never needs the numbering part.
 */
data class ListInfo(
    /** The numbering instance (`w:numId`); paragraphs sharing it share counters. */
    val numId: Int,
    /** Nesting level (`w:ilvl`), 0-based. */
    val level: Int,
    val kind: ListKind,
    /** The counter's starting value (`w:start`). */
    val start: Int = 1,
)

enum class ListKind { BULLET, NUMBER, LETTER_LOWER, LETTER_UPPER, ROMAN_LOWER, ROMAN_UPPER, OTHER }

data class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** Font size in points; null when the document does not specify one. */
    val sizePt: Float? = null,
    /** Text colour as 0xRRGGBB; null when the document uses automatic colour. */
    val colorRgb: Int? = null,
    /**
     * The field this run stands for, such as "PAGE".
     *
     * A field is a piece of text the word processor fills in: the page
     * number, the date, the number of pages. The text of a field run is the
     * result it last had, so a reader that cannot work the field out still
     * shows something sensible.
     */
    val field: String? = null,
)

/** True when two runs would look the same on the page. */
fun Run.hasSameFormattingAs(other: Run): Boolean =
    bold == other.bold &&
        italic == other.italic &&
        underline == other.underline &&
        strike == other.strike &&
        sizePt == other.sizePt &&
        colorRgb == other.colorRgb

enum class ParagraphAlignment { START, CENTER, END, JUSTIFY }

/**
 * A document made of plain text, one paragraph per line.
 *
 * This is what a scanned page becomes once its words are read: a stack of
 * lines. A blank line is a paragraph break, and a run of blank lines is one
 * break rather than several, which is how a page reads.
 */
fun documentFromText(text: String): Document {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<Block>()
    var afterBlank = false
    lines.forEach { line ->
        if (line.isBlank()) {
            if (blocks.isNotEmpty() && !afterBlank) {
                blocks += Paragraph()
                afterBlank = true
            }
        } else {
            blocks += Paragraph(runs = listOf(Run(line.trimEnd())))
            afterBlank = false
        }
    }
    // A page that ends in blank lines should not end in an empty paragraph.
    while (blocks.size > 1 && blocks.last() is Paragraph && (blocks.last() as Paragraph).runs.isEmpty()) {
        blocks.removeAt(blocks.lastIndex)
    }
    return Document(blocks = blocks.ifEmpty { listOf(Paragraph()) })
}

/**
 * The document with a page number in its footer.
 *
 * A footer that is not there yet is made. The number is a field, so the
 * word processor fills it in for each page; the text of the field is the
 * result it last had, so a reader that cannot work it out still shows
 * something.
 */
fun Document.withPageNumberInFooter(): Document {
    val blocks = footer.orEmpty()
    // A footer that already has a page number is left alone.
    val hasNumber = blocks.any { block ->
        block is Paragraph && block.runs.any { it.field?.equals("PAGE", ignoreCase = true) == true }
    }
    if (hasNumber) return this
    val number = Paragraph(runs = listOf(Run(text = "1", field = "PAGE")))
    return copy(footer = blocks + number)
}
