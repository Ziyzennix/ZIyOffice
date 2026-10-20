package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table

/**
 * Writes the ZiyOffice document model back into a `.docx` package, using only
 * platform APIs (`java.util.zip`). The counterpart of [DocxReader], and the
 * foundation of editing: read → edit → write → reopen keeps working.
 *
 * The writer emits a minimal, standards-valid package: content types, package
 * and document relationships, `word/document.xml`, a small `word/styles.xml`
 * with document defaults, and — only when needed — `word/numbering.xml` and
 * `word/media/` entries.
 *
 * Deliberate simplification: the model stores *resolved* formatting, so styles
 * are flattened into direct formatting on save (every run and paragraph carries
 * its own `w:rPr` / `w:pPr`; no `w:pStyle` references are emitted). Content,
 * appearance, lists, tables and images all survive a save/reopen round trip;
 * style *names* do not. A future revision can add style-preserving patching.
 */
class DocxWriter {

    fun write(document: Document): ByteArray {
        val numbering = NumberingPlan.from(document)
        val images = ImageRegistry(
            firstRelId = FIRST_IMAGE_REL_ID + if (numbering.isEmpty) 0 else 1,
        )
        val body = StringBuilder()
        document.blocks.forEach { block -> writeBlock(body, block, images) }
        if (body.isEmpty()) {
            // The body must contain at least one block-level element.
            body.append("<w:p/>")
        }

        val parts = linkedMapOf<String, ByteArray>()
        parts["[Content_Types].xml"] = contentTypes(numbering, images).encodeToByteArray()
        parts["_rels/.rels"] = ROOT_RELS.encodeToByteArray()
        parts["word/document.xml"] = documentXml(body.toString()).encodeToByteArray()
        parts["word/_rels/document.xml.rels"] = documentRels(numbering, images).encodeToByteArray()
        parts["word/styles.xml"] = STYLES_XML.encodeToByteArray()
        if (!numbering.isEmpty) {
            parts["word/numbering.xml"] = numbering.toXml().encodeToByteArray()
        }
        images.slots.forEach { slot -> parts["word/${slot.partName}"] = slot.bytes }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- body xml

    private fun writeBlock(sb: StringBuilder, block: Block, images: ImageRegistry) {
        when (block) {
            is Paragraph -> writeParagraph(sb, block, images)
            is Table -> writeTable(sb, block, images)
        }
    }

    private fun writeParagraph(sb: StringBuilder, paragraph: Paragraph, images: ImageRegistry) {
        sb.append("<w:p>")
        writeParagraphProperties(sb, paragraph)
        paragraph.runs.forEach { run -> writeRun(sb, run) }
        paragraph.images.forEach { image -> writeImage(sb, image, images.relIdFor(image)) }
        sb.append("</w:p>")
    }

    private fun writeParagraphProperties(sb: StringBuilder, paragraph: Paragraph) {
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

    private fun writeRun(sb: StringBuilder, run: Run) {
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
    private fun writeRunText(sb: StringBuilder, text: String) {
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

    private fun writeTable(sb: StringBuilder, table: Table, images: ImageRegistry) {
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
                sb.append("</w:tcPr>")
                if (cell.blocks.isEmpty()) {
                    sb.append("<w:p/>")
                } else {
                    cell.blocks.forEach { block -> writeBlock(sb, block, images) }
                }
                sb.append("</w:tc>")
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
    }

    private fun writeImage(sb: StringBuilder, image: InlineImage, relId: String) {
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
        sb.append("</pic:spPr></pic:pic>")
        sb.append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>")
    }

    private fun documentXml(body: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:document xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\" xmlns:r=\"$RELATIONSHIPS_NAMESPACE\" " +
            "xmlns:wp=\"$WORDPROCESSING_DRAWING_NAMESPACE\" xmlns:a=\"$DRAWINGML_NAMESPACE\" " +
            "xmlns:pic=\"$PICTURE_NAMESPACE\"><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\"1418\"/>" +
            "</w:sectPr></w:body></w:document>"

    // ------------------------------------------------------------- package xml

    private fun contentTypes(numbering: NumberingPlan, images: ImageRegistry): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        builder.append("<Types xmlns=\"$CONTENT_TYPES_NAMESPACE\">")
        builder.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        builder.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        images.slots.map { it.extension }.distinct().forEach { extension ->
            builder.append("<Default Extension=\"").append(extension)
                .append("\" ContentType=\"").append(imageContentType(extension)).append("\"/>")
        }
        builder.append("<Override PartName=\"/word/document.xml\" ")
            .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>")
        builder.append("<Override PartName=\"/word/styles.xml\" ")
            .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>")
        if (!numbering.isEmpty) {
            builder.append("<Override PartName=\"/word/numbering.xml\" ")
                .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>")
        }
        builder.append("</Types>")
        return builder.toString()
    }

    private fun documentRels(numbering: NumberingPlan, images: ImageRegistry): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        builder.append("<Relationships xmlns=\"$PACKAGE_RELS_NAMESPACE\">")
        builder.append("<Relationship Id=\"rId1\" Type=\"$RELATIONSHIPS_NAMESPACE/styles\" Target=\"styles.xml\"/>")
        if (!numbering.isEmpty) {
            builder.append("<Relationship Id=\"rId2\" Type=\"$RELATIONSHIPS_NAMESPACE/numbering\" Target=\"numbering.xml\"/>")
        }
        images.slots.forEach { slot ->
            builder.append("<Relationship Id=\"").append(slot.relId)
                .append("\" Type=\"").append(RELATIONSHIPS_NAMESPACE).append("/image\" Target=\"")
                .append(slot.partName).append("\"/>")
        }
        builder.append("</Relationships>")
        return builder.toString()
    }

    // ------------------------------------------------------------------ pieces

    /** Assigns relationship ids and media part names to images as they appear. */
    private class ImageRegistry(private val firstRelId: Int) {
        val slots = mutableListOf<ImageSlot>()
        private val assigned = IdentityHashMap<InlineImage, ImageSlot>()

        fun relIdFor(image: InlineImage): String = assigned.getOrPut(image) {
            val index = slots.size
            val extension = imageExtension(image.bytes)
            val slot = ImageSlot(
                relId = "rId${firstRelId + index}",
                partName = "media/image${index + 1}.$extension",
                extension = extension,
                bytes = image.bytes,
            )
            slots += slot
            slot
        }.relId
    }

    private class ImageSlot(
        val relId: String,
        val partName: String,
        val extension: String,
        val bytes: ByteArray,
    )

    /** The numbering part, rebuilt from the list memberships used in the document. */
    private class NumberingPlan(private val levels: Map<Int, Map<Int, ListInfo>>) {

        val isEmpty: Boolean get() = levels.isEmpty()

        fun toXml(): String {
            val numIds = levels.keys.sorted()
            val builder = StringBuilder()
            builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            builder.append("<w:numbering xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">")
            numIds.forEachIndexed { abstractIndex, numId ->
                builder.append("<w:abstractNum w:abstractNumId=\"").append(abstractIndex).append("\">")
                levels.getValue(numId).toSortedMap().forEach { (level, info) ->
                    builder.append("<w:lvl w:ilvl=\"").append(level).append("\">")
                    builder.append("<w:start w:val=\"").append(info.start).append("\"/>")
                    builder.append("<w:numFmt w:val=\"").append(numberFormat(info.kind)).append("\"/>")
                    builder.append("<w:lvlText w:val=\"")
                        .append(escapeAttribute(levelText(info.kind, level))).append("\"/>")
                    builder.append("<w:lvlJc w:val=\"left\"/>")
                    builder.append("<w:pPr><w:ind w:left=\"").append((level + 1) * 709)
                        .append("\" w:hanging=\"283\"/></w:pPr>")
                    builder.append("</w:lvl>")
                }
                builder.append("</w:abstractNum>")
            }
            numIds.forEachIndexed { abstractIndex, numId ->
                builder.append("<w:num w:numId=\"").append(numId)
                    .append("\"><w:abstractNumId w:val=\"").append(abstractIndex)
                    .append("\"/></w:num>")
            }
            builder.append("</w:numbering>")
            return builder.toString()
        }

        companion object {
            fun from(document: Document): NumberingPlan {
                val levels = mutableMapOf<Int, MutableMap<Int, ListInfo>>()
                fun visit(blocks: List<Block>) {
                    blocks.forEach { block ->
                        when (block) {
                            is Paragraph -> block.list?.let { list ->
                                val byLevel = levels.getOrPut(list.numId) { mutableMapOf() }
                                if (list.level !in byLevel) byLevel[list.level] = list
                            }
                            is Table -> block.rows.forEach { row ->
                                row.cells.forEach { cell -> visit(cell.blocks) }
                            }
                        }
                    }
                }
                visit(document.blocks)
                return NumberingPlan(levels)
            }
        }
    }

    internal companion object {
        const val RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val WORDPROCESSING_DRAWING_NAMESPACE =
            "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
        const val DRAWINGML_NAMESPACE = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val PICTURE_NAMESPACE = "http://schemas.openxmlformats.org/drawingml/2006/picture"
        const val PACKAGE_RELS_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships"
        const val CONTENT_TYPES_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/content-types"
        const val FIRST_IMAGE_REL_ID = 2
        const val EMU_PER_DP = 9525f
        const val DEFAULT_IMAGE_WIDTH_DP = 320f
        const val DEFAULT_IMAGE_HEIGHT_DP = 200f
        val BORDER_EDGES = listOf("top", "left", "bottom", "right", "insideH", "insideV")

        const val STYLES_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<w:styles xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\">" +
                "<w:docDefaults><w:rPrDefault><w:rPr>" +
                "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>" +
                "</w:rPr></w:rPrDefault></w:docDefaults>" +
                "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">" +
                "<w:name w:val=\"Normal\"/>" +
                "<w:rPr><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/></w:rPr>" +
                "</w:style></w:styles>"

        const val ROOT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"$PACKAGE_RELS_NAMESPACE\">" +
                "<Relationship Id=\"rId1\" Type=\"$RELATIONSHIPS_NAMESPACE/officeDocument\" " +
                "Target=\"word/document.xml\"/></Relationships>"

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
            if (kind == ListKind.BULLET) "\u2022" else "%${level + 1}."

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
    }
}
