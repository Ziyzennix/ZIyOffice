package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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

/** The WordprocessingML namespace, as defined by ECMA-376 / ISO 29500. */
const val WORDPROCESSINGML_NAMESPACE =
    "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

/**
 * Reads a .docx package into the ZiyOffice document model using only platform
 * APIs: `java.util.zip` for the container and an XML pull parser (`org.xmlpull`,
 * present on Android since API 1) for the parts. No Apache POI, no xmlbeans.
 *
 * Supported subset (grows with each milestone):
 *  - document.xml: paragraphs, runs, tabs and breaks, alignment, indentation,
 *    list membership (`w:numPr`), tables (`w:tbl` with grid, grid spans,
 *    repeating header rows, nested tables)
 *  - styles.xml: `w:docDefaults` plus paragraph styles with their `w:basedOn`
 *    chains, resolved onto each run (run-level formatting always wins);
 *    table styles are inspected for borders
 *  - numbering.xml: `num` → `abstractNum` → level number formats and starts
 *  - header/footer parts: the default variant of each section, including their
 *    own images. First-page and even-page variants are not modelled.
 *
 * Unsupported content is skipped, never faked. Known gaps (tracked in the plan):
 * vertical cell merges, numbering inherited from styles, real tab stops.
 *
 * [newParser] exists so tests can supply the JVM-side parser; production code
 * uses the platform factory.
 */
