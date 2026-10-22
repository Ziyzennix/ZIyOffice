package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.ziyoffice.model.Slide
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.SlideTransition
import org.ziyoffice.model.SlideImage
import org.ziyoffice.model.SlideShape
import org.ziyoffice.model.SlideShapeKind

/**
 * Writes a [SlideDeck] back into a .pptx package.
 *
 * [edit] patches slide text and notes in the original package; other parts
 * pass through unchanged, so layout, image and animation data survives.
 * [create] makes a minimal title-and-content deck for new presentations.
 */
class PptxWriter {

    /** Patches [original] so its slides carry [deck]'s text, in [deck]'s order. */
    fun edit(original: ByteArray, deck: SlideDeck): ByteArray {
        val parts = readParts(original)
        val presentation = parts[PRESENTATION_PART]
            ?: throw IOException("$PRESENTATION_PART not found in the package")
        val rels = parseRels(parts[PRESENTATION_RELS_PART] ?: EMPTY_RELS.encodeToByteArray())
        val originalOrder = slideIds(presentation.decodeToString()).mapNotNull { id ->
            rels.firstOrNull { it.id == id }?.let { resolvePart(it.target) }
        }
        val existing = rels.filter { it.type == SLIDE_REL_TYPE }.map { resolvePart(it.target) }.toSet()
        var nextPart = (existing.mapNotNull { Regex("slide(\\d+)\\.xml$").find(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val kept = deck.slides.map { slide ->
            val requested = "ppt/slides/slide${slide.number}.xml"
            val part = if (requested in existing) requested else "ppt/slides/slide${nextPart++}.xml"
            val original = parts[part]
            val slideRels = parts[relsNameOf(part)]?.let { parseRels(it) } ?: emptyList()
            // A picture the file already has is left where it is. Only the
            // ones that are new are added, so a save does not double them.
            val alreadyThere = picturePartsOf(original?.decodeToString().orEmpty(), slideRels)
            val newImages = slide.images.filterNot { it.partName in alreadyThere }
            val relIds = mutableMapOf<String, String>()
            val relsXml = parts[relsNameOf(part)]?.decodeToString()
            var nextRel = nextRelationshipNumber(relsXml)
            newImages.forEach { image ->
                val part2 = if (image.partName.isEmpty()) {
                    "ppt/media/image${nextPart}.${imageExtension(image.bytes)}"
                } else {
                    image.partName
                }
                parts[part2] = image.bytes
                val relId = "rId$nextRel"
                relIds[part2] = relId
                parts[relsNameOf(part)] = (addImageRelationship(relsXml, relId, part2)
                    ?: relsXml.orEmpty()).encodeToByteArray()
                nextRel++
            }
            val withImages = newImages.map { image ->
                val part2 = relIds.keys.firstOrNull { it.endsWith(image.partName) } ?: image.partName
                image.copy(partName = part2)
            }
            parts[part] = parts[part]?.let {
                val text = rewriteSlideText(it.decodeToString(), slide.lines)
                val withShapes = addShapes(text, slide.shapes, withImages, relIds)
                applyTransition(withShapes, slide.transition).encodeToByteArray()
            }
                ?: newSlideXml(slide.lines, slide.transition, slide.shapes, withImages, relIds)
                    .encodeToByteArray()
            notesPartOf(part, parts)?.let { notes ->
                parts[notes] = rewriteSlideText(parts.getValue(notes).decodeToString(), slide.notes.split('\n')).encodeToByteArray()
            }
            part
        }
        originalOrder.filterNot { it in kept }.forEach { part ->
            notesPartOf(part, parts)?.let { notes ->
                parts.remove(notes)
                parts.remove(relsNameOf(notes))
            }
            parts.remove(part)
            parts.remove(relsNameOf(part))
        }
        val idByPart = rels.filter { it.type == SLIDE_REL_TYPE }.associate { resolvePart(it.target) to it.id }
        var nextId = (rels.mapNotNull { it.id.removePrefix("rId").toIntOrNull() }.maxOrNull() ?: 0) + 1
        val newRels = kept.map { part ->
            Relationship(idByPart[part] ?: "rId${nextId++}", SLIDE_REL_TYPE, part.removePrefix("ppt/"))
        }
        parts[PRESENTATION_PART] = rewriteSlideList(presentation.decodeToString(), newRels).encodeToByteArray()
        parts[PRESENTATION_RELS_PART] = relsXml(rels.filterNot { it.type == SLIDE_REL_TYPE } + newRels).encodeToByteArray()
        parts[CONTENT_TYPES_PART] = rewriteContentTypes(parts.getValue(CONTENT_TYPES_PART).decodeToString(), kept).encodeToByteArray()
        return zip(parts)
    }

    /** A minimal valid deck written from scratch. */
    fun create(deck: SlideDeck): ByteArray {
        val slides = deck.slides.ifEmpty { listOf(Slide(1, emptyList())) }
        val parts = linkedMapOf<String, ByteArray>()
        parts[CONTENT_TYPES_PART] = contentTypes(slides.size).encodeToByteArray()
        parts["_rels/.rels"] = ROOT_RELS.encodeToByteArray()
        parts[PRESENTATION_PART] = presentationXml(slides.size, deck.widthEmu, deck.heightEmu).encodeToByteArray()
        parts[PRESENTATION_RELS_PART] = presentationRels(slides.size).encodeToByteArray()
        parts["ppt/theme/theme1.xml"] = themeXml().encodeToByteArray()
        parts["ppt/slideMasters/slideMaster1.xml"] = slideMasterXml().encodeToByteArray()
        parts["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = MASTER_RELS.encodeToByteArray()
        parts["ppt/slideLayouts/slideLayout1.xml"] = slideLayoutXml().encodeToByteArray()
        parts["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = LAYOUT_RELS.encodeToByteArray()
        parts["ppt/notesMasters/notesMaster1.xml"] = notesMasterXml().encodeToByteArray()
        parts["ppt/notesMasters/_rels/notesMaster1.xml.rels"] = notesMasterRels().encodeToByteArray()
        slides.forEachIndexed { index, slide ->
            val number = index + 1
            val relIds = mutableMapOf<String, String>()
            slide.images.forEachIndexed { imageIndex, image ->
                val part = "ppt/media/image${number}_${imageIndex + 1}.${imageExtension(image.bytes)}"
                parts[part] = image.bytes
                relIds[part] = "rId${imageIndex + 1}"
            }
            parts["ppt/slides/slide$number.xml"] = newSlideXml(
                slide.lines,
                slide.transition,
                slide.shapes,
                slide.images.mapIndexed { imageIndex, image ->
                    val part = "ppt/media/image${number}_${imageIndex + 1}.${imageExtension(image.bytes)}"
                    image.copy(partName = part)
                },
                relIds,
            ).encodeToByteArray()
            parts["ppt/slides/_rels/slide$number.xml.rels"] = slideRels(number, relIds).encodeToByteArray()
            parts["ppt/notesSlides/notesSlide$number.xml"] = notesXml(slide.notes).encodeToByteArray()
            parts["ppt/notesSlides/_rels/notesSlide$number.xml.rels"] = notesRels(number).encodeToByteArray()
        }
        return zip(parts)
    }

    /**
     * The relationships of a slide.
     *
     * A slide points at its layout and at its notes, and at one part for
     * each picture on it.
     */
    private fun slideRels(number: Int, imageRelIds: Map<String, String> = emptyMap()): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<Relationships xmlns=\"$RELS_NAMESPACE\">")
            append("<Relationship Id=\"rId1\" Type=\"$REL_BASE/slideLayout\" ")
            append("Target=\"../slideLayouts/slideLayout1.xml\"/>")
            append("<Relationship Id=\"rId2\" Type=\"$REL_BASE/notesSlide\" ")
            append("Target=\"../notesSlides/notesSlide$number.xml\"/>")
            imageRelIds.forEach { (part, relId) ->
                append("<Relationship Id=\"$relId\" Type=\"$REL_BASE/image\" ")
                append("Target=\"${part.removePrefix("ppt/")}\"/>")
            }
            append("</Relationships>")
        }

    /** The kind of picture the bytes hold. */
    internal fun imageExtension(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpeg"
        bytes.size > 2 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "gif"
        bytes.size > 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "bmp"
        else -> "bin"
    }

    /** Replaces paragraph text, appends extra lines and keeps the tail paragraphs. */
    internal fun rewriteSlideText(xml: String, lines: List<String>): String {
        val ranges = paragraphRanges(xml)
        if (ranges.isEmpty()) return xml
        val out = StringBuilder(xml.length + 128)
        var cursor = 0
        // Where the last paragraph ends in the rebuilt string. An offset taken
        // from the original XML would drift as soon as an edit changed the
        // length of the text, and the paragraph the user added would land in
        // the middle of the slide.
        var lastParagraphEnd = 0
        ranges.forEachIndexed { i, range ->
            out.append(xml, cursor, range.first)
            out.append(if (i < lines.size) replaceParagraphText(xml.substring(range), lines[i]) else xml.substring(range))
            cursor = range.last + 1
            lastParagraphEnd = out.length
        }
        out.append(xml, cursor, xml.length)
        if (lines.size > ranges.size) {
            val template = xml.substring(ranges.last())
            out.insert(
                lastParagraphEnd,
                lines.drop(ranges.size).joinToString("") { replaceParagraphText(template, it) },
            )
        }
        return out.toString()
    }

    private fun replaceParagraphText(xml: String, text: String): String {
        val start = findTagStart(xml, "a:t", 0)
        if (start == null) {
            val close = xml.lastIndexOf("</a:p>")
            if (close < 0 || text.isEmpty()) return xml
            return xml.substring(0, close) + "<a:r><a:rPr lang=\"en-US\"/><a:t${spaceAttr(text)}>${escape(text)}</a:t></a:r>" + xml.substring(close)
        }
        val openEnd = xml.indexOf('>', start)
        val close = xml.indexOf("</a:t>", openEnd)
        if (openEnd < 0 || close < 0) return xml
        val open = xml.substring(start, openEnd + 1).replace(Regex("\\s+xml:space=\"[^\"]*\""), "")
            .replace("<a:t", "<a:t${spaceAttr(text)}")
        val before = xml.substring(0, start)
        val after = xml.substring(close + "</a:t>".length)
        return before + open + escape(text) + "</a:t>" + clearTextRuns(after)
    }

    private fun clearTextRuns(xml: String): String {
        val out = StringBuilder(xml.length)
        var cursor = 0
        var first = true
        while (true) {
            val start = findTagStart(xml, "a:t", cursor) ?: break
            val openEnd = xml.indexOf('>', start)
            val close = xml.indexOf("</a:t>", openEnd)
            if (openEnd < 0 || close < 0) break
            out.append(xml, cursor, openEnd + 1)
            if (!first) out.append(xml, openEnd + 1, openEnd + 1)
            first = false
            cursor = close
        }
        out.append(xml, cursor, xml.length)
        return out.toString()
    }

    private fun spaceAttr(text: String) = if (text.startsWith(' ') || text.endsWith(' ')) " xml:space=\"preserve\"" else ""
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun paragraphRanges(xml: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var cursor = 0
        while (true) {
            val start = findTagStart(xml, "a:p", cursor) ?: break
            val openEnd = xml.indexOf('>', start)
            val close = xml.indexOf("</a:p>", openEnd)
            if (openEnd < 0 || close < 0) break
            val end = close + "</a:p>".length - 1
            result += start..end
            cursor = end + 1
        }
        return result
    }

    private fun findTagStart(xml: String, name: String, from: Int): Int? {
        var i = xml.indexOf("<$name", from)
        while (i >= 0) {
            val next = xml.getOrNull(i + name.length + 1)
            if (next == null || next == '>' || next.isWhitespace()) return i
            i = xml.indexOf("<$name", i + 1)
        }
        return null
    }

    private fun slideIds(xml: String) = Regex("<p:sldId\\b[^>]*r:id=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()
    private fun rewriteSlideList(xml: String, rels: List<Relationship>): String {
        val list = "<p:sldIdLst>" + rels.mapIndexed { i, rel -> "<p:sldId id=\"${256 + i}\" r:id=\"${rel.id}\"/>" }.joinToString("") + "</p:sldIdLst>"
        val start = xml.indexOf("<p:sldIdLst>")
        if (start >= 0) {
            val end = xml.indexOf("</p:sldIdLst>", start) + "</p:sldIdLst>".length
            return xml.substring(0, start) + list + xml.substring(end)
        }
        val masterEnd = xml.indexOf("</p:sldMasterIdLst>")
        val at = if (masterEnd >= 0) masterEnd + "</p:sldMasterIdLst>".length else xml.indexOf('>') + 1
        return xml.substring(0, at) + list + xml.substring(at)
    }

    internal fun rewriteContentTypesForTest(xml: String, kept: List<String>): String = rewriteContentTypes(xml, kept)

    private fun rewriteContentTypes(xml: String, kept: List<String>): String {
        val keptParts = kept.toSet()
        val updated = Regex("<Override\\b[^>]*PartName=\"([^\"]+)\"[^>]*/>")
            .replace(xml) { match ->
                val path = match.groupValues[1].removePrefix("/")
                when {
                    path.startsWith("ppt/slides/slide") && path.endsWith(".xml") && path !in keptParts -> ""
                    path.startsWith("ppt/notesSlides/notesSlide") && path.endsWith(".xml") -> {
                        val number = path.removePrefix("ppt/notesSlides/notesSlide").removeSuffix(".xml").toIntOrNull()
                        if (number != null && "ppt/slides/slide$number.xml" !in keptParts) "" else match.value
                    }
                    else -> match.value
                }
            }
        val declared = Regex("PartName=\"([^\"]+)\"").findAll(updated)
            .map { it.groupValues[1] }.toSet()
        val additions = kept.mapNotNull { part ->
            val number = part.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: return@mapNotNull null
            listOf(
                part to "application/vnd.openxmlformats-officedocument.presentationml.slide+xml",
                "ppt/notesSlides/notesSlide$number.xml" to "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml",
            )
        }.flatten().filter { "/${it.first}" !in declared }
        return additions.fold(updated) { current, (part, type) ->
            current.replace("</Types>", "<Override PartName=\"/$part\" ContentType=\"$type\"/></Types>")
        }
    }

    private fun notesPartOf(part: String, parts: Map<String, ByteArray>): String? {
        val rels = parts[relsNameOf(part)] ?: return null
        return parseRels(rels).firstOrNull { it.type.endsWith("/notesSlide") }?.let { resolvePart("slides/${it.target}") }
    }
    private fun relsNameOf(part: String) = part.substringBeforeLast('/') + "/_rels/" + part.substringAfterLast('/') + ".rels"

    private data class Relationship(val id: String, val type: String, val target: String)
    private fun parseRels(bytes: ByteArray): List<Relationship> {
        val tag = Regex("<Relationship\\b([^>]*?)/?>")
        val attr = Regex("([A-Za-z:]+)=\"([^\"]*)\"")
        return tag.findAll(bytes.decodeToString()).mapNotNull { m ->
            val a = attr.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val id = a["Id"] ?: return@mapNotNull null
            val type = a["Type"] ?: return@mapNotNull null
            val target = a["Target"] ?: return@mapNotNull null
            Relationship(id, type, target)
        }.toList()
    }
    private fun relsXml(rels: List<Relationship>) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$RELS_NAMESPACE\">")
        rels.forEach { append("<Relationship Id=\"${it.id}\" Type=\"${it.type}\" Target=\"${it.target}\"/>") }
        append("</Relationships>")
    }
    private fun resolvePart(target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val segments = mutableListOf<String>()
        ("ppt/$target").split('/').forEach { s -> when (s) { "", "." -> Unit; ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex); else -> segments += s } }
        return segments.joinToString("/")
    }
    private fun readParts(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val e = zip.nextEntry ?: break; result[e.name] = zip.readBytes(); zip.closeEntry() } }
        return result
    }
    private fun zip(parts: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> parts.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    private fun newSlideXml(
        lines: List<String>,
        transition: SlideTransition = SlideTransition.NONE,
        shapes: List<SlideShape> = emptyList(),
        images: List<SlideImage> = emptyList(),
        imageRelIds: Map<String, String> = emptyMap(),
    ): String {
        val bodyLines = lines.drop(1)
        val body = bodyLines.joinToString("") { paragraphXml(it) }
        val title = lines.firstOrNull().orEmpty()
        val titleParagraph = if (title.isEmpty()) {
            "<a:p><a:endParaRPr lang=\"en-US\"/></a:p>"
        } else {
            "<a:p><a:r><a:rPr lang=\"en-US\"/><a:t${spaceAttr(title)}>${escape(title)}</a:t></a:r></a:p>"
        }
        val xml = SLIDE_XML.replace("<a:p><a:r><a:rPr lang=\"en-US\"/><a:t>{{TITLE}}</a:t></a:r></a:p>", titleParagraph)
            .replace("{{BODY}}", body.ifEmpty { "<a:p><a:endParaRPr lang=\"en-US\"/></a:p>" })
        val withShapes = addShapes(xml, shapes, images, imageRelIds)
        return applyTransition(withShapes, transition)
    }

    /**
     * Puts the shapes and the pictures into the slide's shape tree.
     *
     * They go at the end of the tree, so they sit on top of whatever the
     * slide already had.
     */
    internal fun addShapes(
        xml: String,
        shapes: List<SlideShape>,
        images: List<SlideImage>,
        imageRelIds: Map<String, String>,
    ): String {
        if (shapes.isEmpty() && images.isEmpty()) return xml
        val builder = StringBuilder()
        shapes.forEachIndexed { index, shape ->
            builder.append(shapeXml(shape, 100 + index))
        }
        images.forEachIndexed { index, image ->
            val relId = imageRelIds[image.partName] ?: return@forEachIndexed
            builder.append(imageXml(image, relId, 200 + index))
        }
        val closeAt = xml.lastIndexOf("</p:spTree>")
        if (closeAt < 0) return xml
        return xml.substring(0, closeAt) + builder + xml.substring(closeAt)
    }

    /** A shape, as the format writes it. */
    private fun shapeXml(shape: SlideShape, id: Int): String {
        val preset = when (shape.kind) {
            SlideShapeKind.RECTANGLE -> "rect"
            SlideShapeKind.ROUNDED_RECTANGLE -> "roundRect"
            SlideShapeKind.ELLIPSE -> "ellipse"
        }
        val fill = shape.fillRgb?.let {
            "<a:solidFill><a:srgbClr val=\"%06X\"/></a:solidFill>".format(it and 0xFFFFFF)
        }.orEmpty()
        val name = escape(shape.name ?: "Shape $id")
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr/><p:nvPr/>" +
            "</p:nvSpPr><p:spPr><a:xfrm><a:off x=\"${shape.xEmu}\" y=\"${shape.yEmu}\"/>" +
            "<a:ext cx=\"${shape.widthEmu}\" cy=\"${shape.heightEmu}\"/></a:xfrm>" +
            "<a:prstGeom prst=\"$preset\"><a:avLst/></a:prstGeom>$fill</p:spPr>" +
            "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang=\"en-US\"/></a:p></p:txBody></p:sp>"
    }

    /** A picture, as the format writes it. */
    private fun imageXml(image: SlideImage, relId: String, id: Int): String =
        "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Picture $id\"/>" +
            "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
            "<p:blipFill><a:blip r:embed=\"$relId\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
            "<p:spPr><a:xfrm><a:off x=\"${image.xEmu}\" y=\"${image.yEmu}\"/>" +
            "<a:ext cx=\"${image.widthEmu}\" cy=\"${image.heightEmu}\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"

    /**
     * Puts the transition into a slide, or takes it out.
     *
     * The element belongs after the colour mapping and before the timing,
     * which is where the format says it goes.
     */
    internal fun applyTransition(xml: String, transition: SlideTransition): String {
        val without = removeElement(xml, "p:transition")
        if (transition == SlideTransition.NONE) return without
        val element = "<p:transition spd=\"med\">${transitionElement(transition)}</p:transition>"
        val timingAt = without.indexOf("<p:timing")
        if (timingAt >= 0) return without.substring(0, timingAt) + element + without.substring(timingAt)
        val closeAt = without.lastIndexOf("</p:sld>")
        if (closeAt < 0) return without
        return without.substring(0, closeAt) + element + without.substring(closeAt)
    }

    /** The element that stands for a transition. */
    private fun transitionElement(transition: SlideTransition): String = when (transition) {
        SlideTransition.NONE -> ""
        SlideTransition.FADE -> "<p:fade/>"
        SlideTransition.WIPE -> "<p:wipe dir=\"d\"/>"
        SlideTransition.PUSH -> "<p:push dir=\"u\"/>"
        SlideTransition.COVER -> "<p:cover dir=\"d\"/>"
        SlideTransition.DISSOLVE -> "<p:dissolve/>"
        SlideTransition.CUT -> "<p:cut/>"
        SlideTransition.SPLIT -> "<p:split orient=\"horz\" dir=\"out\"/>"
        SlideTransition.ZOOM -> "<p:zoom/>"
    }

    /**
     * Takes an element out of a piece of XML.
     *
     * Both forms are handled: an element with a body and one that stands
     * alone.
     */
    private fun removeElement(xml: String, name: String): String {
        val start = xml.indexOf("<$name")
        if (start < 0) return xml
        val tagEnd = xml.indexOf('>', start)
        if (tagEnd < 0) return xml
        if (xml[tagEnd - 1] == '/') return xml.substring(0, start) + xml.substring(tagEnd + 1)
        val close = xml.indexOf("</$name>", tagEnd)
        if (close < 0) return xml.substring(0, start) + xml.substring(tagEnd + 1)
        return xml.substring(0, start) + xml.substring(close + name.length + 3)
    }
    private fun paragraphXml(text: String) = "<a:p><a:r><a:rPr lang=\"en-US\"/><a:t${spaceAttr(text)}>${escape(text)}</a:t></a:r></a:p>"
    private fun notesXml(notes: String): String {
        val paragraphs = notes.split('\n').ifEmpty { listOf("") }.joinToString("") {
            if (it.isEmpty()) "<a:p><a:endParaRPr lang=\"en-US\"/></a:p>" else paragraphXml(it)
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notes xmlns:a="$DRAWINGML_NAMESPACE" xmlns:r="$REL_BASE" xmlns:p="$PRESENTATIONML_NAMESPACE"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:sp><p:nvSpPr><p:cNvPr id="2" name="Notes Body"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>$paragraphs</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>"""
    }
    /** The pictures a slide already has, by the part they point at. */
    private fun picturePartsOf(slideXml: String, rels: List<Relationship>): Set<String> {
        val byId = rels.associate { it.id to resolvePart(it.target) }
        val found = mutableSetOf<String>()
        Regex("""r:embed="(rId\d+)"""").findAll(slideXml).forEach { match ->
            byId[match.groupValues[1]]?.let { found += it }
        }
        return found
    }

    /** A relationship for a picture. */
    private fun addImageRelationship(
        relationships: String?,
        relId: String,
        part: String,
    ): String? {
        val text = relationships ?: return null
        val relationship = "<Relationship Id=\"$relId\" " +
            "Type=\"$REL_BASE/image\" " +
            "Target=\"${part.removePrefix("ppt/")}\"/>"
        return text.replace("</Relationships>", "$relationship</Relationships>")
    }

    /** The number a new relationship gets. */
    private fun nextRelationshipNumber(relationships: String?): Int {
        val highest = relationships?.let { text ->
            Regex("""Id="rId(\d+)"""").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }
                .maxOrNull()
        } ?: 0
        return highest + 1
    }

    private fun notesRels(n: Int) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$RELS_NAMESPACE"><Relationship Id="rId1" Type="$REL_BASE/notesMaster" Target="../notesMasters/notesMaster1.xml"/><Relationship Id="rId2" Type="$REL_BASE/slide" Target="../slides/slide$n.xml"/></Relationships>"""
    private fun notesMasterRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$RELS_NAMESPACE"><Relationship Id="rId1" Type="$REL_BASE/theme" Target="../theme/theme1.xml"/></Relationships>"""
    private fun notesMasterXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notesMaster xmlns:a="$DRAWINGML_NAMESPACE" xmlns:r="$REL_BASE" xmlns:p="$PRESENTATIONML_NAMESPACE"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:notesStyle><a:lvl1pPr><a:defRPr lang="en-US"/></a:lvl1pPr></p:notesStyle></p:notesMaster>"""

    private fun themeXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:theme xmlns:a="$DRAWINGML_NAMESPACE" name="ZiyOffice"><a:themeElements>
        <a:clrScheme name="ZiyOffice"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1A1C1B"/></a:dk2><a:lt2><a:srgbClr val="F5F6F5"/></a:lt2><a:accent1><a:srgbClr val="2F6FED"/></a:accent1><a:accent2><a:srgbClr val="0E5A4E"/></a:accent2><a:accent3><a:srgbClr val="B25E00"/></a:accent3><a:accent4><a:srgbClr val="6B3FA0"/></a:accent4><a:accent5><a:srgbClr val="BA1A1A"/></a:accent5><a:accent6><a:srgbClr val="6F7975"/></a:accent6><a:hlink><a:srgbClr val="2F6FED"/></a:hlink><a:folHlink><a:srgbClr val="6B3FA0"/></a:folHlink></a:clrScheme>
        <a:fontScheme name="ZiyOffice"><a:majorFont><a:latin typeface="Carlito"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Carlito"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme>
        <a:fmtScheme name="ZiyOffice"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme>
        </a:themeElements></a:theme>
    """.trimIndent()

    private fun slideMasterXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldMaster xmlns:a="$DRAWINGML_NAMESPACE" xmlns:r="$REL_BASE" xmlns:p="$PRESENTATIONML_NAMESPACE"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>
    """.trimIndent()

    private fun slideLayoutXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:a="$DRAWINGML_NAMESPACE" xmlns:r="$REL_BASE" xmlns:p="$PRESENTATIONML_NAMESPACE" type="titleAndContent"><p:cSld name="Title and Content"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>
    """.trimIndent()

    private fun presentationXml(count: Int, width: Int, height: Int) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:presentation xmlns:a=\"$DRAWINGML_NAMESPACE\" xmlns:r=\"$REL_BASE\" xmlns:p=\"$PRESENTATIONML_NAMESPACE\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>")
        repeat(count) { append("<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 2}\"/>") }
        append("</p:sldIdLst><p:sldSz cx=\"$width\" cy=\"$height\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>")
    }
    private fun presentationRels(count: Int) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$RELS_NAMESPACE\"><Relationship Id=\"rId1\" Type=\"$REL_BASE/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/><Relationship Id=\"rId${count + 2}\" Type=\"$REL_BASE/notesMaster\" Target=\"notesMasters/notesMaster1.xml\"/>")
        repeat(count) { append("<Relationship Id=\"rId${it + 2}\" Type=\"$SLIDE_REL_TYPE\" Target=\"slides/slide${it + 1}.xml\"/>") }
        append("</Relationships>")
    }
    private fun contentTypes(count: Int) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/><Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/><Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/><Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/><Override PartName=\"/ppt/notesMasters/notesMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml\"/>")
        repeat(count) { i -> append("<Override PartName=\"/ppt/slides/slide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/><Override PartName=\"/ppt/notesSlides/notesSlide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\"/>") }
        append("</Types>")
    }

    private companion object {
        const val CONTENT_TYPES_PART = "[Content_Types].xml"
        const val PRESENTATION_PART = "ppt/presentation.xml"
        const val PRESENTATION_RELS_PART = "ppt/_rels/presentation.xml.rels"
        const val RELS_NAMESPACE = "http://schemas.openxmlformats.org/package/2006/relationships"
        const val REL_BASE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val SLIDE_REL_TYPE = "$REL_BASE/slide"
        const val EMPTY_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$RELS_NAMESPACE\"/>"
        val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$RELS_NAMESPACE"><Relationship Id="rId1" Type="$REL_BASE/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
        val MASTER_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$RELS_NAMESPACE"><Relationship Id="rId1" Type="$REL_BASE/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="$REL_BASE/theme" Target="../theme/theme1.xml"/></Relationships>"""
        val LAYOUT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$RELS_NAMESPACE"><Relationship Id="rId1" Type="$REL_BASE/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""
        val SLIDE_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:a="$DRAWINGML_NAMESPACE" xmlns:r="$RELS_NAMESPACE" xmlns:p="$PRESENTATIONML_NAMESPACE"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US"/><a:t>{{TITLE}}</a:t></a:r></a:p></p:txBody></p:sp><p:sp><p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>{{BODY}}</p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
    }
}
