package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.model.TableCell
import org.ziyoffice.model.TableRow
import org.ziyoffice.model.VerticalMerge
import org.ziyoffice.ooxml.xml.XmlElement
import org.ziyoffice.ooxml.xml.XmlScanner

/**
 * A `.docx` package that has been opened for editing, together with a snapshot
 * of everything the editor is allowed to change.
 *
 * The old writer rebuilt a document from the model alone: a save produced a
 * brand-new package holding only what the model could express, so comments,
 * footnotes, tracked changes, hyperlinks, equations, text boxes, section
 * breaks, styles and document properties were dropped on the first save. That
 * is data loss disguised as a save.
 *
 * [DocxSource] keeps the original package instead. The model is the editable
 * view of it; the snapshot says which bytes each part of the model came from.
 * [DocxEditor] then writes the edited model back into the original package,
 * changing only the elements the user actually touched and copying everything
 * else through untouched.
 */
class DocxSource internal constructor(
    /** Every entry of the original package, keyed by its name inside the zip. */
    val parts: Map<String, ByteArray>,
    /** The document as the model sees it. */
    val document: Document,
    internal val body: SourceBody,
    internal val header: SourceBody?,
    internal val footer: SourceBody?,
    /**
     * Parts the writer owns and must rebuild: styles, numbering and the
     * relationship parts. Null when the document has none.
     */
    internal val stylesPart: String?,
    internal val numberingPart: String?,
    internal val documentRelsPart: String?,
    internal val contentTypesPart: String?,
) {

    companion object {
        /** Reads a package from a stream. */
        fun read(input: InputStream): DocxSource = DocxSourceReader().read(input)

        /** Reads a package from bytes. */
        fun capture(bytes: ByteArray): DocxSource = read(bytes.inputStream())
    }
}

/**
 * One part of a document (the body, a header or a footer) with the source
 * elements its model blocks came from.
 */
internal class SourceBody internal constructor(
    /** The name of the package part, e.g. `word/document.xml`. */
    val partName: String,
    /** The relationship part that belongs to it, e.g. `word/_rels/document.xml.rels`. */
    val relsPartName: String,
    /** The root element (`w:body`, `w:hdr` or `w:ftr`). */
    val container: XmlElement,
    /** The original XML of the part. */
    val xml: String,
    /** The source blocks, in the same order as the model's blocks. */
    val blocks: List<SourceBlock>,
)

/** A block of a document that the model can also represent. */
internal sealed interface SourceBlock {
    /** The element the block came from. */
    val element: XmlElement

    /** The model block this source produced. */
    val modelBlock: Block
}

/**
 * A source paragraph and the model paragraph it produced.
 *
 * [model] is what the editor compares against: when the model paragraph still
 * equals it, the paragraph is written back byte for byte.
 */
internal class SourceParagraph internal constructor(
    /** The `w:p` element. */
    override val element: XmlElement,
    /** Its `w:pPr`, when it has one. */
    val properties: XmlElement?,
    /** The text-bearing runs, in document order. */
    val runs: List<SourceRun>,
    /** The inline images, in document order. */
    val images: List<SourceImage>,
    /** The model paragraph this source produced. */
    val model: Paragraph,
    /** The XML of the part this paragraph belongs to. */
    val xml: String,
) : SourceBlock {
    override val modelBlock: Block get() = model

    /**
     * The images that are part of the model: the ones whose bytes the
     * relationships resolved to. A picture that points at a missing or remote
     * target stays in the package but is not editable.
     */
    val modelledImages: List<SourceImage> get() = images.filter { it.bytes != null }
}

/** One run of a source paragraph. */
internal class SourceRun internal constructor(
    /** The `w:r` element. */
    val element: XmlElement,
    /** Its text-bearing children (`w:t`, `w:tab`, `w:br`, `w:cr`), in order. */
    val contentNodes: List<XmlElement>,
    /** The model run this source produced. */
    val model: Run,
)

/** An inline image inside a source paragraph. */
internal class SourceImage internal constructor(
    /** The `w:drawing` or `w:pict` element that carries the picture. */
    val element: XmlElement,
    /** The relationship id the picture points at, when it has one. */
    val relId: String?,
    /** Display width in dp; null when the source gives no extent. */
    val widthDp: Float?,
    /** Display height in dp; null when the source gives no extent. */
    val heightDp: Float?,
    /** The media bytes the relationship resolves to. */
    val bytes: ByteArray?,
)

