package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.model.TableCell
import org.ziyoffice.model.TableRow
import org.ziyoffice.model.VerticalMerge
import org.ziyoffice.model.hasSameFormattingAs
import org.ziyoffice.ooxml.xml.XmlElement
import org.ziyoffice.ooxml.xml.XmlScanner
import org.ziyoffice.ooxml.xml.escapeXmlAttribute
import org.ziyoffice.ooxml.xml.escapeXmlText

/**
 * Writes an edited document back into the package it came from.
 *
 * The old writer rebuilt the document from the model alone, so a save threw
 * away everything the model could not hold: comments, footnotes, tracked
 * changes, hyperlinks, equations, text boxes, section breaks, styles, themes,
 * document properties.
 *
 * This one starts from the original package and only touches what changed:
 *
 *  - a paragraph nobody touched is copied through byte for byte;
 *  - an edited paragraph keeps its `w:pPr`, its run properties, its hyperlinks,
 *    comment anchors and drawings, and only the text and formatting that
 *    actually changed get rewritten;
 *  - every other part of the package is copied through untouched;
 *  - stuff the editor can't represent stays in the file, and the package keeps
 *    working because its relationships stay intact.
 *
 * When the structure changes (a paragraph added, a row inserted) the new
 * content is generated from the model and spliced into the original XML, so
 * the rest of the document keeps its own markup.
 */
class DocxEditor(private val source: DocxSource) {

    /** Saves [document] into the source package and returns the new bytes. */
    fun save(document: Document): ByteArray {
        val parts = LinkedHashMap(source.parts)
        // A package that has no relationship part yet still needs one to point
        // a new part at, so the name is fixed here rather than taken from the
        // source.
        val bodyMedia = MediaRegistry(
            parts,
            source.documentRelsPart ?: DOCUMENT_RELS_PART,
            source.contentTypesPart,
        )

        parts[source.body.partName] = editBody(source.body, document.blocks, bodyMedia)
            .encodeToByteArray()

        source.header?.let { header ->
            val blocks = document.header.orEmpty()
            if (blocks.isEmpty()) {
                // The header was removed: drop the reference so the document
                // stops showing it. The part itself stays, harmless and still
                // valid, in case something else points at it.
                parts[source.body.partName] = removeSideReference(
                    parts.getValue(source.body.partName).decodeToString(),
                    "headerReference",
                ).encodeToByteArray()
            } else {
                val media = MediaRegistry(parts, header.relsPartName, source.contentTypesPart)
                parts[header.partName] = editBody(header, blocks, media).encodeToByteArray()
            }
        }

        source.footer?.let { footer ->
            val blocks = document.footer.orEmpty()
            if (blocks.isEmpty()) {
                parts[source.body.partName] = removeSideReference(
                    parts.getValue(source.body.partName).decodeToString(),
                    "footerReference",
                ).encodeToByteArray()
            } else {
                val media = MediaRegistry(parts, footer.relsPartName, source.contentTypesPart)
                parts[footer.partName] = editBody(footer, blocks, media).encodeToByteArray()
            }
        }

        if (document.header.orEmpty().isNotEmpty() && source.header == null) {
            addSidePart(parts, document.header.orEmpty(), bodyMedia, "header", "hdr")
        }
        if (document.footer.orEmpty().isNotEmpty() && source.footer == null) {
            addSidePart(parts, document.footer.orEmpty(), bodyMedia, "footer", "ftr")
        }

        return zip(parts)
    }

    // ------------------------------------------------------------------- body

    /**
     * Rewrites one part: body, header or footer.
     *
     * Model blocks are matched to source blocks by position. A changed block
     * gets replaced, an unchanged one is left alone, added blocks are
     * generated and appended, removed ones are dropped.
     */
    private fun editBody(
        body: SourceBody,
        modelBlocks: List<Block>,
        media: MediaRegistry,
    ): String {
        val xml = body.xml
        val edits = mutableListOf<Edit>()
        val sourceBlocks = body.blocks
        val shared = minOf(modelBlocks.size, sourceBlocks.size)

        for (index in 0 until shared) {
            val model = modelBlocks[index]
            val source = sourceBlocks[index]
            when {
                model is Paragraph && source is SourceParagraph ->
                    editParagraph(source, model, media, edits)
                model is Table && source is SourceTable ->
                    editTable(source, model, media, edits)
                else -> {
                    // The block changed shape (a paragraph became a table, or
                    // the other way round): the original XML cannot be reused.
                    edits += Edit(
                        source.element.start,
                        source.element.end,
                        renderBlock(model, media),
                    )
                }
            }
        }

        if (modelBlocks.size > sourceBlocks.size) {
            val extra = modelBlocks.drop(sourceBlocks.size)
            val insertion = insertionPoint(body, sourceBlocks)
            val rendered = extra.joinToString("") { renderBlock(it, media) }
            edits += Edit(insertion, insertion, rendered)
        } else if (modelBlocks.size < sourceBlocks.size) {
            sourceBlocks.drop(modelBlocks.size).forEach { dropped ->
                edits += Edit(dropped.element.start, dropped.element.end, "")
            }
        }

        return applyEdits(xml, edits)
    }

