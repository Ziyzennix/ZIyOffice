package org.ziyoffice.ooxml

import kotlin.math.roundToInt
import org.ziyoffice.model.Block
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.model.VerticalMerge

/**
 * Generates the WordprocessingML for the document model.
 *
 * Two writers use it: [DocxWriter], which builds a whole new package, and
 * [DocxEditor], which needs to synthesise the few blocks a user added to a
 * document that is otherwise copied through unchanged. Keeping the generation
 * in one place means a new document and an edited document are written the
 * same way.
 *
 * Everything here produces direct formatting: the model stores resolved
 * formatting, so style names are flattened. That is a deliberate trade — a
 * document ZiyOffice writes itself has no styles to refer to — and it only
 * applies to content ZiyOffice writes. Content it merely carries over keeps
 * its original markup.
 */
internal object DocxXml {

    /** Supplies the relationship id an image is written under. */
    fun interface ImageRelId {
        fun of(image: InlineImage): String
    }

    fun writeBlock(sb: StringBuilder, block: Block, relId: ImageRelId) {
        when (block) {
            is Paragraph -> writeParagraph(sb, block, relId)
            is Table -> writeTable(sb, block, relId)
        }
    }

    fun writeParagraph(sb: StringBuilder, paragraph: Paragraph, relId: ImageRelId) {
        sb.append("<w:p>")
        writeParagraphProperties(sb, paragraph)
        paragraph.runs.forEach { run -> writeRun(sb, run) }
        paragraph.images.forEach { image -> writeImage(sb, image, relId.of(image)) }
        sb.append("</w:p>")
    }

    fun writeParagraphProperties(sb: StringBuilder, paragraph: Paragraph) {
        val props = StringBuilder()
        if (paragraph.pageBreakBefore) {
            props.append("<w:pageBreakBefore/>")
        }
        if (paragraph.alignment != ParagraphAlignment.START) {
            props.append("<w:jc w:val=\"").append(
                when (paragraph.alignment) {
                    ParagraphAlignment.CENTER -> "center"
                    ParagraphAlignment.END -> "right"
                    ParagraphAlignment.JUSTIFY -> "both"
                    ParagraphAlignment.START -> "left"
                },
            ).append("\"/>")
        }
        paragraph.indentTwips?.let {
            props.append("<w:ind w:left=\"").append(it).append("\"/>")
        }
        if (paragraph.spacingBeforeTwips != null ||
            paragraph.spacingAfterTwips != null ||
            paragraph.lineSpacingPercent != null
        ) {
            props.append("<w:spacing")
            paragraph.spacingBeforeTwips?.let {
                props.append(" w:before=\"").append(it).append('"')
            }
            paragraph.spacingAfterTwips?.let {
                props.append(" w:after=\"").append(it).append('"')
            }
            paragraph.lineSpacingPercent?.let {
                // A percentage of the normal line height is w:line in 240ths
                // (240 = single spacing) with the automatic rule.
                props.append(" w:line=\"").append(it * 240 / 100)
                    .append("\" w:lineRule=\"auto\"")
            }
            props.append("/>")
        }
        paragraph.list?.let { list ->
            props.append("<w:numPr><w:ilvl w:val=\"").append(list.level)
                .append("\"/><w:numId w:val=\"").append(list.numId)
                .append("\"/></w:numPr>")
        }
        if (props.isNotEmpty()) sb.append("<w:pPr>").append(props).append("</w:pPr>")
    }

    fun writeRun(sb: StringBuilder, run: Run) {
        if (run.text.isEmpty()) return
        sb.append("<w:r>")
        val props = StringBuilder()
        if (run.bold) props.append("<w:b/>")
        if (run.italic) props.append("<w:i/>")
        if (run.underline) props.append("<w:u w:val=\"single\"/>")
        if (run.strike) props.append("<w:strike/>")
        run.sizePt?.let {
            val halfPoints = (it * 2f).roundToInt()
            props.append("<w:sz w:val=\"").append(halfPoints)
                .append("\"/><w:szCs w:val=\"").append(halfPoints).append("\"/>")
        }
        run.colorRgb?.let {
            props.append("<w:color w:val=\"").append(hexColor(it)).append("\"/>")
        }
        if (props.isNotEmpty()) sb.append("<w:rPr>").append(props).append("</w:rPr>")
        writeRunText(sb, run.text)
        sb.append("</w:r>")
    }