/** A source table and the model table it produced. */
internal class SourceTable internal constructor(
    /** The `w:tbl` element. */
    override val element: XmlElement,
    /** The rows, in document order. */
    val rows: List<SourceRow>,
    /** The model table this source produced. */
    val model: Table,
    /** The XML of the part this table belongs to. */
    val xml: String,
) : SourceBlock {
    override val modelBlock: Block get() = model
}

/** One row of a source table. */
internal class SourceRow internal constructor(
    /** The `w:tr` element. */
    val element: XmlElement,
    /** The cells, in document order. */
    val cells: List<SourceCell>,
    /** The model row this source produced. */
    val model: TableRow,
    /** The XML of the part this row belongs to. */
    val xml: String,
)

/** One cell of a source table. */
internal class SourceCell internal constructor(
    /** The `w:tc` element. */
    val element: XmlElement,
    /** The blocks inside the cell. */
    val blocks: List<SourceBlock>,
    /** The model cell this source produced. */
    val model: TableCell,
    /** True when the cell draws its own borders. */
    val drawsBorders: Boolean,
    /** The XML of the part this cell belongs to. */
    val xml: String,
)

/**
 * Reads a `.docx` package into a [DocxSource]: the model plus the snapshot of
 * the elements the model came from.
 *
 * The reader is built on [XmlScanner] rather than the platform pull parser
 * because the writer needs to know where each element sits in the original
 * XML. One parser serves both directions, so the model and the snapshot can
 * never disagree about what the document contains.
 */
internal class DocxSourceReader {

    fun read(input: InputStream): DocxSource {
        val parts = readParts(input)
        val documentBytes = parts[DOCUMENT_PART]
            ?: throw IOException("$DOCUMENT_PART not found in the package")
        val styles = parts[STYLES_PART]?.let { parseStyles(it) } ?: StyleSheet.EMPTY
        val numbering = parts[NUMBERING_PART]?.let { parseNumbering(it) } ?: emptyMap()
        val rels = parts[RELS_PART]?.let { parseRelationships(it) } ?: emptyMap()

        val documentXml = documentBytes.decodeToString()
        val documentTree = XmlScanner.parse(documentXml)
        val bodyElement = documentTree.root.childrenNamed("body").firstOrNull()
            ?: documentTree.root

        val reader = BodyReader(documentXml, styles, numbering, rels, parts)
        val bodyBlocks = reader.readBlocks(bodyElement)
        val headerRel = reader.headerRelId
        val footerRel = reader.footerRelId

        val header = readSidePart(parts, rels, headerRel, "word/header1.xml")
        val footer = readSidePart(parts, rels, footerRel, "word/footer1.xml")

        return DocxSource(
            parts = parts,
            document = Document(
                blocks = bodyBlocks.map { it.modelBlock },
                header = header?.blocks?.map { it.modelBlock },
                footer = footer?.blocks?.map { it.modelBlock },
            ),
            body = SourceBody(
                partName = DOCUMENT_PART,
                relsPartName = RELS_PART,
                container = bodyElement,
                xml = documentXml,
                blocks = bodyBlocks,
            ),
            header = header,
            footer = footer,
            stylesPart = STYLES_PART.takeIf { it in parts },
            numberingPart = NUMBERING_PART.takeIf { it in parts },
            documentRelsPart = RELS_PART.takeIf { it in parts },
            contentTypesPart = CONTENT_TYPES_PART.takeIf { it in parts },
        )
    }

    /** Reads a header or footer part and the model blocks inside it. */
    private fun readSidePart(
        parts: Map<String, ByteArray>,
        rels: Map<String, Relationship>,
        relId: String?,
        fallbackName: String,
    ): SourceBody? {
        val target = relId?.let { rels[it]?.target } ?: return null
        val partName = resolvePartPath(target)
        val bytes = parts[partName] ?: return null
        val relsName = relsNameOf(partName)
        val sideRels = parts[relsName]?.let { parseRelationships(it) } ?: emptyMap()
        val xml = bytes.decodeToString()
        val tree = XmlScanner.parse(xml)
        val reader = BodyReader(
            xml = xml,
            styles = StyleSheet.EMPTY,
            numbering = emptyMap(),
            rels = sideRels,
            parts = parts,
        )
        val blocks = reader.readBlocks(tree.root)
        return SourceBody(
            partName = partName,
            relsPartName = relsName,
            container = tree.root,
            xml = xml,
            blocks = blocks,
        )
    }

