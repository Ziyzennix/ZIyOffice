package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table

/**
 * Writes the document model into a new .docx package with nothing but
 * java.util.zip. The counterpart of [DocxReader].
 *
 * This builds a package from scratch, so it is right for a document ZiyOffice
 * creates and wrong for a document ZiyOffice edits: rebuilding from the model
 * drops everything the model cannot express. [DocxEditor] is that path, it
 * starts from the original package and changes only what the user changed.
 *
 * The package is minimal but standards-valid: content types, the package and
 * document relationships, word/document.xml, a small word/styles.xml, and only
 * when needed word/numbering.xml, word/header1.xml, word/footer1.xml and
 * word/media entries.
 *
 * One deliberate simplification: the model stores resolved formatting, so
 * styles are flattened into direct formatting on save. Content, appearance,
 * lists, tables and images survive a round trip; style *names* do not.
 */
class DocxWriter {

    fun write(document: Document): ByteArray {
        val numbering = NumberingPlan.from(document)
        val images = ImageRegistry(
            firstRelId = FIRST_IMAGE_REL_ID + if (numbering.isEmpty) 0 else 1,
        )
        val body = StringBuilder()
        document.blocks.forEach { block ->
            DocxXml.writeBlock(body, block, images)
        }
        if (body.isEmpty()) {
            // The body must hold at least one block-level element.
            body.append("<w:p/>")
        }

        // A header or footer is written only when the document has content for
        // it: an empty reference would add a blank band on every page.
        val header = document.header?.takeIf { it.isNotEmpty() }
        val footer = document.footer?.takeIf { it.isNotEmpty() }

        val parts = linkedMapOf<String, ByteArray>()
        parts["[Content_Types].xml"] = contentTypes(numbering, images, header, footer)
            .encodeToByteArray()
        parts["_rels/.rels"] = ROOT_RELS.encodeToByteArray()
        parts["word/document.xml"] =
            documentXml(body.toString(), header != null, footer != null).encodeToByteArray()
        parts["word/_rels/document.xml.rels"] =
            documentRels(numbering, images, header, footer).encodeToByteArray()
        parts["word/styles.xml"] = STYLES_XML.encodeToByteArray()
        if (!numbering.isEmpty) {
            parts["word/numbering.xml"] = numbering.toXml().encodeToByteArray()
        }
        header?.let {
            parts["word/header1.xml"] = headerFooterXml(it, HEADER_ROOT, images).encodeToByteArray()
        }
        footer?.let {
            parts["word/footer1.xml"] = headerFooterXml(it, FOOTER_ROOT, images).encodeToByteArray()
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

    private fun documentXml(body: String, hasHeader: Boolean, hasFooter: Boolean): String {
        val references = StringBuilder()
        if (hasHeader) {
            references.append("<w:headerReference w:type=\"default\" r:id=\"$HEADER_REL_ID\"/>")
        }
        if (hasFooter) {
            references.append("<w:footerReference w:type=\"default\" r:id=\"$FOOTER_REL_ID\"/>")
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:document xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\" xmlns:r=\"$RELATIONSHIPS_NAMESPACE\" " +
            "xmlns:wp=\"$WORDPROCESSING_DRAWING_NAMESPACE\" xmlns:a=\"$DRAWINGML_NAMESPACE\" " +
            "xmlns:pic=\"$PICTURE_NAMESPACE\"><w:body>$body" +
            "<w:sectPr>$references<w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\"1418\"/>" +
            "</w:sectPr></w:body></w:document>"
    }

    private fun headerFooterXml(
        blocks: List<Block>,
        root: String,
        images: ImageRegistry,
    ): String {
        val body = StringBuilder()
        blocks.forEach { block -> DocxXml.writeBlock(body, block, images) }
        if (body.isEmpty()) body.append("<w:p/>")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:$root xmlns:w=\"$WORDPROCESSINGML_NAMESPACE\" " +
            "xmlns:r=\"$RELATIONSHIPS_NAMESPACE\" " +
            "xmlns:wp=\"$WORDPROCESSING_DRAWING_NAMESPACE\" " +
            "xmlns:a=\"$DRAWINGML_NAMESPACE\" " +
            "xmlns:pic=\"$PICTURE_NAMESPACE\">$body</w:$root>"
    }

    private fun contentTypes(
        numbering: NumberingPlan,
        images: ImageRegistry,
        header: List<Block>?,
        footer: List<Block>?,
    ): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        builder.append("<Types xmlns=\"$CONTENT_TYPES_NAMESPACE\">")
        builder.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        builder.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        images.slots.map { it.extension }.distinct().forEach { extension ->
            builder.append("<Default Extension=\"").append(extension)
                .append("\" ContentType=\"").append(DocxXml.imageContentType(extension)).append("\"/>")
        }
        builder.append("<Override PartName=\"/word/document.xml\" ")
            .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>")
        builder.append("<Override PartName=\"/word/styles.xml\" ")
            .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>")
        if (!numbering.isEmpty) {
            builder.append("<Override PartName=\"/word/numbering.xml\" ")
                .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>")
        }
        if (header != null) {
            builder.append("<Override PartName=\"/word/header1.xml\" ")
                .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>")
        }
        if (footer != null) {
            builder.append("<Override PartName=\"/word/footer1.xml\" ")
                .append("ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>")
        }
        builder.append("</Types>")
        return builder.toString()
    }

    private fun documentRels(
        numbering: NumberingPlan,
        images: ImageRegistry,
        header: List<Block>?,
        footer: List<Block>?,
    ): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        builder.append("<Relationships xmlns=\"$PACKAGE_RELS_NAMESPACE\">")
        builder.append("<Relationship Id=\"rId1\" Type=\"$RELATIONSHIPS_NAMESPACE/styles\" Target=\"styles.xml\"/>")
        if (!numbering.isEmpty) {
            builder.append("<Relationship Id=\"rId2\" Type=\"$RELATIONSHIPS_NAMESPACE/numbering\" Target=\"numbering.xml\"/>")
        }
        if (header != null) {
            builder.append("<Relationship Id=\"$HEADER_REL_ID\" ")
                .append("Type=\"$RELATIONSHIPS_NAMESPACE/header\" Target=\"header1.xml\"/>")
        }
        if (footer != null) {
            builder.append("<Relationship Id=\"$FOOTER_REL_ID\" ")
                .append("Type=\"$RELATIONSHIPS_NAMESPACE/footer\" Target=\"footer1.xml\"/>")
        }
        images.slots.forEach { slot ->
            builder.append("<Relationship Id=\"").append(slot.relId)
                .append("\" Type=\"").append(RELATIONSHIPS_NAMESPACE).append("/image\" Target=\"")
                .append(slot.partName).append("\"/>")
        }
        builder.append("</Relationships>")
        return builder.toString()
    }

    /** Assigns relationship ids and media part names to images as they appear. */
    private class ImageRegistry(private val firstRelId: Int) : DocxXml.ImageRelId {
        val slots = mutableListOf<ImageSlot>()
        private val assigned = IdentityHashMap<InlineImage, ImageSlot>()

        override fun of(image: InlineImage): String = assigned.getOrPut(image) {
            val index = slots.size
            val extension = DocxXml.imageExtension(image.bytes)
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

    /** The numbering part, rebuilt from the lists the document actually uses. */
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
                    builder.append("<w:numFmt w:val=\"").append(DocxXml.numberFormat(info.kind)).append("\"/>")
                    builder.append("<w:lvlText w:val=\"")
                        .append(DocxXml.escapeAttribute(DocxXml.levelText(info.kind, level))).append("\"/>")
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
        const val RELATIONSHIPS_NAMESPACE = DocxXml.RELATIONSHIPS_NAMESPACE
        const val WORDPROCESSING_DRAWING_NAMESPACE = DocxXml.WORDPROCESSING_DRAWING_NAMESPACE
        const val DRAWINGML_NAMESPACE = DocxXml.DRAWINGML_NAMESPACE
        const val PICTURE_NAMESPACE = DocxXml.PICTURE_NAMESPACE
        const val PACKAGE_RELS_NAMESPACE = DocxXml.PACKAGE_RELS_NAMESPACE
        const val CONTENT_TYPES_NAMESPACE = DocxXml.CONTENT_TYPES_NAMESPACE
        const val FIRST_IMAGE_REL_ID = 2
        const val HEADER_REL_ID = "rIdHeader1"
        const val FOOTER_REL_ID = "rIdFooter1"
        const val HEADER_ROOT = "hdr"
        const val FOOTER_ROOT = "ftr"

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

        fun imageExtension(bytes: ByteArray): String = DocxXml.imageExtension(bytes)
    }
}