    /**
     * Where new blocks go: before the section properties when the body has
     * them, otherwise after the last source block.
     */
    private fun insertionPoint(body: SourceBody, sourceBlocks: List<SourceBlock>): Int {
        val children = body.container.children
        val sectPr = children.lastOrNull { it.localName == "sectPr" && it.isWord() }
        if (sectPr != null) return sectPr.start
        return sourceBlocks.lastOrNull()?.element?.end ?: body.container.contentEnd
    }

    // -------------------------------------------------------------- paragraph

    private fun editParagraph(
        source: SourceParagraph,
        model: Paragraph,
        media: MediaRegistry,
        edits: MutableList<Edit>,
    ) {
        if (sameParagraph(source, model)) return
        edits += Edit(
            source.element.start,
            source.element.end,
            renderParagraph(source, model, media),
        )
    }

    /** True when the model paragraph still says what the source paragraph said. */
    private fun sameParagraph(source: SourceParagraph, model: Paragraph): Boolean {
        val original = source.model
        if (model.alignment != original.alignment) return false
        if (model.styleId != original.styleId) return false
        if (model.list != original.list) return false
        if (model.indentTwips != original.indentTwips) return false
        if (model.spacingBeforeTwips != original.spacingBeforeTwips) return false
        if (model.spacingAfterTwips != original.spacingAfterTwips) return false
        if (model.lineSpacingPercent != original.lineSpacingPercent) return false
        if (model.pageBreakBefore != original.pageBreakBefore) return false
        if (model.runs.size != original.runs.size) return false
        model.runs.forEachIndexed { index, run -> if (run != original.runs[index]) return false }
        if (model.images.size != source.modelledImages.size) return false
        model.images.forEachIndexed { index, image ->
            val bytes = source.modelledImages[index].bytes ?: return false
            if (!bytes.contentEquals(image.bytes)) return false
        }
        return true
    }

    /**
     * Builds the new XML of a paragraph.
     *
     * The paragraph's own markup is the starting point: attributes, properties
     * and the elements around its runs (a hyperlink wrapper, a comment anchor,
     * a bookmark) all stay. Only the runs the user edited and the properties
     * they changed get rewritten.
     */
    private fun renderParagraph(
        source: SourceParagraph,
        model: Paragraph,
        media: MediaRegistry,
    ): String {
        val xml = source.xml
        val slots = source.runs.mapIndexed { index, run -> run.element to index }.toMap()
        val removedImages = mutableSetOf<XmlElement>()
        mapImages(source, model, removedImages)

        val builder = StringBuilder()
        builder.append(openTag(source.element, xml))
        builder.append(renderParagraphProperties(source, model))

        val alignment = RunAlignment.of(source.model.runs, model.runs)
        val content = StringBuilder()
        rebuildChildren(source.element, xml, source.runs, slots, alignment, removedImages, content, isParagraph = true)
        content.append(renderStandalonePieces(alignment.trailing))
        builder.append(content)

        // Images the model has that the source did not.
        for (index in source.modelledImages.size until model.images.size) {
            val image = model.images[index]
            val relId = media.of(image)
            val sb = StringBuilder()
            DocxXml.writeImage(sb, image, relId)
            builder.append(sb)
        }

        builder.append("</w:p>")
        return builder.toString()
    }

    /**
     * Rebuilds the children of an element.
     *
     * Runs get patched, a wrapper like a hyperlink is rebuilt around its own
     * children so the runs inside it get patched too, and a text box is copied
     * through untouched since there's no way to put a caret inside one.
     */
    private fun rebuildChildren(
        parent: XmlElement,
        xml: String,
        runs: List<SourceRun>,
        slots: Map<XmlElement, Int>,
        alignment: RunAlignment,
        removedImages: Set<XmlElement>,
        out: StringBuilder,
        isParagraph: Boolean,
    ) {
        parent.children.forEach { child ->
            when {
                isParagraph && child.localName == "pPr" && child.isWord() -> Unit
                child.localName == "txbxContent" -> out.append(child.source(xml))
                child.localName == "r" && child.isWord() -> {
                    val index = slots[child]
                    if (index == null) {
                        renderPlainRun(child, removedImages, xml)?.let { out.append(it) }
                    } else {
                        out.append(renderRunPieces(runs[index], alignment.pieces[index], removedImages, xml))
                    }
                }
                child.isText || child.selfClosing -> out.append(child.source(xml))
                else -> {
                    val open = openTag(child, xml)
                    out.append(open)
                    rebuildChildren(child, xml, runs, slots, alignment, removedImages, out, isParagraph = false)
                    out.append("</").append(child.qName).append(">")
                }
            }
        }
    }

    /**
     * Records the source images the user removed.
     */
    private fun mapImages(
        source: SourceParagraph,
        model: Paragraph,
        removed: MutableSet<XmlElement>,
    ) {
        source.modelledImages.forEachIndexed { index, image ->
            if (model.images.getOrNull(index) == null) removed += image.element
        }
    }

