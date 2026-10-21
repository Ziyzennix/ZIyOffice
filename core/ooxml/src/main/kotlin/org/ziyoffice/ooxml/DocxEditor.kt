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
import org.ziyoffice.ooxml.xml.XmlElement
import org.ziyoffice.ooxml.xml.XmlScanner
import org.ziyoffice.ooxml.xml.escapeXmlAttribute
import org.ziyoffice.ooxml.xml.escapeXmlText

/**
 * Writes an edited document back into the package it came from.
 *
 * The previous writer rebuilt a document from the model alone, which meant a
 * save produced a new package holding only what the model could express:
 * comments, footnotes, tracked changes, hyperlinks, equations, text boxes,
 * section breaks, styles, themes and document properties were all dropped.
 *
 * This writer takes the opposite approach. It starts from the original package
 * and changes only what the user changed:
 *
 *  - a paragraph nobody touched is copied through byte for byte;
 *  - a paragraph that was edited keeps its `w:pPr`, its run properties, its
 *    hyperlinks, comment anchors and drawings, and only the text and the
 *    formatting the user actually changed are rewritten;
 *  - every other part of the package — styles, numbering, theme, settings,
 *    footnotes, comments, headers and footers the editor does not touch — is
 *    copied through untouched;
 *  - content the editor cannot represent is preserved but not editable, and
 *    the package keeps working because its relationships stay intact.
 *
 * When the structure changes — a paragraph added, a row inserted — the new
 * content is generated from the model and spliced into the original XML, so
 * the surrounding document keeps its own markup.
 */
class DocxEditor(private val source: DocxSource) {

    /** Saves [document] into the source package and returns the new bytes. */
    fun save(document: Document): ByteArray {
        val parts = LinkedHashMap(source.parts)
        val bodyMedia = MediaRegistry(parts, source.documentRelsPart, source.contentTypesPart)

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
     * Rewrites one part — body, header or footer — and returns its new XML.
     *
     * The model blocks are matched to the source blocks by position. A block
     * that changed is replaced; a block that did not is left alone; blocks the
     * user added are generated and appended; blocks the user removed are
     * dropped.
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
     * The paragraph's own markup is the starting point: its attributes, its
     * properties, the elements around its runs — a hyperlink wrapper, a comment
     * anchor, a bookmark — are all kept. Only the runs the user edited and the
     * properties the user changed are rewritten.
     */
    private fun renderParagraph(
        source: SourceParagraph,
        model: Paragraph,
        media: MediaRegistry,
    ): String {
        val xml = source.xml
        val slots = source.runs.associateBy { it.element }
        val removedImages = mutableSetOf<XmlElement>()
        mapImages(source, model, removedImages)

        val builder = StringBuilder()
        builder.append(openTag(source.element, xml))
        builder.append(renderParagraphProperties(source, model))

        val state = RunState(model)
        val content = StringBuilder()
        rebuildChildren(source.element, xml, slots, state, removedImages, content, isParagraph = true)
        content.append(renderExtraRuns(state))
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

    /** How far through the model's runs the rebuild has got. */
    private class RunState(val model: Paragraph) {
        var index = 0
    }

    /**
     * Rebuilds the children of an element.
     *
     * Runs are patched; a wrapper such as a hyperlink is rebuilt around its
     * own children so the runs inside it are patched too; a text box is copied
     * through untouched, because the editor cannot place a caret inside one.
     */
    private fun rebuildChildren(
        parent: XmlElement,
        xml: String,
        slots: Map<XmlElement, SourceRun>,
        state: RunState,
        removedImages: Set<XmlElement>,
        out: StringBuilder,
        isParagraph: Boolean,
    ) {
        parent.children.forEach { child ->
            when {
                isParagraph && child.localName == "pPr" && child.isWord() -> Unit
                child.localName == "txbxContent" -> out.append(child.source(xml))
                child.localName == "r" && child.isWord() -> {
                    val slot = slots[child]
                    if (slot != null) {
                        val modelRun = state.model.runs.getOrNull(state.index)
                        state.index++
                        out.append(renderRun(slot, modelRun, removedImages, xml))
                    } else {
                        renderPlainRun(child, removedImages, xml)?.let { out.append(it) }
                    }
                }
                child.isText || child.selfClosing -> out.append(child.source(xml))
                else -> {
                    val open = openTag(child, xml)
                    out.append(open)
                    rebuildChildren(child, xml, slots, state, removedImages, out, isParagraph = false)
                    out.append("</").append(child.qName).append(">")
                }
            }
        }
    }

    /** The runs the model has that the source did not. */
    private fun renderExtraRuns(state: RunState): String {
        val builder = StringBuilder()
        for (index in state.index until state.model.runs.size) {
            val sb = StringBuilder()
            DocxXml.writeRun(sb, state.model.runs[index])
            builder.append(sb)
        }
        return builder.toString()
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
     * Rebuilds a run.
     *
     * The run's properties are kept and only the properties the user changed
     * are rewritten; its text is replaced with the model's text; everything
     * else the run carries — a drawing, a footnote reference — stays.
     */
    private fun renderRun(
        slot: SourceRun,
        modelRun: Run?,
        removedImages: Set<XmlElement>,
        xml: String,
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
                        modelRun?.let { run -> builder.append(renderRunText(run.text)) }
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
     * Rebuilds a run that carries no text — usually a run holding a drawing.
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
     * The properties the editor understands are compared with what the source
     * resolved to. When they differ, the property is written explicitly; when
     * they agree, the source's own markup is left in place — so a style that
     * supplies the spacing keeps supplying it.
     *
     * Properties the editor does not understand — keepNext, tabs, shading, the
     * paragraph mark's own run properties — are copied through.
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
     * the rest of the source's spacing — line rule, before-lines — is kept.
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
     * The same rule as for paragraphs: a property is written only when the
     * model disagrees with what the source resolved to. That keeps a run that
     * inherits its bold from a style free of a redundant `<w:b/>`.
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
     * their contents patched. Rows the user added are generated; rows the user
     * removed are dropped. The table's own properties and grid are kept even
     * when the shape changes.
     */
    private fun editTable(
        source: SourceTable,
        model: Table,
        media: MediaRegistry,
        edits: MutableList<Edit>,
    ) {
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
                    builder.append(renderParagraph(sourceBlock, modelBlock, media))
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
     * type are registered, and the section properties get a reference to it.
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
 * that points at them and a content type that says what they are. All three
 * are added to the parts the original package already had, so nothing else in
 * the package changes.
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
        val text = parts[name]?.decodeToString() ?: return
        val relationship = "<Relationship Id=\"$relId\" " +
            "Type=\"${DocxXml.RELATIONSHIPS_NAMESPACE}/$kind\" Target=\"$target\"/>"
        val index = text.lastIndexOf("</Relationships>")
        if (index < 0) return
        parts[name] = (text.substring(0, index) + relationship + text.substring(index))
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
        if (text.contains("PartName=\"/$partName\"")) return
        val declaration = "<Override PartName=\"/$partName\" ContentType=\"$contentType\"/>"
        val index = text.lastIndexOf("</Types>")
        if (index < 0) return
        parts[name] = (text.substring(0, index) + declaration + text.substring(index))
            .encodeToByteArray()
    }

    private fun nextMediaIndex(): Int {
        val pattern = Regex("word/media/image(\\d+)\\.")
        val highest = parts.keys.mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return highest + 1
    }
}