class DocxReader(
    private val newParser: () -> XmlPullParser = {
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    },
) {

    fun read(input: InputStream): Document {
        val parts = readParts(input)
        val documentBytes = parts[DOCUMENT_PART]
            ?: throw IOException("$DOCUMENT_PART not found in the package")
        val styles = parts[STYLES_PART]?.let { parseStyles(it) } ?: StyleSheet.EMPTY
        val numbering = parts[NUMBERING_PART]?.let { parseNumbering(it) } ?: emptyMap()
        val images = loadImages(parts, RELS_PART)
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(documentBytes), null)
        val bodyParser = DocumentParser(parser, styles, numbering, images)
        val document = bodyParser.parseBody()
        val targets = loadRelationshipTargets(parts, RELS_PART)

        // A section's header/footer references are relationship ids, so the
        // parts are only reachable through document.xml.rels.
        fun part(relId: String?): List<Block>? {
            val target = relId?.let { targets[it] } ?: return null
            val name = resolvePartPath(target)
            val bytes = parts[name] ?: return null
            val relsPath = "word/_rels/${name.substringAfterLast('/')}.rels"
            val partImages = loadImages(parts, relsPath)
            val partParser = newParser()
            partParser.setInput(ByteArrayInputStream(bytes), null)
            return DocumentParser(partParser, styles, numbering, partImages).parseBody().blocks
        }

        return document.copy(
            header = part(bodyParser.headerRelId),
            footer = part(bodyParser.footerRelId),
        )
    }

    private fun readParts(input: InputStream): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in WANTED_PARTS ||
                    entry.name.startsWith(MEDIA_PREFIX) ||
                    // Header/footer parts and their rels are read by name: the
                    // relationship target names the file, not a fixed constant.
                    isHeaderOrFooterPart(entry.name) ||
                    isHeaderOrFooterRels(entry.name)
                ) {
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
        }
        return parts
    }

    /**
     * Resolves image relationship ids to their media part bytes, so a part's
     * parser can embed pictures without touching the zip again. External targets
     * (remote URLs) are never fetched — this app is offline by design.
     *
     * [relsPart] is the relationship part of the content being parsed: the body
     * uses `document.xml.rels`, a header uses `header1.xml.rels`, and so on.
     */
    private fun loadImages(parts: Map<String, ByteArray>, relsPart: String): Map<String, ByteArray> {
        val relsBytes = parts[relsPart] ?: return emptyMap()
        val images = mutableMapOf<String, ByteArray>()
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(relsBytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val type = parser.getAttributeValue(null, "Type")
                val targetMode = parser.getAttributeValue(null, "TargetMode")
                if (type != null && type.endsWith("/image") && targetMode != "External") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    val bytes = if (target != null) parts[resolvePartPath(target)] else null
                    if (id != null && bytes != null) images[id] = bytes
                }
            }
            event = parser.next()
        }
        return images
    }

    /** Every relationship id in [relsPart] mapped to its target path. */
    private fun loadRelationshipTargets(
        parts: Map<String, ByteArray>,
        relsPart: String,
    ): Map<String, String> {
        val relsBytes = parts[relsPart] ?: return emptyMap()
        val targets = mutableMapOf<String, String>()
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(relsBytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                if (parser.getAttributeValue(null, "TargetMode") != "External") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (id != null && target != null) targets[id] = target
                }
            }
            event = parser.next()
        }
        return targets
    }

    /** Turns a relationship target into a zip entry name (relative to /word). */
    private fun resolvePartPath(target: String): String {
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

    // ---------------------------------------------------------------- document

    /**
     * Recursive-descent body parser. Each `parse*` method is entered with the
     * parser on the element's start tag, consumes events through its matching
     * end tag, and returns the parsed model object — which keeps table nesting
     * and interleaved content straightforward.
     */
    private inner class DocumentParser(
        private val parser: XmlPullParser,
        private val styles: StyleSheet,
        private val numbering: Map<Pair<Int, Int>, ListInfo>,
        private val images: Map<String, ByteArray>,
    ) {

        /**
         * `w:br w:type="page"` belongs to the paragraph it sits in, but its
         * meaning is "the next paragraph starts a new page" — so it is carried
         * over to the next paragraph as `pageBreakBefore`.
         */
        private var pendingPageBreak = false

        /**
         * Relationship ids of the default header and footer, read from the body's
         * `w:sectPr`. Only the plain references are kept: `headerReference` and
         * `footerReference` without a `w:type` are the ones that apply to every
         * page. First-page and even-page variants are deliberately not modelled
         * and are reported as loss by ContentAudit.
         */
        var headerRelId: String? = null
            private set
        var footerRelId: String? = null
            private set

        fun parseBody(): Document {
            val blocks = mutableListOf<Block>()
            while (true) {
                val event = parser.next()
                if (event == XmlPullParser.END_DOCUMENT) break
                if (event == XmlPullParser.START_TAG && isWord()) {
                    when (parser.name) {
                        "p" -> blocks += parseParagraph()
                        "tbl" -> blocks += parseTable()
                        "sectPr" -> parseSectionProperties()
                    }
                }
            }
            return Document(blocks)
        }

        /** Records the default header/footer relationship ids of a section. */
        private fun parseSectionProperties() {
            val depth = parser.depth
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT &&
                !(event == XmlPullParser.END_TAG && parser.depth == depth)
            ) {
                if (event == XmlPullParser.START_TAG && isWord()) {
                    val type = attribute(parser, "type")
                    // Absent type means "default", the variant that applies to
                    // every page; "first"/"even" need the document's settings.
                    val isDefault = type == null || type == "default"
                    if (isDefault) {
                        val id = parser.getAttributeValue(RELATIONSHIPS_NAMESPACE, "id")
                            ?: parser.getAttributeValue(null, "id")
                        when (parser.name) {
                            "headerReference" -> if (headerRelId == null) headerRelId = id
                            "footerReference" -> if (footerRelId == null) footerRelId = id
                        }
                    }
                }
                event = parser.next()
            }
        }

        private fun parseParagraph(): Paragraph {
            val depth = parser.depth
            var alignment = ParagraphAlignment.START
            var styleId: String? = null
            var resolved = styles.resolve(null)
            var indentTwips: Int? = null
            var spacingBeforeTwips: Int? = null
            var spacingAfterTwips: Int? = null
            var lineSpacingPercent: Int? = null
            /** True when the paragraph itself sets `w:line`, even by a rule we do not model. */
            var lineSpacingSpecified = false
            var pageBreakBefore = false
            var pageBreakAfterRun = false
            var list: ListInfo? = null
            var pendingNumId: Int? = null
            var pendingLevel = 0
            val runs = mutableListOf<Run>()
            val inlineImages = mutableListOf<InlineImage>()
            var pendingImageRelId: String? = null
            var pendingImageWidth: Float? = null
            var pendingImageHeight: Float? = null

            var inParagraphProperties = false
            var inRun = false
            var inRunProperties = false
            var inText = false
            var runHasContent = false
            var runText = StringBuilder()
            var runBold: Boolean? = null
            var runItalic: Boolean? = null
            var runUnderline: Boolean? = null
            var runStrike: Boolean? = null
            var runSizePt: Float? = null
            var runColorRgb: Int? = null

            fun resetRun() {
                runHasContent = false
                runText = StringBuilder()
                runBold = null
                runItalic = null
                runUnderline = null
                runStrike = null
                runSizePt = null
                runColorRgb = null
            }

            fun flushRun() {
                if (inRun && runHasContent) {
                    val styleProps = resolved.run
                    runs += Run(
                        text = runText.toString(),
                        bold = runBold ?: styleProps.bold ?: false,
                        italic = runItalic ?: styleProps.italic ?: false,
                        underline = runUnderline ?: styleProps.underline ?: false,
                        strike = runStrike ?: styleProps.strike ?: false,
                        sizePt = runSizePt ?: styleProps.sizePt,
                        colorRgb = runColorRgb ?: styleProps.colorRgb,
                    )
                }
                inRun = false
                resetRun()
            }

            fun flushImage() {
                val relId = pendingImageRelId
                val bytes = if (relId != null) images[relId] else null
                if (bytes != null) {
                    inlineImages += InlineImage(bytes, pendingImageWidth, pendingImageHeight)
                }
                pendingImageRelId = null
                pendingImageWidth = null
                pendingImageHeight = null
            }

            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT &&
                !(event == XmlPullParser.END_TAG && parser.depth == depth)
            ) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val isWord = isWord()
                        when {
                            isWord && parser.name == "r" -> {
                                inRun = true
                                resetRun()
                            }
                            isWord && inRun && parser.name == "rPr" -> inRunProperties = true
                            isWord && inRun && inRunProperties -> when (parser.name) {
                                "b" -> runBold = onOffValue(parser)
                                "i" -> runItalic = onOffValue(parser)
                                "u" -> runUnderline = underlineValue(parser)
                                "strike" -> runStrike = onOffValue(parser)
                                "sz" -> runSizePt = attribute(parser, "val")?.toFloatOrNull()?.div(2f)
                                "color" -> runColorRgb = colorValue(parser)
                            }
                            isWord && !inRun && parser.name == "pPr" -> inParagraphProperties = true
                            isWord && inParagraphProperties -> when (parser.name) {
                                "pStyle" -> {
                                    styleId = attribute(parser, "val")
                                    resolved = styles.resolve(styleId)
                                }
                                "numId" -> pendingNumId = attribute(parser, "val")?.toIntOrNull()
                                "ilvl" -> pendingLevel = attribute(parser, "val")?.toIntOrNull() ?: 0
                                "ind" -> {
                                    val value = attribute(parser, "left") ?: attribute(parser, "start")
                                    indentTwips = value?.toIntOrNull()
                                }
                                "jc" -> alignment = alignmentValue(parser)
                                "pageBreakBefore" -> pageBreakBefore = onOffValue(parser)
                                "spacing" -> {
                                    attribute(parser, "before")?.toIntOrNull()
                                        ?.let { spacingBeforeTwips = it }
                                    attribute(parser, "after")?.toIntOrNull()
                                        ?.let { spacingAfterTwips = it }
                                    if (attribute(parser, "line") != null) {
                                        lineSpacingSpecified = true
                                        lineSpacingPercent = lineSpacingPercent(parser)
                                    }
                                }
                            }
                            // Picture markers below live in DrawingML / VML namespaces,
                            // not WordprocessingML, so they match by name only.
                            inRun && parser.name == "blip" -> pendingImageRelId =
                                parser.getAttributeValue(RELATIONSHIPS_NAMESPACE, "embed")
                                    ?: pendingImageRelId
                            inRun && parser.name == "imagedata" -> pendingImageRelId =
                                parser.getAttributeValue(RELATIONSHIPS_NAMESPACE, "id")
                                    ?: pendingImageRelId
                            inRun && parser.name == "extent" -> {
                                pendingImageWidth =
                                    parser.getAttributeValue(null, "cx")?.toFloatOrNull()
                                        ?.div(EMU_PER_DP)
                                pendingImageHeight =
                                    parser.getAttributeValue(null, "cy")?.toFloatOrNull()
                                        ?.div(EMU_PER_DP)
                            }
                            isWord && inRun && parser.name == "t" -> inText = true
                            isWord && inRun && parser.name == "tab" -> {
                                runText.append('\t')
                                runHasContent = true
                            }
                            isWord && inRun && (parser.name == "br" || parser.name == "cr") -> {
                                if (parser.name == "br" && attribute(parser, "type") == "page") {
                                    pageBreakAfterRun = true
                                } else {
                                    runText.append('\n')
                                    runHasContent = true
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (inText) {
                        runText.append(parser.text)
                        runHasContent = true
                    }
                    XmlPullParser.END_TAG -> if (isWord()) {
                        when {
                            inRun && parser.name == "t" -> inText = false
                            inRun && parser.name == "rPr" -> inRunProperties = false
                            inRun && (parser.name == "drawing" || parser.name == "pict") ->
                                flushImage()
                            inRun && parser.name == "r" -> flushRun()
                            inParagraphProperties && parser.name == "numPr" -> {
                                val numId = pendingNumId
                                list = if (numId == null || numId == 0) null
                                else numbering[numId to pendingLevel]
                            }
                            parser.name == "pPr" -> inParagraphProperties = false
                        }
                    }
                }
                event = parser.next()
            }
            flushRun()
            val paragraph = Paragraph(
                runs = runs.toList(),
                alignment = alignment,
                styleId = styleId,
                list = list,
                indentTwips = indentTwips,
                images = inlineImages,
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
            return paragraph
        }

        private fun parseTable(): Table {
            val depth = parser.depth
            var columnCount = 0
            var borders = false
            var tableStyleId: String? = null
            val rows = mutableListOf<TableRow>()
            val cellBorders = BorderSink()

            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT &&
                !(event == XmlPullParser.END_TAG && parser.depth == depth)
            ) {
                if (event == XmlPullParser.START_TAG && isWord()) {
                    when (parser.name) {
                        "tblStyle" -> tableStyleId = attribute(parser, "val")
                        "tblBorders" -> if (parseBorders()) borders = true
                        "gridCol" -> columnCount++
                        "tr" -> rows += parseTableRow(cellBorders)
                    }
                }
                event = parser.next()
            }
            if (columnCount == 0) {
                columnCount = rows.maxOfOrNull { row -> row.cells.sumOf { it.columnSpan } } ?: 0
            }
            val styleBorders = tableStyleId?.let { styles.tableBorders[it] } == true
            return Table(rows, columnCount, borders || styleBorders || cellBorders.drawn)
        }

        /** Reads a `tblBorders`/`tcBorders` element; true when any edge is drawn. */
        private fun parseBorders(): Boolean {
            val depth = parser.depth
            var drawn = false
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT &&
                !(event == XmlPullParser.END_TAG && parser.depth == depth)
            ) {
                if (event == XmlPullParser.START_TAG && isWord()) {
                    val value = attribute(parser, "val")?.lowercase() ?: "single"
                    if (value != "none" && value != "nil") drawn = true
                }
                event = parser.next()
            }
            return drawn
        }

        private fun parseTableRow(cellBorders: BorderSink): TableRow {
            val depth = parser.depth
            var header = false
            val cells = mutableListOf<TableCell>()

            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT &&
                !(event == XmlPullParser.END_TAG && parser.depth == depth)
            ) {
                if (event == XmlPullParser.START_TAG && isWord()) {
                    when (parser.name) {
                        "tblHeader" -> header = onOffValue(parser)
                        "tc" -> cells += parseTableCell(cellBorders)
                    }
                }
                event = parser.next()
            }
            return TableRow(cells, header)
        }

        private fun parseTableCell(cellBorders: BorderSink): TableCell {
            val depth = parser.depth
            var columnSpan = 1
            var verticalMerge = VerticalMerge.NONE
            val blocks = mutableListOf<Block>()

            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT &&
                !(event == XmlPullParser.END_TAG && parser.depth == depth)
            ) {
                if (event == XmlPullParser.START_TAG && isWord()) {
                    when (parser.name) {
                        "gridSpan" -> columnSpan =
                            (attribute(parser, "val")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                        // `w:vMerge w:val="restart"` opens a merge; a bare
                        // `w:vMerge` (or val="continue") continues the one above.
                        "vMerge" -> verticalMerge =
                            if (attribute(parser, "val")?.lowercase() == "restart") {
                                VerticalMerge.RESTART
                            } else {
                                VerticalMerge.CONTINUE
                            }
                        "tcBorders" -> if (parseBorders()) cellBorders.drawn = true
                        "p" -> blocks += parseParagraph()
                        "tbl" -> blocks += parseTable()
                    }
                }
                event = parser.next()
            }
            return TableCell(blocks, columnSpan, verticalMerge)
        }

        private fun isWord(): Boolean = parser.namespace == WORDPROCESSINGML_NAMESPACE
    }

    // ------------------------------------------------------------------ styles

    /**
     * Paragraph styles resolved over `w:docDefaults`. Style chains are followed
     * through `w:basedOn` (cycle-safe), ancestors first, so the leaf style wins.
     * Table styles are only inspected for borders.
     */
    private class StyleSheet(
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

    private class ResolvedStyle(val run: RunProps, val paragraph: ParagraphProps)

    private class ParagraphStyle(
        val id: String,
        val basedOn: String?,
        val run: RunProps,
        val paragraph: ParagraphProps,
        val isDefault: Boolean,
    )

    /** Paragraph properties where null means "not specified here". */
    private class ParagraphProps(
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

    /** Mutable flag threaded through a table's cells while parsing. */
    private class BorderSink(var drawn: Boolean = false)

    /** Run properties where null means "not specified here". */
    private class RunProps(
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
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var docDefaultsRun = RunProps()
        var docDefaultsParagraph = ParagraphProps()
        val styles = mutableMapOf<String, ParagraphStyle>()
        val tableBorders = mutableMapOf<String, Boolean>()
        var defaultStyleId: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                when (parser.name) {
                    // The first rPr / pPr in styles.xml belong to docDefaults (schema order).
                    "rPr" -> docDefaultsRun = parseRunProps(parser)
                    "pPr" -> docDefaultsParagraph = parseParagraphProps(parser)
                    "style" -> when (attribute(parser, "type")) {
                        null, "paragraph" -> parseStyle(parser)?.let { style ->
                            styles[style.id] = style
                            if (style.isDefault) defaultStyleId = style.id
                        }
                        "table" -> parseTableStyle(parser)?.let { (id, borders) ->
                            tableBorders[id] = borders
                        }
                        else -> skipElement(parser, parser.depth)
                    }
                }
            }
            event = parser.next()
        }
        return StyleSheet(
            docDefaultsRun,
            docDefaultsParagraph,
            styles,
            defaultStyleId,
            tableBorders,
        )
    }

    private fun parseStyle(parser: XmlPullParser): ParagraphStyle? {
        val styleDepth = parser.depth
        val id = attribute(parser, "styleId")
        val isDefault = onOff(attribute(parser, "default"))
        var basedOn: String? = null
        var runProps = RunProps()
        var paragraphProps = ParagraphProps()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == styleDepth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                when (parser.name) {
                    "basedOn" -> basedOn = attribute(parser, "val")
                    // Only the style's own rPr / pPr (not a paragraph-mark rPr inside pPr).
                    "rPr" -> if (parser.depth == styleDepth + 1) runProps = parseRunProps(parser)
                    "pPr" -> if (parser.depth == styleDepth + 1) {
                        paragraphProps = parseParagraphProps(parser)
                    }
                }
            }
            event = parser.next()
        }
        return id?.let { ParagraphStyle(it, basedOn, runProps, paragraphProps, isDefault) }
    }

    /** Reads a `w:pPr` (or style-level paragraph properties) for spacing values. */
    private fun parseParagraphProps(parser: XmlPullParser): ParagraphProps {
        val depth = parser.depth
        var before: Int? = null
        var after: Int? = null
        var line: Int? = null

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == depth)
        ) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == WORDPROCESSINGML_NAMESPACE &&
                parser.name == "spacing"
            ) {
                attribute(parser, "before")?.toIntOrNull()?.let { before = it }
                attribute(parser, "after")?.toIntOrNull()?.let { after = it }
                line = lineSpacingPercent(parser) ?: line
            }
            event = parser.next()
        }
        return ParagraphProps(before, after, line)
    }

    /**
     * `w:line` with the automatic rule is a line-height multiplier in 240ths of a
     * line; exact and at-least rules are absolute twips and are not read here.
     */
    private fun lineSpacingPercent(parser: XmlPullParser): Int? {
        val rule = attribute(parser, "lineRule") ?: "auto"
        if (rule != "auto") return null
        val line = attribute(parser, "line")?.toIntOrNull() ?: return null
        if (line <= 0) return null
        return (line * 100 + 120) / 240
    }

    private fun parseTableStyle(parser: XmlPullParser): Pair<String, Boolean>? {
        val styleDepth = parser.depth
        val id = attribute(parser, "styleId")
        var borders = false

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == styleDepth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                if (parser.name == "tblBorders" && parseBordersInto(parser)) borders = true
            }
            event = parser.next()
        }
        return id?.let { it to borders }
    }

    /** Same as a document's border scan, but as an outer-class helper for styles. */
    private fun parseBordersInto(parser: XmlPullParser): Boolean {
        val depth = parser.depth
        var drawn = false
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == depth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                val value = attribute(parser, "val")?.lowercase() ?: "single"
                if (value != "none" && value != "nil") drawn = true
            }
            event = parser.next()
        }
        return drawn
    }

    private fun parseRunProps(parser: XmlPullParser): RunProps {
        val rprDepth = parser.depth
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var strike: Boolean? = null
        var sizePt: Float? = null
        var colorRgb: Int? = null
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == rprDepth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                when (parser.name) {
                    "b" -> bold = onOffValue(parser)
                    "i" -> italic = onOffValue(parser)
                    "u" -> underline = underlineValue(parser)
                    "strike" -> strike = onOffValue(parser)
                    "sz" -> sizePt = attribute(parser, "val")?.toFloatOrNull()?.div(2f)
                    "color" -> colorRgb = colorValue(parser)
                }
            }
            event = parser.next()
        }
        return RunProps(bold, italic, underline, strike, sizePt, colorRgb)
    }

    private fun skipElement(parser: XmlPullParser, startDepth: Int) {
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == startDepth)
        ) {
            event = parser.next()
        }
    }

    // --------------------------------------------------------------- numbering

    private fun parseNumbering(bytes: ByteArray): Map<Pair<Int, Int>, ListInfo> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val abstractLevels = mutableMapOf<Int, Map<Int, Pair<ListKind, Int>>>()
        val nums = mutableMapOf<Int, Int>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                when (parser.name) {
                    "abstractNum" -> parseAbstractNum(parser)?.let { (id, levels) ->
                        abstractLevels[id] = levels
                    }
                    "num" -> parseNum(parser)?.let { (numId, abstractId) ->
                        nums[numId] = abstractId
                    }
                }
            }
            event = parser.next()
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

    private fun parseAbstractNum(parser: XmlPullParser): Pair<Int, Map<Int, Pair<ListKind, Int>>>? {
        val depth = parser.depth
        val id = attribute(parser, "abstractNumId")?.toIntOrNull() ?: return null
        val levels = mutableMapOf<Int, Pair<ListKind, Int>>()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == depth)
        ) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == WORDPROCESSINGML_NAMESPACE &&
                parser.name == "lvl"
            ) {
                parseLevel(parser)?.let { (ilvl, kind, start) -> levels[ilvl] = kind to start }
            }
            event = parser.next()
        }
        return id to levels
    }

    private fun parseLevel(parser: XmlPullParser): Triple<Int, ListKind, Int>? {
        val depth = parser.depth
        val ilvl = attribute(parser, "ilvl")?.toIntOrNull() ?: 0
        var kind = ListKind.OTHER
        var start = 1

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == depth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.namespace == WORDPROCESSINGML_NAMESPACE) {
                when (parser.name) {
                    "numFmt" -> kind = listKind(attribute(parser, "val"))
                    "start" -> start = attribute(parser, "val")?.toIntOrNull() ?: 1
                }
            }
            event = parser.next()
        }
        return Triple(ilvl, kind, start)
    }

    private fun parseNum(parser: XmlPullParser): Pair<Int, Int>? {
        val depth = parser.depth
        val numId = attribute(parser, "numId")?.toIntOrNull() ?: return null
        var abstractId: Int? = null

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == depth)
        ) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == WORDPROCESSINGML_NAMESPACE &&
                parser.name == "abstractNumId"
            ) {
                abstractId = attribute(parser, "val")?.toIntOrNull()
            }
            event = parser.next()
        }
        val abstract = abstractId ?: return null
        return numId to abstract
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

    // ------------------------------------------------------------------ shared

    private fun attribute(parser: XmlPullParser, name: String): String? =
        parser.getAttributeValue(WORDPROCESSINGML_NAMESPACE, name)
            ?: parser.getAttributeValue(null, name)

    /** OOXML on/off element semantics: absent value attribute means on. */
    private fun onOffValue(parser: XmlPullParser): Boolean =
        attribute(parser, "val")?.lowercase()?.let { it !in OFF_VALUES } ?: true

    /** Attribute-value semantics (w:default="1"): absent or off means false. */
    private fun onOff(value: String?): Boolean =
        value?.lowercase()?.let { it !in OFF_VALUES } ?: false

    private fun underlineValue(parser: XmlPullParser): Boolean =
        attribute(parser, "val")?.lowercase()?.let { it !in OFF_VALUES && it != "none" } ?: true

    private fun colorValue(parser: XmlPullParser): Int? {
        val value = attribute(parser, "val")?.removePrefix("#") ?: return null
        if (value.equals("auto", ignoreCase = true)) return null
        return value.toIntOrNull(16)
    }

    private fun alignmentValue(parser: XmlPullParser): ParagraphAlignment =
        when (attribute(parser, "val")?.lowercase()) {
            "center" -> ParagraphAlignment.CENTER
            "right", "end" -> ParagraphAlignment.END
            "both", "justify", "distribute" -> ParagraphAlignment.JUSTIFY
            else -> ParagraphAlignment.START
        }

    private companion object {
        const val DOCUMENT_PART = "word/document.xml"
        const val STYLES_PART = "word/styles.xml"
        const val NUMBERING_PART = "word/numbering.xml"
        const val RELS_PART = "word/_rels/document.xml.rels"
        const val MEDIA_PREFIX = "word/media/"
        const val RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val WANTED_PARTS = setOf(DOCUMENT_PART, STYLES_PART, NUMBERING_PART, RELS_PART)
        const val MAX_PART_BYTES = 64 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 128 * 1024 * 1024
        const val EMU_PER_DP = 9525f
        val OFF_VALUES = setOf("0", "false", "off")
    }
}

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