    /**
     * Rebuilds a run from the pieces that landed on it.
     *
     * A run the user split comes back as several runs: the first one keeps the
     * original element, the rest are written next to it. When every piece
     * looks the same the run is written once with the text joined back up.
     */
    private fun renderRunPieces(
        slot: SourceRun,
        pieces: List<Piece>,
        removedImages: Set<XmlElement>,
        xml: String,
    ): String {
        if (pieces.isEmpty()) {
            // The text was deleted. The run itself stays when it carries
            // something that is not text, so an image is not lost with it.
            return renderRun(slot, null, removedImages, xml)
        }
        val element = slot.element
        val properties = element.child("rPr")
        val first = pieces.first()
        val builder = StringBuilder()

        if (pieces.size == 1 || pieces.all { it.model.hasSameFormattingAs(first.model) }) {
            val text = pieces.joinToString("") { it.text }
            builder.append(openTag(element, xml))
            val newProperties = renderRunProperties(properties, slot.model, first.model, xml)
            if (properties == null && newProperties.isNotEmpty()) builder.append(newProperties)
            var written = false
            element.children.forEach { child ->
                when {
                    child.localName == "rPr" && child.isWord() -> builder.append(newProperties)
                    slot.contentNodes.any { it === child } -> {
                        if (!written) {
                            written = true
                            builder.append(renderRunText(text))
                        }
                    }
                    child.localName == "drawing" || child.localName == "pict" -> {
                        if (!removedImages.any { it === child }) builder.append(child.source(xml))
                    }
                    else -> builder.append(child.source(xml))
                }
            }
            builder.append("</w:r>")
            return builder.toString()
        }

        pieces.forEachIndexed { index, piece ->
            if (index == 0) {
                builder.append(renderRun(slot, piece.model, removedImages, xml, piece.text))
            } else {
                builder.append(renderNewRun(slot, piece, xml))
            }
        }
        return builder.toString()
    }

    /** A run written next to the one it was split off from. */
    private fun renderNewRun(slot: SourceRun, piece: Piece, xml: String): String {
        val element = slot.element
        val builder = StringBuilder()
        builder.append(openTag(element, xml))
        val properties = element.child("rPr")
        val newProperties = renderRunProperties(properties, slot.model, piece.model, xml)
        if (properties == null && newProperties.isNotEmpty()) builder.append(newProperties)
        builder.append(renderRunText(piece.text))
        builder.append("</w:r>")
        return builder.toString()
    }

    /** Text that belongs to no run of the source, written on its own. */
    private fun renderStandalonePieces(pieces: List<Piece>): String {
        if (pieces.isEmpty()) return ""
        val builder = StringBuilder()
        pieces.forEach { piece ->
            builder.append("<w:r>")
            builder.append(renderAbsoluteProperties(piece.model))
            builder.append(renderRunText(piece.text))
            builder.append("</w:r>")
        }
        return builder.toString()
    }

    /** The run properties of a run with no source to copy them from. */
    private fun renderAbsoluteProperties(model: Run): String {
        val fragments = mutableListOf<Fragment>()
        if (model.bold) fragments += Fragment("b", "<w:b/>")
        if (model.italic) fragments += Fragment("i", "<w:i/>")
        if (model.strike) fragments += Fragment("strike", "<w:strike/>")
        model.colorRgb?.let { fragments += Fragment("color", "<w:color w:val=\"${DocxXml.hexColor(it)}\"/>") }
        model.sizePt?.let {
            val halfPoints = (it * 2f).toInt()
            fragments += Fragment("sz", "<w:sz w:val=\"$halfPoints\"/>")
        }
        if (model.underline) fragments += Fragment("u", "<w:u w:val=\"single\"/>")
        if (fragments.isEmpty()) return ""
        val builder = StringBuilder("<w:rPr>")
        fragments.sortedBy { RPR_ORDER.indexOf(it.name).takeIf { index -> index >= 0 } ?: RPR_ORDER.size }
            .forEach { builder.append(it.xml) }
        builder.append("</w:rPr>")
        return builder.toString()
    }

    /**
     * Rebuilds a run.
     *
     * Its properties are kept and only the ones the user changed get
     * rewritten. Its text is replaced with the model's text. Everything else
     * it carries (a drawing, a footnote reference) stays.
     */
    private fun renderRun(
        slot: SourceRun,
        modelRun: Run?,
        removedImages: Set<XmlElement>,
        xml: String,
        text: String? = null,
    ): String {
        val element = slot.element
        val builder = StringBuilder()
        builder.append(openTag(element, xml))
        val contentNodes = slot.contentNodes
        val properties = element.child("rPr")
        val newProperties = renderRunProperties(properties, slot.model, modelRun, xml)
        // A run without its own properties gets them written in front of its
        // content, which is where the schema expects them.
        if (properties == null && newProperties.isNotEmpty()) builder.append(newProperties)
        var written = false
        element.children.forEach { child ->
            when {
                child.localName == "rPr" && child.isWord() -> builder.append(newProperties)
                contentNodes.any { it === child } -> {
                    if (!written) {
                        written = true
                        val value = text ?: modelRun?.text
                        if (value != null) builder.append(renderRunText(value))
                    }
                }
                child.localName == "drawing" || child.localName == "pict" -> {
                    if (!removedImages.any { it === child }) builder.append(child.source(xml))
                }
                else -> builder.append(child.source(xml))
            }
        }
        builder.append("</w:r>")
        return builder.toString()
    }