    /** Reads every entry of the package, with a size limit on the whole thing. */
    private fun readParts(input: InputStream): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readEntryCapped()
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) {
                    throw IOException(
                        "Package exceeds the ${MAX_TOTAL_BYTES / (1024 * 1024)} MB safety limit",
                    )
                }
                parts[entry.name] = bytes
            }
        }
        return parts
    }

    private fun ZipInputStream.readEntryCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_PART_BYTES) {
                throw IOException("Part exceeds the ${MAX_PART_BYTES / (1024 * 1024)} MB safety limit")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ styles

    /**
     * Paragraph styles resolved over `w:docDefaults`. Style chains are followed
     * through `w:basedOn` (cycle-safe), ancestors first, so the leaf style wins.
     * Table styles are only inspected for borders.
     */
    internal class StyleSheet(
        val docDefaultsRun: RunProps,
        val docDefaultsParagraph: ParagraphProps,
        val stylesById: Map<String, ParagraphStyle>,
        val defaultStyleId: String?,
        val tableBorders: Map<String, Boolean>,
    ) {
        fun resolve(styleId: String?): ResolvedStyle {
            val chain = mutableListOf<ParagraphStyle>()
            val seen = mutableSetOf<String>()
            var current = stylesById[styleId ?: defaultStyleId]
            while (current != null && seen.add(current.id)) {
                chain += current
                current = current.basedOn?.let { stylesById[it] }
            }
            var run = docDefaultsRun
            var paragraph = docDefaultsParagraph
            for (style in chain.asReversed()) {
                run = style.run.on(run)
                paragraph = style.paragraph.on(paragraph)
            }
            return ResolvedStyle(run, paragraph)
        }

        companion object {
            val EMPTY = StyleSheet(
                docDefaultsRun = RunProps(),
                docDefaultsParagraph = ParagraphProps(),
                stylesById = emptyMap(),
                defaultStyleId = null,
                tableBorders = emptyMap(),
            )
        }
    }

    internal class ResolvedStyle(val run: RunProps, val paragraph: ParagraphProps)

    internal class ParagraphStyle(
        val id: String,
        val basedOn: String?,
        val run: RunProps,
        val paragraph: ParagraphProps,
        val isDefault: Boolean,
    )

    /** Paragraph properties where null means "not specified here". */
    internal class ParagraphProps(
        val spacingBeforeTwips: Int? = null,
        val spacingAfterTwips: Int? = null,
        val lineSpacingPercent: Int? = null,
    ) {
        /** This style's explicit values layered on top of inherited [base] ones. */
        fun on(base: ParagraphProps): ParagraphProps = ParagraphProps(
            spacingBeforeTwips = spacingBeforeTwips ?: base.spacingBeforeTwips,
            spacingAfterTwips = spacingAfterTwips ?: base.spacingAfterTwips,
            lineSpacingPercent = lineSpacingPercent ?: base.lineSpacingPercent,
        )
    }

    /** Run properties where null means "not specified here". */
    internal class RunProps(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val strike: Boolean? = null,
        val sizePt: Float? = null,
        val colorRgb: Int? = null,
    ) {
        /** This style's explicit values layered on top of inherited [base] ones. */
        fun on(base: RunProps): RunProps = RunProps(
            bold = bold ?: base.bold,
            italic = italic ?: base.italic,
            underline = underline ?: base.underline,
            strike = strike ?: base.strike,
            sizePt = sizePt ?: base.sizePt,
            colorRgb = colorRgb ?: base.colorRgb,
        )
    }

    private fun parseStyles(bytes: ByteArray): StyleSheet {
        val tree = XmlScanner.parse(bytes.decodeToString())
        val root = tree.root
        var docDefaultsRun = RunProps()
        var docDefaultsParagraph = ParagraphProps()
        val styles = mutableMapOf<String, ParagraphStyle>()
        val tableBorders = mutableMapOf<String, Boolean>()
        var defaultStyleId: String? = null

        val docDefaults = root.child("docDefaults")
        docDefaults?.let { defaults ->
            defaults.descendants().forEach { element ->
                when {
                    element.localName == "rPrDefault" -> {
                        element.child("rPr")?.let { docDefaultsRun = parseRunProps(it) }
                    }
                    element.localName == "pPrDefault" -> {
                        element.child("pPr")?.let { docDefaultsParagraph = parseParagraphProps(it) }
                    }
                }
            }
        }
        root.childrenNamed("style").forEach { element ->
            when (element.attr("type")) {
                null, "paragraph" -> {
                    val style = parseStyle(element)
                    if (style != null) {
                        styles[style.id] = style
                        if (style.isDefault) defaultStyleId = style.id
                    }
                }
                "table" -> {
                    val id = element.attr("styleId") ?: return@forEach
                    val borders = element.descendants().firstOrNull {
                        it.localName == "tblBorders"
                    }
                    tableBorders[id] = bordersDrawn(borders)
                }
                else -> Unit
            }
        }
        return StyleSheet(
            docDefaultsRun,
            docDefaultsParagraph,
            styles,
            defaultStyleId,
            tableBorders,
        )
    }

    private fun parseStyle(element: XmlElement): ParagraphStyle? {
        val id = element.attr("styleId") ?: return null
        val basedOn = element.child("basedOn")?.attr("val")
        val runProps = element.child("rPr")?.let { parseRunProps(it) } ?: RunProps()
        val paragraphProps = element.child("pPr")?.let { parseParagraphProps(it) } ?: ParagraphProps()
        return ParagraphStyle(
            id = id,
            basedOn = basedOn,
            run = runProps,
            paragraph = paragraphProps,
            isDefault = onOff(element.attr("default")),
        )
    }

    /** Reads a `w:pPr` for spacing values. */
    private fun parseParagraphProps(element: XmlElement): ParagraphProps {
        val spacing = element.child("spacing")
        return ParagraphProps(
            spacingBeforeTwips = spacing?.attr("before")?.toIntOrNull(),
            spacingAfterTwips = spacing?.attr("after")?.toIntOrNull(),
            lineSpacingPercent = spacing?.let { lineSpacingPercent(it) },
        )
    }

    private fun parseRunProps(element: XmlElement): RunProps {
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var strike: Boolean? = null
        var sizePt: Float? = null
        var colorRgb: Int? = null
        element.children.forEach { child ->
            when (child.localName) {
                "b" -> bold = onOffValue(child)
                "i" -> italic = onOffValue(child)
                "u" -> underline = underlineValue(child)
                "strike" -> strike = onOffValue(child)
                "sz" -> sizePt = child.attr("val")?.toFloatOrNull()?.div(2f)
                "color" -> colorRgb = colorValue(child)
            }
        }
        return RunProps(bold, italic, underline, strike, sizePt, colorRgb)
    }

    // --------------------------------------------------------------- numbering

    private fun parseNumbering(bytes: ByteArray): Map<Pair<Int, Int>, ListInfo> {
        val tree = XmlScanner.parse(bytes.decodeToString())
        val abstractLevels = mutableMapOf<Int, Map<Int, Pair<ListKind, Int>>>()
        val nums = mutableMapOf<Int, Int>()

        tree.root.childrenNamed("abstractNum").forEach { element ->
            val id = element.attr("abstractNumId")?.toIntOrNull() ?: return@forEach
            val levels = mutableMapOf<Int, Pair<ListKind, Int>>()
            element.childrenNamed("lvl").forEach { level ->
                val ilvl = level.attr("ilvl")?.toIntOrNull() ?: 0
                val kind = level.child("numFmt")?.let { listKind(it.attr("val")) } ?: ListKind.OTHER
                val start = level.child("start")?.attr("val")?.toIntOrNull() ?: 1
                levels[ilvl] = kind to start
            }
            abstractLevels[id] = levels
        }
        tree.root.childrenNamed("num").forEach { element ->
            val numId = element.attr("numId")?.toIntOrNull() ?: return@forEach
            val abstractId = element.child("abstractNumId")?.attr("val")?.toIntOrNull()
                ?: return@forEach
            nums[numId] = abstractId
        }

        val resolved = mutableMapOf<Pair<Int, Int>, ListInfo>()
        nums.forEach { (numId, abstractId) ->
            abstractLevels[abstractId]?.forEach { (level, kindAndStart) ->
                resolved[numId to level] =
                    ListInfo(numId, level, kindAndStart.first, kindAndStart.second)
            }
        }
        return resolved
    }

    private fun listKind(value: String?): ListKind = when (value) {
        "bullet" -> ListKind.BULLET
        "decimal", "decimalZero", "ordinal" -> ListKind.NUMBER
        "lowerLetter" -> ListKind.LETTER_LOWER
        "upperLetter" -> ListKind.LETTER_UPPER
        "lowerRoman" -> ListKind.ROMAN_LOWER
        "upperRoman" -> ListKind.ROMAN_UPPER
        else -> ListKind.OTHER
    }


    internal companion object {
        const val DOCUMENT_PART = "word/document.xml"
        const val STYLES_PART = "word/styles.xml"
        const val NUMBERING_PART = "word/numbering.xml"
        const val RELS_PART = "word/_rels/document.xml.rels"
        const val CONTENT_TYPES_PART = "[Content_Types].xml"
        const val MAX_PART_BYTES = 64 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 192 * 1024 * 1024
        const val EMU_PER_DP = 9525f
        val OFF_VALUES = setOf("0", "false", "off")

        /** OOXML on/off element semantics: absent value attribute means on. */
        fun onOffValue(element: XmlElement): Boolean =
            element.attr("val")?.lowercase()?.let { it !in OFF_VALUES } ?: true

        /** Attribute-value semantics (w:default="1"): absent or off means false. */
        fun onOff(value: String?): Boolean =
            value?.lowercase()?.let { it !in OFF_VALUES } ?: false

        fun underlineValue(element: XmlElement): Boolean =
            element.attr("val")?.lowercase()?.let { it !in OFF_VALUES && it != "none" } ?: true

        fun colorValue(element: XmlElement): Int? {
            val value = element.attr("val")?.removePrefix("#") ?: return null
            if (value.equals("auto", ignoreCase = true)) return null
            return value.toIntOrNull(16)
        }

        /**
         * `w:line` with the automatic rule is a line-height multiplier in 240ths
         * of a line; exact and at-least rules are absolute twips and are not
         * read here.
         */
        fun lineSpacingPercent(spacing: XmlElement): Int? {
            val rule = spacing.attr("lineRule") ?: "auto"
            if (rule != "auto") return null
            val line = spacing.attr("line")?.toIntOrNull() ?: return null
            if (line <= 0) return null
            return (line * 100 + 120) / 240
        }

        /** True when any edge of a border element is drawn. */
        fun bordersDrawn(element: XmlElement?): Boolean {
            if (element == null) return false
            return element.children.any { child ->
                val value = child.attr("val")?.lowercase() ?: "single"
                value != "none" && value != "nil"
            }
        }
    }
}