    /** Tabs and line breaks become `w:tab` / `w:br` siblings of the text runs. */
    fun writeRunText(sb: StringBuilder, text: String) {
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                sb.append("<w:t xml:space=\"preserve\">")
                    .append(escapeText(buffer.toString()))
                    .append("</w:t>")
                buffer.clear()
            }
        }
        text.forEach { ch ->
            when (ch) {
                '\n' -> {
                    flush()
                    sb.append("<w:br/>")
                }
                '\t' -> {
                    flush()
                    sb.append("<w:tab/>")
                }
                else -> buffer.append(ch)
            }
        }
        flush()
    }

    fun writeTable(sb: StringBuilder, table: Table, relId: ImageRelId) {
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/>")
        if (table.borders) {
            sb.append("<w:tblBorders>")
            BORDER_EDGES.forEach { edge ->
                sb.append("<w:").append(edge)
                    .append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"808080\"/>")
            }
            sb.append("</w:tblBorders>")
        }
        sb.append("</w:tblPr><w:tblGrid>")
        repeat(table.columnCount) { sb.append("<w:gridCol/>") }
        sb.append("</w:tblGrid>")
        table.rows.forEach { row ->
            sb.append("<w:tr>")
            if (row.header) sb.append("<w:trPr><w:tblHeader/></w:trPr>")
            row.cells.forEach { cell ->
                sb.append("<w:tc><w:tcPr>")
                if (cell.columnSpan > 1) {
                    sb.append("<w:gridSpan w:val=\"").append(cell.columnSpan).append("\"/>")
                }
                when (cell.verticalMerge) {
                    VerticalMerge.RESTART -> sb.append("<w:vMerge w:val=\"restart\"/>")
                    VerticalMerge.CONTINUE -> sb.append("<w:vMerge/>")
                    VerticalMerge.NONE -> Unit
                }
                sb.append("</w:tcPr>")
                if (cell.blocks.isEmpty()) {
                    sb.append("<w:p/>")
                } else {
                    cell.blocks.forEach { block -> writeBlock(sb, block, relId) }
                }
                sb.append("</w:tc>")
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
    }

    fun writeImage(sb: StringBuilder, image: InlineImage, relId: String) {
        val cx = emu(image.widthDp ?: DEFAULT_IMAGE_WIDTH_DP)
        val cy = emu(image.heightDp ?: DEFAULT_IMAGE_HEIGHT_DP)
        sb.append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
        sb.append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
        sb.append("<wp:docPr id=\"1\" name=\"Picture\"/>")
        sb.append("<a:graphic><a:graphicData uri=\"").append(PICTURE_NAMESPACE).append("\">")
        sb.append("<pic:pic><pic:nvPicPr>")
        sb.append("<pic:cNvPr id=\"0\" name=\"Picture\"/><pic:cNvPicPr/>")
        sb.append("</pic:nvPicPr>")
        sb.append("<pic:blipFill><a:blip r:embed=\"").append(relId).append("\"/>")
        sb.append("<a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
        sb.append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
            .append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
        sb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>")
        sb.append("</pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>")
    }

    fun emu(dp: Float): Int = (dp * EMU_PER_DP).roundToInt()

    fun hexColor(rgb: Int): String = (rgb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

    fun imageExtension(bytes: ByteArray): String = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "gif"
        bytes.size >= 12
            && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()
            && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
            && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()
            && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "webp"
        else -> "bin"
    }

    fun imageContentType(extension: String): String = when (extension) {
        "png" -> "image/png"
        "jpg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    fun numberFormat(kind: ListKind): String = when (kind) {
        ListKind.BULLET -> "bullet"
        ListKind.NUMBER, ListKind.OTHER -> "decimal"
        ListKind.LETTER_LOWER -> "lowerLetter"
        ListKind.LETTER_UPPER -> "upperLetter"
        ListKind.ROMAN_LOWER -> "lowerRoman"
        ListKind.ROMAN_UPPER -> "upperRoman"
    }

    fun levelText(kind: ListKind, level: Int): String =
        if (kind == ListKind.BULLET) "•" else "%${level + 1}."

    /**
     * Puts [fragment] inside the element named [container].
     *
     * The container may be written as a self-closing element — a document with
     * no relationships has `<Relationships/>` — in which case it is opened up
     * first. Dropping the fragment silently would leave the package pointing
     * at something that is not there.
     */
    fun insertIntoContainer(text: String, container: String, fragment: String): String {
        val index = text.lastIndexOf("</$container>")
        if (index >= 0) {
            return text.substring(0, index) + fragment + text.substring(index)
        }
        val selfClosing = Regex("<$container\\b[^>]*/>").find(text) ?: return text
        val opened = selfClosing.value.removeSuffix("/>") + ">"
        return text.substring(0, selfClosing.range.first) + opened + fragment +
            "</$container>" + text.substring(selfClosing.range.last + 1)
    }

    fun escapeText(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    fun escapeAttribute(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    const val RELATIONSHIPS_NAMESPACE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val WORDPROCESSING_DRAWING_NAMESPACE =
        "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val DRAWINGML_NAMESPACE = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val PICTURE_NAMESPACE = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    const val PACKAGE_RELS_NAMESPACE =
        "http://schemas.openxmlformats.org/package/2006/relationships"
    const val CONTENT_TYPES_NAMESPACE =
        "http://schemas.openxmlformats.org/package/2006/content-types"
    const val EMU_PER_DP = 9525f
    const val DEFAULT_IMAGE_WIDTH_DP = 320f
    const val DEFAULT_IMAGE_HEIGHT_DP = 200f
    val BORDER_EDGES = listOf("top", "left", "bottom", "right", "insideH", "insideV")
}