    /**
     * Rebuilds a run that carries no text, usually a run holding a drawing.
     * It is dropped when the drawing it holds was removed and nothing else is
     * left.
     */
    private fun renderPlainRun(
        element: XmlElement,
        removedImages: Set<XmlElement>,
        xml: String,
    ): String? {
        var hasContent = false
        val builder = StringBuilder()
        builder.append(openTag(element, xml))
        element.children.forEach { child ->
            when {
                child.localName == "rPr" && child.isWord() -> builder.append(child.source(xml))
                child.localName == "drawing" || child.localName == "pict" -> {
                    if (removedImages.any { it === child }) return@forEach
                    hasContent = true
                    builder.append(child.source(xml))
                }
                else -> {
                    hasContent = true
                    builder.append(child.source(xml))
                }
            }
        }
        builder.append("</w:r>")
        return if (hasContent) builder.toString() else null
    }

    /** The text of a run as `w:t`, `w:tab` and `w:br` elements. */
    private fun renderRunText(text: String): String {
        val builder = StringBuilder()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                builder.append("<w:t xml:space=\"preserve\">")
                    .append(escapeXmlText(buffer.toString()))
                    .append("</w:t>")
                buffer.clear()
            }
        }
        text.forEach { ch ->
            when (ch) {
                '\n' -> {
                    flush()
                    builder.append("<w:br/>")
                }
                '\t' -> {
                    flush()
                    builder.append("<w:tab/>")
                }
                else -> buffer.append(ch)
            }
        }
        flush()
        return builder.toString()
    }

    // ------------------------------------------------- paragraph and run props

    /**
     * Rewrites the paragraph properties.
     *
     * The properties the editor understands get compared against what the
     * source resolved to. When they differ the property is written out, when
     * they agree the source's own markup stays, so a style that supplies the
     * spacing keeps supplying it.
     *
     * Properties the editor doesn't understand (keepNext, tabs, shading, the
     * paragraph mark's own run properties) are copied through.
     */
    private fun renderParagraphProperties(source: SourceParagraph, model: Paragraph): String {
        val original = source.model
        val xml = source.xml
        val fragments = mutableListOf<Fragment>()

        source.properties?.children?.forEach { child ->
            val name = child.localName
            val replacement = when {
                name == "pStyle" && model.styleId != original.styleId ->
                    model.styleId?.let { "<w:pStyle w:val=\"${escapeXmlAttribute(it)}\"/>" }
                name == "numPr" && model.list != original.list -> renderNumPr(model.list)
                name == "pageBreakBefore" && model.pageBreakBefore != original.pageBreakBefore ->
                    if (model.pageBreakBefore) "<w:pageBreakBefore/>" else null
                name == "jc" && model.alignment != original.alignment ->
                    "<w:jc w:val=\"${alignmentValue(model.alignment)}\"/>"
                name == "ind" && model.indentTwips != original.indentTwips ->
                    model.indentTwips?.let { "<w:ind w:left=\"$it\"/>" }
                name == "spacing" && (
                    model.spacingBeforeTwips != original.spacingBeforeTwips ||
                        model.spacingAfterTwips != original.spacingAfterTwips ||
                        model.lineSpacingPercent != original.lineSpacingPercent
                    ) -> renderSpacing(model, original)
                else -> child.source(xml)
            }
            if (replacement != null) {
                fragments += Fragment(name, replacement)
            }
        }

        // Properties the source did not have but the model now needs.
        if (model.styleId != original.styleId && source.properties?.child("pStyle") == null) {
            model.styleId?.let {
                fragments += Fragment("pStyle", "<w:pStyle w:val=\"${escapeXmlAttribute(it)}\"/>")
            }
        }
        if (model.list != original.list && source.properties?.child("numPr") == null) {
            renderNumPr(model.list)?.let { fragments += Fragment("numPr", it) }
        }
        if (model.pageBreakBefore != original.pageBreakBefore &&
            source.properties?.child("pageBreakBefore") == null
        ) {
            if (model.pageBreakBefore) fragments += Fragment("pageBreakBefore", "<w:pageBreakBefore/>")
        }
        if (model.alignment != original.alignment && source.properties?.child("jc") == null) {
            fragments += Fragment("jc", "<w:jc w:val=\"${alignmentValue(model.alignment)}\"/>")
        }
        if (model.indentTwips != original.indentTwips && source.properties?.child("ind") == null) {
            model.indentTwips?.let { fragments += Fragment("ind", "<w:ind w:left=\"$it\"/>") }
        }
        if (fragments.isEmpty()) return ""

        val builder = StringBuilder("<w:pPr>")
        fragments.sortedBy { PPR_ORDER.indexOf(it.name).takeIf { index -> index >= 0 } ?: PPR_ORDER.size }
            .forEach { builder.append(it.xml) }
        builder.append("</w:pPr>")
        return builder.toString()
    }

    private fun renderNumPr(list: ListInfo?): String? {
        if (list == null) return null
        return "<w:numPr><w:ilvl w:val=\"${list.level}\"/>" +
            "<w:numId w:val=\"${list.numId}\"/></w:numPr>"
    }

    /**
     * The `w:spacing` element. Only the values the user changed are written;
     * the rest of the source's spacing (line rule, before-lines) is kept.
     */
    private fun renderSpacing(model: Paragraph, original: Paragraph): String {
        val builder = StringBuilder("<w:spacing")
        if (model.spacingBeforeTwips != original.spacingBeforeTwips) {
            model.spacingBeforeTwips?.let { builder.append(" w:before=\"$it\"") }
        }
        if (model.spacingAfterTwips != original.spacingAfterTwips) {
            model.spacingAfterTwips?.let { builder.append(" w:after=\"$it\"") }
        }
        if (model.lineSpacingPercent != original.lineSpacingPercent) {
            model.lineSpacingPercent?.let {
                builder.append(" w:line=\"").append(it * 240 / 100).append("\" w:lineRule=\"auto\"")
            }
        }
        builder.append("/>")
        return builder.toString()
    }

    /**
     * Rewrites the run properties.
     *
     * Same rule as for paragraphs: a property gets written only when the model
     * disagrees with what the source resolved to. That's what keeps a run
     * that inherits its bold from a style free of a redundant `<w:b/>`.
     */
    private fun renderRunProperties(
        properties: XmlElement?,
        original: Run,
        model: Run?,
        xml: String,
    ): String {
        if (model == null) return properties?.source(xml).orEmpty()
        val fragments = mutableListOf<Fragment>()

        properties?.children?.forEach { child ->
            val name = child.localName
            val replacement = when {
                name == "b" && model.bold != original.bold ->
                    if (model.bold) "<w:b/>" else "<w:b w:val=\"0\"/>"
                name == "i" && model.italic != original.italic ->
                    if (model.italic) "<w:i/>" else "<w:i w:val=\"0\"/>"
                name == "u" && model.underline != original.underline ->
                    if (model.underline) "<w:u w:val=\"single\"/>" else "<w:u w:val=\"none\"/>"
                name == "strike" && model.strike != original.strike ->
                    if (model.strike) "<w:strike/>" else "<w:strike w:val=\"0\"/>"
                name == "sz" && model.sizePt != original.sizePt -> sizeFragment(model, original, "sz")
                name == "szCs" && model.sizePt != original.sizePt ->
                    sizeFragment(model, original, "szCs")
                name == "color" && model.colorRgb != original.colorRgb ->
                    model.colorRgb?.let { "<w:color w:val=\"${DocxXml.hexColor(it)}\"/>" }
                        ?: "<w:color w:val=\"auto\"/>"
                else -> child.source(xml)
            }
            if (replacement != null) fragments += Fragment(name, replacement)
        }

        // Properties the source did not carry but the model now needs.
        if (model.bold != original.bold && properties?.child("b") == null) {
            fragments += Fragment(
                "b",
                if (model.bold) "<w:b/>" else "<w:b w:val=\"0\"/>",
            )
        }
        if (model.italic != original.italic && properties?.child("i") == null) {
            fragments += Fragment(
                "i",
                if (model.italic) "<w:i/>" else "<w:i w:val=\"0\"/>",
            )
        }
        if (model.underline != original.underline && properties?.child("u") == null) {
            fragments += Fragment(
                "u",
                if (model.underline) "<w:u w:val=\"single\"/>" else "<w:u w:val=\"none\"/>",
            )
        }
        if (model.strike != original.strike && properties?.child("strike") == null) {
            fragments += Fragment(
                "strike",
                if (model.strike) "<w:strike/>" else "<w:strike w:val=\"0\"/>",
            )
        }
        if (model.sizePt != original.sizePt && properties?.child("sz") == null) {
            sizeFragment(model, original, "sz")?.let { fragments += Fragment("sz", it) }
        }
        if (model.colorRgb != original.colorRgb && properties?.child("color") == null) {
            fragments += Fragment(
                "color",
                model.colorRgb?.let { "<w:color w:val=\"${DocxXml.hexColor(it)}\"/>" }
                    ?: "<w:color w:val=\"auto\"/>",
            )
        }

        if (fragments.isEmpty()) return ""
        val builder = StringBuilder("<w:rPr>")
        fragments.sortedBy { RPR_ORDER.indexOf(it.name).takeIf { index -> index >= 0 } ?: RPR_ORDER.size }
            .forEach { builder.append(it.xml) }
        builder.append("</w:rPr>")
        return builder.toString()
    }

    private fun sizeFragment(model: Run, original: Run, name: String): String? {
        val size = model.sizePt ?: original.sizePt ?: return null
        val halfPoints = (size * 2f).toInt()
        return "<w:$name w:val=\"$halfPoints\"/>"
    }

    // ------------------------------------------------------------------ table

    /**
     * Rewrites a table.
     *
     * Rows and cells that exist on both sides keep their own markup and get
     * their contents patched. Added rows are generated, removed ones are
     * dropped. The table's own properties and grid stay even when the shape
     * changes.
     */
    private fun editTable(
        source: SourceTable,
        model: Table,
        media: MediaRegistry,
        edits: MutableList<Edit>,
    ) {
        // A table nobody touched is left exactly as it was. Re-rendering it
        // would rewrite every cell and lose the source's own markup.
        if (sameTable(source, model)) return

        val xml = source.xml
        val rows = mutableListOf<String>()
        val shared = minOf(model.rows.size, source.rows.size)

        for (index in 0 until shared) {
            rows += renderRow(source.rows[index], model.rows[index], media)
        }
        for (index in shared until model.rows.size) {
            rows += renderNewRow(model.rows[index], media)
        }

        val properties = source.element.child("tblPr")?.source(xml).orEmpty()
        val grid = source.element.child("tblGrid")?.source(xml).orEmpty()
        val builder = StringBuilder("<w:tbl>")
        builder.append(properties)
        builder.append(grid)
        rows.forEach { builder.append(it) }
        builder.append("</w:tbl>")

        edits += Edit(source.element.start, source.element.end, builder.toString())
    }

    /** True when the model table still says what the source table said. */
    private fun sameTable(source: SourceTable, model: Table): Boolean {
        if (model.columnCount != source.model.columnCount) return false
        if (model.borders != source.model.borders) return false
        if (model.rows.size != source.rows.size) return false
        model.rows.forEachIndexed { index, row ->
            val sourceRow = source.rows[index]
            if (row.header != sourceRow.model.header) return false
            if (row.cells.size != sourceRow.cells.size) return false
            row.cells.forEachIndexed { cellIndex, cell ->
                if (!sameCell(sourceRow.cells[cellIndex], cell)) return false
            }
        }
        return true
    }

    /**
     * True when the model cell still says what the source cell said, so the
     * cell's own markup can be kept untouched.
     */
    private fun sameCell(source: SourceCell, model: TableCell): Boolean {
        if (model.columnSpan != source.model.columnSpan) return false
        if (model.verticalMerge != source.model.verticalMerge) return false
        if (model.blocks.size != source.model.blocks.size) return false
        model.blocks.forEachIndexed { index, block ->
            val original = source.model.blocks[index]
            if (block is Paragraph && original is Paragraph) {
                if (!sameParagraphModel(original, block)) return false
            } else if (block != original) {
                return false
            }
        }
        return true
    }

    /** Compares two model paragraphs without a source paragraph behind them. */
    private fun sameParagraphModel(original: Paragraph, model: Paragraph): Boolean =
        original.alignment == model.alignment &&
            original.styleId == model.styleId &&
            original.list == model.list &&
            original.indentTwips == model.indentTwips &&
            original.spacingBeforeTwips == model.spacingBeforeTwips &&
            original.spacingAfterTwips == model.spacingAfterTwips &&
            original.lineSpacingPercent == model.lineSpacingPercent &&
            original.pageBreakBefore == model.pageBreakBefore &&
            original.runs == model.runs

    private fun renderRow(
        sourceRow: SourceRow,
        modelRow: TableRow,
        media: MediaRegistry,
    ): String {
        val xml = sourceRow.xml
        val builder = StringBuilder()
        builder.append(openTag(sourceRow.element, xml))
        val properties = sourceRow.element.child("trPr")?.source(xml).orEmpty()
        builder.append(properties)
        val shared = minOf(modelRow.cells.size, sourceRow.cells.size)
        for (index in 0 until shared) {
            builder.append(renderCell(sourceRow.cells[index], modelRow.cells[index], media))
        }
        for (index in shared until modelRow.cells.size) {
            builder.append(renderNewCell(modelRow.cells[index], media))
        }
        builder.append("</w:tr>")
        return builder.toString()
    }

    private fun renderNewRow(row: TableRow, media: MediaRegistry): String {
        val builder = StringBuilder("<w:tr>")
        if (row.header) builder.append("<w:trPr><w:tblHeader/></w:trPr>")
        row.cells.forEach { cell -> builder.append(renderNewCell(cell, media)) }
        builder.append("</w:tr>")
        return builder.toString()
    }

    private fun renderCell(
        source: SourceCell,
        model: TableCell,
        media: MediaRegistry,
    ): String {
        val xml = source.xml
        val builder = StringBuilder()
        builder.append(openTag(source.element, xml))
        val properties = source.element.child("tcPr")?.source(xml).orEmpty()
        builder.append(patchedCellProperties(properties, source, model))
        val shared = minOf(model.blocks.size, source.blocks.size)
        for (index in 0 until shared) {
            val modelBlock = model.blocks[index]
            val sourceBlock = source.blocks[index]
            when {
                modelBlock is Paragraph && sourceBlock is SourceParagraph ->
                    builder.append(
                        if (sameParagraph(sourceBlock, modelBlock)) {
                            // Nothing changed in here, so it is copied as it was.
                            sourceBlock.element.source(sourceBlock.xml)
                        } else {
                            renderParagraph(sourceBlock, modelBlock, media)
                        }
                    )
                modelBlock is Table && sourceBlock is SourceTable -> {
                    val nested = mutableListOf<Edit>()
                    editTable(sourceBlock, modelBlock, media, nested)
                    builder.append(
                        if (nested.isEmpty()) {
                            sourceBlock.element.source(sourceBlock.xml)
                        } else {
                            applyEdits(sourceBlock.xml, nested)
                                .substring(sourceBlock.element.start, sourceBlock.element.end)
                        },
                    )
                }
                else -> builder.append(renderBlock(modelBlock, media))
            }
        }
        for (index in shared until model.blocks.size) {
            builder.append(renderBlock(model.blocks[index], media))
        }
        builder.append("</w:tc>")
        return builder.toString()
    }

    /** The cell properties, with the span and the vertical merge kept in step. */
    private fun patchedCellProperties(
        original: String,
        source: SourceCell,
        model: TableCell,
    ): String {
        var xml = original
        if (model.columnSpan != source.model.columnSpan) {
            xml = xml.replace(Regex("<w:gridSpan[^>]*/>"), "")
            if (model.columnSpan > 1) {
                val element = "<w:gridSpan w:val=\"${model.columnSpan}\"/>"
                xml = insertIntoProperties(xml, element)
            }
        }
        if (model.verticalMerge != source.model.verticalMerge) {
            xml = xml.replace(Regex("<w:vMerge[^>]*/>"), "")
            when (model.verticalMerge) {
                VerticalMerge.RESTART -> xml = insertIntoProperties(xml, "<w:vMerge w:val=\"restart\"/>")
                VerticalMerge.CONTINUE -> xml = insertIntoProperties(xml, "<w:vMerge/>")
                VerticalMerge.NONE -> Unit
            }
        }
        return xml
    }

    /** Puts an element at the start of a `w:tcPr`, creating the properties when needed. */
    private fun insertIntoProperties(xml: String, element: String): String {
        val index = xml.indexOf("<w:tcPr>")
        if (index < 0) return "<w:tcPr>$element</w:tcPr>" + xml
        val at = index + "<w:tcPr>".length
        return xml.substring(0, at) + element + xml.substring(at)
    }

    private fun renderNewCell(cell: TableCell, media: MediaRegistry): String {
        val builder = StringBuilder("<w:tc><w:tcPr>")
        if (cell.columnSpan > 1) builder.append("<w:gridSpan w:val=\"${cell.columnSpan}\"/>")
        builder.append("</w:tcPr>")
        if (cell.blocks.isEmpty()) {
            builder.append("<w:p/>")
        } else {
            cell.blocks.forEach { builder.append(renderBlock(it, media)) }
        }
        builder.append("</w:tc>")
        return builder.toString()
    }

    // ------------------------------------------------------------- generation

    /** Generates the XML of a block the user added. */
    private fun renderBlock(block: Block, media: MediaRegistry): String {
        val builder = StringBuilder()
        DocxXml.writeBlock(builder, block, media)
        return builder.toString()
    }

    // ------------------------------------------------------- header / footer

    /**
     * Adds a header or footer part to a document that had none.
     *
     * The new part is written from the model, the relationship and the content
     * type get registered, and the section properties get a reference to it.
     */
    private fun addSidePart(
        parts: MutableMap<String, ByteArray>,
        blocks: List<Block>,
        media: MediaRegistry,
        kind: String,
        root: String,
    ) {
        val index = nextPartIndex(parts, kind)
        val partName = "word/$kind$index.xml"
        val relId = media.nextRelId()
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        builder.append("<w:$root xmlns:w=\"${DocxXml.WORD_NS}\" ")
        builder.append("xmlns:r=\"${DocxXml.RELATIONSHIPS_NAMESPACE}\" ")
        builder.append("xmlns:wp=\"${DocxXml.WORDPROCESSING_DRAWING_NAMESPACE}\" ")
        builder.append("xmlns:a=\"${DocxXml.DRAWINGML_NAMESPACE}\" ")
        builder.append("xmlns:pic=\"${DocxXml.PICTURE_NAMESPACE}\">")
        blocks.forEach { DocxXml.writeBlock(builder, it, media) }
        builder.append("</w:$root>")
        parts[partName] = builder.toString().encodeToByteArray()
        media.addRelationship(relId, kind, "$kind$index.xml")
        media.addContentTypeOverride("/$partName", sideContentType(kind))
        parts[source.body.partName] = insertSideReference(
            parts.getValue(source.body.partName).decodeToString(),
            kind,
            relId,
        ).encodeToByteArray()
    }

    private fun nextPartIndex(parts: Map<String, ByteArray>, kind: String): Int {
        val pattern = Regex("word/$kind(\\d+)\\.xml")
        val highest = parts.keys.mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return highest + 1
    }

    private fun sideContentType(kind: String): String = when (kind) {
        "header" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
        else -> "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"
    }

    /** Adds a `w:headerReference` or `w:footerReference` to the section properties. */
    private fun insertSideReference(xml: String, kind: String, relId: String): String {
        val elementName = if (kind == "header") "headerReference" else "footerReference"
        val reference = "<w:$elementName w:type=\"default\" r:id=\"$relId\"/>"
        val sectPr = XmlScanner.parse(xml).root.descendants()
            .firstOrNull { it.localName == "sectPr" } ?: return xml
        if (sectPr.selfClosing) {
            // `<w:sectPr/>` has no room for a reference; it has to be opened
            // up first, or the reference would land outside the element.
            val openTag = xml.substring(sectPr.start, sectPr.contentEnd)
            val opened = openTag.removeSuffix("/>") + ">"
            return xml.substring(0, sectPr.start) + opened + reference + "</w:sectPr>" +
                xml.substring(sectPr.end)
        }
        val insertAt = sectPr.contentStart
        return xml.substring(0, insertAt) + reference + xml.substring(insertAt)
    }

    /** Removes the default header or footer reference from the section properties. */
    private fun removeSideReference(xml: String, elementName: String): String {
        val reference = XmlScanner.parse(xml).root.descendants().firstOrNull {
            it.localName == elementName &&
                it.attr("type").let { type -> type == null || type == "default" }
        } ?: return xml
        return xml.substring(0, reference.start) + xml.substring(reference.end)
    }

    // --------------------------------------------------------------- helpers

    /** The start tag of an element, copied from the source. */
    private fun openTag(element: XmlElement, xml: String): String =
        xml.substring(element.start, element.contentStart)

    /** Applies a list of edits to a string. */
    private fun applyEdits(xml: String, edits: List<Edit>): String {
        if (edits.isEmpty()) return xml
        val sorted = edits.sortedBy { it.start }
        val builder = StringBuilder(xml.length + 64)
        var cursor = 0
        sorted.forEach { edit ->
            if (edit.start < cursor) return@forEach
            builder.append(xml, cursor, edit.start)
            builder.append(edit.replacement)
            cursor = edit.end
        }
        builder.append(xml, cursor, xml.length)
        return builder.toString()
    }

    private fun alignmentValue(alignment: ParagraphAlignment): String = when (alignment) {
        ParagraphAlignment.CENTER -> "center"
        ParagraphAlignment.END -> "right"
        ParagraphAlignment.JUSTIFY -> "both"
        ParagraphAlignment.START -> "left"
    }

    private fun zip(parts: Map<String, ByteArray>): ByteArray {
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

    /** One replacement in a source string. */
    private data class Edit(val start: Int, val end: Int, val replacement: String)

    /** One piece of a rewritten property element. */
    private data class Fragment(val name: String, val xml: String)

    private companion object {
        /** The relationship part of the document, when one is needed. */
        const val DOCUMENT_RELS_PART = "word/_rels/document.xml.rels"

        /** The order `w:pPr` children must appear in. */
        val PPR_ORDER = listOf(
            "pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl",
            "numPr", "suppressLineNumbers", "pBdr", "shd", "tabs", "suppressAutoHyphens",
            "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE",
            "autoSpaceDN", "bidi", "adjustRightInd", "snapToGrid", "spacing", "ind",
            "contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection",
            "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr",
            "sectPr", "pPrChange",
        )

        /** The order `w:rPr` children must appear in. */
        val RPR_ORDER = listOf(
            "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike",
            "dstrike", "outline", "shadow", "emboss", "imprint", "noProof", "snapToGrid",
            "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz",
            "szCs", "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign",
            "rtl", "cs", "em", "lang", "eastAsianLayout", "specVanish", "oMath",
        )
    }
}

