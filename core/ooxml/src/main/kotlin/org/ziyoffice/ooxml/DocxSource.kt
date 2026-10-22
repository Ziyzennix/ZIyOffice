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
 * A `.docx` package opened for editing, plus a snapshot of everything the
 * editor is allowed to change.
 *
 * The old writer rebuilt a document from the model alone, so a save dropped
 * comments, footnotes, tracked changes, hyperlinks, equations, text boxes,
 * section breaks, styles and document properties. [DocxSource] keeps the
 * original package instead: the model is the editable view of it, the
 * snapshot says which bytes each part came from, and [DocxEditor] writes the
 * edited model back in, changing only what the user touched.
 */
class DocxSource internal constructor(
    val parts: Map<String, ByteArray>,
    val document: Document,
    internal val body: SourceBody,
    internal val header: SourceBody?,
    internal val footer: SourceBody?,
    internal val stylesPart: String?,
    internal val numberingPart: String?,
    internal val documentRelsPart: String?,
    internal val contentTypesPart: String?,
) {

    companion object {
        fun read(input: InputStream): DocxSource = DocxSourceReader().read(input)

        fun capture(bytes: ByteArray): DocxSource = read(bytes.inputStream())
    }
}

/** One part of a document (a body, a header or a footer) and its source elements. */
internal class SourceBody internal constructor(
    val partName: String,
    val relsPartName: String,
    val container: XmlElement,
    val xml: String,
    val blocks: List<SourceBlock>,
)

/** A block of a document that the model can also represent. */
internal sealed interface SourceBlock {
    val element: XmlElement

    val modelBlock: Block
}

/**
 * A source paragraph and the model paragraph it produced.
 *
 * [model] is what the editor compares against: when the model paragraph still
 * equals it, the paragraph is written back byte for byte.
 */
internal class SourceParagraph internal constructor(
    override val element: XmlElement,
    val properties: XmlElement?,
    val runs: List<SourceRun>,
    val images: List<SourceImage>,
    val model: Paragraph,
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

internal class SourceRun internal constructor(
    val element: XmlElement,
    val contentNodes: List<XmlElement>,
    val model: Run,
)

internal class SourceImage internal constructor(
    val element: XmlElement,
    val relId: String?,
    /** Display width in dp; null when the source gives no extent. */
    val widthDp: Float?,
    val heightDp: Float?,
    val bytes: ByteArray?,
)

internal class SourceTable internal constructor(
    override val element: XmlElement,
    val rows: List<SourceRow>,
    val model: Table,
    val xml: String,
) : SourceBlock {
    override val modelBlock: Block get() = model
}

internal class SourceRow internal constructor(
    val element: XmlElement,
    val cells: List<SourceCell>,
    val model: TableRow,
    val xml: String,
)

internal class SourceCell internal constructor(
    val element: XmlElement,
    val blocks: List<SourceBlock>,
    val model: TableCell,
    val drawsBorders: Boolean,
    val xml: String,
)

/**
 * Reads a `.docx` package into a [DocxSource]: the model plus the snapshot
 * of the elements the model came from.
 *
 * Built on [XmlScanner] rather than the platform pull parser, because the
 * writer needs to know where each element sits in the original XML. One
 * parser serves both directions, so the model and the snapshot can never
 * disagree.
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

    /**
     * Paragraph styles resolved over `w:docDefaults`. Chains are followed
     * through `w:basedOn` (cycle-safe), ancestors first, so the leaf style
     * wins. Table styles are only looked at for borders.
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

    /** Paragraph properties; null means "not specified here". */
    internal class ParagraphProps(
        val spacingBeforeTwips: Int? = null,
        val spacingAfterTwips: Int? = null,
        val lineSpacingPercent: Int? = null,
    ) {
        fun on(base: ParagraphProps): ParagraphProps = ParagraphProps(
            spacingBeforeTwips = spacingBeforeTwips ?: base.spacingBeforeTwips,
            spacingAfterTwips = spacingAfterTwips ?: base.spacingAfterTwips,
            lineSpacingPercent = lineSpacingPercent ?: base.lineSpacingPercent,
        )
    }

    /** Run properties; null means "not specified here". */
    internal class RunProps(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val strike: Boolean? = null,
        val sizePt: Float? = null,
        val colorRgb: Int? = null,
    ) {
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
         * `w:line` with the automatic rule is a line-height multiplier in
         * 240ths of a line. The exact and at-least rules are absolute twips
         * and are not read here.
         */
        fun lineSpacingPercent(spacing: XmlElement): Int? {
            val rule = spacing.attr("lineRule") ?: "auto"
            if (rule != "auto") return null
            val line = spacing.attr("line")?.toIntOrNull() ?: return null
            if (line <= 0) return null
            return (line * 100 + 120) / 240
        }

        fun bordersDrawn(element: XmlElement?): Boolean {
            if (element == null) return false
            return element.children.any { child ->
                val value = child.attr("val")?.lowercase() ?: "single"
                value != "none" && value != "nil"
            }
        }
    }
}