/**
 * Reads the blocks of one container — a body, a header or a footer — into the
 * model, keeping the source elements alongside.
 */
internal class BodyReader(
    private val xml: String,
    private val styles: DocxSourceReader.StyleSheet,
    private val numbering: Map<Pair<Int, Int>, ListInfo>,
    private val rels: Map<String, Relationship>,
    private val parts: Map<String, ByteArray>,
) {

    /** Relationship ids of the default header and footer, read from `w:sectPr`. */
    var headerRelId: String? = null
        private set
    var footerRelId: String? = null
        private set

    /**
     * A page break inside a run belongs to the paragraph that follows it, so it
     * is carried over the same way the reader always did.
     */
    private var pendingPageBreak = false

    fun readBlocks(container: XmlElement): List<SourceBlock> {
        val blocks = mutableListOf<SourceBlock>()
        container.children.forEach { child ->
            when {
                child.localName == "p" && child.isWord() -> blocks += readParagraph(child)
                child.localName == "tbl" && child.isWord() -> blocks += readTable(child)
                child.localName == "sectPr" && child.isWord() -> readSectionProperties(child)
            }
        }
        return blocks
    }

    /** Records the default header/footer relationship ids of a section. */
    private fun readSectionProperties(sectPr: XmlElement) {
        sectPr.children.forEach { child ->
            val type = child.attr("type")
            val isDefault = type == null || type == "default"
            if (!isDefault) return@forEach
            val id = child.attr(RELATIONSHIPS_NAMESPACE, "id") ?: child.attr(null, "id")
            when (child.localName) {
                "headerReference" -> if (headerRelId == null) headerRelId = id
                "footerReference" -> if (footerRelId == null) footerRelId = id
            }
        }
    }

    private fun readParagraph(element: XmlElement): SourceParagraph {
        val properties = element.children.firstOrNull { it.localName == "pPr" && it.isWord() }
        var alignment = ParagraphAlignment.START
        var styleId: String? = null
        var resolved = styles.resolve(null)
        var indentTwips: Int? = null
        var spacingBeforeTwips: Int? = null
        var spacingAfterTwips: Int? = null
        var lineSpacingPercent: Int? = null
        var lineSpacingSpecified = false
        var pageBreakBefore = false
        var list: ListInfo? = null
        var pendingNumId: Int? = null
        var pendingLevel = 0

        properties?.children?.forEach { child ->
            when (child.localName) {
                "pStyle" -> {
                    styleId = child.attr("val")
                    resolved = styles.resolve(styleId)
                }
                "numId" -> pendingNumId = child.attr("val")?.toIntOrNull()
                "ilvl" -> pendingLevel = child.attr("val")?.toIntOrNull() ?: 0
                "ind" -> {
                    indentTwips = (child.attr("left") ?: child.attr("start"))?.toIntOrNull()
                }
                "jc" -> alignment = alignmentValue(child)
                "pageBreakBefore" -> pageBreakBefore = DocxSourceReader.onOffValue(child)
                "spacing" -> {
                    child.attr("before")?.toIntOrNull()?.let { spacingBeforeTwips = it }
                    child.attr("after")?.toIntOrNull()?.let { spacingAfterTwips = it }
                    if (child.attr("line") != null) {
                        lineSpacingSpecified = true
                        lineSpacingPercent = DocxSourceReader.lineSpacingPercent(child)
                    }
                }
            }
        }
        val numPr = properties?.child("numPr")
        numPr?.children?.forEach { child ->
            when (child.localName) {
                "numId" -> pendingNumId = child.attr("val")?.toIntOrNull()
                "ilvl" -> pendingLevel = child.attr("val")?.toIntOrNull() ?: 0
            }
        }
        if (numPr != null) {
            val numId = pendingNumId
            list = if (numId == null || numId == 0) null else numbering[numId to pendingLevel]
        }

        val runs = mutableListOf<SourceRun>()
        val images = mutableListOf<SourceImage>()
        var pageBreakAfterRun = false
        collectRuns(element, resolved, runs, images) { pageBreakAfterRun = true }

        val modelRuns = runs.map { it.model }
        val paragraph = Paragraph(
            runs = modelRuns,
            alignment = alignment,
            styleId = styleId,
            list = list,
            indentTwips = indentTwips,
            images = images.filter { it.bytes != null }.map { image ->
                InlineImage(image.bytes!!, image.widthDp, image.heightDp)
            },
            spacingBeforeTwips = spacingBeforeTwips ?: resolved.paragraph.spacingBeforeTwips,
            spacingAfterTwips = spacingAfterTwips ?: resolved.paragraph.spacingAfterTwips,
            lineSpacingPercent = if (lineSpacingSpecified) {
                lineSpacingPercent
            } else {
                resolved.paragraph.lineSpacingPercent
            },
            pageBreakBefore = pageBreakBefore || pendingPageBreak,
        )
        pendingPageBreak = pageBreakAfterRun
        return SourceParagraph(element, properties, runs, images, paragraph, xml)
    }

    /**
     * Collects the runs of a paragraph.
     *
     * Runs inside a text box are left alone: they belong to a shape, not to the
     * paragraph's flow, and the editor cannot place the caret inside one. The
     * text box itself is preserved untouched when the document is saved.
     */
    private fun collectRuns(
        element: XmlElement,
        resolved: DocxSourceReader.ResolvedStyle,
        runs: MutableList<SourceRun>,
        images: MutableList<SourceImage>,
        onPageBreak: () -> Unit,
    ) {
        element.children.forEach { child ->
            when {
                child.localName == "r" && child.isWord() -> {
                    readRun(child, resolved, runs, images, onPageBreak)
                }
                child.localName == "drawing" || child.localName == "pict" -> {
                    readImage(child, images)
                }
                child.localName == "txbxContent" -> Unit
                else -> collectRuns(child, resolved, runs, images, onPageBreak)
            }
        }
    }

    private fun readRun(
        element: XmlElement,
        resolved: DocxSourceReader.ResolvedStyle,
        runs: MutableList<SourceRun>,
        images: MutableList<SourceImage>,
        onPageBreak: () -> Unit,
    ) {
        val contentNodes = mutableListOf<XmlElement>()
        val text = StringBuilder()
        var hasContent = false
        element.children.forEach { child ->
            when {
                child.localName == "t" && child.isWord() -> {
                    val value = child.text
                    if (value.isNotEmpty()) {
                        text.append(value)
                        hasContent = true
                        contentNodes += child
                    }
                }
                child.localName == "tab" && child.isWord() -> {
                    text.append('\t')
                    hasContent = true
                    contentNodes += child
                }
                child.localName == "br" || child.localName == "cr" ->
                    if (child.isWord()) {
                        if (child.localName == "br" && child.attr("type") == "page") {
                            // A page break belongs to the next paragraph; it is
                            // not part of this run's text.
                            onPageBreak()
                        } else {
                            text.append('\n')
                            hasContent = true
                            contentNodes += child
                        }
                    }
                child.localName == "drawing" || child.localName == "pict" -> {
                    readImage(child, images)
                }
                child.localName == "txbxContent" -> Unit
                else -> Unit
            }
        }
        if (!hasContent) return
        val properties = element.child("rPr")
        val props = properties?.let { parseRunProps(it) }
        val model = Run(
            text = text.toString(),
            bold = props?.bold ?: resolved.run.bold ?: false,
            italic = props?.italic ?: resolved.run.italic ?: false,
            underline = props?.underline ?: resolved.run.underline ?: false,
            strike = props?.strike ?: resolved.run.strike ?: false,
            sizePt = props?.sizePt ?: resolved.run.sizePt,
            colorRgb = props?.colorRgb ?: resolved.run.colorRgb,
        )
        runs += SourceRun(element, contentNodes, model)
    }

    private fun readImage(element: XmlElement, images: MutableList<SourceImage>) {
        var relId: String? = null
        var widthDp: Float? = null
        var heightDp: Float? = null
        element.descendants().forEach { descendant ->
            when (descendant.localName) {
                "blip" -> if (relId == null) {
                    relId = descendant.attr(RELATIONSHIPS_NAMESPACE, "embed")
                        ?: descendant.attr(null, "embed")
                }
                "imagedata" -> if (relId == null) {
                    relId = descendant.attr(RELATIONSHIPS_NAMESPACE, "id")
                        ?: descendant.attr(null, "id")
                }
                "extent" -> if (widthDp == null) {
                    widthDp = descendant.attr(null, "cx")?.toFloatOrNull()
                        ?.div(DocxSourceReader.EMU_PER_DP)
                    heightDp = descendant.attr(null, "cy")?.toFloatOrNull()
                        ?.div(DocxSourceReader.EMU_PER_DP)
                }
            }
        }
        val bytes = relId?.let { id -> rels[id] }?.let { relationship ->
            if (relationship.external) null else parts[resolvePartPath(relationship.target)]
        }
        images += SourceImage(element, relId, widthDp, heightDp, bytes)
    }

    private fun readTable(element: XmlElement): SourceTable {
        val properties = element.child("tblPr")
        val tableStyleId = properties?.child("tblStyle")?.attr("val")
        val borders = properties?.child("tblBorders")?.let { DocxSourceReader.bordersDrawn(it) } ?: false
        val gridColumns = element.child("tblGrid")?.childrenNamed("gridCol")?.size ?: 0
        val rows = element.childrenNamed("tr").map { readRow(it) }
        val columnCount = if (gridColumns == 0) {
            rows.maxOfOrNull { row -> row.model.cells.sumOf { it.columnSpan } } ?: 0
        } else {
            gridColumns
        }
        val styleBorders = tableStyleId?.let { styles.tableBorders[it] } == true
        val cellBorders = rows.any { row -> row.cells.any { cell -> cell.drawsBorders } }
        val model = Table(
            rows = rows.map { it.model },
            columnCount = columnCount,
            borders = borders || styleBorders || cellBorders,
        )
        return SourceTable(element, rows, model, xml)
    }

    private fun readRow(element: XmlElement): SourceRow {
        val header = element.child("trPr")?.child("tblHeader")?.let { DocxSourceReader.onOffValue(it) } ?: false
        val cells = element.childrenNamed("tc").map { readCell(it) }
        return SourceRow(element, cells, TableRow(cells.map { it.model }, header), xml)
    }

    private fun readCell(element: XmlElement): SourceCell {
        val properties = element.child("tcPr")
        val columnSpan = (properties?.child("gridSpan")?.attr("val")?.toIntOrNull() ?: 1)
            .coerceAtLeast(1)
        val verticalMerge = properties?.child("vMerge")?.let { merge ->
            if (merge.attr("val")?.lowercase() == "restart") {
                VerticalMerge.RESTART
            } else {
                VerticalMerge.CONTINUE
            }
        } ?: VerticalMerge.NONE
        val blocks = mutableListOf<SourceBlock>()
        element.children.forEach { child ->
            when {
                child.localName == "p" && child.isWord() -> blocks += readParagraph(child)
                child.localName == "tbl" && child.isWord() -> blocks += readTable(child)
            }
        }
        val cellBordersDrawn = properties?.child("tcBorders")?.let { DocxSourceReader.bordersDrawn(it) } ?: false
        val model = TableCell(
            blocks = blocks.map { it.modelBlock },
            columnSpan = columnSpan,
            verticalMerge = verticalMerge,
        )
        return SourceCell(element, blocks, model, cellBordersDrawn, xml)
    }

    private fun parseRunProps(element: XmlElement): DocxSourceReader.RunProps {
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var strike: Boolean? = null
        var sizePt: Float? = null
        var colorRgb: Int? = null
        element.children.forEach { child ->
            when (child.localName) {
                "b" -> bold = DocxSourceReader.onOffValue(child)
                "i" -> italic = DocxSourceReader.onOffValue(child)
                "u" -> underline = DocxSourceReader.underlineValue(child)
                "strike" -> strike = DocxSourceReader.onOffValue(child)
                "sz" -> sizePt = child.attr("val")?.toFloatOrNull()?.div(2f)
                "color" -> colorRgb = DocxSourceReader.colorValue(child)
            }
        }
        return DocxSourceReader.RunProps(bold, italic, underline, strike, sizePt, colorRgb)
    }

    private fun alignmentValue(element: XmlElement): ParagraphAlignment =
        when (element.attr("val")?.lowercase()) {
            "center" -> ParagraphAlignment.CENTER
            "right", "end" -> ParagraphAlignment.END
            "both", "justify", "distribute" -> ParagraphAlignment.JUSTIFY
            else -> ParagraphAlignment.START
        }
}