/**
 * Registers the media a document gains while it is edited.
 *
 * A new image needs three things: the bytes in the package, a relationship
 * pointing at them and a content type saying what they are. All three go into
 * parts the original package already had, so nothing else changes.
 */
internal class MediaRegistry(
    private val parts: MutableMap<String, ByteArray>,
    private val relsPartName: String?,
    private val contentTypesPartName: String?,
) : DocxXml.ImageRelId {
    private val assigned = IdentityHashMap<InlineImage, String>()
    private var nextRelNumber = 0

    /** The relationship id an image is written under, adding it when needed. */
    override fun of(image: InlineImage): String = assigned.getOrPut(image) {
        val relId = nextRelId()
        val extension = DocxXml.imageExtension(image.bytes)
        val partName = "media/image${nextMediaIndex()}.$extension"
        parts["word/$partName"] = image.bytes
        addRelationship(relId, "image", partName)
        addContentTypeDefault(extension, DocxXml.imageContentType(extension))
        relId
    }

    /** A relationship id that is not in use yet. */
    fun nextRelId(): String {
        val existing = parts[relsPartName]?.decodeToString()?.let { text ->
            Regex("Id=\"rId(\\d+)\"").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }
                .maxOrNull()
        } ?: 0
        nextRelNumber = maxOf(nextRelNumber + 1, existing + 1)
        return "rId${nextRelNumber}"
    }

    /** Adds a relationship to the part's relationship file. */
    fun addRelationship(relId: String, kind: String, target: String) {
        val name = relsPartName ?: return
        // A package with no relationships yet has no relationship part, or an
        // empty one. Both have to end up with a relationship in them.
        val text = parts[name]?.decodeToString() ?: EMPTY_RELATIONSHIPS
        val relationship = "<Relationship Id=\"$relId\" " +
            "Type=\"${DocxXml.RELATIONSHIPS_NAMESPACE}/$kind\" Target=\"$target\"/>"
        parts[name] = DocxXml.insertIntoContainer(text, "Relationships", relationship)
            .encodeToByteArray()
    }

    /** Declares a content type for a media extension, when it is missing. */
    fun addContentTypeDefault(extension: String, contentType: String) {
        val name = contentTypesPartName ?: return
        val text = parts[name]?.decodeToString() ?: return
        if (text.contains("Extension=\"$extension\"")) return
        val declaration = "<Default Extension=\"$extension\" ContentType=\"$contentType\"/>"
        val index = text.indexOf("<Override")
        val at = if (index >= 0) index else text.indexOf("</Types>")
        if (at < 0) return
        parts[name] = (text.substring(0, at) + declaration + text.substring(at)).encodeToByteArray()
    }


    /** Declares the content type of a part, when it is missing. */
    fun addContentTypeOverride(partName: String, contentType: String) {
        val name = contentTypesPartName ?: return
        val text = parts[name]?.decodeToString() ?: return
        if (text.contains("PartName=\"$partName\"")) return
        val declaration = "<Override PartName=\"$partName\" ContentType=\"$contentType\"/>"
        parts[name] = DocxXml.insertIntoContainer(text, "Types", declaration)
            .encodeToByteArray()
    }

    private companion object {
        const val EMPTY_RELATIONSHIPS =
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>"
    }

    private fun nextMediaIndex(): Int {
        val pattern = Regex("word/media/image(\\d+)\\.")
        val highest = parts.keys.mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return highest + 1
    }
}