/** Reads the blocks of one container into the model, keeping the source elements. */
internal class BodyReader(
    private val xml: String,
    private val styles: DocxSourceReader.StyleSheet,
    private val numbering: Map<Pair<Int, Int>, ListInfo>,
    private val rels: Map<String, Relationship>,
    private val parts: Map<String, ByteArray>,
) {

    /** Relationship ids of the default header and footer, from `w:sectPr`. */
    var headerRelId: String? = null
        private set
    var footerRelId: String? = null
        private set

    /**
     * A page break inside a run belongs to the paragraph that follows it, so
     * it is carried over instead of staying with the run.
     */
    private var pendingPageBreak = false
    fun readBlocks(container: XmlElement): List<SourceBlock> {
        val blocks = mutableListOf<SourceBlock>()
        container.children.forEach { child ->
            when {
                child.localName == "p" && child.isWord() -> blocks += readParagraph(child)
                child.localName == "tbl" && child.isWord() -> blocks += readTable(child)
                // A content control wraps its content in w:sdtContent. Word
                // uses these for cover pages, TOCs and templates, so skipping
                // them would make whole documents look empty.
                child.localName == "sdt" && child.isWord() ->
                    child.child("sdtContent")?.let { blocks += readBlocks(it) }
                child.localName == "sectPr" && child.isWord() -> readSectionProperties(child)
            }
        }
        return blocks
    }

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
     * Collects the runs of a paragraph. Runs inside a text box are left alone:
     * they belong to a shape, not to the paragraph's flow, and the editor
     * cannot put a caret inside one. The text box itself is preserved
     * untouched on save.
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
                child.localName == "fldSimple" && child.isWord() -> {
                    readField(child, resolved, runs, images, onPageBreak)
                }
                child.localName == "drawing" || child.localName == "pict" -> {
                    readImage(child, images)
                }
                child.localName == "txbxContent" -> Unit
                else -> collectRuns(child, resolved, runs, images, onPageBreak)
            }
        }
    }

    /**
     * A field: an element the word processor fills in.
     *
     * The instruction says which field it is ("PAGE" for the page number),
     * and the element holds the result it last had. The result is kept as
     * the run's text, so a reader that cannot work the field out still shows
     * something sensible.
     */
    private fun readField(
        element: XmlElement,
        resolved: DocxSourceReader.ResolvedStyle,
        runs: MutableList<SourceRun>,
        images: MutableList<SourceImage>,
        onPageBreak: () -> Unit,
    ) {
        val instruction = element.attr("instr")?.trim().orEmpty()
        if (instruction.isEmpty()) return
        val name = instruction.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
        if (name.isEmpty()) return

        val contentNodes = mutableListOf<XmlElement>()
        val text = StringBuilder()
        element.descendants().forEach { descendant ->
            if (descendant.localName == "t" && descendant.isWord()) {
                text.append(descendant.text)
                contentNodes += descendant
            }
        }
        val inner = element.child("r")
        val properties = inner?.child("rPr")
        val props = properties?.let { parseRunProps(it) }
        val model = Run(
            text = text.toString(),
            bold = props?.bold ?: resolved.run.bold ?: false,
            italic = props?.italic ?: resolved.run.italic ?: false,
            underline = props?.underline ?: resolved.run.underline ?: false,
            strike = props?.strike ?: resolved.run.strike ?: false,
            sizePt = props?.sizePt ?: resolved.run.sizePt,
            colorRgb = props?.colorRgb ?: resolved.run.colorRgb,
            field = name,
        )
        runs += SourceRun(element, contentNodes, model)
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

internal fun relsNameOf(partName: String): String =
    partName.substringBeforeLast('/') + "/_rels/" + partName.substringAfterLast('/') + ".rels"

internal const val RELATIONSHIPS_NAMESPACE =
    "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

internal fun XmlElement.isWord(): Boolean = namespaceUri == WORDPROCESSINGML_NAMESPACE

/** `word/header1.xml`, `word/footer2.xml`, … but not their `_rels` parts. */
internal fun isHeaderOrFooterPart(name: String): Boolean =
    (name.startsWith("word/header") || name.startsWith("word/footer")) &&
        name.endsWith(".xml") &&
        !name.contains("_rels/")

/** `word/_rels/header1.xml.rels`, the relationship part of a header or footer. */
internal fun isHeaderOrFooterRels(name: String): Boolean =
    name.startsWith("word/_rels/") &&
        (name.contains("header") || name.contains("footer")) &&
        name.endsWith(".rels")