/** A relationship from a package part to another part or an external target. */
internal data class Relationship(
    val id: String,
    val type: String,
    val target: String,
    val external: Boolean,
)

/** Reads a `.rels` part into its relationships. */
internal fun parseRelationships(bytes: ByteArray): Map<String, Relationship> {
    val tree = XmlScanner.parse(bytes.decodeToString())
    val result = mutableMapOf<String, Relationship>()
    tree.root.childrenNamed("Relationship").forEach { element ->
        val id = element.attr("Id") ?: return@forEach
        val type = element.attr("Type") ?: return@forEach
        val target = element.attr("Target") ?: return@forEach
        result[id] = Relationship(
            id = id,
            type = type,
            target = target,
            external = element.attr("TargetMode") == "External",
        )
    }
    return result
}

/** Turns a relationship target into a package entry name. */
internal fun resolvePartPath(target: String): String {
    val raw = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
    val segments = mutableListOf<String>()
    raw.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
    }
    return segments.joinToString("/")
}

/** The relationship part that belongs to a package part. */
internal fun relsNameOf(partName: String): String =
    partName.substringBeforeLast('/') + "/_rels/" + partName.substringAfterLast('/') + ".rels"

/** The relationship namespace. */
internal const val RELATIONSHIPS_NAMESPACE =
    "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

/** True when the element is in the WordprocessingML namespace. */
internal fun XmlElement.isWord(): Boolean = namespaceUri == WORDPROCESSINGML_NAMESPACE

/** `word/header1.xml`, `word/footer2.xml`, … but not their `_rels` parts. */
internal fun isHeaderOrFooterPart(name: String): Boolean =
    (name.startsWith("word/header") || name.startsWith("word/footer")) &&
        name.endsWith(".xml") &&
        !name.contains("_rels/")

/** `word/_rels/header1.xml.rels`, … — the relationship part of a header or footer. */
internal fun isHeaderOrFooterRels(name: String): Boolean =
    name.startsWith("word/_rels/") &&
        (name.contains("header") || name.contains("footer")) &&
        name.endsWith(".rels")
